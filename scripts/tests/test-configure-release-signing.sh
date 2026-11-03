#!/usr/bin/env bash
# Generates temporary keys and tests uploads against a local fake gh only.
set +x
set -euo pipefail
umask 077

test_directory=$(mktemp -d /tmp/medtracker-signing-test.XXXXXX)
trap 'unset SIGNING_TEST_PASSWORD; rm -rf -- "$test_directory"' EXIT
test_script_directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
checkout=$(git -C "$test_script_directory/../.." rev-parse --show-toplevel)
script="$checkout/scripts/configure-release-signing.sh"
export XDG_DATA_HOME="$test_directory/data"
export FAKE_GH_STATE="$test_directory/github"
export PATH="$test_script_directory/fakes:$PATH"
mkdir -p "$FAKE_GH_STATE"
key_directory="$XDG_DATA_HOME/medtracker/release-signing"
key_store="$key_directory/release.p12"
password_file="$key_directory/release.password"
secret_names=(
  ANDROID_RELEASE_KEY_ALIAS
  ANDROID_RELEASE_STORE_PASSWORD
  ANDROID_RELEASE_KEY_PASSWORD
  ANDROID_RELEASE_KEYSTORE_BASE64
)
fail() { echo "FAIL: $*" >&2; exit 1; }
clear_fake_secrets() {
  for secret in "${secret_names[@]}"; do rm -f -- "$FAKE_GH_STATE/$secret"; done
}

bash "$script"
[[ -f "$key_store" && -f "$password_file" ]] || fail 'Key/password pair missing.'
[[ $(stat -c %a "$key_directory") == 700 ]] || fail 'Directory must be private.'
[[ $(stat -c %a "$key_store") == 600 && $(stat -c %a "$password_file") == 600 ]] || fail 'Files must be private.'
for secret in "${secret_names[@]}"; do
  [[ -f "$FAKE_GH_STATE/$secret" ]] || fail "Missing $secret"
done
IFS= read -r SIGNING_TEST_PASSWORD < "$password_file"
export SIGNING_TEST_PASSWORD
keytool -certreq -keystore "$key_store" -alias medtracker \
  -storepass:env SIGNING_TEST_PASSWORD -keypass:env SIGNING_TEST_PASSWORD >/dev/null
openssl dgst -sha256 < "$key_store" > "$test_directory/expected-key"
printf '%s' "$SIGNING_TEST_PASSWORD" | openssl dgst -sha256 > "$test_directory/expected-password"
printf '%s' medtracker | openssl dgst -sha256 > "$test_directory/expected-alias"
cmp -s "$test_directory/expected-key" "$FAKE_GH_STATE/ANDROID_RELEASE_KEYSTORE_BASE64" || fail 'Uploaded key differs.'
cmp -s "$test_directory/expected-password" "$FAKE_GH_STATE/ANDROID_RELEASE_STORE_PASSWORD" || fail 'Store password differs.'
cmp -s "$test_directory/expected-password" "$FAKE_GH_STATE/ANDROID_RELEASE_KEY_PASSWORD" || fail 'Key password differs.'
cmp -s "$test_directory/expected-alias" "$FAKE_GH_STATE/ANDROID_RELEASE_KEY_ALIAS" || fail 'Alias differs.'
key_digest=$(sha256sum "$key_store")

# The fake rejects overwrites, so successful repetition proves existing secrets are skipped.
bash "$script"
[[ $(sha256sum "$key_store") == "$key_digest" ]] || fail 'Repeated run replaced the key.'

# Resume after a partially successful upload using the same local pair.
clear_fake_secrets
export FAKE_GH_FAIL_SECRET=ANDROID_RELEASE_STORE_PASSWORD
if bash "$script"; then fail 'Expected simulated upload failure.'; fi
unset FAKE_GH_FAIL_SECRET
[[ -f "$FAKE_GH_STATE/ANDROID_RELEASE_KEY_ALIAS" ]] || fail 'Expected first upload before failure.'
bash "$script"
[[ $(sha256sum "$key_store") == "$key_digest" ]] || fail 'Retry replaced the key.'
cmp -s "$test_directory/expected-key" "$FAKE_GH_STATE/ANDROID_RELEASE_KEYSTORE_BASE64" || fail 'Retry uploaded wrong key.'

# Never regenerate a lost key when remote secrets already partly exist.
clear_fake_secrets
printf '%s' medtracker | openssl dgst -sha256 > "$FAKE_GH_STATE/ANDROID_RELEASE_KEY_ALIAS"
mv "$key_directory" "$test_directory/backup"
if bash "$script"; then fail 'Expected refusal with remote secrets and missing local key.'; fi
[[ ! -e "$key_directory" ]] || fail 'A replacement key was generated.'
mv "$test_directory/backup" "$key_directory"

# A corrupt/incomplete local pair also fails without generating a replacement.
clear_fake_secrets
mv "$password_file" "$test_directory/password-backup"
if bash "$script"; then fail 'Expected refusal of incomplete local credentials.'; fi
[[ $(sha256sum "$key_store") == "$key_digest" ]] || fail 'Incomplete pair replaced the key.'
mv "$test_directory/password-backup" "$password_file"

# Refuse symlinked credential stores instead of reading or modifying their target.
mv "$key_directory" "$test_directory/symlink-backup"
ln -s "$test_directory/symlink-backup" "$key_directory"
if bash "$script"; then fail 'Expected refusal of symlinked credentials.'; fi
rm -- "$key_directory"
mv "$test_directory/symlink-backup" "$key_directory"

# A concurrent process holding the same lock prevents mutation/upload.
exec {test_lock_fd}> "$XDG_DATA_HOME/medtracker/.release-signing.lock"
flock -n "$test_lock_fd"
if bash "$script"; then fail 'Expected refusal while another setup holds the lock.'; fi
flock -u "$test_lock_fd"
exec {test_lock_fd}>&-

# A caller cannot accidentally put newly generated credentials into the checkout.
unsafe_directory="$checkout/.signing-test-${test_directory##*/}"
export XDG_DATA_HOME="$unsafe_directory"
if bash "$script"; then fail 'Expected refusal to store credentials in checkout.'; fi
[[ ! -e "$unsafe_directory" ]] || fail 'Created credentials in checkout.'

echo 'PASS: generation, uploaded values, permissions, idempotency, retry and refusal paths.'
