package com.fullfud.fullfud.client.sound;

import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FpvEngineSoundInstance extends AbstractTickableSoundInstance {
    private final FpvDroneEntity drone;
    private static final float VOLUME_MULT = 0.2F;

    public FpvEngineSoundInstance(final FpvDroneEntity drone) {
        super(FullfudRegistries.FPV_ENGINE_LOOP.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.drone = drone;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.pitch = 1.0F;
        this.x = drone.getX();
        this.y = drone.getY();
        this.z = drone.getZ();
        this.attenuation = Attenuation.LINEAR;
    }

    @Override
    public void tick() {
        if (this.drone.isRemoved()) {
            this.stop();
            return;
        }

        this.x = this.drone.getX();
        this.y = this.drone.getY();
        this.z = this.drone.getZ();

        final float thrust = this.drone.getThrust();
        final boolean isArmed = this.drone.isArmed();

        float baseVolume;
        if (isArmed && thrust > 0.05F) {
            baseVolume = Mth.lerp(0.1F, this.volume / VOLUME_MULT, 1.0F);
            this.pitch = 0.8F + thrust * 0.7F;
        } else {
            baseVolume = Mth.lerp(0.15F, this.volume / VOLUME_MULT, 0.0F);
        }

        this.volume = baseVolume * VOLUME_MULT;

        if (baseVolume < 0.01F && !isArmed) {
            this.stop();
        }
    }
}
