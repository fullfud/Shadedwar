package com.fullfud.fullfud.client;

import com.fullfud.fullfud.client.render.FpvDroneRenderer;
import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.network.FullfudNetwork;
import com.fullfud.fullfud.core.network.packet.FpvControlPacket;
import com.fullfud.fullfud.core.network.packet.FpvReleasePacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class FpvClientHandler {
    private static final KeyMapping FPV_YAW_LEFT = new KeyMapping("key.fullfud.fpv_yaw_left", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, "key.categories.fullfud");
    private static final KeyMapping FPV_YAW_RIGHT = new KeyMapping("key.fullfud.fpv_yaw_right", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, "key.categories.fullfud");
    private static final KeyMapping FPV_ARM = new KeyMapping("key.fullfud.fpv_arm", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.fullfud");

    private static UUID activeDrone;
    private static float throttleDemand;
    private static double lastX;
    private static double lastY;
    private static double lastZ;
    private static double speedMs;
    private static boolean escRequested;
    private static boolean releaseSent;

    private FpvClientHandler() {
    }

    public static void registerClientEvents(final IEventBus modEventBus) {
        modEventBus.addListener(FpvClientHandler::onRegisterRenderers);
        modEventBus.addListener(FpvClientHandler::onRegisterKeyMappings);
    }

    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onCameraAngles);
            MinecraftForge.EVENT_BUS.addListener(FpvClientHandler::onRenderGui);
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

    private static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            activeDrone = null;
            throttleDemand = 0.0F;
            escRequested = false;
            releaseSent = false;
            return;
        }
        if (!(minecraft.getCameraEntity() instanceof FpvDroneEntity drone)) {
            activeDrone = null;
            throttleDemand = 0.0F;
            escRequested = false;
            releaseSent = false;
            return;
        }
        final UUID controller = drone.getControllerId();
        if (controller == null || !controller.equals(minecraft.player.getUUID())) {
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
        if (minecraft.screen instanceof PauseScreen && !escRequested) {
            escRequested = true;
            FullfudNetwork.getChannel().sendToServer(new FpvReleasePacket(drone.getUUID()));
            return;
        } else if (!(minecraft.screen instanceof PauseScreen)) {
            escRequested = false;
        }
        if (activeDrone == null || !activeDrone.equals(drone.getUUID())) {
            throttleDemand = drone.getThrust();
            activeDrone = drone.getUUID();
            lastX = drone.getX();
            lastY = drone.getY();
            lastZ = drone.getZ();
        }
        final double dx = drone.getX() - lastX;
        final double dy = drone.getY() - lastY;
        final double dz = drone.getZ() - lastZ;
        speedMs = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0D;
        lastX = drone.getX();
        lastY = drone.getY();
        lastZ = drone.getZ();
        final float pitchInput = axis(minecraft.options.keyUp.isDown(), minecraft.options.keyDown.isDown());
        final float rollInput = axis(minecraft.options.keyLeft.isDown(), minecraft.options.keyRight.isDown());
        final float yawInput = axis(FPV_YAW_LEFT.isDown(), FPV_YAW_RIGHT.isDown());
        final float throttleDelta = axis(minecraft.options.keyJump.isDown(), minecraft.options.keyShift.isDown());
        if (Math.abs(throttleDelta) > 0.001F) {
            throttleDemand = Mth.clamp(throttleDemand + throttleDelta * 0.02F, 0.0F, 1.0F);
        }
        byte armAction = 0;
        if (FPV_ARM.consumeClick()) {
            armAction = drone.isArmed() ? (byte) 2 : (byte) 1;
        }
        FullfudNetwork.getChannel().sendToServer(new FpvControlPacket(drone.getUUID(), pitchInput, rollInput, yawInput, throttleDemand, armAction));
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
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        if (!(minecraft.getCameraEntity() instanceof FpvDroneEntity drone)) {
            return;
        }
        final UUID controller = drone.getControllerId();
        if (controller == null || !controller.equals(minecraft.player.getUUID()) || !hasLinkedGoggles(minecraft, drone)) {
            return;
        }
        final GuiGraphics g = event.getGuiGraphics();
        final int w = minecraft.getWindow().getGuiScaledWidth();
        final int h = minecraft.getWindow().getGuiScaledHeight();
        final int cx = w / 2;
        final int cy = h / 2;
        g.fill(cx - 1, cy - 6, cx + 1, cy + 6, 0x80FFFFFF);
        g.fill(cx - 6, cy - 1, cx + 6, cy + 1, 0x80FFFFFF);
        final String throttle = String.format("PWR %3d%%", (int) (drone.getThrust() * 100.0F));
        final String armed = drone.isArmed() ? "ARMED" : "SAFE";
        final String speed = String.format("%.1f m/s", speedMs);
        final String alt = String.format("ALT %.1f", drone.getY());
        int y = h - 45;
        g.drawString(minecraft.font, armed, 12, y, drone.isArmed() ? 0xFFFF5555 : 0xFF55FF55, false);
        y += 10;
        g.drawString(minecraft.font, throttle, 12, y, 0xFFFFFFFF, false);
        y += 10;
        g.drawString(minecraft.font, speed, 12, y, 0xFFFFFFFF, false);
        y += 10;
        g.drawString(minecraft.font, alt, 12, y, 0xFFFFFFFF, false);
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
        return com.fullfud.fullfud.common.item.FpvGogglesItem.getLinked(head)
            .filter(id -> id.equals(drone.getUUID()))
            .isPresent();
    }
}
