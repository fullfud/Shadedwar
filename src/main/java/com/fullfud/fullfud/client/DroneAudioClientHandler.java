package com.fullfud.fullfud.client;

import com.fullfud.fullfud.client.sound.RemoteDroneLoopSoundInstance;
import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.config.FullfudClientConfig;
import com.fullfud.fullfud.core.network.packet.DroneAudioLoopPacket;
import com.fullfud.fullfud.core.network.packet.DroneAudioOneShotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class DroneAudioClientHandler {
    public static final byte TYPE_FPV = 0;
    public static final byte TYPE_SHAHED = 1;

    public static final byte KIND_START = 1;
    public static final byte KIND_STOP = 2;

    private static final Map<UUID, RemoteDroneLoopSoundInstance> LOOPS = new HashMap<>();

    private DroneAudioClientHandler() { }

    public static void handleLoop(final DroneAudioLoopPacket packet) {
        if (packet == null || packet.droneId() == null) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            clearLoops();
            return;
        }
        if (!FullfudClientConfig.CLIENT.droneAudioRemoteEnabled.get()) {
            clearLoops();
            return;
        }
        cleanupStoppedLoops();
        if (shouldMuteForLoadedEntity(mc, packet.droneType(), packet.droneId())) {
            stopLoop(packet.droneId());
            return;
        }

        if (!packet.active()) {
            stopLoop(packet.droneId());
            return;
        }

        final SoundEvent event = switch (packet.droneType()) {
            case TYPE_FPV -> FullfudRegistries.FPV_ENGINE_LOOP.get();
            case TYPE_SHAHED -> FullfudRegistries.SHAHED_ENGINE_LOOP.get();
            default -> null;
        };
        if (event == null) {
            return;
        }

        final long tick = mc.level.getGameTime();
        final double volumeScale = FullfudClientConfig.CLIENT.droneAudioRemoteVolumeScale.get();
        final float scaledVolume = (float) Mth.clamp(packet.volume() * volumeScale, 0.0D, 10.0D);
        RemoteDroneLoopSoundInstance instance = LOOPS.get(packet.droneId());
        if (instance == null || instance.isStopped()) {
            instance = new RemoteDroneLoopSoundInstance(event);
            LOOPS.put(packet.droneId(), instance);
            mc.getSoundManager().play(instance);
        }
        instance.update(packet.x(), packet.y(), packet.z(), scaledVolume, packet.pitch(), tick);
    }

    public static void handleOneShot(final DroneAudioOneShotPacket packet) {
        if (packet == null) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            clearLoops();
            return;
        }
        if (!FullfudClientConfig.CLIENT.droneAudioRemoteEnabled.get()) {
            clearLoops();
            return;
        }
        cleanupStoppedLoops();
        if (shouldMuteForLoadedEntity(mc, packet.droneType(), packet.droneId())) {
            stopLoop(packet.droneId());
            return;
        }
        final SoundEvent event = resolveOneShot(packet.droneType(), packet.soundKind());
        if (event == null) {
            return;
        }
        final double volumeScale = FullfudClientConfig.CLIENT.droneAudioRemoteVolumeScale.get();
        final float scaledVolume = (float) Mth.clamp(packet.volume() * volumeScale, 0.0D, 10.0D);
        mc.level.playLocalSound(packet.x(), packet.y(), packet.z(), event, SoundSource.NEUTRAL, scaledVolume, packet.pitch(), false);
    }

    private static SoundEvent resolveOneShot(final byte droneType, final byte kind) {
        if (droneType == TYPE_FPV) {
            return switch (kind) {
                case KIND_START -> FullfudRegistries.FPV_ENGINE_START.get();
                case KIND_STOP -> FullfudRegistries.FPV_ENGINE_STOP.get();
                default -> null;
            };
        }
        if (droneType == TYPE_SHAHED) {
            return switch (kind) {
                case KIND_START -> FullfudRegistries.SHAHED_ENGINE_START.get();
                case KIND_STOP -> FullfudRegistries.SHAHED_ENGINE_END.get();
                default -> null;
            };
        }
        return null;
    }

    private static boolean isDroneEntityPresent(final Minecraft mc, final UUID droneId) {
        if (mc == null || mc.level == null || droneId == null) {
            return false;
        }
        for (final var entity : mc.level.entitiesForRendering()) {
            if (entity == null) {
                continue;
            }
            if (!droneId.equals(entity.getUUID())) {
                continue;
            }
            return (entity instanceof FpvDroneEntity) || (entity instanceof ShahedDroneEntity);
        }
        return false;
    }

    private static boolean shouldMuteForLoadedEntity(final Minecraft mc, final byte droneType, final UUID droneId) {
        if (droneId == null) {
            return false;
        }
        final boolean muteWhenLoaded = switch (droneType) {
            case TYPE_FPV -> FullfudClientConfig.CLIENT.droneAudioMuteRemoteFpvWhenLoaded.get();
            case TYPE_SHAHED -> FullfudClientConfig.CLIENT.droneAudioMuteRemoteShahedWhenLoaded.get();
            default -> false;
        };
        return muteWhenLoaded && isDroneEntityPresent(mc, droneId);
    }

    private static void stopLoop(final UUID droneId) {
        if (droneId == null) {
            return;
        }
        final RemoteDroneLoopSoundInstance existing = LOOPS.remove(droneId);
        if (existing != null) {
            existing.forceStop();
        }
    }

    private static void cleanupStoppedLoops() {
        LOOPS.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isStopped());
    }

    private static void clearLoops() {
        for (final RemoteDroneLoopSoundInstance loop : LOOPS.values()) {
            if (loop != null) {
                loop.forceStop();
            }
        }
        LOOPS.clear();
    }
}
