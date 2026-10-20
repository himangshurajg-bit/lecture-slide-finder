import {test,expect} from '@playwright/test';
import path from 'node:path';
const fixture=path.resolve('tests/fixtures/lecture.mp4');
const transcript='1\n00:00:00,000 --> 00:00:04,000\nForce changes motion.\n\n2\n00:00:04,000 --> 00:00:08,000\nMass and acceleration.\n\n3\n00:00:08,000 --> 00:00:12,000\nDouble the force.';
test('import, search, seek, frame capture, note export and responsive layout',async({page},testInfo)=>{
 const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('/');await expect(page.getByRole('heading',{name:'Find the moment. Keep the idea.'})).toBeVisible();
 await page.locator('#video-file').setInputFiles(fixture);
 await expect.poll(()=>page.locator('video').first().evaluate((el:HTMLVideoElement)=>el.readyState)).toBeGreaterThan(0);
 await page.locator('#transcript-file').setInputFiles({name:'lecture.srt',mimeType:'text/plain',buffer:Buffer.from(transcript)});
 await page.getByRole('searchbox').fill('mass');await expect(page.locator('.cue')).toHaveCount(1);
 await page.getByRole('button',{name:'Jump to 00:04',exact:true}).click();
 await expect(page.locator('#frame-box')).toBeVisible();
 // The second slide's known synthetic background proves the canvas is a decoded frame, not a placeholder.
 const pixel=await page.locator('#frame').evaluate((canvas:HTMLCanvasElement)=>Array.from(canvas.getContext('2d')!.getImageData(5,5,1,1).data));
 expect(pixel[0]).toBeGreaterThan(210);expect(pixel[0]).toBeLessThan(225);expect(pixel[1]).toBeGreaterThan(225);
 expect(await page.locator('#video').evaluate((v:HTMLVideoElement)=>v.currentTime)).toBeCloseTo(4,0);
 await page.getByRole('button',{name:'Save note at 00:04'}).click();
 const downloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:'Export notes'}).click();const download=await downloadPromise;
 const stream=await download.createReadStream();let content='';for await(const chunk of stream!) content+=chunk.toString();expect(content).toContain('Mass and acceleration.');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth)).toBe(true);
 await page.screenshot({path:`docs/${testInfo.project.name}.png`,fullPage:true});expect(errors).toEqual([]);
});
test('bad import preserves transcript and hostile text cannot execute',async({page})=>{
 await page.goto('/');await page.getByRole('button',{name:'Try a sample'}).click();
 await page.locator('#transcript-file').setInputFiles({name:'bad.json',mimeType:'application/json',buffer:Buffer.from('[{"start":2,"end":1,"text":"bad"}]')});
 await expect(page.getByRole('status')).toContainText('invalid time');await expect(page.locator('.cue')).toHaveCount(3);
 await page.locator('#transcript-file').setInputFiles({name:'x.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify([{start:0,end:1,text:'<img src=x onerror="window.pwned=true">'}]))});
 await expect(page.locator('.cue p')).toContainText('<img');expect(await page.evaluate(()=>Reflect.get(window,'pwned'))).toBeUndefined();
 await page.getByRole('button',{name:'Jump to 00:00'}).click();await expect(page.getByRole('status')).toContainText('Open a playable video');
 await page.getByRole('searchbox').fill('not found');await expect(page.getByText('No matching passages.')).toBeVisible();
});
test('app shell works offline after a complete online visit',async({page,context})=>{
 await page.goto('/');await page.evaluate(()=>navigator.serviceWorker.ready);await page.reload();await page.getByRole('button',{name:'Try a sample'}).click();
 await context.setOffline(true);await page.reload();await expect(page.getByRole('heading',{name:'Words worth finding.'})).toBeVisible();await page.getByRole('button',{name:'Try a sample'}).click();await expect(page.locator('.cue')).toHaveCount(3);
});
test('changing transcript or video asks before discarding notes',async({page})=>{
 await page.goto('/');await page.locator('#video-file').setInputFiles(fixture);await page.getByRole('button',{name:'Try a sample'}).click();
 await page.getByRole('button',{name:'Save note at 00:00'}).click();
 page.once('dialog',dialog=>dialog.dismiss());await page.getByRole('button',{name:'Try a sample'}).click();await expect(page.locator('#notes-count')).toHaveText('1');
 page.once('dialog',dialog=>dialog.dismiss());await page.locator('#video-file').setInputFiles(fixture);await expect(page.locator('#notes-count')).toHaveText('1');
 page.once('dialog',dialog=>dialog.accept());await page.locator('#video-file').setInputFiles(fixture);await expect(page.locator('#notes-count')).toHaveText('0');await expect(page.getByRole('searchbox')).toBeDisabled();await expect(page.locator('.cue')).toHaveCount(0);
});
