#!/usr/bin/env bash
# Builds Obtainium Wear (the phone app, arm64) and its Wear OS helper
# (wear_helper/), both signed with the same key so the watch data layer
# connects them.
#
# usage: tool/wear_build.sh <out dir>
# needs: flutter, gradle, apksigner (Android build-tools on PATH),
#        WEAR_KEYSTORE (path), WEAR_KEYSTORE_PASS_FILE (path), WEAR_KEY_ALIAS
# writes: <out>/ObtainiumWear-<version>-arm64.apk, <out>/ObtainiumWearHelper-<version>.apk,
#         their .sha256 files and <out>/version
set -euo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
out=$1
mkdir -p "$out"

upstream=$(grep -oP '^version: \K[^+]+' "$repo/pubspec.yaml")
build=$(grep -oP '^version: [^+]+\+\K\d+' "$repo/pubspec.yaml")
rev=$(tr -d '[:space:]' < "$repo/WEAR_REVISION")
ver="$upstream-wear$rev"
echo "$ver" > "$out/version"
echo "Obtainium $upstream -> Obtainium Wear $ver"

cd "$repo"
flutter pub get
# Same workaround as upstream's release.yml.
sed -i -e 's/-Wl,/-Wl,--build-id=none,/' "${PUB_CACHE:-$HOME/.pub-cache}"/hosted/*/jni-*/src/CMakeLists.txt || true
flutter build apk --flavor normal --split-per-abi --target-platform android-arm64 \
  --build-name "$ver" --build-number "$build"
phone=build/app/outputs/flutter-apk/app-arm64-v8a-normal-release.apk

# Minutes since the epoch: always higher than the last build.
code=$(( $(date +%s) / 60 ))
gradle --no-daemon -q -p "$repo/wear_helper" :app:assembleRelease \
  -PphoneApplicationId=dev.imranr.obtainium.wear -PhelperVersionName="$ver" -PhelperVersionCode="$code"
helper=wear_helper/app/build/outputs/apk/release/app-release-unsigned.apk

sign() {
  apksigner sign --ks "$WEAR_KEYSTORE" --ks-pass "file:$WEAR_KEYSTORE_PASS_FILE" \
    --ks-key-alias "$WEAR_KEY_ALIAS" --out "$2" "$1"
  apksigner verify --print-certs "$2" | grep 'SHA-256'
  (cd "$(dirname "$2")" && sha256sum "$(basename "$2")" > "$(basename "$2").sha256")
}
sign "$phone" "$out/ObtainiumWear-$ver-arm64.apk"
sign "$helper" "$out/ObtainiumWearHelper-$ver.apk"
ls -l "$out"
