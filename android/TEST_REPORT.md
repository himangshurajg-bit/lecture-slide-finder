# Android 0.1.0-test.1 — validation report

This is a **prerelease, debuggable test APK**, not a Play Store or production release.

## Artifact

- Package: `io.github.himangshurajg.lecture`
- Label: `Lecture Finder (test)`
- Version: `0.1.0-test.1`, code 1
- Declared minimum Android 8.0/API 26, target/compile API 35
- Platform WebView hosts bundled production assets; no localhost, hosting or login required.
- No Internet, media, storage, camera or microphone permissions are requested.
- Android document picker is implemented for selected videos/transcripts; Android document saver is implemented for Markdown export. Their actual native runtime behavior remains untested in this release.

## Verified checks

- Production TypeScript/Vite build passed.
- 34 Vitest unit tests passed.
- Nine Python tests passed: four transcription helper tests and five Android manifest/source boundary tests.
- All 20 Playwright browser tests passed on the final full run: 16 existing behavior/style regressions and four packaged-origin/native-export simulations, across desktop Chromium and Pixel 7 browser emulation. The new checks cover actual Markdown, delayed success reporting and cancel-preserved notes. These are bridge mocks and browser emulation, not Android OS/document-provider tests. Earlier full-suite retries hit a leftover preview-port conflict and a server startup timeout; the final fresh full run passed without changing the assertions.
- Android Java compilation, DEX generation, APK resource packaging and alignment passed.
- Android Build Tools `apksigner verify` confirms APK Signature Schemes v2 and v3; `aapt dump badging` confirms package/version/minimum/target values. APK archive inspected for bundled UI and DEX; no fixture lecture videos or signing secrets are packaged.
- SHA-256 calculated from the built APK; the release includes `SHA256SUMS.txt`.

## Android runtime limitations

No native emulator install/launch, file-picker/video/save flow or physical-phone test was performed for this release. The shared emulator was occupied by another project's tests and was left untouched; launching a competing emulator was avoided. Build/signature/static/browser checks do not establish that every native flow works on a real Android device. Test with synthetic/non-sensitive media first, and report any launch, selection, playback or saving problem.

Automatic speech recognition still runs separately on a computer with the documented transcription helper. This APK imports a timestamped transcript; it does not generate one on the phone. Frame capture is a frame at the selected time, not slide OCR/recognition. Video codec support depends on Android/WebView. Notes and selections are session-only, so export before closing or switching files. OS process termination can lose unexported work.

The test signing state is retained outside the repository for compatible updates. A hardened release-signing/distribution process and broad device/version coverage remain future work.
