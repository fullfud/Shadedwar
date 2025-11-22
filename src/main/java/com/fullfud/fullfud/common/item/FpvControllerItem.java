package com.fullfud.fullfud.common.item;

import com.fullfud.fullfud.client.render.FpvControllerRenderer;
import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class FpvControllerItem extends Item implements GeoItem {
    private static final String LINK_TAG = "LinkedFpvDrone";
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public FpvControllerItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            clearLink(stack);
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.link_cleared"), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        final Optional<UUID> linked = getLinked(stack);
        if (linked.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.no_link"), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        final ServerLevel serverLevel = serverPlayer.serverLevel();
        final var entity = serverLevel.getEntity(linked.get());
        if (!(entity instanceof FpvDroneEntity drone) || drone.isRemoved()) {
            clearLink(stack);
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.drone_missing"), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        drone.beginControl(serverPlayer);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public void link(final ItemStack stack, final FpvDroneEntity drone, final Player player) {
        setLinked(stack, drone.getUUID());
        if (player instanceof ServerPlayer serverPlayer) {
            drone.setOwner(serverPlayer);
        }
        player.displayClientMessage(Component.translatable("message.fullfud.fpv.linked"), true);
        linkGoggles(player, drone.getUUID());
    }

    public static Optional<UUID> getLinked(final ItemStack stack) {
        final var tag = stack.getTag();
        if (tag == null || !tag.hasUUID(LINK_TAG)) {
            return Optional.empty();
        }
        return Optional.of(tag.getUUID(LINK_TAG));
    }

    public static void setLinked(final ItemStack stack, final UUID id) {
        stack.getOrCreateTag().putUUID(LINK_TAG, id);
    }

    public static void clearLink(final ItemStack stack) {
        final var tag = stack.getTag();
        if (tag != null) {
            tag.remove(LINK_TAG);
        }
    }

    private static void linkGoggles(final Player player, final java.util.UUID id) {
        final ItemStack head = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if (head.getItem() instanceof FpvGogglesItem) {
            FpvGogglesItem.setLinked(head, id);
            return;
        }
        for (final ItemStack stack : player.getHandSlots()) {
            if (stack.getItem() instanceof FpvGogglesItem) {
                FpvGogglesItem.setLinked(stack, id);
                return;
            }
        }
        if (player.getInventory() != null) {
            for (final ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof FpvGogglesItem) {
                    FpvGogglesItem.setLinked(stack, id);
                    return;
                }
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private FpvControllerRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null)
                    this.renderer = new FpvControllerRenderer();
                return this.renderer;
            }
        });
    }
}