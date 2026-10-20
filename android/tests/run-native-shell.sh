#!/usr/bin/env bash
# Isolated JNI smoke test, not installed-app pipeline or language-quality certification.
set -euo pipefail
cd "$(dirname "$0")/../.."
SDK=${ANDROID_HOME:-/home/user/workspace/.android-sdk}
ADB=("$SDK/platform-tools/adb" -s emulator-5556)
OUT=android/runtime/shell-checks
mkdir -p "$OUT/classes" "$OUT/dex"
javac --release 8 -classpath "$SDK/platforms/android-35/android.jar" -d "$OUT/classes" \
 android/app/src/main/java/io/github/himangshurajg/lecture/WhisperEngine.java \
 android/tests/shell/NativeShellChecks.java
"$SDK/build-tools/35.0.0/d8" --lib "$SDK/platforms/android-35/android.jar" --min-api 26 \
 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q ../checks.jar classes.dex)
# Intentionally requires an already booted, isolated x86 test emulator.
[ "$(timeout 5 "${ADB[@]}" shell getprop sys.boot_completed | tr -d '\r')" = 1 ]
"${ADB[@]}" shell mkdir -p /data/local/tmp/lecture-asr
"${ADB[@]}" push "$OUT/checks.jar" android/asr-deps/native-x86/liblecture_asr.so \
 android/asr-deps/ggml-tiny.bin android/runtime/asr-fixtures/en.wav /data/local/tmp/lecture-asr/
"${ADB[@]}" shell 'CLASSPATH=/data/local/tmp/lecture-asr/checks.jar app_process -Djava.library.path=/data/local/tmp/lecture-asr /system/bin io.github.himangshurajg.lecture.NativeShellChecks /data/local/tmp/lecture-asr/ggml-tiny.bin /data/local/tmp/lecture-asr/en.wav' \
 | tee "$OUT/run.log"
