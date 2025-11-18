package com.fullfud.fullfud.common.entity;

import com.fullfud.fullfud.FullfudMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public enum ShahedColor {
    WHITE(0, "white", new ResourceLocation(FullfudMod.MOD_ID, "textures/entity/shahed_136.png")),
    BLACK(1, "black", new ResourceLocation(FullfudMod.MOD_ID, "textures/entity/shahed_136_black.png"));

    private final int id;
    private final String translationKey;
    private final ResourceLocation texture;

    ShahedColor(final int id, final String translationKey, final ResourceLocation texture) {
        this.id = id;
        this.translationKey = translationKey;
        this.texture = texture;
    }

    public int getId() {
        return id;
    }

    public ResourceLocation getTexture() {
        return texture;
    }

    public Component getDisplayName() {
        return Component.translatable("color.fullfud.shahed." + translationKey);
    }

    public ShahedColor next() {
        final ShahedColor[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static ShahedColor byId(final int id) {
        for (final ShahedColor color : values()) {
            if (color.id == id) {
                return color;
            }
        }
        return WHITE;
    }
}
