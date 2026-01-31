package com.fullfud.fullfud.core;

import com.fullfud.fullfud.FullfudMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.LargeFireball;
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
        if (!(exploder instanceof LargeFireball fireball)) {
            return;
        }
        if (!fireball.getPersistentData().getBoolean(TAG_NO_BLOCK_DAMAGE)) {
            return;
        }
        event.getAffectedBlocks().clear();
    }

    public static void markNoBlockDamage(final LargeFireball fireball) {
        if (fireball == null) {
            return;
        }
        fireball.getPersistentData().putBoolean(TAG_NO_BLOCK_DAMAGE, true);
    }
}
