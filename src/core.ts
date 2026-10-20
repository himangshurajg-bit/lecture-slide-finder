export interface Cue { start: number; end: number; text: string }
export const MAX_TRANSCRIPT = 5 * 1024 * 1024;
export function timecode(value: string): number {
  const match = value.trim().match(/^(?:(\d{1,3}):)?([0-5]\d):([0-5]\d)[.,](\d{3})$/);
  if (!match) throw new Error(`Invalid timestamp: ${value}`);
  return Number(match[1] || 0) * 3600 + Number(match[2]) * 60 + Number(match[3]) + Number(match[4]) / 1000;
}
export function parseTranscript(raw: string, name: string): Cue[] {
  if (new TextEncoder().encode(raw).length > MAX_TRANSCRIPT) throw new Error('Transcript must be smaller than 5 MB.');
  let cues: Cue[];
  if (name.toLowerCase().endsWith('.json')) {
    const data = JSON.parse(raw);
    const rows = Array.isArray(data) ? data : data && typeof data === 'object' ? data.segments : undefined;
    if (!Array.isArray(rows)) throw new Error('JSON needs an array of {start, end, text} segments.');
    cues = rows.map((row: unknown) => {
      if (!row || typeof row !== 'object') throw new Error('Invalid transcript segment.');
      const r = row as Record<string, unknown>;
      if (typeof r.start !== 'number' || typeof r.end !== 'number' || typeof r.text !== 'string') throw new Error('Segments need numeric start/end and text.');
      return { start: r.start, end: r.end, text: r.text.trim() };
    });
  } else if (/\.(srt|vtt)$/i.test(name)) {
    const blocks = raw.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n').trim().split(/\n\s*\n/);
    cues = [];
    for (const block of blocks) {
      const lines = block.split('\n');
      if (/^(WEBVTT|NOTE|STYLE|REGION)(\s|$)/.test(lines[0])) continue;
      const at = lines.findIndex(line => line.includes('-->'));
      if (at < 0) throw new Error('A subtitle block is missing its timing line.');
      const parts = lines[at].split('-->');
      if (parts.length !== 2) throw new Error('Invalid subtitle timing.');
      cues.push({ start: timecode(parts[0]), end: timecode(parts[1].trim().split(/\s+/)[0]), text: lines.slice(at + 1).join(' ').replace(/<[^>]*>/g, '').trim() });
    }
  } else throw new Error('Choose an .srt, .vtt or .json transcript.');
  if (!cues.length || cues.length > 20000) throw new Error('Use a transcript with 1–20,000 segments.');
  for (const cue of cues) {
    if (!Number.isFinite(cue.start) || !Number.isFinite(cue.end) || cue.start < 0 || cue.end <= cue.start || cue.end > 604800 || !cue.text || cue.text.length > 10000) throw new Error('Transcript has empty text or invalid time ranges.');
  }
  return cues.sort((a,b) => a.start - b.start);
}
export function searchCues(cues: Cue[], query: string): Cue[] {
  const terms = query.toLocaleLowerCase().trim().split(/\s+/).filter(Boolean);
  return cues.filter(cue => terms.every(term => cue.text.toLocaleLowerCase().includes(term)));
}
export function formatTime(seconds: number): string {
  const n = Math.max(0, Math.floor(seconds));
  return `${Math.floor(n / 60).toString().padStart(2,'0')}:${(n % 60).toString().padStart(2,'0')}`;
}
export function notesMarkdown(name: string, notes: Cue[]): string {
  const clean = (s: string) => s.replace(/[\r\n]+/g, ' ').replace(/[\\`*_{}\[\]<>#!|]/g, '\\$&');
  return `# Study notes — ${clean(name)}\n\n` + notes.map(c => `- **${formatTime(c.start)}–${formatTime(c.end)}** ${clean(c.text)}`).join('\n') + '\n';
}
