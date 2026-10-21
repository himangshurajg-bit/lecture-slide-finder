# Lecture — slide finder

A local-first lecture player with transcript search, a video-frame preview and timestamped study notes. A minimal, frosted-glass interface for desktop and Android browsers, plus a bundled Android APK. No account, paid AI API or media-upload server.

![Desktop interface](docs/desktop-chromium.png)

## Android 16 stable release

[Download the signed 1.00.0.00 stable APK](https://github.com/himangshurajg-bit/lecture-slide-finder/releases/tag/android-v1.00.0.00-stable). All 12 covered Android16 emulator qualification checks passed, along with normal CI. See the [test matrix and known limits](docs/ANDROID16_STATUS.md). Physical-phone performance and older-version compatibility remain unqualified.

- Open a video from your device; H.264/AAC MP4 is recommended. Playback depends on the browser's codecs.
- Import timestamped **SRT, WebVTT or JSON**. The stable Android APK can generate speech transcripts offline on the phone in English or Hindi; the optional **local Python speech-recognition tool** remains available for computer/browser workflows.
- Search transcript words (case-insensitive, all query words must occur in a passage). This is keyword search, not semantic AI search.
- Tap a timestamp to seek the video and capture its frame nearby. It shows the slide **if the slide is visible at that moment**; the separate Android frame-study workflow adds visible-text OCR, not semantic slide recognition, deck extraction or slide-change detection.
- Save passages and export Markdown with timestamps. Current source restores validated transcripts, notes and search locally; select the same video again after reopening. Replacing a lecture/transcript warns before clearing selected notes. Export a separate backup; storage can fail or be cleared.
- Responsive touch-friendly layout, labelled controls, keyboard focus, reduced-motion support and a cacheable PWA shell.

## Run locally

Install Node.js **22.12+** (Node 24 recommended) and **pnpm 11.9.0**. Then:

```sh
git clone https://github.com/himangshurajg-bit/lecture-slide-finder.git
cd lecture-slide-finder
pnpm install --frozen-lockfile
pnpm dev
```

Open the local address Vite prints. For the production build:

```sh
pnpm build
pnpm preview
```

There is no backend. `dist/` is the static site. A relative base supports hosting under a subdirectory. No public deployment is configured by this project.

### Generate a real transcript on a computer

Install Python 3.10+ and [uv](https://docs.astral.sh/uv/getting-started/installation/), then run:

```sh
uv run tools/transcribe.py /path/to/lecture.mp4 --model tiny --language en
# Produces /path/to/lecture.transcript.json. Import that file in the app.
```

The script uses `faster-whisper` on CPU with int8 computation. `uv` installs its isolated dependencies. First use downloads the selected speech model; the audio/video is processed locally, not sent to an AI API. Cached models can run without another model download. Allow local disk space, memory and processing time; larger models are slower and need more resources. `tiny` trades accuracy for a smaller download. Omit `--language` to detect, or supply a supported ISO language code. Test quality in your language.

```sh
uv run tools/transcribe.py lecture.mp4 --model base --language hi --output lecture-hi.json
```

The tool refuses to overwrite an existing transcript unless `--force` is supplied, and never allows output to replace the media input. **Review recognition carefully**: accents, noise, technical terms and even synthetic speech can be misheard. A successful run is not proof of transcript accuracy. PyAV is pinned to 16.1.0 for compatibility with faster-whisper 1.2.1. FFmpeg is useful for converting unsupported media but is not a separate CLI prerequisite for this PyAV-based tool.

### Android browser/PWA

The web version is an **Android-compatible responsive web/PWA**. A separate [bundled stable APK](#android-apk-test-build) is also available. Open it in a current Chrome browser from a static HTTPS host. Use its install prompt (when offered) or Chrome's **Add to Home screen / Install app** menu. After a complete online visit the app shell can reopen offline. Pick your video and transcript from the device's file picker.

In the browser/PWA, automatic speech recognition runs on a separate computer via the Python tool, **not inside the Android browser**. Transfer its JSON file yourself and import it. Existing SRT/VTT files work directly in the browser. The separate stable APK bundles offline English/Hindi recognition on the phone; that native feature is not provided by the PWA. Local `localhost` works on the development computer; `http://<computer-LAN-IP>` may open on a phone with `pnpm dev --host 0.0.0.0`, but installation/offline service workers require a secure context (HTTPS, or the device's own localhost). Only expose the development server on a trusted network.

Browser tests use Playwright's Pixel 7 emulation in Chromium. They do **not** establish physical Android device, vendor file-picker, low-memory playback or APK installation compatibility. The stable APK has separate Android16 emulator evidence; no Play Store release is included.

## Transcript formats

JSON may be an array or `{ "segments": [...] }`; times are seconds:

```json
{
  "segments": [
    { "start": 0, "end": 4.5, "text": "A force changes motion." },
    { "start": 4.5, "end": 8, "text": "Force equals mass times acceleration." }
  ]
}
```

SRT uses `HH:MM:SS,mmm`; VTT supports `MM:SS.mmm` or `HH:MM:SS.mmm`. Cue identifiers/settings and simple subtitle markup are supported. Blank-line-separated cue blocks are required. Full WebVTT rendering/styling, subtitle overlays and speaker diarization are not implemented.

Imports are limited to 5 MiB and 20,000 nonempty segments; time ranges must be finite, increasing within each cue and no longer than seven days. Results use 150-passage pages with Previous/Next controls and exact match/page counts, so later matches remain reachable without rendering an entire long transcript. Changing the query or transcript returns to the first page; saved notes stay selected across pages. Import errors preserve the previous transcript.

## Privacy and safety

- Imported videos use browser object URLs and are not uploaded. Current source saves validated transcript, notes and search in local storage; frame analysis is saved separately. Nothing synchronizes to a server.
- The service worker caches public app assets only, not imported blobs, transcripts or notes. Clear site data to remove the cached shell.
- The browser still requests the app from wherever it is hosted; speech model/dependency installation contacts their distributors. “Local-first” does not mean zero network requests during installation.
- User transcript text is rendered as text, not executable HTML. Markdown exports escape HTML/formatting characters. Keep media dependencies/browser updated and only open files you trust.
- Reopening restores saved text when local storage is available, not access to the video. Select the same video again. Export important notes before clearing app/site data. No private lecture samples are included.

## Checks

```sh
pnpm build
pnpm test
pnpm exec playwright install --with-deps chromium
pnpm test:e2e
```

The E2E suite starts a production preview server automatically; build first. It checks import, search, seek, actual video-frame capture, Markdown download, bad inputs, hostile text, responsive width and offline reopening. Original synthetic physics slides in `tests/fixtures/lecture.mp4` are the browser fixture. Screenshots are regenerated under `docs/` by the E2E test.

See [docs/VALIDATION.md](docs/VALIDATION.md) for the measured validation and limits. The normal workflow checks web build/tests. The separate Android16 qualification workflow covers bundled speech-engine and installed-app workflows on synthetic/public fixtures; neither workflow establishes broad lecture-domain ASR accuracy.

## Structure and credits

- `src/core.ts`: bounded parsing, literal search and safe Markdown export.
- `src/main.ts` and `src/style.css`: native DOM UI, playback and frame capture; Tesseract.js supplies the separate offline visible-text OCR.
- `public/`: manifest, icons and app-shell service worker.
- `tools/transcribe.py`: optional local speech transcription.
- `tests/`: unit cases and browser integration checks.

Original implementation and fixture artwork. Dependency credits/license metadata are in [THIRD_PARTY.md](THIRD_PARTY.md); no competitor source or private media was copied. No project-wide open-source license has been selected by the owner yet. A public GitHub repository alone is not an open-source license grant.

## Light liquid-glass interface

The current interface uses a light pearl/mint background, translucent panels, rounded rim highlights, ambient lighting, spring/elastic touch responses and varied transitions. Motion intensity can be reduced; system **Reduce motion** disables nonessential effects. These are rendered glass/depth effects, not physical refraction. Browser screenshots establish browser appearance; hosted Android16 runtime screenshots/tests do not establish physical-phone speed or battery performance.

<a id="android-apk-test-build"></a>

## Android APK (stable release)

The repository now also includes a **bundled, offline Android app**, not a link to a development server. See [Android installation, build and privacy notes](android/README.md). Android 8.0+ is declared, with an up-to-date Android System WebView required. Only emulator/device versions actually exercised in the test report should be treated as tested.

The APK imports videos/transcripts through Android's document picker and exports Markdown through **Save as**. No Internet or broad storage permission is requested. The older public test build has no on-phone recognition. The signed **1.00.0.00 stable** release includes bundled offline English/Hindi Vosk recognition, playback speed, selected-passage repeat and the frame-study/OCR workflow below. All 12 covered Android16 emulator checks passed, including native export readback, both speech-screen workflows, active cancellation and preservation of existing transcripts/notes. This is not a physical-phone or broad lecture-accuracy qualification; older Android versions remain deferred. Export important notes as a separate backup. Distribution is through GitHub Releases, not the Play Store.

## Frame gallery and visible slide points

The stable Android APK includes one **All frames · gallery** entry. It reads consecutive output frames from the selected video's decoder, four at a time, preserving each output timestamp and frame index. This is not sparse keyframe sampling. Tap a thumbnail for an exact decoded-image view and single-frame stepping within the retained pages. The ordinary player seek is approximate and is labeled separately. At most four pages (16 thumbnails) remain in memory; restart from the first frame to revisit evicted pages. No guessed FPS index or whole-video image cache is created. A codec/provider error stops the traversal and prevents a complete-coverage claim.

**Read every decoded frame** runs bundled offline Tesseract OCR in English, Hindi or both. It converts only visible lines into timestamped bullet points, groups adjacent identical OCR output, supports search/paged browsing, preserves original OCR beside editable corrections, saves text locally and exports Markdown with frame/time sources and explicit partial/complete coverage. OCR is not the spoken transcript, an AI summary, or verified equations. Images are at most 1280 px, so small type, handwriting, formulas and low-quality slides can be missed. Blank/unrecognized frames still count as checked but add no invented point. OCR of every frame can take much longer than video playback and consume battery. Cancellation or leaving the app keeps completed points and does not claim a full pass. Analysis intentionally stops at 2,000 unique text groups or 1,000,000 stored original-plus-edited characters; it does not silently discard content to claim completion. Export a partial study before changing the video.

The native sequential decoder requires Android's codec to expose YUV frames. H.264 MP4 is the tested fixture format; other codecs/devices can reject it. Browser/PWA mode can OCR the current displayed picture only and explicitly does not claim exact frame indexing. Browser OCR assets are fetched from the app's own origin, so an installed APK is the fully bundled no-network form; ordinary service-worker shell caching does not promise that every OCR asset is cached.

The OCR assets add about 13 MiB uncompressed. Tesseract.js 6.0.1, matching core and pinned `tessdata_fast` English/Hindi models are bundled with licenses and checksums under `public/ocr/`. No model-download account, paid API, remote lecture upload or computer is required at runtime. Source generation uses `tools/make-frame-fixture.py`, which creates original six-frame English/Hindi slides for tests.

The stable release passed the covered Android16 native qualification, not merely browser tests or APK compilation. See the [qualification matrix](docs/ANDROID16_STATUS.md) for exact scope. Physical-phone performance, long-lecture accuracy and older-version compatibility remain separate, unestablished checks.
