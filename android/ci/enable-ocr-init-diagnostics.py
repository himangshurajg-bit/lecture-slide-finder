#!/usr/bin/env python3
"""Opt-in diagnostic-only patch for generated Android CI OCR assets."""
from __future__ import annotations

import argparse
from pathlib import Path

CORE_NAME = "tesseract-core-lstm.wasm.js"
WORKER_NAME = "worker.min.js"
CORE_ANCHOR = "var oa=b.print||console.log.bind(console),n=b.printErr||console.warn.bind(console);"
CORE_DIAGNOSTIC = (
    'var oa=b.print||console.log.bind(console),n=(function(f){return function(){'
    'try{var a=self.__lectureOcrInitErrors||(self.__lectureOcrInitErrors=[]),'
    's=Array.prototype.join.call(arguments," ").slice(0,300);'
    'if(a.length<24)a.push(s);else a[a.length-1]=s'
    '}catch(e){}return f.apply(console,arguments)}})'
    '(b.printErr||console.warn.bind(console));'
)
WORKER_RESET = "O=new l.TessBaseAPI,-1!==(b=O.Init"
WORKER_RESET_DIAGNOSTIC = "O=new l.TessBaseAPI,r.g.__lectureOcrInitErrors=[],-1!==(b=O.Init"
WORKER_FAILURE = 'r.reject("initialization failed");t.next=38;break'
WORKER_FAILURE_DIAGNOSTIC = (
    'r.reject("initialization failed"+(r.g.__lectureOcrInitErrors&&'
    'r.g.__lectureOcrInitErrors.length?": "+'
    'r.g.__lectureOcrInitErrors.slice(-8).join(" | "):""));'
    't.next=38;break'
)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one {label} anchor; found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ocr_dir", type=Path, help="generated/copied ocr asset directory")
    args = parser.parse_args()
    root = args.ocr_dir
    core_path = root / CORE_NAME
    worker_path = root / WORKER_NAME
    hindi_path = root / "lang" / "hin.traineddata"
    if not hindi_path.is_file() or hindi_path.stat().st_size == 0:
        raise SystemExit("real Hindi traineddata asset is missing or empty")
    if not core_path.is_file() or not worker_path.is_file():
        raise SystemExit("expected generated LSTM core and worker assets are missing")

    core = core_path.read_text(encoding="utf-8")
    worker = worker_path.read_text(encoding="utf-8")
    # Validate every anchor before writing either file, so a version mismatch is a no-op.
    core_new = replace_once(core, CORE_ANCHOR, CORE_DIAGNOSTIC, "Emscripten printErr")
    worker_new = replace_once(worker, WORKER_RESET, WORKER_RESET_DIAGNOSTIC, "worker init reset")
    worker_new = replace_once(worker_new, WORKER_FAILURE, WORKER_FAILURE_DIAGNOSTIC, "worker init error")

    core_path.write_text(core_new, encoding="utf-8")
    worker_path.write_text(worker_new, encoding="utf-8")
    print(f"Enabled opt-in CI Hindi-init diagnostics in generated assets; Hindi asset present ({hindi_path.stat().st_size} bytes).")
    print("No source assets, production cache settings, or screenshots were changed.")


if __name__ == "__main__":
    main()
