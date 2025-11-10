package com.fullfud.fullfud.client.model;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class FpvDroneModel extends GeoModel<FpvDroneEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(FullfudMod.MOD_ID, "geo/fpv_drone.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FullfudMod.MOD_ID, "textures/entity/fpv_drone.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(FullfudMod.MOD_ID, "animations/fpv_drone.animation.json");

    @Override
    public ResourceLocation getModelResource(final FpvDroneEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(final FpvDroneEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(final FpvDroneEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(final FpvDroneEntity animatable, final long instanceId, final AnimationState<FpvDroneEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        final CoreGeoBone root = this.getAnimationProcessor().getBone("root");
        if (root == null) {
            return;
        }
        final float partialTick = animationState.getPartialTick();
        final float pitch = (float) Math.toRadians(animatable.getVisualPitch(partialTick));
        final float roll = (float) Math.toRadians(animatable.getVisualRoll(partialTick));
        root.setRotX(pitch);
        root.setRotZ(-roll);
    }
}
