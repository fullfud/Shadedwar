package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.core.network.handler.ShahedNetworkHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record ShahedControlPacket(
    UUID droneId,
    float forward,
    float strafe,
    float vertical,
    float thrustDelta,
    float mousePitchDelta,
    float mouseRollDelta
) {

    public static ShahedControlPacket decode(final FriendlyByteBuf buffer) {
        final UUID droneId = buffer.readUUID();
        final float forward = buffer.readFloat();
        final float strafe = buffer.readFloat();
        final float vertical = buffer.readFloat();
        final float thrustDelta = buffer.readFloat();
        final float mousePitchDelta = buffer.readFloat();
        final float mouseRollDelta = buffer.readFloat();
        return new ShahedControlPacket(droneId, forward, strafe, vertical, thrustDelta, mousePitchDelta, mouseRollDelta);
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeUUID(droneId);
        buffer.writeFloat(forward);
        buffer.writeFloat(strafe);
        buffer.writeFloat(vertical);
        buffer.writeFloat(thrustDelta);
        buffer.writeFloat(mousePitchDelta);
        buffer.writeFloat(mouseRollDelta);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        if (context.getSender() == null || !context.getDirection().getReceptionSide().isServer()) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> ShahedNetworkHandlers.handleControl(this, context.getSender()));
        context.setPacketHandled(true);
    }
}
