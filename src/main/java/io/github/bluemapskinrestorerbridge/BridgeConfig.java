package io.github.bluemapskinrestorerbridge;

import net.neoforged.neoforge.common.ModConfigSpec;

final class BridgeConfig {
    static final ModConfigSpec SPEC;
    static final ModConfigSpec.BooleanValue ENABLED;
    static final ModConfigSpec.IntValue DETECTION_SECONDS;
    static final ModConfigSpec.IntValue CONNECT_TIMEOUT_SECONDS;
    static final ModConfigSpec.IntValue READ_TIMEOUT_SECONDS;
    static final ModConfigSpec.IntValue MAX_IMAGE_BYTES;
    static final ModConfigSpec.IntValue MAX_CACHE_ENTRIES;
    static final ModConfigSpec.IntValue CACHE_EXPIRE_MINUTES;
    static final ModConfigSpec.IntValue RETRY_COUNT;
    static final ModConfigSpec.IntValue RETRY_BACKOFF_MILLIS;
    static final ModConfigSpec.EnumValue<FallbackPolicy> FALLBACK_POLICY;
    static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ENABLED = builder.comment("Enable the bridge.").define("enabled", true);
        DETECTION_SECONDS = builder.comment("Online-player selection polling interval.")
                .defineInRange("changeDetectionSeconds", 10, 2, 3600);
        CONNECT_TIMEOUT_SECONDS = builder.defineInRange("connectTimeoutSeconds", 5, 1, 60);
        READ_TIMEOUT_SECONDS = builder.defineInRange("readTimeoutSeconds", 10, 1, 120);
        MAX_IMAGE_BYTES = builder.defineInRange("maxImageBytes", 2 * 1024 * 1024, 64 * 1024, 16 * 1024 * 1024);
        MAX_CACHE_ENTRIES = builder.defineInRange("maxCacheEntries", 256, 8, 4096);
        CACHE_EXPIRE_MINUTES = builder.defineInRange("cacheExpireMinutes", 60, 1, 1440);
        RETRY_COUNT = builder.defineInRange("retryCount", 2, 0, 5);
        RETRY_BACKOFF_MILLIS = builder.defineInRange("retryBackoffMillis", 250, 0, 5000);
        FALLBACK_POLICY = builder.comment("ORIGINAL_PROVIDER or EMPTY.")
                .defineEnum("fallbackPolicy", FallbackPolicy.ORIGINAL_PROVIDER);
        DEBUG_LOGGING = builder.define("debugLogging", false);
        SPEC = builder.build();
    }

    enum FallbackPolicy { ORIGINAL_PROVIDER, EMPTY }

    private BridgeConfig() {}
}
