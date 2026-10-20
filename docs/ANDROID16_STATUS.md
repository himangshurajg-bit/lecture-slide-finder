# Android 16 candidate: current status

This is the concise status of the unpublished `1.00.0.00 stable` candidate, not a release announcement. Historical attempts remain in [the test report](../android/TEST_REPORT.md). Active blocker: [issue #1](https://github.com/himangshurajg-bit/lecture-slide-finder/issues/1).

## What the candidate does

- Bundled local video player, playback speed and passage repeat; glass-style ambient/touch/bouncy effects with reduced-motion support.
- Phone-only, offline English/Hindi speech transcription with bundled small Vosk models; optional timestamped transcript import.
- Search with paged results beyond 150 matches, seek to the passage/frame, save notes, restore local transcript/notes and export Markdown.
- Sequential decoded-frame gallery with bounded pages, individual-frame inspection and offline English/Hindi visible-text OCR into editable, searchable, timestamped points. OCR is not a summary or semantic slide detector.

Assamese is excluded. Android 16/API36 is the first release target; older-version testing follows the first release. The manifest's minimum API26 is not an Android8–16 compatibility qualification.

## Latest completed test matrix

Test-only revision [`bcb9af4`](https://github.com/himangshurajg-bit/lecture-slide-finder/commit/bcb9af451d4f0524737111bbc96de242f916f0b3). [Native run 39502780440](https://github.com/himangshurajg-bit/lecture-slide-finder/actions/runs/39502780440) **failed overall**. [Normal CI 39502780435](https://github.com/himangshurajg-bit/lecture-slide-finder/actions/runs/39502780435) passed; local 27 Python regressions and clean instrumentation compilation passed. Individual native outcomes:

| Check | Result | Meaning / limit |
| --- | --- | --- |
| `frame-study` | Passed | Synthetic six-frame gallery, offline English/Hindi OCR, persistence/export and cancellation coverage. |
| `engine-en`, `engine-hi` | Passed | Installed short-fixture pipeline; not broad language accuracy. |
| `documents`, `export-bytes` | Passed | Native picker, video/transcript workflow and saved document contents. |
| `speech-ui-en` | Passed | Speech/search/frame/notes plus separate long-media cancellation preserving prior transcript/notes. |
| `speech-export-bytes-en`, `speech-export-bytes-hi` | Passed | Saved English and Hindi speech-note file contents checked. |
| `speech-ui-hi` | Failed | Timed out awaiting cancelled status. Pointerdown/up reached Cancel but no click appeared; cause unresolved. Earlier recognition/search/frame/notes and export succeeded. |
| `handled-errors` | Did not execute | Runner returned 1: `ErrorChecks` exists in source but was not registered in the test manifest. This is a test-harness coverage gap, not a demonstrated application error-handling failure. |
| `lifecycle-errors` | Passed | Scoped lifecycle/recovery, paging and error assertions. This does not cancel the separate `handled-errors` failure. |
| `frame-stress` | Passed | 900 synthetic H.264 frames / 60 seconds, bounded decoder batches; not a full-lecture OCR/battery benchmark. |

## Failure and alternative, without conflating them

[Previous integrated run 39500287891](https://github.com/himangshurajg-bit/lecture-slide-finder/actions/runs/39500287891) passed recognition/search/frame/note use in both languages, then timed out during cancellation of a second short transcription. A native window appeared. The source permits a replace-notes confirmation after completed recognition; finishing before cancellation is a supported **hypothesis**, not a proven cause or established ASR defect. That run did not reach speech exports.

The new test exports completed speech results first. It then resets only test state, chooses a separate 601-second public-domain recording through the real native picker, seeds a previous transcript and note, requires an active transcription worker, and uses the real Cancel control. Exact preservation and native export-byte assertions remain mandatory. No production code was changed to obtain these results. English cancellation and both speech exports passed; Hindi cancellation and the unexecuted handled-error coverage still block release.

Test-only repair [`900d877`](https://github.com/himangshurajg-bit/lecture-slide-finder/commit/900d8777b945913a07253b97c7c5b8c0aea71a72) registers `ErrorChecks`, verifies that every runner-referenced class is registered, and lets the long-media Cancel gesture settle after scrolling while requiring a trusted click receipt. All 27 Python regressions and clean instrumentation compilation passed. [Integrated native run 39504829180](https://github.com/himangshurajg-bit/lecture-slide-finder/actions/runs/39504829180) is pending; no runtime pass is inferred. Next work is to complete the missing checks and integrated qualification without weakening assertions. Earlier report entries that called handled errors passed are superseded: a failed instrumentation launch is not a passed application check. A green ordinary CI run, an internal APK filename containing `stable`, or previously passing subsets cannot authorize a stable claim.

## Accuracy, performance and storage limits

- Native evidence is a hosted Google APIs Android16 emulator with KVM, not a physical phone. CI uses disposable signing; the retained distribution candidate is signed with the original project key. No new stable APK has been published.
- Single public-speech **host** baselines measured normalized WER of 4.5% English and 16.7% Hindi. These are not Android/lecture-domain/mixed-language accuracy guarantees. Review technical terms and names.
- Tiny text, handwriting and equations can be missed or wrong in OCR. Review/edit against the source frame. All-frame OCR may take longer than playback and consume substantial battery; no phone thermal/battery benchmark is available.
- Decoder batching limits and browser motion checks do not establish complete-app memory, GPU or physical-device performance.
- Transcripts/notes/search are saved locally; videos need re-selection after reopening. Clearing data, uninstalling, storage failure or device loss can erase state. Export important notes.
- The public older test APK is different from this unpublished candidate. No Play Store deployment is configured.
