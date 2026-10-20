#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
NDK="${ANDROID_NDK_HOME:-$(pwd)/.local/android-ndk-r27c}"
LLVM="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
mkdir -p android/runtime
for entry in 'arm64-v8a:aarch64-linux-android' 'armeabi-v7a:armv7a-linux-androideabi' 'x86_64:x86_64-linux-android' 'x86:i686-linux-android'; do
 abi=${entry%%:*}; triple=${entry#*:}; out=android/asr-deps/vosk-native-$abi; mkdir -p "$out"
 "$LLVM/${triple}26-clang" -shared -fPIC -O2 -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 \
  -Wl,-soname,liblecture_vosk.so android/native/vosk_jni.c -L"android/asr-deps/vosk/jni/$abi" -lvosk -o "$out/liblecture_vosk.so"
 "$LLVM/llvm-strip" "$out/liblecture_vosk.so"
done
