package io.github.bluemapskinrestorerbridge;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

final class ImageCache {
    private final ImageDownloader downloader;
    private final Executor executor;
    private final Clock clock;
    private final long ttlMillis;
    private final int maxEntries;
    private final Map<URI, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final ConcurrentHashMap<URI, CompletableFuture<Entry>> inFlight = new ConcurrentHashMap<>();

    ImageCache(ImageDownloader downloader, Executor executor, Duration ttl, int maxEntries) {
        this(downloader, executor, ttl, maxEntries, Clock.systemUTC());
    }

    ImageCache(ImageDownloader downloader, Executor executor, Duration ttl, int maxEntries, Clock clock) {
        this.downloader = downloader;
        this.executor = executor;
        this.ttlMillis = ttl.toMillis();
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    BufferedImage get(URI uri) throws IOException {
        Entry stale;
        synchronized (cache) {
            stale = cache.get(uri);
            if (stale != null && stale.expiresAt > clock.millis()) return stale.image;
        }

        CompletableFuture<Entry> future = inFlight.computeIfAbsent(uri, key ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return new Entry(downloader.download(key), clock.millis() + ttlMillis);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, executor));
        try {
            Entry loaded = future.join();
            synchronized (cache) {
                cache.put(uri, loaded);
                while (cache.size() > maxEntries) cache.remove(cache.keySet().iterator().next());
            }
            return loaded.image;
        } catch (CompletionException e) {
            if (stale != null && clock.millis() <= stale.expiresAt + ttlMillis) return stale.image;
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) throw io;
            throw new IOException("skin download failed", cause);
        } finally {
            inFlight.remove(uri, future);
        }
    }

    synchronized void invalidate(URI uri) {
        synchronized (cache) { cache.remove(uri); }
    }

    void clear() {
        synchronized (cache) { cache.clear(); }
        inFlight.clear();
    }

    int size() {
        synchronized (cache) { return cache.size(); }
    }

    private record Entry(BufferedImage image, long expiresAt) {}
}
