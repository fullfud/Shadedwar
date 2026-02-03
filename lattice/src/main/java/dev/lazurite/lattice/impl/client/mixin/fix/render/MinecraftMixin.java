package dev.lazurite.lattice.impl.client.mixin.fix.render;

import dev.lazurite.lattice.api.player.LatticePlayer;
import dev.lazurite.lattice.api.point.ViewPoint;
import dev.lazurite.lattice.api.util.BlockPosUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fixes some hard-coded {@link LocalPlayer} positions to use {@link ViewPoint} instead.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    /**
     * Uses the {@link ViewPoint}'s position instead of the {@link LocalPlayer}'s position.
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getBlockX()I"
            )
    )
    public final int tick_getBlockX(LocalPlayer player) {
        return BlockPosUtil.posToBlockCoord(((LatticePlayer) player).getViewPoint().getX());
    }

    /**
     * Uses the {@link ViewPoint}'s position instead of the {@link LocalPlayer}'s position.
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getBlockY()I"
            )
    )
    public final int tick_getBlockY(LocalPlayer player) {
        return BlockPosUtil.posToBlockCoord(((LatticePlayer) player).getViewPoint().getY());
    }

    /**
     *
     * Uses the {@link ViewPoint}'s position instead of the {@link LocalPlayer}'s position.
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getBlockZ()I"
            )
    )
    public final int tick_getBlockZ(LocalPlayer player) {
        return BlockPosUtil.posToBlockCoord(((LatticePlayer) player).getViewPoint().getZ());
    }

}
