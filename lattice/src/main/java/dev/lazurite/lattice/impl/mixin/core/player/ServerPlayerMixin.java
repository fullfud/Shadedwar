package dev.lazurite.lattice.impl.mixin.core.player;

import dev.lazurite.lattice.api.level.LatticeServerLevel;
import dev.lazurite.lattice.api.point.ViewPoint;
import dev.lazurite.lattice.impl.Networking;
import dev.lazurite.lattice.impl.api.ChunkPosSupplierWrapper;
import dev.lazurite.lattice.impl.api.level.InternalLatticeServerLevel;
import dev.lazurite.lattice.impl.api.player.InternalLatticeServerPlayer;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player implements InternalLatticeServerPlayer {

    @Shadow public abstract ServerLevel serverLevel();
    @Shadow public abstract Entity getCamera();
    @Shadow private @Nullable Entity camera;
    @Shadow public ServerGamePacketListenerImpl connection;

    public ServerPlayerMixin(Level level, BlockPos blockPos, float f, GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    @Override
    public ViewPoint getViewPoint() {
        return ((LatticeServerLevel) this.serverLevel()).getViewPoint((ServerPlayer) (Object) this);
    }

    @Override
    public void setViewPoint(final ViewPoint viewPoint) {
        ((LatticeServerLevel) this.serverLevel()).bind((ServerPlayer) (Object) this, viewPoint);
        Networking.sendSetViewPointPacket((ServerPlayer) (Object) this, viewPoint);
    }

    @Override
    public void removeViewPoint() {
        ((LatticeServerLevel) this.serverLevel()).unbind((ServerPlayer) (Object) this);
        Networking.sendSetViewPointPacket((ServerPlayer) (Object) this, (ViewPoint) this);
    }

    @Override
    public void setCameraWithoutViewPoint(final Entity entity) {
        final var entity2 = this.getCamera();
        this.camera = entity == null ? this : entity;
        if (entity2 != this.camera) {
            this.connection.send(new ClientboundSetCameraPacket(this.camera));
            this.setViewPoint((ViewPoint) this.getCamera());
        }
    }

    @Override
    public ChunkPosSupplierWrapper getChunkPosSupplierWrapper() {
        return ((InternalLatticeServerLevel) this.serverLevel()).getChunkPosSupplierWrapper((ServerPlayer) (Object) this);
    }

    @Override
    public ChunkPosSupplierWrapper getViewpointChunkPosSupplierWrapper() {
        return ((InternalLatticeServerLevel) this.serverLevel()).getViewpointChunkPosSupplierWrapper((ServerPlayer) (Object) this);
    }

}
