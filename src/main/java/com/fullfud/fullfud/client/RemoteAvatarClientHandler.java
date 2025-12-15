package com.fullfud.fullfud.client;

import com.fullfud.fullfud.core.network.packet.RemoteAvatarVisibilityPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class RemoteAvatarClientHandler {
    private static final Set<UUID> HIDDEN_AVATARS = new HashSet<>();

    private RemoteAvatarClientHandler() { }

    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> MinecraftForge.EVENT_BUS.addListener(RemoteAvatarClientHandler::onRenderPlayer));
    }

    public static void handleVisibility(final RemoteAvatarVisibilityPacket packet) {
        if (packet == null || packet.avatarId() == null) {
            return;
        }
        if (packet.hidden()) {
            HIDDEN_AVATARS.add(packet.avatarId());
        } else {
            HIDDEN_AVATARS.remove(packet.avatarId());
        }
    }

    private static void onRenderPlayer(final RenderPlayerEvent.Pre event) {
        if (event == null || event.getEntity() == null) {
            return;
        }
        if (HIDDEN_AVATARS.contains(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }
}

