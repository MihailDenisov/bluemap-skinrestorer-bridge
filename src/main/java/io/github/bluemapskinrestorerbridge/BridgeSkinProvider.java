package io.github.bluemapskinrestorerbridge;

import de.bluecolored.bluemap.api.plugin.SkinProvider;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class BridgeSkinProvider implements SkinProvider {
    private final SkinProvider fallback;
    private final SelectionSource source;
    private final SelectionTracker tracker;
    private final ImageCache cache;
    private final BridgeConfig.FallbackPolicy fallbackPolicy;
    private final Consumer<UUID> terminalEmpty;
    private final RateLimitedLog failures;
    private final AtomicBoolean active = new AtomicBoolean(true);

    BridgeSkinProvider(SkinProvider fallback, SelectionSource source, SelectionTracker tracker,
                       ImageCache cache, BridgeConfig.FallbackPolicy fallbackPolicy,
                       Consumer<UUID> terminalEmpty, RateLimitedLog failures) {
        this.fallback = fallback;
        this.source = source;
        this.tracker = tracker;
        this.cache = cache;
        this.fallbackPolicy = fallbackPolicy;
        this.terminalEmpty = terminalEmpty;
        this.failures = failures;
    }

    SkinProvider fallback() { return fallback; }
    void close() { active.set(false); }

    @Override
    public Optional<BufferedImage> load(UUID playerId) throws IOException {
        if (!active.get()) return Optional.empty();
        SkinSelection selection = source.read(playerId);
        SelectionTracker.Versioned version = tracker.observe(playerId, selection).value();
        if (selection.kind() == SkinSelection.Kind.CLEARED) {
            terminalEmpty.accept(playerId);
            return Optional.empty();
        }
        if (selection.kind() == SkinSelection.Kind.VALID) {
            try {
                BufferedImage image = cache.get(selection.textureUrl());
                return active.get() && tracker.isCurrent(playerId, version) ? Optional.of(image) : Optional.empty();
            } catch (IOException e) {
                failures.warn(playerId, selection.fingerprint(), "Could not load selected SkinRestorer texture", e);
            }
        } else if (selection.kind() == SkinSelection.Kind.INVALID) {
            failures.warn(playerId, selection.fingerprint(), "Unsupported or invalid SkinRestorer texture: " + selection.diagnostic(), null);
        }
        if (!active.get() || !tracker.isCurrent(playerId, version)) return Optional.empty();
        Optional<BufferedImage> result = fallbackPolicy == BridgeConfig.FallbackPolicy.ORIGINAL_PROVIDER
                ? fallback.load(playerId) : Optional.empty();
        if (result.isEmpty()) terminalEmpty.accept(playerId);
        return result;
    }
}
