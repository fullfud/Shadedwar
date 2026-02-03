package dev.lazurite.lattice.impl.mixin.core.point;

import dev.lazurite.lattice.api.point.Point3D;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockPos.class)
@Implements(@Interface(iface = Point3D.class, prefix = "p3d$"))
public abstract class BlockPosMixin extends Vec3i {

    public BlockPosMixin(int i, int j, int k) {
        super(i, j, k);
    }

    @Intrinsic
    public double p3d$getX() {
        return this.getX();
    }

    @Intrinsic
    public double p3d$getZ() {
        return this.getZ();
    }

    @Intrinsic
    public double p3d$getY() {
        return this.getZ();
    }

}
