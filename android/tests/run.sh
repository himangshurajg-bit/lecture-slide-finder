#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
SDK=${ANDROID_HOME:-/home/user/workspace/.android-sdk}
BT=$SDK/build-tools/35.0.0
JAR=$SDK/platforms/android-35/android.jar
OUT=android/runtime/instrumentation
mkdir -p "$OUT/classes" "$OUT/dex"
"$BT/aapt" package -f -M android/tests/AndroidManifest.xml -A "${CHECK_FIXTURES:-android/runtime/release-fixtures}" -I "$JAR" -F "$OUT/raw.apk"
javac --release 8 -classpath "$JAR" -d "$OUT/classes" $(find android/tests/src -name '*.java')
"$BT/d8" --lib "$JAR" --min-api 26 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
cp "$OUT/raw.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KEYDIR=${LECTURE_SIGNING_DIR:-$HOME/.android/lecture-slide-finder-signing}
export LECTURE_SIGNING_PASSWORD="$(cat "$KEYDIR/password")"
"$BT/apksigner" sign --ks "$KEYDIR/test.keystore" --ks-key-alias lecture-test --ks-pass env:LECTURE_SIGNING_PASSWORD --out "$OUT/runtime-checks.apk" "$OUT/aligned.apk"
unset LECTURE_SIGNING_PASSWORD
if [ "${CHECK_BUILD_ONLY:-0}" = 1 ]; then
 echo "Built instrumentation APK only; no Android runtime result claimed."
 exit 0
fi
ADB="$SDK/platform-tools/adb -s emulator-5556"
$ADB install -r "$OUT/runtime-checks.apk"
$ADB shell am force-stop io.github.himangshurajg.lecture
$ADB shell am instrument -w -e segment "${CHECK_SEGMENT:-all}" -e language "${CHECK_LANGUAGE:-en}" io.github.himangshurajg.lecture.test/.${CHECK_CLASS:-RuntimeChecks}
