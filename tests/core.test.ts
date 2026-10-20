import { describe, expect, it } from 'vitest';
import { timecode, parseTranscript, searchCues, formatTime, notesMarkdown, MAX_TRANSCRIPT } from '../src/core';
const srt='1\n00:00:00,000 --> 00:00:04,000\nForce changes motion.\n\n2\n00:00:04,000 --> 00:00:08,000\nMass and acceleration.';
describe('timestamps',()=>{
 it.each([['00:01,500',1.5],['01:02:03.004',3723.004],['123:00:00.000',442800]])('parses %s',(value,seconds)=>expect(timecode(value)).toBe(seconds));
 it.each(['garbage','00:60:00,000','00:00:-1,000','00:00:01','00:00:00.9999'])('rejects %s',v=>expect(()=>timecode(v)).toThrow());
 it('formats a playback position',()=>expect(formatTime(125.8)).toBe('02:05'));
});
describe('transcripts',()=>{
 it('imports SRT',()=>expect(parseTranscript(srt,'test.srt')).toHaveLength(2));
 it('handles BOM and CRLF',()=>expect(parseTranscript('\uFEFF'+srt.replaceAll('\n','\r\n'),'TEST.SRT')[1].start).toBe(4));
 it('imports VTT with cue settings and markup',()=>expect(parseTranscript('WEBVTT\n\n00:00.000 --> 00:04.000 align:start\n<b>Force</b> changes motion.','x.vtt')[0].text).toBe('Force changes motion.'));
 it('skips VTT notes',()=>expect(parseTranscript('WEBVTT\n\nNOTE hello\nworld\n\n00:00.000 --> 00:04.000\nHi','x.vtt')).toHaveLength(1));
 it('imports JSON from transcription tool',()=>expect(parseTranscript('{"segments":[{"start":2,"end":4,"text":"hello"}]}','x.json')[0].start).toBe(2));
 it('sorts JSON segments',()=>expect(parseTranscript('[{"start":4,"end":6,"text":"b"},{"start":0,"end":3,"text":"a"}]','x.json')[0].text).toBe('a'));
 it.each(['[]','{}','null','[{"start":"0","end":4,"text":"x"}]','[{"start":-1,"end":4,"text":"x"}]','[{"start":4,"end":4,"text":"x"}]','[{"start":0,"end":4,"text":""}]','[{"start":0,"end":604801,"text":"x"}]'])('rejects bad JSON %s',raw=>expect(()=>parseTranscript(raw,'x.json')).toThrow());
 it('rejects missing times',()=>expect(()=>parseTranscript('1\ntext','x.srt')).toThrow());
 it('rejects nontranscript file',()=>expect(()=>parseTranscript(srt,'x.exe')).toThrow());
 it('rejects oversize data',()=>expect(()=>parseTranscript('x'.repeat(MAX_TRANSCRIPT+1),'x.srt')).toThrow());
 it('limits segment count',()=>expect(()=>parseTranscript(JSON.stringify(Array.from({length:20001},()=>({start:0,end:1,text:'x'}))),'x.json')).toThrow());
});
describe('search and export',()=>{
 const cues=parseTranscript(srt,'x.srt');
 it('case insensitive literal search',()=>expect(searchCues(cues,'FORCE')).toHaveLength(1));
 it('requires all words',()=>expect(searchCues(cues,'mass acceleration')).toHaveLength(1));
 it('returns no results',()=>expect(searchCues(cues,'quantum')).toHaveLength(0));
 it('treats regex characters literally',()=>expect(searchCues(cues,'[.*')).toHaveLength(0));
 it('blank returns all',()=>expect(searchCues(cues,'  ')).toHaveLength(2));
 it('exports timestamps',()=>expect(notesMarkdown('Lecture',cues)).toContain('00:04–00:08'));
 it('escapes Markdown and HTML',()=>expect(notesMarkdown('<script>',[{start:0,end:1,text:'[click](bad) <img>'}])).toContain('\\<img\\>'));
});
