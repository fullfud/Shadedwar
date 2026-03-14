package com.fullfud.fullfud.client.screen;

import com.fullfud.fullfud.client.input.ControllerCalibration;
import com.fullfud.fullfud.client.input.ControllerCalibrationStore;
import com.fullfud.fullfud.client.input.FpvControllerInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ControllerCalibrationScreen extends Screen {
    private static final Component TITLE = Component.translatable("screen.fullfud.calibration.title");
    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 10;
    private static final float ASSIGN_THRESHOLD = 0.35F;

    private final ControllerCalibration targetCalibration;
    private final ControllerCalibration workingCalibration;

    private Button actionButton;
    private Button cancelButton;

    private final boolean[] usedAxes = new boolean[32];
    private float[] baselineAxes = new float[0];
    private byte[] baselineButtons = new byte[0];
    private float[] liveAxes = new float[0];
    private byte[] liveButtons = new byte[0];
    private String controllerName = "";

    private Step step = Step.THROTTLE;

    public ControllerCalibrationScreen(final ControllerCalibration calibration) {
        super(TITLE);
        this.targetCalibration = calibration;
        this.workingCalibration = calibration.copy();
        this.workingCalibration.reset();
    }

    @Override
    protected void init() {
        final int cx = width / 2;
        final int bottomY = height - 40;

        actionButton = Button.builder(Component.translatable("screen.fullfud.calibration.waiting"), button -> onAction())
            .bounds(cx - 105, bottomY, 100, 20)
            .build();
        cancelButton = Button.builder(Component.translatable("screen.fullfud.calibration.cancel"), button -> onClose())
            .bounds(cx + 5, bottomY, 100, 20)
            .build();

        addRenderableWidget(actionButton);
        addRenderableWidget(cancelButton);

        beginStepListening();
        refreshActionButton();
    }

    @Override
    public void tick() {
        super.tick();

        final FpvControllerInput.RawJoystickState rawState = FpvControllerInput.snapshotRawState();
        if (rawState == null) {
            liveAxes = new float[0];
            liveButtons = new byte[0];
            controllerName = "";
            refreshActionButton();
            return;
        }

        liveAxes = rawState.axes();
        liveButtons = rawState.buttons();
        controllerName = rawState.name();
        workingCalibration.setControllerName(controllerName);

        if (step == Step.THROTTLE) {
            detectAxisAssignment(ControllerCalibration.AXIS_THROTTLE, Step.YAW);
        } else if (step == Step.YAW) {
            detectAxisAssignment(ControllerCalibration.AXIS_YAW, Step.PITCH);
        } else if (step == Step.PITCH) {
            detectAxisAssignment(ControllerCalibration.AXIS_PITCH, Step.ROLL);
        } else if (step == Step.ROLL) {
            detectAxisAssignment(ControllerCalibration.AXIS_ROLL, Step.ARM);
        } else if (step == Step.ARM) {
            detectArmBinding();
        } else if (step == Step.RANGE && workingCalibration.isSampling()) {
            workingCalibration.sampleAxes(
                logicalRaw(ControllerCalibration.AXIS_ROLL),
                logicalRaw(ControllerCalibration.AXIS_PITCH),
                logicalRaw(ControllerCalibration.AXIS_YAW),
                logicalRaw(ControllerCalibration.AXIS_THROTTLE)
            );
        }

        refreshActionButton();
    }

    private void detectAxisAssignment(final int logicalAxis, final Step nextStep) {
        final int axisIndex = detectMovedAxis();
        if (axisIndex < 0) {
            return;
        }

        final float delta = safeGet(liveAxes, axisIndex) - safeGet(baselineAxes, axisIndex);
        workingCalibration.setAxisMapping(logicalAxis, axisIndex);
        final boolean invertPositiveDirection = logicalAxis == ControllerCalibration.AXIS_YAW || logicalAxis == ControllerCalibration.AXIS_ROLL;
        workingCalibration.setAxisInverted(logicalAxis, invertPositiveDirection ? delta > 0.0F : delta < 0.0F);
        if (axisIndex >= 0 && axisIndex < usedAxes.length) {
            usedAxes[axisIndex] = true;
        }
        moveToStep(nextStep);
    }

    private void detectArmBinding() {
        final int buttonIndex = detectPressedButton();
        if (buttonIndex >= 0) {
            workingCalibration.setArmBinding(buttonIndex, false);
            moveToStep(Step.CENTER);
            return;
        }

        final int axisIndex = detectMovedAxis();
        if (axisIndex < 0) {
            return;
        }

        final float delta = safeGet(liveAxes, axisIndex) - safeGet(baselineAxes, axisIndex);
        workingCalibration.setArmBinding(-axisIndex - 1, delta < 0.0F);
        moveToStep(Step.CENTER);
    }

    private int detectMovedAxis() {
        int bestAxis = -1;
        float bestDelta = ASSIGN_THRESHOLD;
        final int max = Math.min(liveAxes.length, baselineAxes.length);
        for (int i = 0; i < max; i++) {
            if (i < usedAxes.length && usedAxes[i]) {
                continue;
            }
            final float delta = Math.abs(liveAxes[i] - baselineAxes[i]);
            if (delta > bestDelta) {
                bestDelta = delta;
                bestAxis = i;
            }
        }
        return bestAxis;
    }

    private int detectPressedButton() {
        final int max = Math.min(liveButtons.length, baselineButtons.length);
        for (int i = 0; i < max; i++) {
            if (liveButtons[i] != baselineButtons[i] && liveButtons[i] != 0) {
                return i;
            }
        }
        return -1;
    }

    private void moveToStep(final Step nextStep) {
        step = nextStep;
        if (step == Step.CENTER) {
            workingCalibration.cancelCalibration();
        } else if (step == Step.RANGE) {
            workingCalibration.startCalibration();
        }
        beginStepListening();
        refreshActionButton();
    }

    private void beginStepListening() {
        final FpvControllerInput.RawJoystickState rawState = FpvControllerInput.snapshotRawState();
        if (rawState == null) {
            baselineAxes = new float[0];
            baselineButtons = new byte[0];
            controllerName = "";
            return;
        }
        baselineAxes = rawState.axes().clone();
        baselineButtons = rawState.buttons().clone();
        liveAxes = rawState.axes();
        liveButtons = rawState.buttons();
        controllerName = rawState.name();
        workingCalibration.setControllerName(controllerName);
    }

    private void refreshActionButton() {
        if (actionButton == null) {
            return;
        }

        final boolean controllerPresent = liveAxes.length > 0 || FpvControllerInput.snapshotRawState() != null;
        if (step == Step.CENTER) {
            actionButton.setMessage(Component.translatable("screen.fullfud.calibration.record_center"));
            actionButton.active = controllerPresent;
        } else if (step == Step.RANGE) {
            actionButton.setMessage(Component.translatable("screen.fullfud.calibration.finish"));
            actionButton.active = controllerPresent;
        } else {
            actionButton.setMessage(Component.translatable("screen.fullfud.calibration.waiting"));
            actionButton.active = false;
        }
    }

    private void onAction() {
        if (step == Step.CENTER) {
            workingCalibration.recordCenter(
                logicalRaw(ControllerCalibration.AXIS_ROLL),
                logicalRaw(ControllerCalibration.AXIS_PITCH),
                logicalRaw(ControllerCalibration.AXIS_YAW),
                logicalRaw(ControllerCalibration.AXIS_THROTTLE)
            );
            moveToStep(Step.RANGE);
            return;
        }

        if (step == Step.RANGE) {
            workingCalibration.finishCalibration();
            targetCalibration.copyFrom(workingCalibration);
            ControllerCalibrationStore.save(targetCalibration);
            onClose();
        }
    }

    private float logicalRaw(final int logicalAxis) {
        return workingCalibration.readMappedAxis(liveAxes, logicalAxis);
    }

    private static float safeGet(final float[] values, final int index) {
        if (values == null || index < 0 || index >= values.length) {
            return 0.0F;
        }
        final float value = values[index];
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static String assignmentLabel(final int physicalAxis, final boolean inverted) {
        if (physicalAxis < 0) {
            return "-";
        }
        return "axis " + physicalAxis + (inverted ? " (inv)" : "");
    }

    private String armBindingLabel() {
        if (!workingCalibration.hasArmBinding()) {
            return "-";
        }
        if (workingCalibration.isArmBindingAxis()) {
            return "axis " + workingCalibration.getArmAxisIndex() + (workingCalibration.isArmInverted() ? " (inv)" : "");
        }
        return "button " + workingCalibration.getArmButtonIndex();
    }

    private Component currentInstruction() {
        return switch (step) {
            case THROTTLE -> Component.translatable("screen.fullfud.calibration.wait_throttle");
            case YAW -> Component.translatable("screen.fullfud.calibration.wait_yaw");
            case PITCH -> Component.translatable("screen.fullfud.calibration.wait_pitch");
            case ROLL -> Component.translatable("screen.fullfud.calibration.wait_roll");
            case ARM -> Component.translatable("screen.fullfud.calibration.wait_arm");
            case CENTER -> Component.translatable("screen.fullfud.calibration.instruction_center");
            case RANGE -> Component.translatable("screen.fullfud.calibration.instruction_extremes");
        };
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        final int cx = width / 2;
        int y = 24;

        graphics.drawCenteredString(font, title, cx, y, 0xFFFFFF);
        y += 16;

        if (controllerName.isBlank()) {
            graphics.drawCenteredString(font, Component.translatable("screen.fullfud.calibration.controller_missing"), cx, y, 0xFF5555);
        } else {
            graphics.drawCenteredString(font, controllerName, cx, y, 0xAAAAAA);
        }
        y += 20;

        graphics.drawCenteredString(font, currentInstruction(), cx, y, 0xDDDDDD);
        y += 24;

        final float[] values = {
            logicalRaw(ControllerCalibration.AXIS_ROLL),
            logicalRaw(ControllerCalibration.AXIS_PITCH),
            logicalRaw(ControllerCalibration.AXIS_YAW),
            logicalRaw(ControllerCalibration.AXIS_THROTTLE)
        };

        for (int i = 0; i < ControllerCalibration.AXIS_COUNT; i++) {
            final int barX = cx - BAR_WIDTH / 2;
            final int barY = y;

            graphics.drawString(font, ControllerCalibration.getAxisName(i), barX - 68, barY + 1, 0xCCCCCC);
            graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF333333);

            final float norm = (values[i] + 1.0F) * 0.5F;
            final int indicatorX = barX + (int) (norm * BAR_WIDTH);
            graphics.fill(indicatorX - 2, barY, indicatorX + 2, barY + BAR_HEIGHT, 0xFF00FF88);

            final int centerX = barX + BAR_WIDTH / 2;
            graphics.fill(centerX, barY, centerX + 1, barY + BAR_HEIGHT, 0xFF666666);

            if (step == Step.RANGE && workingCalibration.isSampling()) {
                final float sampleMin = (workingCalibration.getSampleMin(i) + 1.0F) * 0.5F;
                final float sampleMax = (workingCalibration.getSampleMax(i) + 1.0F) * 0.5F;
                final int minX = barX + (int) (sampleMin * BAR_WIDTH);
                final int maxX = barX + (int) (sampleMax * BAR_WIDTH);
                graphics.fill(minX, barY, minX + 1, barY + BAR_HEIGHT, 0xFFFF4444);
                graphics.fill(maxX, barY, maxX + 1, barY + BAR_HEIGHT, 0xFF4488FF);
            }

            graphics.drawString(font, String.format("%.2f", values[i]), barX + BAR_WIDTH + 8, barY + 1, 0x999999);
            y += BAR_HEIGHT + 12;
        }

        y += 8;
        graphics.drawCenteredString(font, "Throttle: " + assignmentLabel(
            workingCalibration.getAxisMapping(ControllerCalibration.AXIS_THROTTLE),
            workingCalibration.isAxisInverted(ControllerCalibration.AXIS_THROTTLE)
        ), cx, y, 0xBBBBBB);
        y += 10;
        graphics.drawCenteredString(font, "Yaw: " + assignmentLabel(
            workingCalibration.getAxisMapping(ControllerCalibration.AXIS_YAW),
            workingCalibration.isAxisInverted(ControllerCalibration.AXIS_YAW)
        ), cx, y, 0xBBBBBB);
        y += 10;
        graphics.drawCenteredString(font, "Pitch: " + assignmentLabel(
            workingCalibration.getAxisMapping(ControllerCalibration.AXIS_PITCH),
            workingCalibration.isAxisInverted(ControllerCalibration.AXIS_PITCH)
        ), cx, y, 0xBBBBBB);
        y += 10;
        graphics.drawCenteredString(font, "Roll: " + assignmentLabel(
            workingCalibration.getAxisMapping(ControllerCalibration.AXIS_ROLL),
            workingCalibration.isAxisInverted(ControllerCalibration.AXIS_ROLL)
        ), cx, y, 0xBBBBBB);
        y += 10;
        graphics.drawCenteredString(font, "Arm: " + armBindingLabel(), cx, y, 0xBBBBBB);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Step {
        THROTTLE,
        YAW,
        PITCH,
        ROLL,
        ARM,
        CENTER,
        RANGE
    }
}
