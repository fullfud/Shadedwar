package com.fullfud.fullfud.client.screen;

import com.fullfud.fullfud.client.input.ControllerCalibration;
import com.fullfud.fullfud.client.input.FpvControllerInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ControllerStickWizardScreen extends Screen {
    private static final float ASSIGN_THRESHOLD = 0.3F;
    private static final int BAR_WIDTH = 144;
    private static final int BAR_HEIGHT = 8;

    private final ControllerCalibrationScreen parentScreen;
    private final ControllerCalibration calibration;

    private Button backButton;
    private Button proceedButton;
    private Button retryButton;

    private float[] baselineAxes = new float[0];
    private float[] liveAxes = new float[0];
    private String liveControllerName = "";
    private Step step = Step.BEGIN;
    private boolean showRecenter;

    public ControllerStickWizardScreen(
        final ControllerCalibrationScreen parentScreen,
        final ControllerCalibration calibration
    ) {
        super(Component.translatable("screen.fullfud.calibration.wizard.sticks.title"));
        this.parentScreen = parentScreen;
        this.calibration = calibration;
    }

    @Override
    protected void init() {
        final int bottom = height - 28;
        backButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.fullfud.calibration.cancel"),
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
                button -> onRetry()
            )
            .bounds(width / 2 - 50, bottom - 24, 100, 20)
            .build());

        refreshLiveState();
        refreshButtons();
    }

    @Override
    public void tick() {
        super.tick();
        refreshLiveState();

        if (step == Step.RANGE && calibration.isSampling()) {
            calibration.sampleAxes(liveAxes);
        } else if (isListeningForAxis()) {
            detectAxisAssignment();
        }

        refreshButtons();
    }

    private void refreshLiveState() {
        final FpvControllerInput.RawJoystickState rawState = FpvControllerInput.snapshotRawState(calibration);
        if (rawState == null) {
            liveAxes = new float[0];
            liveControllerName = "";
            return;
        }

        liveAxes = rawState.axes();
        liveControllerName = rawState.name();
        if (calibration.getControllerName().isBlank()) {
            calibration.setControllerName(liveControllerName);
            parentScreen.persistWorkingCalibration();
        }
    }

    private void onProceed() {
        switch (step) {
            case BEGIN -> {
                step = Step.LEFT_UP;
                showRecenter = false;
                startListening();
            }
            case LEFT_UP, LEFT_RIGHT, RIGHT_UP, RIGHT_RIGHT -> {
                if (!showRecenter) {
                    return;
                }
                showRecenter = false;
                final Step next = nextStep(step);
                if (next == Step.RANGE) {
                    step = Step.RANGE;
                    startRangeSampling();
                } else {
                    step = next;
                    startListening();
                }
            }
            case RANGE -> {
                if (!calibration.isSampling()) {
                    startRangeSampling();
                    return;
                }
                calibration.finishRangeSampling();
                parentScreen.persistWorkingCalibration();
                step = Step.VERIFY;
            }
            case VERIFY -> {
                parentScreen.persistWorkingCalibration();
                if (minecraft != null) {
                    minecraft.setScreen(new ControllerArmWizardScreen(parentScreen, calibration));
                }
            }
        }
    }

    private void onBack() {
        if (calibration.isSampling()) {
            calibration.cancelRangeSampling();
        }

        switch (step) {
            case BEGIN -> closeToParent();
            case LEFT_UP -> {
                if (showRecenter) {
                    showRecenter = false;
                    startListening();
                } else {
                    closeToParent();
                }
            }
            case LEFT_RIGHT -> {
                if (showRecenter) {
                    showRecenter = false;
                    startListening();
                } else {
                    step = Step.LEFT_UP;
                    showRecenter = true;
                }
            }
            case RIGHT_UP -> {
                if (showRecenter) {
                    showRecenter = false;
                    startListening();
                } else {
                    step = Step.LEFT_RIGHT;
                    showRecenter = true;
                }
            }
            case RIGHT_RIGHT -> {
                if (showRecenter) {
                    showRecenter = false;
                    startListening();
                } else {
                    step = Step.RIGHT_UP;
                    showRecenter = true;
                }
            }
            case RANGE -> {
                step = Step.RIGHT_RIGHT;
                showRecenter = true;
            }
            case VERIFY -> {
                step = Step.RANGE;
                startRangeSampling();
            }
        }
    }

    private void onRetry() {
        if (calibration.isSampling()) {
            calibration.cancelRangeSampling();
        }
        step = Step.BEGIN;
        showRecenter = false;
        refreshButtons();
    }

    private void closeToParent() {
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }

    private boolean isListeningForAxis() {
        return !showRecenter && switch (step) {
            case LEFT_UP, LEFT_RIGHT, RIGHT_UP, RIGHT_RIGHT -> true;
            default -> false;
        };
    }

    private void startListening() {
        baselineAxes = liveAxes.clone();
    }

    private void startRangeSampling() {
        calibration.startRangeSampling(liveAxes.length, liveAxes);
        parentScreen.persistWorkingCalibration();
    }

    private void detectAxisAssignment() {
        final int axisIndex = detectMovedAxis();
        if (axisIndex < 0) {
            return;
        }
        final float delta = deltaForAxis(axisIndex);
        final int logicalAxis = logicalAxisForStep(step);
        if (logicalAxis < 0) {
            return;
        }

        calibration.setAxisMapping(logicalAxis, axisIndex);
        calibration.setAxisInverted(logicalAxis, delta < 0.0F);
        parentScreen.persistWorkingCalibration();
        showRecenter = true;
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

    private float deltaForAxis(final int axisIndex) {
        final float current = axisIndex >= 0 && axisIndex < liveAxes.length && Float.isFinite(liveAxes[axisIndex]) ? liveAxes[axisIndex] : 0.0F;
        final float baseline = axisIndex >= 0 && axisIndex < baselineAxes.length && Float.isFinite(baselineAxes[axisIndex]) ? baselineAxes[axisIndex] : 0.0F;
        return current - baseline;
    }

    private void refreshButtons() {
        if (proceedButton == null) {
            return;
        }

        proceedButton.setMessage(proceedLabel());
        proceedButton.active = canProceed();
        retryButton.visible = step == Step.VERIFY;
        backButton.setMessage(Component.translatable(
            step == Step.BEGIN
                ? "screen.fullfud.calibration.cancel"
                : "gui.back"
        ));
    }

    private Component proceedLabel() {
        if (showRecenter) {
            return Component.translatable("gui.continue");
        }
        return switch (step) {
            case BEGIN -> Component.translatable("screen.fullfud.calibration.wizard.start");
            case RANGE -> Component.translatable(
                calibration.isSampling()
                    ? "screen.fullfud.calibration.range.finish"
                    : "screen.fullfud.calibration.range.start"
            );
            case VERIFY -> Component.translatable("gui.proceed");
            default -> Component.translatable("screen.fullfud.calibration.waiting");
        };
    }

    private boolean canProceed() {
        if (showRecenter) {
            return true;
        }
        return switch (step) {
            case BEGIN -> hasControllerInput();
            case RANGE -> hasControllerInput() && calibration.hasCompleteAxisMapping();
            case VERIFY -> calibration.hasCompleteAxisMapping();
            default -> false;
        };
    }

    private boolean hasControllerInput() {
        return liveAxes.length > 0 && !calibration.getControllerName().isBlank();
    }

    private Component currentTitle() {
        if (showRecenter) {
            return Component.translatable("screen.fullfud.calibration.wizard.recenter");
        }
        return switch (step) {
            case BEGIN -> Component.translatable("screen.fullfud.calibration.wizard.sticks.begin");
            case LEFT_UP -> Component.translatable("screen.fullfud.calibration.wait_throttle");
            case LEFT_RIGHT -> Component.translatable("screen.fullfud.calibration.wait_yaw");
            case RIGHT_UP -> Component.translatable("screen.fullfud.calibration.wait_pitch");
            case RIGHT_RIGHT -> Component.translatable("screen.fullfud.calibration.wait_roll");
            case RANGE -> Component.translatable("screen.fullfud.calibration.range.instruction");
            case VERIFY -> Component.translatable("screen.fullfud.calibration.wizard.sticks.verify");
        };
    }

    private Component currentSubtitle() {
        return switch (step) {
            case BEGIN -> Component.translatable("screen.fullfud.calibration.instruction_main");
            case LEFT_UP -> Component.translatable("screen.fullfud.calibration.wizard.sticks.left_up");
            case LEFT_RIGHT -> Component.translatable("screen.fullfud.calibration.wizard.sticks.left_right");
            case RIGHT_UP -> Component.translatable("screen.fullfud.calibration.wizard.sticks.right_up");
            case RIGHT_RIGHT -> Component.translatable("screen.fullfud.calibration.wizard.sticks.right_right");
            case RANGE -> Component.translatable("screen.fullfud.calibration.wizard.sticks.range");
            case VERIFY -> Component.translatable("screen.fullfud.calibration.wizard.sticks.verify_hint");
        };
    }

    private static Step nextStep(final Step current) {
        return switch (current) {
            case LEFT_UP -> Step.LEFT_RIGHT;
            case LEFT_RIGHT -> Step.RIGHT_UP;
            case RIGHT_UP -> Step.RIGHT_RIGHT;
            default -> Step.RANGE;
        };
    }

    private static int logicalAxisForStep(final Step current) {
        return switch (current) {
            case LEFT_UP -> ControllerCalibration.AXIS_THROTTLE;
            case LEFT_RIGHT -> ControllerCalibration.AXIS_YAW;
            case RIGHT_UP -> ControllerCalibration.AXIS_PITCH;
            case RIGHT_RIGHT -> ControllerCalibration.AXIS_ROLL;
            default -> -1;
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
            64,
            0x88C0FF
        );

        renderChannelRow(graphics, 94, ControllerCalibration.AXIS_THROTTLE);
        renderChannelRow(graphics, 122, ControllerCalibration.AXIS_YAW);
        renderChannelRow(graphics, 150, ControllerCalibration.AXIS_PITCH);
        renderChannelRow(graphics, 178, ControllerCalibration.AXIS_ROLL);

        if (step == Step.RANGE || step == Step.VERIFY) {
            graphics.drawCenteredString(
                font,
                Component.translatable(
                    "screen.fullfud.calibration.debug.range_state",
                    calibration.getRangeAxisCount()
                ),
                cx,
                208,
                0xBBBBBB
            );
        }
    }

    private void renderChannelRow(final GuiGraphics graphics, final int y, final int logicalAxis) {
        final int left = width / 2 - 150;
        final int barX = left + 92;
        final boolean highlighted = logicalAxis == logicalAxisForStep(step) && !showRecenter;

        graphics.drawString(
            font,
            rowName(logicalAxis),
            left,
            y + 1,
            highlighted ? 0xFFFF88 : 0xFFFFFF
        );
        renderAxisPreview(graphics, barX, y, logicalAxis);
        graphics.drawString(font, describeAssignment(logicalAxis), barX + BAR_WIDTH + 10, y + 1, 0xAAAAAA);
    }

    private void renderAxisPreview(final GuiGraphics graphics, final int x, final int y, final int logicalAxis) {
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF333333);

        final int physicalAxis = calibration.getAxisMapping(logicalAxis);
        if (physicalAxis < 0) {
            return;
        }

        float value;
        if (calibration.hasRangeForAxis(physicalAxis)) {
            value = calibration.normalizeMappedAxis(liveAxes, logicalAxis);
        } else {
            value = calibration.readMappedAxis(liveAxes, logicalAxis);
            if (calibration.isAxisInverted(logicalAxis)) {
                value = -value;
            }
            value = Mth.clamp(value, -1.0F, 1.0F);
        }

        final int centerX = x + BAR_WIDTH / 2;
        graphics.fill(centerX, y, centerX + 1, y + BAR_HEIGHT, 0xFF666666);

        final int indicatorX = x + (int) (((value + 1.0F) * 0.5F) * BAR_WIDTH);
        graphics.fill(indicatorX - 1, y, indicatorX + 1, y + BAR_HEIGHT, 0xFF00FF88);

        if (step == Step.RANGE && calibration.isSampling()) {
            final float min = rangeToBar(calibration.getSampleMin(physicalAxis));
            final float max = rangeToBar(calibration.getSampleMax(physicalAxis));
            graphics.fill(x + (int) min, y, x + (int) min + 1, y + BAR_HEIGHT, 0xFFFF5555);
            graphics.fill(x + (int) max, y, x + (int) max + 1, y + BAR_HEIGHT, 0xFF5599FF);
        }
    }

    private String describeAssignment(final int logicalAxis) {
        final int mapping = calibration.getAxisMapping(logicalAxis);
        if (mapping < 0) {
            return "-";
        }
        final String suffix = calibration.isAxisInverted(logicalAxis)
            ? " " + Component.translatable("screen.fullfud.calibration.binding.inverted").getString()
            : "";
        return FpvControllerInput.formatChannelLabel(mapping) + suffix;
    }

    private float rangeToBar(final float raw) {
        return Mth.clamp((raw + 1.0F) * 0.5F, 0.0F, 1.0F) * BAR_WIDTH;
    }

    private static Component rowName(final int logicalAxis) {
        return switch (logicalAxis) {
            case ControllerCalibration.AXIS_ROLL -> Component.translatable("screen.fullfud.calibration.axis.roll");
            case ControllerCalibration.AXIS_PITCH -> Component.translatable("screen.fullfud.calibration.axis.pitch");
            case ControllerCalibration.AXIS_YAW -> Component.translatable("screen.fullfud.calibration.axis.yaw");
            case ControllerCalibration.AXIS_THROTTLE -> Component.translatable("screen.fullfud.calibration.axis.throttle");
            default -> Component.translatable("screen.fullfud.calibration.axis.unknown");
        };
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
        LEFT_UP,
        LEFT_RIGHT,
        RIGHT_UP,
        RIGHT_RIGHT,
        RANGE,
        VERIFY
    }
}
