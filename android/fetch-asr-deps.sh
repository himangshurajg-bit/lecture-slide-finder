#!/usr/bin/env bash
# Reproducible build-time downloads; the Android app itself has no network permission.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p android/asr-deps android/licenses
fetch(){ local path=$1 sha=$2 url=$3; if ! test -f "$path"; then curl -fL --max-time 180 -o "$path.partial" "$url"; mv "$path.partial" "$path"; fi; echo "$sha  $path" | sha256sum -c -; }
fetch android/asr-deps/vosk.aar ab2f8b91ac8051561aa325546b35fed9a68b36b8121bac5c6fb927525c4adfad https://repo1.maven.org/maven2/com/alphacephei/vosk-android/0.3.75/vosk-android-0.3.75.aar
fetch android/asr-deps/vosk-model-small-en-us-0.15.zip 30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498 https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
fetch android/asr-deps/vosk-model-small-hi-0.22.zip 7c50a10866889f0ac21d912c20537a055a597ed09fc1d3e5bcd798f9f0017e48 https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip
mkdir -p android/asr-deps/vosk; unzip -oq android/asr-deps/vosk.aar -d android/asr-deps/vosk
for abi in arm64-v8a armeabi-v7a x86_64 x86; do test -f "android/asr-deps/vosk-native-$abi/liblecture_vosk.so" || { bash android/build-vosk.sh; break; }; done
