package com.fullfud.fullfud.common.item;

import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class ShahedDroneItem extends Item {
    public ShahedDroneItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Level level = context.getLevel();
        final Player player = context.getPlayer();
        if (!(level instanceof ServerLevel serverLevel) || player == null) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        final ShahedDroneEntity drone = FullfudRegistries.SHAHED_ENTITY.get().create(serverLevel);
        if (drone == null) {
            return InteractionResult.PASS;
        }

        final Direction face = context.getClickedFace();
        final BlockPos placePos = context.getClickedPos().relative(face);
        drone.moveTo(placePos.getX() + 0.5D, placePos.getY() + 0.25D, placePos.getZ() + 0.5D, player.getYRot(), 0.0F);
        drone.setYBodyRot(player.getYRot());
        drone.setYHeadRot(player.getYRot());
        if (!serverLevel.noCollision(drone)) {
            return InteractionResult.FAIL;
        }
        drone.initializePlacement(placePos.getY() + 0.25D);
        serverLevel.addFreshEntity(drone);

        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }

        linkWithMonitor(player, drone);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    private static void linkWithMonitor(final Player player, final ShahedDroneEntity drone) {
        final ItemStack mainHand = player.getMainHandItem();
        final ItemStack offHand = player.getOffhandItem();

        if (mainHand.getItem() instanceof MonitorItem) {
            MonitorItem.setLinkedDrone(mainHand, drone.getUUID());
            if (player instanceof ServerPlayer serverPlayer) {
                drone.assignOwner(serverPlayer);
            }
            player.displayClientMessage(Component.translatable("message.fullfud.monitor.linked"), true);
            return;
        }

        if (offHand.getItem() instanceof MonitorItem) {
            MonitorItem.setLinkedDrone(offHand, drone.getUUID());
            if (player instanceof ServerPlayer serverPlayer) {
                drone.assignOwner(serverPlayer);
            }
            player.displayClientMessage(Component.translatable("message.fullfud.monitor.linked"), true);
        }
    }
}
