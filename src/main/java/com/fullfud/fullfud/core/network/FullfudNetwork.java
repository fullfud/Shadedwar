package com.fullfud.fullfud.core.network;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.core.network.packet.FpvControlPacket;
import com.fullfud.fullfud.core.network.packet.FpvReleasePacket;
import com.fullfud.fullfud.core.network.packet.RemoteAvatarVisibilityPacket;
import com.fullfud.fullfud.core.network.packet.ShahedControlPacket;
import com.fullfud.fullfud.core.network.packet.ShahedLinkPacket;
import com.fullfud.fullfud.core.network.packet.ShahedStatusPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class FullfudNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static SimpleChannel channel;
    private static int packetId = 0;

    private FullfudNetwork() {
    }

    public static void init() {
        if (channel != null) {
            return;
        }

        channel = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FullfudMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
        );

        registerPackets();
    }

    public static SimpleChannel getChannel() {
        if (channel == null) {
            throw new IllegalStateException("Network channel accessed before initialization");
        }
        return channel;
    }

    private static void registerPackets() {
        channel.registerMessage(nextId(), ShahedControlPacket.class, ShahedControlPacket::encode, ShahedControlPacket::decode, ShahedControlPacket::handle);
        channel.registerMessage(nextId(), ShahedStatusPacket.class, ShahedStatusPacket::encode, ShahedStatusPacket::decode, ShahedStatusPacket::handle);
        channel.registerMessage(nextId(), ShahedLinkPacket.class, ShahedLinkPacket::encode, ShahedLinkPacket::decode, ShahedLinkPacket::handle);
        channel.registerMessage(nextId(), FpvControlPacket.class, FpvControlPacket::encode, FpvControlPacket::decode, FpvControlPacket::handle);
        channel.registerMessage(nextId(), FpvReleasePacket.class, FpvReleasePacket::encode, FpvReleasePacket::decode, FpvReleasePacket::handle);
        channel.registerMessage(nextId(), RemoteAvatarVisibilityPacket.class, RemoteAvatarVisibilityPacket::encode, RemoteAvatarVisibilityPacket::decode, RemoteAvatarVisibilityPacket::handle);
    }

    private static int nextId() {
        return packetId++;
    }
}
