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
        return true;
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
        final ServerLevel level = serverPlayer.serverLevel();
        if (ShahedDroneEntity.find(level, droneId).map(drone -> {
            drone.removeViewer(serverPlayer);
            drone.endRemoteControl(serverPlayer);
            return true;
        }).orElse(false)) {
            return;
        }

        final CompoundTag root = serverPlayer.getPersistentData();
        if (root.contains(ShahedDroneEntity.PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)) {
            ShahedDroneEntity.forceRestoreFromPersistentData(serverPlayer, root.getCompound(ShahedDroneEntity.PLAYER_REMOTE_TAG));
            root.remove(ShahedDroneEntity.PLAYER_REMOTE_TAG);
        }
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
