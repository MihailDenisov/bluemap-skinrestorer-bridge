package io.github.bluemapskinrestorerbridge;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** The only class that depends on BlueMap 5.7 implementation details. */
final class BlueMap57Adapter {
    private static final String ASSET_PREFIX = "playerheads/";
    private final BlueMapAPI api;
    private final Object updater;
    private final Method updateSkin;
    private final Map<UUID, Long> throttle;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> chains = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    BlueMap57Adapter(BlueMapAPI api) throws ReflectiveOperationException {
        this.api = api;
        Object apiPlugin = api.getPlugin();
        Method unwrap = apiPlugin.getClass().getMethod("getPlugin");
        Object commonPlugin = unwrap.invoke(apiPlugin);
        Method getUpdater = commonPlugin.getClass().getMethod("getSkinUpdater");
        updater = getUpdater.invoke(commonPlugin);
        updateSkin = updater.getClass().getMethod("updateSkin", UUID.class);
        Field updates = updater.getClass().getDeclaredField("skinUpdates");
        updates.setAccessible(true);
        throttle = (Map<UUID, Long>) updates.get(updater);
    }

    void refresh(UUID playerId) {
        enqueue(playerId, () -> {
            throttle.remove(playerId);
            try {
                return ((CompletableFuture<?>) updateSkin.invoke(updater, playerId)).thenApply(ignored -> null);
            } catch (IllegalAccessException | InvocationTargetException e) {
                return CompletableFuture.failedFuture(e);
            }
        });
    }

    void delete(UUID playerId) {
        enqueue(playerId, () -> {
            for (BlueMapMap map : api.getMaps()) {
                try {
                    map.getAssetStorage().deleteAsset(ASSET_PREFIX + playerId + ".png");
                } catch (Exception e) {
                    BlueMapSkinRestorerBridge.LOGGER.warn("Could not delete stale BlueMap head for {} on map {}: {}",
                            playerId, map.getId(), e.toString());
                }
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    private void enqueue(UUID playerId, java.util.function.Supplier<CompletableFuture<Void>> action) {
        if (!active.get()) return;
        chains.compute(playerId, (id, previous) -> {
            CompletableFuture<Void> base = previous == null ? CompletableFuture.completedFuture(null)
                    : previous.exceptionally(error -> null);
            CompletableFuture<Void> next = base.thenCompose(ignored -> active.get()
                    ? action.get() : CompletableFuture.completedFuture(null));
            next.whenCompleteAsync((ignored, error) -> {
                chains.remove(id, next);
                if (error != null && active.get()) BlueMapSkinRestorerBridge.LOGGER.warn(
                        "BlueMap 5.7 head refresh failed for {}: {}", id, error.toString());
            });
            return next;
        });
    }

    void forget(UUID playerId) {
        CompletableFuture<Void> chain = chains.get(playerId);
        if (chain == null || chain.isDone()) chains.remove(playerId, chain);
    }

    void close() {
        active.set(false);
        chains.clear();
    }
}
