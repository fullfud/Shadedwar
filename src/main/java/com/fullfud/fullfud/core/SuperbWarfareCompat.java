package com.fullfud.fullfud.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

public final class SuperbWarfareCompat {
    private static final String VEHICLE_ENTITY_CLASS = "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity";
    private static final String MOD_DAMAGE_TYPES_CLASS = "com.atsuishio.superbwarfare.init.ModDamageTypes";

    @Nullable
    private static Class<?> vehicleClass;
    private static boolean vehicleClassLoaded;

    @Nullable
    private static Method customExplosionDamageFactory;
    private static boolean customExplosionFactoryLoaded;

    private SuperbWarfareCompat() {
    }

    public static boolean isVehicle(@Nullable final Entity entity) {
        if (entity == null) {
            return false;
        }

        final Class<?> resolvedVehicleClass = resolveVehicleClass();
        if (resolvedVehicleClass != null) {
            return resolvedVehicleClass.isInstance(entity);
        }

        try {
            for (Class<?> clazz = entity.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                final String className = clazz.getName();
                if (className.contains("superbwarfare") && className.contains("vehicle")) {
                    return true;
                }
            }
        } catch (final Exception ignored) {
            return false;
        }

        return false;
    }

    public static DamageSource explosionDamageSource(
        final ServerLevel level,
        final Entity directEntity,
        @Nullable final Entity attacker
    ) {
        final Method factory = resolveCustomExplosionFactory();
        if (factory != null) {
            try {
                final Object result = factory.invoke(null, level.registryAccess(), directEntity, attacker);
                if (result instanceof DamageSource damageSource) {
                    return damageSource;
                }
            } catch (final Exception ignored) {
            }
        }

        return level.damageSources().explosion(directEntity, attacker != null ? attacker : directEntity);
    }

    @Nullable
    private static Class<?> resolveVehicleClass() {
        if (!vehicleClassLoaded) {
            vehicleClassLoaded = true;
            try {
                vehicleClass = Class.forName(VEHICLE_ENTITY_CLASS);
            } catch (final Exception ignored) {
                vehicleClass = null;
            }
        }
        return vehicleClass;
    }

    @Nullable
    private static Method resolveCustomExplosionFactory() {
        if (!customExplosionFactoryLoaded) {
            customExplosionFactoryLoaded = true;
            try {
                customExplosionDamageFactory = Class.forName(MOD_DAMAGE_TYPES_CLASS).getMethod(
                    "causeCustomExplosionDamage",
                    net.minecraft.core.RegistryAccess.class,
                    Entity.class,
                    Entity.class
                );
            } catch (final Exception ignored) {
                customExplosionDamageFactory = null;
            }
        }
        return customExplosionDamageFactory;
    }
}
