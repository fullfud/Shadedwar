package com.fullfud.fullfud.common.item;

import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public class FpvDroneItem extends Item {
    private final double signalRangeScale;
    private final double signalPenetrationScale;

    public FpvDroneItem(final Properties properties) {
        this(properties, 1.0D, 1.0D);
    }

    public FpvDroneItem(final Properties properties, final double signalRangeScale, final double signalPenetrationScale) {
        super(properties);
        this.signalRangeScale = signalRangeScale;
        this.signalPenetrationScale = signalPenetrationScale;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        final BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        final FpvDroneEntity drone = FullfudRegistries.FPV_DRONE_ENTITY.get().create(serverLevel);
        if (drone == null) {
            return InteractionResult.FAIL;
        }
        final Direction facing = context.getHorizontalDirection();
        drone.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY() + 0.05D, spawnPos.getZ() + 0.5D, facing.toYRot(), 0.0F);
        drone.setSignalScales(signalRangeScale, signalPenetrationScale);
        serverLevel.addFreshEntity(drone);
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            drone.setOwner(serverPlayer);
        }
        if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(serverLevel.isClientSide);
    }
}
