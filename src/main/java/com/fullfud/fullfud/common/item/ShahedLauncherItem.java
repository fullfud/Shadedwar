package com.fullfud.fullfud.common.item;

import com.fullfud.fullfud.common.entity.ShahedLauncherEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ShahedLauncherItem extends Item {
    public ShahedLauncherItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Level level = context.getLevel();
        final Player player = context.getPlayer();
        if (!(level instanceof ServerLevel serverLevel) || player == null) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        final Direction face = context.getClickedFace();
        final BlockPos clickedPos = context.getClickedPos();
        final BlockPos placePos = face == Direction.UP ? clickedPos.above() : clickedPos.relative(face);
        if (!serverLevel.isEmptyBlock(placePos)) {
            return InteractionResult.FAIL;
        }

        final BlockState support = serverLevel.getBlockState(placePos.below());
        if (!support.isFaceSturdy(serverLevel, placePos.below(), Direction.UP)) {
            return InteractionResult.FAIL;
        }

        final ShahedLauncherEntity launcher = FullfudRegistries.SHAHED_LAUNCHER_ENTITY.get().create(serverLevel);
        if (launcher == null) {
            return InteractionResult.FAIL;
        }

        final ItemStack stack = context.getItemInHand();
        launcher.moveTo(placePos.getX() + 0.5D, placePos.getY(), placePos.getZ() + 0.5D, player.getYRot(), 0.0F);
        if (!serverLevel.noCollision(launcher)) {
            launcher.discard();
            return InteractionResult.FAIL;
        }

        serverLevel.addFreshEntity(launcher);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

}
