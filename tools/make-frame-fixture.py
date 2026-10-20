"""Original, deterministic six-frame teaching fixture. No private media."""
from PIL import Image,ImageDraw,ImageFont
from pathlib import Path
import subprocess,json
out=Path('tests/fixtures/frames');out.mkdir(parents=True,exist_ok=True)
latin='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
hindi='/usr/share/fonts/truetype/noto/NotoSansDevanagari-Regular.ttf'
for i in range(6):
 im=Image.new('RGB',(960,540),'#f2faf7');d=ImageDraw.Draw(im)
 d.text((54,45),'PHYSICS / ORIGINAL TEST SLIDE',font=ImageFont.truetype(latin,20),fill='#31565a')
 d.text((54,118),'Mass and acceleration',font=ImageFont.truetype(latin,42),fill='#102f30')
 d.text((54,205),'Force equals mass times acceleration.',font=ImageFont.truetype(latin,32),fill='#102f30')
 d.text((54,280),'बल गति बदलता है',font=ImageFont.truetype(hindi,46),fill='#102f30')
 d.text((54,410),f'Frame {i+1}',font=ImageFont.truetype(latin,30),fill='#31565a')
 im.save(out/f'{i:02}.png')
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-framerate','2','-i',str(out/'%02d.png'),'-c:v','libx264','-pix_fmt','yuv420p','-bf','2','-movflags','+faststart','tests/fixtures/frame-study.mp4'],check=True)
Path('tests/fixtures/frame-study.expected.json').write_text(json.dumps({'frames':6,'times':[0,.5,1,1.5,2,2.5],'english':['Mass and acceleration','Force equals mass times acceleration.'],'hindi':'बल गति बदलता है'},ensure_ascii=False,indent=2))
