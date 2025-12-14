package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.core.network.handler.FpvNetworkHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record FpvControlPacket(UUID droneId,
                               float pitchInput,
                               float rollInput,
                               float yawInput,
                               float throttle,
                               byte armAction) {

    public static FpvControlPacket decode(final FriendlyByteBuf buffer) {
        final UUID droneId = buffer.readUUID();
        final float pitch = buffer.readFloat();
        final float roll = buffer.readFloat();
        final float yaw = buffer.readFloat();
        final float throttle = buffer.readFloat();
        final byte arm = buffer.readByte();
        return new FpvControlPacket(droneId, pitch, roll, yaw, throttle, arm);
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeUUID(droneId);
        buffer.writeFloat(pitchInput);
        buffer.writeFloat(rollInput);
        buffer.writeFloat(yawInput);
        buffer.writeFloat(throttle);
        buffer.writeByte(armAction);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        if (context.getSender() == null || !context.getDirection().getReceptionSide().isServer()) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> FpvNetworkHandlers.handleControl(this, context.getSender()));
        context.setPacketHandled(true);
    }
}
