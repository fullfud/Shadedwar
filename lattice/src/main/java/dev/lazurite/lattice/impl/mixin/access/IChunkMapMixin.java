package dev.lazurite.lattice.impl.mixin.access;

import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface IChunkMapMixin {
    @Accessor int getViewDistance();
}
