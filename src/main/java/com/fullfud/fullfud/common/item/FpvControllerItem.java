package com.fullfud.fullfud.common.item;

import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.common.item.FpvGogglesItem;
import com.fullfud.fullfud.common.menu.ShahedMonitorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

import java.util.Optional;
import java.util.UUID;

public class FpvControllerItem extends Item {
    private static final String DRONE_TAG = "LinkedFpvDrone";

    public FpvControllerItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        final Optional<UUID> linked = getLinkedDrone(stack);
        if (linked.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.controller.no_link"), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        final UUID droneId = linked.get();
        FpvDroneEntity.find(serverLevel, droneId).ifPresentOrElse(drone -> {
            if (!FpvGogglesItem.isLinkedTo(serverPlayer, droneId)) {
                player.displayClientMessage(Component.translatable("message.fullfud.fpv.goggles.required"), true);
                return;
            }
            if (!drone.beginRemoteSession(serverPlayer)) {
                return;
            }
            NetworkHooks.openScreen(serverPlayer,
                new SimpleMenuProvider((containerId, inv, ply) -> new ShahedMonitorMenu(containerId, inv, droneId, drone.getId(), ShahedMonitorMenu.Target.FPV),
                    Component.translatable("menu.fullfud.shahed_monitor")),
                buf -> {
                    buf.writeUUID(droneId);
                    buf.writeInt(drone.getId());
                    buf.writeEnum(ShahedMonitorMenu.Target.FPV);
                });
        }, () -> {
            clearLinkedDrone(stack);
            player.displayClientMessage(Component.translatable("message.fullfud.monitor.drone_missing"), true);
        });
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static boolean isController(final ItemStack stack) {
        return stack.getItem() instanceof FpvControllerItem;
    }

    public static Optional<UUID> getLinkedDrone(final ItemStack stack) {
        final var tag = stack.getTag();
        if (tag == null || !tag.hasUUID(DRONE_TAG)) {
            return Optional.empty();
        }
        return Optional.of(tag.getUUID(DRONE_TAG));
    }

    public static void setLinkedDrone(final ItemStack stack, final UUID droneId) {
        stack.getOrCreateTag().putUUID(DRONE_TAG, droneId);
    }

    public static void clearLinkedDrone(final ItemStack stack) {
        final var tag = stack.getTag();
        if (tag != null) {
            tag.remove(DRONE_TAG);
        }
    }
}
