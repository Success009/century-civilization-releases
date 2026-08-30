package com.century.civilization;

import com.century.civilization.network.WorldSeedPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CenturyMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("century-civilization");
    
    @Override
    public void onInitialize() {
        if (JanitorPreLaunch.isDisabled()) {
            LOGGER.info("Century Civilization (Older Version) is disabled and inactive.");
            return;
        }
        LOGGER.info("Century Civilization Mod Initializing...");
        BridgeManager.start();

        // Register custom S2C play payload type
        PayloadTypeRegistry.clientboundPlay().register(WorldSeedPayload.TYPE, WorldSeedPayload.CODEC);

        // Server-side Seed Dispatch on player join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                long seed = server.overworld().getSeed();
                ServerPlayNetworking.send(handler.player, new WorldSeedPayload(seed));
                LOGGER.info("[SEED-DISPATCH] Sent overworld seed " + seed + " to player " + handler.player.getName().getString());
            } catch (Throwable t) {
                LOGGER.error("Failed to dispatch world seed on join", t);
            }
        });
    }
}
