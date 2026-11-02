#!/usr/bin/env bash
# Personal test-build signing only. Never use this debug key for production.
# Secret data travels over stdin to gh; it is never printed or committed.
set -euo pipefail
umask 077

repository="dadyarri/med-tracker"
key_directory="${XDG_DATA_HOME:-$HOME/.local/share}/medtracker"
key_store="${1:-$key_directory/ci-debug.keystore}"

command -v keytool >/dev/null || { echo 'Install JDK 21 (keytool is required).' >&2; exit 1; }
command -v gh >/dev/null || { echo 'Install and authenticate GitHub CLI (gh).' >&2; exit 1; }
command -v base64 >/dev/null || { echo 'base64 is required.' >&2; exit 1; }
# Verify repository API access before generating or changing anything.
gh repo view "$repository" --json nameWithOwner >/dev/null
existing_secrets=$(gh secret list --repo "$repository" --json name --jq '.[].name')
if [[ "$existing_secrets" == *ANDROID_DEBUG_KEYSTORE_BASE64* ]]; then
  echo 'ANDROID_DEBUG_KEYSTORE_BASE64 already exists; leaving it unchanged.'
  echo 'Do not replace a configured signing key: installed APKs depend on it.'
  exit 0
fi

if [[ ! -e "$key_store" ]]; then
  # Supplying an explicit path means reuse an existing key, never silently create one.
  if [[ $# -gt 0 ]]; then
    echo "Keystore does not exist: $key_store" >&2
    exit 1
  fi
  mkdir -p "$key_directory"
  keytool -genkeypair -keystore "$key_store" -storetype JKS \
    -alias androiddebugkey -storepass android -keypass android \
    -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=MedTracker Test, O=MedTracker, C=RU' >/dev/null 2>&1
fi

keytool -list -keystore "$key_store" -storepass android -alias androiddebugkey >/dev/null
base64 < "$key_store" | gh secret set ANDROID_DEBUG_KEYSTORE_BASE64 --repo "$repository"
echo 'Persistent test signing key saved to GitHub repository secret.'
echo "Keep the original keystore safe: $key_store"
echo 'Reuse it for every future test build; do not regenerate it.'
