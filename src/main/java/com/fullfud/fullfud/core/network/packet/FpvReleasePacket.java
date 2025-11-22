package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.core.network.handler.FpvNetworkHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record FpvReleasePacket(UUID droneId) {
    public static FpvReleasePacket decode(final FriendlyByteBuf buffer) {
        return new FpvReleasePacket(buffer.readUUID());
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeUUID(droneId);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> FpvNetworkHandlers.handleRelease(this, context.getSender()));
        context.setPacketHandled(true);
    }
}
