package com.fullfud.fullfud.core;

import com.fullfud.fullfud.FullfudMod;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FullfudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DroneExplosionLimiter {
    private DroneExplosionLimiter() { }

    private static final String TAG_NO_BLOCK_DAMAGE = "fullfud_no_block_damage";

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosionDetonate(final ExplosionEvent.Detonate event) {
        if (event == null || event.getExplosion() == null) {
            return;
        }
        final Entity exploder = event.getExplosion().getExploder();
        if (exploder == null) {
            return;
        }
        if (!exploder.getPersistentData().getBoolean(TAG_NO_BLOCK_DAMAGE)) {
            return;
        }
        event.getAffectedBlocks().clear();
    }

    public static void markNoBlockDamage(final Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentData().putBoolean(TAG_NO_BLOCK_DAMAGE, true);
    }
}
