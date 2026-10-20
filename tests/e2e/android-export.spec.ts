import {test,expect} from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

test.beforeEach(async({page})=>{
  await page.route('https://appassets.androidplatform.net/**',async route=>{
    const pathname=new URL(route.request().url()).pathname;
    const relative=pathname.replace(/^\/assets\//,'');
    // Controlled production files only, matching the native bundled origin.
    if(relative.includes('..'))return route.abort();
    const file=path.resolve('dist',relative);
    const type=file.endsWith('.html')?'text/html':file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'application/octet-stream';
    await route.fulfill({body:await fs.readFile(file),contentType:type});
  });
  await page.goto('https://appassets.androidplatform.net/assets/index.html');
  await page.getByRole('button',{name:'Try a sample'}).click();
  await page.getByRole('button',{name:'Save note at 00:00'}).click();
});

test('Android export waits for native save result and carries actual Markdown',async({page})=>{
  page.once('dialog',async dialog=>{
    expect(dialog.message()).toBe('lecture-export-v1');
    expect(dialog.defaultValue()).toContain('A force changes the motion');
    await dialog.accept('pending');
  });
  await page.getByRole('button',{name:'Export notes'}).click();
  await expect(page.locator('#status')).toHaveText('Choose where to save your notes.');
  await page.evaluate(()=>window.dispatchEvent(new CustomEvent('lecture-export-result',{detail:'Notes exported. Keep the saved file before closing this app.'})));
  await expect(page.locator('#status')).toHaveText('Notes exported. Keep the saved file before closing this app.');
  await expect(page.locator('#notes-count')).toHaveText('1');
});

test('cancelled Android export preserves notes and reports no successful save',async({page})=>{
  page.once('dialog',dialog=>dialog.accept('pending'));
  await page.getByRole('button',{name:'Export notes'}).click();
  await page.evaluate(()=>window.dispatchEvent(new CustomEvent('lecture-export-result',{detail:'Export cancelled. Your notes are still here.'})));
  await expect(page.locator('#status')).toHaveText('Export cancelled. Your notes are still here.');
  await expect(page.locator('#notes-count')).toHaveText('1');
});
