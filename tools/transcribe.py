# /// script
# requires-python = ">=3.10"
# dependencies = ["faster-whisper==1.2.1", "huggingface-hub==0.36.0", "av==16.1.0"]
# ///
"""Generate a timestamped JSON transcript locally. First use downloads a model."""
import argparse
import json
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video", type=Path)
    parser.add_argument("--model", default="tiny", choices=["tiny", "base", "small", "medium", "large-v3"])
    parser.add_argument("--language", default=None, help="ISO language code, e.g. en, hi, as; omit to detect")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--force", action="store_true", help="Overwrite an existing output")
    args = parser.parse_args()
    output = args.output or args.video.with_suffix(".transcript.json")
    if not args.video.is_file():
        parser.error("Input video/audio file does not exist.")
    if output.resolve() == args.video.resolve():
        parser.error("Output must not replace the input media.")
    if output.exists() and not args.force:
        parser.error("Output already exists. Choose --output or use --force.")
    try:
        from faster_whisper import WhisperModel
        print("Loading local speech model (first use needs an internet download)…", file=sys.stderr)
        model = WhisperModel(args.model, device="cpu", compute_type="int8")
        segments, info = model.transcribe(str(args.video), language=args.language, beam_size=5)
        rows = [{"start": s.start, "end": s.end, "text": s.text.strip()} for s in segments if s.text.strip() and s.end > s.start]
        if not rows:
            raise ValueError("No speech was detected; no output written.")
        payload = {"language": info.language, "model": args.model, "segments": rows}
        # Exclusive create by default prevents an accidental overwrite after the initial check.
        with output.open("w" if args.force else "x", encoding="utf-8") as file:
            json.dump(payload, file, ensure_ascii=False, indent=2)
            file.write("\n")
        print(f"Wrote {len(rows)} segments to {output}. Review speech recognition for mistakes.")
    except Exception as exc:
        print(f"Transcription failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
