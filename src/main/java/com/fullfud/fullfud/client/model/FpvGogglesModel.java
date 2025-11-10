package com.fullfud.fullfud.client.model;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.item.FpvGogglesItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class FpvGogglesModel extends GeoModel<FpvGogglesItem> {
    private static final ResourceLocation MODEL = new ResourceLocation(FullfudMod.MOD_ID, "geo/fpv_goggles.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FullfudMod.MOD_ID, "textures/models/armor/fpv_goggles.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(FullfudMod.MOD_ID, "animations/fpv_goggles.animation.json");

    @Override
    public ResourceLocation getModelResource(final FpvGogglesItem animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(final FpvGogglesItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(final FpvGogglesItem animatable) {
        return ANIMATION;
    }
}
