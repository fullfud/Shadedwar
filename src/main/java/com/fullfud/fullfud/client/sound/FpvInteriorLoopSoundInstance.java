package com.fullfud.fullfud.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class FpvInteriorLoopSoundInstance extends AbstractTickableSoundInstance {
    private boolean shouldStop;
    private boolean terminated;
    private float thrust;

    public FpvInteriorLoopSoundInstance(final SoundEvent sound) {
        super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.volume = 0.2F;
        this.pitch = 1.0F;
    }

    public void update(final float thrust) {
        this.thrust = Mth.clamp(thrust, 0.0F, 1.0F);
        this.shouldStop = false;
    }

    public void markForStop() {
        this.shouldStop = true;
    }

    public void forceStop() {
        this.shouldStop = true;
        this.terminated = true;
        this.volume = 0.0F;
        stop();
    }

    public boolean isTerminated() {
        return terminated;
    }

    @Override
    public void tick() {
        if (shouldStop) {
            terminated = true;
            this.volume = 0.0F;
            stop();
            return;
        }
        this.volume = Mth.clamp(0.2F + thrust * 1.2F, 0.2F, 1.0F);
        this.pitch = Mth.clamp(0.8F + thrust * 0.6F, 0.8F, 1.4F);
    }
}
