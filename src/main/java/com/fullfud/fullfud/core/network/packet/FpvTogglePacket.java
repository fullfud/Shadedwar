package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.common.item.MonitorItem;
import com.fullfud.fullfud.core.network.handler.ShahedNetworkHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record FpvTogglePacket(UUID droneId) {

    public static FpvTogglePacket decode(final FriendlyByteBuf buffer) {
        return new FpvTogglePacket(buffer.readUUID());
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeUUID(droneId);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            final ServerPlayer sender = context.getSender();
            if (sender == null || droneId == null) {
                return;
            }
            ShahedNetworkHandlers.handleFpvToggle(this, sender);
        });
        context.setPacketHandled(true);
    }
}
