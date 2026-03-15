package com.fullfud.fullfud.core;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.core.config.FullfudServerConfig;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FullfudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExplosionControl {
    private ExplosionControl() {
    }

    public static boolean isExplosionBlockDamageDisabled(final Level level) {
        if (FullfudServerConfig.SERVER.disableExplosionBlockDamage.get()) {
            return true;
        }
        return level != null && level.getGameRules().getBoolean(FullfudGameRules.DISABLE_EXPLOSION_BLOCK_DAMAGE);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionDetonate(final ExplosionEvent.Detonate event) {
        if (event == null || event.getExplosion() == null || event.getLevel() == null) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (isExplosionBlockDamageDisabled(event.getLevel())) {
            event.getAffectedBlocks().clear();
        }
    }
}
