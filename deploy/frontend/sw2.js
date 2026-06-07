// LifeWise Service Worker - v3 (development-friendly caching)
const CACHE = 'lifewise-v3'

self.addEventListener('install', (e) => {
  self.skipWaiting()
  // Don't cache pages on install - let the fetch handler manage them
  e.waitUntil(Promise.resolve())
})

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    )
  )
})

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url)
  if (e.request.method !== 'GET' || url.origin !== self.location.origin) return

  // API calls: go directly to network, no SW interference
  if (url.pathname.startsWith('/api/')) {
    e.respondWith(fetch(e.request))
    return
  }

  // Dev source files: always go to network (never cache)
  if (url.pathname.startsWith('/src/') || url.pathname.startsWith('/node_modules/') || url.pathname.startsWith('/@')) {
    e.respondWith(fetch(e.request))
    return
  }

  // Static built assets (only in production): cache first, network fallback
  if (url.pathname.match(/\.(js|css|svg|png|jpg|woff2?)$/)) {
    e.respondWith(
      caches.match(e.request).then((r) => r || fetch(e.request).then((r) => {
        const clone = r.clone()
        caches.open(CACHE).then((c) => c.put(e.request, clone))
        return r
      }))
    )
    return
  }

  // All other requests (pages, etc.): always go to network, no caching
  e.respondWith(fetch(e.request).catch(() => caches.match(e.request).then((r) => r || caches.match('/'))))
})
