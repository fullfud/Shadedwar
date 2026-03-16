package com.fullfud.fullfud.client;

import com.fullfud.fullfud.client.render.FpvDroneRenderer;
import com.fullfud.fullfud.client.render.PlayerDecoyRenderer;
import com.fullfud.fullfud.client.input.ControllerCalibration;
import com.fullfud.fullfud.client.input.ControllerCalibrationStore;
import com.fullfud.fullfud.client.input.FpvControllerInput;
import com.fullfud.fullfud.client.screen.ControllerCalibrationScreen;
import com.fullfud.fullfud.client.screen.FpvConfiguratorScreen;
import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.config.FullfudClientConfig;
import com.fullfud.fullfud.core.network.FullfudNetwork;
import com.fullfud.fullfud.core.network.packet.FpvControlPacket;
import com.fullfud.fullfud.core.network.packet.FpvReleasePacket;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class FpvClientHandler {
    private static final KeyMapping FPV_YAW_LEFT = new KeyMapping("key.fullfud.fpv_yaw_left", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, "key.categories.fullfud");
    private static final KeyMapping FPV_YAW_RIGHT = new KeyMapping("key.fullfud.fpv_yaw_right", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, "key.categories.fullfud");
    private static final KeyMapping FPV_ARM = new KeyMapping("key.fullfud.fpv_arm", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.fullfud");
    private static final KeyMapping FPV_CALIBRATE = new KeyMapping("key.fullfud.fpv_calibrate", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.fullfud");

    private static final ResourceLocation FONT_DIGITAL_LOC = new ResourceLocation("fullfud", "digital");
    private static final Style DIGITAL_STYLE = Style.EMPTY.withFont(FONT_DIGITAL_LOC);

    private static final ResourceLocation TEX_PRICEL = new ResourceLocation("fullfud", "textures/gui/hud/pricel.png");
    private static final ResourceLocation TEX_VERT = new ResourceLocation("fullfud", "textures/gui/hud/vert.png");

    private static final ResourceLocation BATTERY_0 = new ResourceLocation("fullfud", "textures/gui/hud/battery/a0.png");
    private static final ResourceLocation BATTERY_25 = new ResourceLocation("fullfud", "textures/gui/hud/battery/a25.png");
    private static final ResourceLocation BATTERY_50 = new ResourceLocation("fullfud", "textures/gui/hud/battery/a50.png");
    private static final ResourceLocation BATTERY_75 = new ResourceLocation("fullfud", "textures/gui/hud/battery/a75.png");
    private static final ResourceLocation BATTERY_100 = new ResourceLocation("fullfud", "textures/gui/hud/battery/a100.png");

    private static final ResourceLocation SIGNAL_0 = new ResourceLocation("fullfud", "textures/gui/hud/signal/0.png");
    private static final ResourceLocation SIGNAL_25 = new ResourceLocation("fullfud", "textures/gui/hud/signal/25.png");
    private static final ResourceLocation SIGNAL_50 = new ResourceLocation("fullfud", "textures/gui/hud/signal/50.png");
    private static final ResourceLocation SIGNAL_75 = new ResourceLocation("fullfud", "textures/gui/hud/signal/75.png");
    private static final ResourceLocation SIGNAL_100 = new ResourceLocation("fullfud", "textures/gui/hud/signal/100.png");

    private static final ResourceLocation SHADER_LOC = new ResourceLocation("fullfud", "shaders/post/fpv_post.json");
    private static final float KEYBOARD_THROTTLE_MAX = 0.65F;

    private static UUID activeDrone;
    private static FpvDroneEntity cachedResolvedDrone;
    private static long cachedResolvedDroneGameTime = Long.MIN_VALUE;
    private static UUID cachedResolvedPlayerId;
    private static UUID cachedResolvedPreferredDroneId;
    private static Object cachedResolvedLevel;
    private static boolean resolvedDroneCacheComputed;
    private static float throttleDemand;
    private static float throttleDisplayMax = 1.0F;
    private static double speedMs;
    private static double groundSpeedKmh;
    private static boolean escRequested;
    private static boolean releaseSent;
    private static double distanceToPilot;

    private static double lastMouseX;
    private static double lastMouseY;
    private static boolean mouseInitialized = false;

    private static boolean inFpvMode = false;
    private static CameraType previousCameraType;
    private static boolean forcedFirstPerson;
    private static Integer previousFov;
    private static boolean forcedFov;
    private static PostChain fpvPostChain;
    private static Field passesFieldCache;
    private static Method cameraSetPositionMethodCache;
    private static int lastChainWidth = -1;
    private static int lastChainHeight = -1;
    private static float clientTime = 0.0F;
    private static final boolean OPTIFINE_PRESENT = isClassPresent("net.optifine.Config");
    private static float lastResolvedCameraYaw = 0.0F;
    private static final float CAMERA_ROTATION_SMOOTH_ALPHA = 0.42F;
    private static final double CAMERA_POSITION_SMOOTH_ALPHA = 0.48D;
    private static final float CAMERA_ROTATION_SNAP_DOT = 0.15F;
    private static final double CAMERA_POSITION_SNAP_DISTANCE_SQR = 4.0D;
    private static final float CAMERA_YAW_SINGULARITY_PITCH_DEG = 89.0F;
    private static final double CAMERA_YAW_SINGULARITY_HORIZ_EPS = 1.0E-3D;
    private static final Quaternionf smoothedCameraQuaternion = new Quaternionf();
    private static final Quaternionf targetCameraQuaternion = new Quaternionf();
    private static final Quaternionf zeroRollQuaternionScratch = new Quaternionf();
    private static final Vector3f cameraForwardScratch = new Vector3f();
    private static final Vector3f cameraUpScratch = new Vector3f();
    private static final Vector3f zeroRollUpScratch = new Vector3f();
    private static final Vector3f rollCrossScratch = new Vector3f();
    private static UUID smoothedCameraDroneId;
    private static boolean smoothedCameraInitialized;
    private static double smoothedCameraX;
    private static double smoothedCameraY;
    private static double smoothedCameraZ;
    private static boolean localPlayerStateCaptured = false;

    private static final ControllerCalibration controllerCalibration = new ControllerCalibration();
    private static boolean lastControllerPresent = false;
    private static boolean localPlayerInvisible;
    private static boolean localPlayerSilent;

    private FpvClientHandler() {
    }

    public static void registerClientEvents(final IEventBus modEventBus) {
        modEventBus.addListener(FpvClientHandler::onRegisterRenderers);
        modEventBus.addListener(FpvClientHandler::onRegisterKeyMappings);
    }

    public static void openConfigurator(final UUID droneId) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new FpvConfiguratorScreen(droneId));
    }

    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ControllerCalibrationStore.loadInto(controllerCalibration);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onRenderTick);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onCameraAngles);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onRenderGui);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onRenderOverlay);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onRenderHand);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onRenderLiving);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onRenderPlayer);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onPlayLevelSoundAtEntity);
        });
    }

    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FullfudRegistries.FPV_DRONE_ENTITY.get(), FpvDroneRenderer::new);
        event.registerEntityRenderer(FullfudRegistries.PLAYER_DECOY_ENTITY.get(), PlayerDecoyRenderer::new);
        event.registerEntityRenderer(FullfudRegistries.EXPLOSION_SHRAPNEL_ENTITY.get(), context -> new ThrownItemRenderer<>(context, 0.5F, false));
    }

    public static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(FPV_YAW_LEFT);
        event.register(FPV_YAW_RIGHT);
        event.register(FPV_ARM);
        event.register(FPV_CALIBRATE);
    }

    private static void onRenderTick(final TickEvent.RenderTickEvent event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            invalidateSmoothedCameraState();
            if (event.phase != TickEvent.Phase.END) return;
            if (inFpvMode) {
                inFpvMode = false;
                destroyFpvChain();
            }
            restoreFov();
            return;
        }

        final FpvDroneEntity drone = resolveActiveControlledDrone(minecraft);
        if (isFpvActive(minecraft, drone)) {
            updateSmoothedCameraState(drone, event.renderTickTime);
        } else {
            invalidateSmoothedCameraState();
        }

        if (event.phase != TickEvent.Phase.END) return;

        boolean shouldFpv = false;
        float signal = 1.0F;
        if (drone != null) {
            shouldFpv = true;
            signal = drone.getSignalQuality();
        }

        if (!shouldFpv) {
            if (inFpvMode) {
                inFpvMode = false;
                destroyFpvChain();
            }
            restoreFov();
            return;
        }

        inFpvMode = true;
        final double shaderTimeScale = FullfudClientConfig.CLIENT.fpvPostShaderTimeScale.get();
        clientTime += event.renderTickTime * (float) shaderTimeScale;

        if (shouldUsePostShader()) {
            ensureFpvChain(minecraft);
            if (fpvPostChain != null) {
                resizeFpvChainIfNeeded(minecraft);
                updateShaderUniforms(signal);
                try {
                    fpvPostChain.process(event.renderTickTime);
                } catch (Exception e) {
                }
            }
        } else {
            destroyFpvChain();
        }
    }

    private static void ensureFpvChain(final Minecraft mc) {
        if (fpvPostChain != null) return;
        try {
            fpvPostChain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), SHADER_LOC);
            lastChainWidth = mc.getWindow().getWidth();
            lastChainHeight = mc.getWindow().getHeight();
            fpvPostChain.resize(lastChainWidth, lastChainHeight);
            passesFieldCache = null;
        } catch (Exception e) {
            fpvPostChain = null;
            passesFieldCache = null;
        }
    }

    private static void resizeFpvChainIfNeeded(final Minecraft mc) {
        if (fpvPostChain == null) return;
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w != lastChainWidth || h != lastChainHeight) {
            lastChainWidth = w;
            lastChainHeight = h;
            try {
                fpvPostChain.resize(w, h);
            } catch (Exception e) {
            }
        }
    }

    private static void destroyFpvChain() {
        if (fpvPostChain != null) {
            try {
                fpvPostChain.close();
            } catch (Exception e) {
            }
        }
        fpvPostChain = null;
        passesFieldCache = null;
        lastChainWidth = -1;
        lastChainHeight = -1;
    }

    private static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            resetState();
            return;
        }

        // Controller calibration: keybind and auto-show on first detect
        handleCalibration(minecraft);

        if (minecraft.getCameraEntity() instanceof FpvDroneEntity cameraDrone && (cameraDrone.isRemoved() || !cameraDrone.isAlive())) {
            resetState();
            return;
        }

        final FpvDroneEntity drone = resolveActiveControlledDrone(minecraft);
        if (drone == null) {
            resetState();
            return;
        }

        ensureDroneCamera(minecraft, drone);

        if (!hasUsableGoggles(minecraft, drone)) {
            if (!releaseSent) {
                FullfudNetwork.getChannel().sendToServer(new FpvReleasePacket(drone.getUUID()));
                releaseSent = true;
            }
            return;
        } else {
            releaseSent = false;
        }

        ensureFirstPerson(minecraft);
        ensureFpvFov(minecraft);
        stabilizeLocalPlayer(minecraft.player, drone);
        if (FullfudClientConfig.CLIENT.fpvSuppressSpectatorHotbarKeys.get()) {
            suppressSpectatorHotbarKeys(minecraft);
        }

        if (activeDrone == null || !activeDrone.equals(drone.getUUID())) {
            throttleDemand = drone.getThrust();
            activeDrone = drone.getUUID();
        }

        final boolean canProcessInput = minecraft.screen == null;

        final FpvControllerInput.State controllerState = canProcessInput
            ? FpvControllerInput.poll(controllerCalibration)
            : new FpvControllerInput.State(false, 0, 0, 0, 0, false, false);
        final boolean controllerActive = isControllerInputActive(controllerState);

        double curX = minecraft.mouseHandler.xpos();
        double curY = minecraft.mouseHandler.ypos();

        if (!mouseInitialized) {
            lastMouseX = curX;
            lastMouseY = curY;
            mouseInitialized = true;
        }

        float mousePitchDelta = 0.0F;
        float mouseRollDelta = 0.0F;
        if (canProcessInput && FullfudClientConfig.CLIENT.fpvCameraMouseLookEnabled.get()) {
            final double dx = curX - lastMouseX;
            final double dy = curY - lastMouseY;
            final float vanillaSensitivity = (float) (double) minecraft.options.sensitivity().get();
            final float legacyMultiplier = (float) (FullfudClientConfig.CLIENT.fpvCameraMouseSensitivity.get() / 0.015D);
            final float mouseScale = vanillaSensitivity * 0.007F * legacyMultiplier;
            mousePitchDelta = (float) dy * mouseScale;
            mouseRollDelta = (float) dx * mouseScale;
        }

        lastMouseX = curX;
        lastMouseY = curY;

        Vec3 velocity = drone.getDeltaMovement();
        speedMs = velocity.length() * 20.0D;
        Vec3 horiz = new Vec3(velocity.x, 0, velocity.z);
        groundSpeedKmh = horiz.length() * 20.0D * 3.6D;

        distanceToPilot = Math.sqrt(drone.distanceToSqr(minecraft.player));

        // Inverted pitch on keyboard: W/S swapped.
        final float keyPitch = axis(minecraft.options.keyDown.isDown(), minecraft.options.keyUp.isDown());
        // A/D now controls yaw (left/right turn) instead of roll.
        final float keyYawFromMovement = axis(minecraft.options.keyRight.isDown(), minecraft.options.keyLeft.isDown());

        float pitchInput = keyPitch;
        float rollInput = 0.0F;

        float yawInput = Mth.clamp(
            keyYawFromMovement + axis(FPV_YAW_LEFT.isDown(), FPV_YAW_RIGHT.isDown()),
            -1.0F,
            1.0F
        );
        final boolean jumpDown = minecraft.options.keyJump.isDown();

        if (controllerActive) {
            if (FullfudClientConfig.CLIENT.fpvCameraControllerPriority.get()) {
                pitchInput = controllerState.pitch();
                rollInput = controllerState.roll();
                yawInput = controllerState.yaw();
                mousePitchDelta = 0.0F;
                mouseRollDelta = 0.0F;
            } else {
                pitchInput = Mth.clamp(pitchInput + controllerState.pitch(), -1.0F, 1.0F);
                rollInput = Mth.clamp(rollInput + controllerState.roll(), -1.0F, 1.0F);
                yawInput = Mth.clamp(yawInput + controllerState.yaw(), -1.0F, 1.0F);
            }
        }

        if (jumpDown) {
            // Always allow immediate keyboard throttle burst on Space.
            throttleDemand = KEYBOARD_THROTTLE_MAX;
            throttleDisplayMax = KEYBOARD_THROTTLE_MAX;
        } else if (controllerActive && controllerState.hasThrottle()) {
            final double slew = FullfudClientConfig.CLIENT.fpvControllerThrottleSlew.get();
            final float a = (float) Mth.clamp(1.0D - slew, 0.0D, 1.0D);
            throttleDemand = Mth.lerp(a, throttleDemand, Mth.clamp(controllerState.throttle(), 0.0F, 1.0F));
            throttleDisplayMax = 1.0F;
        } else {
            // fpvdrone intentionally avoids an unrestricted keyboard throttle path.
            throttleDemand = 0.0F;
            throttleDisplayMax = KEYBOARD_THROTTLE_MAX;
        }

        byte armAction = 0;
        if (FPV_ARM.consumeClick()) {
            armAction = drone.isArmed() ? (byte) 2 : (byte) 1;
        }

        if (controllerState.present() && controllerState.armClicked()) {
            armAction = drone.isArmed() ? (byte) 2 : (byte) 1;
        }
        
        FullfudNetwork.getChannel().sendToServer(new FpvControlPacket(
            drone.getUUID(),
            pitchInput,
            rollInput,
            yawInput,
            mousePitchDelta,
            mouseRollDelta,
            throttleDemand,
            armAction
        ));

        if (FullfudClientConfig.CLIENT.fpvCameraReleaseOnPause.get() && minecraft.screen instanceof PauseScreen && !escRequested) {
            escRequested = true;
            FullfudNetwork.getChannel().sendToServer(new FpvReleasePacket(drone.getUUID()));
        } else if (!(minecraft.screen instanceof PauseScreen)) {
            escRequested = false;
        }
    }

    private static void suppressSpectatorHotbarKeys(final Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            final KeyMapping mapping = minecraft.options.keyHotbarSlots[i];
            if (mapping == null) {
                continue;
            }
            mapping.setDown(false);
            while (mapping.consumeClick()) {
                // drain queued presses
            }
        }
    }

    private static void ensureFirstPerson(final Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        if (forcedFirstPerson) {
            return;
        }
        previousCameraType = minecraft.options.getCameraType();
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        forcedFirstPerson = true;
    }

    private static void restoreCameraType() {
        if (!forcedFirstPerson) {
            return;
        }
        forcedFirstPerson = false;
        final CameraType restore = previousCameraType;
        previousCameraType = null;

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        if (restore != null) {
            minecraft.options.setCameraType(restore);
        }
    }

    private static void ensureFpvFov(final Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        if (!FullfudClientConfig.CLIENT.fpvCameraForceFov.get()) {
            restoreFov();
            return;
        }
        if (forcedFov) {
            return;
        }
        previousFov = minecraft.options.fov().get();
        minecraft.options.fov().set(FullfudClientConfig.CLIENT.fpvCameraFov.get());
        forcedFov = true;
    }

    private static void restoreFov() {
        if (!forcedFov) {
            return;
        }
        forcedFov = false;
        final Integer restore = previousFov;
        previousFov = null;

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return;
        }
        if (restore != null) {
            minecraft.options.fov().set(restore);
        }
    }

    private static void handleCalibration(final Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        final FpvControllerInput.RawJoystickState rawState = FpvControllerInput.snapshotRawState();
        final boolean controllerNow = rawState != null;

        if (controllerNow
                && !lastControllerPresent
                && rawState != null
                && !controllerCalibration.isReady()
                && !(minecraft.screen instanceof ControllerCalibrationScreen)) {
            openCalibrationScreen(minecraft);
        }
        lastControllerPresent = controllerNow;

        if (FPV_CALIBRATE.consumeClick() && !(minecraft.screen instanceof ControllerCalibrationScreen)) {
            openCalibrationScreen(minecraft);
        }
    }

    private static void openCalibrationScreen(final Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new ControllerCalibrationScreen(controllerCalibration));
    }

    public static ControllerCalibration getCalibration() {
        return controllerCalibration;
    }

    private static void resetState() {
        final Minecraft minecraft = Minecraft.getInstance();
        final boolean shouldRestoreCamera = minecraft != null
            && (minecraft.getCameraEntity() instanceof FpvDroneEntity || activeDrone != null);
        restoreCameraType();
        restoreFov();
        if (inFpvMode) {
            inFpvMode = false;
            destroyFpvChain();
        }
        stopActiveDroneAudio();
        if (shouldRestoreCamera) {
            forceCameraToPlayer();
        }
        restoreLocalPlayerState();
        activeDrone = null;
        invalidateResolvedDroneCache();
        throttleDemand = 0.0F;
        throttleDisplayMax = 1.0F;
        escRequested = false;
        releaseSent = false;
        distanceToPilot = 0;
        lastResolvedCameraYaw = 0.0F;
        invalidateSmoothedCameraState();
        mouseInitialized = false;
    }

    private static void stopActiveDroneAudio() {
        if (activeDrone == null) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        for (final var entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof FpvDroneEntity drone)) {
                continue;
            }
            if (!activeDrone.equals(drone.getUUID())) {
                continue;
            }
            drone.stopClientSound();
            break;
        }
    }

    private static void forceCameraToPlayer() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        if (minecraft.getCameraEntity() != minecraft.player) {
            minecraft.setCameraEntity(minecraft.player);
        }
        tryUpdateSoundListener(minecraft);
    }

    private static void tryUpdateSoundListener(final Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        try {
            final var soundManager = minecraft.getSoundManager();
            final var camera = minecraft.gameRenderer.getMainCamera();
            final java.lang.reflect.Method method = soundManager.getClass().getMethod("updateSource", camera.getClass());
            method.invoke(soundManager, camera);
        } catch (Throwable ignored) {
        }
        try {
            final var soundManager = minecraft.getSoundManager();
            final java.lang.reflect.Method method = soundManager.getClass().getMethod("resume");
            method.invoke(soundManager);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void updateShaderUniforms(final float signalQuality) {
        if (fpvPostChain == null) return;
        try {
            if (passesFieldCache == null) {
                for (Field f : PostChain.class.getDeclaredFields()) {
                    if (List.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object obj = f.get(fpvPostChain);
                        if (obj instanceof List<?> list && !list.isEmpty()) {
                            if (list.get(0) instanceof PostPass) {
                                passesFieldCache = f;
                                break;
                            }
                        }
                    }
                }
            }

            if (passesFieldCache != null) {
                List<PostPass> passes = (List<PostPass>) passesFieldCache.get(fpvPostChain);
                for (final PostPass pass : passes) {
                    final Uniform signalU = pass.getEffect().getUniform("SignalQuality");
                    if (signalU != null) signalU.set(signalQuality);

                    final Uniform timeU = pass.getEffect().getUniform("Time");
                    if (timeU != null) timeU.set(clientTime);
                }
            }
        } catch (Exception e) {
        }
    }

    private static void ensureDroneCamera(final Minecraft minecraft, final FpvDroneEntity drone) {
        if (minecraft == null || minecraft.player == null || drone == null) {
            return;
        }
        if (minecraft.getCameraEntity() != drone) {
            minecraft.setCameraEntity(drone);
            tryUpdateSoundListener(minecraft);
        }
    }

    private static boolean shouldUsePostShader() {
        return FullfudClientConfig.CLIENT.fpvPostShaderEnabled.get() && !OPTIFINE_PRESENT;
    }

    private static boolean isControllerInputActive(final FpvControllerInput.State state) {
        if (state == null || !state.present()) {
            return false;
        }
        final float axisThreshold = 0.03F;
        if (Math.abs(state.pitch()) > axisThreshold || Math.abs(state.roll()) > axisThreshold || Math.abs(state.yaw()) > axisThreshold) {
            return true;
        }
        if (state.hasThrottle()) {
            final float throttleNeutral = expectedThrottleNeutral();
            final float throttleDelta = Math.abs(Mth.clamp(state.throttle(), 0.0F, 1.0F) - throttleNeutral);
            if (throttleDelta > axisThreshold) {
                return true;
            }
        }
        if (state.armClicked()) {
            return true;
        }
        return false;
    }

    private static float expectedThrottleNeutral() {
        if (FullfudClientConfig.CLIENT.fpvControllerGamepadThrottleMode.get() == FullfudClientConfig.GamepadThrottleMode.RIGHT_TRIGGER) {
            return 0.0F;
        }
        return 0.5F;
    }

    private static boolean isClassPresent(final String className) {
        try {
            Class.forName(className, false, FpvClientHandler.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static float axis(final boolean positive, final boolean negative) {
        final float pos = positive ? 1.0F : 0.0F;
        final float neg = negative ? 1.0F : 0.0F;
        return Mth.clamp(pos - neg, -1.0F, 1.0F);
    }

    private static void invalidateSmoothedCameraState() {
        smoothedCameraInitialized = false;
        smoothedCameraDroneId = null;
        smoothedCameraX = 0.0D;
        smoothedCameraY = 0.0D;
        smoothedCameraZ = 0.0D;
        smoothedCameraQuaternion.identity();
        targetCameraQuaternion.identity();
    }

    private static void updateSmoothedCameraState(final FpvDroneEntity drone, final float partialTick) {
        if (drone == null) {
            invalidateSmoothedCameraState();
            return;
        }

        final float clampedPartial = Mth.clamp(partialTick, 0.0F, 1.0F);
        targetCameraQuaternion.set(drone.getCameraQuaternion(clampedPartial));
        if (!Float.isFinite(targetCameraQuaternion.x)
            || !Float.isFinite(targetCameraQuaternion.y)
            || !Float.isFinite(targetCameraQuaternion.z)
            || !Float.isFinite(targetCameraQuaternion.w)) {
            return;
        }
        targetCameraQuaternion.normalize();

        final Vec3 targetPosition = drone.getEyePosition(clampedPartial);
        final UUID droneId = drone.getUUID();
        if (!smoothedCameraInitialized || !Objects.equals(smoothedCameraDroneId, droneId)) {
            smoothedCameraQuaternion.set(targetCameraQuaternion);
            smoothedCameraDroneId = droneId;
            smoothedCameraInitialized = true;
            smoothedCameraX = targetPosition.x;
            smoothedCameraY = targetPosition.y;
            smoothedCameraZ = targetPosition.z;
            return;
        }

        float dot = smoothedCameraQuaternion.x * targetCameraQuaternion.x
            + smoothedCameraQuaternion.y * targetCameraQuaternion.y
            + smoothedCameraQuaternion.z * targetCameraQuaternion.z
            + smoothedCameraQuaternion.w * targetCameraQuaternion.w;
        if (dot < 0.0F) {
            targetCameraQuaternion.mul(-1.0F);
            dot = -dot;
        }

        if (!Float.isFinite(dot) || dot < CAMERA_ROTATION_SNAP_DOT) {
            smoothedCameraQuaternion.set(targetCameraQuaternion);
        } else {
            smoothedCameraQuaternion.slerp(targetCameraQuaternion, CAMERA_ROTATION_SMOOTH_ALPHA);
            smoothedCameraQuaternion.normalize();
        }

        final double dx = targetPosition.x - smoothedCameraX;
        final double dy = targetPosition.y - smoothedCameraY;
        final double dz = targetPosition.z - smoothedCameraZ;
        final double distSq = dx * dx + dy * dy + dz * dz;
        if (!Double.isFinite(distSq) || distSq > CAMERA_POSITION_SNAP_DISTANCE_SQR) {
            smoothedCameraX = targetPosition.x;
            smoothedCameraY = targetPosition.y;
            smoothedCameraZ = targetPosition.z;
        } else {
            smoothedCameraX = Mth.lerp(CAMERA_POSITION_SMOOTH_ALPHA, smoothedCameraX, targetPosition.x);
            smoothedCameraY = Mth.lerp(CAMERA_POSITION_SMOOTH_ALPHA, smoothedCameraY, targetPosition.y);
            smoothedCameraZ = Mth.lerp(CAMERA_POSITION_SMOOTH_ALPHA, smoothedCameraZ, targetPosition.z);
        }
    }

    private static void trySetCameraPosition(final Camera camera, final double x, final double y, final double z) {
        if (camera == null) {
            return;
        }
        try {
            if (cameraSetPositionMethodCache == null) {
                cameraSetPositionMethodCache = Camera.class.getDeclaredMethod(
                    "setPosition",
                    double.class,
                    double.class,
                    double.class
                );
                cameraSetPositionMethodCache.setAccessible(true);
            }
            cameraSetPositionMethodCache.invoke(camera, x, y, z);
        } catch (Throwable ignored) {
        }
    }

    private static CameraAngles resolveSmoothedCameraOrientation(final float fallbackYaw) {
        if (!smoothedCameraInitialized) {
            return new CameraAngles(fallbackYaw, 0.0F, 0.0F);
        }

        cameraForwardScratch.set(0.0F, 0.0F, 1.0F);
        cameraUpScratch.set(0.0F, 1.0F, 0.0F);
        final Quaternionf orientation = targetCameraQuaternion.set(smoothedCameraQuaternion);
        orientation.transform(cameraForwardScratch);
        orientation.transform(cameraUpScratch);

        final double horiz = Math.sqrt(
            cameraForwardScratch.x * cameraForwardScratch.x
                + cameraForwardScratch.z * cameraForwardScratch.z
        );
        final float pitch = (float) Math.toDegrees(Math.atan2(-cameraForwardScratch.y, horiz));

        float yaw = fallbackYaw;
        if (horiz > CAMERA_YAW_SINGULARITY_HORIZ_EPS && Math.abs(pitch) < CAMERA_YAW_SINGULARITY_PITCH_DEG) {
            yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-cameraForwardScratch.x, cameraForwardScratch.z)));
        }

        zeroRollQuaternionScratch.rotationYXZ(
            (float) Math.toRadians(-yaw),
            (float) Math.toRadians(pitch),
            0.0F
        );
        zeroRollUpScratch.set(0.0F, 1.0F, 0.0F);
        zeroRollQuaternionScratch.transform(zeroRollUpScratch);

        rollCrossScratch.set(zeroRollUpScratch).cross(cameraUpScratch);
        float roll = (float) Math.toDegrees(Math.atan2(
            rollCrossScratch.dot(cameraForwardScratch),
            zeroRollUpScratch.dot(cameraUpScratch)
        ));
        if (!Float.isFinite(roll)) {
            roll = 0.0F;
        }

        return new CameraAngles(
            Mth.wrapDegrees(yaw),
            Mth.wrapDegrees(pitch),
            Mth.wrapDegrees(roll)
        );
    }

    private static void onCameraAngles(final ViewportEvent.ComputeCameraAngles event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final FpvDroneEntity drone = resolveActiveControlledDrone(minecraft);
        if (!isFpvActive(minecraft, drone)) {
            invalidateSmoothedCameraState();
            return;
        }

        final float partialTick = (float) event.getPartialTick();
        updateSmoothedCameraState(drone, partialTick);
        final CameraAngles orientation = resolveSmoothedCameraOrientation(lastResolvedCameraYaw);
        float yaw = orientation.yaw();
        final float pitch = orientation.pitch();
        final float roll = orientation.roll();

        if (!Float.isFinite(yaw)) {
            yaw = lastResolvedCameraYaw;
        } else {
            lastResolvedCameraYaw = yaw;
        }

        if (smoothedCameraInitialized
            && Objects.equals(smoothedCameraDroneId, drone.getUUID())
            && Double.isFinite(smoothedCameraX)
            && Double.isFinite(smoothedCameraY)
            && Double.isFinite(smoothedCameraZ)) {
            trySetCameraPosition(event.getCamera(), smoothedCameraX, smoothedCameraY, smoothedCameraZ);
        }

        final CameraType cameraType = minecraft != null && minecraft.options != null
            ? minecraft.options.getCameraType()
            : CameraType.FIRST_PERSON;

        if (cameraType == CameraType.THIRD_PERSON_FRONT) {
            event.setYaw(Mth.wrapDegrees(yaw + 180.0F));
            event.setPitch(-pitch);
            event.setRoll(-roll);
            return;
        }

        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setRoll(roll);
    }

    private record CameraAngles(float yaw, float pitch, float roll) {
    }

    private static void onRenderGui(final net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        if (!FullfudClientConfig.CLIENT.fpvHudEnabled.get()) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) return;
        final FpvDroneEntity drone = resolveActiveControlledDrone(minecraft);
        if (drone == null) return;
        if (!isFpvActive(minecraft, drone)) {
            return;
        }

        final GuiGraphics g = event.getGuiGraphics();
        FpvOsdHudRenderer.render(g, minecraft, drone, speedMs, groundSpeedKmh, distanceToPilot, throttleDisplayMax);
    }

    private static int displayedPowerPercent(final FpvDroneEntity drone) {
        if (drone == null) {
            return 0;
        }
        final float displayMax = Math.max(throttleDisplayMax, 0.01F);
        final float normalizedPower = Mth.clamp(drone.getThrust() / displayMax, 0.0F, 1.0F);
        return Mth.floor(normalizedPower * 100.0F);
    }

    private static void renderControllerDebug(final GuiGraphics graphics, final Font font, final int width, final int height) {
        final FpvControllerInput.DebugState debug = FpvControllerInput.getLastDebugState();
        if (debug == null) {
            return;
        }

        final String[] lines = {
            "CTRL dbg",
            "Ввод: " + (debug.inputEnabled() ? "вкл" : "выкл") + " | режим: " + debug.mode(),
            "Пульт: " + (debug.joystickName().isBlank() ? "-" : debug.joystickName()),
            "Калибровка: " + (debug.calibrationControllerName().isBlank() ? "-" : debug.calibrationControllerName()),
            "Совпадение: " + (debug.calibrationMatches() ? "да" : "нет") + " | готова: " + (debug.calibrationReady() ? "да" : "нет"),
            "Подключено: " + debug.connectedControllers() + " | jid: " + debug.joystickId(),
            "Взвод: " + debug.armBinding() + " | pressed=" + debug.armPressed() + " | click=" + debug.armClicked(),
            String.format("P %.2f | R %.2f | Y %.2f | T %.2f", debug.pitch(), debug.roll(), debug.yaw(), debug.throttle())
        };

        int maxWidth = 0;
        for (final String line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }

        final int padding = 4;
        final int lineHeight = 10;
        final int boxWidth = maxWidth + padding * 2;
        final int boxHeight = lines.length * lineHeight + padding * 2;
        final int boxX = width - boxWidth - 10;
        final int boxY = height - boxHeight - 40;

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x88000000);
        for (int i = 0; i < lines.length; i++) {
            final int color = i == 0 ? 0xFF88FF88 : 0xFFFFFFFF;
            graphics.drawString(font, lines[i], boxX + padding, boxY + padding + i * lineHeight, color, false);
        }
    }

    private static void onRenderOverlay(final RenderGuiOverlayEvent.Pre event) {
        if (!FullfudClientConfig.CLIENT.fpvHideVanillaHud.get()) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) return;
        final FpvDroneEntity drone = resolveActiveControlledDrone(minecraft);
        if (drone == null) return;
        if (!isFpvActive(minecraft, drone)) return;
        event.setCanceled(true);
    }

    private static void onRenderHand(final RenderHandEvent event) {
        if (!FullfudClientConfig.CLIENT.fpvHideHand.get()) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) return;
        if (!isOwnerFpvSessionActive(minecraft)) return;
        event.setCanceled(true);
    }

    private static void onRenderLiving(final RenderLivingEvent.Pre<?, ?> event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || event == null || event.getEntity() == null) {
            return;
        }
        if (!isLocalOwnerEntity(minecraft, event.getEntity())) {
            return;
        }
        if (!isOwnerFpvSessionActive(minecraft)) {
            return;
        }
        event.setCanceled(true);
    }

    private static void onRenderPlayer(final RenderPlayerEvent.Pre event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || event == null || event.getEntity() == null) {
            return;
        }
        if (!isLocalOwnerEntity(minecraft, event.getEntity())) {
            return;
        }
        if (!isOwnerFpvSessionActive(minecraft)) {
            return;
        }
        event.setCanceled(true);
    }

    private static void onPlayLevelSoundAtEntity(final PlayLevelSoundEvent.AtEntity event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || event == null || event.getEntity() == null) {
            return;
        }
        if (!isLocalOwnerEntity(minecraft, event.getEntity())) {
            return;
        }
        if (!isOwnerFpvSessionActive(minecraft)) {
            return;
        }
        if (event.getSource() != SoundSource.PLAYERS) {
            return;
        }
        event.setCanceled(true);
    }

    private static ResourceLocation getBatteryTexture(int percent) {
        if (percent >= 75) return BATTERY_100;
        if (percent >= 50) return BATTERY_75;
        if (percent >= 25) return BATTERY_50;
        if (percent > 0) return BATTERY_25;
        return BATTERY_0;
    }

    private static ResourceLocation getSignalTexture(int percent) {
        if (percent >= 75) return SIGNAL_100;
        if (percent >= 50) return SIGNAL_75;
        if (percent >= 25) return SIGNAL_50;
        if (percent > 0) return SIGNAL_25;
        return SIGNAL_0;
    }

    private static boolean isFpvActive(final Minecraft minecraft, final FpvDroneEntity drone) {
        if (minecraft == null || minecraft.player == null || drone == null) {
            return false;
        }
        return isDroneControlledByLocalPlayer(minecraft, drone) && hasUsableGoggles(minecraft, drone);
    }

    private static boolean isOwnerFpvSessionActive(final Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        if (minecraft.getCameraEntity() instanceof FpvDroneEntity cameraDrone) {
            return !cameraDrone.isRemoved() && cameraDrone.isAlive();
        }
        final FpvDroneEntity resolved = resolveActiveControlledDrone(minecraft);
        if (resolved != null) {
            return true;
        }
        return activeDrone != null;
    }

    private static boolean isLocalOwnerEntity(final Minecraft minecraft, final Entity entity) {
        return minecraft != null
            && minecraft.player != null
            && entity != null
            && minecraft.player.getUUID().equals(entity.getUUID());
    }

    private static boolean hasUsableGoggles(final Minecraft minecraft, final FpvDroneEntity drone) {
        final var player = minecraft.player;
        if (player == null) {
            return false;
        }
        final ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof com.fullfud.fullfud.common.item.FpvGogglesItem)) {
            return false;
        }
        final var linked = com.fullfud.fullfud.common.item.FpvGogglesItem.getLinked(head);
        if (linked.isEmpty()) {
            return true;
        }
        return linked.get().equals(drone.getUUID()) || isDroneControlledByLocalPlayer(minecraft, drone);
    }

    private static boolean isDroneControlledByLocalPlayer(final Minecraft minecraft, final FpvDroneEntity drone) {
        if (minecraft == null || minecraft.player == null || drone == null) {
            return false;
        }
        final UUID controller = drone.getControllerId();
        return controller != null && controller.equals(minecraft.player.getUUID());
    }

    private static void invalidateResolvedDroneCache() {
        cachedResolvedDrone = null;
        cachedResolvedDroneGameTime = Long.MIN_VALUE;
        cachedResolvedPlayerId = null;
        cachedResolvedPreferredDroneId = null;
        cachedResolvedLevel = null;
        resolvedDroneCacheComputed = false;
    }

    private static FpvDroneEntity cacheResolvedDrone(
        final Minecraft minecraft,
        final UUID preferredDroneId,
        final FpvDroneEntity drone
    ) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            invalidateResolvedDroneCache();
            return drone;
        }
        cachedResolvedDrone = drone;
        cachedResolvedDroneGameTime = minecraft.level.getGameTime();
        cachedResolvedPlayerId = minecraft.player.getUUID();
        cachedResolvedPreferredDroneId = preferredDroneId;
        cachedResolvedLevel = minecraft.level;
        resolvedDroneCacheComputed = true;
        return drone;
    }

    private static boolean isResolvedDroneCacheHit(final Minecraft minecraft, final UUID preferredDroneId) {
        if (!resolvedDroneCacheComputed || minecraft == null || minecraft.level == null || minecraft.player == null) {
            return false;
        }
        if (cachedResolvedLevel != minecraft.level || cachedResolvedDroneGameTime != minecraft.level.getGameTime()) {
            return false;
        }
        if (!Objects.equals(cachedResolvedPlayerId, minecraft.player.getUUID())) {
            return false;
        }
        if (!Objects.equals(cachedResolvedPreferredDroneId, preferredDroneId)) {
            return false;
        }
        if (cachedResolvedDrone == null) {
            return true;
        }
        return !cachedResolvedDrone.isRemoved()
            && cachedResolvedDrone.isAlive()
            && isDroneControlledByLocalPlayer(minecraft, cachedResolvedDrone);
    }

    static FpvDroneEntity resolveActiveControlledDrone(final Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return null;
        }

        final ItemStack head = minecraft.player.getItemBySlot(EquipmentSlot.HEAD);
        UUID preferredDroneId = null;
        if (head.getItem() instanceof com.fullfud.fullfud.common.item.FpvGogglesItem) {
            preferredDroneId = com.fullfud.fullfud.common.item.FpvGogglesItem.getLinked(head).orElse(null);
        }
        if (preferredDroneId == null) {
            preferredDroneId = activeDrone;
        }

        if (minecraft.getCameraEntity() instanceof FpvDroneEntity cameraDrone
            && !cameraDrone.isRemoved()
            && cameraDrone.isAlive()
            && isDroneControlledByLocalPlayer(minecraft, cameraDrone)) {
            return cacheResolvedDrone(minecraft, preferredDroneId, cameraDrone);
        }

        if (isResolvedDroneCacheHit(minecraft, preferredDroneId)) {
            return cachedResolvedDrone;
        }

        FpvDroneEntity fallback = null;
        for (final var entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof FpvDroneEntity drone)) {
                continue;
            }
            if (drone.isRemoved() || !drone.isAlive()) {
                continue;
            }
            if (!isDroneControlledByLocalPlayer(minecraft, drone)) {
                continue;
            }
            if (preferredDroneId != null && preferredDroneId.equals(drone.getUUID())) {
                return cacheResolvedDrone(minecraft, preferredDroneId, drone);
            }
            if (fallback == null) {
                fallback = drone;
            }
        }
        return cacheResolvedDrone(minecraft, preferredDroneId, fallback);
    }

    private static void stabilizeLocalPlayer(final net.minecraft.client.player.LocalPlayer player, final FpvDroneEntity drone) {
        if (player == null || drone == null) {
            return;
        }
        suppressOwnerEntityCopies(player);
        if (!localPlayerStateCaptured) {
            localPlayerInvisible = player.isInvisible();
            localPlayerSilent = player.isSilent();
            localPlayerStateCaptured = true;
        }
        player.setInvisible(true);
        player.setSilent(true);
    }

    private static void suppressOwnerEntityCopies(final net.minecraft.client.player.LocalPlayer player) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || player == null) {
            return;
        }
        final UUID ownerId = player.getUUID();
        for (final Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity == null || entity == player || !ownerId.equals(entity.getUUID())) {
                continue;
            }
            entity.setInvisible(true);
            entity.setSilent(true);
        }
    }

    private static void restoreLocalPlayerState() {
        if (!localPlayerStateCaptured) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            localPlayerStateCaptured = false;
            return;
        }
        minecraft.player.setInvisible(localPlayerInvisible);
        minecraft.player.setSilent(localPlayerSilent);
        localPlayerStateCaptured = false;
    }
}
