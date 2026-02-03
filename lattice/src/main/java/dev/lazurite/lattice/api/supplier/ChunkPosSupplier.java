package dev.lazurite.lattice.api.supplier;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;

public interface ChunkPosSupplier {
    ChunkPos getChunkPos();

    /**
     * The distance to be loaded. Will be clamped by 0 and {@link ChunkMap#viewDistance}.
     */
    default int getDistance() {
        return 0;
    }

    default boolean isActive() {
        return true;
    }

}
