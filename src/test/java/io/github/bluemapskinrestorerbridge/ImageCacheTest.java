package io.github.bluemapskinrestorerbridge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageCacheTest {
    private final ExecutorService workers = Executors.newFixedThreadPool(4);

    @AfterEach void stop() { workers.shutdownNow(); }

    @Test
    void deduplicatesConcurrentDownloads() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ImageCache cache = new ImageCache(uri -> {
            calls.incrementAndGet(); entered.countDown();
            try { release.await(); } catch (InterruptedException e) { throw new IOException(e); }
            return image();
        }, workers, Duration.ofMinutes(1), 8);
        URI uri = URI.create("https://example.test/skin.png");
        Future<BufferedImage> first = workers.submit(() -> cache.get(uri));
        entered.await();
        Future<BufferedImage> second = workers.submit(() -> cache.get(uri));
        release.countDown();
        assertThat(first.get()).isSameAs(second.get());
        assertThat(calls).hasValue(1);
    }

    @Test
    void expiresInvalidatesAndDoesNotPermanentlyCacheFailures() throws Exception {
        MutableClock clock = new MutableClock();
        AtomicInteger calls = new AtomicInteger();
        ImageCache cache = new ImageCache(uri -> {
            int call = calls.incrementAndGet();
            if (call == 2) throw new IOException("temporary");
            return image();
        }, Runnable::run, Duration.ofSeconds(10), 8, clock);
        URI uri = URI.create("https://example.test/skin.png");
        BufferedImage initial = cache.get(uri);
        clock.advance(Duration.ofSeconds(11));
        assertThat(cache.get(uri)).isSameAs(initial); // stale-on-error recovery
        clock.advance(Duration.ofSeconds(11));
        assertThat(cache.get(uri)).isNotSameAs(initial);
        cache.invalidate(uri);
        cache.get(uri);
        assertThat(calls).hasValue(4);
    }

    @Test
    void failedFirstRequestCanRecover() {
        AtomicInteger calls = new AtomicInteger();
        ImageCache cache = new ImageCache(uri -> {
            if (calls.incrementAndGet() == 1) throw new IOException("temporary");
            return image();
        }, Runnable::run, Duration.ofMinutes(1), 8);
        URI uri = URI.create("https://example.test/retry.png");
        assertThatThrownBy(() -> cache.get(uri)).isInstanceOf(IOException.class);
        assertThat(cacheGet(cache, uri)).isNotNull();
        assertThat(calls).hasValue(2);
    }

    private static BufferedImage cacheGet(ImageCache cache, URI uri) {
        try { return cache.get(uri); } catch (IOException e) { throw new AssertionError(e); }
    }

    private static BufferedImage image() { return new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB); }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.EPOCH;
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
