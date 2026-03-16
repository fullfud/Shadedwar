package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.core.network.handler.FpvNetworkHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record OpenFpvConfiguratorPacket(UUID droneId) {
    public static OpenFpvConfiguratorPacket decode(final FriendlyByteBuf buffer) {
        return new OpenFpvConfiguratorPacket(buffer.readUUID());
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeUUID(droneId);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        if (!context.getDirection().getReceptionSide().isClient()) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> FpvNetworkHandlers.handleOpenConfigurator(this));
        context.setPacketHandled(true);
    }
}
