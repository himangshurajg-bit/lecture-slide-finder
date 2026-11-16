import {readFileSync} from 'node:fs';
import {describe, expect, it} from 'vitest';

describe('bundled Tesseract worker initialization errors',()=>{
 it('stops after rejecting failed initialization instead of resolving a rejected job',()=>{
  const worker=readFileSync(new URL('../public/ocr/worker.min.js',import.meta.url),'utf8');
  expect(worker).toContain('case 30:if(-1===b){r.reject("initialization failed");t.next=38;break}r.progress({workerId:n,status:c,progress:1}),r.resolve()');
  expect(worker).not.toContain('case 30:-1===b&&r.reject("initialization failed"),r.progress({workerId:n,status:c,progress:1}),r.resolve()');
 });
});
