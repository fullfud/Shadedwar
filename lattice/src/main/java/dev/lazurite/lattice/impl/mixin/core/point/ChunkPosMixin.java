package dev.lazurite.lattice.impl.mixin.core.point;

import dev.lazurite.lattice.api.point.Point2D;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkPos.class)
public abstract class ChunkPosMixin implements Point2D {

    @Shadow @Final public int x;
    @Shadow @Final public int z;

    @Override
    public double getX() {
        return this.x;
    }

    @Override
    public double getZ() {
        return this.z;
    }

}
