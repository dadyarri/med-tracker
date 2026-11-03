#!/usr/bin/env bash
# Generate a persistent release key and upload only missing repository secrets.
# Secrets travel over stdin; neither passwords nor private key bytes are printed.
set +x
set -euo pipefail
umask 077

if [[ ${1:-} == --help ]]; then
  printf '%s\n' 'Usage: bash scripts/configure-release-signing.sh'
  printf '%s\n' 'Generates/reuses a release key outside the checkout and uploads missing GitHub Secrets.'
  printf 'Location: %s\n' "${XDG_DATA_HOME:-$HOME/.local/share}/medtracker/release-signing"
  exit 0
fi
[[ $# == 0 ]] || { echo 'No arguments expected; use --help.' >&2; exit 1; }

repository=dadyarri/med-tracker
release_alias=medtracker
for tool in gh keytool openssl base64 realpath git flock; do
  command -v "$tool" >/dev/null || { echo "Install $tool first." >&2; exit 1; }
done

# Authenticate before generating any key or touching the local credential store.
gh repo view "$repository" --json nameWithOwner >/dev/null
existing_secrets=
has_secret() { [[ $'\n'"$existing_secrets"$'\n' == *$'\n'"$1"$'\n'* ]]; }
required_secrets=(
  ANDROID_RELEASE_KEY_ALIAS
  ANDROID_RELEASE_STORE_PASSWORD
  ANDROID_RELEASE_KEY_PASSWORD
  ANDROID_RELEASE_KEYSTORE_BASE64
)
load_secret_state() {
  existing_secrets=$(gh secret list --repo "$repository" --json name --jq '.[].name')
  configured_count=0
  for secret in "${required_secrets[@]}"; do
    if has_secret "$secret"; then configured_count=$((configured_count + 1)); fi
  done
}
load_secret_state
if [[ $configured_count == 4 ]]; then
  echo 'All release secrets already exist; leaving them unchanged.'
  echo 'Keep the original release keystore and passwords safe.'
  exit 0
fi

script_directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
checkout=$(git -C "$script_directory/.." rev-parse --show-toplevel)
key_parent=$(realpath -m "${XDG_DATA_HOME:-$HOME/.local/share}/medtracker")
key_directory="$key_parent/release-signing"
case "$key_directory/" in
  "$checkout/"*) echo 'Refusing to store release credentials inside the checkout.' >&2; exit 1 ;;
esac
key_store="$key_directory/release.p12"
password_file="$key_directory/release.password"
if [[ -L "$key_directory" || -L "$key_store" || -L "$password_file" ]]; then
  echo 'Refusing symlinks in the release credential store.' >&2
  exit 1
fi
if [[ $configured_count != 0 && (! -f "$key_store" || ! -f "$password_file") ]]; then
  echo 'Some release secrets exist, but the local key/password pair is missing.' >&2
  echo 'Restore the original local pair; a replacement key will not be generated.' >&2
  exit 1
fi

mkdir -p "$key_parent"
exec {release_lock_fd}> "$key_parent/.release-signing.lock"
flock -n "$release_lock_fd" || { echo 'Another release-signing setup is running.' >&2; exit 1; }
# A concurrent setup may have finished while authentication was in progress.
load_secret_state
if [[ $configured_count == 4 ]]; then
  echo 'All release secrets already exist; leaving them unchanged.'
  exit 0
fi
if [[ $configured_count != 0 && (! -f "$key_store" || ! -f "$password_file") ]]; then
  echo 'Restore the original local key/password pair before completing partial secrets.' >&2
  exit 1
fi
staging_directory=
trap 'unset MEDTRACKER_RELEASE_PASSWORD; if [[ -n "$staging_directory" && -d "$staging_directory" ]]; then rm -f -- "$staging_directory/release.p12" "$staging_directory/release.password"; rmdir -- "$staging_directory"; fi' EXIT

if [[ ! -e "$key_directory" ]]; then
  # Install the complete pair as one directory rename, never overwrite a key.
  staging_directory=$(mktemp -d "$key_parent/.release-signing.XXXXXX")
  openssl rand -hex 32 > "$staging_directory/release.password"
  IFS= read -r MEDTRACKER_RELEASE_PASSWORD < "$staging_directory/release.password"
  export MEDTRACKER_RELEASE_PASSWORD
  keytool -genkeypair -keystore "$staging_directory/release.p12" -storetype PKCS12 \
    -alias "$release_alias" -storepass:env MEDTRACKER_RELEASE_PASSWORD \
    -keypass:env MEDTRACKER_RELEASE_PASSWORD -keyalg RSA -keysize 3072 \
    -validity 10000 -dname 'CN=MedTracker Release, O=MedTracker, C=RU' -noprompt >/dev/null
  mv -- "$staging_directory" "$key_directory"
  staging_directory=
fi

if [[ ! -f "$key_store" || ! -f "$password_file" ]]; then
  echo 'Incomplete local release credentials; restore the original pair. Nothing was uploaded.' >&2
  exit 1
fi
chmod 700 "$key_directory"
chmod 600 "$key_store" "$password_file"
IFS= read -r MEDTRACKER_RELEASE_PASSWORD < "$password_file"
[[ "$MEDTRACKER_RELEASE_PASSWORD" =~ ^[0-9a-f]{64}$ ]] || {
  echo 'Invalid local password file; restore the original pair. Nothing was uploaded.' >&2
  exit 1
}
export MEDTRACKER_RELEASE_PASSWORD
# Validate the key and both passwords before uploading anything.
keytool -certreq -keystore "$key_store" -alias "$release_alias" \
  -storepass:env MEDTRACKER_RELEASE_PASSWORD -keypass:env MEDTRACKER_RELEASE_PASSWORD >/dev/null

for secret in "${required_secrets[@]}"; do
  if has_secret "$secret"; then continue; fi
  case "$secret" in
    ANDROID_RELEASE_KEY_ALIAS)
      printf '%s' "$release_alias" | gh secret set "$secret" --repo "$repository" ;;
    ANDROID_RELEASE_STORE_PASSWORD|ANDROID_RELEASE_KEY_PASSWORD)
      printf '%s' "$MEDTRACKER_RELEASE_PASSWORD" | gh secret set "$secret" --repo "$repository" ;;
    ANDROID_RELEASE_KEYSTORE_BASE64)
      base64 < "$key_store" | gh secret set "$secret" --repo "$repository" ;;
  esac
done

echo 'Release signing secrets configured.'
echo "Keep BOTH files safe: $key_store and $password_file"
echo 'Back up this pair securely outside the repository; future APK updates require the same key.'
