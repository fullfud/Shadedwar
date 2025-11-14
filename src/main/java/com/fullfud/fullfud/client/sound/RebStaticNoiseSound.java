package com.fullfud.fullfud.client.sound;

import com.fullfud.fullfud.common.entity.RebEmitterEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class RebStaticNoiseSound extends AbstractTickableSoundInstance {
    private static final float BASE_VOLUME = 0.2F;
    private static final float MAX_PITCH_DRIFT = 0.08F;
    private final RebEmitterEntity emitter;
    private float targetVolume = BASE_VOLUME;

    public RebStaticNoiseSound(final RebEmitterEntity emitter) {
        super(FullfudRegistries.REB_STATIC_NOISE.get(), SoundSource.AMBIENT, RandomSource.create());
        this.emitter = emitter;
        this.looping = true;
        this.delay = 0;
        this.volume = BASE_VOLUME;
        this.pitch = 1.0F;
        this.attenuation = Attenuation.LINEAR;
        this.relative = false;
    }

    @Override
    public void tick() {
        if (shouldSilence()) {
            stop();
            return;
        }
        this.x = emitter.getX();
        this.y = emitter.getY() + 0.5F;
        this.z = emitter.getZ();
        final float flicker = 0.85F + random.nextFloat() * 0.3F;
        targetVolume = BASE_VOLUME * flicker;
        this.volume = Mth.lerp(0.25F, this.volume, targetVolume);
        final float pitchDrift = (random.nextFloat() - 0.5F) * 2.0F * MAX_PITCH_DRIFT;
        this.pitch = Mth.lerp(0.4F, this.pitch, 1.0F + pitchDrift);
    }

    private boolean shouldSilence() {
        return emitter.isRemoved() || !emitter.isAlive() || !emitter.hasBattery() || emitter.getChargeTicks() <= 0;
    }

    public boolean shouldRemove() {
        return isStopped();
    }

    public void stopSound() {
        stop();
    }
}
