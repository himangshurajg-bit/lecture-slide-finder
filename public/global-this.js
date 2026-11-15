// Android WebView versions before Chrome 71 lack the globalThis alias. Keep
// regenerator-runtime from falling back to Function(), which strict CSP blocks.
if (typeof window.globalThis === 'undefined') {
  Object.defineProperty(window, 'globalThis', {
    value: window,
    configurable: true,
    writable: true,
  });
}
// regenerator-runtime's legacy fallback calls Function(), which the app's CSP
// intentionally blocks. Predeclare the global so its strict-mode assignment
// succeeds without eval on WebViews lacking globalThis.
// Declare a classic-script global binding so strict-mode legacy runtimes can
// assign to it without attempting their CSP-blocked Function() fallback.
var regeneratorRuntime = window.regeneratorRuntime || {};
window.regeneratorRuntime = regeneratorRuntime;
