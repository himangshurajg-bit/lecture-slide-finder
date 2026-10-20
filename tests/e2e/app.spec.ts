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

test('light liquid glass has bounded tap feedback and readable keyboard focus',async({page})=>{
 await page.emulateMedia({reducedMotion:'no-preference'});
 await page.goto('/');
 expect(await page.evaluate(()=>getComputedStyle(document.documentElement).colorScheme)).toBe('light');
 const glass=await page.locator('.glass').first().evaluate(el=>({background:getComputedStyle(el).backgroundImage,shadow:getComputedStyle(el).boxShadow}));
 expect(glass.background).toContain('linear-gradient');expect(glass.shadow).toContain('inset');
 const sample=page.getByRole('button',{name:'Try a sample'});
 await sample.dispatchEvent('pointerdown',{button:0,clientX:20,clientY:20});
 await expect(sample.locator('.water-ripple')).toHaveCount(1);
 await sample.dispatchEvent('pointerdown',{button:0,clientX:25,clientY:20});
 await expect(sample.locator('.water-ripple')).toHaveCount(1);
 await expect(sample.locator('.water-ripple')).toHaveCount(0,{timeout:2000});
 await sample.focus();
 await expect(sample).toBeFocused();
 await page.keyboard.press('Enter');await expect(page.locator('.cue')).toHaveCount(3);
 await page.keyboard.press('/');await expect(page.getByRole('searchbox')).toBeFocused();
 const outline=await page.locator('.search-box').evaluate(el=>getComputedStyle(el).outlineStyle);
 expect(outline).toBe('solid');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

test('reduced motion disables fluid animations and tap rings without blocking actions',async({page})=>{
 await page.emulateMedia({reducedMotion:'reduce'});await page.goto('/');
 const sample=page.getByRole('button',{name:'Try a sample'});
 await sample.dispatchEvent('pointerdown',{button:0,clientX:20,clientY:20});
 await expect(page.locator('.water-ripple')).toHaveCount(0);
 for(const selector of ['.glass','#demo']){
   const motion=await page.locator(selector).first().evaluate(el=>({animation:getComputedStyle(el).animationName,transition:getComputedStyle(el).transitionDuration}));
   expect(motion.animation).toBe('none');expect(motion.transition).toBe('0s');
 }
 await sample.click();await expect(page.locator('.cue')).toHaveCount(3);
 await page.getByRole('button',{name:'Save note at 00:00'}).click();await expect(page.locator('#notes-count')).toHaveText('1');
 expect(await page.locator('#notes p').evaluate(el=>getComputedStyle(el).animationName)).toBe('none');
});

test('small Android-sized viewport keeps controls reachable without horizontal scrolling',async({page})=>{
 await page.setViewportSize({width:320,height:740});await page.goto('/');
 await page.getByRole('button',{name:'Try a sample'}).click();
 await page.getByRole('button',{name:'Save note at 00:00'}).click();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 const exportButton=page.getByRole('button',{name:'Export notes'});await exportButton.scrollIntoViewIfNeeded();
 await expect(exportButton).toBeInViewport();
 expect((await exportButton.boundingBox())!.height).toBeGreaterThanOrEqual(44);
});

test('liquid edges cover controls, disclosures and input without sacrificing actions',async({page})=>{
 await page.emulateMedia({reducedMotion:'no-preference'});await page.goto('/');
 await page.getByRole('button',{name:'Try a sample'}).click();
 for (const selector of ['.brand','summary','.search-box','.cue .time','.cue .save','.glass','#demo']) {
  const edge=await page.locator(selector).first().evaluate(el=>{const s=getComputedStyle(el);return {border:s.borderTopWidth,style:s.borderTopStyle,shadow:s.boxShadow};});
  expect(edge.border).toBe('1px');expect(edge.style).toBe('solid');expect(edge.shadow).toContain('inset');
 }
 const disclosure=page.locator('summary');await disclosure.focus();await page.keyboard.press('Enter');await expect(page.locator('details')).toHaveAttribute('open','');
 await expect(disclosure).toHaveCSS('outline-style','solid');
 await disclosure.dispatchEvent('pointerdown',{button:0,clientX:20,clientY:20});await expect(disclosure.locator('.water-ripple')).toHaveCount(1);
 await expect(disclosure.locator('.water-ripple')).toHaveCount(0,{timeout:2000});
 await page.locator('.search-box').dispatchEvent('pointerdown',{button:0,clientX:20,clientY:20});await expect(page.locator('.search-box .water-ripple')).toHaveCount(1);
 await page.getByRole('searchbox').fill('connects');await expect(page.locator('.cue')).toHaveCount(1);
 await page.getByRole('button',{name:'Save note at 00:04'}).click();await expect(page.locator('#notes-count')).toHaveText('1');
 await page.emulateMedia({reducedMotion:'reduce'});await expect(page.locator('.water-ripple')).toHaveCount(0);
 await page.locator('summary').dispatchEvent('pointerdown',{button:0,clientX:20,clientY:20});await expect(page.locator('.water-ripple')).toHaveCount(0);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
