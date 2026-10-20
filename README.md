# Lecture — slide finder

A local-first lecture player with transcript search, a video-frame preview and timestamped study notes. A minimal, frosted-glass interface for desktop and Android browsers. No account, paid AI API or media-upload server.

![Desktop interface](docs/desktop-chromium.png)

## What works in v0.1

- Open a video from your device; H.264/AAC MP4 is recommended. Playback depends on the browser's codecs.
- Import timestamped **SRT, WebVTT or JSON**, or generate JSON using the included **local Python speech-recognition tool**.
- Search transcript words (case-insensitive, all query words must occur in a passage). This is keyword search, not semantic AI search.
- Tap a timestamp to seek the video and capture its frame nearby. It shows the slide **if the slide is visible at that moment**; there is no automatic slide recognition, OCR, deck extraction or slide-change detection yet.
- Save passages and export a Markdown file with timestamps. Notes stay in the tab; **export before closing**. Replacing a lecture/transcript warns before clearing selected notes.
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

### Android

The app is an **Android-compatible responsive web/PWA**, **not a native APK**. Open it in a current Chrome browser from a static HTTPS host. Use its install prompt (when offered) or Chrome's **Add to Home screen / Install app** menu. After a complete online visit the app shell can reopen offline. Pick your video and transcript from the device's file picker.

Automatic speech recognition currently runs on a separate computer via the Python tool, **not on Android**. Transfer its JSON file yourself and import it. Existing SRT/VTT files work directly on Android. Local `localhost` works on the development computer; `http://<computer-LAN-IP>` may open on a phone with `pnpm dev --host 0.0.0.0`, but installation/offline service workers require a secure context (HTTPS, or the device's own localhost). Only expose the development server on a trusted network.

Android tests use Playwright's Pixel 7 emulation in Chromium. They do **not** establish physical Android device, vendor file-picker, low-memory playback or installation compatibility. No APK or Play Store release is included.

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

Imports are limited to 5 MiB and 20,000 nonempty segments; time ranges must be finite, increasing within each cue and no longer than seven days. Results render the first 150 matches; narrow the query for longer transcripts. Import errors preserve the previous transcript.

## Privacy and safety

- Imported videos use browser object URLs. Transcripts and notes are in memory, not uploaded, stored in a database or synchronized.
- The service worker caches public app assets only, not imported blobs, transcripts or notes. Clear site data to remove the cached shell.
- The browser still requests the app from wherever it is hosted; speech model/dependency installation contacts their distributors. “Local-first” does not mean zero network requests during installation.
- User transcript text is rendered as text, not executable HTML. Markdown exports escape HTML/formatting characters. Keep media dependencies/browser updated and only open files you trust.
- Reopening the tab does not restore media or notes. Export selected notes before closing. No private lecture samples are included.

## Checks

```sh
pnpm build
pnpm test
pnpm exec playwright install --with-deps chromium
pnpm test:e2e
```

The E2E suite starts a production preview server automatically; build first. It checks import, search, seek, actual video-frame capture, Markdown download, bad inputs, hostile text, responsive width and offline reopening. Original synthetic physics slides in `tests/fixtures/lecture.mp4` are the browser fixture. Screenshots are regenerated under `docs/` by the E2E test.

See [docs/VALIDATION.md](docs/VALIDATION.md) for the measured validation and limits. The workflow checks web build/tests; model downloads and ASR accuracy are not CI requirements.

## Structure and credits

- `src/core.ts`: bounded parsing, literal search and safe Markdown export.
- `src/main.ts` and `src/style.css`: native DOM UI, playback and frame capture; no UI runtime dependencies.
- `public/`: manifest, icons and app-shell service worker.
- `tools/transcribe.py`: optional local speech transcription.
- `tests/`: unit cases and browser integration checks.

Original implementation and fixture artwork. Dependency credits/license metadata are in [THIRD_PARTY.md](THIRD_PARTY.md); no competitor source or private media was copied. No project-wide open-source license has been selected by the owner yet. A public GitHub repository alone is not an open-source license grant.
