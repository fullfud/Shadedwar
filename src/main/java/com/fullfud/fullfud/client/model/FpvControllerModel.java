package com.fullfud.fullfud.client.model;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.item.FpvControllerItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class FpvControllerModel extends GeoModel<FpvControllerItem> {
    private static final ResourceLocation MODEL = new ResourceLocation(FullfudMod.MOD_ID, "geo/joystickfpv.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FullfudMod.MOD_ID, "textures/item/joystick.png");
    private static final ResourceLocation ANIM = new ResourceLocation(FullfudMod.MOD_ID, "animations/fpv.animation.json");

    @Override
    public ResourceLocation getModelResource(FpvControllerItem object) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(FpvControllerItem object) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(FpvControllerItem animatable) {
        return ANIM;
    }
}