package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.core.network.handler.ShahedNetworkHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record ShahedControlPacket(UUID droneId, float forward, float strafe, float vertical, float thrustDelta, boolean boost) {

    public static ShahedControlPacket decode(final FriendlyByteBuf buffer) {
        final UUID droneId = buffer.readUUID();
        final float forward = buffer.readFloat();
        final float strafe = buffer.readFloat();
        final float vertical = buffer.readFloat();
        final float thrustDelta = buffer.readFloat();
        final boolean boost = buffer.readBoolean();
        return new ShahedControlPacket(droneId, forward, strafe, vertical, thrustDelta, boost);
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeUUID(droneId);
        buffer.writeFloat(forward);
        buffer.writeFloat(strafe);
        buffer.writeFloat(vertical);
        buffer.writeFloat(thrustDelta);
        buffer.writeBoolean(boost);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ShahedNetworkHandlers.handleControl(this, context.getSender()));
        context.setPacketHandled(true);
    }
}
