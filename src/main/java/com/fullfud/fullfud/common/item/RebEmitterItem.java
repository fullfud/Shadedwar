package com.fullfud.fullfud.common.item;

import com.fullfud.fullfud.common.entity.RebEmitterEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class RebEmitterItem extends Item {
    public RebEmitterItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
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

        final RebEmitterEntity emitter = FullfudRegistries.REB_EMITTER_ENTITY.get().create(serverLevel);
        if (emitter == null) {
            return InteractionResult.FAIL;
        }

        emitter.moveTo(placePos.getX() + 0.5D, placePos.getY(), placePos.getZ() + 0.5D, context.getRotation(), 0.0F);
        if (!serverLevel.noCollision(emitter)) {
            emitter.discard();
            return InteractionResult.FAIL;
        }

        serverLevel.addFreshEntity(emitter);
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
