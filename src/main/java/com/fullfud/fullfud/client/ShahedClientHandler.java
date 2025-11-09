package com.fullfud.fullfud.client;

import com.fullfud.fullfud.client.render.ShahedDroneRenderer;
import com.fullfud.fullfud.client.screen.ShahedMonitorScreen;
import com.fullfud.fullfud.client.sound.ShahedEngineLoopSound;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.network.FullfudNetwork;
import com.fullfud.fullfud.core.network.packet.ShahedControlPacket;
import com.fullfud.fullfud.core.network.packet.ShahedLinkPacket;
import com.fullfud.fullfud.core.network.packet.ShahedStatusPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class ShahedClientHandler {
    private static final KeyMapping POWER_UP = new KeyMapping(
        "key.fullfud.power_up",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_R,
        "key.categories.fullfud"
    );
    private static final KeyMapping POWER_DOWN = new KeyMapping(
        "key.fullfud.power_down",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_F,
        "key.categories.fullfud"
    );

    private static ShahedStatusPacket lastStatus;
    private static long lastStatusTimestamp;
    private static final Map<UUID, EngineAudioController> ENGINE_AUDIO = new HashMap<>();

    private ShahedClientHandler() {
    }

    public static void registerClientEvents(final IEventBus modEventBus) {
        modEventBus.addListener(ShahedClientHandler::onRegisterRenderers);
        modEventBus.addListener(ShahedClientHandler::onRegisterKeyMappings);
    }

    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(FullfudRegistries.SHAHED_MONITOR_MENU.get(), ShahedMonitorScreen::new);
            MinecraftForge.EVENT_BUS.addListener(ShahedClientHandler::onClientTick);
        });
    }

    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FullfudRegistries.SHAHED_ENTITY.get(), ShahedDroneRenderer::new);
    }

    public static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(POWER_UP);
        event.register(POWER_DOWN);
    }

    public static void handleStatusPacket(final ShahedStatusPacket packet) {
        lastStatus = packet;
        lastStatusTimestamp = System.currentTimeMillis();
    }

    public static void handleLinkPacket(final ShahedLinkPacket packet) {
        
    }

    public static ShahedStatusPacket getLastStatus() {
        return lastStatus;
    }

    public static KeyMapping getPowerUpKey() {
        return POWER_UP;
    }

    public static KeyMapping getPowerDownKey() {
        return POWER_DOWN;
    }

    public static boolean hasFreshStatus(final int timeoutMs) {
        if (lastStatus == null) {
            return false;
        }
        return System.currentTimeMillis() - lastStatusTimestamp <= timeoutMs;
    }

    public static void sendControlPacket(final UUID droneId, final float forward, final float strafe, final float vertical, final float thrustDelta, final boolean boost) {
        if (droneId == null) {
            return;
        }
        FullfudNetwork.getChannel().sendToServer(new ShahedControlPacket(droneId, forward, strafe, vertical, thrustDelta, boost));
    }

    private static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (minecraft.level == null || minecraft.isPaused()) {
            ENGINE_AUDIO.values().forEach(EngineAudioController::stop);
            ENGINE_AUDIO.clear();
            return;
        }
        ENGINE_AUDIO.values().forEach(controller -> controller.seen = false);
        for (final var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof ShahedDroneEntity drone) {
                ENGINE_AUDIO.computeIfAbsent(drone.getUUID(), id -> new EngineAudioController(drone))
                    .updateFromEntity();
            }
        }
        ENGINE_AUDIO.entrySet().removeIf(entry -> entry.getValue().shouldRemove());
    }

    private static final class EngineAudioController {
        private static final float ACTIVE_THRESHOLD = 0.02F;
        private final ShahedDroneEntity drone;
        private ShahedEngineLoopSound loopSound;
        private float lastThrust;

        private boolean seen;

        private EngineAudioController(final ShahedDroneEntity drone) {
            this.drone = drone;
        }

        private boolean isInvalid() {
            return drone.isRemoved() || !drone.isAlive();
        }

        private void stop() {
            if (loopSound != null) {
                loopSound.stopSound();
                loopSound = null;
            }
        }

        private void updateFromEntity() {
            seen = true;
            update(drone.getThrust());
        }

        private void update(final float thrust) {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.level == null) {
                return;
            }
            if (isInvalid()) {
                stop();
                return;
            }
            final float clamped = Mth.clamp(thrust, 0.0F, 1.0F);
            if (clamped > ACTIVE_THRESHOLD) {
                if (lastThrust <= ACTIVE_THRESHOLD) {
                    playOneShot(FullfudRegistries.SHAHED_ENGINE_START.get(), clamped);
                }
                ensureLoop();
                if (loopSound != null) {
                    loopSound.setEngineMix(clamped);
                }
            } else if (lastThrust > ACTIVE_THRESHOLD) {
                stop();
                playOneShot(FullfudRegistries.SHAHED_ENGINE_END.get(), lastThrust);
            }
            lastThrust = clamped;
            if (clamped <= ACTIVE_THRESHOLD && loopSound != null) {
                loopSound.setEngineMix(0.0F);
            }
        }

        private boolean shouldRemove() {
            if (!seen || isInvalid()) {
                stop();
                return true;
            }
            return false;
        }

        private void ensureLoop() {
            final Minecraft minecraft = Minecraft.getInstance();
            if (loopSound != null && !loopSound.isStopped()) {
                return;
            }
            loopSound = new ShahedEngineLoopSound(drone);
            loopSound.setEngineMix(Math.max(lastThrust, ACTIVE_THRESHOLD));
            minecraft.getSoundManager().play(loopSound);
        }

        private void playOneShot(final SoundEvent event, final float thrust) {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.level == null) {
                return;
            }
            final float volume = 0.25F + 0.75F * thrust;
            final float pitch = 0.9F + 0.2F * thrust;
            minecraft.level.playLocalSound(drone.getX(), drone.getY(), drone.getZ(), event, SoundSource.NEUTRAL, volume, pitch, false);
        }
    }
}
