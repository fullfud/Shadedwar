package com.fullfud.fullfud.common.menu;

import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

public class ShahedMonitorMenu extends AbstractContainerMenu {
    private final UUID droneId;
    private final int droneEntityId;

    public ShahedMonitorMenu(final int containerId, final Inventory inventory, final FriendlyByteBuf buffer) {
        this(containerId, inventory, readDroneUuid(buffer), readDroneEntityId(buffer));
    }

    public ShahedMonitorMenu(final int containerId, final Inventory inventory, final UUID droneId, final int droneEntityId) {
        super(FullfudRegistries.SHAHED_MONITOR_MENU.get(), containerId);
        this.droneId = droneId;
        this.droneEntityId = droneEntityId;
    }

    public UUID getDroneId() {
        return droneId;
    }

    public int getDroneEntityId() {
        return droneEntityId;
    }

    @Override
    public boolean stillValid(final Player player) {
        if (droneId == null || player == null || !player.isAlive()) {
            return false;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        return findDrone(serverPlayer, droneId).isPresent();
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(final Player player) {
        super.removed(player);
        if (!(player instanceof ServerPlayer serverPlayer) || droneId == null) {
            return;
        }
        if (findDrone(serverPlayer, droneId).map(drone -> {
            drone.removeViewer(serverPlayer);
            drone.endRemoteControl(serverPlayer);
            return true;
        }).orElse(false)) {
            return;
        }

        final CompoundTag root = serverPlayer.getPersistentData();
        if (root.contains(ShahedDroneEntity.PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)) {
            final CompoundTag tag = root.getCompound(ShahedDroneEntity.PLAYER_REMOTE_TAG);
            ShahedDroneEntity.forceRestoreFromPersistentData(serverPlayer, tag);
            tag.putLong("FreezeUntil", serverPlayer.level().getGameTime() + 60);
            tag.putBoolean("FreezeOnly", true);
            root.put(ShahedDroneEntity.PLAYER_REMOTE_TAG, tag);
        }
    }

    private static Optional<ShahedDroneEntity> findDrone(final ServerPlayer player, final UUID droneId) {
        final ServerLevel currentLevel = player.serverLevel();
        final Optional<ShahedDroneEntity> local = ShahedDroneEntity.find(currentLevel, droneId);
        if (local.isPresent()) {
            return local;
        }
        if (player.getServer() == null) {
            return Optional.empty();
        }
        for (final ServerLevel level : player.getServer().getAllLevels()) {
            if (level == currentLevel) {
                continue;
            }
            final Optional<ShahedDroneEntity> found = ShahedDroneEntity.find(level, droneId);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static UUID readDroneUuid(final FriendlyByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() < 16) {
            return null;
        }
        return buffer.readUUID();
    }

    private static int readDroneEntityId(final FriendlyByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() < 4) {
            return -1;
        }
        return buffer.readInt();
    }
}
