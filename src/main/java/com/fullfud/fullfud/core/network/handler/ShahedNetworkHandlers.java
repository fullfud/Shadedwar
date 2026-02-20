package com.fullfud.fullfud.core.network.handler;

import com.fullfud.fullfud.client.ShahedClientHandler;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.core.network.PacketRateLimiter;
import com.fullfud.fullfud.core.network.packet.ShahedControlPacket;
import com.fullfud.fullfud.core.network.packet.ShahedGhostUpdatePacket;
import com.fullfud.fullfud.core.network.packet.ShahedLinkPacket;
import com.fullfud.fullfud.core.network.packet.ShahedStatusPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class ShahedNetworkHandlers {
    private ShahedNetworkHandlers() {
    }

    public static void handleControl(final ShahedControlPacket packet, final ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        if (!PacketRateLimiter.allow(sender, "shahed_control", 4, 80)) {
            return;
        }
        final ServerLevel level = sender.serverLevel();
        ShahedDroneEntity.find(level, packet.droneId())
            .ifPresent(drone -> drone.applyControl(packet, sender));
    }

    public static void handleStatus(final ShahedStatusPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ShahedClientHandler.handleStatusPacket(packet));
    }

    public static void handleGhostUpdate(final ShahedGhostUpdatePacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ShahedClientHandler.handleGhostPacket(packet));
    }

    public static void handleLinkUpdate(final ShahedLinkPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ShahedClientHandler.handleLinkPacket(packet));
    }
}
