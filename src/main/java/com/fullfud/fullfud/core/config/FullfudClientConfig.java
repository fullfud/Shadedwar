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
        public final ForgeConfigSpec.BooleanValue fpvCameraForceFirstPerson;
        public final ForgeConfigSpec.BooleanValue fpvCameraForceFov;
        public final ForgeConfigSpec.IntValue fpvCameraFov;
        public final ForgeConfigSpec.DoubleValue fpvCameraMouseSensitivity;
        public final ForgeConfigSpec.BooleanValue fpvCameraMouseLookEnabled;
        public final ForgeConfigSpec.BooleanValue fpvCameraControllerPriority;
        public final ForgeConfigSpec.BooleanValue fpvCameraReleaseOnPause;
        public final ForgeConfigSpec.BooleanValue fpvSuppressSpectatorHotbarKeys;
        public final ForgeConfigSpec.BooleanValue fpvPostShaderEnabled;
        public final ForgeConfigSpec.DoubleValue fpvPostShaderTimeScale;
        public final ForgeConfigSpec.BooleanValue fpvHudEnabled;
        public final ForgeConfigSpec.BooleanValue fpvHideVanillaHud;
        public final ForgeConfigSpec.BooleanValue fpvHideHand;
        public final ForgeConfigSpec.IntValue fpvRenderDistanceCap;

        public final ForgeConfigSpec.IntValue shahedRenderDistanceCap;
        public final ForgeConfigSpec.BooleanValue shahedGhostRenderEnabled;
        public final ForgeConfigSpec.DoubleValue shahedGhostRenderRange;
        public final ForgeConfigSpec.IntValue shahedGhostTimeoutTicks;
        public final ForgeConfigSpec.BooleanValue shahedGhostRenderFullBright;
        public final ForgeConfigSpec.BooleanValue shahedUseLocalEntityAudio;
        public final ForgeConfigSpec.DoubleValue shahedLocalAudioActiveThreshold;
        public final ForgeConfigSpec.IntValue shahedStatusFreshnessMs;
        public final ForgeConfigSpec.IntValue shahedMonitorControlIntervalTicks;
        public final ForgeConfigSpec.DoubleValue shahedMonitorControlMaxDistance;
        public final ForgeConfigSpec.DoubleValue shahedMonitorNoiseMaxDistance;
        public final ForgeConfigSpec.DoubleValue shahedMonitorNoiseMaxOpacity;
        public final ForgeConfigSpec.DoubleValue shahedMonitorJammerMaxRadius;
        public final ForgeConfigSpec.DoubleValue shahedMonitorJammerFullStrengthRadius;
        public final ForgeConfigSpec.DoubleValue shahedMonitorJammerEdgeStrength;
        public final ForgeConfigSpec.DoubleValue shahedMonitorThrustDeltaStep;
        public final ForgeConfigSpec.BooleanValue shahedMonitorCameraShakeEnabled;
        public final ForgeConfigSpec.DoubleValue shahedMonitorEngineShakeScale;
        public final ForgeConfigSpec.DoubleValue shahedMonitorDiveSpeedThreshold;
        public final ForgeConfigSpec.DoubleValue shahedMonitorDiveSpeedRange;
        public final ForgeConfigSpec.DoubleValue shahedMonitorDiveShakeScale;
        public final ForgeConfigSpec.BooleanValue shahedMonitorPreviewEnabled;
        public final ForgeConfigSpec.BooleanValue shahedMonitorReticleEnabled;

        public final ForgeConfigSpec.BooleanValue droneAudioRemoteEnabled;
        public final ForgeConfigSpec.BooleanValue droneAudioMuteRemoteFpvWhenLoaded;
        public final ForgeConfigSpec.BooleanValue droneAudioMuteRemoteShahedWhenLoaded;
        public final ForgeConfigSpec.DoubleValue droneAudioRemoteVolumeScale;
        public final ForgeConfigSpec.IntValue droneAudioLoopTimeoutTicks;
        public final ForgeConfigSpec.DoubleValue droneAudioLoopVolumeLerp;
        public final ForgeConfigSpec.DoubleValue droneAudioLoopPitchLerp;
        public final ForgeConfigSpec.DoubleValue droneAudioStopVolumeEpsilon;

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

            builder.push("camera");

            fpvCameraForceFirstPerson = builder
                .comment("Force first person camera while controlling FPV drone.")
                .define("forceFirstPerson", true);

            fpvCameraForceFov = builder
                .comment("Force custom FOV while controlling FPV drone.")
                .define("forceFov", true);

            fpvCameraFov = builder
                .comment("FPV camera FOV when forceFov is enabled.")
                .defineInRange("fov", 110, 30, 170);

            fpvCameraMouseSensitivity = builder
                .comment("Mouse sensitivity multiplier for FPV pitch and roll control.")
                .defineInRange("mouseSensitivity", 0.015D, 0.001D, 0.1D);

            fpvCameraMouseLookEnabled = builder
                .comment("Enable mouse look input while controlling FPV drone.")
                .define("mouseLookEnabled", true);

            fpvCameraControllerPriority = builder
                .comment("When controller is present, controller pitch/roll/yaw override keyboard and mouse.")
                .define("controllerPriority", true);

            fpvCameraReleaseOnPause = builder
                .comment("Send release packet when pause menu is opened.")
                .define("releaseOnPause", true);

            fpvSuppressSpectatorHotbarKeys = builder
                .comment("Suppress hotbar keys while camera is attached to FPV drone.")
                .define("suppressSpectatorHotbarKeys", true);

            fpvPostShaderEnabled = builder
                .comment("Enable FPV post processing shader effects.")
                .define("postShaderEnabled", true);

            fpvPostShaderTimeScale = builder
                .comment("Time scale multiplier for FPV post shader animation.")
                .defineInRange("postShaderTimeScale", 0.02D, 0.0D, 1.0D);

            fpvHudEnabled = builder
                .comment("Draw custom FPV HUD overlay.")
                .define("hudEnabled", true);

            fpvHideVanillaHud = builder
                .comment("Hide vanilla HUD while controlling FPV drone.")
                .define("hideVanillaHud", true);

            fpvHideHand = builder
                .comment("Hide hand rendering while controlling FPV drone.")
                .define("hideHand", true);

            builder.pop();

            builder.push("render");

            fpvRenderDistanceCap = builder
                .comment("Additional FPV render distance cap in blocks when frustum fallback is used.")
                .defineInRange("renderDistanceCap", 256, 64, 10000);

            builder.pop();
            builder.pop();

            builder.push("shahed");

            builder.push("render");

            shahedRenderDistanceCap = builder
                .comment("Additional Shahed render distance cap in blocks when frustum fallback is used.")
                .defineInRange("renderDistanceCap", 2000, 64, 10000);

            shahedGhostRenderEnabled = builder
                .comment("Enable client side Shahed ghost rendering outside normal entity tracking.")
                .define("ghostRenderEnabled", true);

            shahedGhostRenderRange = builder
                .comment("Maximum distance in blocks to draw Shahed ghost render.")
                .defineInRange("ghostRenderRange", 10000.0D, 128.0D, 20000.0D);

            shahedGhostTimeoutTicks = builder
                .comment("How long to keep ghost state without updates.")
                .defineInRange("ghostTimeoutTicks", 60, 1, 1200);

            shahedGhostRenderFullBright = builder
                .comment("Render Shahed ghost with full brightness.")
                .define("ghostRenderFullBright", true);

            builder.pop();

            builder.push("audio");

            shahedUseLocalEntityAudio = builder
                .comment("Use local Shahed entity audio system on client. Disable to use only remote audio packets.")
                .define("useLocalEntityAudio", false);

            shahedLocalAudioActiveThreshold = builder
                .comment("Local Shahed engine activity threshold.")
                .defineInRange("localAudioActiveThreshold", 0.02D, 0.0D, 1.0D);

            builder.pop();

            builder.push("monitor");

            shahedStatusFreshnessMs = builder
                .comment("How long status packets are considered fresh in Shahed monitor UI.")
                .defineInRange("statusFreshnessMs", 2000, 100, 30000);

            shahedMonitorControlIntervalTicks = builder
                .comment("Interval in ticks between Shahed monitor control packets.")
                .defineInRange("controlIntervalTicks", 1, 1, 20);

            shahedMonitorControlMaxDistance = builder
                .comment("Maximum control distance from player to Shahed in blocks.")
                .defineInRange("controlMaxDistance", 10000.0D, 128.0D, 20000.0D);

            shahedMonitorNoiseMaxDistance = builder
                .comment("Distance in blocks at which monitor distance noise reaches maximum.")
                .defineInRange("noiseMaxDistance", 10000.0D, 128.0D, 20000.0D);

            shahedMonitorNoiseMaxOpacity = builder
                .comment("Maximum opacity contributed by distance based monitor noise.")
                .defineInRange("noiseMaxOpacity", 0.5D, 0.0D, 1.0D);

            shahedMonitorJammerMaxRadius = builder
                .comment("Maximum jammer radius in blocks.")
                .defineInRange("jammerMaxRadius", 600.0D, 32.0D, 5000.0D);

            shahedMonitorJammerFullStrengthRadius = builder
                .comment("Jammer radius in blocks where effect is full strength.")
                .defineInRange("jammerFullStrengthRadius", 300.0D, 0.0D, 5000.0D);

            shahedMonitorJammerEdgeStrength = builder
                .comment("Remaining jammer strength at max radius.")
                .defineInRange("jammerEdgeStrength", 0.01D, 0.0D, 1.0D);

            shahedMonitorThrustDeltaStep = builder
                .comment("Thrust delta step applied each control tick.")
                .defineInRange("thrustDeltaStep", 0.02D, 0.0D, 1.0D);

            shahedMonitorCameraShakeEnabled = builder
                .comment("Enable monitor camera shake from thrust and dive speed.")
                .define("cameraShakeEnabled", true);

            shahedMonitorEngineShakeScale = builder
                .comment("Engine shake multiplier.")
                .defineInRange("engineShakeScale", 1.2D, 0.0D, 10.0D);

            shahedMonitorDiveSpeedThreshold = builder
                .comment("Vertical speed threshold for dive shake in m/s.")
                .defineInRange("diveSpeedThreshold", 15.0D, 0.0D, 200.0D);

            shahedMonitorDiveSpeedRange = builder
                .comment("Vertical speed range over threshold used to ramp dive shake.")
                .defineInRange("diveSpeedRange", 40.0D, 0.01D, 200.0D);

            shahedMonitorDiveShakeScale = builder
                .comment("Dive shake multiplier.")
                .defineInRange("diveShakeScale", 2.5D, 0.0D, 10.0D);

            shahedMonitorPreviewEnabled = builder
                .comment("Draw mini drone preview in monitor overlay.")
                .define("previewEnabled", true);

            shahedMonitorReticleEnabled = builder
                .comment("Draw center reticle in monitor overlay.")
                .define("reticleEnabled", true);

            builder.pop();
            builder.pop();

            builder.push("droneAudio");

            droneAudioRemoteEnabled = builder
                .comment("Enable remote drone audio packets.")
                .define("remoteEnabled", true);

            droneAudioMuteRemoteFpvWhenLoaded = builder
                .comment("Mute remote FPV audio when the FPV entity is locally loaded to avoid duplicates.")
                .define("muteRemoteFpvWhenLoaded", true);

            droneAudioMuteRemoteShahedWhenLoaded = builder
                .comment("Mute remote Shahed audio when the Shahed entity is locally loaded.")
                .define("muteRemoteShahedWhenLoaded", false);

            droneAudioRemoteVolumeScale = builder
                .comment("Volume scale multiplier for remote drone audio packets.")
                .defineInRange("remoteVolumeScale", 1.0D, 0.0D, 4.0D);

            droneAudioLoopTimeoutTicks = builder
                .comment("Ticks after last remote loop update before sound stops.")
                .defineInRange("loopTimeoutTicks", 40, 1, 600);

            droneAudioLoopVolumeLerp = builder
                .comment("Smoothing factor for remote loop volume.")
                .defineInRange("loopVolumeLerp", 0.35D, 0.0D, 1.0D);

            droneAudioLoopPitchLerp = builder
                .comment("Smoothing factor for remote loop pitch.")
                .defineInRange("loopPitchLerp", 0.25D, 0.0D, 1.0D);

            droneAudioStopVolumeEpsilon = builder
                .comment("Stop remote loop when volume is below epsilon and target is zero.")
                .defineInRange("stopVolumeEpsilon", 0.001D, 0.0D, 1.0D);

            builder.pop();
        }
    }
}
