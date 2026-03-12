package com.fullfud.fullfud.client.input;

import com.fullfud.fullfud.core.config.FullfudClientConfig;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

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

    private static int lastJid = -1;
    private static boolean lastArmPressed = false;

    private static final GLFWGamepadState GAMEPAD_STATE = GLFWGamepadState.create();

    private FpvControllerInput() { }

    public static State poll(final ControllerCalibration calibration) {
        if (!FullfudClientConfig.CLIENT.fpvControllerEnabled.get()) {
            lastJid = -1;
            lastArmPressed = false;
            return new State(false, 0, 0, 0, 0, false, false);
        }

        final int jid = resolveJoystickId();
        if (jid < 0) {
            lastJid = -1;
            lastArmPressed = false;
            return new State(false, 0, 0, 0, 0, false, false);
        }
        lastJid = jid;

        final String controllerName = safeJoystickName(jid);
        if (calibration != null && calibration.isReadyForController(controllerName)) {
            return pollCalibratedRawJoystick(jid, calibration);
        }

        final boolean preferGamepad = FullfudClientConfig.CLIENT.fpvControllerPreferGamepadMapping.get();
        if (preferGamepad && GLFW.glfwJoystickIsGamepad(jid) && GLFW.glfwGetGamepadState(jid, GAMEPAD_STATE)) {
            return pollGamepad(jid, GAMEPAD_STATE);
        }
        return pollRawJoystick(jid);
    }

    public static int findJoystickId() {
        return resolveJoystickId();
    }

    public static RawJoystickState snapshotRawState() {
        final int jid = resolveJoystickId();
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

    private static int resolveJoystickId() {
        final int forced = FullfudClientConfig.CLIENT.fpvControllerJoystickId.get();
        if (forced >= 0) {
            final int jid = GLFW.GLFW_JOYSTICK_1 + forced;
            return GLFW.glfwJoystickPresent(jid) ? jid : -1;
        }
        for (int i = 0; i <= 15; i++) {
            final int jid = GLFW.GLFW_JOYSTICK_1 + i;
            if (GLFW.glfwJoystickPresent(jid)) {
                return jid;
            }
        }
        return -1;
    }

    private static State pollCalibratedRawJoystick(final int jid, final ControllerCalibration calibration) {
        final RawJoystickState rawState = snapshotRawState(jid);
        if (rawState == null) {
            lastArmPressed = false;
            return new State(false, 0, 0, 0, 0, false, false);
        }

        final float dz = configuredDeadzone();

        final float pitch = applyDeadzone(
            calibration.normalizeMappedAxis(rawState.axes(), ControllerCalibration.AXIS_PITCH),
            dz
        );
        final float roll = applyDeadzone(
            calibration.normalizeMappedAxis(rawState.axes(), ControllerCalibration.AXIS_ROLL),
            dz
        );
        final float yaw = applyDeadzone(
            calibration.normalizeMappedAxis(rawState.axes(), ControllerCalibration.AXIS_YAW),
            dz
        );
        final float throttle = Mth.clamp(calibration.normalizeMappedThrottle(rawState.axes()), 0.0F, 1.0F);

        final boolean armPressed = readArmBindingPressed(calibration, rawState);
        final boolean armClicked = armPressed && !lastArmPressed;
        lastArmPressed = armPressed;

        return new State(true, pitch, roll, yaw, throttle, true, armClicked);
    }

    private static State pollGamepad(final int jid, final GLFWGamepadState state) {
        final float dz = configuredDeadzone();

        final float rightX = applyDeadzone(state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X), dz);
        final float rightY = applyDeadzone(state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y), dz);
        final float leftX = applyDeadzone(state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X), dz);
        final float leftY = applyDeadzone(state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y), dz);

        final float pitch = invert(rightY, FullfudClientConfig.CLIENT.fpvControllerInvertPitch.get());
        final float roll = invert(rightX, FullfudClientConfig.CLIENT.fpvControllerInvertRoll.get());
        final float yaw = invert(-leftX, FullfudClientConfig.CLIENT.fpvControllerInvertYaw.get());

        final float throttleTarget;
        final boolean hasThrottle = true;
        if (FullfudClientConfig.CLIENT.fpvControllerGamepadThrottleMode.get() == FullfudClientConfig.GamepadThrottleMode.RIGHT_TRIGGER) {
            throttleTarget = Mth.clamp(state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER), 0.0F, 1.0F);
        } else {
            final float tAxis = invert(leftY, FullfudClientConfig.CLIENT.fpvControllerInvertThrottle.get());
            throttleTarget = axisToThrottle01(tAxis);
        }

        final boolean armPressed = state.buttons(GLFW.GLFW_GAMEPAD_BUTTON_A) == GLFW.GLFW_PRESS;
        final boolean armClicked = armPressed && !lastArmPressed;
        lastArmPressed = armPressed;

        return new State(true, pitch, roll, yaw, throttleTarget, hasThrottle, armClicked);
    }

    private static State pollRawJoystick(final int jid) {
        final FloatBuffer axes = GLFW.glfwGetJoystickAxes(jid);
        final ByteBuffer buttons = GLFW.glfwGetJoystickButtons(jid);
        if (axes == null) {
            lastArmPressed = false;
            return new State(false, 0, 0, 0, 0, false, false);
        }

        final float dz = configuredDeadzone();

        final int rollAxis = FullfudClientConfig.CLIENT.fpvControllerRawRollAxis.get();
        final int pitchAxis = FullfudClientConfig.CLIENT.fpvControllerRawPitchAxis.get();
        final int yawAxis = FullfudClientConfig.CLIENT.fpvControllerRawYawAxis.get();
        final int throttleAxis = FullfudClientConfig.CLIENT.fpvControllerRawThrottleAxis.get();

        float roll = getAxis(axes, rollAxis);
        float pitch = getAxis(axes, pitchAxis);
        float yaw = getAxis(axes, yawAxis);
        float throttleAxisValue = getAxis(axes, throttleAxis);

        roll = applyDeadzone(roll, dz);
        pitch = applyDeadzone(pitch, dz);
        yaw = applyDeadzone(yaw, dz);

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
        return value > 0.5F;
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
}
