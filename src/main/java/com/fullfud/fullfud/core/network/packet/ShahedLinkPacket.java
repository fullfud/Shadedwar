package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.core.network.handler.ShahedNetworkHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record ShahedLinkPacket(UUID droneId, boolean linked) {

    public static ShahedLinkPacket decode(final FriendlyByteBuf buffer) {
        final UUID droneId = buffer.readUUID();
        final boolean linked = buffer.readBoolean();
        return new ShahedLinkPacket(droneId, linked);
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeUUID(droneId);
        buffer.writeBoolean(linked);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        if (!context.getDirection().getReceptionSide().isClient()) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> ShahedNetworkHandlers.handleLinkUpdate(this, context.getSender()));
        context.setPacketHandled(true);
    }
}
