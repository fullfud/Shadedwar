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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FullfudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RemoteControlFailsafe {
    private RemoteControlFailsafe() { }

    private static final String TAG_ORIGIN_DIM = "OriginDim";
    private static final String TAG_ORIGIN_X = "OriginX";
    private static final String TAG_ORIGIN_Y = "OriginY";
    private static final String TAG_ORIGIN_Z = "OriginZ";
    private static final String TAG_ORIGIN_YAW = "OriginYaw";
    private static final String TAG_ORIGIN_PITCH = "OriginPitch";
    private static final double ANCHOR_EPSILON_SQR = 0.0004D;
    private static final double HEIGHT_EPSILON = 0.01D;
    private static final String TAG_FREEZE_UNTIL = "FreezeUntil";
    private static final String TAG_FREEZE_ONLY = "FreezeOnly";
    private static final int POST_FREEZE_TICKS = 60;
    private static final int CHUNK_TRACK_INTERVAL_TICKS = 20;
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

        if (root.contains(ShahedDroneEntity.PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)) {
            final CompoundTag tag = root.getCompound(ShahedDroneEntity.PLAYER_REMOTE_TAG);
            final boolean active = player.containerMenu instanceof ShahedMonitorMenu;
            final boolean postFreeze = isPostFreezeActive(player, tag);
            if (active) {
                clearPostFreeze(tag);
                keepPlayerHeight(player, tag);
            } else if (postFreeze) {
                keepPlayerHeight(player, tag);
            } else if (tag.getBoolean(TAG_FREEZE_ONLY)) {
                root.remove(ShahedDroneEntity.PLAYER_REMOTE_TAG);
            } else {
                ShahedDroneEntity.forceRestoreFromPersistentData(player, tag);
                startPostFreeze(player, tag);
                if (!isPostFreezeActive(player, tag)) {
                    root.remove(ShahedDroneEntity.PLAYER_REMOTE_TAG);
                }
            }
        }

        if (root.contains(FpvDroneEntity.PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)) {
            final CompoundTag tag = root.getCompound(FpvDroneEntity.PLAYER_REMOTE_TAG);
            final boolean active = isFpvControlActive(player, tag);
            final boolean postFreeze = isPostFreezeActive(player, tag);
            if (active) {
                clearPostFreeze(tag);
                keepPlayerHeight(player, tag);
            } else if (postFreeze) {
                keepPlayerHeight(player, tag);
            } else if (tag.getBoolean(TAG_FREEZE_ONLY)) {
                root.remove(FpvDroneEntity.PLAYER_REMOTE_TAG);
            } else {
                FpvDroneEntity.forceRestoreFromPersistentData(player, tag);
                startPostFreeze(player, tag);
                if (!isPostFreezeActive(player, tag)) {
                    root.remove(FpvDroneEntity.PLAYER_REMOTE_TAG);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        final CompoundTag root = player.getPersistentData();
        if (root.contains(ShahedDroneEntity.PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)) {
            final CompoundTag tag = root.getCompound(ShahedDroneEntity.PLAYER_REMOTE_TAG);
            ShahedDroneEntity.forceReleaseFromPersistentData(player.getServer(), player.getUUID(), tag);
        }
        if (root.contains(FpvDroneEntity.PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)) {
            final CompoundTag tag = root.getCompound(FpvDroneEntity.PLAYER_REMOTE_TAG);
            FpvDroneEntity.forceReleaseFromPersistentData(player.getServer(), player.getUUID(), tag);
        }
    }

    private static boolean isFpvControlActive(final ServerPlayer player, final CompoundTag tag) {
        if (player == null || tag == null || player.getServer() == null) {
            return false;
        }
        if (!tag.hasUUID("Drone")) {
            return false;
        }
        final java.util.UUID droneId = tag.getUUID("Drone");

        final ItemStack head = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof com.fullfud.fullfud.common.item.FpvGogglesItem)) {
            return false;
        }
        final var linked = com.fullfud.fullfud.common.item.FpvGogglesItem.getLinked(head);
        if (linked.isPresent() && !linked.get().equals(droneId)) {
            return false;
        }
        if (linked.isEmpty()) {
            com.fullfud.fullfud.common.item.FpvGogglesItem.setLinked(head, droneId);
        }

        for (final var level : player.getServer().getAllLevels()) {
            final var entity = level.getEntity(droneId);
            if (entity instanceof FpvDroneEntity drone) {
                final java.util.UUID controller = drone.getControllerId();
                return controller != null && controller.equals(player.getUUID());
            }
        }
        return false;
    }

    private static void keepPlayerHeight(final ServerPlayer player, final CompoundTag tag) {
        if (player == null || tag == null) {
            return;
        }
        if (!tag.contains(TAG_ORIGIN_Y, Tag.TAG_DOUBLE)) {
            return;
        }
        if (tag.contains(TAG_ORIGIN_DIM, Tag.TAG_STRING)) {
            final String dimId = tag.getString(TAG_ORIGIN_DIM);
            if (!dimId.isBlank() && !dimId.equals(player.level().dimension().location().toString())) {
                return;
            }
        }

        final double y = tag.getDouble(TAG_ORIGIN_Y);
        if (Math.abs(player.getY() - y) > HEIGHT_EPSILON) {
            player.teleportTo(player.getX(), y, player.getZ());
        }
        final Vec3 vel = player.getDeltaMovement();
        player.setDeltaMovement(vel.x, 0.0D, vel.z);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
        if (player.tickCount % CHUNK_TRACK_INTERVAL_TICKS == 0) {
            forceChunkTracking(player);
        }
    }

    private static boolean isPostFreezeActive(final ServerPlayer player, final CompoundTag tag) {
        if (player == null || tag == null || !tag.contains(TAG_FREEZE_UNTIL, Tag.TAG_LONG)) {
            return false;
        }
        final long until = tag.getLong(TAG_FREEZE_UNTIL);
        return player.level().getGameTime() <= until;
    }

    private static void startPostFreeze(final ServerPlayer player, final CompoundTag tag) {
        if (player == null || tag == null) {
            return;
        }
        tag.putLong(TAG_FREEZE_UNTIL, player.level().getGameTime() + POST_FREEZE_TICKS);
        tag.putBoolean(TAG_FREEZE_ONLY, true);
    }

    private static void clearPostFreeze(final CompoundTag tag) {
        if (tag == null) {
            return;
        }
        tag.remove(TAG_FREEZE_UNTIL);
        tag.remove(TAG_FREEZE_ONLY);
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

    public static void resetViewpointChunksToPlayer(final ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!(player instanceof dev.lazurite.lattice.impl.api.player.InternalLatticeServerPlayer lattice)) {
            return;
        }
        final ChunkPos pos = player.chunkPosition();
        final var viewWrapper = lattice.getViewpointChunkPosSupplierWrapper();
        if (viewWrapper != null) {
            viewWrapper.setLastChunkPos(pos);
            viewWrapper.setLastLastChunkPos(pos);
        }
        final var playerWrapper = lattice.getChunkPosSupplierWrapper();
        if (playerWrapper != null) {
            playerWrapper.setLastChunkPos(pos);
            playerWrapper.setLastLastChunkPos(pos);
        }
    }

    public static void ensureLatticePlayerRegistered(final ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!(player.level() instanceof dev.lazurite.lattice.impl.api.level.InternalLatticeServerLevel latticeLevel)) {
            return;
        }
        // Re-register and rebind to self to restore the chunk-pos graph after remote view usage.
        latticeLevel.registerPlayer(player);
        latticeLevel.unbind(player);
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
