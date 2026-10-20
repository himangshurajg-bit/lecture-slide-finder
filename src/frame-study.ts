import {createWorker, type Worker} from 'tesseract.js';
import {FRAME_KEY, MAX_TEXT, addFrameText, decodeFrameStudy, frameMarkdown, type FrameStudy, type StudyFrame} from './frame-points';
import {formatTime} from './core';

type Hooks={video: HTMLVideoElement; source:()=>{name:string;url:string}; asrBusy:()=>boolean; lock:(busy:boolean)=>void};
export function installFrameStudy(hooks: Hooks) {
 const native=location.origin==='https://appassets.androidplatform.net';
 const section=document.createElement('section');section.className='glass frame-study';section.setAttribute('aria-label','Frame gallery and visible points');
 section.innerHTML=`<div class="panel-top"><span class="section-label">04 / SEE THE WHOLE LECTURE</span><span class="badge">LOCAL IMAGE TEXT</span></div>
 <h2>Frames into study points.</h2><p>Browse decoded frames in order. Read the words visible on each slide as editable, timestamped points—not the spoken transcript.</p>
 <details id="all-frames"><summary>All frames · gallery</summary><p class="tiny">Android: four consecutive decoder-output frames per page, not sampled keyframes. Only the latest four pages stay in memory. Older pages can be reached by restarting. Exact output depends on the device decoder; an error stops coverage rather than skipping frames.</p>
 <div class="actions"><button id="frames-start">Open from first frame</button><button id="frames-prev" disabled>Previous page</button><button id="frames-next" disabled>Next four frames</button></div>
 <div id="frame-gallery" class="frame-gallery"></div><p id="gallery-state" role="status">Open a lecture first.</p></details>
 <label for="ocr-language">Visible text language</label><select id="ocr-language"><option value="eng">English</option><option value="hin">Hindi</option><option value="eng+hin">English + Hindi</option></select>
 <div class="actions"><button id="frames-scan">Read every decoded frame</button><button id="frames-current">Read current picture</button><button id="frames-stop" hidden>Stop analysis</button><button id="frames-export" disabled>Export frame points</button></div>
 <p class="tiny">Offline OCR assets are bundled. Every-frame OCR can take much longer than playback and use substantial battery. Keep this screen open. Images are scaled to at most 1280 px; tiny text, handwriting and equations may be wrong or missed. Review against the image. No summaries or extra facts are invented.</p>
 <p id="ocr-state" role="status">No frames analysed.</p><label class="search-box"><input id="points-query" type="search" placeholder="Find words in visible points…" aria-label="Search frame points"></label>
 <div id="frame-points"></div><div class="actions"><button id="points-prev" disabled>Previous points</button><button id="points-next" disabled>More points</button></div><p id="points-count" class="tiny"></p><dialog id="exact-frame"><button id="close-frame">Close frame</button><img id="exact-image" alt="Exact decoded frame"><p id="exact-caption"></p><div class="actions"><button id="exact-prev">Previous frame</button><button id="exact-next">Next frame</button><button id="exact-seek">Seek playback to this time</button></div></dialog>`;
 document.querySelector('main')!.insertBefore(section,document.querySelector('#storage-warning'));
 const $=<T extends HTMLElement>(id:string)=>section.querySelector<T>('#'+id)!;
 let study: FrameStudy={version:1,source:'',name:'Lecture',points:[],decoded:0,checked:0,complete:false,manual:0};
 let pointOffset=0,exactIndex=0;
 let pages: StudyFrame[][]=[],pageOffset=0,pageIndex=-1,done=false,busy=false,scanning=false,worker:Worker|null=null,token=0,requestId=0;
 let pending: {id:number;resolve:(v:FrameBatch)=>void;reject:(e:Error)=>void;timer:number}|null=null;
 type FrameBatch={frames: StudyFrame[]; decoded:number;duration:number;done:boolean;error?:string};
 const message=(s:string)=>{$('ocr-state').textContent=s;};
 const save=()=>{try{localStorage.setItem(FRAME_KEY,JSON.stringify(study));}catch{message('Storage is full or unavailable. Export the completed points before closing.');}};
 function renderPoints(){
  const query=$<HTMLInputElement>('points-query').value.toLocaleLowerCase();const list=$('frame-points');list.textContent='';
  const found=study.points.filter(p=>p.text.toLocaleLowerCase().includes(query));
  pointOffset=Math.min(pointOffset,Math.max(0,Math.floor((found.length-1)/100)*100));
  for(const p of found.slice(pointOffset,pointOffset+100)){
   const item=document.createElement('article');item.className='visible-point';const button=document.createElement('button');button.className='time';button.textContent=`${formatTime(p.start)} · ${p.manual?'manual picture':`frame ${p.firstFrame+1}`}`;
   button.onclick=()=>{if(!hooks.source().url){message('Select the same lecture again to jump to this point.');return;}hooks.video.currentTime=p.start;hooks.video.pause();message(`Selected frame time ${p.start.toFixed(3)} s. Playback seeks may show a nearby frame; use the gallery to inspect decoded frames.`);};
   const list=document.createElement('ul');for(const line of p.text.split('\n').filter(Boolean)){const li=document.createElement('li');li.textContent=line;list.append(li);}
   const note=document.createElement('p');note.className='tiny';note.textContent=`${p.manual?'Manual picture; no frame index':`Frames ${p.firstFrame+1}–${p.lastFrame+1}`} · OCR confidence ${Math.round(p.confidence)}% (not a correctness score)${p.edited?' · edited':''}`;
   const edit=document.createElement('details');const summary=document.createElement('summary');summary.textContent='Correct extracted text';const area=document.createElement('textarea');area.value=p.text;area.setAttribute('aria-label',`Edit frame ${p.firstFrame+1} text`);area.maxLength=12000;
   const apply=document.createElement('button');apply.textContent='Save correction';apply.onclick=()=>{if(study.points.reduce((n,p)=>n+p.original.length+p.text.length,0)-p.text.length+area.value.length>MAX_TEXT){message('Text storage limit reached. Export points before making a longer correction.');return;}p.text=area.value;p.edited=p.text!==p.original;save();renderPoints();};edit.append(summary,area,apply);item.append(button,list,note,edit);
   $('frame-points').append(item);
  }
  $('points-count').textContent=`${found.length} text group${found.length===1?'':'s'}${found.length>100?' · showing '+(pointOffset+1)+'–'+Math.min(pointOffset+100,found.length):''}. Repeated consecutive identical OCR text is grouped; blank or unreadable frames add no point.`;
  $<HTMLButtonElement>('frames-export').disabled=!study.points.length;$<HTMLButtonElement>('points-prev').disabled=pointOffset===0;$<HTMLButtonElement>('points-next').disabled=pointOffset+100>=found.length;
 }
 function setBusy(value:boolean){busy=value;hooks.lock(value);for(const id of ['frames-start','frames-next','frames-prev','frames-scan','frames-current','ocr-language'])$<HTMLButtonElement>(id).disabled=value; $('frames-stop').hidden=!value;
  if(!value){$<HTMLButtonElement>('frames-next').disabled=!native||!pages.length||(done&&pageIndex===pages.length-1);$<HTMLButtonElement>('frames-prev').disabled=pageIndex<=0;$<HTMLButtonElement>('frames-scan').disabled=!native;$<HTMLButtonElement>('frames-start').disabled=!native;}
 }
 function bridge(action:'reset'|'next'):Promise<FrameBatch>{
  return new Promise((resolve,reject)=>{const id=++requestId;const timer=window.setTimeout(()=>{if(pending?.id===id){pending=null;reject(new Error('Frame decoding timed out. Restart to continue; full coverage is not established.'));}},90000);pending={id,resolve,reject,timer};window.prompt('lecture-frames-v1',JSON.stringify({action,request:id}));});
 }
 window.addEventListener('lecture-frames-result',e=>{
  if(!native||!(e instanceof CustomEvent)||typeof e.detail!=='string')return;
  try{const result=JSON.parse(e.detail);if(!pending||result.request!==pending.id)return;const p=pending;clearTimeout(p.timer);
   if(result.error){pending=null;p.reject(new Error(result.error));return;}
   if(!Array.isArray(result.frames)||result.frames.length>4||!Number.isInteger(result.decoded)||typeof result.done!=='boolean'||!Number.isFinite(result.duration)||result.frames.some((f:StudyFrame)=>!Number.isInteger(f.index)||f.index<0||!Number.isFinite(f.time)||f.time<0||typeof f.image!=='string'||!f.image.startsWith('data:image/jpeg;base64,')))throw new Error('Invalid frame response');
   pending=null;p.resolve(result);
  }catch{pending?.reject(new Error('Cannot read decoded frames.'));pending=null;}
 });
 function closeDecoder(){if(native)window.prompt('lecture-frames-v1',JSON.stringify({action:'close',request:++requestId}));}
 function renderGallery(){const box=$('frame-gallery');box.textContent='';for(const frame of pages[pageIndex]||[]){const card=document.createElement('button');card.className='gallery-frame';const image=document.createElement('img');image.src=frame.image;image.alt=`Decoded frame ${frame.index+1} at ${frame.time.toFixed(3)} seconds`;const caption=document.createElement('span');caption.textContent=`${frame.index+1} · ${frame.time.toFixed(3)} s`;card.onclick=()=>{exactIndex=(pages.flat()).findIndex(f=>f.index===frame.index);renderExact();$<HTMLDialogElement>('exact-frame').showModal();};card.append(image,caption);box.append(card);}
  $('gallery-state').textContent=pages.length?`Page ${pageOffset+pageIndex+1} · ${done?'end of decoded video':'consecutive decoded frames; more may follow'}. Tap a frame to seek its time.`:'No decoded frames.';
 }
 function renderExact(){const frames=pages.flat(),frame=frames[exactIndex];if(!frame)return;$<HTMLImageElement>('exact-image').src=frame.image;$('exact-caption').textContent=`Exact decoder output frame ${frame.index+1} · ${frame.time.toFixed(3)} s. Step within the retained pages; close and load the next page to continue.`;$<HTMLButtonElement>('exact-prev').disabled=exactIndex===0;$<HTMLButtonElement>('exact-next').disabled=exactIndex>=frames.length-1;}
 $('close-frame').onclick=()=>$<HTMLDialogElement>('exact-frame').close();$('exact-prev').onclick=()=>{exactIndex--;renderExact();};$('exact-next').onclick=()=>{exactIndex++;renderExact();};$('exact-seek').onclick=()=>{const f=pages.flat()[exactIndex];if(f){hooks.video.pause();hooks.video.currentTime=f.time;}};
 function addPage(batch:FrameBatch){done=batch.done;if(batch.frames.length){pages.push(batch.frames);if(pages.length>4){pages.shift();pageOffset++;}pageIndex=pages.length-1;}renderGallery();}
 async function getWorker(guard:number){if(worker)return worker;message('Preparing bundled offline OCR…');
  const base=new URL('ocr/',document.baseURI).href;
  const created=await createWorker($<HTMLSelectElement>('ocr-language').value,1,{workerPath:base+'worker.min.js',corePath:base,langPath:base+'lang',gzip:false,workerBlobURL:false,cacheMethod:'none'});
  if(guard!==token){await created.terminate();throw new Error('Analysis cancelled.');}worker=created;return created;
 }
 async function recognize(frame:StudyFrame,guard:number){const ocr=await getWorker(guard);const result=await ocr.recognize(frame.image);if(guard!==token)throw new Error('Analysis cancelled.');addFrameText(study.points,frame,result.data.text,Math.max(0,Math.min(100,result.data.confidence)));if(!frame.manual)study.checked++;save();renderPoints();}
 async function stop(note='Analysis stopped. Completed points are kept; coverage is partial.') {token++;scanning=false;pending?.reject(new Error(note));if(pending)clearTimeout(pending.timer);pending=null;closeDecoder();const old=worker;worker=null;if(old)void old.terminate();setBusy(false);message(note);save();}
 async function finish(){closeDecoder();const old=worker;worker=null;if(old)await old.terminate();setBusy(false);}
 function ready(){if(hooks.asrBusy()){message('Wait for transcription to finish first.');return false;}if(!hooks.source().url){message('Open a local lecture video first.');return false;}return true;}
 $('frames-start').onclick=async()=>{if(!ready()||busy)return;const guard=++token;pages=[];pageIndex=-1;pageOffset=0;done=false;setBusy(true);try{addPage(await bridge('reset'));}catch(e){if(guard===token)$('gallery-state').textContent=(e as Error).message;}finally{if(guard===token)setBusy(false);}};
 $('frames-next').onclick=async()=>{if(busy)return;if(pageIndex<pages.length-1){pageIndex++;renderGallery();setBusy(false);return;}const guard=token;setBusy(true);try{addPage(await bridge('next'));}catch(e){if(guard===token)$('gallery-state').textContent=(e as Error).message;}finally{if(guard===token)setBusy(false);}};
 $('frames-prev').onclick=()=>{if(pageIndex>0){pageIndex--;renderGallery();setBusy(false);}};
 $('frames-scan').onclick=async()=>{
  if(!ready()||busy)return;
  if(!confirm('Read every decoded frame from the beginning? This can take much longer than playback. Existing frame points will be replaced; export them first.'))return;
  const guard=++token;study.points=[];study.manual=0;study.checked=0;study.decoded=0;study.complete=false;pages=[];pageOffset=0;pageIndex=-1;done=false;scanning=true;hooks.video.pause();setBusy(true);save();renderPoints();
  try{let action:'reset'|'next'='reset';while(guard===token){const batch=await bridge(action);if(guard!==token)break;addPage(batch);study.decoded=batch.decoded;for(const frame of batch.frames){message(`Reading frame ${frame.index+1} at ${frame.time.toFixed(3)} s${batch.duration>0?' / '+formatTime(batch.duration):''}. Full coverage is pending.`);await recognize(frame,guard);}save();if(batch.done){study.complete=true;save();message(`Complete decoded-frame pass: ${study.checked} frames OCR-checked. Review all extracted text; OCR can miss content.`);break;}action='next';}}
  catch(e){if(guard===token)message(`${(e as Error).message} Coverage remains partial (${study.checked} checked).`);}
  finally{if(guard===token){scanning=false;await finish();save();}}
 };
 $('frames-current').onclick=async()=>{if(!ready()||busy)return;const guard=++token;setBusy(true);hooks.video.pause();try{if(hooks.video.readyState<2)throw new Error('Wait for a decoded video picture first.');const canvas=document.createElement('canvas'),v=hooks.video;const scale=Math.min(1,1280/Math.max(v.videoWidth,v.videoHeight));canvas.width=Math.max(1,Math.round(v.videoWidth*scale));canvas.height=Math.max(1,Math.round(v.videoHeight*scale));canvas.getContext('2d')!.drawImage(v,0,0,canvas.width,canvas.height);
  const frame={index:1000000000+study.manual,time:v.currentTime,image:canvas.toDataURL('image/jpeg',0.9),manual:true};study.manual++;study.complete=false;await recognize(frame,guard);message('Current displayed picture read. This is a partial, manual capture—not a decoded-frame index or whole-video analysis.');}catch(e){if(guard===token)message((e as Error).message);}finally{if(guard===token)await finish();}};
 $('frames-stop').onclick=()=>void stop();$('points-query').oninput=()=>{pointOffset=0;renderPoints();};$('points-prev').onclick=()=>{pointOffset-=100;renderPoints();};$('points-next').onclick=()=>{pointOffset+=100;renderPoints();};
 $('frames-export').onclick=()=>{const text=frameMarkdown(study);if(native){window.prompt('lecture-export-v1',text);return;}const url=URL.createObjectURL(new Blob([text],{type:'text/markdown;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='visible-frame-points.md';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
 document.addEventListener('visibilitychange',()=>{if(document.hidden&&(busy||pages.length))void stop('Stopped while away from the app. Completed points remain; restart frame browsing to continue.');});
 try{const raw=localStorage.getItem(FRAME_KEY);if(raw){study=decodeFrameStudy(raw);message(`Restored ${study.points.length} visible-text groups. ${study.complete?'A complete decoded pass was saved.':'Saved analysis is partial.'} Select the same video again to use timestamps.`);}}catch{message('Saved frame points could not be read. Export any other notes before clearing this session.');}
 if(!native)$('gallery-state').textContent='Exact decoded-frame traversal is available in the Android app. Browser mode can OCR the current displayed picture only.';
 setBusy(false);renderPoints();
 return {
  canOpen(file:File){const id=`${file.name}:${file.size}:${file.lastModified}`;return !study.points.length||!study.source||study.source===id||confirm('Open a different lecture and clear its frame points? Export frame points first to keep a copy.');},
  onOpen(file:File){const id=`${file.name}:${file.size}:${file.lastModified}`;if(busy||pages.length)void stop();pages=[];pageIndex=-1;pageOffset=0;done=false;renderGallery();if(study.source!==id){study={version:1,source:id,name:file.name,points:[],decoded:0,checked:0,complete:false,manual:0};save();renderPoints();message('Ready. Frame analysis stays on this device.');}setBusy(false);},
  clear(){void stop();study={version:1,source:'',name:'Lecture',points:[],decoded:0,checked:0,complete:false,manual:0};pages=[];pageIndex=-1;renderGallery();renderPoints();try{localStorage.removeItem(FRAME_KEY);}catch{}},
  close(){closeDecoder();}
 };
}
