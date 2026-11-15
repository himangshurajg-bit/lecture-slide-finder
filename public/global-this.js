// Android WebView versions before Chrome 71 lack the globalThis alias. Keep
// regenerator-runtime from falling back to Function(), which strict CSP blocks.
if (typeof window.globalThis === 'undefined') {
  Object.defineProperty(window, 'globalThis', {
    value: window,
    configurable: true,
    writable: true,
  });
}
