package com.fullfud.fullfud.client.model;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class ShahedDroneModel extends GeoModel<ShahedDroneEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(FullfudMod.MOD_ID, "geo/shahed_136.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FullfudMod.MOD_ID, "textures/entity/shahed_136.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(FullfudMod.MOD_ID, "animations/shahed_136.animation.json");

    @Override
    public ResourceLocation getModelResource(final ShahedDroneEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(final ShahedDroneEntity animatable) {
        return TEXTURE;
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
        float extraPitch = 0.0F;
        final Vec3 motion = animatable.getDeltaMovement();
        final double speedSq = motion.lengthSqr();
        if (speedSq > 1.0E-3D) {
            final double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            if (horizontalSpeed > 0.1D || Math.abs(motion.y) > 0.05D) {
                final float velocityPitch = (float) Math.atan2(motion.y, Math.max(1.0E-3D, horizontalSpeed));
                final float pitchWeight = (float) Mth.clamp(horizontalSpeed / 12.0D, 0.0D, 1.0D);
                extraPitch = Mth.clamp((velocityPitch - basePitchRad) * pitchWeight * 8.0F, (float) Math.toRadians(-60.0D), (float) Math.toRadians(60.0D));
            }
        }

        root.setRotX(basePitchRad + extraPitch);
        root.setRotZ(0.0F);
    }
}
