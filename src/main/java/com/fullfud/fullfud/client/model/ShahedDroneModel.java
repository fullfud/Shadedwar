package com.fullfud.fullfud.client.model;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.entity.ShahedColor;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class ShahedDroneModel extends GeoModel<ShahedDroneEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(FullfudMod.MOD_ID, "geo/shahed_136.geo.json");
    private static final ResourceLocation MODEL_ON_LAUNCHER = new ResourceLocation(FullfudMod.MOD_ID, "geo/shahed_136onlauncher.geo.json");
    private static final ResourceLocation TEXTURE_WHITE = new ResourceLocation(FullfudMod.MOD_ID, "textures/entity/shahed_136.png");
    private static final ResourceLocation TEXTURE_BLACK = new ResourceLocation(FullfudMod.MOD_ID, "textures/entity/shahed_136_black.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(FullfudMod.MOD_ID, "animations/shahed_136.animation.json");

    @Override
    public ResourceLocation getModelResource(final ShahedDroneEntity animatable) {
        return animatable.isOnLauncher() ? MODEL_ON_LAUNCHER : MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(final ShahedDroneEntity animatable) {
        return animatable.getColor() == ShahedColor.BLACK ? TEXTURE_BLACK : TEXTURE_WHITE;
    }

    @Override
    public ResourceLocation getAnimationResource(final ShahedDroneEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(final ShahedDroneEntity animatable, final long instanceId, final AnimationState<ShahedDroneEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        final CoreGeoBone root = this.getAnimationProcessor().getBone("bone");
        if (root == null) {
            return;
        }
        final float partialTick = animationState.getPartialTick();
        final float basePitchRad = (float) Math.toRadians(animatable.getVisualPitch(partialTick));
        root.setRotX(-basePitchRad);
        root.setRotZ(0.0F);
    }
}
