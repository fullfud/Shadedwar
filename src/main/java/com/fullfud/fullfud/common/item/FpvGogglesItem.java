package com.fullfud.fullfud.common.item;

import com.fullfud.fullfud.client.render.FpvGogglesRenderer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public class FpvGogglesItem extends ArmorItem implements GeoItem {
    public static final String LINK_TAG = "LinkedFpvDrone";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.fpv_goggles.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public FpvGogglesItem(final Properties properties) {
        super(GogglesMaterial.INSTANCE, ArmorItem.Type.HELMET, properties);
    }

    @Override
    public void registerControllers(final AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "fpv_goggles", 0, state -> state.setAndContinue(IDLE)));
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
            public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(final LivingEntity living, final ItemStack stack, final EquipmentSlot slot, final net.minecraft.client.model.HumanoidModel<?> original) {
                if (renderer == null) {
                    renderer = new FpvGogglesRenderer();
                }
                renderer.prepForRender(living, stack, slot, original);
                return renderer;
            }
        });
    }

    public static void setLinked(final ItemStack stack, final java.util.UUID id) {
        stack.getOrCreateTag().putUUID(LINK_TAG, id);
    }

    public static java.util.Optional<java.util.UUID> getLinked(final ItemStack stack) {
        final var tag = stack.getTag();
        if (tag == null || !tag.hasUUID(LINK_TAG)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(tag.getUUID(LINK_TAG));
    }

    private enum GogglesMaterial implements ArmorMaterial {
        INSTANCE;

        @Override
        public int getDurabilityForType(final ArmorItem.Type type) {
            return 150;
        }

        @Override
        public int getDefenseForType(final ArmorItem.Type type) {
            return type == ArmorItem.Type.HELMET ? 1 : 0;
        }

        @Override
        public int getEnchantmentValue() {
            return 8;
        }

        @Override
        public net.minecraft.sounds.SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_LEATHER;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return "fullfud:fpv_goggles";
        }

        @Override
        public float getToughness() {
            return 0.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0F;
        }
    }
}
