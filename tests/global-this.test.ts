import { readFileSync } from 'node:fs';
import { createContext, runInContext } from 'node:vm';
import { describe, expect, it } from 'vitest';

const polyfill = readFileSync(new URL('../public/global-this.js', import.meta.url), 'utf8');

describe('legacy WebView globalThis compatibility', () => {
  it('provides a strict global binding without invoking Function', () => {
    const context = createContext({});
    runInContext('delete globalThis; Function = function () { throw new Error("CSP eval reached"); };', context);
    const legacyWindow = runInContext('this', context) as Record<string, unknown>;
    legacyWindow.window = legacyWindow;

    runInContext(polyfill, context);

    expect(runInContext('typeof globalThis', context)).toBe('object');
    expect(runInContext('globalThis === window', context)).toBe(true);
    expect(runInContext('typeof window.regeneratorRuntime', context)).toBe('object');
    runInContext('"use strict"; regeneratorRuntime = { ready: true };', context);
    expect(runInContext('window.regeneratorRuntime.ready', context)).toBe(true);
  });
});
