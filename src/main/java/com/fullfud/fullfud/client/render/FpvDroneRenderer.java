package com.fullfud.fullfud.client.render;

import com.fullfud.fullfud.client.model.FpvDroneModel;
import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class FpvDroneRenderer extends GeoEntityRenderer<FpvDroneEntity> {
    public FpvDroneRenderer(final EntityRendererProvider.Context context) {
        super(context, new FpvDroneModel());
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(final FpvDroneEntity entity, final float entityYaw, final float partialTick, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedLight) {
        poseStack.pushPose();
        
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getVisualRoll(partialTick)));

        poseStack.translate(0.0D, -0.05D, 0.0D);
        
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(final FpvDroneEntity entity, final Frustum frustum, final double x, final double y, final double z) {
        if (super.shouldRender(entity, frustum, x, y, z)) {
            return true;
        }
        final double distSq = x * x + y * y + z * z;
        final double max = 256.0D;
        return distSq <= max * max && frustum.isVisible(entity.getBoundingBoxForCulling());
    }
}