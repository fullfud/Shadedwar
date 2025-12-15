package com.fullfud.fullfud.core.network.handler;

import com.fullfud.fullfud.client.RemoteAvatarClientHandler;
import com.fullfud.fullfud.core.network.packet.RemoteAvatarVisibilityPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class RemoteAvatarNetworkHandlers {
    private RemoteAvatarNetworkHandlers() { }

    public static void handleVisibility(final RemoteAvatarVisibilityPacket packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RemoteAvatarClientHandler.handleVisibility(packet));
    }
}

