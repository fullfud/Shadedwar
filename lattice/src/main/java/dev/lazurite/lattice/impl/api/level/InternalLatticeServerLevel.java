package dev.lazurite.lattice.impl.api.level;

import dev.lazurite.lattice.api.level.LatticeServerLevel;
import dev.lazurite.lattice.impl.api.ChunkPosSupplierWrapper;
import net.minecraft.server.level.ServerPlayer;

public interface InternalLatticeServerLevel extends LatticeServerLevel {
    void registerPlayer(final ServerPlayer serverPlayer);
    void unregisterPlayer(final ServerPlayer serverPlayer);

    ChunkPosSupplierWrapper getChunkPosSupplierWrapper(final ServerPlayer serverPlayer);
    ChunkPosSupplierWrapper getViewpointChunkPosSupplierWrapper(final ServerPlayer serverPlayer);
}
