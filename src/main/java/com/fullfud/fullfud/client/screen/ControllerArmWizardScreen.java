package com.fullfud.fullfud.client.screen;

import com.fullfud.fullfud.client.input.ControllerCalibration;
import com.fullfud.fullfud.client.input.FpvControllerInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class ControllerArmWizardScreen extends Screen {
    private static final float ASSIGN_THRESHOLD = 0.3F;

    private final ControllerCalibrationScreen parentScreen;
    private final ControllerCalibration calibration;

    private Button backButton;
    private Button proceedButton;
    private Button retryButton;
    private Button flipButton;

    private float[] baselineAxes = new float[0];
    private byte[] baselineButtons = new byte[0];
    private float[] liveAxes = new float[0];
    private byte[] liveButtons = new byte[0];
    private String liveControllerName = "";
    private Step step = Step.BEGIN;

    public ControllerArmWizardScreen(
        final ControllerCalibrationScreen parentScreen,
        final ControllerCalibration calibration
    ) {
        super(Component.translatable("screen.fullfud.calibration.wizard.arm.title"));
        this.parentScreen = parentScreen;
        this.calibration = calibration;
    }

    @Override
    protected void init() {
        final int bottom = height - 28;
        backButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                button -> onBack()
            )
            .bounds(width / 2 - 155, bottom, 100, 20)
            .build());
        proceedButton = addRenderableWidget(Button.builder(
                Component.empty(),
                button -> onProceed()
            )
            .bounds(width / 2 + 55, bottom, 100, 20)
            .build());
        retryButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.fullfud.calibration.wizard.retry"),
                button -> restartListening()
            )
            .bounds(width / 2 - 50, bottom - 24, 100, 20)
            .build());
        flipButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.fullfud.calibration.wizard.arm.flip_button"),
                button -> flipArmDirection()
            )
            .bounds(width / 2 - 70, bottom - 24, 140, 20)
            .build());

        refreshLiveState();
        restartListening();
        refreshButtons();
    }

    @Override
    public void tick() {
        super.tick();
        refreshLiveState();
        if (step == Step.BEGIN) {
            detectArmBinding();
        }
        refreshButtons();
    }

    private void refreshLiveState() {
        final FpvControllerInput.RawJoystickState rawState = FpvControllerInput.snapshotRawState(calibration);
        if (rawState == null) {
            liveAxes = new float[0];
            liveButtons = new byte[0];
            liveControllerName = "";
            return;
        }

        liveAxes = rawState.axes();
        liveButtons = rawState.buttons();
        liveControllerName = rawState.name();
    }

    private void restartListening() {
        step = Step.BEGIN;
        baselineAxes = liveAxes.clone();
        baselineButtons = liveButtons.clone();
    }

    private void detectArmBinding() {
        final int buttonIndex = detectPressedButton();
        if (buttonIndex >= 0) {
            calibration.setArmBinding(buttonIndex, false);
            parentScreen.persistWorkingCalibration();
            step = Step.VERIFY;
            return;
        }

        final int axisIndex = detectMovedAxis();
        if (axisIndex < 0) {
            return;
        }

        calibration.setArmBinding(-axisIndex - 1, deltaForAxis(axisIndex) < 0.0F);
        parentScreen.persistWorkingCalibration();
        step = Step.VERIFY;
    }

    private void onProceed() {
        switch (step) {
            case BEGIN -> {
            }
            case VERIFY -> step = Step.FLIP;
            case FLIP -> closeToParent();
        }
    }

    private void onBack() {
        switch (step) {
            case BEGIN -> closeToParent();
            case VERIFY -> restartListening();
            case FLIP -> step = Step.VERIFY;
        }
    }

    private void flipArmDirection() {
        if (!calibration.hasArmBinding()) {
            return;
        }
        calibration.setArmBinding(calibration.getArmBinding(), !calibration.isArmInverted());
        parentScreen.persistWorkingCalibration();
    }

    private void closeToParent() {
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }

    private int detectMovedAxis() {
        int bestAxis = -1;
        float bestDelta = ASSIGN_THRESHOLD;
        final int count = Math.min(liveAxes.length, baselineAxes.length);
        for (int i = 0; i < count; i++) {
            final float delta = Math.abs(deltaForAxis(i));
            if (delta > bestDelta) {
                bestDelta = delta;
                bestAxis = i;
            }
        }
        return bestAxis;
    }

    private int detectPressedButton() {
        final int count = Math.min(liveButtons.length, baselineButtons.length);
        for (int i = 0; i < count; i++) {
            if (liveButtons[i] != baselineButtons[i] && liveButtons[i] != 0) {
                return i;
            }
        }
        return -1;
    }

    private float deltaForAxis(final int axisIndex) {
        final float current = axisIndex >= 0 && axisIndex < liveAxes.length && Float.isFinite(liveAxes[axisIndex]) ? liveAxes[axisIndex] : 0.0F;
        final float baseline = axisIndex >= 0 && axisIndex < baselineAxes.length && Float.isFinite(baselineAxes[axisIndex]) ? baselineAxes[axisIndex] : 0.0F;
        return current - baseline;
    }

    private boolean isArmPressed() {
        if (!calibration.hasArmBinding()) {
            return false;
        }

        if (!calibration.isArmBindingAxis()) {
            final int buttonIndex = calibration.getArmButtonIndex();
            return buttonIndex >= 0 && buttonIndex < liveButtons.length && liveButtons[buttonIndex] == GLFW.GLFW_PRESS;
        }

        final int axisIndex = calibration.getArmAxisIndex();
        if (axisIndex < 0 || axisIndex >= liveAxes.length) {
            return false;
        }
        float value = liveAxes[axisIndex];
        if (calibration.isArmInverted()) {
            value = -value;
        }
        return value > 0.1F;
    }

    private void refreshButtons() {
        if (proceedButton == null) {
            return;
        }

        proceedButton.setMessage(switch (step) {
            case BEGIN -> Component.translatable("screen.fullfud.calibration.assign.waiting", Component.literal(""));
            case VERIFY -> Component.translatable("gui.proceed");
            case FLIP -> Component.translatable("gui.done");
        });
        proceedButton.active = step != Step.BEGIN;

        retryButton.visible = step == Step.VERIFY;
        flipButton.visible = step == Step.FLIP;
    }

    private Component currentTitle() {
        return switch (step) {
            case BEGIN -> Component.translatable("screen.fullfud.calibration.wait_arm");
            case VERIFY -> Component.translatable("screen.fullfud.calibration.wizard.arm.verify");
            case FLIP -> Component.translatable("screen.fullfud.calibration.wizard.arm.flip");
        };
    }

    private Component currentSubtitle() {
        return switch (step) {
            case BEGIN -> Component.translatable("screen.fullfud.calibration.wizard.arm.begin");
            case VERIFY -> Component.translatable("screen.fullfud.calibration.wizard.arm.verify_hint");
            case FLIP -> Component.translatable("screen.fullfud.calibration.wizard.arm.flip_hint");
        };
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        final int cx = width / 2;
        graphics.drawCenteredString(font, title, cx, 16, 0xFFFFFF);
        graphics.drawCenteredString(font, currentTitle(), cx, 36, 0xFFFFFF);
        graphics.drawCenteredString(font, currentSubtitle(), cx, 50, 0xBBBBBB);
        graphics.drawCenteredString(
            font,
            Component.translatable(
                "screen.fullfud.calibration.debug.current_controller",
                liveControllerName.isBlank() ? "-" : liveControllerName
            ),
            cx,
            66,
            0x88C0FF
        );

        graphics.drawCenteredString(
            font,
            Component.translatable(
                "screen.fullfud.calibration.debug.arm_state",
                Component.translatable(
                    isArmPressed()
                        ? "screen.fullfud.calibration.debug.pressed"
                        : "screen.fullfud.calibration.debug.not_pressed"
                ),
                FpvControllerInput.describeArmBinding(calibration, FpvControllerInput.findJoystickId(calibration))
            ),
            cx,
            102,
            0xFFFFFF
        );
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Step {
        BEGIN,
        VERIFY,
        FLIP
    }
}
