package com.fullfud.fullfud.core;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.common.menu.ShahedMonitorMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FullfudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RemoteControlFailsafe {
    private RemoteControlFailsafe() { }

    private static java.lang.reflect.Method chunkUpdateMethod;
    private static java.lang.reflect.Field chunkMapField;
    private static java.lang.reflect.Method chunkMapUpdatePlayerStatusMethod;

    @SubscribeEvent
    public static void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        final CompoundTag root = player.getPersistentData();

        if (root.contains(FpvDroneEntity.PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)) {
            final CompoundTag tag = root.getCompound(FpvDroneEntity.PLAYER_REMOTE_TAG);
            if (FpvDroneEntity.isRemoteControlActive(player.getServer(), player.getUUID(), tag)) {
                forceChunkTracking(player);
            } else {
                FpvDroneEntity.forceRestoreFromPersistentData(player, tag);
                root.remove(FpvDroneEntity.PLAYER_REMOTE_TAG);
            }
        }

        if (root.contains(ShahedDroneEntity.PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)) {
            final CompoundTag tag = root.getCompound(ShahedDroneEntity.PLAYER_REMOTE_TAG);
            final boolean active = player.containerMenu instanceof ShahedMonitorMenu;
            if (active) {
                forceChunkTracking(player);
            } else {
                ShahedDroneEntity.forceRestoreFromPersistentData(player, tag);
                root.remove(ShahedDroneEntity.PLAYER_REMOTE_TAG);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        final CompoundTag root = player.getPersistentData();
        if (root.contains(FpvDroneEntity.PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)) {
            final CompoundTag tag = root.getCompound(FpvDroneEntity.PLAYER_REMOTE_TAG);
            FpvDroneEntity.forceReleaseFromPersistentData(player.getServer(), player.getUUID(), tag);
        }
        if (root.contains(ShahedDroneEntity.PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)) {
            final CompoundTag tag = root.getCompound(ShahedDroneEntity.PLAYER_REMOTE_TAG);
            ShahedDroneEntity.forceReleaseFromPersistentData(player.getServer(), player.getUUID(), tag);
        }
    }

    public static void forceChunkTracking(final ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final ServerChunkCache chunkSource = serverLevel.getChunkSource();
        try {
            if (chunkUpdateMethod == null) {
                chunkUpdateMethod = resolveChunkUpdateMethod(chunkSource.getClass());
            }
            if (chunkUpdateMethod != null) {
                chunkUpdateMethod.invoke(chunkSource, player);
            }
        } catch (Throwable ignored) {
            // Best-effort: if reflection fails, we keep the player anchored without forcing chunk tracking.
        }
    }

    public static void forceChunkRefresh(final ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final ServerChunkCache chunkSource = serverLevel.getChunkSource();
        try {
            final Object chunkMap = resolveChunkMap(chunkSource);
            if (chunkMap == null) {
                return;
            }
            if (chunkMapUpdatePlayerStatusMethod == null) {
                chunkMapUpdatePlayerStatusMethod = resolveChunkMapUpdatePlayerStatusMethod(chunkMap.getClass());
            }
            if (chunkMapUpdatePlayerStatusMethod != null) {
                chunkMapUpdatePlayerStatusMethod.invoke(chunkMap, player, true);
            }
        } catch (Throwable ignored) {
            // Best-effort: avoid crashing if reflective access fails.
        }
    }

    private static java.lang.reflect.Method resolveChunkUpdateMethod(final Class<?> chunkSourceClass) {
        for (final java.lang.reflect.Method method : chunkSourceClass.getDeclaredMethods()) {
            final Class<?>[] params = method.getParameterTypes();
            if (params.length == 1 && params[0] == ServerPlayer.class && method.getReturnType() == void.class) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Object resolveChunkMap(final ServerChunkCache chunkSource) throws IllegalAccessException {
        if (chunkMapField == null) {
            for (final java.lang.reflect.Field field : chunkSource.getClass().getDeclaredFields()) {
                if (ChunkMap.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    chunkMapField = field;
                    break;
                }
            }
        }
        return chunkMapField == null ? null : chunkMapField.get(chunkSource);
    }

    private static java.lang.reflect.Method resolveChunkMapUpdatePlayerStatusMethod(final Class<?> chunkMapClass) {
        for (final java.lang.reflect.Method method : chunkMapClass.getDeclaredMethods()) {
            final Class<?>[] params = method.getParameterTypes();
            if (params.length == 2 && params[0] == ServerPlayer.class && params[1] == boolean.class && method.getReturnType() == void.class) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }
}
