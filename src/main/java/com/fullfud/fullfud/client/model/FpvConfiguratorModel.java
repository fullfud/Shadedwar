package com.fullfud.fullfud.client.model;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.item.FpvConfiguratorItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class FpvConfiguratorModel extends GeoModel<FpvConfiguratorItem> {
    private static final ResourceLocation MODEL = new ResourceLocation(FullfudMod.MOD_ID, "geo/monitorshahed.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FullfudMod.MOD_ID, "textures/item/monitor.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(FullfudMod.MOD_ID, "animations/fpv.animation.json");

    @Override
    public ResourceLocation getModelResource(final FpvConfiguratorItem object) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(final FpvConfiguratorItem object) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(final FpvConfiguratorItem animatable) {
        return ANIMATION;
    }
}
