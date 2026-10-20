# Lecture Finder — Android test APK

This is an Android Activity containing the locally bundled production web UI. It starts offline without a server or laptop. It preserves the light liquid-glass UI, touch ripples and reduced-motion support. It is **not** a remotely hosted website or a native speech-recognition engine.

## Install

Download `lecture-slide-finder-0.1.0-test.1.apk` from this repository's `android-v0.1.0-test.1` GitHub prerelease or the supplied chat attachment. Android 8.0/API 26 is the declared minimum; keep Android System WebView current (Chromium 120+ recommended). Testing on every supported Android release and a physical phone has not been performed.

1. Open the APK in Android's Files or download manager.
2. If Android asks, allow **Install unknown apps** for that source only, then install. This is sideloading, not a Play Store install; don't disable Play Protect.
3. Open **Lecture Finder (test)**. No sign-in or network connection is needed.
4. Use **Open lecture** to select a local, playable video. H.264/AAC MP4 is recommended. Codec support depends on the device's WebView.
5. Use **Import transcript** to select SRT, VTT or supported JSON. The picker intentionally allows all file types because subtitle files are inconsistently labelled by document providers; the parser validates contents and the 5 MB limit.
6. Search words, tap a timestamp, then save a passage with **+**. The preview captures the video frame at that time, not automatically recognized slides.
7. **Export notes** opens Android's **Save as** dialog. Choose a location and save `lecture-notes.md`. A successful save is reported only after the provider accepts the write. Cancelling/failing leaves the selected notes in the app.

Notes, transcript and video selection are session-only. Switching lectures/transcripts warns before discarding notes. The back button asks before closing. Background process termination, app restart or OS memory pressure can still clear the session; **export frequently**. Changing screen orientation does not intentionally recreate the Activity, but this is not a promise of durable note storage. Android's own picker/video controls keep system styling.

## Privacy and security boundary

- No `INTERNET`, camera, microphone, broad media or storage permissions. The app is packaged with its assets and cannot contact a backend.
- Videos/transcripts are read only after selection through Android's system document picker. Imported text is rendered as text, not HTML.
- The native document saver receives only explicitly exported Markdown; selecting a cloud document provider is the user's own choice. Use local storage for local-only notes.
- Backups are disabled. No persistent URI grant, advertising, telemetry, new accounts or remote ASR API.
- The WebView serves only bundled assets on a fixed synthetic HTTPS origin. Navigation to remote URLs, arbitrary file URLs and intent URLs is blocked. There is no `addJavascriptInterface`; export is a size-bounded, origin-checked prompt handled by the Activity.
- This **debuggable test build** enables WebView debugging for test tools. Use synthetic/non-sensitive materials when testing; it is not a hardened production signing/distribution setup. Signing keys/password are kept outside the repo and are never released. A different builder's key will require uninstalling this test package before installing theirs.

## Build from source (Linux)

Requirements: Node 22.12+/pnpm 11.9.0, JDK 17+, Python 3, `zip`, Android SDK platform **35** and Build Tools **35.0.0**. Android SDK terms/installations are the builder's responsibility. Existing project dependencies remain pinned in `pnpm-lock.yaml`; no extra JavaScript/native library was added.

```sh
pnpm install --frozen-lockfile
export ANDROID_HOME="$HOME/Android/Sdk" # adjust to your SDK
bash android/build-test-apk.sh
```

The script builds the static UI, packages resources with `aapt`, compiles Java, converts classes with `d8`, aligns and signs the APK with APK Signature Schemes v2/v3, then verifies the signature and calculates `SHA256SUMS.txt`. Outputs are in ignored `android/build/`. It does not upload, deploy or publish anything.

A per-project test key is created once in `$HOME/.android/lecture-slide-finder-signing/`, or `LECTURE_SIGNING_DIR` if set; protect and retain that directory to sign compatible updates. Do not commit it. A missing key with an existing password stops the build rather than silently breaking signing continuity. This is test signing only, not a production release-key solution. The APK contains no native architecture-specific code; its embedded WebView is supplied by Android.

## Validation

```sh
pnpm test
python3 -m unittest discover -s tests -p '*_test.py' -v
pnpm build
pnpm test:e2e
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose android/build/lecture-slide-finder-0.1.0-test.1.apk
$ANDROID_HOME/build-tools/35.0.0/aapt dump badging android/build/lecture-slide-finder-0.1.0-test.1.apk
```

`tests/android_test.py` checks source/manifest boundaries; the additional Playwright export tests simulate the native origin and receipt. Neither substitutes for Android runtime tests. See `TEST_REPORT.md` for the actual checks and remaining limits for the published artifact.
