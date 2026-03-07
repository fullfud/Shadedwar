package com.fullfud.fullfud.core;

import com.fullfud.fullfud.FullfudMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerLevel;

@Mod.EventBusSubscriber(modid = FullfudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChunkLoadEvents {
    private ChunkLoadEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ChunkLoadManager.cleanupStaleTickets();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ChunkLoadManager.clearLevel(serverLevel);
        }
    }
}
