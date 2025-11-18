package com.fullfud.fullfud.client.render;

import com.fullfud.fullfud.client.model.ShahedLauncherModel;
import com.fullfud.fullfud.common.entity.ShahedLauncherEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ShahedLauncherRenderer extends GeoEntityRenderer<ShahedLauncherEntity> {

    public ShahedLauncherRenderer(final EntityRendererProvider.Context context) {
        super(context, new ShahedLauncherModel());
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(final ShahedLauncherEntity entity, final float entityYaw, final float partialTick, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }
}
