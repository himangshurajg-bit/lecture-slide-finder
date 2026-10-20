/** Visible-image text, never spoken ASR or generated lecture facts. */
export type StudyFrame = {index: number; time: number; image: string; manual?: boolean};
export type FramePoint = {id: number; text: string; original: string; start: number; end: number; firstFrame: number; lastFrame: number; confidence: number; edited: boolean; manual: boolean};
export type FrameStudy = {version: 1; source: string; name: string; points: FramePoint[]; decoded: number; checked: number; complete: boolean; manual: number};
export const FRAME_KEY='lecture-frame-study-v1';
export const MAX_POINTS=2000;
export const MAX_TEXT=1000000;
export function normalizeText(text: string): string {return text.replace(/\r/g,'').split('\n').map(s=>s.trim().replace(/[\t ]+/g,' ')).filter(Boolean).join('\n');}
export function addFrameText(points: FramePoint[],frame: Pick<StudyFrame,'index'|'time'|'manual'>,text: string,confidence: number): void {
  const clean=normalizeText(text);if(!clean)return;
  const last=points[points.length-1];
  if(last && last.original===clean && !last.manual && !frame.manual && last.lastFrame===frame.index-1){last.lastFrame=frame.index;last.end=frame.time;last.confidence=Math.min(last.confidence,confidence);return;}
  if(clean.length>12000 || points.reduce((n,p)=>n+p.original.length+p.text.length,0)+2*clean.length>MAX_TEXT)throw new Error('Text storage limit reached. Export this partial analysis; no further frames were accepted.');
  if(points.length>=MAX_POINTS)throw new Error('Point limit reached. Export this partial analysis before continuing with another lecture.');
  points.push({id:frame.index,text:clean,original:clean,start:frame.time,end:frame.time,firstFrame:frame.index,lastFrame:frame.index,confidence,edited:false,manual:!!frame.manual});
}
export function decodeFrameStudy(raw: string): FrameStudy {
 const s=JSON.parse(raw);if(s && s.manual===undefined)s.manual=0;
 if(!s || s.version!==1 || typeof s.source!=='string'||typeof s.name!=='string'||!Array.isArray(s.points)||s.points.length>MAX_POINTS||!Number.isInteger(s.decoded)||s.decoded<0||!Number.isInteger(s.checked)||s.checked<0||s.checked>s.decoded||!Number.isInteger(s.manual)||s.manual<0||typeof s.complete!=='boolean')throw new Error('Invalid frame study');
 if(s.points.reduce((n:number,p:FramePoint)=>n+(typeof p.original==='string'?p.original.length:MAX_TEXT)+(typeof p.text==='string'?p.text.length:MAX_TEXT),0)>MAX_TEXT)throw new Error('Text storage limit');
 for(const p of s.points){if(p.manual===undefined)p.manual=false;if(!p||!Number.isInteger(p.id)||typeof p.text!=='string'||p.text.length>12000||typeof p.original!=='string'||p.original.length>12000||![p.start,p.end,p.confidence].every(Number.isFinite)||p.start<0||p.end<p.start||p.confidence<0||p.confidence>100||!Number.isInteger(p.firstFrame)||p.firstFrame<0||!Number.isInteger(p.lastFrame)||p.lastFrame<p.firstFrame||typeof p.manual!=='boolean'||typeof p.edited!=='boolean')throw new Error('Invalid point');}
 return s as FrameStudy;
}
export function frameMarkdown(study: FrameStudy): string {
 const lines=['# Visible frame text',`Source: ${study.name.replace(/[\r\n]/g,' ')}`,
  `Coverage: ${study.complete?'complete decoded-frame pass':'PARTIAL — not the whole video'}; ${study.checked} OCR-checked / ${study.decoded} decoded frames; ${study.manual} manual pictures.`,
  'OCR is fallible. These are extracted visible lines, not an AI summary, spoken transcript or verified equations. Edited text is marked.',''];
 for(const p of study.points){lines.push(`## ${p.start.toFixed(3)}–${p.end.toFixed(3)} s · ${p.manual?'manual picture (no frame index)':`frames ${p.firstFrame+1}–${p.lastFrame+1}`}${p.edited?' · edited':''}`);
 for(const line of p.text.split('\n').filter(Boolean))lines.push(`- ${line}`);
 if(p.edited)lines.push(`\nOriginal OCR: ${p.original.replace(/\n/g,' / ')}`);lines.push('');}
 return lines.join('\n');
}
