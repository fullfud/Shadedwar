package com.fullfud.fullfud.client.model;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.item.MonitorItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class MonitorModel extends GeoModel<MonitorItem> {
    private static final ResourceLocation MODEL = new ResourceLocation(FullfudMod.MOD_ID, "geo/monitorshahed.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FullfudMod.MOD_ID, "textures/item/monitor.png");
    private static final ResourceLocation ANIM = new ResourceLocation(FullfudMod.MOD_ID, "animations/fpv.animation.json");

    @Override
    public ResourceLocation getModelResource(MonitorItem object) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(MonitorItem object) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(MonitorItem animatable) {
        return ANIM;
    }
}