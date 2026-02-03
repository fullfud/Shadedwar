package dev.lazurite.lattice.impl.network;

import dev.lazurite.lattice.api.point.ViewPoint;
import dev.lazurite.lattice.impl.client.LatticeClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class SetViewPointPacket {

    private final boolean isEntity;
    private final int entityId;

    public SetViewPointPacket(boolean isEntity, int entityId) {
        this.isEntity = isEntity;
        this.entityId = entityId;
    }

    public static SetViewPointPacket fromViewPoint(ViewPoint viewPoint) {
        if (viewPoint instanceof Entity entity) {
            return new SetViewPointPacket(true, entity.getId());
        }
        return new SetViewPointPacket(false, -1);
    }

    public static void encode(SetViewPointPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isEntity);
        if (msg.isEntity) {
            buf.writeVarInt(msg.entityId);
        }
    }

    public static SetViewPointPacket decode(FriendlyByteBuf buf) {
        boolean isEntity = buf.readBoolean();
        int entityId = isEntity ? buf.readVarInt() : -1;
        return new SetViewPointPacket(isEntity, entityId);
    }

    public static void handle(SetViewPointPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> LatticeClient.handleSetViewPointPacket(msg)));
        ctx.setPacketHandled(true);
    }

    public boolean isEntity() {
        return isEntity;
    }

    public int getEntityId() {
        return entityId;
    }
}
