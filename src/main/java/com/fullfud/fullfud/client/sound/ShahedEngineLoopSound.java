package com.fullfud.fullfud.client.sound;

import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class ShahedEngineLoopSound extends AbstractTickableSoundInstance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ShahedDroneEntity drone;
    private float targetVolume = 0.0F;
    private float targetPitch = 1.0F;
    private String lastDebugState = "";
    private float smoothedTurn = 0.0F;

    public ShahedEngineLoopSound(final ShahedDroneEntity drone) {
        super(FullfudRegistries.SHAHED_ENGINE_LOOP.get(), SoundSource.NEUTRAL, RandomSource.create());
        this.drone = drone;
        this.looping = true;
        this.attenuation = Attenuation.NONE;
        this.relative = false;
        this.delay = 0;
        this.volume = 0.0F;
        this.pitch = 1.0F;
    }

    public void setEngineMix(final float thrust) {
        final float clamped = Mth.clamp(thrust, 0.0F, 1.0F);
        this.targetVolume = 0.3F + clamped * 0.7F;
        this.targetPitch = 0.85F + clamped * 0.35F;
        this.volume = this.targetVolume;
        this.pitch = this.targetPitch;
    }

    @Override
    public void tick() {
        if (drone.isRemoved() || !drone.isAlive()) {
            stop();
            return;
        }
        this.x = drone.getX();
        this.y = drone.getY();
        this.z = drone.getZ();
        final Vec3 motion = drone.getDeltaMovement();
        final double speed = motion.length();
        final float diveFactor = (float) Mth.clamp(-motion.y / 1.4D, 0.0D, 1.4D);
        final float climbFactor = (float) Mth.clamp(motion.y / 1.8D, 0.0D, 1.0D);
        final float speedFactor = (float) Mth.clamp(speed / 40.0D, 0.0D, 1.0D);
        final float rawTurn = (float) Mth.clamp(Math.abs(Mth.wrapDegrees(drone.getYRot() - drone.yRotO)) / 8.0F, 0.0D, 1.0D);
        smoothedTurn = Mth.lerp(0.08F, smoothedTurn, rawTurn);

        final float flightVolumeMult = 1.0F + diveFactor * 0.9F + speedFactor * 0.35F + smoothedTurn * 0.25F;
        final float flightPitchOffset = diveFactor * 1.15F - climbFactor * 0.25F + speedFactor * 0.18F + smoothedTurn * 0.75F;

        float distanceFactor = 1.0F;
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null) {
            final double dist = minecraft.player.distanceTo(drone);
            final double norm = Mth.clamp(dist / 700.0D, 0.0D, 1.0D);
            distanceFactor = (float) Math.pow(1.0D - norm, 1.4D);
        }
        final float desiredVolume = targetVolume * flightVolumeMult * distanceFactor;
        final float desiredPitch = targetPitch + flightPitchOffset;

        this.volume = Mth.lerp(0.2F, this.volume, desiredVolume);
        this.pitch = Mth.lerp(0.25F, this.pitch, desiredPitch);

        logState(distanceFactor, diveFactor, climbFactor, speedFactor, smoothedTurn);
    }

    private void logState(final float distanceFactor, final float diveFactor, final float climbFactor, final float speedFactor, final float turnFactor) {
        final String state = diveFactor > 0.35F ? "PIKING"
            : climbFactor > 0.35F ? "CLIMBING"
            : turnFactor > 0.25F ? "TURNING"
            : "LEVEL";
        if (!state.equals(lastDebugState)) {
            LOGGER.info("[ShahedAudio] {} thrustVol={} dive={} climb={} turn={} speed={}",
                state,
                String.format("%.2f", targetVolume),
                String.format("%.2f", diveFactor),
                String.format("%.2f", climbFactor),
                String.format("%.2f", turnFactor),
                String.format("%.2f", speedFactor)
            );
            lastDebugState = state;
        }
    }

    public void stopSound() {
        stop();
    }
}
