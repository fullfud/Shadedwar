package dev.lazurite.lattice.api.point;

import dev.lazurite.lattice.api.supplier.ChunkPosSupplier;
import dev.lazurite.lattice.api.util.ChunkPosUtil;
import net.minecraft.world.level.ChunkPos;

public interface Point2D extends ChunkPosSupplier {
    double getX();
    double getZ();

    @Override
    default ChunkPos getChunkPos() {
        return ChunkPosUtil.of(this.getX(), this.getZ());
    }

}
