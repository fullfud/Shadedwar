package com.fullfud.fullfud.core;

import net.minecraft.world.level.GameRules;

public final class FullfudGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> DISABLE_EXPLOSION_BLOCK_DAMAGE = GameRules.register(
        "DisableExplosionsFullfud",
        GameRules.Category.MISC,
        GameRules.BooleanValue.create(false)
    );

    private FullfudGameRules() {
    }

    public static void init() {
    }
}
