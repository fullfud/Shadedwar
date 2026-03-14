package com.fullfud.fullfud.common.entity;

import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.SuperbWarfareCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.network.NetworkHooks;

public class ExplosionShrapnelEntity extends ThrowableItemProjectile {
    private float damage = 5.0F;
    private double maxRange = 50.0D;
    private Vec3 startPos;

    public ExplosionShrapnelEntity(final EntityType<? extends ExplosionShrapnelEntity> type, final Level level) {
        super(type, level);
    }

    public ExplosionShrapnelEntity(
        final EntityType<? extends ExplosionShrapnelEntity> type,
        final double x,
        final double y,
        final double z,
        final Level level
    ) {
        super(type, x, y, z, level);
        this.startPos = new Vec3(x, y, z);
    }

    public void setDamage(final float damage) {
        this.damage = damage;
    }

    public void setMaxRange(final double maxRange) {
        this.maxRange = maxRange;
    }

    public void setStartPos(final Vec3 startPos) {
        this.startPos = startPos;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.IRON_NUGGET;
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > 60) {
            discard();
            return;
        }

        final Vec3 velocity = getDeltaMovement();
        setDeltaMovement(velocity.x * 0.992D, velocity.y * 0.992D, velocity.z * 0.992D);
    }

    @Override
    protected void onHitEntity(final EntityHitResult result) {
        super.onHitEntity(result);

        final Entity target = result.getEntity();
        if (isSuperbWarfareVehicle(target)) {
            discard();
            return;
        }

        target.invulnerableTime = 0;
        target.hurt(level().damageSources().thrown(this, getOwner()), calculateDamage());

        if (level() instanceof ServerLevel serverLevel) {
            spawnImpactEffects(serverLevel);
        }
        discard();
    }

    @Override
    protected void onHitBlock(final BlockHitResult result) {
        super.onHitBlock(result);

        if (level() instanceof ServerLevel serverLevel) {
            final BlockPos blockPos = result.getBlockPos();
            final BlockState blockState = serverLevel.getBlockState(blockPos);
            if (blockState.is(Tags.Blocks.GLASS) || blockState.is(Tags.Blocks.GLASS_PANES)) {
                serverLevel.levelEvent(2001, blockPos, Block.getId(blockState));
                serverLevel.destroyBlock(blockPos, false, this);
            }
            spawnImpactEffects(serverLevel);
        }

        discard();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private float calculateDamage() {
        if (startPos == null) {
            return damage;
        }

        final double distance = position().distanceTo(startPos);
        final double falloff = Math.max(0.1D, 1.0D - distance / maxRange);
        return (float) (damage * falloff);
    }

    private void spawnImpactEffects(final ServerLevel serverLevel) {
        serverLevel.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 3, 0.02D, 0.02D, 0.02D, 0.001D);
        serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 6, 0.04D, 0.04D, 0.04D, 0.01D);

        if (tickCount > 3 && serverLevel.random.nextFloat() < 0.02F) {
            final float volume = 0.15F + serverLevel.random.nextFloat() * 0.15F;
            final float pitch = 0.85F + serverLevel.random.nextFloat() * 0.3F;
            serverLevel.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                FullfudRegistries.SHRAPNEL_HIT.get(),
                SoundSource.NEUTRAL,
                volume,
                pitch
            );
        }
    }

    private boolean isSuperbWarfareVehicle(final Entity entity) {
        return SuperbWarfareCompat.isVehicle(entity);
    }
}
