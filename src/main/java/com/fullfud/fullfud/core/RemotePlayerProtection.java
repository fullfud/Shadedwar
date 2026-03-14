package com.fullfud.fullfud.core;

import com.fullfud.fullfud.FullfudMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FullfudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RemotePlayerProtection {
    private static final String ROOT_TAG = "fullfud_remote_protection";
    private static final String TAG_EXPIRES_AT = "ExpiresAt";
    private static final String TAG_DRONE_UUID = "Drone";
    private static final String TAG_DRONE_DIM = "DroneDim";
    private static final String TAG_HAZARD_DRONE_UUID = "fullfud_protected_drone";
    private static final String TAG_HAZARD_DRONE_DIM = "fullfud_protected_drone_dim";
    private static final long DURATION_TICKS = 20L;

    private RemotePlayerProtection() {
    }

    public static void touch(final ServerPlayer player) {
        touch(player, null, 0.0D);
    }

    public static void touch(final ServerPlayer player, final Entity drone, final double radius) {
        if (player == null) {
            return;
        }
        final CompoundTag root = player.getPersistentData();
        final CompoundTag protectionTag = root.contains(ROOT_TAG, Tag.TAG_COMPOUND)
            ? root.getCompound(ROOT_TAG)
            : new CompoundTag();
        protectionTag.putLong(TAG_EXPIRES_AT, currentTick(player) + DURATION_TICKS);
        if (drone != null) {
            protectionTag.putUUID(TAG_DRONE_UUID, drone.getUUID());
            protectionTag.putString(TAG_DRONE_DIM, drone.level().dimension().location().toString());
        }
        root.put(ROOT_TAG, protectionTag);
        player.fallDistance = 0.0F;
    }

    public static void markHazard(final Entity hazard, final Entity drone) {
        if (hazard == null || drone == null) {
            return;
        }
        final CompoundTag root = hazard.getPersistentData();
        root.putUUID(TAG_HAZARD_DRONE_UUID, drone.getUUID());
        root.putString(TAG_HAZARD_DRONE_DIM, drone.level().dimension().location().toString());
    }

    public static void copyHazardTag(final Entity target, final Entity source) {
        if (target == null || source == null) {
            return;
        }
        final CompoundTag sourceTag = source.getPersistentData();
        if (!sourceTag.hasUUID(TAG_HAZARD_DRONE_UUID) || !sourceTag.contains(TAG_HAZARD_DRONE_DIM, Tag.TAG_STRING)) {
            return;
        }
        final CompoundTag targetTag = target.getPersistentData();
        targetTag.putUUID(TAG_HAZARD_DRONE_UUID, sourceTag.getUUID(TAG_HAZARD_DRONE_UUID));
        targetTag.putString(TAG_HAZARD_DRONE_DIM, sourceTag.getString(TAG_HAZARD_DRONE_DIM));
    }

    public static void tick(final ServerPlayer player) {
        if (player == null) {
            return;
        }
        final CompoundTag root = player.getPersistentData();
        if (!root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        final CompoundTag protectionTag = root.getCompound(ROOT_TAG);
        if (currentTick(player) > protectionTag.getLong(TAG_EXPIRES_AT)) {
            clear(player);
            return;
        }
        player.fallDistance = 0.0F;
    }

    public static void clear(final ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.fallDistance = 0.0F;
        player.getPersistentData().remove(ROOT_TAG);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(final LivingAttackEvent event) {
        if (event == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (shouldCancelDamage(player, event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(final LivingHurtEvent event) {
        if (event == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (shouldCancelDamage(player, event.getSource())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }

    private static boolean shouldCancelDamage(final ServerPlayer player, final DamageSource source) {
        if (player == null || source == null) {
            return false;
        }
        if (source.is(DamageTypes.GENERIC_KILL)) {
            return false;
        }
        final ProtectedDrone protectedDrone = resolveProtectedDrone(player);
        if (protectedDrone == null) {
            return false;
        }
        return matchesProtectedDrone(protectedDrone, source.getDirectEntity())
            || matchesProtectedDrone(protectedDrone, source.getEntity());
    }

    private static ProtectedDrone resolveProtectedDrone(final ServerPlayer player) {
        final CompoundTag root = player.getPersistentData();
        if (!root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        final CompoundTag protectionTag = root.getCompound(ROOT_TAG);
        if (currentTick(player) > protectionTag.getLong(TAG_EXPIRES_AT)) {
            clear(player);
            return null;
        }
        if (!protectionTag.hasUUID(TAG_DRONE_UUID) || !protectionTag.contains(TAG_DRONE_DIM, Tag.TAG_STRING)) {
            return null;
        }
        final ResourceLocation dimensionId = ResourceLocation.tryParse(protectionTag.getString(TAG_DRONE_DIM));
        if (dimensionId == null) {
            return null;
        }
        return new ProtectedDrone(protectionTag.getUUID(TAG_DRONE_UUID), dimensionId);
    }

    private static boolean matchesProtectedDrone(final ProtectedDrone protectedDrone, final Entity sourceEntity) {
        if (protectedDrone == null || sourceEntity == null) {
            return false;
        }
        if (sourceEntity.getUUID().equals(protectedDrone.droneId)
            && sourceEntity.level().dimension().location().equals(protectedDrone.dimensionId)) {
            return true;
        }
        final CompoundTag root = sourceEntity.getPersistentData();
        return root.hasUUID(TAG_HAZARD_DRONE_UUID)
            && root.getUUID(TAG_HAZARD_DRONE_UUID).equals(protectedDrone.droneId)
            && root.contains(TAG_HAZARD_DRONE_DIM, Tag.TAG_STRING)
            && protectedDrone.dimensionId.toString().equals(root.getString(TAG_HAZARD_DRONE_DIM));
    }

    private static long currentTick(final ServerPlayer player) {
        return player.serverLevel().getGameTime();
    }

    private record ProtectedDrone(java.util.UUID droneId, ResourceLocation dimensionId) {
    }
}
