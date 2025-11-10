package com.fullfud.fullfud.core.network.handler;

import com.fullfud.fullfud.client.ShahedClientHandler;
import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.common.item.FpvGogglesItem;
import com.fullfud.fullfud.common.item.MonitorItem;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.core.network.packet.FpvTogglePacket;
import com.fullfud.fullfud.core.network.packet.ShahedControlPacket;
import com.fullfud.fullfud.core.network.packet.ShahedLinkPacket;
import com.fullfud.fullfud.core.network.packet.ShahedStatusPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class ShahedNetworkHandlers {
    private ShahedNetworkHandlers() {
    }

    public static void handleControl(final ShahedControlPacket packet, final ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        final ServerLevel level = sender.serverLevel();
        boolean handled = ShahedDroneEntity.find(level, packet.droneId())
            .map(drone -> {
                drone.applyControl(packet, sender);
                return true;
            }).orElse(false);

        if (!handled) {
            FpvDroneEntity.find(level, packet.droneId())
                .ifPresent(drone -> drone.handleControlPacket(packet, sender));
        }
    }

    public static void handleStatus(final ShahedStatusPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ShahedClientHandler.handleStatusPacket(packet));
    }

    public static void handleLinkUpdate(final ShahedLinkPacket packet, final ServerPlayer sender) {
        if (sender != null) {
            
        }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ShahedClientHandler.handleLinkPacket(packet));
    }

    public static void handleFpvToggle(final FpvTogglePacket packet, final ServerPlayer sender) {
        if (sender == null || packet.droneId() == null) {
            return;
        }
        final ServerLevel level = sender.serverLevel();
        FpvDroneEntity.find(level, packet.droneId()).ifPresent(drone -> {
            if (!FpvGogglesItem.getLinkedDrone(sender).map(packet.droneId()::equals).orElse(false)) {
                sender.displayClientMessage(Component.translatable("message.fullfud.fpv.goggles.no_link"), true);
                return;
            }
            MonitorItem.openFpvMonitor(sender, packet.droneId());
        });
    }
}
