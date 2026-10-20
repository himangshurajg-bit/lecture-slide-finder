# Public/synthetic test media only

These are test fixtures, not private lecture recordings.

- `lecture.srt` and the existing `tests/fixtures/lecture.mp4`: original synthetic physics slides; no personal information.
- `en-clip.m4a` / `speech-en.mp4`: short AAC adaptation of John F. Kennedy’s 1961 inaugural speech sample from whisper.cpp, https://github.com/ggml-org/whisper.cpp/blob/master/samples/jfk.wav . US federal speech, public domain. The MP4 adds a plain synthetic background; the clip is shortened for decoder regression.
- `hi-clip.m4a`, `hi-full.m4a`, `speech-hi.mp4`: AAC adaptations (short/full, MP4 with synthetic background) of Google FLEURS Hindi dev example 1602, `10003386135529782184.wav`. Dataset: https://huggingface.co/datasets/google/fleurs ; dev metadata: https://huggingface.co/datasets/google/fleurs/blob/main/data/hi_in/dev.tsv . Google FLEURS, CC BY 4.0: https://creativecommons.org/licenses/by/4.0/ . Speech content is about Majorcan food, not a lecture.

Successful recognition of these short samples is pipeline evidence, not representative lecture accuracy. No Assamese fixture or recognition is included.
