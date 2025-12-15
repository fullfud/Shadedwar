package com.fullfud.fullfud.core.network.handler;

import com.fullfud.fullfud.client.DroneAudioClientHandler;
import com.fullfud.fullfud.core.network.packet.DroneAudioLoopPacket;
import com.fullfud.fullfud.core.network.packet.DroneAudioOneShotPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class DroneAudioNetworkHandlers {
    private DroneAudioNetworkHandlers() { }

    public static void handleLoop(final DroneAudioLoopPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DroneAudioClientHandler.handleLoop(packet));
    }

    public static void handleOneShot(final DroneAudioOneShotPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DroneAudioClientHandler.handleOneShot(packet));
    }
}

