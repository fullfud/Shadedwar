package com.fullfud.fullfud.core;

import com.fullfud.fullfud.common.entity.PlayerDecoyEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDecoyManager {
    private static final Map<UUID, DecoyData> ACTIVE_DECOYS = new ConcurrentHashMap<>();

    private PlayerDecoyManager() {
    }

    @Nullable
    public static PlayerDecoyEntity createDecoy(final ServerPlayer player, final Entity drone) {
        if (player.level().isClientSide() || !(player.level() instanceof ServerLevel level) || drone == null) {
            return null;
        }
        removeDecoy(player.getUUID());

        final PlayerDecoyEntity decoy = FullfudRegistries.PLAYER_DECOY_ENTITY.get().create(level);
        if (decoy == null) {
            return null;
        }

        decoy.initFromPlayer(player);
        level.addFreshEntity(decoy);
        retargetMobsToDecoy(level, player, decoy);
        ACTIVE_DECOYS.put(player.getUUID(), new DecoyData(decoy.getUUID(), drone.getUUID(), level));
        return decoy;
    }

    public static void removeDecoy(final UUID playerUUID) {
        final DecoyData data = ACTIVE_DECOYS.remove(playerUUID);
        if (data == null) {
            return;
        }
        final Entity entity = data.level.getEntity(data.decoyEntityId);
        if (entity instanceof PlayerDecoyEntity decoy) {
            decoy.discard();
        }
    }

    public static void removeDecoyByDrone(final UUID droneUUID) {
        if (droneUUID == null) {
            return;
        }
        UUID ownerId = null;
        for (final Map.Entry<UUID, DecoyData> entry : ACTIVE_DECOYS.entrySet()) {
            if (entry.getValue().droneId.equals(droneUUID)) {
                ownerId = entry.getKey();
                break;
            }
        }
        if (ownerId != null) {
            removeDecoy(ownerId);
        }
    }

    public static void unregisterDecoy(final UUID playerUUID, final UUID decoyEntityId) {
        if (playerUUID == null || decoyEntityId == null) {
            return;
        }
        ACTIVE_DECOYS.computeIfPresent(playerUUID, (ignored, data) -> data.decoyEntityId.equals(decoyEntityId) ? null : data);
    }

    public static boolean hasDecoy(final UUID playerUUID) {
        return ACTIVE_DECOYS.containsKey(playerUUID);
    }

    @Nullable
    public static PlayerDecoyEntity getDecoy(final UUID playerUUID) {
        final DecoyData data = ACTIVE_DECOYS.get(playerUUID);
        if (data == null) {
            return null;
        }
        final Entity entity = data.level.getEntity(data.decoyEntityId);
        return entity instanceof PlayerDecoyEntity decoy ? decoy : null;
    }

    @Nullable
    public static PlayerDecoyEntity getDecoyByDrone(final UUID droneUUID) {
        for (final DecoyData data : ACTIVE_DECOYS.values()) {
            if (!data.droneId.equals(droneUUID)) {
                continue;
            }
            final Entity entity = data.level.getEntity(data.decoyEntityId);
            if (entity instanceof PlayerDecoyEntity decoy) {
                return decoy;
            }
        }
        return null;
    }

    public static void syncDecoyEquipment(final ServerPlayer player) {
        final PlayerDecoyEntity decoy = getDecoy(player.getUUID());
        if (decoy != null) {
            decoy.copyEquipment(player);
        }
    }

    public static void syncDecoyHealth(final ServerPlayer player) {
        final PlayerDecoyEntity decoy = getDecoy(player.getUUID());
        if (decoy != null) {
            decoy.syncHealthFromOwner(player.getHealth());
        }
    }

    public static void clearLevel(final ServerLevel level) {
        ACTIVE_DECOYS.entrySet().removeIf(entry -> {
            if (entry.getValue().level != level) {
                return false;
            }
            final Entity entity = level.getEntity(entry.getValue().decoyEntityId);
            if (entity != null) {
                entity.discard();
            }
            return true;
        });
    }

    public static void clearAll() {
        for (final DecoyData data : ACTIVE_DECOYS.values()) {
            final Entity entity = data.level.getEntity(data.decoyEntityId);
            if (entity != null) {
                entity.discard();
            }
        }
        ACTIVE_DECOYS.clear();
    }

    public static boolean isDecoy(final Entity entity) {
        return entity instanceof PlayerDecoyEntity;
    }

    @Nullable
    public static UUID getDecoyOwner(final PlayerDecoyEntity decoy) {
        return decoy.getOwnerUUID();
    }

    private static void retargetMobsToDecoy(final ServerLevel level, final ServerPlayer owner, final PlayerDecoyEntity decoy) {
        final AABB searchBox = decoy.getBoundingBox().inflate(64.0D);
        for (final Mob mob : level.getEntitiesOfClass(Mob.class, searchBox)) {
            if (mob.getTarget() == owner) {
                mob.setTarget(decoy);
            }
        }
    }

    private record DecoyData(UUID decoyEntityId, UUID droneId, ServerLevel level) {
    }
}
