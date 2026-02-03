package dev.lazurite.lattice.impl.client.mixin.fix.chunk;

import dev.lazurite.lattice.api.util.ChunkPosUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicReferenceArray;

// TODO: More than two "regions"
/**
 * Modifies {@link ClientChunkCache.Storage} to allow for two chunk "regions."
 */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientChunkCache$Storage")
public abstract class StorageMixin {

    @Shadow @Final int chunkRadius;
    @Shadow @Final AtomicReferenceArray<LevelChunk> chunks;

    /**
     * Like {@link ClientChunkCache.Storage#inRange(int, int)} but uses the {@link LocalPlayer}'s {@link ChunkPos}
     * instead of {@link ClientChunkCache.Storage#viewCenterX} and {@link ClientChunkCache.Storage#viewCenterZ}.
     * @see ClientChunkCache.Storage#inRange(int, int)
     */
    private boolean inPlayerRange(final int i, final int j) {
        final var localPlayer = Minecraft.getInstance().player;
        assert localPlayer != null;

        ChunkPos chunkPos = ChunkPosUtil.of(localPlayer);
        return Math.abs(i - chunkPos.x) <= this.chunkRadius && Math.abs(j - chunkPos.z) <= this.chunkRadius;
    }

    /**
     * Doubles the size of the {@link ClientChunkCache.Storage#chunks}.
     * Allows for the storing of two chunk "regions."
     */
    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/atomic/AtomicReferenceArray;<init>(I)V"
            )
    )
    int init_init(int viewRangeSquared) {
        return viewRangeSquared * 2;
    }

    /**
     * Allows getting indicies for the second half of the {@link ClientChunkCache.Storage#chunks}.
     * @see StorageMixin#init_init(int)
     */
    @Inject(
            method = "getIndex",
            at = @At("TAIL"),
            cancellable = true
    )
    void getIndex_TAIL(int i, int j, CallbackInfoReturnable<Integer> cir) {
        if (!this.inPlayerRange(i, j)) {
            cir.setReturnValue(cir.getReturnValueI() + this.chunks.length() / 2);
        }
    }

    /**
     * Modifies {@link ClientChunkCache.Storage#inRange(int, int)} to use the {@link LocalPlayer}'s {@link ChunkPos}
     * as well as {@link ClientChunkCache.Storage#viewCenterX} and {@link ClientChunkCache.Storage#viewCenterZ}.
     * @see StorageMixin#inPlayerRange(int, int)
     * @see ClientChunkCache.Storage#inRange(int, int)
     */
    @Inject(
            method = "inRange",
            at = @At("RETURN"),
            cancellable = true
    )
    void inRange(int i, int j, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(cir.getReturnValueZ() || this.inPlayerRange(i, j));
    }

}
