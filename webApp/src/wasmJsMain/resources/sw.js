// Service Worker — Tarati PWA
// Bump CACHE_VERSION en cada deploy para invalidar caché anterior.
const CACHE_VERSION = 'v1';
const CACHE_NAME = `tarati-${CACHE_VERSION}`;

// Assets con nombre fijo: se precachean en el install.
const PRECACHE_ASSETS = [
    '/index.html',
    '/manifest.json',
    '/tarati.js',
    '/favicon.ico',
    '/favicon-16x16.png',
    '/favicon-32x32.png',
    '/favicon-48x48.png',
    '/icon-192x192.png',
    '/icon-512x512.png',
    '/apple-touch-icon.png',
    '/composeResources/com.agustin.tarati.shared.generated.resources/values/strings.commonMain.cvr',
    '/composeResources/com.agustin.tarati.shared.generated.resources/values-es/strings.commonMain.cvr',
];

// Paths que nunca se cachean: API REST, auth, WebSocket, métricas.
const NETWORK_ONLY_PREFIXES = ['/api', '/auth', '/ws', '/metrics', '/health', '/stats', '/admin'];

self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME).then(cache => {
            // Cacheamos cada asset individualmente para que un fallo no aborte la instalación.
            return Promise.allSettled(
                PRECACHE_ASSETS.map(asset =>
                    cache.add(asset).catch(err =>
                        console.warn(`[SW] Precache failed for ${asset}:`, err)
                    )
                )
            );
        })
    );
    // Activa el nuevo SW sin esperar que las tabs anteriores se cierren.
    self.skipWaiting();
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys().then(keys =>
            Promise.all(
                keys
                    .filter(key => key !== CACHE_NAME)
                    .map(key => caches.delete(key))
            )
        )
    );
    // Toma control de todas las tabs abiertas inmediatamente.
    self.clients.claim();
});

self.addEventListener('fetch', event => {
    const url = new URL(event.request.url);

    // Solo interceptamos requests al mismo origen.
    if (url.origin !== self.location.origin) return;

    // API / auth / WebSocket: siempre red, nunca caché.
    if (NETWORK_ONLY_PREFIXES.some(prefix => url.pathname.startsWith(prefix))) return;

    // Navegación (documentos: raíz, .html y deep-links SPA como /play o /reset-password):
    // network-first para que los deploys se propaguen. Sin red, sirve la página cacheada
    // y, si no está, cae al shell /index.html — así la app y sus rutas abren offline.
    if (event.request.mode === 'navigate' || url.pathname === '/' || url.pathname.endsWith('.html')) {
        event.respondWith(
            fetch(event.request)
                .then(response => {
                    const clone = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
                    return response;
                })
                .catch(() => caches.match(event.request).then(cached => cached || caches.match('/index.html')))
        );
        return;
    }

    // Archivos WASM y MJS (incluye los hasheados como 0c7a3f….wasm y skiko.wasm):
    // cache-first. Si no está en caché, lo descarga y lo guarda.
    // El hash en el nombre garantiza que archivos de versiones distintas no colisionen.
    if (url.pathname.endsWith('.wasm') || url.pathname.endsWith('.mjs')) {
        event.respondWith(
            caches.open(CACHE_NAME).then(cache =>
                cache.match(event.request).then(cached => {
                    if (cached) return cached;
                    return fetch(event.request).then(response => {
                        cache.put(event.request, response.clone());
                        return response;
                    });
                })
            )
        );
        return;
    }

    // Resto (JS, imágenes, fuentes, composeResources): cache-first con fallback a red.
    event.respondWith(
        caches.match(event.request).then(cached => {
            if (cached) return cached;
            return fetch(event.request).then(response => {
                const clone = response.clone();
                caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
                return response;
            });
        })
    );
});
