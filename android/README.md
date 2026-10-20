# Lecture Finder — Android APK

This is an Android Activity containing the locally bundled production web UI. It starts offline without a server or laptop. It preserves the light liquid-glass UI, touch ripples and reduced-motion support. It is **not** a remotely hosted website. The local unpublished candidate now adds Android audio decoding and bundled English/Hindi Vosk recognition. The first stable release is scoped to Android 16/API 36. Actual installed-app workflows, recognition quality, cancellation and lifecycle checks on Android 16 remain release gates; other Android versions are deferred until after that first release, not considered tested. Assamese support was removed from this release at the owner’s request; no Assamese model is bundled.

## Install

Download `lecture-slide-finder-1.00.0.00-stable.apk` from the matching GitHub release, when published. The manifest still declares Android 8.0/API 26 as its minimum, but this is not a compatibility qualification: the first release will be validated for Android 16/API 36 only. Keep Android System WebView current (Chromium 120+ recommended). Older versions and physical-phone performance remain untested. The build currently compiles/targets SDK 35; runtime testing on version 16 is a separate requirement.

1. Open the APK in Android's Files or download manager.
2. If Android asks, allow **Install unknown apps** for that source only, then install. This is sideloading, not a Play Store install; don't disable Play Protect.
3. Open **Lecture Finder**. No sign-in or network connection is needed.
4. Use **Open lecture** to select a local, playable video. H.264/AAC MP4 is recommended. Codec support depends on the device's WebView.
5. In the experimental candidate, select a language and **Transcribe on phone** to try local transcription without a computer, account, network or paid API. Small English US 0.15 and Hindi 0.22 Vosk models are bundled (about 82 MiB combined compressed), and the selected model is extracted on first use; allow about 250 MiB additional free storage for both. Select the recording’s main language. Recognition can be inaccurate, especially names, technical terms or mixed speech. Keep the app open; cancellation should preserve previous notes and transcripts, but native cancellation/lifecycle validation is not complete. Alternatively use **Import transcript** to select SRT, VTT or supported JSON. The picker intentionally allows all file types because subtitle files are inconsistently labelled by document providers; the parser validates contents and the 5 MB limit.
6. Search words, tap a timestamp, then save a passage with **+**. The preview captures the video frame at that time, not automatically recognized slides. Playback speed can be changed locally. **Repeat passage** loops the selected timestamped passage; replacing the transcript or video stops the loop.
7. **Export notes** opens Android's **Save as** dialog. Choose a location and save `lecture-notes.md`. A successful save is reported only after the provider accepts the write. Cancelling/failing leaves the selected notes in the app.

Transcripts, selected notes and search text are validated and saved locally on the device. Reopening restores them, but you must select the same video again to play or capture frames: video access is not retained across restarts. Switching lectures/transcripts warns before discarding notes. **Clear saved session** asks before deleting local state. Export important notes as a separate copy; clearing app data, uninstalling, storage failures or device loss can remove local state. Android's picker/video controls keep system styling.

## Privacy and security boundary

- No `INTERNET`, camera, microphone, broad media or storage permissions. The app is packaged with its assets and cannot contact a backend.
- Videos/transcripts are read only after selection through Android's system document picker. Imported text is rendered as text, not HTML.
- The native document saver receives only explicitly exported Markdown; selecting a cloud document provider is the user's own choice. Use local storage for local-only notes.
- Backups are disabled. No persistent URI grant, advertising, telemetry, new accounts or remote ASR API.
- The WebView serves only bundled assets on a fixed synthetic HTTPS origin. Navigation to remote URLs, arbitrary file URLs and intent URLs is blocked. There is no `addJavascriptInterface`; export is a size-bounded, origin-checked prompt handled by the Activity.
- This build is **non-debuggable** and disables WebView debugging. The separate instrumentation APK used for validation is not distributed with the app. Signing keys/password are kept outside the repo and are never released. A different builder's key will require uninstalling this test package before installing theirs.

## Build from source (Linux)

Requirements: Node 22.12+/pnpm 11.9.0, JDK 17+, Python 3, `zip`, Android SDK platform **35** and Build Tools **35.0.0**. Android SDK terms/installations are the builder's responsibility. Dependencies remain pinned in `pnpm-lock.yaml`; the frame-study candidate adds Tesseract.js and bundled offline OCR assets (about 13 MiB uncompressed). The unpublished ASR candidate requires Android NDK r27c to build a small C/JNI bridge for arm64-v8a, armeabi-v7a, x86 and x86_64. Vosk Android 0.3.75 and the small English/Hindi models are fetched at build time with pinned SHA-256 checks. The app uses no JNA or remote model downloads. Model/runtime notices are in `android/licenses/`.

```sh
pnpm install --frozen-lockfile
export ANDROID_HOME="$HOME/Android/Sdk" # adjust to your SDK
bash android/fetch-asr-deps.sh
bash android/build-test-apk.sh
```

The script builds the static UI, packages resources with `aapt`, compiles Java, converts classes with `d8`, aligns and signs the APK with APK Signature Schemes v2/v3, then verifies the signature and calculates `SHA256SUMS.txt`. Outputs are in ignored `android/build/`. It does not upload, deploy or publish anything.

A per-project test key is created once in `$HOME/.android/lecture-slide-finder-signing/`, or `LECTURE_SIGNING_DIR` if set; protect and retain that directory to sign compatible updates. Do not commit it. A missing key with an existing password stops the build rather than silently breaking signing continuity. The retained self-signed project key has a legacy test alias/certificate name; the release candidate is non-debuggable. There is no Play App Signing enrollment or Play Store publication. The unpublished candidate includes architecture-specific Vosk native libraries and a small direct JNI bridge; its embedded WebView is supplied by Android. The existing public test APK is a different, older artifact.

## Validation

```sh
pnpm test
python3 -m unittest discover -s tests -p '*_test.py' -v
pnpm build
pnpm test:e2e
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose android/build/lecture-slide-finder-1.00.0.00-stable.apk
$ANDROID_HOME/build-tools/35.0.0/aapt dump badging android/build/lecture-slide-finder-1.00.0.00-stable.apk
```

`tests/android_test.py` checks source/manifest boundaries; the additional Playwright export tests simulate the native origin and receipt. Neither substitutes for Android runtime tests. See `TEST_REPORT.md` for the actual checks and remaining limits for this unpublished candidate.

### Candidate all-frame / visible-text workflow
Open a local lecture, expand **All frames · gallery**, and choose **Open from first frame**. Each page contains up to four consecutive decoded output frames, with exact decoder timestamps, not sampled keyframes. Tap an image to inspect it and step through retained images. Four pages are retained; restart to revisit older evicted pages. Ordinary playback seeking may display a nearby frame and does not substitute for that exact-image preview.

Choose English, Hindi or both, then **Read every decoded frame** for source-linked visible bullet points. This is fully offline in the APK, using bundled OCR assets. Search, correct and export those points separately from spoken-transcript notes. Full coverage is claimed only when the decoder reaches end-of-stream and every returned frame has gone through OCR. A completed pass says nothing about OCR correctness. Review tiny type, equations, handwriting and Hindi against the original image. Every-frame OCR is expensive; allow more time than playback and substantial battery. Work stops on cancellation/backgrounding, decode/OCR failure or bounded text storage (2,000 text groups / 1,000,000 original+edited characters), retaining a clearly partial result. No higher minimum RAM or hardware specification has been asserted without device testing.
