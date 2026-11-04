// Keeps Fidelis opening without a connection. Its own files only: Spotify and the lyrics are always asked fresh.
const CACHE = 'fidelis-2.0.0';
const FILES = ['./', 'index.html', 'style.css', 'manifest.webmanifest', 'icons/icon-192.png', 'icons/icon-512.png', 'icons/icon-full.png',
  ...['regular', 'medium', 'semibold', 'bold', 'extrabold'].map(w => `fonts/manrope_${w}.ttf`),
  ...['spotify', 'ui', 'lyrics', 'player', 'screens', 'app'].map(f => `js/${f}.js`)];

self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
// network first (no-cache skips the browser's own copy), so an update shows at once; the cache only offline
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin || url.search.includes('code=')) return;
  e.respondWith(fetch(e.request, { cache: 'no-cache' }).then(r => {
    if (r.ok) { const copy = r.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)); }
    return r;
  }).catch(() => caches.match(e.request, { ignoreSearch: true })));
});
