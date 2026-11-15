import { readFileSync } from 'node:fs';
import { createContext, runInContext } from 'node:vm';
import { describe, expect, it } from 'vitest';

const polyfill = readFileSync(new URL('../public/global-this.js', import.meta.url), 'utf8');

describe('legacy WebView globalThis compatibility', () => {
  it('restores the global alias without enabling eval', () => {
    const context = createContext({});
    runInContext('delete globalThis', context);
    const legacyWindow = runInContext('this', context) as Record<string, unknown>;
    legacyWindow.window = legacyWindow;

    runInContext(polyfill, context);

    expect(runInContext('typeof globalThis', context)).toBe('object');
    expect(runInContext('globalThis === window', context)).toBe(true);
    expect(runInContext('typeof window.regeneratorRuntime', context)).toBe('object');

    // Match regenerator-runtime's strict global assignment on Chrome 69, where
    // globalThis is still absent and CSP blocks its Function() fallback.
    runInContext('delete globalThis; Function = function () { throw new Error(\"CSP eval reached\"); };', context);
    runInContext('try { regeneratorRuntime = { ready: true }; } catch (e) { if (typeof globalThis === \"object\") globalThis.regeneratorRuntime = { ready: true }; else Function(\"r\", \"regeneratorRuntime = r\")({ ready: true }); }', context);
    expect(runInContext('regeneratorRuntime.ready', context)).toBe(true);
  });
});
