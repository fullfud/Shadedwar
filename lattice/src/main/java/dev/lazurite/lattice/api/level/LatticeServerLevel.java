package dev.lazurite.lattice.api.level;

import dev.lazurite.lattice.api.supplier.ChunkPosSupplier;
import dev.lazurite.lattice.api.point.ViewPoint;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

public interface LatticeServerLevel {
    void register(final ChunkPosSupplier chunkPosSupplier);
    void unregister(final ChunkPosSupplier chunkPosSupplier);

    Set<ChunkPosSupplier> getAllChunkPosSuppliers();

    void bind(final ServerPlayer serverPlayer, final ViewPoint viewPoint);
    void unbind(final ServerPlayer serverPlayer);
    void unbindAll(final ViewPoint viewPoint);

    ViewPoint getViewPoint(final ServerPlayer serverPlayer);

    Set<ServerPlayer> getBoundPlayers(final ViewPoint viewPoint);
    Set<ServerPlayer> getAllBoundPlayers();
}
