package dev.lazurite.lattice.impl.mixin.fix.misc;

import dev.lazurite.lattice.api.player.LatticePlayer;
import dev.lazurite.lattice.api.point.ViewPoint;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * For sounds and "events."
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    private ServerPlayer serverPlayer;

    /**
     * Captures the {@link ServerPlayer} instance used in later comparisons. Not thread-safe.
     */
    @Inject(
            method = "broadcast",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Ljava/util/List;get(I)Ljava/lang/Object;",
                    shift = At.Shift.BY,
                    by = 2
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    public void broadcast(@Nullable Player player, double d, double e, double f, double g, ResourceKey<Level> resourceKey, Packet<?> packet, CallbackInfo ci, int i, ServerPlayer serverPlayer) {
        this.serverPlayer = serverPlayer;
    }

    /**
     * Returns the minimum of the original calculation (which uses the {@link ServerPlayer})
     * and the following (which uses the {@link ServerPlayer}'s {@link ViewPoint}).
     */
    @ModifyVariable(
            method = "broadcast",
            at = @At(
                    value = "STORE",
                    ordinal = 0
            ),
            ordinal = 4
    )
    public double broadcast_STORE0(double h, @Nullable Player player, double d) {
        return Math.min(h, d - ((LatticePlayer) this.serverPlayer).getViewPoint().getX());
    }

    /**
     * Returns the minimum of the original calculation (which uses the {@link ServerPlayer})
     * and the following (which uses the {@link ServerPlayer}'s {@link ViewPoint}).
     */
    @ModifyVariable(
            method = "broadcast",
            at = @At(
                    value = "STORE",
                    ordinal = 0
            ),
            ordinal = 5
    )
    public double broadcast_STORE1(double j, @Nullable Player player, double d, double e) {
        return Math.min(j, e - ((LatticePlayer) this.serverPlayer).getViewPoint().getY());
    }

    /**
     * Returns the minimum of the original calculation (which uses the {@link ServerPlayer})
     * and the following (which uses the {@link ServerPlayer}'s {@link ViewPoint}).
     */
    @ModifyVariable(
            method = "broadcast",
            at = @At(
                    value = "STORE",
                    ordinal = 0
            ),
            ordinal = 6
    )
    public double broadcast_STORE2(double k, @Nullable Player player, double d, double e, double f) {
        return Math.min(k, f - ((LatticePlayer) this.serverPlayer).getViewPoint().getZ());
    }

}
