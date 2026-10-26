package io.github.bluemapskinrestorerbridge;

import com.mojang.logging.LogUtils;
import de.bluecolored.bluemap.api.BlueMapAPI;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(BlueMapSkinRestorerBridge.MOD_ID)
public final class BlueMapSkinRestorerBridge {
    public static final String MOD_ID = "bluemap_skinrestorer_bridge";
    static final Logger LOGGER = LogUtils.getLogger();
    private static volatile IntegrationService service;

    public BlueMapSkinRestorerBridge(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, BridgeConfig.SPEC);
        BlueMapAPI.onEnable(api -> {
            IntegrationService current = service;
            if (current != null) current.attach(api);
        });
        BlueMapAPI.onDisable(api -> {
            IntegrationService current = service;
            if (current != null) current.detach(api);
        });
    }

    static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
    }

    @EventBusSubscriber(modid = MOD_ID)
    public static final class Events {
        @SubscribeEvent
        public static void serverStarted(ServerStartedEvent event) {
            IntegrationService created = new IntegrationService(event.getServer());
            service = created;
            BlueMapAPI.getInstance().ifPresent(created::attach);
        }

        @SubscribeEvent
        public static void serverStopped(ServerStoppedEvent event) {
            IntegrationService current = service;
            service = null;
            if (current != null) current.close();
        }

        @SubscribeEvent
        public static void serverTick(ServerTickEvent.Post event) {
            IntegrationService current = service;
            if (current != null) current.tick();
        }

        @SubscribeEvent
        public static void playerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                IntegrationService current = service;
                if (current != null) current.playerJoined(player.getUUID());
            }
        }

        @SubscribeEvent
        public static void playerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                IntegrationService current = service;
                if (current != null) current.playerLeft(player.getUUID());
            }
        }

        private Events() {}
    }
}
