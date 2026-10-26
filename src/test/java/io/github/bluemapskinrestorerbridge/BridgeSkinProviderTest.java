package io.github.bluemapskinrestorerbridge;

import de.bluecolored.bluemap.api.plugin.SkinProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BridgeSkinProviderTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final BufferedImage IMAGE = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

    @Test
    void validAlternativeSelectionWinsOverFallback() throws Exception {
        AtomicInteger fallbackCalls = new AtomicInteger();
        SkinProvider fallback = id -> { fallbackCalls.incrementAndGet(); return Optional.empty(); };
        BridgeSkinProvider provider = provider(
                id -> SkinSelection.valid(URI.create("https://ely.example/skin?x=1"), "ely.by", "abc"),
                fallback, uri -> IMAGE, id -> {});
        assertThat(provider.load(PLAYER)).containsSame(IMAGE);
        assertThat(fallbackCalls).hasValue(0);
    }

    @Test
    void noSelectionAndInvalidSelectionUseOriginalFallback() throws Exception {
        SkinProvider fallback = id -> Optional.of(IMAGE);
        assertThat(provider(id -> SkinSelection.state(SkinSelection.Kind.NO_SELECTION, "none", "none", "none"),
                fallback, uri -> { throw new AssertionError(); }, id -> {}).load(PLAYER)).containsSame(IMAGE);
        assertThat(provider(id -> SkinSelection.state(SkinSelection.Kind.INVALID, "custom", "bad", "bad JSON"),
                fallback, uri -> { throw new AssertionError(); }, id -> {}).load(PLAYER)).containsSame(IMAGE);
    }

    @Test
    void clearReturnsEmptyAndRequestsStaleAssetRemoval() throws Exception {
        AtomicInteger deletes = new AtomicInteger();
        BridgeSkinProvider provider = provider(
                id -> SkinSelection.state(SkinSelection.Kind.CLEARED, "empty", "cleared", "cleared"),
                id -> Optional.of(IMAGE), uri -> IMAGE, id -> deletes.incrementAndGet());
        assertThat(provider.load(PLAYER)).isEmpty();
        assertThat(deletes).hasValue(1);
    }

    @Test
    void olderAsyncResultIsRejectedAndClosedProviderCannotWrite() throws Exception {
        SelectionTracker tracker = new SelectionTracker();
        SkinSelection first = SkinSelection.valid(URI.create("https://a.example/one"), "web", "one");
        ImageCache cache = new ImageCache(uri -> {
            tracker.observe(PLAYER, SkinSelection.valid(URI.create("https://b.example/two"), "web", "two"));
            return IMAGE;
        }, Runnable::run, Duration.ofMinutes(1), 8);
        BridgeSkinProvider provider = new BridgeSkinProvider(id -> Optional.empty(), id -> first, tracker, cache,
                BridgeConfig.FallbackPolicy.ORIGINAL_PROVIDER, id -> {},
                new RateLimitedLog(LoggerFactory.getLogger("test"), Duration.ZERO));
        assertThat(provider.load(PLAYER)).isEmpty();
        provider.close();
        assertThat(provider.load(PLAYER)).isEmpty();
    }

    @Test
    void terminalNetworkFailureFallsBackAndRemovesOldAssetWhenFallbackIsEmpty() throws Exception {
        AtomicInteger deletes = new AtomicInteger();
        BridgeSkinProvider provider = provider(
                id -> SkinSelection.valid(URI.create("https://down.example/skin"), "url", "down"),
                id -> Optional.empty(), uri -> { throw new IOException("offline"); }, id -> deletes.incrementAndGet());
        assertThat(provider.load(PLAYER)).isEmpty();
        assertThat(deletes).hasValue(1);
    }

    private static BridgeSkinProvider provider(SelectionSource source, SkinProvider fallback,
                                               ImageDownloader downloader, java.util.function.Consumer<UUID> delete) {
        return new BridgeSkinProvider(fallback, source, new SelectionTracker(),
                new ImageCache(downloader, Runnable::run, Duration.ofMinutes(1), 8),
                BridgeConfig.FallbackPolicy.ORIGINAL_PROVIDER, delete,
                new RateLimitedLog(LoggerFactory.getLogger("test"), Duration.ZERO));
    }
}
