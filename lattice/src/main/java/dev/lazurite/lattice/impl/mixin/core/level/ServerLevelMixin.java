package dev.lazurite.lattice.impl.mixin.core.level;

import dev.lazurite.lattice.api.supplier.ChunkPosSupplier;
import dev.lazurite.lattice.api.point.ViewPoint;
import dev.lazurite.lattice.impl.ChunkPosSupplierWrapperImpl;
import dev.lazurite.lattice.impl.api.ChunkPosSupplierWrapper;
import dev.lazurite.lattice.impl.api.level.InternalLatticeServerLevel;
import com.google.common.graph.ElementOrder;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Set;
import java.util.stream.Collectors;

// TODO: Verify this works.
//  ServerPlayer from a different ServerLevel should cause error.
//  Too many new objects being created.

@Mixin(ServerLevel.class)
@SuppressWarnings("UnstableApiUsage")
public abstract class ServerLevelMixin implements InternalLatticeServerLevel {

    final MutableGraph<ChunkPosSupplier> chunkPosSuppliers = GraphBuilder.directed()
            .allowsSelfLoops(true)
            .incidentEdgeOrder(ElementOrder.stable())
            .build();

    @Override
    public void register(final ChunkPosSupplier chunkPosSupplier) {
        if (chunkPosSupplier instanceof Player) return;

        final var chunkPosSupplierWrapper = new ChunkPosSupplierWrapperImpl(chunkPosSupplier, (ServerLevel) (Object) this);
        this.chunkPosSuppliers.addNode(chunkPosSupplierWrapper);
    }

    @Override
    public void unregister(final ChunkPosSupplier chunkPosSupplier) {
        if (chunkPosSupplier instanceof Player) return;

        final var chunkPosSupplierWrapper = new ChunkPosSupplierWrapperImpl(chunkPosSupplier, (ServerLevel) (Object) this);

        if (chunkPosSupplier instanceof ViewPoint) {
            // update any viewing ServerPlayer's edge to be self-referential
            this.chunkPosSuppliers.predecessors(chunkPosSupplierWrapper).forEach(_chunkPosSupplierWrapper -> {
                this.chunkPosSuppliers.putEdge(_chunkPosSupplierWrapper, _chunkPosSupplierWrapper);
            });
        }

        this.chunkPosSuppliers.removeNode(chunkPosSupplierWrapper); // also removes edges
    }

    @Override
    public Set<ChunkPosSupplier> getAllChunkPosSuppliers() {
        return this.chunkPosSuppliers.nodes();
    }

    @Override
    public void bind(final ServerPlayer serverPlayer, final ViewPoint viewPoint) {
        final var playerWrapper = this.getOrRegisterPlayerWrapper(serverPlayer);
        if (playerWrapper == null) {
            return;
        }

        this.unbind(serverPlayer);

        final var chunkPosSupplierWrapper = new ChunkPosSupplierWrapperImpl(viewPoint, (ServerLevel) (Object) this);
        this.chunkPosSuppliers.putEdge(playerWrapper, chunkPosSupplierWrapper);

        this.chunkPosSuppliers.removeEdge(playerWrapper, playerWrapper);
    }

    @Override
    public void unbind(final ServerPlayer serverPlayer) {
        final var chunkPosSupplierWrapper = new ChunkPosSupplierWrapperImpl((ChunkPosSupplier) serverPlayer, (ServerLevel) (Object) this);
        if (!this.chunkPosSuppliers.nodes().contains(chunkPosSupplierWrapper)) {
            return;
        }

        final var successors = new java.util.ArrayList<>(this.chunkPosSuppliers.successors(chunkPosSupplierWrapper));
        for (final var successor : successors) {
            if (successor.equals(chunkPosSupplierWrapper)) {
                continue;
            }
            this.chunkPosSuppliers.removeEdge(chunkPosSupplierWrapper, successor);

            if (successor instanceof final ChunkPosSupplierWrapper successorWrapper
                    && !(successorWrapper.getChunkPosSupplier() instanceof ServerPlayer)) {
                if (this.chunkPosSuppliers.inDegree(successorWrapper) == 0
                        && successorWrapper.getChunkPosSupplier() instanceof ViewPoint viewPoint
                        && viewPoint.unregistersWithNoViewers()) {
                    this.chunkPosSuppliers.removeNode(successorWrapper);
                }
            }
        }

        this.chunkPosSuppliers.putEdge(chunkPosSupplierWrapper, chunkPosSupplierWrapper);
    }

    @Override
    public void unbindAll(final ViewPoint viewPoint) {
        final var chunkPosSupplierWrapper = new ChunkPosSupplierWrapperImpl(viewPoint, (ServerLevel) (Object) this);

        this.chunkPosSuppliers.predecessors(chunkPosSupplierWrapper).forEach(_chunkPosSupplierWrapper -> {
            this.chunkPosSuppliers.removeEdge(_chunkPosSupplierWrapper, chunkPosSupplierWrapper);
            this.chunkPosSuppliers.putEdge(_chunkPosSupplierWrapper, _chunkPosSupplierWrapper);
        });

        if (viewPoint.unregistersWithNoViewers()) {
            this.chunkPosSuppliers.removeNode(chunkPosSupplierWrapper);
        }
    }

    @Override
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public ViewPoint getViewPoint(final ServerPlayer serverPlayer) {
        final var chunkPosSupplierWrapper = new ChunkPosSupplierWrapperImpl((ChunkPosSupplier) serverPlayer, (ServerLevel) (Object) this);
        if (!this.chunkPosSuppliers.nodes().contains(chunkPosSupplierWrapper)) {
            return (ViewPoint) serverPlayer;
        }
        final var successor = this.chunkPosSuppliers.successors(chunkPosSupplierWrapper).stream().findFirst().orElse(null);
        if (successor == null) {
            return (ViewPoint) serverPlayer;
        }
        return (ViewPoint) ((ChunkPosSupplierWrapper) successor).getChunkPosSupplier();
    }

    @Override
    public Set<ServerPlayer> getBoundPlayers(final ViewPoint viewPoint) {
        final var chunkPosSupplierWrapper = new ChunkPosSupplierWrapperImpl(viewPoint, (ServerLevel) (Object) this);
        return this.chunkPosSuppliers.predecessors(chunkPosSupplierWrapper).stream()
                .map(_chunkPosSupplierWrapper -> (ChunkPosSupplierWrapper) _chunkPosSupplierWrapper) // cast to ChunkPosSupplierWrapper
                .map(_chunkPosSupplierWrapper -> (ServerPlayer) _chunkPosSupplierWrapper.getChunkPosSupplier()) // cast to ServerPlayer
                .collect(Collectors.toSet()); // collect to Set
    }

    @Override
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public Set<ServerPlayer> getAllBoundPlayers() {
        return this.chunkPosSuppliers.nodes().stream()
                .map(chunkPosSupplierWrapper -> (ChunkPosSupplierWrapper) chunkPosSupplierWrapper) // cast to ChunkPosSupplierWrapper
                .filter(chunkPosSupplierWrapper -> chunkPosSupplierWrapper.getChunkPosSupplier() instanceof ServerPlayer) // get ServerPlayers
                .filter(chunkPosSupplierWrapper -> {
                    final var successor = this.chunkPosSuppliers.successors(chunkPosSupplierWrapper).stream().findFirst().orElse(null);
                    return successor != null && !successor.equals(chunkPosSupplierWrapper);
                }) // remove self-referential edges
                .map(chunkPosSupplierWrapper -> (ServerPlayer) chunkPosSupplierWrapper.getChunkPosSupplier()) // cast to ServerPlayer
                .collect(Collectors.toSet()); // collect to Set
    }

    @Override
    public void registerPlayer(final ServerPlayer serverPlayer) {
        final var chunkPosSupplierWrapper = new ChunkPosSupplierWrapperImpl((ChunkPosSupplier) serverPlayer, (ServerLevel) (Object) this);
        this.chunkPosSuppliers.putEdge(chunkPosSupplierWrapper, chunkPosSupplierWrapper);
    }

    @Override
    public void unregisterPlayer(final ServerPlayer serverPlayer) {
        this.unbind(serverPlayer);

        final var chunkPosSupplierWrapper = new ChunkPosSupplierWrapperImpl((ChunkPosSupplier) serverPlayer, (ServerLevel) (Object) this);
        this.chunkPosSuppliers.removeNode(chunkPosSupplierWrapper);
    }

    @Override
    @SuppressWarnings({"OptionalGetWithoutIsPresent", "EqualsBetweenInconvertibleTypes"})
    public ChunkPosSupplierWrapper getChunkPosSupplierWrapper(final ServerPlayer serverPlayer) {
        final var chunkPosSupplierWrapper = new ChunkPosSupplierWrapperImpl((ChunkPosSupplier) serverPlayer, (ServerLevel) (Object) this);
        if (this.chunkPosSuppliers.nodes().contains(chunkPosSupplierWrapper)) {
            return (ChunkPosSupplierWrapper) this.chunkPosSuppliers.nodes().stream()
                    .filter(node -> node.equals(chunkPosSupplierWrapper))
                    .findFirst()
                    .orElse(chunkPosSupplierWrapper);
        }
        return this.getOrRegisterPlayerWrapper(serverPlayer);
    }

    @Override
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public ChunkPosSupplierWrapper getViewpointChunkPosSupplierWrapper(final ServerPlayer serverPlayer) {
        final var chunkPosSupplierWrapper = this.getChunkPosSupplierWrapper(serverPlayer);
        if (chunkPosSupplierWrapper == null) {
            return null;
        }
        return (ChunkPosSupplierWrapper) this.chunkPosSuppliers.successors(chunkPosSupplierWrapper).stream()
                .findFirst()
                .orElse(chunkPosSupplierWrapper);
    }

    private ChunkPosSupplierWrapper getOrRegisterPlayerWrapper(final ServerPlayer serverPlayer) {
        final var wrapper = new ChunkPosSupplierWrapperImpl((ChunkPosSupplier) serverPlayer, (ServerLevel) (Object) this);
        if (!this.chunkPosSuppliers.nodes().contains(wrapper)) {
            this.chunkPosSuppliers.putEdge(wrapper, wrapper);
        }
        return wrapper;
    }

}
