package com.fullfud.fullfud.client.input;

import com.fullfud.fullfud.core.config.FullfudClientConfig;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FpvControllerInput {
    public record State(
        boolean present,
        float pitch,
        float roll,
        float yaw,
        float throttle,
        boolean hasThrottle,
        boolean armClicked
    ) { }

    public record RawJoystickState(
        int jid,
        String name,
        float[] axes,
        byte[] buttons
    ) { }

    public record ConnectedController(
        int jid,
        String name,
        boolean gamepad,
        int axisCount,
        int buttonCount
    ) { }

    public record DebugState(
        boolean inputEnabled,
        int connectedControllers,
        int joystickId,
        String joystickName,
        String mode,
        String calibrationControllerName,
        boolean calibrationReady,
        boolean calibrationMatches,
        boolean armPressed,
        boolean armClicked,
        String armBinding,
        float pitch,
        float roll,
        float yaw,
        float throttle
    ) { }

    private static final GLFWGamepadState GAMEPAD_STATE = GLFWGamepadState.create();

    private static int lastJid = -1;
    private static boolean lastArmPressed = false;

    private static DebugState lastDebugState = new DebugState(
        false,
        0,
        -1,
        "",
        "off",
        "",
        false,
        false,
        false,
        false,
        "-",
        0.0F,
        0.0F,
        0.0F,
        0.0F
    );

    private FpvControllerInput() { }

    public static DebugState getLastDebugState() {
        return lastDebugState;
    }

    public static List<ConnectedController> listConnectedControllers() {
        final List<ConnectedController> controllers = new ArrayList<>();
        for (int i = 0; i <= 15; i++) {
            final int jid = GLFW.GLFW_JOYSTICK_1 + i;
            if (!GLFW.glfwJoystickPresent(jid)) {
                continue;
            }
            final FloatBuffer axes = GLFW.glfwGetJoystickAxes(jid);
            final ByteBuffer buttons = GLFW.glfwGetJoystickButtons(jid);
            controllers.add(new ConnectedController(
                jid,
                safeJoystickName(jid),
                GLFW.glfwJoystickIsGamepad(jid),
                axes == null ? 0 : axes.remaining(),
                buttons == null ? 0 : buttons.remaining()
            ));
        }
        return controllers;
    }

    public static State poll(final ControllerCalibration calibration) {
        final int connectedControllers = countConnectedJoysticks();
        final int jid = resolveJoystickId(calibration);
        final String controllerName = jid >= 0 ? safeJoystickName(jid) : "";
        final boolean calibrationReady = calibration != null && calibration.isReady();
        final boolean calibrationMatches = calibration != null && calibration.matchesController(controllerName);
        final boolean inputEnabled = isControllerInputEnabled(calibration);
        final String calibrationControllerName = calibration != null ? calibration.getControllerName() : "";
        final String armBinding = calibration != null ? describeArmBinding(calibration, jid) : "-";

        if (!inputEnabled) {
            lastJid = -1;
            lastArmPressed = false;
            lastDebugState = new DebugState(
                false,
                connectedControllers,
                jid,
                controllerName,
                "off",
                calibrationControllerName,
                calibrationReady,
                calibrationMatches,
                false,
                false,
                armBinding,
                0.0F,
                0.0F,
                0.0F,
                0.0F
            );
            return new State(false, 0.0F, 0.0F, 0.0F, 0.0F, false, false);
        }

        if (jid < 0) {
            lastJid = -1;
            lastArmPressed = false;
            lastDebugState = new DebugState(
                true,
                connectedControllers,
                -1,
                "",
                "none",
                calibrationControllerName,
                calibrationReady,
                false,
                false,
                false,
                armBinding,
                0.0F,
                0.0F,
                0.0F,
                0.0F
            );
            return new State(false, 0.0F, 0.0F, 0.0F, 0.0F, false, false);
        }
        lastJid = jid;

        final boolean allowSingleControllerCalibration = calibration != null
            && calibration.isReady()
            && connectedControllers == 1;
        if (calibration != null
            && calibration.isReady()
            && (calibration.isReadyForController(controllerName) || allowSingleControllerCalibration)) {
            return pollCalibratedRawJoystick(jid, calibration, connectedControllers);
        }

        final boolean preferGamepad = FullfudClientConfig.CLIENT.fpvControllerPreferGamepadMapping.get();
        if (preferGamepad && GLFW.glfwJoystickIsGamepad(jid) && GLFW.glfwGetGamepadState(jid, GAMEPAD_STATE)) {
            return pollGamepad(jid, GAMEPAD_STATE, connectedControllers);
        }
        return pollRawJoystick(jid, connectedControllers);
    }

    public static int findJoystickId() {
        return resolveJoystickId(null);
    }

    public static int findJoystickId(final ControllerCalibration calibration) {
        return resolveJoystickId(calibration);
    }

    public static RawJoystickState snapshotRawState() {
        return snapshotRawState((ControllerCalibration) null);
    }

    public static RawJoystickState snapshotRawState(final ControllerCalibration calibration) {
        final int jid = resolveJoystickId(calibration);
        return snapshotRawState(jid);
    }

    public static RawJoystickState snapshotRawState(final int jid) {
        if (jid < 0 || !GLFW.glfwJoystickPresent(jid)) {
            return null;
        }

        final FloatBuffer axes = GLFW.glfwGetJoystickAxes(jid);
        if (axes == null) {
            return null;
        }

        final float[] axisValues = new float[axes.remaining()];
        axes.duplicate().get(axisValues);

        final ByteBuffer buttons = GLFW.glfwGetJoystickButtons(jid);
        final byte[] buttonValues;
        if (buttons == null) {
            buttonValues = new byte[0];
        } else {
            buttonValues = new byte[buttons.remaining()];
            buttons.duplicate().get(buttonValues);
        }

        return new RawJoystickState(jid, safeJoystickName(jid), axisValues, buttonValues);
    }

    public static String formatChannelLabel(final int channelIndex) {
        if (channelIndex < 0) {
            return "-";
        }
        return "CH " + (channelIndex + 1);
    }

    public static String describeArmBinding(final ControllerCalibration calibration, final int jid) {
        if (calibration == null || !calibration.hasArmBinding()) {
            return "-";
        }
        final int axisCount = axisCountFor(jid, calibration);
        if (calibration.isArmBindingAxis()) {
            return formatChannelLabel(calibration.getArmAxisIndex());
        }
        return formatChannelLabel(axisCount + calibration.getArmButtonIndex());
    }

    private static int resolveJoystickId(final ControllerCalibration calibration) {
        if (calibration != null && calibration.getControllerName() != null && !calibration.getControllerName().isBlank()) {
            final int byName = findJoystickByName(calibration.getControllerName());
            if (byName >= 0) {
                return byName;
            }
        }

        final int forced = FullfudClientConfig.CLIENT.fpvControllerJoystickId.get();
        if (forced >= 0) {
            final int jid = GLFW.GLFW_JOYSTICK_1 + forced;
            if (GLFW.glfwJoystickPresent(jid)) {
                return jid;
            }
        }

        for (int i = 0; i <= 15; i++) {
            final int jid = GLFW.GLFW_JOYSTICK_1 + i;
            if (GLFW.glfwJoystickPresent(jid)) {
                return jid;
            }
        }
        return -1;
    }

    private static int findJoystickByName(final String expectedName) {
        if (expectedName == null || expectedName.isBlank()) {
            return -1;
        }
        final String normalizedExpected = normalizeControllerName(expectedName);
        for (int i = 0; i <= 15; i++) {
            final int jid = GLFW.GLFW_JOYSTICK_1 + i;
            if (!GLFW.glfwJoystickPresent(jid)) {
                continue;
            }
            final String actual = safeJoystickName(jid);
            if (expectedName.equalsIgnoreCase(actual)) {
                return jid;
            }
            if (!normalizedExpected.isEmpty() && normalizedExpected.equals(normalizeControllerName(actual))) {
                return jid;
            }
        }
        return -1;
    }

    private static int countConnectedJoysticks() {
        int count = 0;
        for (int i = 0; i <= 15; i++) {
            final int jid = GLFW.GLFW_JOYSTICK_1 + i;
            if (GLFW.glfwJoystickPresent(jid)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isControllerInputEnabled(final ControllerCalibration calibration) {
        return FullfudClientConfig.CLIENT.fpvControllerEnabled.get()
            || (calibration != null && calibration.isReady());
    }

    private static State pollCalibratedRawJoystick(
        final int jid,
        final ControllerCalibration calibration,
        final int connectedControllers
    ) {
        final RawJoystickState rawState = snapshotRawState(jid);
        if (rawState == null) {
            lastArmPressed = false;
            lastDebugState = new DebugState(
                true,
                connectedControllers,
                jid,
                safeJoystickName(jid),
                "calibrated-missing",
                calibration != null ? calibration.getControllerName() : "",
                calibration != null && calibration.isReady(),
                calibration != null && calibration.matchesController(safeJoystickName(jid)),
                false,
                false,
                describeArmBinding(calibration, jid),
                0.0F,
                0.0F,
                0.0F,
                0.0F
            );
            return new State(false, 0.0F, 0.0F, 0.0F, 0.0F, false, false);
        }

        final float deadzone = configuredDeadzone();
        final float pitch = applyDeadzone(
            calibration.normalizeMappedAxis(rawState.axes(), ControllerCalibration.AXIS_PITCH),
            deadzone
        );
        final float roll = applyDeadzone(
            calibration.normalizeMappedAxis(rawState.axes(), ControllerCalibration.AXIS_ROLL),
            deadzone
        );
        final float yaw = applyDeadzone(
            calibration.normalizeMappedAxis(rawState.axes(), ControllerCalibration.AXIS_YAW),
            deadzone
        );
        final float throttle = Mth.clamp(calibration.normalizeMappedThrottle(rawState.axes()), 0.0F, 1.0F);

        final boolean armPressed = readArmBindingPressed(calibration, rawState);
        final boolean armClicked = armPressed && !lastArmPressed;
        lastArmPressed = armPressed;

        lastDebugState = new DebugState(
            true,
            connectedControllers,
            jid,
            rawState.name(),
            "calibrated",
            calibration != null ? calibration.getControllerName() : "",
            calibration != null && calibration.isReady(),
            calibration != null && calibration.matchesController(rawState.name()),
            armPressed,
            armClicked,
            describeArmBinding(calibration, jid),
            pitch,
            roll,
            yaw,
            throttle
        );

        return new State(true, pitch, roll, yaw, throttle, true, armClicked);
    }

    private static State pollGamepad(final int jid, final GLFWGamepadState state, final int connectedControllers) {
        final float deadzone = configuredDeadzone();

        final float rightX = applyDeadzone(state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X), deadzone);
        final float rightY = applyDeadzone(state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y), deadzone);
        final float leftX = applyDeadzone(state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X), deadzone);
        final float leftY = applyDeadzone(state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y), deadzone);

        final float pitch = invert(rightY, FullfudClientConfig.CLIENT.fpvControllerInvertPitch.get());
        final float roll = invert(-rightX, FullfudClientConfig.CLIENT.fpvControllerInvertRoll.get());
        final float yaw = invert(leftX, FullfudClientConfig.CLIENT.fpvControllerInvertYaw.get());

        final float throttleTarget;
        if (FullfudClientConfig.CLIENT.fpvControllerGamepadThrottleMode.get()
            == FullfudClientConfig.GamepadThrottleMode.RIGHT_TRIGGER) {
            throttleTarget = Mth.clamp(state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER), 0.0F, 1.0F);
        } else {
            final float axis = invert(leftY, FullfudClientConfig.CLIENT.fpvControllerInvertThrottle.get());
            throttleTarget = axisToThrottle01(axis);
        }

        final boolean armPressed = state.buttons(GLFW.GLFW_GAMEPAD_BUTTON_A) == GLFW.GLFW_PRESS;
        final boolean armClicked = armPressed && !lastArmPressed;
        lastArmPressed = armPressed;

        lastDebugState = new DebugState(
            true,
            connectedControllers,
            jid,
            safeJoystickName(jid),
            "gamepad",
            "",
            false,
            false,
            armPressed,
            armClicked,
            "button A",
            pitch,
            roll,
            yaw,
            throttleTarget
        );

        return new State(true, pitch, roll, yaw, throttleTarget, true, armClicked);
    }

    private static State pollRawJoystick(final int jid, final int connectedControllers) {
        final FloatBuffer axes = GLFW.glfwGetJoystickAxes(jid);
        final ByteBuffer buttons = GLFW.glfwGetJoystickButtons(jid);
        if (axes == null) {
            lastArmPressed = false;
            lastDebugState = new DebugState(
                true,
                connectedControllers,
                jid,
                safeJoystickName(jid),
                "raw-missing",
                "",
                false,
                false,
                false,
                false,
                "button " + FullfudClientConfig.CLIENT.fpvControllerArmButton.get(),
                0.0F,
                0.0F,
                0.0F,
                0.0F
            );
            return new State(false, 0.0F, 0.0F, 0.0F, 0.0F, false, false);
        }

        final float deadzone = configuredDeadzone();
        final int rollAxis = FullfudClientConfig.CLIENT.fpvControllerRawRollAxis.get();
        final int pitchAxis = FullfudClientConfig.CLIENT.fpvControllerRawPitchAxis.get();
        final int yawAxis = FullfudClientConfig.CLIENT.fpvControllerRawYawAxis.get();
        final int throttleAxis = FullfudClientConfig.CLIENT.fpvControllerRawThrottleAxis.get();

        float roll = -getAxis(axes, rollAxis);
        float pitch = getAxis(axes, pitchAxis);
        float yaw = getAxis(axes, yawAxis);
        float throttleAxisValue = getAxis(axes, throttleAxis);

        roll = applyDeadzone(roll, deadzone);
        pitch = applyDeadzone(pitch, deadzone);
        yaw = applyDeadzone(yaw, deadzone);

        pitch = invert(pitch, FullfudClientConfig.CLIENT.fpvControllerInvertPitch.get());
        roll = invert(roll, FullfudClientConfig.CLIENT.fpvControllerInvertRoll.get());
        yaw = invert(yaw, FullfudClientConfig.CLIENT.fpvControllerInvertYaw.get());

        throttleAxisValue = invert(throttleAxisValue, FullfudClientConfig.CLIENT.fpvControllerInvertThrottle.get());
        final float throttle = axisToThrottle01(throttleAxisValue);

        boolean armPressed = false;
        if (buttons != null) {
            final int armButton = FullfudClientConfig.CLIENT.fpvControllerArmButton.get();
            if (armButton >= 0 && armButton < buttons.limit()) {
                armPressed = buttons.get(armButton) == GLFW.GLFW_PRESS;
            }
        }
        final boolean armClicked = armPressed && !lastArmPressed;
        lastArmPressed = armPressed;

        lastDebugState = new DebugState(
            true,
            connectedControllers,
            jid,
            safeJoystickName(jid),
            "raw",
            "",
            false,
            false,
            armPressed,
            armClicked,
            "button " + FullfudClientConfig.CLIENT.fpvControllerArmButton.get(),
            pitch,
            roll,
            yaw,
            throttle
        );

        return new State(true, pitch, roll, yaw, throttle, true, armClicked);
    }

    private static boolean readArmBindingPressed(final ControllerCalibration calibration, final RawJoystickState rawState) {
        if (calibration == null || rawState == null || !calibration.hasArmBinding()) {
            return false;
        }

        if (!calibration.isArmBindingAxis()) {
            final int buttonIndex = calibration.getArmButtonIndex();
            return buttonIndex >= 0
                && buttonIndex < rawState.buttons().length
                && rawState.buttons()[buttonIndex] == GLFW.GLFW_PRESS;
        }

        final int axisIndex = calibration.getArmAxisIndex();
        if (axisIndex < 0 || axisIndex >= rawState.axes().length) {
            return false;
        }
        float value = rawState.axes()[axisIndex];
        if (calibration.isArmInverted()) {
            value = -value;
        }
        return value > 0.1F;
    }

    private static int axisCountFor(final int jid, final ControllerCalibration calibration) {
        if (jid >= 0 && GLFW.glfwJoystickPresent(jid)) {
            final FloatBuffer axes = GLFW.glfwGetJoystickAxes(jid);
            if (axes != null) {
                return axes.remaining();
            }
        }
        return calibration == null ? 0 : calibration.getRangeAxisCount();
    }

    private static float configuredDeadzone() {
        return (float) Mth.clamp(FullfudClientConfig.CLIENT.fpvControllerDeadzone.get(), 0.0D, 0.5D);
    }

    private static String safeJoystickName(final int jid) {
        final String name = GLFW.glfwGetJoystickName(jid);
        return name == null ? "" : name;
    }

    private static float getAxis(final FloatBuffer axes, final int index) {
        if (axes == null || index < 0 || index >= axes.limit()) {
            return 0.0F;
        }
        final float value = axes.get(index);
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static float invert(final float value, final boolean invert) {
        return invert ? -value : value;
    }

    private static float applyDeadzone(final float value, final float deadzone) {
        final float abs = Math.abs(value);
        if (abs <= deadzone) {
            return 0.0F;
        }
        final float scaled = (abs - deadzone) / (1.0F - deadzone);
        return Math.copySign(scaled, value);
    }

    private static float axisToThrottle01(final float axisMinus1To1) {
        return Mth.clamp((axisMinus1To1 + 1.0F) * 0.5F, 0.0F, 1.0F);
    }

    private static String normalizeControllerName(final String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]+", "");
    }
}
