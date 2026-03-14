package com.fullfud.fullfud.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

public final class RemotePlayerProtection {
    private static final String ROOT_TAG = "fullfud_remote_protection";
    private static final String TAG_PREV_INVULNERABLE = "PrevInvulnerable";
    private static final String TAG_EXPIRES_AT = "ExpiresAt";
    private static final long DURATION_TICKS = 20L;

    private RemotePlayerProtection() {
    }

    public static void touch(final ServerPlayer player) {
        if (player == null) {
            return;
        }
        final CompoundTag root = player.getPersistentData();
        final CompoundTag protectionTag = root.contains(ROOT_TAG, Tag.TAG_COMPOUND)
            ? root.getCompound(ROOT_TAG)
            : new CompoundTag();
        if (!protectionTag.contains(TAG_PREV_INVULNERABLE, Tag.TAG_BYTE)) {
            protectionTag.putBoolean(TAG_PREV_INVULNERABLE, player.getAbilities().invulnerable);
        }
        protectionTag.putLong(TAG_EXPIRES_AT, currentTick(player) + DURATION_TICKS);
        root.put(ROOT_TAG, protectionTag);
        setInvulnerable(player, true);
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
        if (!player.getAbilities().invulnerable) {
            setInvulnerable(player, true);
        }
    }

    public static void clear(final ServerPlayer player) {
        if (player == null) {
            return;
        }
        final CompoundTag root = player.getPersistentData();
        final CompoundTag protectionTag = root.contains(ROOT_TAG, Tag.TAG_COMPOUND)
            ? root.getCompound(ROOT_TAG)
            : null;
        final boolean restoreInvulnerable = protectionTag != null
            && protectionTag.contains(TAG_PREV_INVULNERABLE, Tag.TAG_BYTE)
            && protectionTag.getBoolean(TAG_PREV_INVULNERABLE);
        setInvulnerable(player, restoreInvulnerable);
        root.remove(ROOT_TAG);
    }

    private static void setInvulnerable(final ServerPlayer player, final boolean invulnerable) {
        player.getAbilities().invulnerable = invulnerable;
        player.onUpdateAbilities();
        player.invulnerableTime = 0;
        player.fallDistance = 0.0F;
    }

    private static long currentTick(final ServerPlayer player) {
        return player.serverLevel().getGameTime();
    }
}
