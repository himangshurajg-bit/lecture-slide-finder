import { describe, it, expect } from 'vitest';
import {encodeSession,decodeSession} from '../src/session';
const cues=[{start:0,end:2,text:'Force and mass'},{start:2,end:3,text:'Acceleration'}];
describe('durable session validation',()=>{
 it('restores notes as references to cues',()=>{const s=decodeSession(encodeSession({cues,notes:[cues[1]],fileName:'Lecture.mp4',query:'acc'}));expect(s.notes[0]).toBe(s.cues[1]);expect(s.fileName).toBe('Lecture.mp4');expect(s.query).toBe('acc');});
 it('handles an empty session',()=>expect(decodeSession(encodeSession({cues:[],notes:[],fileName:'Lecture',query:''})).cues).toEqual([]));
 it('rejects corrupt, oversized or unsafe saved state',()=>{for(const raw of ['garbage','{}', 'a'.repeat(6*1024*1024),JSON.stringify({version:1,cues,selected:[999],fileName:'x',query:''}),JSON.stringify({version:1,cues:[{start:-1,end:2,text:'bad'}],selected:[],fileName:'x',query:''})])expect(()=>decodeSession(raw)).toThrow();});
});
