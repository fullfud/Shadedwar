package com.fullfud.fullfud.client.sound;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DroneSoundEffects {
    private static final double MIN_ATTEN_RATIO = 0.075D;
    private static final double SPEED_OF_SOUND = 17.0D;
    private static final double ALTITUDE_THRESHOLD = 50.0D;
    private static final double ALTITUDE_MAX = 200.0D;
    private static final float ALTITUDE_MIN_GAIN_HF = 0.3F;

    private DroneSoundEffects() {
    }

    public static float computeDistanceGainHF(final double distance, final double maxAudibleDistance) {
        if (maxAudibleDistance <= 0.0D) {
            return 1.0F;
        }
        final double minAtten = maxAudibleDistance * MIN_ATTEN_RATIO;
        double attenRange = maxAudibleDistance - minAtten;
        if (attenRange <= 0.0D) {
            attenRange = 1.0D;
        }
        if (distance <= minAtten) {
            return 1.0F;
        }
        if (distance >= maxAudibleDistance) {
            return 0.03F;
        }
        final double normalized = (distance - minAtten) / attenRange;
        return (float) Math.exp(-normalized);
    }

    public static float computeDopplerPitch(final Vec3 dronePos, final Vec3 droneVelocity, final Vec3 playerPos) {
        final Vec3 toPlayer = playerPos.subtract(dronePos);
        final double distance = toPlayer.length();
        if (distance < 0.01D) {
            return 1.0F;
        }
        final Vec3 dirToPlayer = toPlayer.scale(1.0D / distance);
        final double radialVelocity = droneVelocity.dot(dirToPlayer);
        final double denominator = SPEED_OF_SOUND - radialVelocity;
        if (denominator <= 0.1D) {
            return 2.0F;
        }
        final float dopplerPitch = (float) (SPEED_OF_SOUND / denominator);
        return Mth.clamp(dopplerPitch, 0.5F, 2.0F);
    }

    public static float computeAltitudeGainHF(final double droneY, final double playerY) {
        final double altitudeDiff = Math.abs(droneY - playerY);
        if (altitudeDiff <= ALTITUDE_THRESHOLD) {
            return 1.0F;
        }
        if (altitudeDiff >= ALTITUDE_MAX) {
            return ALTITUDE_MIN_GAIN_HF;
        }
        final double normalized = (altitudeDiff - ALTITUDE_THRESHOLD) / (ALTITUDE_MAX - ALTITUDE_THRESHOLD);
        return Mth.lerp((float) normalized, 1.0F, ALTITUDE_MIN_GAIN_HF);
    }

    public static float computeCombinedGainHF(
        final double distance,
        final double droneY,
        final double playerY,
        final double maxAudibleDistance
    ) {
        final float distanceGainHF = computeDistanceGainHF(distance, maxAudibleDistance);
        final float altitudeGainHF = computeAltitudeGainHF(droneY, playerY);
        return distanceGainHF * altitudeGainHF;
    }

    public static float computeDistanceVolumeFactor(final double distance, final double maxAudibleDistance) {
        if (maxAudibleDistance <= 0.0D) {
            return 1.0F;
        }
        final double minAtten = maxAudibleDistance * MIN_ATTEN_RATIO;
        double attenRange = maxAudibleDistance - minAtten;
        if (attenRange <= 0.0D) {
            attenRange = 1.0D;
        }
        if (distance <= minAtten) {
            return 1.0F;
        }
        if (distance >= maxAudibleDistance) {
            return 0.0F;
        }
        final float invDistance = (float) (minAtten / distance);
        final double normalized = (distance - minAtten) / attenRange;
        final float edgeFade = (float) (1.0D - normalized * normalized);
        return invDistance * edgeFade;
    }
}
