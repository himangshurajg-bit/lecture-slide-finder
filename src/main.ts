import { parseTranscript, searchCues, formatTime, notesMarkdown, MAX_TRANSCRIPT, type Cue } from './core';
import './style.css';
const root = document.querySelector<HTMLDivElement>('#app')!;
root.innerHTML = `<header class="top"><a class="brand" href="./" aria-label="Lecture home"><span class="brand-icon">L<span>↗</span></span>lecture<span class="brand-dot">.</span></a><span class="privacy"><span class="light"></span> Local files. Just yours.</span><button id="install" hidden>Install app</button></header>
<main><section class="intro"><div class="eyebrow">YOUR KNOWLEDGE, A LITTLE CLOSER</div><h1>Find the moment.<br><span>Keep the idea.</span></h1><p>Turn a long lecture into a few good notes.<br>Search the words. See the frame. Pick up where it clicks.</p></section>
<div class="workspace"><section class="glass player-panel" aria-label="Lecture player"><div class="panel-top"><span class="section-label">01 / THE LECTURE</span><span class="badge" id="video-badge">ON YOUR DEVICE</span></div><div class="video-wrap"><video id="video" controls playsinline preload="metadata" aria-label="Lecture video"></video><div class="empty-player" id="empty"><div class="play-symbol">▷</div><h2>A fresh perspective</h2><p>Start with a video from your device.</p><button class="button primary" data-pick="video-file">Open lecture <span>↗</span></button><span class="tiny">MP4 recommended · nothing uploaded</span></div></div><div class="file-bar"><div><span class="file-label">CURRENT LECTURE</span><p id="filename">No video selected</p></div><button class="button small" data-pick="video-file">Change video</button></div><input id="video-file" type="file" aria-label="Choose lecture video" accept="video/*,.mp4,.webm,.mov" class="visually-hidden"><div class="frame-box" id="frame-box" hidden><div><span class="section-label">MATCHING VIDEO FRAME</span><span id="frame-time"></span></div><canvas id="frame" aria-label="Video frame at selected transcript time" role="img"></canvas><p class="tiny">A frame at this moment, not automatic slide recognition.</p></div></section>
<section class="glass transcript-panel" aria-label="Transcript search"><div class="panel-top"><span class="section-label">02 / FIND AN IDEA</span><span class="badge" id="cue-count">NO TRANSCRIPT</span></div><div class="transcript-intro"><h2>Words worth finding.</h2><p>Add a timestamped transcript to explore your lecture.</p><div class="actions"><button class="button primary" data-pick="transcript-file">Import transcript <span>+</span></button><button id="demo">Try a sample</button></div><input id="transcript-file" type="file" aria-label="Choose transcript" accept=".srt,.vtt,.json" class="visually-hidden"><details><summary>Need to generate a transcript?</summary><p>Run the included local Python transcription tool on a computer, then import its JSON here. It downloads a speech model once; no paid API or media upload is needed. Android users can import an existing SRT, VTT or JSON file. On-device Android transcription isn’t included yet.</p><code>uv run tools/transcribe.py lecture.mp4</code><p>See the repository README for installation and language options.</p></details></div><label class="search-box"><span aria-hidden="true">⌕</span><input id="query" type="search" placeholder="Search transcript words…" aria-label="Search transcript" disabled><kbd>/</kbd></label><div id="result-summary" class="result-summary">Your transcript will appear here.</div><div id="results" class="results"><div class="empty-results"><span>≋</span><p>Less scrubbing.<br>More understanding.</p></div></div></section></div>
<section class="glass notes-panel" aria-label="Study notes"><div class="notes-heading"><div><span class="section-label">03 / TAKE IT WITH YOU</span><h2>Your little collection <span id="notes-count">0</span></h2></div><button id="export" disabled>Export notes <span>↗</span></button></div><p id="notes-empty">Save a passage from the transcript. Your notes stay in this tab until you export them.</p><div id="notes"></div></section><p id="status" role="status" aria-live="polite"></p><footer><span>MADE FOR THE CURIOUS.</span><span>No account. No cloud. One idea at a time.</span></footer></main>`;
const $ = <T extends HTMLElement>(s: string) => document.querySelector<T>(s)!;
document.querySelectorAll<HTMLButtonElement>('[data-pick]').forEach(button=>button.addEventListener('click',()=> $<HTMLInputElement>('#'+button.dataset.pick).click()));
const video = $<HTMLVideoElement>('#video');
let cues: Cue[] = [], notes: Cue[] = [], fileName = 'Lecture', videoUrl = '', loadGeneration = 0, frameGeneration = 0;
let extractor: HTMLVideoElement | null = null;
const status = (text: string, error = false) => { $('#status').textContent = text; $('#status').classList.toggle('error', error); };
function render() {
  const list = $('#results'); list.replaceChildren();
  const matches = searchCues(cues, $<HTMLInputElement>('#query').value);
  $('#result-summary').textContent = cues.length ? `${matches.length} passage${matches.length === 1 ? '' : 's'}${matches.length > 150 ? ' · showing first 150; narrow your search' : ''}` : 'Your transcript will appear here.';
  if (cues.length && !matches.length) { const p = document.createElement('p'); p.className='no-match'; p.textContent='No matching passages. Try a different word.'; list.append(p); }
  matches.slice(0,150).forEach(cue => {
    const row = document.createElement('article'); row.className='cue';
    const seek = document.createElement('button'); seek.className='time'; seek.textContent=formatTime(cue.start); seek.setAttribute('aria-label', `Jump to ${formatTime(cue.start)}`); seek.addEventListener('click', () => void jump(cue));
    const text = document.createElement('p'); text.textContent=cue.text;
    const save = document.createElement('button'); const saved=notes.includes(cue); save.className='save'; save.textContent=saved ? '✓' : '+'; save.setAttribute('aria-label', saved ? `Remove note at ${formatTime(cue.start)}` : `Save note at ${formatTime(cue.start)}`); save.setAttribute('aria-pressed',String(saved)); save.addEventListener('click',()=>{ notes=saved?notes.filter(n=>n!==cue):[...notes,cue]; render(); renderNotes(); });
    row.append(seek,text,save); list.append(row);
  });
}
function renderNotes() {
  $('#notes-count').textContent=String(notes.length); $<HTMLButtonElement>('#export').disabled=!notes.length; $('#notes-empty').hidden=!!notes.length;
  const list=$('#notes'); list.replaceChildren(); notes.forEach(cue=>{ const p=document.createElement('p'); p.textContent=`${formatTime(cue.start)}  ${cue.text}`; list.append(p); });
}
async function jump(cue: Cue) {
  if (!videoUrl || video.readyState<1) { status('Open a playable video first to jump to this passage.',true); return; }
  if (cue.start >= video.duration) { status('This passage is beyond the video duration. Check that the transcript matches.',true); return; }
  const generation=++frameGeneration;
  video.currentTime=cue.start;
  status(`Selected ${formatTime(cue.start)}. Press play when you’re ready.`);
  extractor?.pause(); if (extractor) { extractor.removeAttribute('src'); extractor.load(); }
  const clip=document.createElement('video'); extractor=clip; clip.muted=true; clip.preload='auto'; clip.playsInline=true;
  try {
    await new Promise<void>((resolve,reject)=>{ const timeout=window.setTimeout(()=>reject(new Error('Frame preview timed out. Playback is still available.')),12000); clip.onloadedmetadata=()=>{ clip.currentTime=Math.min(cue.start+0.03, Math.max(0,clip.duration-0.05)); }; clip.onseeked=()=>{clearTimeout(timeout);resolve();}; clip.onerror=()=>{clearTimeout(timeout);reject(new Error('This video cannot produce a preview in this browser.'));}; clip.src=videoUrl; });
    if (generation!==frameGeneration) return;
    const canvas=$<HTMLCanvasElement>('#frame'); canvas.width=640; canvas.height=Math.round(640*clip.videoHeight/clip.videoWidth); const ctx=canvas.getContext('2d'); if (!ctx) throw new Error('Frame previews are unavailable.'); ctx.drawImage(clip,0,0,canvas.width,canvas.height); $('#frame-time').textContent=formatTime(cue.start); $('#frame-box').hidden=false;
  } catch(err) { if (generation===frameGeneration) status((err as Error).message,true); }
  finally { clip.removeAttribute('src'); clip.load(); if(extractor===clip)extractor=null; }
}
$<HTMLInputElement>('#video-file').addEventListener('change',event=>{
  const input=event.target as HTMLInputElement;
  const file=input.files?.[0]; if(!file)return;
  if(videoUrl && notes.length && !window.confirm('Change the lecture and clear its transcript and notes? Export your notes first if you want to keep them.')) { input.value='';return; }
  if(videoUrl) { cues=[];notes=[];$('#cue-count').textContent='NO TRANSCRIPT';$<HTMLInputElement>('#query').value='';$<HTMLInputElement>('#query').disabled=true;render();renderNotes(); }
  input.value='';
  ++frameGeneration; extractor?.pause(); if(extractor){extractor.removeAttribute('src');extractor.load();extractor=null;}
  video.pause(); video.removeAttribute('src'); video.load(); if(videoUrl)URL.revokeObjectURL(videoUrl);
  videoUrl=URL.createObjectURL(file); fileName=file.name; video.src=videoUrl; video.hidden=false; $('#empty').hidden=true; $('#filename').textContent=file.name; $('#frame-box').hidden=true; status('Video opened locally. Add its matching transcript.');
});
video.addEventListener('error',()=>status('Your browser cannot play this file. Try H.264/AAC MP4 or another supported format.',true));
video.addEventListener('loadedmetadata',()=>{ $('#video-badge').textContent=formatTime(video.duration); });
function setTranscript(next: Cue[], label: string){ if(notes.length && !window.confirm('Replace this transcript and clear its selected notes? Export your notes first if you want to keep them.')) return; cues=next;notes=[];$<HTMLInputElement>('#query').value='';$<HTMLInputElement>('#query').disabled=false;$('#cue-count').textContent=`${cues.length} PASSAGES`;render();renderNotes();status(`${label} loaded. Search, jump or save a passage.`); }
$<HTMLInputElement>('#transcript-file').addEventListener('change',async event=>{
  const file=(event.target as HTMLInputElement).files?.[0];if(!file)return;const generation=++loadGeneration;
  try { if(file.size>MAX_TRANSCRIPT)throw new Error('Transcript must be smaller than 5 MB.'); const next=parseTranscript(await file.text(),file.name);if(generation===loadGeneration)setTranscript(next,file.name); }
  catch(err){if(generation===loadGeneration)status((err as Error).message,true);} finally {(event.target as HTMLInputElement).value='';}
});
$('#demo').addEventListener('click',()=>{++loadGeneration;setTranscript([{start:0,end:4,text:'A force changes the motion of an object.'},{start:4,end:8,text:'Newton’s second law connects force, mass and acceleration.'},{start:8,end:12,text:'Double the force and the acceleration doubles, if mass stays the same.'}], 'Sample transcript (illustrative, no video attached)');});
$('#query').addEventListener('input',render);
$('#export').addEventListener('click',()=>{const url=URL.createObjectURL(new Blob([notesMarkdown(fileName,notes)],{type:'text/markdown;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='lecture-notes.md';a.click();setTimeout(()=>URL.revokeObjectURL(url),30000);status('Notes exported. Keep the download before closing this tab.');});
window.addEventListener('keydown',e=>{if(e.key==='/'&&!(e.target instanceof HTMLInputElement)&&!(e.target instanceof HTMLTextAreaElement)){e.preventDefault();$('#query').focus();}});
window.addEventListener('beforeunload',e=>{if(notes.length){e.preventDefault();e.returnValue='';}});
if('serviceWorker'in navigator)window.addEventListener('load',()=>{navigator.serviceWorker.register('./sw.js').catch(()=>{});});
let installPrompt: (Event & {prompt:()=>Promise<void>})|null=null;
window.addEventListener('beforeinstallprompt',e=>{e.preventDefault();installPrompt=e as typeof installPrompt;$('#install').hidden=false;});
$('#install').addEventListener('click',async()=>{await installPrompt?.prompt();installPrompt=null;$('#install').hidden=true;});

// A brief, bounded water ring on deliberate taps. No background animation or media access.
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
document.addEventListener('pointerdown', event => {
  if (reducedMotion.matches || event.button !== 0 || !(event.target instanceof Element)) return;
  const button = event.target.closest<HTMLElement>('button, .button, summary, .search-box, .brand');
  if (!button || button.matches(':disabled')) return;
  button.querySelector('.water-ripple')?.remove();
  const bounds = button.getBoundingClientRect();
  const ring = document.createElement('span');
  ring.className = 'water-ripple';
  ring.setAttribute('aria-hidden', 'true');
  ring.style.left = `${event.clientX - bounds.left}px`;
  ring.style.top = `${event.clientY - bounds.top}px`;
  button.append(ring);
  ring.addEventListener('animationend', () => ring.remove(), {once: true});
  window.setTimeout(() => ring.remove(), 800);
});
reducedMotion.addEventListener('change', () => {
  if (reducedMotion.matches) document.querySelectorAll('.water-ripple').forEach(ring => ring.remove());
});
