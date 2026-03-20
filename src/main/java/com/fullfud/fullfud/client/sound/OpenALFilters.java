package com.fullfud.fullfud.client.sound;

import com.fullfud.fullfud.mixin.client.ChannelAccessor;
import com.fullfud.fullfud.mixin.client.SoundEngineAccessor;
import com.fullfud.fullfud.mixin.client.SoundManagerAccessor;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

@OnlyIn(Dist.CLIENT)
public final class OpenALFilters {
    private static boolean initialized;
    private static boolean efxAvailable;
    private static int lowPassFilter;

    private OpenALFilters() {
    }

    public static boolean isAvailable() {
        ensureInitialized();
        return efxAvailable;
    }

    public static void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            final long currentContext = ALC10.alcGetCurrentContext();
            if (currentContext == 0L) {
                return;
            }
            final long currentDevice = ALC10.alcGetContextsDevice(currentContext);
            if (currentDevice == 0L) {
                return;
            }
            if (!ALC10.alcIsExtensionPresent(currentDevice, "ALC_EXT_EFX")) {
                return;
            }
            lowPassFilter = EXTEfx.alGenFilters();
            if (lowPassFilter == 0) {
                return;
            }
            EXTEfx.alFilteri(lowPassFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            EXTEfx.alFilterf(lowPassFilter, EXTEfx.AL_LOWPASS_GAIN, 1.0F);
            EXTEfx.alFilterf(lowPassFilter, EXTEfx.AL_LOWPASS_GAINHF, 1.0F);
            efxAvailable = true;
        } catch (Exception ignored) {
            efxAvailable = false;
        }
    }

    public static void applyToSource(final int sourceId, final float gain, final float gainHF) {
        if (!efxAvailable || sourceId == 0) {
            return;
        }
        EXTEfx.alFilterf(lowPassFilter, EXTEfx.AL_LOWPASS_GAIN, clamp01(gain));
        EXTEfx.alFilterf(lowPassFilter, EXTEfx.AL_LOWPASS_GAINHF, clamp01(gainHF));
        AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, lowPassFilter);
    }

    public static void removeFromSource(final int sourceId) {
        if (!efxAvailable || sourceId == 0) {
            return;
        }
        AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, 0);
    }

    public static void applyFilterForInstance(final SoundInstance instance, final float gain, final float gainHF) {
        if (!efxAvailable) {
            return;
        }
        final ChannelAccess.ChannelHandle handle = getChannelHandle(instance);
        if (handle == null) {
            return;
        }
        final float clampedGain = clamp01(gain);
        final float clampedGainHF = clamp01(gainHF);
        handle.execute(channel -> {
            final int sourceId = ((ChannelAccessor) channel).fullfud$getSource();
            applyToSource(sourceId, clampedGain, clampedGainHF);
        });
    }

    public static void removeFilterForInstance(final SoundInstance instance) {
        if (!efxAvailable) {
            return;
        }
        final ChannelAccess.ChannelHandle handle = getChannelHandle(instance);
        if (handle == null) {
            return;
        }
        handle.execute(channel -> {
            final int sourceId = ((ChannelAccessor) channel).fullfud$getSource();
            removeFromSource(sourceId);
        });
    }

    private static ChannelAccess.ChannelHandle getChannelHandle(final SoundInstance instance) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        final SoundManager soundManager = minecraft.getSoundManager();
        final SoundEngine soundEngine = ((SoundManagerAccessor) soundManager).fullfud$getSoundEngine();
        final Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel =
            ((SoundEngineAccessor) soundEngine).fullfud$getInstanceToChannel();
        return instanceToChannel.get(instance);
    }

    private static float clamp01(final float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
