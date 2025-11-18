package com.fullfud.fullfud.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

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

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        return InteractionResult.PASS;
    }
}
