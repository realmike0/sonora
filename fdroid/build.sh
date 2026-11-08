#!/usr/bin/env bash
# Costruisce il repository F-Droid in fdroid/ (repo/ con gli APK + indice firmato).
# Variabili: FDROID_KEYSTORE_B64, FDROID_KEYPASS, REPO_URL. Gli APK vanno già in fdroid/repo/.
set -euo pipefail
cd "$(dirname "$0")"
: "${FDROID_KEYSTORE_B64:?manca il secret FDROID_KEYSTORE_B64}"
: "${FDROID_KEYPASS:?manca il secret FDROID_KEYPASS}"
: "${REPO_URL:?manca REPO_URL}"
mkdir -p repo
echo "$FDROID_KEYSTORE_B64" | base64 -d > keystore.p12
cat > config.yml <<EOC
repo_url: ${REPO_URL}
repo_name: Sonora
repo_description: Repository ufficiale di Sonora (beta).
repo_keyalias: sonora-repo
keystore: keystore.p12
keystorepass: ${FDROID_KEYPASS}
keypass: ${FDROID_KEYPASS}
keydname: CN=Sonora repo, O=realmike0
make_current_version_link: false
EOC
# apksigner dagli Android build-tools (sui runner GitHub è già installato)
SIGNER=$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)
[ -z "$SIGNER" ] && { echo "apksigner non trovato"; exit 1; }
echo "apksigner: ${SIGNER}" >&2
echo "apksigner: ${SIGNER}" >> config.yml
chmod 600 config.yml keystore.p12
fdroid update
FP=$(keytool -list -v -keystore keystore.p12 -storetype PKCS12 -storepass "$FDROID_KEYPASS" -alias sonora-repo 2>/dev/null | grep -m1 'SHA256:' | sed 's/.*SHA256: *//' | tr -d ':' | tr 'A-F' 'a-f')
echo "Impronta del repository: $FP"
echo "fingerprint=$FP" > repo/.fingerprint
rm -f keystore.p12 config.yml
