package com.fullfud.fullfud.client;

import com.fullfud.fullfud.client.render.FpvDroneRenderer;
import com.fullfud.fullfud.client.input.FpvControllerInput;
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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class FpvClientHandler {
    private static final KeyMapping FPV_YAW_LEFT = new KeyMapping("key.fullfud.fpv_yaw_left", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, "key.categories.fullfud");
    private static final KeyMapping FPV_YAW_RIGHT = new KeyMapping("key.fullfud.fpv_yaw_right", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, "key.categories.fullfud");
    private static final KeyMapping FPV_ARM = new KeyMapping("key.fullfud.fpv_arm", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.fullfud");

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

    private static UUID activeDrone;
    private static float throttleDemand;
    private static double speedMs;
    private static double groundSpeedKmh;
    private static boolean escRequested;
    private static boolean releaseSent;
    private static double distanceToPilot;

    private static double lastMouseX;
    private static double lastMouseY;
    private static boolean mouseInitialized = false;
    private static double mouseAccumX;
    private static double mouseAccumY;
    private static final double MOUSE_SENSITIVITY = 0.015D;

    private static boolean inFpvMode = false;
    private static CameraType previousCameraType;
    private static boolean forcedFirstPerson;
    private static final int FPV_FOV = 110;
    private static Integer previousFov;
    private static boolean forcedFov;
    private static PostChain fpvPostChain;
    private static Field passesFieldCache;
    private static int lastChainWidth = -1;
    private static int lastChainHeight = -1;
    private static float clientTime = 0.0F;

    private FpvClientHandler() {
    }

    public static void registerClientEvents(final IEventBus modEventBus) {
        modEventBus.addListener(FpvClientHandler::onRegisterRenderers);
        modEventBus.addListener(FpvClientHandler::onRegisterKeyMappings);
    }

    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onRenderTick);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onCameraAngles);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onRenderGui);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onRenderOverlay);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onRenderHand);
        });
    }

    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FullfudRegistries.FPV_DRONE_ENTITY.get(), FpvDroneRenderer::new);
    }

    public static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(FPV_YAW_LEFT);
        event.register(FPV_YAW_RIGHT);
        event.register(FPV_ARM);
    }

    private static void onRenderTick(final TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            if (inFpvMode) {
                inFpvMode = false;
                destroyFpvChain();
            }
            restoreFov();
            return;
        }

        boolean shouldFpv = false;
        float signal = 1.0F;

        if (minecraft.getCameraEntity() instanceof FpvDroneEntity drone) {
            UUID controller = drone.getControllerId();
            if (controller != null && controller.equals(minecraft.player.getUUID()) && hasLinkedGoggles(minecraft, drone)) {
                shouldFpv = true;
                signal = drone.getSignalQuality();
            }
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
        clientTime += event.renderTickTime * 0.02F;

        ensureFpvChain(minecraft);
        if (fpvPostChain != null) {
            resizeFpvChainIfNeeded(minecraft);
            updateShaderUniforms(signal);
            try {
                fpvPostChain.process(event.renderTickTime);
            } catch (Exception e) {
            }
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

        if (!(minecraft.getCameraEntity() instanceof FpvDroneEntity drone)) {
            resetState();
            return;
        }

        final UUID controller = drone.getControllerId();
        if (controller == null || !controller.equals(minecraft.player.getUUID())) {
            resetState();
            return;
        }

        if (!hasLinkedGoggles(minecraft, drone)) {
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
        suppressSpectatorHotbarKeys(minecraft);

        if (activeDrone == null || !activeDrone.equals(drone.getUUID())) {
            throttleDemand = drone.getThrust();
            activeDrone = drone.getUUID();
        }

        final boolean canProcessInput = minecraft.screen == null && minecraft.isWindowActive();

        final FpvControllerInput.State controllerState = canProcessInput ? FpvControllerInput.poll() : new FpvControllerInput.State(false, 0, 0, 0, 0, false, false);

        double curX = minecraft.mouseHandler.xpos();
        double curY = minecraft.mouseHandler.ypos();

        if (!mouseInitialized) {
            lastMouseX = curX;
            lastMouseY = curY;
            mouseInitialized = true;
        }

        if (canProcessInput) {
            double dx = curX - lastMouseX;
            double dy = curY - lastMouseY;
            
            mouseAccumX += dx * MOUSE_SENSITIVITY;
            mouseAccumY -= dy * MOUSE_SENSITIVITY;
        }
        
        lastMouseX = curX;
        lastMouseY = curY;

        Vec3 velocity = drone.getDeltaMovement();
        speedMs = velocity.length() * 20.0D;
        Vec3 horiz = new Vec3(velocity.x, 0, velocity.z);
        groundSpeedKmh = horiz.length() * 20.0D * 3.6D;

        distanceToPilot = Math.sqrt(drone.distanceToSqr(minecraft.player));

        final float keyPitch = axis(minecraft.options.keyUp.isDown(), minecraft.options.keyDown.isDown());
        final float keyRoll = axis(minecraft.options.keyLeft.isDown(), minecraft.options.keyRight.isDown());

        float pitchInput = Mth.clamp(keyPitch + (float) mouseAccumY, -1.0F, 1.0F);
        float rollInput = Mth.clamp(keyRoll + (float) mouseAccumX, -1.0F, 1.0F);
        
        mouseAccumX = 0;
        mouseAccumY = 0;

        float yawInput = axis(FPV_YAW_LEFT.isDown(), FPV_YAW_RIGHT.isDown());
        final boolean jumpDown = minecraft.options.keyJump.isDown();

        if (controllerState.present()) {
            pitchInput = controllerState.pitch();
            rollInput = controllerState.roll();
            yawInput = controllerState.yaw();
        }

        if (controllerState.present() && controllerState.hasThrottle()) {
            final double slew = FullfudClientConfig.CLIENT.fpvControllerThrottleSlew.get();
            final float a = (float) Mth.clamp(1.0D - slew, 0.0D, 1.0D);
            throttleDemand = Mth.lerp(a, throttleDemand, Mth.clamp(controllerState.throttle(), 0.0F, 1.0F));
        } else {
            throttleDemand = jumpDown ? 1.0F : 0.0F;
        }

        byte armAction = 0;
        if (FPV_ARM.consumeClick()) {
            armAction = drone.isArmed() ? (byte) 2 : (byte) 1;
        }

        if (controllerState.present() && controllerState.armClicked()) {
            armAction = drone.isArmed() ? (byte) 2 : (byte) 1;
        }
        
        FullfudNetwork.getChannel().sendToServer(new FpvControlPacket(drone.getUUID(), pitchInput, rollInput, yawInput, throttleDemand, armAction));

        if (minecraft.screen instanceof PauseScreen && !escRequested) {
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
        if (forcedFov) {
            return;
        }
        previousFov = minecraft.options.fov().get();
        minecraft.options.fov().set(FPV_FOV);
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

    private static void resetState() {
        restoreCameraType();
        restoreFov();
        if (inFpvMode) {
            inFpvMode = false;
            destroyFpvChain();
        }
        activeDrone = null;
        throttleDemand = 0.0F;
        escRequested = false;
        releaseSent = false;
        distanceToPilot = 0;
        mouseAccumX = 0;
        mouseAccumY = 0;
        mouseInitialized = false;
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

    private static float axis(final boolean positive, final boolean negative) {
        final float pos = positive ? 1.0F : 0.0F;
        final float neg = negative ? 1.0F : 0.0F;
        return Mth.clamp(pos - neg, -1.0F, 1.0F);
    }

    private static void onCameraAngles(final ViewportEvent.ComputeCameraAngles event) {
        if (event.getCamera().getEntity() instanceof FpvDroneEntity drone) {
            final float partial = (float) event.getPartialTick();
            event.setRoll(drone.getCameraRoll(partial));
            event.setPitch(drone.getCameraPitch(partial));
        }
    }

    private static void onRenderGui(final net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) return;
        if (!(minecraft.getCameraEntity() instanceof FpvDroneEntity drone)) return;
        if (!isFpvActive(minecraft, drone)) {
            return;
        }

        final GuiGraphics g = event.getGuiGraphics();
        final PoseStack pose = g.pose();
        final int w = minecraft.getWindow().getGuiScaledWidth();
        final int h = minecraft.getWindow().getGuiScaledHeight();

        Font font = minecraft.font;

        float quality = drone.getSignalQuality();
        int rssi = (int) (quality * 100.0F);
        int battery = drone.getBatteryPercent();

        int cx = w / 2;
        int cy = h / 2;
        float partial = minecraft.getPartialTick();
        float roll = drone.getVisualRoll(partial);
        float pitch = drone.getVisualPitch(partial);

        g.blit(TEX_PRICEL, cx - 16, cy - 16, 0, 0, 32, 32, 32, 32);

        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-roll));

        float pitchOffset = pitch * 2.5F;
        pose.translate(0, pitchOffset, 0);

        pose.scale(2.0F, 2.0F, 1.0F);
        g.blit(TEX_VERT, -32, -2, 0, 0, 64, 4, 64, 4);

        pose.popPose();

        ResourceLocation batTex = getBatteryTexture(battery);
        g.blit(batTex, 10, 10, 16, 32, 0, 0, 64, 128, 64, 128);

        ResourceLocation sigTex = getSignalTexture(rssi);
        int sigW = 64;
        int sigH = 32;
        int sigX = w - 10 - sigW;
        g.blit(sigTex, sigX, 10, sigW, sigH, 0, 0, 128, 64, 128, 64);

        MutableComponent acroText = Component.literal("ACRO").withStyle(DIGITAL_STYLE);
        int acroW = font.width(acroText);
        g.drawString(font, acroText, cx - acroW / 2, 45, 0xFFFFFFFF, true);

        if (!drone.isArmed()) {
            MutableComponent disarmedText = Component.literal("D  I  S  A  R  M  E  D").withStyle(DIGITAL_STYLE);
            int dW = font.width(disarmedText);
            int dY = (int) (h * 0.75f);

            g.drawString(font, disarmedText, cx - dW / 2, dY, 0xFFFFFFFF, true);
        }

        if (quality <= 0.05F) {
            if (System.currentTimeMillis() % 1000 < 500) {
                MutableComponent text = Component.literal("CONNECTION LOST").withStyle(DIGITAL_STYLE);
                int tw = font.width(text);
                g.fill(cx - tw / 2 - 2, cy - 40 - 2, cx + tw / 2 + 2, cy - 40 + 10, 0xAA000000);
                g.drawString(font, text, cx - tw / 2, cy - 40, 0xFFFF0000, true);
            }
        } else if (distanceToPilot > 500 * drone.getSignalRangeScale()) {
            MutableComponent text = Component.literal("MAX RANGE").withStyle(DIGITAL_STYLE);
            int tw = font.width(text);
            g.drawString(font, text, cx - tw / 2, cy - 40, 0xFFFF0000, true);
        } else if (distanceToPilot > 450 * drone.getSignalRangeScale()) {
            if (System.currentTimeMillis() % 1000 < 500) {
                MutableComponent text = Component.literal("TURN AROUND").withStyle(DIGITAL_STYLE);
                int tw = font.width(text);
                g.drawString(font, text, cx - tw / 2, cy - 40, 0xFFFFAA00, true);
            }
        }

        MutableComponent batText = Component.literal(String.valueOf(battery)).withStyle(DIGITAL_STYLE);
        g.drawString(font, batText, 30, 22, 0xFFFFFFFF, true);

        MutableComponent rssiText = Component.literal(String.valueOf(rssi)).withStyle(DIGITAL_STYLE);
        int rssiTextW = font.width(rssiText);
        g.drawString(font, rssiText, sigX - rssiTextW - 5, 22, 0xFFFFFFFF, true);

        int botY = h - 10;

        MutableComponent cZ = Component.literal(String.format("z = %.0f", drone.getZ())).withStyle(DIGITAL_STYLE);
        MutableComponent cY = Component.literal(String.format("y = %.0f", drone.getY())).withStyle(DIGITAL_STYLE);
        MutableComponent cX = Component.literal(String.format("x = %.0f", drone.getX())).withStyle(DIGITAL_STYLE);

        g.drawString(font, cZ, 10, botY, 0xFFFFFFFF, true);
        g.drawString(font, cY, 10, botY - 10, 0xFFFFFFFF, true);
        g.drawString(font, cX, 10, botY - 20, 0xFFFFFFFF, true);

        MutableComponent power = Component.literal(String.format("Power = %3d%%", (int) (drone.getThrust() * 100))).withStyle(DIGITAL_STYLE);
        MutableComponent ias = Component.literal(String.format("IAS = %.0f KM/h", speedMs * 3.6D)).withStyle(DIGITAL_STYLE);
        MutableComponent gs = Component.literal(String.format("GS = %.0f KM/h", groundSpeedKmh)).withStyle(DIGITAL_STYLE);

        int rightX = w - 10;
        g.drawString(font, gs, rightX - font.width(gs), botY, 0xFFFFFFFF, true);
        g.drawString(font, ias, rightX - font.width(ias), botY - 10, 0xFFFFFFFF, true);
        g.drawString(font, power, rightX - font.width(power), botY - 20, 0xFFFFFFFF, true);
    }

    private static void onRenderOverlay(final RenderGuiOverlayEvent.Pre event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) return;
        if (!(minecraft.getCameraEntity() instanceof FpvDroneEntity drone)) return;
        if (!isFpvActive(minecraft, drone)) return;
        event.setCanceled(true);
    }

    private static void onRenderHand(final RenderHandEvent event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) return;
        if (!(minecraft.getCameraEntity() instanceof FpvDroneEntity drone)) return;
        if (!isFpvActive(minecraft, drone)) return;
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
        final UUID controller = drone.getControllerId();
        if (controller == null || !controller.equals(minecraft.player.getUUID())) {
            return false;
        }
        return hasLinkedGoggles(minecraft, drone);
    }

    private static boolean hasLinkedGoggles(final Minecraft minecraft, final FpvDroneEntity drone) {
        final var player = minecraft.player;
        if (player == null) {
            return false;
        }
        final ItemStack head = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof com.fullfud.fullfud.common.item.FpvGogglesItem)) {
            return false;
        }
        final var linked = com.fullfud.fullfud.common.item.FpvGogglesItem.getLinked(head);
        if (linked.isPresent()) {
            return linked.get().equals(drone.getUUID());
        }
        return true;
    }
}
