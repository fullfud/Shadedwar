package com.fullfud.fullfud.core;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.common.menu.ShahedMonitorMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FullfudMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RemoteControlFailsafe {
    private RemoteControlFailsafe() { }

    @SubscribeEvent
    public static void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        final CompoundTag root = player.getPersistentData();

        if (root.contains(ShahedDroneEntity.PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)
            && !(player.containerMenu instanceof ShahedMonitorMenu)) {
            ShahedDroneEntity.forceRestoreFromPersistentData(player, root.getCompound(ShahedDroneEntity.PLAYER_REMOTE_TAG));
            root.remove(ShahedDroneEntity.PLAYER_REMOTE_TAG);
        }

        if (root.contains(FpvDroneEntity.PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)) {
            final CompoundTag tag = root.getCompound(FpvDroneEntity.PLAYER_REMOTE_TAG);
            if (!isFpvControlActive(player, tag)) {
                FpvDroneEntity.forceRestoreFromPersistentData(player, tag);
                root.remove(FpvDroneEntity.PLAYER_REMOTE_TAG);
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
        final boolean hasLinkedGoggles = head.getItem() instanceof com.fullfud.fullfud.common.item.FpvGogglesItem
            && com.fullfud.fullfud.common.item.FpvGogglesItem.getLinked(head).filter(droneId::equals).isPresent();
        if (!hasLinkedGoggles) {
            return false;
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
}
