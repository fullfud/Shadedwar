package dev.lazurite.lattice.api.util;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

public final class ChunkPosUtil {

    public static ChunkPos of(double x, double z) {
        return new ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
    }

    public static ChunkPos of(Entity entity) {
        return entity.chunkPosition();
    }

    private ChunkPosUtil() { }
}
