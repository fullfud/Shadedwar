package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.core.network.handler.ShahedNetworkHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record ShahedStatusPacket(UUID droneId,
                                 double x,
                                 double y,
                                 double z,
                                 float yaw,
                                 float pitch,
                                 float thrust,
                                 float noiseLevel,
                                 boolean signalLost,
                                 float airSpeed,
                                 float groundSpeed,
                                 float verticalSpeed,
                                 float angleOfAttack,
                                 float slipAngle,
                                 float fuelKg,
                                 float airDensity) {

    public static ShahedStatusPacket decode(final FriendlyByteBuf buffer) {
        final UUID droneId = buffer.readUUID();
        final double x = buffer.readDouble();
        final double y = buffer.readDouble();
        final double z = buffer.readDouble();
        final float yaw = buffer.readFloat();
        final float pitch = buffer.readFloat();
        final float thrust = buffer.readFloat();
        final float noise = buffer.readFloat();
        final boolean signalLost = buffer.readBoolean();
        final float airSpeed = buffer.readFloat();
        final float groundSpeed = buffer.readFloat();
        final float verticalSpeed = buffer.readFloat();
        final float angleOfAttack = buffer.readFloat();
        final float slipAngle = buffer.readFloat();
        final float fuelKg = buffer.readFloat();
        final float airDensity = buffer.readFloat();
        return new ShahedStatusPacket(
            droneId,
            x,
            y,
            z,
            yaw,
            pitch,
            thrust,
            noise,
            signalLost,
            airSpeed,
            groundSpeed,
            verticalSpeed,
            angleOfAttack,
            slipAngle,
            fuelKg,
            airDensity
        );
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeUUID(droneId);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);
        buffer.writeFloat(thrust);
        buffer.writeFloat(noiseLevel);
        buffer.writeBoolean(signalLost);
        buffer.writeFloat(airSpeed);
        buffer.writeFloat(groundSpeed);
        buffer.writeFloat(verticalSpeed);
        buffer.writeFloat(angleOfAttack);
        buffer.writeFloat(slipAngle);
        buffer.writeFloat(fuelKg);
        buffer.writeFloat(airDensity);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ShahedNetworkHandlers.handleStatus(this));
        context.setPacketHandled(true);
    }
}
