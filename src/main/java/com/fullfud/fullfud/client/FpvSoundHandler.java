package com.fullfud.fullfud.client;

import com.fullfud.fullfud.client.sound.DroneEngineLoopSoundInstance;
import com.fullfud.fullfud.client.sound.DroneSoundEffects;
import com.fullfud.fullfud.client.sound.FpvInteriorLoopSoundInstance;
import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.config.FullfudClientConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;

@OnlyIn(Dist.CLIENT)
public final class FpvSoundHandler {
    private static final Map<UUID, Controller> CONTROLLERS = new HashMap<>();

    private FpvSoundHandler() {
    }

    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.isPaused()) {
            clear();
            return;
        }
        if (!FullfudClientConfig.CLIENT.fpvUseLocalEntityAudio.get()) {
            clear();
            return;
        }
        final FpvDroneEntity controlledDrone = FpvClientHandler.resolveActiveControlledDrone(minecraft);
        final UUID controlledDroneId = controlledDrone != null ? controlledDrone.getUUID() : null;
        CONTROLLERS.values().forEach(controller -> controller.seenThisTick = false);
        for (final Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof FpvDroneEntity drone) {
                CONTROLLERS.computeIfAbsent(drone.getUUID(), id -> new Controller()).updateFromEntity(drone, nowTick(minecraft), controlledDroneId);
            }
        }
        final long nowTick = nowTick(minecraft);
        CONTROLLERS.entrySet().removeIf(entry -> entry.getValue().shouldRemove(nowTick));
    }

    public static void stopForDrone(final UUID droneId) {
        if (droneId == null) {
            return;
        }
        final Controller controller = CONTROLLERS.remove(droneId);
        if (controller != null) {
            controller.stopHard();
        }
    }

    public static void clear() {
        CONTROLLERS.values().forEach(Controller::stopHard);
        CONTROLLERS.clear();
    }

    private static long nowTick(final Minecraft minecraft) {
        return minecraft.level != null ? minecraft.level.getGameTime() : 0L;
    }

    private static final class Controller {
        private static final long TRACKING_FADE_START_TICKS = 200L;
        private static final long TRACKING_HARD_REMOVE_TICKS = 250L;
        private DroneEngineLoopSoundInstance engine;
        private FpvInteriorLoopSoundInstance interior;
        private long lastSeenTick = -1L;
        private boolean seenThisTick;
        private Vec3 prevDronePos;
        private Vec3 lastDroneVelocity = Vec3.ZERO;
        private Vec3 extrapolatedPos;

        private void ensureEngine(final Minecraft minecraft, final double maxDistance) {
            if (engine != null && !engine.isStopped()) {
                return;
            }
            final SoundEvent sound = FullfudRegistries.FPV_ENGINE_LOOP.get();
            engine = new DroneEngineLoopSoundInstance(sound, maxDistance);
            minecraft.getSoundManager().play(engine);
        }

        private void ensureInterior(final Minecraft minecraft) {
            if (interior != null && !interior.isStopped() && !interior.isTerminated()) {
                return;
            }
            final SoundEvent sound = FullfudRegistries.FPV_ENGINE_INTERIOR.get();
            interior = new FpvInteriorLoopSoundInstance(sound);
            minecraft.getSoundManager().play(interior);
        }

        private void updateFromEntity(final FpvDroneEntity drone, final long nowTick, final UUID controlledDroneId) {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.level == null) {
                return;
            }
            seenThisTick = true;
            lastSeenTick = nowTick;

            if (!drone.isArmed()) {
                if (engine != null) {
                    engine.requestFadeOut();
                }
                if (interior != null) {
                    interior.markForStop();
                }
                prevDronePos = null;
                return;
            }

            final boolean controlled = controlledDroneId != null && controlledDroneId.equals(drone.getUUID());
            if (controlled) {
                if (engine != null) {
                    engine.requestFadeOut();
                }
                ensureInterior(minecraft);
                if (interior != null) {
                    interior.update(drone.getThrust());
                }
                prevDronePos = null;
                return;
            }

            if (interior != null) {
                interior.markForStop();
            }

            final Vec3 dronePos = drone.position();
            final Vec3 motion = drone.getDeltaMovement();
            final double speed = motion.horizontalDistance();
            Vec3 droneVelocity = motion;
            if (prevDronePos != null) {
                droneVelocity = dronePos.subtract(prevDronePos);
            }

            prevDronePos = dronePos;
            lastDroneVelocity = droneVelocity;
            extrapolatedPos = dronePos;

            final float powerMix = Mth.clamp(drone.getThrust(), 0.0F, 1.0F);
            final float speedFactor = (float) Mth.clamp(speed / 0.5D, 0.0D, 1.0D);
            Vec3 playerPos = dronePos;
            if (minecraft.getCameraEntity() != null) {
                playerPos = minecraft.getCameraEntity().position();
            }
            final double distance = dronePos.distanceTo(playerPos);
            final double maxDistance = FullfudClientConfig.CLIENT.fpvSoundMaxDistance.get();
            final float dopplerPitch = DroneSoundEffects.computeDopplerPitch(dronePos, droneVelocity, playerPos);
            final float gainHF = DroneSoundEffects.computeCombinedGainHF(distance, dronePos.y, playerPos.y, maxDistance);
            ensureEngine(minecraft, maxDistance);
            if (engine != null) {
                engine.update(dronePos.x, dronePos.y, dronePos.z, powerMix, speedFactor, dopplerPitch, gainHF, nowTick);
            }
        }

        private boolean shouldRemove(final long nowTick) {
            final boolean engineStopped = engine == null || engine.isStopped();
            final boolean interiorStopped = interior == null || interior.isStopped() || interior.isTerminated();
            if (engineStopped && interiorStopped) {
                return true;
            }
            if (!seenThisTick) {
                final long elapsed = lastSeenTick >= 0L ? nowTick - lastSeenTick : 0L;
                if (elapsed > TRACKING_HARD_REMOVE_TICKS) {
                    stopHard();
                    return true;
                }
                extrapolateAndUpdate(nowTick, elapsed);
            }
            return false;
        }

        private void extrapolateAndUpdate(final long nowTick, final long elapsed) {
            if (interior != null) {
                interior.markForStop();
            }
            if (extrapolatedPos == null) {
                if (engine != null && !engine.isStopped()) {
                    engine.keepAlive(nowTick);
                }
                return;
            }
            extrapolatedPos = extrapolatedPos.add(lastDroneVelocity);
            final Minecraft minecraft = Minecraft.getInstance();
            Vec3 playerPos = extrapolatedPos;
            if (minecraft != null && minecraft.getCameraEntity() != null) {
                playerPos = minecraft.getCameraEntity().position();
            }
            final double distance = extrapolatedPos.distanceTo(playerPos);
            final double maxDistance = FullfudClientConfig.CLIENT.fpvSoundMaxDistance.get();
            final float dopplerPitch = DroneSoundEffects.computeDopplerPitch(extrapolatedPos, lastDroneVelocity, playerPos);
            final float gainHF = DroneSoundEffects.computeCombinedGainHF(distance, extrapolatedPos.y, playerPos.y, maxDistance);
            if (engine != null && !engine.isStopped()) {
                engine.extrapolate(extrapolatedPos.x, extrapolatedPos.y, extrapolatedPos.z, dopplerPitch, gainHF, nowTick);
            }
            final double fadeDistance = maxDistance > 0.0D ? maxDistance : 1500.0D;
            if ((distance > fadeDistance || elapsed > TRACKING_FADE_START_TICKS) && engine != null) {
                engine.requestFadeOut();
            }
        }

        private void stopHard() {
            if (engine != null) {
                engine.forceStop();
                engine = null;
            }
            if (interior != null) {
                interior.forceStop();
                interior = null;
            }
        }
    }
}
