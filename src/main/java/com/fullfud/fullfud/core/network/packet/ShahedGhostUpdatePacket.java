package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.core.network.handler.ShahedNetworkHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record ShahedGhostUpdatePacket(UUID droneId,
                                      double x,
                                      double y,
                                      double z,
                                      double velocityX,
                                      double velocityY,
                                      double velocityZ,
                                      float yaw,
                                      float pitch,
                                      float roll,
                                      float thrust,
                                      int colorId,
                                      boolean onLauncher) {

    public static ShahedGhostUpdatePacket decode(final FriendlyByteBuf buffer) {
        final UUID droneId = buffer.readUUID();
        final double x = buffer.readDouble();
        final double y = buffer.readDouble();
        final double z = buffer.readDouble();
        final double velocityX = buffer.readDouble();
        final double velocityY = buffer.readDouble();
        final double velocityZ = buffer.readDouble();
        final float yaw = buffer.readFloat();
        final float pitch = buffer.readFloat();
        final float roll = buffer.readFloat();
        final float thrust = buffer.readFloat();
        final int colorId = buffer.readVarInt();
        final boolean onLauncher = buffer.readBoolean();
        return new ShahedGhostUpdatePacket(droneId, x, y, z, velocityX, velocityY, velocityZ, yaw, pitch, roll, thrust, colorId, onLauncher);
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeUUID(droneId);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeDouble(velocityX);
        buffer.writeDouble(velocityY);
        buffer.writeDouble(velocityZ);
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);
        buffer.writeFloat(roll);
        buffer.writeFloat(thrust);
        buffer.writeVarInt(colorId);
        buffer.writeBoolean(onLauncher);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        if (!context.getDirection().getReceptionSide().isClient()) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> ShahedNetworkHandlers.handleGhostUpdate(this));
        context.setPacketHandled(true);
    }
}
