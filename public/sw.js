/* Cache only this app's public shell. Never cache imported media or transcripts. */
const CACHE = 'lecture-shell-v1';
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', event => event.waitUntil((async () => { for (const key of await caches.keys()) if(key.startsWith('lecture-shell-') && key!==CACHE) await caches.delete(key); await self.clients.claim(); })()));
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if(event.request.method!=='GET'||url.origin!==self.location.origin||url.protocol==='blob:')return;
  const scopePath = new URL(self.registration.scope).pathname;
  if(!url.pathname.startsWith(scopePath))return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE);
    try { const response = await fetch(event.request); if(response.ok && (event.request.mode==='navigate'||/\.(js|css|png|svg|webmanifest)$/.test(url.pathname))) await cache.put(event.request, response.clone()); return response; }
    catch { return await cache.match(event.request) || (event.request.mode==='navigate' ? await cache.match(self.registration.scope) : null) || new Response('Offline: open this app once while online first.',{status:503}); }
  })());
});
