package com.fullfud.fullfud.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class FpvConfiguratorScreen extends Screen {
    private final UUID droneId;

    public FpvConfiguratorScreen(final UUID droneId) {
        super(Component.translatable("screen.fullfud.fpv_configurator.title"));
        this.droneId = droneId;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(width / 2 - 50, height - 28, 100, 20)
            .build());
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        graphics.drawCenteredString(
            font,
            Component.translatable("screen.fullfud.fpv_configurator.empty"),
            width / 2,
            height / 2 - 6,
            0xAFAFAF
        );
        if (droneId != null) {
            graphics.drawCenteredString(font, Component.literal(droneId.toString()), width / 2, height / 2 + 8, 0x6F6F6F);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
