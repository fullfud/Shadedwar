package dev.lazurite.lattice.api.util;

import net.minecraft.util.Mth;

public final class BlockPosUtil {

    public static int posToBlockCoord(double value) {
        return Mth.floor(value);
    }

    private BlockPosUtil() { }
}
