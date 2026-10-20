"""Regression for Android's MP4 elst movie/audio timescale confusion."""
import pathlib, struct, subprocess, tempfile, unittest
ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'android/app/src/main/java/io/github/himangshurajg/lecture/Mp4AudioEdits.java'
def box(name, body): return struct.pack('>I4s', len(body)+8, name.encode())+body
def fixture(movie=1000, media=44100, track=1, entries=1, edit=1024, rate=0x10000):
    mvhd=box('mvhd', struct.pack('>4I',0,0,0,movie))
    tkhd=box('tkhd',struct.pack('>4I',0,0,0,track))
    mdhd=box('mdhd',struct.pack('>4I',0,0,0,media))
    elst=box('elst',struct.pack('>IIIiI',0,entries,4000,edit,rate))
    return box('moov',mvhd+box('trak',tkhd+box('mdia',mdhd)+box('edts',elst)))
class Mp4EditRegression(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory();cls.root=pathlib.Path(cls.temp.name)
        main=cls.root/'Check.java'
        main.write_text('''import java.io.*;import io.github.himangshurajg.lecture.Mp4AudioEdits;
public class Check { public static void main(String[] a)throws Exception {try(RandomAccessFile r=new RandomAccessFile(a[0],"r")){try{System.out.println(Mp4AudioEdits.correctedDelay(r.getChannel(),Long.parseLong(a[1]),Long.parseLong(a[2]),Integer.parseInt(a[3]),44100,Integer.parseInt(a[4])));}catch(IOException e){System.out.println("malformed");}}}}''')
        subprocess.run(['javac','--release','8','-d',str(cls.root),str(SOURCE),str(main)],check=True)
    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()
    def check(self,blob,expected,track=1,reported=45158,start=0,length=-1):
        path=self.root/'sample.mp4';path.write_bytes(blob)
        got=subprocess.check_output(['java','-cp',str(self.root),'Check',str(path),str(start),str(length),str(track),str(reported)],text=True).strip()
        self.assertEqual(got,expected)
    def test_wrong_movie_scale_is_corrected(self):self.check(fixture(),'1024')
    def test_correct_delay_is_untouched(self):self.check(fixture(),'null',reported=1024)
    def test_wrong_track_is_untouched(self):self.check(fixture(),'null',track=2)
    def test_complex_edit_is_untouched(self):self.check(fixture(entries=2),'null')
    def test_non_rate_one_is_untouched(self):self.check(fixture(rate=0x20000),'null')
    def test_empty_edit_is_untouched(self):self.check(fixture(edit=-1),'null')
    def test_unmatched_reported_delay_is_untouched(self):self.check(fixture(),'null',reported=40000)
    def test_equal_scales_are_untouched(self):self.check(fixture(media=1000),'null')
    def test_embedded_asset_offset(self):self.check(b'x'*37+fixture()+b'tail','1024',start=37,length=len(fixture()))
    def test_corrupt_size_is_bounded(self):self.check(struct.pack('>I4s',1000000,b'moov'),'malformed')
    def test_truncated_size_is_bounded(self):self.check(fixture()[:-2],'malformed')
    def test_real_retained_aac_fixture(self):
        self.check((ROOT/'android/ci/fixtures/en-clip.m4a').read_bytes(),'1024')
if __name__=='__main__':unittest.main()
