# Validation record — 20 October 2026

This is measured first-version validation, not a guarantee for all devices or media.

## Environment and successful checks

- Linux x86-64; Node 24.20.0, pnpm 11.9.0.
- `pnpm install --frozen-lockfile`: locked dependency installation.
- `pnpm build`: TypeScript checks and Vite production bundle.
- `pnpm test`: **34 passing cases**. SRT/VTT/JSON parsing, timestamp ranges, data/segment size limits, keyword semantics and Markdown escaping.
- `python -m unittest discover -s tests -p '*_test.py' -v`: **4 passing cases**. Missing input, invalid model, refusing media overwrite and refusing existing transcript overwrite. These do not load a speech model.
- `pnpm test:e2e`: **8 passing cases**, four each in desktop Chromium and Pixel 7 Android emulation. Real production preview; local MP4 plus transcript import; search; seeking; decoded frame pixels; note export; bad import preserving data; hostile HTML rendered as text; no-match/no-video errors; width checks; offline shell reopening; and confirmation before discarding notes.
- Visually inspected full-page desktop/mobile captures in this directory. These are synthetic fixture content, not a person's lectures.
- `uv run tools/transcribe.py .local/speech.wav --language en --output .local/final-recognition.json`: actual CPU int8 `tiny` model inference on original synthesized English speech generated **3 timestamped segments**. This is not a mocked output. Speech and output are local validation scratch files, not committed media from a user. Recognition made mistakes, including mishearing “Newton's second law”; accuracy needs human review. This is a functional pipeline test, not an accuracy benchmark.

## Problems encountered and resolved

- The environment's `npm` executable crashed before dependency installation. `pnpm` provided a working installation/build path; the README and lockfile consistently use it.
- Chromium initially lacked Linux system libraries. `pnpm exec playwright install-deps chromium` installed them and browser tests then ran.
- Initial faster-whisper inference with a newer unconstrained PyAV failed with `metadata_errors`. Pinning PyAV 16.1.0 resolved that actual test failure. The speech script pins compatible versions rather than pretending the failed run succeeded.
- Imported transcript/media replacement could silently discard selected notes in the initial implementation. Confirmation now protects notes; desktop and mobile-emulated regression checks cover both cancel and accept.

## What is not established

- No physical Android phone, native APK, device installation, vendor file picker or low-memory media stress test. Responsive Chromium emulation is the supported evidence.
- No on-device Android ASR. Use an existing transcript or the separate computer-side local generator.
- No automatic slide recognition, OCR, semantic search, model accuracy benchmark, speaker detection or exhaustive format compatibility.
- No deployed live URL. Static deployment/account settings were not changed.
- GitHub-hosted workflow results, if present, are separate from the local checks above; adding a workflow is not a passing remote run.

## Light liquid-glass refinement — 20 October 2026

- Replaced the dark treatment with light translucent surfaces, soft mint/blue lighting,
  curved inset highlights and a still water-droplet accent. This is a CSS depth
  treatment, not physical optical refraction.
- Added bounded 650 ms tap rings and short transform/opacity transitions; no continuous
  animation, WebGL, animation library or extra dependency. At most one ring per
  control, cleaned up after animation or 800 ms; Reduce motion skips/removes rings
  and disables all CSS animations/transitions.
- `pnpm build`: passed TypeScript and Vite production build.
- `pnpm test`: all **34 unit tests** passed.
- `python -m unittest discover -s tests -p '*_test.py' -v`: all **4 CLI tests** passed.
- `pnpm test:e2e`: all **14 browser tests** passed across desktop Chromium and
  Pixel 7 emulation. Existing import/search/seek/frame/export, invalid-input,
  hostile-text, offline and note-loss checks remain passing. New checks cover
  light surfaces, bounded repeated tap rings, keyboard search/focus, reduced-motion
  behavior, and reachable controls/no horizontal overflow at 320 CSS pixels.
- Visually inspected new desktop and Android-emulated full-page screenshots.
  The droplet and panel highlights are still effects; fluid motion was exercised
  by browser interactions. Screenshots alone cannot demonstrate animation.
- No physical Android testing, APK, deployment, new speech-model validation,
  licensing choice or changes to transcript/media handling in this refinement.
