// LLMWeather service worker: network first, cache as fallback.
// The forecast changes hourly, so we never serve stale data while online; offline, the last
// successfully fetched page, assets and forecast.json are shown instead.
const CACHE = 'llmweather-v1';

// Precache the app shell, the hashed assets it references and the current forecast, so the site
// opens offline even if the first visit ended before this worker took control of the page.
self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    const scopePath = new URL(self.registration.scope).pathname;
    try {
      const shell = await fetch(scopePath, { cache: 'no-cache' });
      if (shell.ok) {
        const html = await shell.clone().text();
        await cache.put(scopePath, shell);
        const assets = [...html.matchAll(/(?:src|href)="([^"]+\.(?:js|css|png|webmanifest))"/g)].map((m) => m[1]);
        await Promise.all(assets.map((a) => cache.add(new URL(a, self.registration.scope)).catch(() => {})));
      }
      await cache.add(new URL('data/forecast.json', self.registration.scope)).catch(() => {});
    } catch (e) { /* offline during install: nothing to precache */ }
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return;
  event.respondWith(
    fetch(req)
      .then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(req, copy));
        }
        return res;
      })
      .catch(async () => {
        const cached = await caches.match(req);
        if (cached) return cached;
        if (req.mode === 'navigate') {
          const shell = await caches.match(new URL(self.registration.scope).pathname);
          if (shell) return shell;
        }
        return new Response('Offline and no cached forecast yet.', { status: 503, headers: { 'Content-Type': 'text/plain' } });
      })
  );
});
