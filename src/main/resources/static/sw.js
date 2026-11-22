/*
 * BackTrack service worker.
 *
 * It only stores public, non-private things: the stylesheet, the helper
 * script, the icons and the logo. Pages, form posts and anything that comes
 * from the database always go straight to the server, so no business data is
 * ever written into a cache and the app is not offered as an offline app.
 */
const CACHE_NAME = "backtrack-static-v2";

const STATIC_FILES = [
    "/css/style.css",
    "/css/login.css",
    "/js/app.js",
    "/images/backtrack-logo.png",
    "/icons/icon-192.png",
    "/icons/icon-512.png",
    "/manifest.json"
];

self.addEventListener("install", function (event) {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(function (cache) {
                return cache.addAll(STATIC_FILES);
            })
            .then(function () {
                return self.skipWaiting();
            })
    );
});

self.addEventListener("activate", function (event) {
    event.waitUntil(
        caches.keys().then(function (keys) {
            return Promise.all(keys.map(function (key) {
                if (key !== CACHE_NAME) {
                    return caches.delete(key);
                }
                return Promise.resolve();
            }));
        }).then(function () {
            return self.clients.claim();
        })
    );
});

self.addEventListener("fetch", function (event) {
    const request = event.request;

    if (request.method !== "GET") {
        return; // Form posts always reach the server.
    }

    const url = new URL(request.url);

    if (url.origin !== self.location.origin) {
        return;
    }

    const isStatic = url.pathname.startsWith("/css/")
        || url.pathname.startsWith("/js/")
        || url.pathname.startsWith("/icons/")
        || url.pathname.startsWith("/images/");

    if (!isStatic) {
        return; // Pages and API responses are never cached.
    }

    // Network first: a new design or script is always picked up when the
    // phone is online. The cache is only a fallback for a flaky connection.
    event.respondWith(
        fetch(request).then(function (response) {
            if (response && response.ok) {
                const copy = response.clone();
                caches.open(CACHE_NAME).then(function (cache) {
                    cache.put(request, copy);
                });
            }
            return response;
        }).catch(function () {
            return caches.match(request);
        })
    );
});
