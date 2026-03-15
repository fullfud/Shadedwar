package com.fullfud.fullfud.client.screen;

import com.fullfud.fullfud.client.input.FpvControllerInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ControllerSelectScreen extends Screen {
    private final ControllerCalibrationScreen parentScreen;
    private List<FpvControllerInput.ConnectedController> controllers = List.of();

    public ControllerSelectScreen(final ControllerCalibrationScreen parentScreen) {
        super(Component.translatable("screen.fullfud.calibration.controller.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        controllers = FpvControllerInput.listConnectedControllers();

        final int buttonWidth = 300;
        int y = 36;
        if (controllers.isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.fullfud.calibration.controller.none"), button -> { })
                .bounds(width / 2 - buttonWidth / 2, y, buttonWidth, 20)
                .build()).active = false;
            y += 26;
        } else {
            final String selectedName = parentScreen.getSelectedControllerName();
            for (final FpvControllerInput.ConnectedController controller : controllers) {
                final Component label = Component.literal(
                    (controller.name().equalsIgnoreCase(selectedName) ? "> " : "")
                        + controller.name()
                        + " [" + controller.axisCount() + "/" + controller.buttonCount() + "]"
                );
                addRenderableWidget(Button.builder(label, button -> choose(controller))
                    .bounds(width / 2 - buttonWidth / 2, y, buttonWidth, 20)
                    .build());
                y += 24;
            }
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.fullfud.calibration.cancel"), button -> onClose())
            .bounds(width / 2 - 75, height - 28, 150, 20)
            .build());
    }

    private void choose(final FpvControllerInput.ConnectedController controller) {
        parentScreen.handleControllerChosen(controller);
        if (minecraft != null && minecraft.screen == this) {
            minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }
}
