package com.fullfud.fullfud.common.item;

import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.common.item.FpvGogglesItem;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.common.menu.ShahedMonitorMenu;
import com.fullfud.fullfud.core.data.ShahedLinkData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import java.util.Optional;
import java.util.UUID;

public class MonitorItem extends Item {
    private static final String DRONE_TAG = "LinkedShahed";
    private static final String FPV_TAG = "LinkedFpv";

    public MonitorItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        final Optional<UUID> linkedFpv = getLinkedFpvDrone(stack);
        if (linkedFpv.isPresent()) {
            openFpvMonitor(serverPlayer, stack, linkedFpv.get());
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        final Optional<UUID> linkedDrone = getLinkedDrone(stack);
        if (linkedDrone.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fullfud.monitor.no_link"), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        final UUID droneId = linkedDrone.get();
        final ServerLevel serverLevel = serverPlayer.serverLevel();
        ShahedDroneEntity.find(serverLevel, droneId).ifPresentOrElse(drone -> {
            drone.assignOwner(serverPlayer);
            drone.addViewer(serverPlayer);
            if (!drone.beginRemoteControl(serverPlayer)) {
                player.displayClientMessage(Component.translatable("message.fullfud.monitor.in_use"), true);
                return;
            }
            NetworkHooks.openScreen(serverPlayer,
                new SimpleMenuProvider((containerId, inv, ply) -> new ShahedMonitorMenu(containerId, inv, droneId, drone.getId(), ShahedMonitorMenu.Target.SHAHED),
                    Component.translatable("menu.fullfud.shahed_monitor")),
                buf -> {
                    buf.writeUUID(droneId);
                    buf.writeInt(drone.getId());
                    buf.writeEnum(ShahedMonitorMenu.Target.SHAHED);
                });
        }, () -> {
            ShahedLinkData.get(serverLevel).unlink(droneId);
            clearLinkedDrone(stack);
            player.displayClientMessage(Component.translatable("message.fullfud.monitor.drone_missing"), true);
        });

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static Optional<UUID> getLinkedDrone(final ItemStack stack) {
        final CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(DRONE_TAG)) {
            return Optional.empty();
        }
        return Optional.of(tag.getUUID(DRONE_TAG));
    }

    public static void setLinkedDrone(final ItemStack stack, final UUID droneId) {
        stack.getOrCreateTag().putUUID(DRONE_TAG, droneId);
    }

    public static void clearLinkedDrone(final ItemStack stack) {
        final CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(DRONE_TAG);
        }
    }

    public static Optional<UUID> getLinkedFpvDrone(final ItemStack stack) {
        final CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(FPV_TAG)) {
            return Optional.empty();
        }
        return Optional.of(tag.getUUID(FPV_TAG));
    }

    public static void setLinkedFpvDrone(final ItemStack stack, final UUID droneId) {
        stack.getOrCreateTag().putUUID(FPV_TAG, droneId);
    }

    public static void clearLinkedFpv(final ItemStack stack) {
        final CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(FPV_TAG);
        }
    }

    public static boolean openFpvMonitor(final ServerPlayer serverPlayer, final UUID droneId) {
        final ServerLevel serverLevel = serverPlayer.serverLevel();
        return FpvDroneEntity.find(serverLevel, droneId).map(drone -> {
            if (!drone.beginRemoteSession(serverPlayer)) {
                return false;
            }
            NetworkHooks.openScreen(serverPlayer,
                new SimpleMenuProvider((containerId, inv, ply) -> new ShahedMonitorMenu(containerId, inv, droneId, drone.getId(), ShahedMonitorMenu.Target.FPV),
                    Component.translatable("menu.fullfud.shahed_monitor")),
                buf -> {
                    buf.writeUUID(droneId);
                    buf.writeInt(drone.getId());
                    buf.writeEnum(ShahedMonitorMenu.Target.FPV);
                });
            return true;
        }).orElseGet(() -> {
            serverPlayer.displayClientMessage(Component.translatable("message.fullfud.monitor.drone_missing"), true);
            return false;
        });
    }

    public static boolean openFpvMonitor(final ServerPlayer serverPlayer, final ItemStack stack, final UUID droneId) {
        if (!FpvGogglesItem.isLinkedTo(serverPlayer, droneId)) {
            serverPlayer.displayClientMessage(Component.translatable("message.fullfud.fpv.goggles.required"), true);
            clearLinkedFpv(stack);
            return false;
        }
        final boolean result = openFpvMonitor(serverPlayer, droneId);
        if (!result) {
            clearLinkedFpv(stack);
        }
        return result;
    }
}
