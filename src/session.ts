import { parseTranscript, MAX_TRANSCRIPT, type Cue } from './core';
export interface Session { cues: Cue[]; notes: Cue[]; fileName: string; query: string }
export const SESSION_KEY = 'lecture-session-v1';
export function encodeSession(session: Session): string {
  const data = JSON.stringify({version:1, cues:session.cues, selected:session.notes.map(n=>session.cues.indexOf(n)).filter(n=>n>=0), fileName:session.fileName, query:session.query});
  if (data.length > MAX_TRANSCRIPT) throw new Error('Session is too large to save. Export your notes.');
  return data;
}
export function decodeSession(raw: string): Session {
  if(raw.length>MAX_TRANSCRIPT) throw new Error('Saved session is too large.');
  const data = JSON.parse(raw);
  if (data.version!==1 || !Array.isArray(data.cues) || !Array.isArray(data.selected) || typeof data.fileName!=='string' || data.fileName.length>1024 || typeof data.query!=='string' || data.query.length>10000) throw new Error('Invalid saved session.');
  const cues = data.cues.length ? parseTranscript(JSON.stringify(data.cues), 'saved.json') : [];
  const indices: number[] = data.selected;
  if (indices.some(i=>!Number.isInteger(i)||i<0||i>=cues.length)) throw new Error('Invalid selected notes.');
  return {cues, notes:[...new Set(indices)].map(i=>cues[i]), fileName:data.fileName, query:data.query};
}
