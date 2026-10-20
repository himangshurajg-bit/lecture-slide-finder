#!/usr/bin/env bash
# Build candidate only; publication requires runtime validation. Retains original signing identity.
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
for tool in aapt d8 zipalign apksigner; do test -x "$BT/$tool" || { echo "Missing Android Build Tools 35.0.0: $tool"; exit 1; }; done
test -f "$JAR" || { echo 'Install SDK platform android-35 first.'; exit 1; }
KEYDIR="${LECTURE_SIGNING_DIR:-$HOME/.android/lecture-slide-finder-signing}"
mkdir -p "$KEYDIR"; chmod 700 "$KEYDIR"
if [[ ! -f "$KEYDIR/test.keystore" ]]; then
  test ! -f "$KEYDIR/password" || { echo 'Signing state incomplete; restore the matching keystore before continuing.'; exit 1; }
  (umask 077; python3 -c 'import secrets; print(secrets.token_urlsafe(32))' > "$KEYDIR/password")
  keytool -genkeypair -keystore "$KEYDIR/test.keystore" -alias lecture-test -storepass:file "$KEYDIR/password" -keypass:file "$KEYDIR/password" -keyalg RSA -keysize 3072 -validity 3650 -dname "CN=Lecture Finder Test Build" >/dev/null 2>&1
fi
export LECTURE_SIGNING_PASSWORD="$(cat "$KEYDIR/password")"
bash android/fetch-asr-deps.sh
pnpm build
OUT=android/build
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/assets"
cp -R dist/. "$OUT/assets/"
mkdir -p "$OUT/assets/asr" "$OUT/assets/licenses" "$OUT/lib"
cp android/asr-deps/vosk-model-small-*.zip "$OUT/assets/asr/"
cp android/licenses/VOSK-APACHE-2.0.txt android/licenses/VOSK-MODELS-NOTICE.txt "$OUT/assets/licenses/"
for abi in arm64-v8a armeabi-v7a x86_64 x86; do
 mkdir -p "$OUT/lib/$abi"
 cp "android/asr-deps/vosk/jni/$abi/libvosk.so" "android/asr-deps/vosk-native-$abi/liblecture_vosk.so" "$OUT/lib/$abi/"
done
# No service worker is needed for bundled assets; no remote shell or private fixture media.
rm -f "$OUT/assets/sw.js"
"$BT/aapt" package -f -M android/app/src/main/AndroidManifest.xml -S android/app/src/main/res -A "$OUT/assets" -I "$JAR" -F "$OUT/unsigned.apk"
javac -encoding UTF-8 --release 8 -classpath "$JAR" -d "$OUT/classes" $(find android/app/src/main/java -name '*.java')
"$BT/d8" --lib "$JAR" --min-api 26 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q ../unsigned.apk classes*.dex)
(cd "$OUT" && zip -qr unsigned.apk lib)
"$BT/zipalign" -f -P 16 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KEYDIR/test.keystore" --ks-key-alias lecture-test --ks-pass env:LECTURE_SIGNING_PASSWORD --key-pass env:LECTURE_SIGNING_PASSWORD --out "$OUT/lecture-slide-finder-1.00.0.00-stable.apk" "$OUT/aligned.apk"
unset LECTURE_SIGNING_PASSWORD
"$BT/apksigner" verify --verbose --print-certs "$OUT/lecture-slide-finder-1.00.0.00-stable.apk"
(cd "$OUT" && sha256sum lecture-slide-finder-1.00.0.00-stable.apk > SHA256SUMS.txt)
echo "APK: $OUT/lecture-slide-finder-1.00.0.00-stable.apk"
