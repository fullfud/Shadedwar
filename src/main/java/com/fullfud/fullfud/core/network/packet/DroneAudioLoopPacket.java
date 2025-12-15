package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.core.network.handler.DroneAudioNetworkHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record DroneAudioLoopPacket(byte droneType,
                                  UUID droneId,
                                  double x,
                                  double y,
                                  double z,
                                  float volume,
                                  float pitch,
                                  boolean active) {

    public static DroneAudioLoopPacket decode(final FriendlyByteBuf buffer) {
        final byte type = buffer.readByte();
        final UUID id = buffer.readUUID();
        final double x = buffer.readDouble();
        final double y = buffer.readDouble();
        final double z = buffer.readDouble();
        final float volume = buffer.readFloat();
        final float pitch = buffer.readFloat();
        final boolean active = buffer.readBoolean();
        return new DroneAudioLoopPacket(type, id, x, y, z, volume, pitch, active);
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeByte(droneType);
        buffer.writeUUID(droneId);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeFloat(volume);
        buffer.writeFloat(pitch);
        buffer.writeBoolean(active);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        if (!context.getDirection().getReceptionSide().isClient()) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> DroneAudioNetworkHandlers.handleLoop(this));
        context.setPacketHandled(true);
    }
}

