package com.fullfud.fullfud.client.model;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.entity.RebEmitterEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class RebEmitterModel extends GeoModel<RebEmitterEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(FullfudMod.MOD_ID, "geo/reb_emitter.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FullfudMod.MOD_ID, "textures/entity/reb_emitter.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(FullfudMod.MOD_ID, "animations/reb.animation.json");

    @Override
    public ResourceLocation getModelResource(final RebEmitterEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(final RebEmitterEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(final RebEmitterEntity animatable) {
        return ANIMATION;
    }
}
