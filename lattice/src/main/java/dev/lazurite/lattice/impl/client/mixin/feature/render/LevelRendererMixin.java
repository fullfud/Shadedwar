package dev.lazurite.lattice.impl.client.mixin.feature.render;

import dev.lazurite.lattice.api.player.LatticePlayer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    /**
     * Allows for the rendering of the {@link LocalPlayer}.
     */
    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;isDetached()Z"
            )
    )
    public boolean renderLevel(Camera camera) {
        final var latticePlayer = (LatticePlayer) Minecraft.getInstance().player;
        return camera.isDetached() || latticePlayer.getViewPoint().shouldRenderPlayer();
    }

}
