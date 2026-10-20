import {test,expect} from '@playwright/test';
import path from 'node:path';
const fixture=path.resolve('tests/fixtures/frame-study.mp4');
test('offline bundled OCR reads actual English/Hindi picture, edits, searches, restores and exports source-linked points',async({page})=>{
 test.setTimeout(120000);const external:string[]=[];
 page.on('request',r=>{if(r.url().startsWith('http')&&!r.url().startsWith('http://127.0.0.1:4173'))external.push(r.url());});
 await page.goto('/');await page.locator('#video-file').setInputFiles(fixture);
 await expect.poll(()=>page.locator('#video').evaluate((v:HTMLVideoElement)=>v.readyState)).toBeGreaterThan(1);
 await page.locator('#ocr-language').selectOption('eng+hin');
 await page.locator('#frames-current').click();
 await expect(page.locator('#ocr-state')).toContainText('Current displayed picture read',{timeout:90000});
 await expect(page.locator('#frame-points')).toContainText('Mass and acceleration');
 await expect(page.locator('#frame-points')).toContainText('बल गति बदलता है');
 await expect(page.locator('#frame-points')).toContainText('Manual picture; no frame index');
 expect(external).toEqual([]);
 await page.locator('.visible-point details summary').click();await page.locator('.visible-point textarea').fill('Mass and acceleration\nReviewed source text.');await page.getByRole('button',{name:'Save correction'}).click();
 await page.locator('#points-query').fill('not present');await expect(page.locator('.visible-point')).toHaveCount(0);
 await page.locator('#points-query').fill('Reviewed');await expect(page.locator('.visible-point')).toHaveCount(1);
 const download=page.waitForEvent('download');await page.locator('#frames-export').click();const file=await(await download).path();const fs=await import('node:fs/promises');const text=await fs.readFile(file!,'utf8');expect(text).toContain('PARTIAL');expect(text).toContain('manual picture (no frame index)');expect(text).toContain('Original OCR:');expect(text).toContain('- Reviewed source text.');
 await page.reload();await expect(page.locator('#frame-points')).toContainText('Reviewed source text.');
 await expect(page.locator('#ocr-state')).toContainText('partial');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.locator('.frame-study').screenshot({path:`test-results/frame-study-${test.info().project.name}.png`});
});
test('browser states honest exact-frame limits; cancellation restores controls',async({page})=>{
 await page.goto('/');await page.locator('#all-frames summary').click();await expect(page.locator('#gallery-state')).toContainText('Android app');await expect(page.locator('#frames-start')).toBeDisabled();await expect(page.locator('#frames-scan')).toBeDisabled();
 await page.locator('#video-file').setInputFiles(fixture);await expect.poll(()=>page.locator('#video').evaluate((v:HTMLVideoElement)=>v.readyState)).toBeGreaterThan(1);
 await page.locator('#frames-current').click();await page.locator('#frames-stop').click();await expect(page.locator('#ocr-state')).toContainText('stopped');await expect(page.locator('#frames-current')).toBeEnabled();await expect(page.locator('[data-pick=video-file]').first()).toBeEnabled();
});
