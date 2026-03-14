package com.fullfud.fullfud.core;

import com.fullfud.fullfud.common.entity.ExplosionShrapnelEntity;
import com.fullfud.fullfud.common.entity.drone.DronePreset;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class DroneExplosionEffects {
    private static final float SBW_VEHICLE_DIRECT_IMPACT_DAMAGE = 340.0F;
    private static final float SBW_VEHICLE_EXPLOSION_DAMAGE = 80.0F;
    private static final float SBW_VEHICLE_EXPLOSION_RADIUS = 5.0F;

    private static final BlastProfile FPV_PROFILE = new BlastProfile(180, 18.0F, 25.0D, 4.0F, 12.0F, 4.2F, ShrapnelPattern.HORIZONTAL_RING, 0.0F);
    private static final BlastProfile FPV_STRIKE_PROFILE = new BlastProfile(180, 18.0F, 25.0D, 4.0F, 12.0F, 4.2F, ShrapnelPattern.FORWARD_CONE, 16.0F);
    private static final BlastProfile SHAHED_PROFILE = new BlastProfile(400, 15.0F, 200.0D, 10.0F, 50.0F, 3.8F, ShrapnelPattern.SPHERICAL, 0.0F);

    private static final float DISTANT_CLOSE_RADIUS = 24.0F;
    private static final float DISTANT_MEDIUM_RADIUS = 80.0F;
    private static final float DISTANT_FAR_RADIUS = 280.0F;

    private DroneExplosionEffects() {
    }

    public static void afterFpvExplosion(final ServerLevel level, final Entity source, @Nullable final LivingEntity attacker) {
        applyExplosionEffects(level, source, attacker, FPV_PROFILE, null);
    }

    public static void afterFpvExplosion(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final DronePreset preset,
        @Nullable final Vec3 impactDirection
    ) {
        final BlastProfile profile = preset == DronePreset.STRIKE_7INCH ? FPV_STRIKE_PROFILE : FPV_PROFILE;
        applyExplosionEffects(level, source, attacker, profile, impactDirection);
    }

    public static void afterShahedExplosion(final ServerLevel level, final Entity source, @Nullable final LivingEntity attacker) {
        applyExplosionEffects(level, source, attacker, SHAHED_PROFILE, null);
    }

    public static void applyDirectImpactVehicleDamage(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final Entity target
    ) {
        if (!isSuperbWarfareVehicle(target)) {
            return;
        }

        target.hurt(superbWarfareExplosionDamageSource(level, source, attacker), SBW_VEHICLE_DIRECT_IMPACT_DAMAGE);
    }

    private static void applyExplosionEffects(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final BlastProfile profile,
        @Nullable final Vec3 impactDirection
    ) {
        final Vec3 origin = source.position();
        playLayeredDistanceSounds(level, origin);
        applyWarbornBlastDamage(level, source, attacker, origin, profile);
        applySuperbWarfareExplosionDamage(level, source, attacker, origin);
        spawnShrapnel(level, source, attacker, origin, profile, impactDirection);
    }

    private static void applyWarbornBlastDamage(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final Vec3 origin,
        final BlastProfile profile
    ) {
        final float baseDamage = profile.baseBlastDamage();
        final float lethalRadius = profile.blastLethalRadius();
        final float maxRadius = profile.blastMaxRadius();
        final Vec3 explosionOrigin = origin.add(0.0D, 0.5D, 0.0D);
        final AABB explosionArea = source.getBoundingBox().inflate(maxRadius);
        final List<Entity> entities = new ArrayList<>(level.getEntities(source, explosionArea));
        final DamageSource damageSource = level.damageSources().thrown(source, attacker != null ? attacker : source);

        for (final Entity target : entities) {
            if (target.isSpectator() || isSuperbWarfareVehicle(target)) {
                continue;
            }

            final Vec3 center = target.getBoundingBox().getCenter();
            final double distance = center.distanceTo(explosionOrigin);
            if (distance > maxRadius) {
                continue;
            }

            if (!hasWarbornLineOfSight(level, source, explosionOrigin, target, center)) {
                continue;
            }

            final float damage;
            if (distance <= lethalRadius) {
                damage = baseDamage;
            } else {
                final float falloff = (float) ((distance - lethalRadius) / Math.max(0.001D, maxRadius - lethalRadius));
                damage = baseDamage * (1.0F - falloff);
            }
            if (damage <= 0.0F) {
                continue;
            }

            target.invulnerableTime = 0;
            final boolean hurt = target.hurt(damageSource, damage);
            if (!hurt) {
                target.hurt(level.damageSources().generic(), damage);
            }
        }
    }

    private static void applySuperbWarfareExplosionDamage(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final Vec3 origin
    ) {
        final float diameter = SBW_VEHICLE_EXPLOSION_RADIUS * 2.0F;
        final AABB area = new AABB(
            origin.x - diameter - 1.0D,
            origin.y - diameter - 1.0D,
            origin.z - diameter - 1.0D,
            origin.x + diameter + 1.0D,
            origin.y + diameter + 1.0D,
            origin.z + diameter + 1.0D
        );
        final DamageSource damageSource = superbWarfareExplosionDamageSource(level, source, attacker);
        final List<Entity> vehicles = level.getEntities(source, area, DroneExplosionEffects::isSuperbWarfareVehicle);

        for (final Entity vehicle : vehicles) {
            final double distanceRatio = Math.sqrt(vehicle.distanceToSqr(origin)) / diameter;
            if (distanceRatio > 1.0D) {
                continue;
            }

            final double seenPercent = Mth.clamp(net.minecraft.world.level.Explosion.getSeenPercent(origin, vehicle), 0.01D, 1.0D);
            final double damagePercent = (1.0D - distanceRatio) * seenPercent;
            final float damage = (float) (((damagePercent * damagePercent + damagePercent) / 2.0D) * SBW_VEHICLE_EXPLOSION_DAMAGE);
            if (damage <= 0.0F) {
                continue;
            }

            vehicle.hurt(damageSource, damage);
        }
    }

    private static void spawnShrapnel(
        final ServerLevel level,
        final Entity source,
        @Nullable final LivingEntity attacker,
        final Vec3 origin,
        final BlastProfile profile,
        @Nullable final Vec3 impactDirection
    ) {
        final Vec3 forwardDirection = impactDirection != null && impactDirection.lengthSqr() > 1.0E-6D
            ? impactDirection.normalize()
            : null;
        for (int i = 0; i < profile.shrapnelCount(); i++) {
            final Vec3 direction = switch (profile.shrapnelPattern()) {
                case FORWARD_CONE -> forwardDirection != null
                    ? randomDirectionInCone(forwardDirection, profile.coneHalfAngleDeg(), level)
                    : randomSphericalDirection(level);
                case SPHERICAL -> randomSphericalDirection(level);
                case HORIZONTAL_RING -> randomHorizontalDirection(level);
            };

            final ExplosionShrapnelEntity shrapnel = new ExplosionShrapnelEntity(
                FullfudRegistries.EXPLOSION_SHRAPNEL_ENTITY.get(),
                origin.x,
                origin.y + 0.5D,
                origin.z,
                level
            );
            shrapnel.setOwner(attacker != null ? attacker : source);
            shrapnel.setDamage(profile.shrapnelDamage());
            shrapnel.setMaxRange(profile.shrapnelRange());
            shrapnel.setStartPos(origin);
            shrapnel.shoot(direction.x, direction.y, direction.z, Math.max(0.4F, profile.shrapnelSpeedCap()), 5.0F);
            level.addFreshEntity(shrapnel);
        }
    }

    private static Vec3 randomHorizontalDirection(final ServerLevel level) {
        final double theta = Math.PI * 2.0D * level.random.nextDouble();
        final double horizontalX = Math.cos(theta);
        final double horizontalZ = Math.sin(theta);
        final double vertical = (level.random.nextDouble() - 0.5D) * 0.7D;
        return new Vec3(horizontalX, vertical, horizontalZ).normalize();
    }

    private static Vec3 randomSphericalDirection(final ServerLevel level) {
        final double u = level.random.nextDouble();
        final double v = level.random.nextDouble();
        final double theta = Math.PI * 2.0D * u;
        final double phi = Math.acos(2.0D * v - 1.0D);
        final double dx = Math.sin(phi) * Math.cos(theta);
        final double dy = Math.sin(phi) * Math.sin(theta);
        final double dz = Math.cos(phi);
        return new Vec3(dx, dy, dz).normalize();
    }

    private static Vec3 randomDirectionInCone(final Vec3 forward, final float coneHalfAngleDeg, final ServerLevel level) {
        final Vec3 normalizedForward = forward.normalize();
        final Vec3 referenceUp = Math.abs(normalizedForward.y) > 0.98D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        final Vec3 basisRight = normalizedForward.cross(referenceUp).normalize();
        final Vec3 basisUp = basisRight.cross(normalizedForward).normalize();

        final double coneHalfAngleRad = Math.toRadians(coneHalfAngleDeg);
        final double azimuth = Math.PI * 2.0D * level.random.nextDouble();
        final double cosTheta = Mth.lerp(level.random.nextDouble(), Math.cos(coneHalfAngleRad), 1.0D);
        final double sinTheta = Math.sqrt(Math.max(0.0D, 1.0D - cosTheta * cosTheta));

        final Vec3 lateral = basisRight.scale(Math.cos(azimuth)).add(basisUp.scale(Math.sin(azimuth)));
        return normalizedForward.scale(cosTheta).add(lateral.scale(sinTheta)).normalize();
    }

    private static void playLayeredDistanceSounds(final ServerLevel level, final Vec3 origin) {
        for (final ServerPlayer player : level.players()) {
            final double distanceSqr = player.distanceToSqr(origin);
            if (distanceSqr < DISTANT_CLOSE_RADIUS * DISTANT_CLOSE_RADIUS) {
                sendDistanceSound(level, player, origin, FullfudRegistries.EXPLOSION_CLOSE.getHolder(), DISTANT_CLOSE_RADIUS / 16.0F);
            } else if (distanceSqr < DISTANT_MEDIUM_RADIUS * DISTANT_MEDIUM_RADIUS) {
                sendDistanceSound(level, player, origin, FullfudRegistries.EXPLOSION_MEDIUM.getHolder(), DISTANT_MEDIUM_RADIUS / 16.0F);
            } else if (distanceSqr < DISTANT_FAR_RADIUS * DISTANT_FAR_RADIUS) {
                sendDistanceSound(level, player, origin, FullfudRegistries.EXPLOSION_FAR.getHolder(), DISTANT_FAR_RADIUS / 16.0F);
            } else {
                sendDistanceSound(level, player, origin, FullfudRegistries.EXPLOSION_VERYFAR.getHolder(), 20.0F);
            }
        }
    }

    private static void sendDistanceSound(
        final ServerLevel level,
        final ServerPlayer player,
        final Vec3 origin,
        final java.util.Optional<Holder<SoundEvent>> soundHolder,
        final float volume
    ) {
        if (soundHolder.isEmpty()) {
            return;
        }

        player.connection.send(new ClientboundSoundPacket(
            soundHolder.get(),
            SoundSource.NEUTRAL,
            origin.x,
            origin.y,
            origin.z,
            volume,
            1.0F,
            level.random.nextLong()
        ));
    }

    private static DamageSource superbWarfareExplosionDamageSource(
        final ServerLevel level,
        final Entity directEntity,
        @Nullable final LivingEntity attacker
    ) {
        return SuperbWarfareCompat.explosionDamageSource(level, directEntity, attacker != null ? attacker : directEntity);
    }

    private static boolean hasWarbornLineOfSight(
        final ServerLevel level,
        final Entity source,
        final Vec3 explosionOrigin,
        final Entity target,
        final Vec3 center
    ) {
        final Vec3[] targetPoints;
        if (target instanceof LivingEntity living) {
            final Vec3 basePos = living.position();
            targetPoints = new Vec3[] {
                basePos.add(0.0D, 0.1D, 0.0D),
                center,
                basePos.add(0.0D, living.getBbHeight(), 0.0D)
            };
        } else {
            targetPoints = new Vec3[] { center };
        }

        for (final Vec3 targetPoint : targetPoints) {
            final ClipContext clipContext = new ClipContext(explosionOrigin, targetPoint, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source);
            if (level.clip(clipContext).getType() == HitResult.Type.MISS) {
                return true;
            }
        }

        return false;
    }

    private static boolean isSuperbWarfareVehicle(final Entity entity) {
        return SuperbWarfareCompat.isVehicle(entity);
    }

    private record BlastProfile(
        int shrapnelCount,
        float shrapnelDamage,
        double shrapnelRange,
        float blastLethalRadius,
        float blastMaxRadius,
        float shrapnelSpeedCap,
        ShrapnelPattern shrapnelPattern,
        float coneHalfAngleDeg
    ) {
        private float baseBlastDamage() {
            return Math.max(120.0F, this.blastLethalRadius * 60.0F);
        }
    }

    private enum ShrapnelPattern {
        SPHERICAL,
        HORIZONTAL_RING,
        FORWARD_CONE
    }
}
