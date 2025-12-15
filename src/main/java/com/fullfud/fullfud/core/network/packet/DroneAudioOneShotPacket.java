package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.core.network.handler.DroneAudioNetworkHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record DroneAudioOneShotPacket(byte droneType,
                                     byte soundKind,
                                     UUID droneId,
                                     double x,
                                     double y,
                                     double z,
                                     float volume,
                                     float pitch) {

    public static DroneAudioOneShotPacket decode(final FriendlyByteBuf buffer) {
        final byte type = buffer.readByte();
        final byte kind = buffer.readByte();
        final UUID id = buffer.readUUID();
        final double x = buffer.readDouble();
        final double y = buffer.readDouble();
        final double z = buffer.readDouble();
        final float volume = buffer.readFloat();
        final float pitch = buffer.readFloat();
        return new DroneAudioOneShotPacket(type, kind, id, x, y, z, volume, pitch);
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeByte(droneType);
        buffer.writeByte(soundKind);
        buffer.writeUUID(droneId);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeFloat(volume);
        buffer.writeFloat(pitch);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        if (!context.getDirection().getReceptionSide().isClient()) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> DroneAudioNetworkHandlers.handleOneShot(this));
        context.setPacketHandled(true);
    }
}

