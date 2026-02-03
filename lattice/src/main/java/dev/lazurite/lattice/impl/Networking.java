package dev.lazurite.lattice.impl;

import dev.lazurite.lattice.api.point.ViewPoint;
import dev.lazurite.lattice.impl.network.SetViewPointPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Networking {

    public static final ResourceLocation SET_VIEWPOINT_PACKET_IDENTIFIER =
        ResourceLocation.fromNamespaceAndPath("lattice", "set_viewpoint");
    private static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath("lattice", "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    public static void init() {
        int index = 0;
        CHANNEL.registerMessage(
            index++,
            SetViewPointPacket.class,
            SetViewPointPacket::encode,
            SetViewPointPacket::decode,
            SetViewPointPacket::handle
        );
    }

    public static void sendSetViewPointPacket(final ServerPlayer serverPlayer, final ViewPoint viewPoint) {
        CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> serverPlayer),
            SetViewPointPacket.fromViewPoint(viewPoint)
        );
    }

    private Networking() { }
}
