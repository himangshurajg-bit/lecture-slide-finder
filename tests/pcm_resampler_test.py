"""Execute the actual Java resampler on host; not an Android/ASR quality test."""
import pathlib,shutil,subprocess,tempfile,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'Requires Java compiler/runtime')
class PcmResamplerTests(unittest.TestCase):
 def test_streaming_resampling_and_validation(self):
  source=ROOT/'android/app/src/main/java/io/github/himangshurajg/lecture/PcmResampler.java'
  harness='''import io.github.himangshurajg.lecture.PcmResampler;import java.util.*;
public class ResamplerCheck {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] ignored){
 short[] mono={-32768,-12000,0,12000,32767};check(Arrays.equals(mono,new PcmResampler(16000,1).convert(mono)),"16k mono identity");
 short[] stereo={1000,3000,-1000,-3000,32767,-32768};check(Arrays.equals(new short[]{2000,-2000,0},new PcmResampler(16000,2).convert(stereo)),"stereo average without overflow");
 check(Arrays.equals(new short[]{0,500,1000,1500,2000},new PcmResampler(8000,1).convert(new short[]{0,1000,2000})),"8k interpolation");
 for(int rate:new int[]{8000,16000,22050,44100,48000,96000,192000}){
 short[] input=new short[rate*2];for(int i=0;i<input.length;i++)input[i]=(short)((i*71)%32000);
 short[] full=new PcmResampler(rate,2).convert(input);PcmResampler stream=new PcmResampler(rate,2);ArrayList<Short> pieces=new ArrayList<>();
 for(int i=0;i<input.length;i+=514)for(short s:stream.convert(Arrays.copyOfRange(input,i,Math.min(i+514,input.length))))pieces.add(s);
 check(full.length==pieces.size(),"stream sample count "+rate);for(int i=0;i<full.length;i++)check(full[i]==pieces.get(i),"stream phase "+rate);
 check(Math.abs(full.length-16000)<=2,"one second rate conversion "+rate);
 }
 check(new PcmResampler(16000,1).convert(new short[0]).length==0,"empty input");
 try{new PcmResampler(7999,1);throw new AssertionError("bad rate accepted");}catch(IllegalArgumentException ok){}
 try{new PcmResampler(16000,9);throw new AssertionError("bad channels accepted");}catch(IllegalArgumentException ok){}
 try{new PcmResampler(16000,2).convert(new short[3]);throw new AssertionError("incomplete frame accepted");}catch(IllegalArgumentException ok){}
 System.out.println("PASS native-source PCM identity, stereo, interpolation, streaming, rates and invalid input");
 }}'''
  with tempfile.TemporaryDirectory() as temp:
   path=pathlib.Path(temp);(path/'ResamplerCheck.java').write_text(harness)
   subprocess.run(['javac','--release','8','-d',temp,str(source),str(path/'ResamplerCheck.java')],check=True,capture_output=True,text=True)
   result=subprocess.run(['java','-cp',temp,'ResamplerCheck'],check=True,capture_output=True,text=True)
   self.assertIn('PASS',result.stdout)
