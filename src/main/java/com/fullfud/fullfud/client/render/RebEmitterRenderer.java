package com.fullfud.fullfud.client.render;

import com.fullfud.fullfud.client.model.RebEmitterModel;
import com.fullfud.fullfud.common.entity.RebEmitterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class RebEmitterRenderer extends GeoEntityRenderer<RebEmitterEntity> {

    public RebEmitterRenderer(final EntityRendererProvider.Context context) {
        super(context, new RebEmitterModel());
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(final RebEmitterEntity entity, final float entityYaw, final float partialTick, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        poseStack.translate(0.0D, 0.0D, 0.0D);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }
}
