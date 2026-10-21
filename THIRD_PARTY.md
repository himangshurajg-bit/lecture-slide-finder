# Third-party dependencies

This app uses upstream packages rather than copying a competitor implementation. Original synthetic slide fixture, icons and app UI were created for this repository. Dependencies keep their own licenses; this file does not relicense them.

## Web build and test dependencies

Metadata from `pnpm licenses list --json` for the locked install. Full upstream license texts are distributed in the installed packages.

| Package | Version | License |
| --- | --- | --- |
| @playwright/test | 1.64.1 | Apache-2.0 |
| detect-libc | 2.1.2 | Apache-2.0 |
| expect-type | 1.4.0 | Apache-2.0 |
| playwright | 1.64.1 | Apache-2.0 |
| playwright-core | 1.64.1 | Apache-2.0 |
| typescript | 5.9.3 | Apache-2.0 |
| source-map-js | 1.2.2 | BSD-3-Clause |
| picocolors | 1.1.1 | ISC |
| @jridgewell/resolve-uri | 3.1.2 | MIT |
| @jridgewell/sourcemap-codec | 1.6.0 | MIT |
| @jridgewell/trace-mapping | 0.3.31 | MIT |
| @oxc-project/types | 0.154.0 | MIT |
| @rolldown/binding-linux-x64-gnu | 1.2.14 | MIT |
| @rolldown/pluginutils | 1.0.1 | MIT |
| @types/chai | 5.2.3 | MIT |
| @types/deep-eql | 4.0.2 | MIT |
| @types/estree | 1.0.9 | MIT |
| @vitest/mocker | 5.0.5 | MIT |
| @vitest/spy | 5.0.5 | MIT |
| assertion-error | 2.0.1 | MIT |
| chai | 6.3.0 | MIT |
| es-module-lexer | 2.3.2 | MIT |
| estree-walker | 3.0.3 | MIT |
| fdir | 6.5.0 | MIT |
| magic-string | 1.4.3 | MIT |
| nanoid | 3.3.20 | MIT |
| obug | 3.0.0 | MIT |
| picomatch | 4.0.7 | MIT |
| postcss | 8.5.31 | MIT |
| rolldown | 1.2.14 | MIT |
| std-env | 4.3.0 | MIT |
| tinybench | 6.2.2 | MIT |
| tinyexec | 1.3.1 | MIT |
| tinyglobby | 0.2.17 | MIT |
| vite | 8.3.4 | MIT |
| vitest | 5.0.5 | MIT |
| why-is-node-running | 3.2.1 | MIT |
| lightningcss | 1.33.0 | MPL-2.0 |
| lightningcss-linux-x64-gnu | 1.33.0 | MPL-2.0 |

## Optional speech tool

- [faster-whisper](https://github.com/SYSTRAN/faster-whisper) 1.2.1: MIT.
- [PyAV](https://github.com/PyAV-Org/PyAV) 16.1.0: BSD-3-Clause; packaged FFmpeg components have their own licenses.
- [huggingface-hub](https://github.com/huggingface/huggingface_hub) 0.36.0: Apache-2.0.
- Downloaded speech models and transitive binary dependencies have their own licenses and model cards. Check those before redistribution; no model weights are committed in this repository.

## Android test package

The Android host uses platform Activity, WebView and Storage Access Framework APIs, with no bundled AndroidX/Capacitor/Cordova library. Android SDK build tools compile/package the app; they and the operating-system WebView are not copied into the APK. Existing web dependency notices above continue to apply to its bundled UI. No upstream application code was copied for this host. No project license was selected by this change.

## Bundled Android speech and OCR assets

The stable APK includes the following upstream components; it does not copy an upstream application UI or change their licenses.

- [Vosk Android](https://github.com/alphacep/vosk-api) 0.3.75: Apache-2.0. The small English US 0.15 and Hindi 0.22 models are Apache-2.0 according to their upstream model listings. Runtime/model notices are retained in `android/licenses/VOSK-APACHE-2.0.txt` and `android/licenses/VOSK-MODELS-NOTICE.txt`; build downloads have pinned checksums in `android/fetch-asr-deps.sh`.
- [Tesseract.js](https://github.com/naptha/tesseract.js) 6.0.1 and bundled Tesseract core: Apache-2.0. English/Hindi `tessdata_fast` assets retain their Apache-2.0 license. Full notices, including component/worker dependencies, are in `public/ocr/licenses/`; asset digests are in `public/ocr/SHA256SUMS` and the model revision is in `public/ocr/lang/REVISION`.
- Retained Whisper/GGML license files document earlier experiments, not an additional speech engine shipped by the current Vosk-based APK. Check `android/fetch-asr-deps.sh` and the actual APK contents when assessing a rebuilt artifact.
