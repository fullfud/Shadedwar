package com.fullfud.fullfud.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class FullfudClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Client CLIENT;

    static {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(builder);
        SPEC = builder.build();
    }

    private FullfudClientConfig() { }

    public enum GamepadThrottleMode {
        LEFT_STICK_Y,
        RIGHT_TRIGGER
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue fpvControllerEnabled;
        public final ForgeConfigSpec.BooleanValue fpvControllerPreferGamepadMapping;
        public final ForgeConfigSpec.IntValue fpvControllerJoystickId;

        public final ForgeConfigSpec.DoubleValue fpvControllerDeadzone;
        public final ForgeConfigSpec.DoubleValue fpvControllerThrottleSlew;

        public final ForgeConfigSpec.EnumValue<GamepadThrottleMode> fpvControllerGamepadThrottleMode;

        public final ForgeConfigSpec.IntValue fpvControllerRawRollAxis;
        public final ForgeConfigSpec.IntValue fpvControllerRawPitchAxis;
        public final ForgeConfigSpec.IntValue fpvControllerRawYawAxis;
        public final ForgeConfigSpec.IntValue fpvControllerRawThrottleAxis;

        public final ForgeConfigSpec.BooleanValue fpvControllerInvertRoll;
        public final ForgeConfigSpec.BooleanValue fpvControllerInvertPitch;
        public final ForgeConfigSpec.BooleanValue fpvControllerInvertYaw;
        public final ForgeConfigSpec.BooleanValue fpvControllerInvertThrottle;

        public final ForgeConfigSpec.IntValue fpvControllerArmButton;

        private Client(final ForgeConfigSpec.Builder builder) {
            builder.push("fpv");
            builder.push("controller");

            fpvControllerEnabled = builder
                .comment("Enable joystick/gamepad input for FPV drone control.")
                .define("enabled", true);

            fpvControllerPreferGamepadMapping = builder
                .comment("If the device is recognized as a GLFW 'gamepad', use standard gamepad mapping; otherwise use raw joystick axes mapping.")
                .define("preferGamepadMapping", true);

            fpvControllerJoystickId = builder
                .comment("GLFW joystick id (0..15). Use -1 for auto-detect.")
                .defineInRange("joystickId", -1, -1, 15);

            fpvControllerDeadzone = builder
                .comment("Deadzone for pitch/roll/yaw (0..0.5).")
                .defineInRange("deadzone", 0.08D, 0.0D, 0.5D);

            fpvControllerThrottleSlew = builder
                .comment("Throttle smoothing per tick (0 = instant, 1 = very slow).")
                .defineInRange("throttleSlew", 0.25D, 0.0D, 1.0D);

            fpvControllerGamepadThrottleMode = builder
                .comment("Gamepad throttle source. LEFT_STICK_Y mimics FPV Mode 2; RIGHT_TRIGGER mimics common game controls.")
                .defineEnum("gamepadThrottleMode", GamepadThrottleMode.LEFT_STICK_Y);

            builder.pop();
            builder.push("rawMapping");

            fpvControllerRawRollAxis = builder
                .comment("Raw joystick axis index for roll.")
                .defineInRange("rollAxis", 0, 0, 31);

            fpvControllerRawPitchAxis = builder
                .comment("Raw joystick axis index for pitch.")
                .defineInRange("pitchAxis", 1, 0, 31);

            fpvControllerRawYawAxis = builder
                .comment("Raw joystick axis index for yaw.")
                .defineInRange("yawAxis", 3, 0, 31);

            fpvControllerRawThrottleAxis = builder
                .comment("Raw joystick axis index for throttle.")
                .defineInRange("throttleAxis", 2, 0, 31);

            fpvControllerInvertRoll = builder
                .comment("Invert roll axis.")
                .define("invertRoll", false);

            fpvControllerInvertPitch = builder
                .comment("Invert pitch axis (typical when pushing stick up gives negative values).")
                .define("invertPitch", true);

            fpvControllerInvertYaw = builder
                .comment("Invert yaw axis (to match default keyboard mapping where positive yaw means 'left').")
                .define("invertYaw", true);

            fpvControllerInvertThrottle = builder
                .comment("Invert throttle axis before mapping [-1..1] -> [0..1].")
                .define("invertThrottle", true);

            fpvControllerArmButton = builder
                .comment("Raw joystick button index used for arm/disarm toggle.")
                .defineInRange("armButton", 0, 0, 63);

            builder.pop();
            builder.pop();
        }
    }
}
