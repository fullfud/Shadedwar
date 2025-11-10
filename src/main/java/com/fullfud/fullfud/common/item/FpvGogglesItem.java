package com.fullfud.fullfud.common.item;

import com.fullfud.fullfud.client.render.FpvGogglesRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class FpvGogglesItem extends ArmorItem implements GeoItem {
    private static final String DRONE_TAG = "LinkedFpvDrone";
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public FpvGogglesItem(final Properties properties) {
        super(ArmorMaterials.LEATHER, ArmorItem.Type.HELMET, properties);
    }

    @Override
    public void registerControllers(final AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(final Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private FpvGogglesRenderer renderer;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(final LivingEntity living, final ItemStack stack,
                                                          final EquipmentSlot slot, final HumanoidModel<?> original) {
                if (renderer == null) {
                    renderer = new FpvGogglesRenderer();
                }
                renderer.prepForRender(living, stack, slot, original);
                return renderer;
            }
        });
    }

    public static boolean isWearing(final Player player) {
        if (player == null) {
            return false;
        }
        final ItemStack stack = player.getItemBySlot(EquipmentSlot.HEAD);
        return stack.getItem() instanceof FpvGogglesItem;
    }

    public static Optional<UUID> getLinkedDrone(final Player player) {
        if (!isWearing(player)) {
            return Optional.empty();
        }
        return getLinkedDrone(player.getItemBySlot(EquipmentSlot.HEAD));
    }

    public static boolean isLinkedTo(final Player player, final UUID droneId) {
        return isWearing(player) && getLinkedDrone(player).map(droneId::equals).orElse(false);
    }

    public static Optional<UUID> getLinkedDrone(final ItemStack stack) {
        if (!(stack.getItem() instanceof FpvGogglesItem)) {
            return Optional.empty();
        }
        final var tag = stack.getTag();
        if (tag == null || !tag.hasUUID(DRONE_TAG)) {
            return Optional.empty();
        }
        return Optional.of(tag.getUUID(DRONE_TAG));
    }

    public static void setLinkedDrone(final ItemStack stack, final UUID droneId) {
        if (!(stack.getItem() instanceof FpvGogglesItem)) {
            return;
        }
        stack.getOrCreateTag().putUUID(DRONE_TAG, droneId);
    }

    public static boolean linkFromPlayer(final ServerPlayer player, final UUID droneId) {
        final ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof FpvGogglesItem)) {
            return false;
        }
        setLinkedDrone(head, droneId);
        player.displayClientMessage(Component.translatable("message.fullfud.fpv.goggles.linked"), true);
        return true;
    }
}
