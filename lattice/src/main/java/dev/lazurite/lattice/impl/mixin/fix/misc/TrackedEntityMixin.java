package dev.lazurite.lattice.impl.mixin.fix.misc;

import dev.lazurite.lattice.api.player.LatticePlayer;
import dev.lazurite.lattice.api.point.ViewPoint;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin {

    @Shadow @Final Entity entity;

    /**
     * Returns the minimum of the original calculation (which uses the {@link ServerPlayer})
     * and the following (which uses the {@link ServerPlayer}'s {@link ViewPoint}).
     */
    @ModifyVariable(
            method = "updatePlayer",
            at = @At(
                    value = "STORE",
                    ordinal = 0
            ),
            ordinal = 1
    )
    public double updatePlayer_STORE(double e, ServerPlayer serverPlayer) {
        final var viewPoint = ((LatticePlayer) serverPlayer).getViewPoint();
        final var vec3 = new Vec3(viewPoint.getX(), viewPoint.getY(), viewPoint.getZ());
        final var position = vec3.subtract(this.entity.position());
        return Math.min(e, position.x * position.x + position.z * position.z);
    }

}
