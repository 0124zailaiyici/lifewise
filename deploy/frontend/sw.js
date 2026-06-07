// LifeWise Service Worker - v2 (fixed caching)
const CACHE = 'lifewise-v2'
const STATIC_URLS = ['/', '/home', '/login', '/manifest.json', '/icon.svg']

self.addEventListener('install', (e) => {
  self.skipWaiting()
  e.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(STATIC_URLS))
  )
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

  // All source files: network first (never cache)
  if (url.pathname.startsWith('/src/') || url.pathname.startsWith('/node_modules/') || url.pathname.startsWith('/@')) {
    e.respondWith(fetch(e.request))
    return
  }

  // Static built assets: cache first
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

  // Pages: network first, cache fallback
  e.respondWith(
    fetch(e.request).then((r) => {
      const clone = r.clone()
      caches.open(CACHE).then((c) => c.put(e.request, clone))
      return r
    }).catch(() => caches.match(e.request).then((r) => r || caches.match('/')))
  )
})
