package com.fullfud.fullfud.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class FullfudServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Server SERVER;

    static {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SERVER = new Server(builder);
        SPEC = builder.build();
    }

    private FullfudServerConfig() {
    }

    public static final class Server {
        public final ForgeConfigSpec.BooleanValue disableExplosionBlockDamage;

        private Server(final ForgeConfigSpec.Builder builder) {
            builder.push("world");

            disableExplosionBlockDamage = builder
                .comment("Globally disable block destruction from all explosions in every dimension, including TNT, creepers and mod explosions.")
                .define("disableExplosionBlockDamage", false);

            builder.pop();
        }
    }
}
