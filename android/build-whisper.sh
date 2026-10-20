#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SOURCE="$(pwd)/.local/whisper.cpp"
NDK="${ANDROID_NDK_HOME:-$(pwd)/.local/android-ndk-r27c}"
for abi in arm64-v8a armeabi-v7a x86_64 x86; do
 cmake -S android/native -B "android/asr-deps/native-$abi" -G Ninja -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release -DWHISPER_SOURCE="$SOURCE" > "android/runtime/whisper-$abi-build.log" 2>&1
 cmake --build "android/asr-deps/native-$abi" --target lecture_asr -j 3 >> "android/runtime/whisper-$abi-build.log" 2>&1
 "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" "android/asr-deps/native-$abi/liblecture_asr.so"
done
