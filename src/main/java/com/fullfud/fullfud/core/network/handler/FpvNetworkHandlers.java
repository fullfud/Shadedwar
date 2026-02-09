package com.fullfud.fullfud.core.network.handler;

import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.core.network.PacketRateLimiter;
import com.fullfud.fullfud.core.network.packet.FpvControlPacket;
import com.fullfud.fullfud.core.network.packet.FpvReleasePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class FpvNetworkHandlers {
    private FpvNetworkHandlers() {
    }

    public static void handleControl(final FpvControlPacket packet, final ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        // Soft limit: keep enough headroom for normal/bursty control while blocking packet floods.
        if (!PacketRateLimiter.allow(sender, "fpv_control", 12, 240)) {
            return;
        }
        final ServerLevel level = sender.serverLevel();
        if (level == null) {
            return;
        }
        final var entity = level.getEntity(packet.droneId());
        if (entity instanceof FpvDroneEntity drone) {
            drone.queueControl(packet, sender);
        }
    }

    public static void handleRelease(final FpvReleasePacket packet, final ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        if (!PacketRateLimiter.allow(sender, "fpv_release", 2, 20)) {
            return;
        }
        final ServerLevel level = sender.serverLevel();
        if (level == null) {
            return;
        }
        final var entity = level.getEntity(packet.droneId());
        if (entity instanceof FpvDroneEntity drone) {
            drone.requestRelease(sender);
        }
    }
}
