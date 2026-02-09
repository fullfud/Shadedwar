package com.fullfud.fullfud.common.item;

import com.fullfud.fullfud.client.render.MonitorRenderer;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.common.menu.ShahedMonitorMenu;
import com.fullfud.fullfud.core.data.ShahedLinkData;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
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
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class MonitorItem extends Item implements GeoItem {
    private static final String DRONE_TAG = "LinkedShahed";
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MonitorItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
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
            if (!drone.assignOwner(serverPlayer)) {
                player.displayClientMessage(Component.translatable("message.fullfud.monitor.in_use"), true);
                return;
            }
            if (!drone.beginRemoteControl(serverPlayer)) {
                player.displayClientMessage(Component.translatable("message.fullfud.monitor.in_use"), true);
                return;
            }
            drone.addViewer(serverPlayer);
            try {
                NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((containerId, inv, ply) -> new ShahedMonitorMenu(containerId, inv, droneId, drone.getId()),
                        Component.translatable("menu.fullfud.shahed_monitor")),
                    buf -> {
                        buf.writeUUID(droneId);
                        buf.writeInt(drone.getId());
                    });
            } catch (Throwable t) {
                drone.removeViewer(serverPlayer);
                drone.endRemoteControl(serverPlayer);
                player.displayClientMessage(Component.translatable("message.fullfud.monitor.open_failed"), true);
            }
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
            private MonitorRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null)
                    this.renderer = new MonitorRenderer();
                return this.renderer;
            }
        });
    }
}
