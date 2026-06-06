// LifeWise Service Worker
const CACHE = 'lifewise-v1'
const STATIC_URLS = ['/', '/home', '/login', '/manifest.json', '/icon.svg']

// Install: cache static assets
self.addEventListener('install', (e) => {
  self.skipWaiting()
  e.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(STATIC_URLS))
  )
})

// Activate: clean old caches
self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    )
  )
})

// Fetch: network first, fallback to cache
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url)
  // Only handle same-origin GET requests
  if (e.request.method !== 'GET' || url.origin !== self.location.origin) return

  // API calls: network only (don't cache dynamic data)
  if (url.pathname.startsWith('/api/')) {
    e.respondWith(fetch(e.request).catch(() => new Response(JSON.stringify({ error: '离线' }), {
      status: 503, headers: { 'Content-Type': 'application/json' }
    })))
    return
  }

  // Static assets: cache first
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
