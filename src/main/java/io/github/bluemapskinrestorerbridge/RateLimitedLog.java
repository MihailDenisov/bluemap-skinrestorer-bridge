package io.github.bluemapskinrestorerbridge;

import org.slf4j.Logger;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class RateLimitedLog {
    private final Logger logger;
    private final long intervalMillis;
    private final ConcurrentHashMap<String, Long> last = new ConcurrentHashMap<>();

    RateLimitedLog(Logger logger, Duration interval) {
        this.logger = logger;
        this.intervalMillis = interval.toMillis();
    }

    void warn(UUID player, String fingerprint, String message, Throwable error) {
        String key = player + ":" + fingerprint + ":" + message;
        long now = System.currentTimeMillis();
        Long previous = last.putIfAbsent(key, now);
        if (previous == null || now - previous >= intervalMillis && last.replace(key, previous, now)) {
            if (error == null) logger.warn("{} (player={}, fingerprint={})", message, player, fingerprint);
            else logger.warn("{} (player={}, fingerprint={}): {}", message, player, fingerprint, error.toString());
        }
    }
}
