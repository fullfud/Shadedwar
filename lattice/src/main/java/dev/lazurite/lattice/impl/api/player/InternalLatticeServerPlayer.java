package dev.lazurite.lattice.impl.api.player;

import dev.lazurite.lattice.api.player.LatticeServerPlayer;
import dev.lazurite.lattice.impl.api.ChunkPosSupplierWrapper;

public interface InternalLatticeServerPlayer extends LatticeServerPlayer {
    ChunkPosSupplierWrapper getChunkPosSupplierWrapper();
    ChunkPosSupplierWrapper getViewpointChunkPosSupplierWrapper();
}
