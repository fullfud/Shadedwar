package dev.lazurite.lattice.impl.mixin.fix.chunk;

import dev.lazurite.lattice.api.player.LatticePlayer;
import dev.lazurite.lattice.api.player.LatticeServerPlayer;
import dev.lazurite.lattice.api.point.ViewPoint;
import dev.lazurite.lattice.impl.api.level.InternalLatticeServerLevel;
import dev.lazurite.lattice.impl.api.player.InternalLatticeServerPlayer;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Shadow int viewDistance;
    @Shadow public abstract DistanceManager getDistanceManager();
    @Shadow protected abstract void updateChunkTracking(ServerPlayer serverPlayer, ChunkPos chunkPos, MutableObject<ClientboundLevelChunkWithLightPacket> mutableObject, boolean bl, boolean bl2);
    @Shadow public static boolean isChunkInRange(int i, int j, int k, int l, int m) { return false; }
    @Shadow private static boolean isChunkOnRangeBorder(int i, int j, int k, int l, int m) { return false; }

    @Unique private final List<ChunkPos> queuedChunks = new ArrayList<>();
    @Unique private final List<ChunkPos> addedChunks = new ArrayList<>();
    @Unique private final List<ChunkPos> removedChunks = new ArrayList<>();
    @Unique private final List<ChunkPos> inRangeChunks = new ArrayList<>();
    @Unique private ServerPlayer serverPlayer;
    @Unique private int lattice$getViewDistance(final ServerPlayer serverPlayer) {
        if (serverPlayer instanceof InternalLatticeServerPlayer lattice) {
            final var wrapper = lattice.getViewpointChunkPosSupplierWrapper();
            if (wrapper != null) {
                final int distance = wrapper.getDistance();
                if (distance > 0) {
                    return distance;
                }
            }
        }
        return this.viewDistance;
    }

    // region euclideanDistanceSquared
    /*
    Used in comparison with a constant (< 16384.0D).
    Returning the smallest number increases the odds that the comparison will pass.
    Same as || in boolean logic.
    */

    @ModifyVariable(
            method = "euclideanDistanceSquared",
            at = @At("STORE"),
            ordinal = 2
    )
    private static double euclideanDistanceSquared_STORE2(double f, ChunkPos chunkPos, Entity entity) {
        double d = SectionPos.sectionToBlockCoord(chunkPos.x, 8);
        return Math.min(f, d - ((LatticeServerPlayer) entity).getViewPoint().getX());
    }

    @ModifyVariable(
            method = "euclideanDistanceSquared",
            at = @At("STORE"),
            ordinal = 3
    )
    private static double euclideanDistanceSquared_STORE3(double g, ChunkPos chunkPos, Entity entity) {
        double e = SectionPos.sectionToBlockCoord(chunkPos.z, 8);
        return Math.min(g, e - ((LatticeServerPlayer) entity).getViewPoint().getZ());
    }

    // endregion euclideanDistanceSquared

    // region setViewDistance

    @ModifyVariable(
            method = "lambda$setViewDistance$51(Lnet/minecraft/world/level/ChunkPos;ILorg/apache/commons/lang3/mutable/MutableObject;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("STORE"),
            ordinal = 0
    )
    protected boolean method_17219_STORE0(boolean bl, ChunkPos chunkPos, int k, MutableObject<ClientboundLevelChunkWithLightPacket> mutableObject, ServerPlayer serverPlayer) {
        final var lastViewPointChunkPos = ((InternalLatticeServerPlayer) serverPlayer).getViewpointChunkPosSupplierWrapper().getLastChunkPos();
        return bl || ChunkMap.isChunkInRange(chunkPos.x, chunkPos.z, lastViewPointChunkPos.x, lastViewPointChunkPos.z, k);
    }

    @ModifyVariable(
            method = "lambda$setViewDistance$51(Lnet/minecraft/world/level/ChunkPos;ILorg/apache/commons/lang3/mutable/MutableObject;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("STORE"),
            ordinal = 1
    )
    protected boolean method_17219_STORE1(boolean bl, ChunkPos chunkPos, int k, MutableObject<ClientboundLevelChunkWithLightPacket> mutableObject, ServerPlayer serverPlayer) {
        final var lastViewPointChunkPos = ((InternalLatticeServerPlayer) serverPlayer).getViewpointChunkPosSupplierWrapper().getLastChunkPos();
        final int viewDistance = lattice$getViewDistance(serverPlayer);
        return bl || ChunkMap.isChunkInRange(chunkPos.x, chunkPos.z, lastViewPointChunkPos.x, lastViewPointChunkPos.z, viewDistance);
    }

    // endregion setViewDistance

    // region updatePlayerStatus
    /*
    Both playerMap#addPlayer and playerMap#removePlayer take a long (ChunkPos) but it isn't used.
    This may change in the future and is the reason for this comment.
    */

    @Inject(
            method = "updatePlayerStatus",
            at = @At("HEAD")
    )
    void updatePlayerStatus_HEAD(ServerPlayer serverPlayer, boolean bl, CallbackInfo ci) {
        this.queuedChunks.clear();

        // registers the player to the ServerLevel's graph
        if (bl) {
            ((InternalLatticeServerLevel) serverPlayer.serverLevel()).registerPlayer(serverPlayer);
        }
    }

    @Redirect(
            method = "updatePlayerStatus",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;removePlayer(Lnet/minecraft/core/SectionPos;Lnet/minecraft/server/level/ServerPlayer;)V"
            )
    )
    void updatePlayerStatus_removePlayer_safe(@Coerce Object distanceManager, SectionPos sectionPos, ServerPlayer serverPlayer) {
        lattice$safeRemovePlayer(sectionPos, serverPlayer);
    }

    @Redirect(
            method = "updatePlayerStatus",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;addPlayer(Lnet/minecraft/core/SectionPos;Lnet/minecraft/server/level/ServerPlayer;)V"
            )
    )
    void updatePlayerStatus_addPlayer_safe(@Coerce Object distanceManager, SectionPos sectionPos, ServerPlayer serverPlayer) {
        lattice$safeAddPlayer(sectionPos, serverPlayer);
    }

    @Inject(
            method = "updatePlayerStatus",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;addPlayer(Lnet/minecraft/core/SectionPos;Lnet/minecraft/server/level/ServerPlayer;)V",
                    shift = At.Shift.AFTER
            )
    )
    void updatePlayerStatus_addPlayer(ServerPlayer serverPlayer, boolean bl, CallbackInfo ci) {
        final var chunkPosSupplierWrapper = ((InternalLatticeServerPlayer) serverPlayer).getViewpointChunkPosSupplierWrapper();

        if (!chunkPosSupplierWrapper.isInSameChunk(serverPlayer)) {
            this.getDistanceManager().addPlayer(SectionPos.of(chunkPosSupplierWrapper.getChunkPos(), 0), serverPlayer);
        }
    }

    @Inject(
            method = "updatePlayerStatus",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;removePlayer(Lnet/minecraft/core/SectionPos;Lnet/minecraft/server/level/ServerPlayer;)V",
                    shift = At.Shift.AFTER
            )
    )
    void updatePlayerStatus_removePlayer(ServerPlayer serverPlayer, boolean bl, CallbackInfo ci) {
        final var chunkPosSupplierWrapper = ((InternalLatticeServerPlayer) serverPlayer).getViewpointChunkPosSupplierWrapper();

        if (!chunkPosSupplierWrapper.wasInSameChunk(serverPlayer, false)) {
            lattice$safeRemovePlayer(SectionPos.of(chunkPosSupplierWrapper.getChunkPos(), 0), serverPlayer);
        }
    }

    @Redirect(
            method = "updatePlayerStatus",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/ChunkPos;Lorg/apache/commons/lang3/mutable/MutableObject;ZZ)V"
            )
    )
    void updatePlayerStatus_updateChunkTracking(ChunkMap chunkMap, ServerPlayer serverPlayer, ChunkPos chunkPos, MutableObject<ClientboundLevelChunkWithLightPacket> mutableObject, boolean bl, boolean bl2) {
        this.queuedChunks.add(chunkPos);
    }

    @Inject(
            method = "updatePlayerStatus",
            at = @At("TAIL")
    )
    void updatePlayerStatus_TAIL(ServerPlayer serverPlayer, boolean bl, CallbackInfo ci) {
        final var chunkPosSupplierWrapper = ((InternalLatticeServerPlayer) serverPlayer).getViewpointChunkPosSupplierWrapper();
        final var chunkPos = chunkPosSupplierWrapper.getChunkPos();
        final int viewDistance = lattice$getViewDistance(serverPlayer);

        if (!chunkPosSupplierWrapper.isInSameChunk(serverPlayer)) {
            for (var x = chunkPos.x - viewDistance - 1; x <= chunkPos.x + viewDistance + 1; ++x) {
                for (var z = chunkPos.z - viewDistance - 1; z <= chunkPos.z + viewDistance + 1; ++z) {
                    if (ChunkMap.isChunkInRange(x, z, chunkPos.x, chunkPos.z, viewDistance)) { // && !this.queuedChunks.contains(chunkPos)) {
                        this.queuedChunks.add(new ChunkPos(x, z));
                    }
                }
            }
        }

        this.queuedChunks.forEach(_chunkPos -> this.updateChunkTracking(serverPlayer, _chunkPos, new MutableObject<>(), !bl, bl));

        // unregisters the player from the ServerLevel's graph
        if (!bl) {
            ((InternalLatticeServerLevel) serverPlayer.serverLevel()).unregisterPlayer(serverPlayer);
        }
    }

    // endregion updatePlayerStatus

    // region updatePlayerPos
    /*
    The updatePlayerPos method returns a SectionPos that is never used.
    This may change in the future and is the reason for this comment.
    */

    @Inject(
            method = "updatePlayerPos",
            at = @At("HEAD")
    )
    private void updatePlayerPos_HEAD(ServerPlayer serverPlayer, CallbackInfoReturnable<SectionPos> cir) {
        final var chunkPosSupplierWrapper = ((InternalLatticeServerPlayer) serverPlayer).getViewpointChunkPosSupplierWrapper();

        chunkPosSupplierWrapper.setLastLastChunkPos(chunkPosSupplierWrapper.getLastChunkPos());
        chunkPosSupplierWrapper.setLastChunkPos(chunkPosSupplierWrapper.getChunkPos());

        ((InternalLatticeServerPlayer) serverPlayer).getChunkPosSupplierWrapper().setLastLastChunkPos(serverPlayer.getLastSectionPos().chunk());
    }

    @Redirect(
            method = "updatePlayerPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/SectionPos;x()I"
            )
    )
    private int updatePlayerPos_x(SectionPos sectionPos, ServerPlayer serverPlayer) {
        return ((LatticePlayer) serverPlayer).getViewPoint().getChunkPos().x;
    }

    @Redirect(
            method = "updatePlayerPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/SectionPos;z()I"
            )
    )
    private int updatePlayerPos_z(SectionPos sectionPos, ServerPlayer serverPlayer) {
        return ((LatticePlayer) serverPlayer).getViewPoint().getChunkPos().z;
    }

    // endregion updatePlayerPos

    // region move
    /*
    The move method calls playerMap.updatePlayer which has an empty body.
    This may change in the future and is the reason for this comment.
    */

    @Inject(
            method = "move",
            at = @At("HEAD")
    )
    public void move_HEAD(ServerPlayer serverPlayer, CallbackInfo ci) {
        this.addedChunks.clear();
        this.removedChunks.clear();
        this.inRangeChunks.clear();
    }

    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;removePlayer(Lnet/minecraft/core/SectionPos;Lnet/minecraft/server/level/ServerPlayer;)V"
            )
    )
    private void move_removePlayer_safe(@Coerce Object distanceManager, SectionPos sectionPos, ServerPlayer serverPlayer) {
        lattice$safeRemovePlayer(sectionPos, serverPlayer);
    }

    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;addPlayer(Lnet/minecraft/core/SectionPos;Lnet/minecraft/server/level/ServerPlayer;)V"
            )
    )
    private void move_addPlayer_safe(@Coerce Object distanceManager, SectionPos sectionPos, ServerPlayer serverPlayer) {
        lattice$safeAddPlayer(sectionPos, serverPlayer);
    }

    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;getLastSectionPos()Lnet/minecraft/core/SectionPos;"
            )
    )
    private SectionPos move_getLastSectionPos(ServerPlayer serverPlayer) {
        final var chunkPosSupplierWrapper = ((InternalLatticeServerPlayer) serverPlayer).getViewpointChunkPosSupplierWrapper();
        return SectionPos.of(chunkPosSupplierWrapper.getLastChunkPos(), 0);
    }

    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"
            )
    )
    private SectionPos move_of(EntityAccess entityAccess) {
        if (entityAccess instanceof ServerPlayer serverPlayer) {
            return lattice$getViewPointSectionPos(serverPlayer);
        }
        return SectionPos.of(entityAccess);
    }

    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;getBlockX()I"
            )
    )
    private int move_getBlockX(ServerPlayer serverPlayer) {
        return Mth.floor(((LatticePlayer) serverPlayer).getViewPoint().getX());
    }

    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;getBlockZ()I"
            )
    )
    private int move_getBlockZ(ServerPlayer serverPlayer) {
        return Mth.floor(((LatticePlayer) serverPlayer).getViewPoint().getZ());
    }

    // bl3 is no longer used after this point
    // if it were, I would have to set it back to its original value
    @ModifyVariable(
            method = "move",
            at = @At("LOAD"),
            ordinal = 2
    )
    public boolean move_LOAD(boolean bl3, ServerPlayer serverPlayer) {
        final var chunkPosSupplierWrapper = ((InternalLatticeServerPlayer) serverPlayer).getViewpointChunkPosSupplierWrapper();

        return bl3 || chunkPosSupplierWrapper.getChunkPos().toLong() != chunkPosSupplierWrapper.getLastChunkPos().toLong(); // TODO: Potentially missing Y
    }

    @Inject(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;removePlayer(Lnet/minecraft/core/SectionPos;Lnet/minecraft/server/level/ServerPlayer;)V",
                    shift = At.Shift.AFTER
            )
    )
    public void move_removePlayer(ServerPlayer serverPlayer, CallbackInfo ci) {
        final var chunkPosSupplierWrapper = ((InternalLatticeServerPlayer) serverPlayer).getViewpointChunkPosSupplierWrapper();

        if (!chunkPosSupplierWrapper.wasInSameChunk(serverPlayer, true)) {
            lattice$safeRemovePlayer(SectionPos.of(chunkPosSupplierWrapper.getLastLastChunkPos(), 0), serverPlayer);
        }
    }

    @Inject(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;addPlayer(Lnet/minecraft/core/SectionPos;Lnet/minecraft/server/level/ServerPlayer;)V",
                    shift = At.Shift.AFTER
            )
    )
    public void move_addPlayer(ServerPlayer serverPlayer, CallbackInfo ci) {
        final var chunkPosSupplierWrapper = ((InternalLatticeServerPlayer) serverPlayer).getViewpointChunkPosSupplierWrapper();

        if (!chunkPosSupplierWrapper.isInSameChunk(serverPlayer)) {
            lattice$safeAddPlayer(SectionPos.of(chunkPosSupplierWrapper.getChunkPos(), 0), serverPlayer);
        }
    }

    @Unique
    private void lattice$safeRemovePlayer(final SectionPos sectionPos, final ServerPlayer serverPlayer) {
        try {
            this.getDistanceManager().removePlayer(sectionPos, serverPlayer);
        } catch (NullPointerException ignored) {
            // Some modded mixes (or view-point swaps) can desync the internal set; avoid a hard crash.
        }
    }

    @Unique
    private void lattice$safeAddPlayer(final SectionPos sectionPos, final ServerPlayer serverPlayer) {
        try {
            this.getDistanceManager().addPlayer(sectionPos, serverPlayer);
        } catch (NullPointerException ignored) {
            // Defensive: avoid crashing if the internal set is unexpectedly null.
        }
    }

    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/ChunkPos;Lorg/apache/commons/lang3/mutable/MutableObject;ZZ)V"
            )
    )
    public void move_updateChunkTracking(ChunkMap chunkMap, ServerPlayer serverPlayer, ChunkPos chunkPos, MutableObject<ClientboundLevelChunkWithLightPacket> mutableObject, boolean bl, boolean bl2) {
        if (bl && bl2) {
            this.inRangeChunks.add(chunkPos);
        } else if (!bl && bl2) {
            this.addedChunks.add(chunkPos);
        } else {
            this.removedChunks.add(chunkPos);
        }
    }

    @Inject(
            method = "move",
            at = @At("TAIL")
    )
    public void move_TAIL(ServerPlayer serverPlayer, CallbackInfo ci) {
        final var chunkPosSupplierWrapper = ((InternalLatticeServerPlayer) serverPlayer).getViewpointChunkPosSupplierWrapper();

        final var viewPoint = (ViewPoint) chunkPosSupplierWrapper.getChunkPosSupplier();

        final var viewPointChunkPos = viewPoint.getChunkPos();
        final var lastViewPointChunkPos = chunkPosSupplierWrapper.getLastChunkPos();
        final int viewDistance = lattice$getViewDistance(serverPlayer);

        // if check not necessary but more efficient
        if (!chunkPosSupplierWrapper.isInSameChunk(serverPlayer) || !chunkPosSupplierWrapper.wasInSameChunk(serverPlayer, true)) {

            if (Math.abs(lastViewPointChunkPos.x - viewPointChunkPos.x) <= viewDistance * 2 && Math.abs(lastViewPointChunkPos.z - viewPointChunkPos.z) <= viewDistance * 2) {
                for (var x = Math.min(viewPointChunkPos.x, lastViewPointChunkPos.x) - viewDistance - 1; x <= Math.max(viewPointChunkPos.x, lastViewPointChunkPos.x) + viewDistance + 1; ++x) {
                    for (var z = Math.min(viewPointChunkPos.z, lastViewPointChunkPos.z) - viewDistance - 1; z <= Math.max(viewPointChunkPos.z, lastViewPointChunkPos.z) + viewDistance + 1; ++z) {
                        boolean bl = isChunkInRange(x, z, lastViewPointChunkPos.x, lastViewPointChunkPos.z, viewDistance);
                        boolean bl2 = isChunkInRange(x, z, viewPointChunkPos.x, viewPointChunkPos.z, viewDistance);

                        ChunkPos chunkPos = new ChunkPos(x, z);

                        if (bl && bl2) {
                            this.inRangeChunks.add(chunkPos);
                        } else if (!bl && bl2) {
                            this.addedChunks.add(chunkPos);
                        } else {
                            this.removedChunks.add(chunkPos);
                        }
                    }
                }
            } else {
                for (var x = lastViewPointChunkPos.x - viewDistance - 1; x <= lastViewPointChunkPos.x + viewDistance + 1; ++x) {
                    for (var z = lastViewPointChunkPos.z - viewDistance - 1; z <= lastViewPointChunkPos.z + viewDistance + 1; ++z) {
                        final var chunkPos = new ChunkPos(x, z);

                        if (isChunkInRange(x, z, lastViewPointChunkPos.x, lastViewPointChunkPos.z, viewDistance)) { // && !this.removedChunks.contains(chunkPos)) {
                            this.removedChunks.add(chunkPos);
                        }
                    }
                }

                for (var x = viewPointChunkPos.x - viewDistance - 1; x <= viewPointChunkPos.x + viewDistance + 1; ++x) {
                    for (var z = viewPointChunkPos.z - viewDistance - 1; z <= viewPointChunkPos.z + viewDistance + 1; ++z) {
                        final var chunkPos = new ChunkPos(x, z);

                        if (isChunkInRange(x, z, viewPointChunkPos.x, viewPointChunkPos.z, viewDistance)) { // && !this.addedChunks.contains(chunkPos)) {
                            this.addedChunks.add(chunkPos);
                        }
                    }
                }
            }

//            this.addedChunks.removeIf(this.inRangeChunks::contains);

            this.removedChunks.removeIf(this.addedChunks::contains);
            this.removedChunks.removeIf(this.inRangeChunks::contains);
        }

        this.addedChunks.forEach(chunkPos -> this.updateChunkTracking(serverPlayer, chunkPos, new MutableObject<>(), false, true));
        this.removedChunks.forEach(chunkPos -> this.updateChunkTracking(serverPlayer, chunkPos, new MutableObject<>(), true, false));
    }

    @Unique
    private static SectionPos lattice$getViewPointSectionPos(final ServerPlayer serverPlayer) {
        final var viewPoint = ((LatticePlayer) serverPlayer).getViewPoint();
        return SectionPos.of(BlockPos.containing(viewPoint.getX(), viewPoint.getY(), viewPoint.getZ()));
    }

    // endregion move

    // region getPlayers

    @Inject(
            method = "getPlayers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;getLastSectionPos()Lnet/minecraft/core/SectionPos;"
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    public void getPlayers_getLastSectionPos(ChunkPos chunkPos, boolean bl, CallbackInfoReturnable<List<ServerPlayer>> cir, Set<ServerPlayer> set, ImmutableList.Builder<ServerPlayer> builder, Iterator<ServerPlayer> var5, ServerPlayer serverPlayer) {
        this.serverPlayer = serverPlayer;
    }

    @Redirect(
            method = "getPlayers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;isChunkOnRangeBorder(IIIII)Z"
            )
    )
    public boolean getPlayers_isChunkOnRangeBorder(int i, int j, int k, int l, int m) {
        final var lastViewPointChunkPos = ((InternalLatticeServerPlayer) serverPlayer).getViewpointChunkPosSupplierWrapper().getLastChunkPos();
        final int viewDistance = lattice$getViewDistance(serverPlayer);
        return isChunkOnRangeBorder(i, j, k, l, m) || isChunkOnRangeBorder(i, j, lastViewPointChunkPos.x, lastViewPointChunkPos.z, viewDistance);
    }

    @Redirect(
            method = "getPlayers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;isChunkInRange(IIIII)Z"
            )
    )
    public boolean getPlayers_isChunkInRange(int i, int j, int k, int l, int m) {
        final var lastViewPointChunkPos = ((InternalLatticeServerPlayer) serverPlayer).getViewpointChunkPosSupplierWrapper().getLastChunkPos();
        final int viewDistance = lattice$getViewDistance(serverPlayer);
        return isChunkInRange(i, j, k, l, m) || isChunkInRange(i, j, lastViewPointChunkPos.x, lastViewPointChunkPos.z, viewDistance);
    }

    // endregion getPlayers

}
