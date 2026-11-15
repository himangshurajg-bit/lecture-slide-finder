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
  });
});
