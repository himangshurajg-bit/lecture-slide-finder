# Android 16 release status

[Download 1.00.0.00 stable](https://github.com/himangshurajg-bit/lecture-slide-finder/releases/tag/android-v1.00.0.00-stable). Version code 2; original project signer retained.

## Final Android 16 qualification — 2026-10-20 UTC

[Full native run 39504829180](https://github.com/himangshurajg-bit/lecture-slide-finder/actions/runs/39504829180) passed all 12 required checks on tested revision `900d8777b945913a07253b97c7c5b8c0aea71a72`: frame-study, handled-errors, engine-en, engine-hi, documents, speech-ui-en, speech-ui-hi, lifecycle-errors, frame-stress, export-bytes, speech-export-bytes-en and speech-export-bytes-hi. [Normal CI 39504829298](https://github.com/himangshurajg-bit/lecture-slide-finder/actions/runs/39504829298) also passed. Native export contents were read back. Both languages passed active cancellation and exact previous-transcript/note preservation. ErrorChecks executed after its instrumentation registration repair.

This qualifies the covered workflows in hosted Android 16/API36 KVM emulation with disposable CI signing, not physical-phone performance, broad language accuracy or older Android versions. The distribution APK retains the original signer and unchanged production assets. Historical failures below remain evidence of earlier attempts, not current blockers.

## Included workflows


- Bundled local video player, playback speed and passage repeat; glass-style ambient/touch/bouncy effects with reduced-motion support.
- Phone-only, offline English/Hindi speech transcription with bundled small Vosk models; optional timestamped transcript import.
- Search with paged results beyond 150 matches, seek to the passage/frame, save notes, restore local transcript/notes and export Markdown.
- Sequential decoded-frame gallery with bounded pages, individual-frame inspection and offline English/Hindi visible-text OCR into editable, searchable, timestamped points. OCR is not a summary or semantic slide detector.

Assamese is excluded. Android 16/API36 is the first release target; older-version testing follows the first release. The manifest's minimum API26 is not an Android8–16 compatibility qualification.

## Earlier failures and alternative

Earlier short-recognition cancellation tests raced completion; a native replacement dialog was observed, but that was not established as an ASR defect. A separate 601-second fixture now tests active cancellation and preservation without that race. The subsequent Hindi tap-delivery issue and missing ErrorChecks registration were repaired in test-only commit `900d877`. The final integrated run passed without weakening assertions. See [the historical test report](../android/TEST_REPORT.md) and [issue #1](https://github.com/himangshurajg-bit/lecture-slide-finder/issues/1).

## Accuracy, performance and storage limits

- Native evidence is a hosted Google APIs Android16 emulator with KVM, not a physical phone. CI uses disposable signing; the retained distribution candidate is signed with the original project key. The stable APK is distributed through the linked GitHub Release.
- Single public-speech **host** baselines measured normalized WER of 4.5% English and 16.7% Hindi. These are not Android/lecture-domain/mixed-language accuracy guarantees. Review technical terms and names.
- Tiny text, handwriting and equations can be missed or wrong in OCR. Review/edit against the source frame. All-frame OCR may take longer than playback and consume substantial battery; no phone thermal/battery benchmark is available.
- Decoder batching limits and browser motion checks do not establish complete-app memory, GPU or physical-device performance.
- Transcripts/notes/search are saved locally; videos need re-selection after reopening. Clearing data, uninstalling, storage failure or device loss can erase state. Export important notes.
- The older test APK is different from this stable APK. No Play Store deployment is configured.
