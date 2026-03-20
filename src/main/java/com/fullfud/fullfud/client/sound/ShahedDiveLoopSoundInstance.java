package com.fullfud.fullfud.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ShahedDiveLoopSoundInstance extends AbstractTickableSoundInstance {
    private static final int MAX_FADE_TICKS = 50;
    private static final long TIMEOUT_TICKS = 15L;
    private long lastUpdateTick = -1L;
    private boolean dying;
    private int fadeTicks;
    private float targetVolume;
    private float targetPitch = 0.9F;
    private float targetDopplerPitch = 1.0F;
    private float currentDopplerPitch = 1.0F;
    private float targetGainHF = 1.0F;
    private float currentGainHF = 1.0F;
    private final double maxAudibleDistance;

    public ShahedDiveLoopSoundInstance(final SoundEvent sound, final double maxAudibleDistance) {
        super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.relative = false;
        this.attenuation = Attenuation.NONE;
        this.volume = 0.0F;
        this.pitch = 0.9F;
        this.maxAudibleDistance = maxAudibleDistance;
    }

    public void update(
        final double x,
        final double y,
        final double z,
        final float diveIntensity,
        final float speedFactor,
        final float dopplerPitch,
        final float gainHF,
        final long gameTick
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.lastUpdateTick = gameTick;
        this.dying = false;
        this.targetDopplerPitch = dopplerPitch;
        this.targetGainHF = gainHF;
        final float dive = Mth.clamp(diveIntensity, 0.0F, 1.6F);
        final float speed = Mth.clamp(speedFactor, 0.0F, 1.0F);
        this.targetVolume = Mth.clamp(2.0F + dive * 2.5F + speed * 0.5F, 0.0F, 8.0F);
        final float speedPitchMult = Mth.clamp(speed * 0.3F, 0.0F, 0.4F);
        this.targetPitch = Mth.clamp(0.9F + dive * 0.5F + speedPitchMult, 0.2F, 2.0F);
    }

    public void requestFadeOut() {
        this.dying = true;
    }

    public void keepAlive(final long gameTick) {
        this.lastUpdateTick = gameTick;
    }

    public void extrapolate(
        final double newX,
        final double newY,
        final double newZ,
        final float dopplerPitch,
        final float gainHF,
        final long gameTick
    ) {
        this.x = newX;
        this.y = newY;
        this.z = newZ;
        this.targetDopplerPitch = dopplerPitch;
        this.targetGainHF = gainHF;
        this.lastUpdateTick = gameTick;
    }

    public void forceStop() {
        removeFilter();
        stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.isPaused()) {
            removeFilter();
            stop();
            return;
        }
        final long now = minecraft.level.getGameTime();
        if (lastUpdateTick >= 0L && now - lastUpdateTick > TIMEOUT_TICKS) {
            dying = true;
        }
        if (dying) {
            if (fadeTicks <= 0) {
                removeFilter();
                stop();
                return;
            }
            --fadeTicks;
            targetGainHF = 1.0F;
            targetDopplerPitch = 1.0F;
        } else if (fadeTicks < MAX_FADE_TICKS) {
            ++fadeTicks;
        }
        currentDopplerPitch = Mth.lerp(0.2F, currentDopplerPitch, targetDopplerPitch);
        currentGainHF = Mth.lerp(0.15F, currentGainHF, targetGainHF);
        float distanceVolumeFactor = 1.0F;
        if (minecraft.getCameraEntity() != null) {
            final double distance = minecraft.getCameraEntity().position().distanceTo(new Vec3(x, y, z));
            distanceVolumeFactor = DroneSoundEffects.computeDistanceVolumeFactor(distance, maxAudibleDistance);
        }
        final float fadeMultiplier = (float) fadeTicks / (float) MAX_FADE_TICKS;
        final float desiredVolume = targetVolume * fadeMultiplier * distanceVolumeFactor;
        this.volume = Mth.lerp(0.25F, this.volume, desiredVolume);
        this.pitch = Mth.lerp(0.25F, this.pitch, targetPitch * currentDopplerPitch);
        applyFilter();
        if (this.volume <= 5.0E-4F && dying) {
            removeFilter();
            stop();
        }
    }

    private void applyFilter() {
        if (OpenALFilters.isAvailable()) {
            OpenALFilters.applyFilterForInstance(this, 1.0F, currentGainHF);
        }
    }

    private void removeFilter() {
        if (OpenALFilters.isAvailable()) {
            OpenALFilters.removeFilterForInstance(this);
        }
    }
}
