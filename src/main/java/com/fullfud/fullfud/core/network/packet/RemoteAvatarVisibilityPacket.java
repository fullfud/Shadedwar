package com.fullfud.fullfud.core.network.packet;

import com.fullfud.fullfud.core.network.handler.RemoteAvatarNetworkHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record RemoteAvatarVisibilityPacket(UUID avatarId, boolean hidden) {

    public static RemoteAvatarVisibilityPacket decode(final FriendlyByteBuf buffer) {
        final UUID avatarId = buffer.readUUID();
        final boolean hidden = buffer.readBoolean();
        return new RemoteAvatarVisibilityPacket(avatarId, hidden);
    }

    public void encode(final FriendlyByteBuf buffer) {
        buffer.writeUUID(avatarId);
        buffer.writeBoolean(hidden);
    }

    public void handle(final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        if (!context.getDirection().getReceptionSide().isClient()) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> RemoteAvatarNetworkHandlers.handleVisibility(this));
        context.setPacketHandled(true);
    }
}

