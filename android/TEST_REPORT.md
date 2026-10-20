# Android 1.00.0.00 stable — candidate validation

This report describes an unpublished candidate until the final native checks below pass. It does not rename or endorse the earlier debuggable `0.1.0-test.1` APK.

## Artifact and reproducibility

- Package `io.github.himangshurajg.lecture`; label `Lecture Finder`.
- Exact Android version name `1.00.0.00 stable`, version code 2.
- Non-debuggable; bundled offline UI; minimum API 26, target/compile API 35.
- APK Signature Schemes v2/v3 verified. Signer certificate SHA-256: `705cffadb070570ce6248d3aba3f0fc00d6726c4615713fc9750fc863d01b934`, identical to the downloaded earlier public test APK, preserving update continuity.
- No requested Android permissions, network backend, telemetry or account login. No test media, test instrumentation or signing secrets are bundled.
- Build with `ANDROID_HOME=... bash android/build-test-apk.sh`. The legacy script/key names are retained; this candidate manifest is non-debuggable.

## Automated regressions

- 37 Vitest unit tests passed, including validated local session encoding/recovery.
- Nine Python transcription-helper and Android source/manifest boundary tests passed.
- 24 Playwright desktop/Pixel-emulated browser tests passed on the pre-ASR candidate, including state restoration, reset confirmation and packaged-origin export receipt behavior.
- Production TypeScript/Vite build and Android Java/DEX/resource packaging passed.
- Browser tests are not native Android tests. Native checks below use a separate, same-signed instrumentation APK, never included in the distributable.

## Genuine Android runtime results

A dedicated Android 9/API 28 software emulator, with its older platform WebView, exercised real Android document-provider flows using synthetic lecture fixtures. Native tests found and led to fixes for unsupported `replaceChildren`, `Blob.text`, CSS `aspect-ratio` and reduced-motion listener APIs; these were not caught by current desktop Chromium.

Verified in the retained `workflow-resumed.log` run:

- Bundled offline UI launch and liquid-glass layout.
- Real Android document picker selection of `lecture.mp4`.
- Video metadata, H.264 decoding and advancing playback.
- Real Android document picker selection of `lecture.srt`.
- Transcript parsing/import and search of the imported transcript.
- Timestamp seek to 00:04 and decoded matching-frame capture.
- Passage selection in notes.

Native Markdown save was completed through the actual Android Save dialog. The resulting `/sdcard/Download/lecture-notes.md` was read back and contained:

```markdown
# Study notes — lecture.mp4

- **00:04–00:08** Mass and acceleration.
```

The automation's receipt assertion timed out after the time-limited emulator expired; the output file was verified separately, not inferred from a click or simulated provider. Native session recovery across WebView reload also passed in earlier retained checks.

Android9 native rotation and transcript-picker cancellation passed with notes preserved. Export cancellation, remaining lifecycle and invalid stored-state checks are being completed in the separate `EdgeChecks` instrumentation runner. This candidate must not be published while those release checks remain unresolved.

## Coverage limits

- No physical-phone test, no broad Android-version/device matrix, and no crash-free/all-device guarantee. The software emulator experienced System UI ANRs under resource pressure and multiple time-limited emulator processes expired. These are environmental observations, not proof that all app failures are environmental.
- The earlier public test APK only imports computer-generated transcripts. The current unpublished candidate adds Android audio decoding/resampling and small bundled English/Hindi Vosk models through a direct JNI bridge. This supersedes the historical Whisper/Assamese investigation below. No broad language-quality claim, Android8–16 runtime compatibility or stable qualification is implied by compilation. Import remains available.
- Frame capture is the image at a selected time, not OCR or automatic slide recognition.
- Transcripts, notes and search are saved locally. Videos must be selected again after reopening. Important notes should be exported separately; storage failure, clearing app data, uninstalling or device loss can erase local state.
- The retained self-signed project key has a legacy test certificate name. This is sideload distribution, not Play Store publication or Play App Signing.

## Running native checks

Install the built candidate on the isolated test emulator, prepare synthetic `lecture.mp4` and `lecture.srt` in Downloads, then run:

```sh
ANDROID_HOME=... bash android/tests/run.sh
ANDROID_HOME=... CHECK_CLASS=EdgeChecks bash android/tests/run.sh
```

The scripts explicitly target `emulator-5556`; review and change that only for an intentionally selected test device. They do not operate other projects or emulators. Do not ship the instrumentation APK. Runtime logs/screenshots/build products remain ignored rather than committed wholesale.


## Historical phone-only ASR investigation (2026-10-20)

Historical evidence below is retained for reproducibility, not current release requirements. The owner subsequently removed Assamese recognition. No further Assamese/IndicConformer/ONNX research or integration is in scope; the current English/Hindi path is described in the latest section.

- Recovered the retained Hindi IndicConformer benchmark instead of repeating it. The saved host result has Hindi script with wording/spelling errors. Loading 0.679 s, decoding 2.248 s for 12.24 s of audio, peak process RSS 468,936 KiB. These are **Linux host** results, not phone benchmarks.
- Assamese int8 IndicConformer (~188 MiB) retained much more content and Assamese script on one FLEURS sample than Whisper Tiny/Small; spelling/repetition errors remain. It is not yet integrated into the APK and mixed-language behavior is unverified.
- Public conversion pinned to `parismitaglobalsolutions/indicconformer-sherpa-onnx@9721eb71eea141fae0982cfcdb9dd2e3d4953c4a` labels tooling Apache-2.0 and explicitly retains AI4Bharat source weight MIT licensing. Original Assamese repository labels MIT but source access is gated. No source gate was accepted and no model was redistributed. Full original access conditions are not exposed by the public model card/API, so redistribution reconciliation remains open rather than treating the conversion's Apache label as a replacement weight license.
- Retrieved sherpa-onnx 1.13.8 native libraries have 16 KiB LOAD alignment across all four ABIs. However `libonnxruntime.so` declares build API 27, above the requested API26 floor. This is a compatibility risk needing rebuild/API26 runtime validation, not proof that every API26 load fails. Audit saved in ignored `android/runtime/sherpa-elf-audit.json`.
- API28 emulator was recovered without removing other projects: the project-local NDK toolchain was compressed and archive-listed before removing its expanded copy to free disk. Archive `.local/android-ndk-r27c.tar.gz` can restore it.
- Android8/API26 and Android16/API36 runtime coverage remain missing. The API36 AVD's large userdata request is not evidence about the application's compatibility.

No stable release, push, new public APK or new attachment has been made during this investigation. The `stable` candidate filename/version is not publication or release qualification.

## Recovery and alternative-model evaluation (2026-10-20, continued)

- Recovered the prior diagnostic command `19a152ae1138b79eae95cfbcc4a734d9`. No state-changing operation was retried from that listing.
- Removed only this project's disposable failed API36 emulator userdata/overlay files after inspecting their locations. Source, fixtures, reports, signing key and other projects were preserved. The NDK archive remains available.
- The intercepted-asset-origin browser case passed on a serial, resource-bounded rerun: **24/24 browser checks passed**, including desktop and Android Chrome emulation. Log: `android/runtime/recovery-browser.log`. This does not cover native Android inference or Android-version compatibility.
- A reduced-memory API28 software emulator booted (`sys.boot_completed=1`), but the pending APK reinstall did not finish before its 600-second command deadline. No new installed-app ASR result is claimed. That timed-out install must be reconciled before retrying it.
- A separate Android `app_process`/JNI smoke-test runner was prepared to exercise the same native library without depending on PackageInstaller. It is deliberately separate from the installed-app decoder/instrumentation test; a shell-library pass alone cannot qualify the end-to-end app.
- Correct original AI4Bharat URL/API was retrieved (no `hugface.co` misspelling). API identifies automatic gating and profile-sharing; public model card requires reviewing and accepting conditions to access source weights. No gate accepted. Conversion's Apache-2.0 tooling label does not override source-weight access/licensing conditions; public IndicConformer redistribution remains unresolved.
- Investigated an ungated, Apache-2.0-labelled alternative: `ismaeilhf/whisper-small-as@e3f23f24c6e0ea29208f9e3a6dc9c0b8a42f8b3e`. Converted safetensors without executing pickle/model-supplied code. Converted f16 file is **487,601,984 bytes** (about 465 MiB). It was not integrated or redistributed.
- On the existing 8.52-second public Assamese fixture, that fine-tune produced Assamese script but substantial wording/spelling errors: `এতিও আমাৰ আসিখটাংখত ৰেপুৰ কৰ তাৰিকৰ জোকেতি জেন্তৰেল আমেৰিকান দেখ হৱম্হন প্ৰদান কৰা হৈছিল, আমি আপোনাৰ যত নলও`. The host run used 2 CPU threads, took **189.0 seconds** total, and peaked at **768,092 KiB RSS**. Its output extended timestamps to 30 seconds despite the shorter fixture. These are host observations, not phone performance, and do not establish usable Assamese accuracy.
- Alternative evidence is retained under `android/runtime/asr-comparison/assamese-open/` (card, conversion, result, metrics and computed digests). The source-weight download was removed after hashing and conversion to recover space; the pinned source and GGML conversion remain reproducible and the converted model is retained. No source code, signing material or test fixture was removed.

Stable release remains withheld. No public push, release or model bundling occurred in this recovery.

### API26 runtime investigation

A concrete alternative ONNX Runtime build was obtained from official Maven Central: `com.microsoft.onnxruntime:onnxruntime-android:1.22.0`. Computed artifact digest, original URL, ELF notes and LOAD segments are retained in `android/runtime/onnx-api26-investigation/audit.json`. All four included `libonnxruntime.so` ELF notes specify **API24** and LOAD segments use **16 KiB alignment**, so API26 is not inherently excluded by that runtime build. Upstream v1.22.0 Android AAR build script also defaults to minimum SDK24 (saved in `android/runtime/asr-source-evidence/onnx-android-build.py`).

This is **not a drop-in repair**: the retrieved sherpa library identifies version 1.13.8 and references `VERS_1.28.2`; replacing its ONNX dependency with v1.22.0 without a matching source rebuild/ABI validation would be unsafe. No such replacement was made. A compatible sherpa source build against an API24/26-capable official runtime is still required, followed by actual API26 execution. Source-weight redistribution conditions remain a separate unresolved gate.

### Native smoke-test outcome and retained limits

The separate API28 x86 `app_process` runner did execute the app's Whisper JNI library and load the bundled multilingual Tiny model: `PASS ANDROID_JNI_MODEL_LOAD elapsed_ms=7478` appears in `android/runtime/shell-checks/run.log`. The run then exceeded its 600-second command deadline without a transcription or cancellation success marker. The completed command receipt reports exit -9 / timed_out=true. This establishes Android JNI/model loading only—not successful inference, cancellation, installed-app audio decoding, performance, or an end-to-end phone-only workflow. No inference/cancellation pass is claimed. The software emulator and runner are no longer running.

The instrumentation APK built using `CHECK_BUILD_ONLY=1`; its log explicitly claims no Android execution (`android/runtime/shell-checks/instrumentation-build.log`). The attempted partial NDK extraction returned exit 2; its newly expanded cache was removed again after inspecting it, preserving `.local/android-ndk-r27c.tar.gz`. Final free space was approximately 2.4 GiB. No source, signing key, fixtures, reports, other project data or original NDK archive was removed. `git diff --check` passed.

At that historical point, release gates included acceptable Assamese and mixed-language quality, lawful model redistribution, source-compatible sherpa/ONNX integration for API26, actual successful native inference/cancellation and app lifecycle checks, and Android8/16 execution. Assamese/sherpa/ONNX gates were later removed from the release by the owner’s explicit scope reduction. None is replaced by a compilation, a model-load pass, a language token, host inference, ELF alignment, or a browser emulation result. No stable APK is qualified or published.


## Current owner scope: English/Hindi only (2026-10-20)

The owner explicitly stopped Assamese recognition while continuing app development. English/Hindi phone-only transcription, lecture import/search/notes/export and the liquid-glass UI remain required, with minimum API26 and Android16 validation. Retained Assamese benchmarks are historical only; no Assamese model is in the current app or selector. No gate acceptance or model redistribution occurred.

The current candidate uses Vosk Android 0.3.75, small US English 0.15 and Hindi 0.22 models, pinned by SHA-256. The official Vosk model list labels both selected models Apache-2.0; their original model READMEs and the license are included in the app. It bundles about 82 MiB of compressed models. No Whisper, ONNX, JNA or remote service is required by this path. Native audio is passed in 250ms/4000-sample chunks, with cooperative cancellation before/after every call and thread-owned teardown to avoid freeing live native state. Model extraction verifies the compressed archive, rejects traversal, bounds entries/expanded size, and installs through a staging directory.

Compilation, 37 unit and 10 Python checks passed for this candidate. An instrumentation APK was built separately; build success alone is not a native runtime pass. The earlier 24/24 browser check belongs to the earlier code state and must not be silently attributed to new ASR changes.

## English/Hindi installed-app recovery and AAC priming fix (2026-10-20)

The owner has since prioritized Android16 as the first stable-release runtime target. Older-version validation is deferred, not silently considered passed; minSDK26 remains unchanged. No stable release is qualified by the evidence below.

- Recovered command `4855b6a71614056c33201c9c4c3600fc` to exit 0 before further changes. The old expanded Assamese model was preserved in a verified compressed archive and then removed; no new Assamese recognition/research was done.
- The initial retained short English installed-app run completed in 101,568 ms but failed its required `country` word: output was `drake i do for yo` / `at what you`. The Hindi short run produced timestamped Hindi words in 134,100 ms, with recognition errors. A Hindi script/token check alone is not an accuracy qualification.
- Independent MediaCodec/PCM diagnostic (`1711053e2da1fae6e50b4e40e7635157`, exit 1 because later edge checks timed out) completed audio decode, native recognition and PCM pull. Android9 yielded 48,261 16kHz mono samples (~3.02 seconds) from the 4.02-second English AAC fixture; ffmpeg plus the same Java resampler yielded 64,273 (~4.02 seconds). Host recognition of the pulled Android PCM reproduced the bad words, isolating audio loss before Vosk rather than blaming recognizer speed alone.
- Android9 reported encoder-delay 45,158 samples. The AAC packet metadata and MP4 edit list establish 1,024 priming samples. Retained AOSP Android9 MPEG4Extractor source (`vosk-checks/aosp9-MPEG4Extractor.cpp`, lines 446 onward) computes this edit's media time using the movie timescale rather than the audio-track timescale. The exact original source URL is https://android.googlesource.com/platform/frameworks/av/+/android-9.0.0_r1/media/extractors/mp4/MPEG4Extractor.cpp?format=TEXT .
- Added `Mp4AudioEdits.java`: bounded, positional ISO-BMFF inspection, same-track matching, one rate-1 edit only, correct v0/v1 fields, overflow/bounds checks, and correction only when the reported delay exactly equals the erroneous movie-timescale calculation. Unknown containers, pipes, complex edits and already-correct metadata remain untouched. `OfflineTranscriber` applies it to AAC metadata before decoder configuration; no audio/model fixture or recognition assertion was weakened.
- Twelve new host parser regressions passed, including the unchanged actual AAC fixture, embedded-asset offset, matching-track correction, no-op cases and malformed lengths. Total Python suite: 22/22. TypeScript unit suite: 37/37. Production build, Android candidate compilation/signature verification and `git diff --check` passed. These are not Android16 execution tests.
- Completed command `9ddf07ed4c57bd6e554acab19931c466` (exit 0) installed the corrected candidate in the API28 emulator and exercised actual app `OfflineTranscriber` + MediaExtractor/MediaCodec + bundled model + JNI. English output: `what your country can do for you ask what you`, cue 0.023219–4.043219 seconds, elapsed 105,973 ms; required word/timing checks and pre-cancel passed. Hindi output: `घाना भूमध्यसागर में सामान क्षेत्रों की तरह रोटी साथ`, cue 1.283219–5.033219 seconds, elapsed 146,518 ms; required script/token/timing checks and pre-cancel passed. Logs: `vosk-checks/priming-fix-en.log`, `priming-fix-hi.log`, `priming-fix-orchestration.log`.
- These are software-emulator timings, not phone performance. The tests call the installed app's pipeline on public short fixture URIs. They do not establish a full lecture's accuracy, mixed-language behavior, real-device speed, picker-to-ASR/result-acceptance UI flow, or Android16 support. Hindi wording errors remain visible; no release-quality claim is made.
- Existing API28 edge runner got through clean UI launch, note seeding and transcript-picker cancellation, but timed out waiting for export cancellation. The test previously pressed Back only once while DocumentsUI's filename keyboard could consume it. Updated test to verify DocumentsUI owns the window and press a second Back only if that external picker remains; the test rerun is separately recorded below. No production export behavior was changed on that unproven cause.

Stable publication remains withheld. No push, GitHub release or APK attachment was made in this continuation. The local filename containing `stable` is an internal candidate filename, not evidence of qualification.

## Android16-first continuation: current UI regression and quality evidence

- Recovered SDK packaging command `d2f13f7a51b0e2c7e568261cc2a5ea14` to exit 0; no repack/retry occurred. The original API36 attempt `14e329a14280015dff9f04fd47c35739` was reconciled as disk refusal, not an app compatibility result.
- Provisioned an isolated `lecture-android16` Computer with the retained API36 x86_64 system image, emulator and platform tools. This overcomes the former disk-space blocker without deleting source, signing data, evidence or other projects. First software boot has not yet completed; candidate installation previously failed because the package service was not available. Neither emulator provisioning nor ADB's API36/release16 properties is an app runtime pass.
- Current web UI regression suite completed: **28/28 Playwright checks**, one worker, desktop Chromium and Android Chrome emulation, including the new playback-speed/repeat-passage controls, global nonblocking touch light and reduced-motion behavior. Log: `android/runtime/api36-priority/browser-current.log`. **37/37 TypeScript unit tests** and **22/22 Python tests** also passed (`unit-current.log`, `python-current.log`). Browser-emulated Android remains distinct from installed Android16.
- Inspected the updated mobile screenshot `docs/android-chrome-emulation.png`: translucent panels, clear text, speed/repeat controls, search result and saved note are visible without horizontal overflow. This is a browser capture, not a native Android16 screenshot or physical-phone performance measurement.
- Built the fuller Android instrumentation APK, including an actual picker-to-ASR-to-timestamped-result workflow, search/frame/note use and active cancellation. It is retained at `android/runtime/api36-priority/runtime-checks-full.apk`; build success is not test execution.
- Assessed the complete 12.24-second public FLEURS Hindi fixture against its supplied transcript using the unchanged small Hindi model on the host. It produced 5 word edits over 30 reference words (16.7% normalized word error rate), including the initial food-name omission/substitution, joined `भूमध्यसागर`, and `सामान` for `समान`. This supersedes neither the earlier short installed-app output nor its known errors. It is a single host baseline, not lecture-domain or Android16 accuracy qualification. Script/reference/hypothesis/timings: `android/runtime/api36-priority/assess-hindi.py`, `hindi-full-quality.json`, `hindi-full-quality.log`.

No APK was delivered to the owner, no GitHub push/release occurred, and stable release remains withheld pending actual Android16 installed-app workflows and representative recognition/usability assessment. Android16 means API36, not API16. Other Android versions remain deferred; Assamese remains stopped.

### Further bounded Android16 recovery and English baseline

- The isolated API36 emulator progressed through first-boot package compilation, but repeatedly watchdog-killed `system_server` while starting framework services/home. `sys.boot_completed` never became 1 in that attempt; the package service disappeared and no candidate installation succeeded. Logs are preserved on `lecture-android16` under `logs/boot-all.log` and `logs/boot-watchdog-recovery.log`. The host has two CPU cores, about 4 GiB RAM and no `/dev/kvm`; this is a software-emulator environment failure, not a demonstrated app incompatibility.
- The looping emulator was stopped. A distinct bounded recovery reuses its retained userdata/dexopt work with one virtual core and 2 GiB RAM. The attempted `ro.hw_timeout_multiplier=10` boot option was explicitly rejected by the emulator (only qemu.* boot properties supported), so no timeout-multiplier change is claimed. Its outcome is recorded separately, not inferred from launch.
- On one full 11-second public JFK English fixture, host recognition produced `and so my fellow americans as not what your country can do for you ask what you can do for your country`. Case-insensitive NFC/punctuation-normalized comparison gives 1 word substitution over 22 words (4.5% WER). Case-sensitive capitalization artifacts were removed from the calculation; the recognizer/audio were not changed. This single public-speech host baseline is not lecture-domain, mixed-language, Android16 or phone-speed qualification. Script and full results are in `api36-priority/assess-english.py` and `english-full-quality.json`.

- Additional current-UI diagnostic: headless Chromium with Pixel 7 viewport and 4× CPU throttling observed 300 requestAnimationFrame callbacks in five seconds of repeated touch/scroll effects; median/p95 16.7 ms, maximum 33.4 ms, zero >50 ms callbacks, no horizontal overflow or page errors, and a subsequent saved-note action succeeded. Reduced-motion disabled the touch aura. These are host frame-callback observations, not native Android16/GPU-compositor/phone-battery performance. Full metrics and script: `api36-priority/browser-motion-diagnostic.json` and `.mjs`.

### Retained one-core boot: follow-up inspection (2026-10-20)

The original running command `f24e67849e51edc3624741987bc7812d` was inspected before any further launch/install. At approximately 18:31 UTC it was still running after about 9.5 minutes; `sys.boot_completed` remained blank. `package`, `activity` and `settings` services appeared transiently after an earlier observation found them absent, then ADB returned `device offline`. No second emulator or installation was started. Preserved `lecture-android16:logs/recovery-inspection-logcat.log` shows `WATCHDOG KILLING SYSTEM PROCESS` at 18:28:25.197 after the system-server main handler blocked for 65 seconds. This remains a framework/software-emulation startup failure, not an app compatibility failure. Detailed observation is retained in `android/runtime/api36-priority/recovery-status-1832.txt`; command completion must still be reconciled before another attempt. No previously passed suite was rerun or native success inferred.

The retained one-core command subsequently finished with **exit -9, timed_out=true** (`wait_command` receipt), without boot completion. A post-terminal process/device inspection found no QEMU process and an empty ADB device list, establishing that this launch was stopped before any replacement.

A materially different, lower-overhead native-test route is being prepared using Google's official API36 AOSP Automated Test Device image. Official repository catalog `https://dl.google.com/android/repository/sys-img/aosp_atd/sys-img2-3.xml` lists `system-images;android-36;aosp_atd;x86_64`, revision 1; its downloaded archive passed the catalog SHA-1 `abfa4db989684e3f72b4a48644a7f8190295b49e`. It is separate from the retained regular-image userdata. Android's ATD documentation states that background services/apps and hardware rendering are disabled; therefore even successful native pipeline tests here cannot qualify hardware-rendered screenshots, regular-device appearance, phone performance or missing external UI flows. No previously failed workflow is replaced with a stub or marked passed. The bounded boot/install/pipeline command and outcome remain separately recorded. No production code changed or passed suites repeated.


### API36 ATD terminal reconciliation (2026-10-20)

Recovered the retained ATD command `1390fae81bdb071e83cd9ce154237912` before any new launch or installation. Its authoritative receipt is **completed, exit 1, timed_out=false**: the script raised `ATD boot did not complete within bounded 260s`, rather than reaching its boot-complete branch. Retained final properties identify release16/API36 but contain no `sys.boot_completed=1`. Logs still report the activity service missing; neither candidate installation nor instrumentation was attempted. A post-terminal inspection found no QEMU emulator and an empty ADB device list. No replacement emulator was launched in this continuation.

The official lower-overhead ATD route has therefore also not yielded an executable Android16 test environment here. The host has no `/dev/kvm`; the prior regular image's framework watchdog failure and this bounded ATD startup timeout do **not** establish an application compatibility defect or that Android16 cannot boot elsewhere. They do mean native gates cannot currently be executed on these available environments. Repeated equivalent software boots are not counted as progress or qualification. A functioning Android16 runtime is the concrete dependency for continuing the real install/import/transcription/results/cancel/persistence/export/lifecycle/error checks. ATD would in any case not qualify normal-device rendering, phone performance or unavailable document-provider flows.

Machine-readable terminal evidence and selected startup log excerpts are retained in `android/runtime/api36-priority/atd-terminal/`. Full logs and AVD data remain on `lecture-android16:/home/user/workspace/logs/atd36/`. No production code changed, previously passed suites were not repeated, no native pass was added, and no push/release/APK delivery occurred. Stable remains unqualified; Android16 first, English/Hindi only, Assamese stopped and other versions deferred.

## Frame-gallery candidate expansion — 2026-10-20
- Added sequential MediaCodec decoder-output traversal with four-frame batches, accurate PTS/index, end-of-stream accounting, cancellation, bounded 16-thumbnail history and exact-image single-frame inspection. No sparse sampled keyframe claim.
- Added bundled Tesseract.js 6.0.1 / tessdata_fast English+Hindi OCR; visible-line bullet points, adjacency grouping, editable corrections retaining original OCR, search/paged points, persistence, Markdown sources/coverage and bounded-text stop conditions. No transcript/summary/equation fabrication.
- Local candidate APK and instrumentation compile/sign checks passed, retaining original signing identity. This is not publication or Android16 runtime evidence.
- New original six-frame H.264/B-frame fixture includes known English/Hindi text and half-second PTS. Browser OCR actually recognized both expected strings without external HTTP requests in desktop Chromium and Pixel7 browser emulation. Cancellation, editing, search, persistence and Markdown original/source/partial labels passed.
- Prior hosted API36 run 39478277214: KVM/build/install, handled errors, English/Hindi engine, documents, English speech UI and native export bytes passed. Hindi speech UI fixture selection and lifecycle/error segment failed/timed out. That baseline does not validate the new gallery/OCR. Stable remains withheld.
- New API36 FrameStudyChecks verifies all six decoder outputs, PTS order/EOS, pre-cancel, real picker, gallery pages, single-frame detail, actual bundled English/Hindi all-frame OCR, saved complete coverage and active cancel. Result must be recorded from an actual runner, not inferred from test compilation.


## Frame-study branch native attempt (39483088621)
- API36 installed candidate/instrumentation; sequential decoder returned all six original B-frame fixture frames with expected PTS/EOS. Pre-cancellation passed.
- Emulator connection disappeared after choosing that fixture in the system picker, before gallery/OCR assertions. Full native feature is not qualified.
- Retrying a changed rendering configuration (Vulkan disabled, 3GiB guest vs 4GiB) with live diagnostic capture. This is environment diagnosis, not proof of an application fix.
- Clean-host Python CI found an ignored runtime-only fixture path; regression now reads the same retained public fixture from tracked `android/ci/fixtures/`.
