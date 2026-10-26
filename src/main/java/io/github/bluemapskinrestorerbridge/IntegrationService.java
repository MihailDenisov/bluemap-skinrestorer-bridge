package io.github.bluemapskinrestorerbridge;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.plugin.SkinProvider;
import net.minecraft.server.MinecraftServer;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class IntegrationService implements AutoCloseable {
    private final MinecraftServer server;
    private final SkinRestorerSelectionSource source = new SkinRestorerSelectionSource();
    private final SelectionTracker tracker = new SelectionTracker();
    private final ThreadPoolExecutor pollWorkers = executor("selection", 2, 128);
    private final ThreadPoolExecutor downloadWorkers = executor("download", 2, 64);
    private final ImageCache imageCache;
    private final RateLimitedLog failures = new RateLimitedLog(BlueMapSkinRestorerBridge.LOGGER, Duration.ofMinutes(5));
    private volatile BlueMapAPI blueMap;
    private volatile BlueMap57Adapter adapter;
    private volatile BridgeSkinProvider provider;
    private volatile boolean closed;
    private long tickCounter;

    IntegrationService(MinecraftServer server) {
        this.server = server;
        ImageDownloader downloader = new ImageDownloader.Http(
                Duration.ofSeconds(BridgeConfig.CONNECT_TIMEOUT_SECONDS.get()),
                Duration.ofSeconds(BridgeConfig.READ_TIMEOUT_SECONDS.get()),
                BridgeConfig.MAX_IMAGE_BYTES.get(), BridgeConfig.RETRY_COUNT.get(),
                BridgeConfig.RETRY_BACKOFF_MILLIS.get());
        imageCache = new ImageCache(downloader, downloadWorkers,
                Duration.ofMinutes(BridgeConfig.CACHE_EXPIRE_MINUTES.get()), BridgeConfig.MAX_CACHE_ENTRIES.get());
    }

    synchronized void attach(BlueMapAPI api) {
        if (closed || !BridgeConfig.ENABLED.get()) return;
        detach(this.blueMap);
        try {
            if (!"5.7".equals(api.getBlueMapVersion())) {
                throw new IllegalStateException("expected BlueMap 5.7, found " + api.getBlueMapVersion());
            }
            BlueMap57Adapter newAdapter = new BlueMap57Adapter(api);
            SkinProvider original = api.getPlugin().getSkinProvider();
            if (original instanceof BridgeSkinProvider bridge) original = bridge.fallback();
            BridgeSkinProvider bridge = new BridgeSkinProvider(original, source, tracker, imageCache,
                    BridgeConfig.FALLBACK_POLICY.get(), newAdapter::delete, failures);
            api.getPlugin().setSkinProvider(bridge);
            blueMap = api;
            adapter = newAdapter;
            provider = bridge;
            BlueMapSkinRestorerBridge.LOGGER.info(
                    "Initialized bridge with BlueMap {} and SkinRestorer {} (poll={}s, fallback={})",
                    api.getBlueMapVersion(), BlueMapSkinRestorerBridge.modVersion("skinrestorer"),
                    BridgeConfig.DETECTION_SECONDS.get(), BridgeConfig.FALLBACK_POLICY.get());
            scanOnlinePlayers();
        } catch (ReflectiveOperationException | RuntimeException e) {
            BlueMapSkinRestorerBridge.LOGGER.error(
                    "BlueMap 5.7 compatibility check failed; the bridge was not installed. " +
                            "Recheck BlueMap57Adapter against the installed BlueMap JAR.", e);
        }
    }

    synchronized void detach(BlueMapAPI api) {
        if (api == null || api != blueMap) return;
        BridgeSkinProvider installed = provider;
        if (installed != null) installed.close();
        if (installed != null && api.getPlugin().getSkinProvider() == installed) {
            api.getPlugin().setSkinProvider(installed.fallback());
        }
        BlueMap57Adapter old = adapter;
        if (old != null) old.close();
        adapter = null;
        provider = null;
        blueMap = null;
    }

    void tick() {
        if (closed || ++tickCounter % (BridgeConfig.DETECTION_SECONDS.get() * 20L) != 0) return;
        scanOnlinePlayers();
    }

    void playerJoined(UUID playerId) { submit(playerId); }

    void playerLeft(UUID playerId) {
        tracker.remove(playerId);
        BlueMap57Adapter current = adapter;
        if (current != null) current.forget(playerId);
    }

    private void scanOnlinePlayers() {
        if (closed) return;
        List<UUID> online = server.getPlayerList().getPlayers().stream()
                .map(player -> player.getUUID()).toList();
        online.forEach(this::submit);
    }

    private void submit(UUID playerId) {
        try {
            pollWorkers.execute(() -> detect(playerId));
        } catch (RejectedExecutionException ignored) {
            if (BridgeConfig.DEBUG_LOGGING.get()) BlueMapSkinRestorerBridge.LOGGER.debug(
                    "Selection scan queue is full; deferring {} to the next interval", playerId);
        }
    }

    private void detect(UUID playerId) {
        if (closed) return;
        try {
            SkinSelection selection = source.read(playerId);
            SelectionTracker.Change change = tracker.observe(playerId, selection);
            if (!change.changed()) return;
            if (BridgeConfig.DEBUG_LOGGING.get()) BlueMapSkinRestorerBridge.LOGGER.debug(
                    "Skin selection changed (player={}, provider={}, state={}, fingerprint={})",
                    playerId, selection.provider(), selection.kind(), selection.fingerprint());
            BlueMap57Adapter current = adapter;
            if (current == null) return;
            if (selection.kind() == SkinSelection.Kind.CLEARED) current.delete(playerId);
            else current.refresh(playerId);
        } catch (RuntimeException e) {
            failures.warn(playerId, "read", "Could not read SkinRestorer selection", e);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        detach(blueMap);
        tracker.clear();
        imageCache.clear();
        pollWorkers.shutdownNow();
        downloadWorkers.shutdownNow();
    }

    private static ThreadPoolExecutor executor(String purpose, int threads, int queueCapacity) {
        AtomicInteger ids = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "bluemap-skinrestorer-" + purpose + "-" + ids.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity), factory, new ThreadPoolExecutor.AbortPolicy());
    }
}
