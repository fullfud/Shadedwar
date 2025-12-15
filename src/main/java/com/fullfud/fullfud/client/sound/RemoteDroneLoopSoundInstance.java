package com.fullfud.fullfud.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RemoteDroneLoopSoundInstance extends AbstractTickableSoundInstance {
    private static final long TIMEOUT_TICKS = 40;

    private long lastUpdateTick = -1;
    private float targetVolume = 0.0F;
    private float targetPitch = 1.0F;

    public RemoteDroneLoopSoundInstance(final SoundEvent event) {
        super(event, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.attenuation = Attenuation.NONE;
        this.relative = false;
        this.volume = 0.01F;
        this.pitch = 1.0F;
    }

    public void update(final double x, final double y, final double z, final float volume, final float pitch, final long gameTick) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.targetVolume = Mth.clamp(volume, 0.0F, 10.0F);
        this.targetPitch = Mth.clamp(pitch, 0.2F, 4.0F);
        this.lastUpdateTick = gameTick;
    }

    public void forceStop() {
        stop();
    }

    @Override
    public void tick() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.isPaused()) {
            stop();
            return;
        }
        final long now = mc.level.getGameTime();
        if (lastUpdateTick >= 0 && now - lastUpdateTick > TIMEOUT_TICKS) {
            stop();
            return;
        }
        this.volume = Mth.lerp(0.35F, this.volume, targetVolume);
        this.pitch = Mth.lerp(0.25F, this.pitch, targetPitch);
        if (this.volume < 0.001F && targetVolume <= 0.0F) {
            stop();
        }
    }
}
