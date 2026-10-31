#!/usr/bin/env bash
# For whoever publishes releases, once: makes the key that signs every published APK. Keep the .jks file and
# its password safe and never commit them: each later release must be signed with the same key, or TVs that
# have Glass TV installed refuse the update.
#
# Usage: bash tools/make-release-key.sh [where-to-keep-it.jks]     (default ~/glass-tv-release.jks)
set -euo pipefail
KS="${1:-$HOME/glass-tv-release.jks}"
[ -e "$KS" ] && { echo "$KS already exists; not overwriting it." >&2; exit 1; }
KEYTOOL=keytool
command -v keytool >/dev/null 2>&1 && keytool -help >/dev/null 2>&1 \
  || KEYTOOL="$(brew --prefix openjdk@17 2>/dev/null)/bin/keytool"
echo "keytool asks for a password (6 characters or more). Remember it: it goes into GitHub as a secret."
"$KEYTOOL" -genkeypair -keystore "$KS" -storetype PKCS12 -alias glasstv -keyalg RSA -keysize 3072 \
  -validity 10000 -dname "CN=Glass TV Malaysia"
base64 < "$KS" | tr -d '\n' > "$KS.b64"
command -v pbcopy >/dev/null 2>&1 && pbcopy < "$KS.b64" && copied=" (already copied to the clipboard)" || copied=""
cat <<MSG

Key made: $KS
Now on GitHub, in your repository: Settings > Secrets and variables > Actions > New repository secret.
  RELEASE_KEYSTORE_B64       the contents of $KS.b64$copied
  RELEASE_KEYSTORE_PASSWORD  the password you just chose
  RELEASE_KEY_ALIAS          glasstv
  RELEASE_KEY_PASSWORD       the same password
Then delete $KS.b64. Keep $KS and the password somewhere safe (a password manager), off GitHub.
MSG
