package com.fullfud.fullfud.common.item;

import com.fullfud.fullfud.common.entity.RebEmitterEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

public class RebBatteryItem extends Item {
    public static final String TAG_ENERGY_TICKS = "EnergyTicks";
    public static final int MAX_CHARGE_TICKS = 200 * 20;

    public RebBatteryItem(final Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getDefaultInstance() {
        final ItemStack stack = super.getDefaultInstance();
        setChargeTicks(stack, MAX_CHARGE_TICKS);
        return stack;
    }

    @Override
    public void appendHoverText(final ItemStack stack, @Nullable final Level level, final List<Component> tooltip, final TooltipFlag flag) {
        final int ticks = getChargeTicks(stack);
        final float percent = (ticks / (float) MAX_CHARGE_TICKS) * 100.0F;
        tooltip.add(Component.translatable("item.fullfud.reb_battery.charge", String.format("%.0f%%", percent)).withStyle(ChatFormatting.GRAY));
    }

    public static int getChargeTicks(final ItemStack stack) {
        final CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(TAG_ENERGY_TICKS)) {
            tag.putInt(TAG_ENERGY_TICKS, MAX_CHARGE_TICKS);
        }
        return Math.min(tag.getInt(TAG_ENERGY_TICKS), MAX_CHARGE_TICKS);
    }

    public static void setChargeTicks(final ItemStack stack, final int value) {
        final CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_ENERGY_TICKS, Math.max(0, Math.min(MAX_CHARGE_TICKS, value)));
    }

    // === НОВОЕ: батарейкой можно ставить эмиттер и сразу его запитывать ===
    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        final Direction face = context.getClickedFace();
        final BlockPos clickedPos = context.getClickedPos();
        final BlockPos placePos = (face == Direction.UP) ? clickedPos.above() : clickedPos.relative(face);

        // место свободно?
        if (!serverLevel.isEmptyBlock(placePos)) {
            return InteractionResult.FAIL;
        }

        // есть опора снизу?
        final BlockState support = serverLevel.getBlockState(placePos.below());
        if (!support.isFaceSturdy(serverLevel, placePos.below(), Direction.UP)) {
            return InteractionResult.FAIL;
        }

        // создаём эмиттер
        final RebEmitterEntity emitter = FullfudRegistries.REB_EMITTER_ENTITY.get().create(serverLevel);
        if (emitter == null) return InteractionResult.FAIL;

        emitter.moveTo(
                placePos.getX() + 0.5D,
                placePos.getY(),
                placePos.getZ() + 0.5D,
                context.getRotation(),
                0.0F
        );

        // коллизия ок?
        if (!serverLevel.noCollision(emitter)) {
            emitter.discard();
            return InteractionResult.FAIL;
        }

        // спавним в мире
        serverLevel.addFreshEntity(emitter);

        // если есть игрок — «прикладываем» правый клик к сущности,
        // чтобы сработала её штатная логика вставки батареи (и корректно уменьшился стак)
        if (context.getPlayer() != null) {
            // interact внутри сущности сам проверит, что в руке батарейка, вставит её и уменьшит стак
            emitter.interact(context.getPlayer(), context.getHand());
        } else {
            // если игрока нет (например, диспансер), то батарея не вставляется автоматически.
            // можно дописать логику, но по ТЗ — не требуется.
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
