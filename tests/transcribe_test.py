"""Fast CLI safety checks: no model download or additional dependency required."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'tools/transcribe.py'

class TranscriptCLITests(unittest.TestCase):
    def run_tool(self, *args):
        return subprocess.run([sys.executable, str(SCRIPT), *map(str,args)], capture_output=True, text=True)

    def test_missing_input(self):
        result = self.run_tool('/does-not-exist/video.mp4')
        self.assertEqual(result.returncode, 2)
        self.assertIn('does not exist', result.stderr)

    def test_no_media_overwrite(self):
        with tempfile.TemporaryDirectory() as temp:
            media = Path(temp) / 'lecture.mp4'
            media.write_bytes(b'original')
            result = self.run_tool(media, '--output', media, '--force')
            self.assertEqual(result.returncode, 2)
            self.assertEqual(media.read_bytes(), b'original')

    def test_no_existing_output_overwrite(self):
        with tempfile.TemporaryDirectory() as temp:
            media = Path(temp) / 'lecture.mp4'
            media.write_bytes(b'media')
            output = Path(temp) / 'lecture.transcript.json'
            output.write_text('original')
            result = self.run_tool(media)
            self.assertEqual(result.returncode, 2)
            self.assertEqual(output.read_text(), 'original')

    def test_model_choice_validation(self):
        result = self.run_tool('lecture.mp4', '--model', 'not-a-model')
        self.assertEqual(result.returncode, 2)
        self.assertIn('invalid choice', result.stderr)

if __name__ == '__main__':
    unittest.main()
