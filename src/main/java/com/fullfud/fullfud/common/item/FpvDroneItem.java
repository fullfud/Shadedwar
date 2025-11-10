package com.fullfud.fullfud.common.item;

import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

public class FpvDroneItem extends Item {
    public FpvDroneItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Level level = context.getLevel();
        final Player player = context.getPlayer();
        if (!(level instanceof ServerLevel serverLevel) || player == null) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        final FpvDroneEntity drone = FullfudRegistries.FPV_DRONE_ENTITY.get().create(serverLevel);
        if (drone == null) {
            return InteractionResult.FAIL;
        }

        final Direction face = context.getClickedFace();
        final BlockPos placePos = context.getClickedPos().relative(face);
        final double x = placePos.getX() + 0.5D;
        final double y = placePos.getY() + 0.1D;
        final double z = placePos.getZ() + 0.5D;
        drone.moveTo(x, y, z, player.getYRot(), 0.0F);
        drone.setYHeadRot(player.getYRot());
        serverLevel.addFreshEntity(drone);
        if (player instanceof ServerPlayer serverPlayer) {
            drone.assignOperator(serverPlayer);
        }
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
