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
    private static final ResourceLocation ANIM = new ResourceLocation(FullfudMod.MOD_ID, "animations/fpv.animation.json");

    @Override
    public ResourceLocation getModelResource(final FpvDroneEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(final FpvDroneEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(final FpvDroneEntity entity) {
        return ANIM;
    }

    @Override
    public void setCustomAnimations(FpvDroneEntity animatable, long instanceId, AnimationState<FpvDroneEntity> animationState) {
        CoreGeoBone body = getAnimationProcessor().getBone("Body");

        if (body != null) {
            body.setRotX((float) Math.toRadians(-animatable.getXRot()));

            body.setRotZ((float) Math.toRadians(-animatable.getVisualRoll(animationState.getPartialTick())));
        }
    }
}