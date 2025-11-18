package com.fullfud.fullfud.client.model;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.entity.ShahedLauncherEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ShahedLauncherModel extends GeoModel<ShahedLauncherEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(FullfudMod.MOD_ID, "geo/launcher.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FullfudMod.MOD_ID, "textures/entity/shahed_launcher.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(FullfudMod.MOD_ID, "animations/shahed_136.animation.json");

    @Override
    public ResourceLocation getModelResource(final ShahedLauncherEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(final ShahedLauncherEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(final ShahedLauncherEntity animatable) {
        return ANIMATION;
    }
}
