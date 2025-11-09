package com.fullfud.fullfud.client.render;

import com.fullfud.fullfud.client.model.ShahedDroneModel;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ShahedDroneRenderer extends GeoEntityRenderer<ShahedDroneEntity> {

    public ShahedDroneRenderer(final EntityRendererProvider.Context context) {
        super(context, new ShahedDroneModel());
        this.shadowRadius = 0.45F;
    }

    @Override
    public void render(final ShahedDroneEntity entity, final float entityYaw, final float partialTick, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        final float pitch = entity.getVisualPitch(partialTick);
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getVisualRoll(partialTick)));
        poseStack.translate(0.0D, -0.25D, 0.0D);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(final ShahedDroneEntity entity, final Frustum frustum, final double x, final double y, final double z) {
        if (super.shouldRender(entity, frustum, x, y, z)) {
            return true;
        }
        final double distSq = x * x + y * y + z * z;
        final double max = 800.0D;
        return distSq <= max * max && frustum.isVisible(entity.getBoundingBoxForCulling());
    }
}
