package com.fullfud.fullfud.common.entity;

import com.fullfud.fullfud.common.item.MonitorItem;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.data.ShahedLinkData;
import com.fullfud.fullfud.core.network.FullfudNetwork;
import com.fullfud.fullfud.core.network.packet.ShahedControlPacket;
import com.fullfud.fullfud.core.network.packet.ShahedStatusPacket;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ShahedDroneEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Float> DATA_THRUST = SynchedEntityData.defineId(ShahedDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(ShahedDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ON_LAUNCHER = SynchedEntityData.defineId(ShahedDroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData.defineId(ShahedDroneEntity.class, EntityDataSerializers.FLOAT);

    private static final String TAG_THRUST = "Thrust";
    private static final String TAG_MOTION = "Motion";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_VIEW = "OwnerView";
    private static final String TAG_ARMED = "Armed";
    private static final String TAG_LAUNCH_Y = "LaunchY";
    private static final String TAG_BODY_YAW = "BodyYaw";
    private static final String TAG_BODY_PITCH = "BodyPitch";
    private static final String TAG_BODY_ROLL = "BodyRoll";
    private static final String TAG_REMOTE_INIT = "RemoteInit";
    private static final String TAG_COLOR = "Color";
    private static final String TAG_ON_LAUNCHER = "OnLauncher";
    private static final String TAG_LAUNCHER_UUID = "LauncherUUID";
    private static final String TAG_PROJECTILE_HITS = "ProjectileHits";
    private static final String TAG_DAMAGE_TARGET_SPEED = "DamageTargetSpeed";
    private static final String TAG_LINEAR_VELOCITY = "LinearVelocity";
    private static final String TAG_FUEL = "FuelMass";
    
    private static final String TAG_SESS_X = "SessX";
    private static final String TAG_SESS_Y = "SessY";
    private static final String TAG_SESS_Z = "SessZ";
    private static final String TAG_SESS_YAW = "SessYaw";
    private static final String TAG_SESS_PITCH = "SessPitch";
    private static final String TAG_SESS_DIM = "SessDim";
    private static final String TAG_SESS_GM = "SessGM";
    private static final String TAG_CONTROLLER = "ControllerUUID";

    private static final int STATUS_INTERVAL = 1;
    private static final int CONTROL_TIMEOUT_TICKS = 20;
    private static final double TICK_SECONDS = 1.0D / 20.0D;
    private static final double BASE_MASS_KG = 210.0D;
    private static final double FUEL_CAPACITY_KG = 45.0D;
    private static final double FUEL_CONSUMPTION_PER_SEC = 0.9286D;
    private static final double MAX_THRUST_FORCE = 1500.0D;
    private static final double THRUST_CURVE_EXPONENT = 2.0D;
    private static final double RHO_SEA_LEVEL = 1.225D;
    private static final double ATMOSPHERE_SCALE_HEIGHT = 8500.0D;
    private static final double WING_AREA = 4.0D;
    private static final double CL_ALPHA = 2.5D;
    private static final double CL_MAX = 1.5D;
    private static final double CL_ZERO = 0.25D;
    private static final double CD_MIN = 0.052D;
    private static final double DRAG_FACTOR = 0.055D;
    private static final double CY_BETA = -0.85D;
    private static final double GRAVITY = 9.81D;
    private static final double MAX_ROLL_RATE = 60.0D;
    private static final double MAX_PITCH_RATE = 45.0D;
    private static final double ROLL_ACCEL = 120.0D;
    private static final double PITCH_ACCEL = 90.0D;
    private static final double GROUND_FRICTION = 0.62D;
    private static final double MAX_AIRSPEED = 72.222D;
    private static final double INITIAL_LAUNCH_SPEED = 55.5D;
    private static final double ENGINE_IDLE_THRUST = 0.0D;
    private static final double ENGINE_SPOOL_RATE = 0.05D;
    private static final double STALL_ANGLE = Math.toRadians(17.0D);
    private static final double PROJECTILE_DAMAGE_DECEL_PER_SEC = 24.0D;
    private static final double DAMAGE_SMOKE_PARTICLES_PER_TICK = 7.0D / 20.0D;
    private static final double DAMAGE_SMOKE_SPREAD = 0.7D;
    private static final TicketType<Integer> SHAHED_TICKET = TicketType.create("fullfud_shahed", Integer::compareTo, 4);
    private static final EntityDimensions SHAHEED_DIMENSIONS = EntityDimensions.scalable(3.0F, 1.0F);
    private static final Logger LOG = LogManager.getLogger("ShahedDrone");

    private final Map<UUID, Integer> viewerDistances = new HashMap<>();
    private float controlForward;
    private float controlStrafe;
    private float controlVertical;
    private Vec3 linearVelocity = Vec3.ZERO;
    private double crippledHorizontalTargetSpeed = -1.0D;
    private double damageSmokeAccumulator;
    private int projectileHitCount;
    private int controlTimeout;
    private double rollRate;
    private double pitchRate;
    private UUID ownerUUID;
    private int ownerViewDistance = 8;
    private ChunkPos lastTicketPos;
    private int lastTicketRadius;
    private int desiredChunkRadius;
    private float jammerOverride;
    private boolean jammerSuppressControls;
    private static final double JAMMER_HARD_RADIUS = 300.0D;
    private static final double JAMMER_MAX_RADIUS = 600.0D;
    private boolean armed;
    private double launchBaselineY;
    private UUID controllingPlayer;
    private ControlSession controlSession;
    private RemotePilotFakePlayer avatar;
    private double fuelMass = FUEL_CAPACITY_KG;
    private FlightTelemetry telemetry = FlightTelemetry.ZERO;
    private double bodyYaw;
    private double bodyPitch;
    private double bodyRoll;
    private double bodyPitchO;
    private double bodyRollO;
    private double engineOutput;
    private boolean remoteInitialized;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("animation.model.idle");
    private static final RawAnimation RUN_ANIMATION = RawAnimation.begin().thenLoop("animation.model.running");
    private boolean cameraPinned;
    private static final double LAUNCHER_VERTICAL_OFFSET = 0.25D;
    private static final double LAUNCHER_FORWARD_OFFSET = 2.0D;
    private static final double LAUNCHER_UP_OFFSET = 10.0D;
    private static final double LAUNCHER_LAUNCH_SPEED = 260.0D / 3.6D;
    private static final float LAUNCHER_LAUNCH_PITCH = -12.5F;
    private int mountedLauncherId = -1;
    private UUID mountedLauncherUuid;

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    public ShahedDroneEntity(final EntityType<? extends ShahedDroneEntity> entityType, final Level level) {
        super(entityType, level);
        this.noPhysics = false;
        this.setNoGravity(true);
        this.launchBaselineY = this.getY();
        this.bodyYaw = this.getYRot();
        this.bodyPitch = 0.0D;
        this.bodyPitchO = 0.0D;
        this.bodyRoll = 0.0D;
        this.bodyRollO = 0.0D;
        this.setXRot((float) bodyPitch);
        this.engineOutput = 0.0D;
        this.remoteInitialized = false;
        updateBoundingBox();
        this.refreshDimensions();
    }

    public static Optional<ShahedDroneEntity> find(final ServerLevel level, final UUID uuid) {
        final Entity entity = level.getEntity(uuid);
        if (entity instanceof ShahedDroneEntity drone) {
            return Optional.of(drone);
        }
        return Optional.empty();
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_THRUST, 0.25F);
        this.entityData.define(DATA_COLOR, ShahedColor.WHITE.getId());
        this.entityData.define(DATA_ON_LAUNCHER, false);
        this.entityData.define(DATA_ROLL, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide()) {
            updateJammerState();
            if (controllingPlayer != null) {
                final ServerPlayer player = getControllingPlayer();
                if (player != null && !cameraPinned) {
                     endRemoteControl(player);
                }
            }
        }

        if (isOnLauncher()) {
            if (!level().isClientSide) {
                handleLauncherAttachment();
            }
            return;
        }

        bodyPitchO = bodyPitch;
        bodyRollO = bodyRoll;

        if (level().isClientSide()) {
            this.bodyRoll = this.entityData.get(DATA_ROLL);
        }

        if (!level().isClientSide() || isControlledByLocalInstance()) {
            if (!level().isClientSide() && controllingPlayer == null) {
                bodyPitch = bodyPitch * 0.9f;
                bodyRoll = bodyRoll * 0.9f;
            }
            this.setXRot((float) bodyPitch);
            updateControlTimeout();
            updateFlight();
            
            if (!level().isClientSide()) {
                this.entityData.set(DATA_ROLL, (float) bodyRoll);
            }
        }

        if (level().isClientSide() && !isControlledByLocalInstance()) {
            if (this.lerpSteps > 0) {
                double d0 = this.getX() + (this.lerpX - this.getX()) / (double) this.lerpSteps;
                double d1 = this.getY() + (this.lerpY - this.getY()) / (double) this.lerpSteps;
                double d2 = this.getZ() + (this.lerpZ - this.getZ()) / (double) this.lerpSteps;
                double d3 = Mth.wrapDegrees(this.lerpYRot - (double) this.getYRot());
                this.setYRot(this.getYRot() + (float) d3 / (float) this.lerpSteps);
                double d4 = Mth.wrapDegrees(this.lerpXRot - (double) this.getXRot());
                this.setXRot(this.getXRot() + (float) d4 / (float) this.lerpSteps);
                this.lerpSteps--;
                this.setPos(d0, d1, d2);
                this.setRot(this.getYRot(), this.getXRot());
            }
            this.bodyPitch = this.getXRot();
            this.bodyYaw = this.getYRot();
        }

        if (level().isClientSide() && isControlledByLocalInstance()) {
            this.setYRot((float) bodyYaw);
            this.setXRot((float) bodyPitch);
        }

        if (!level().isClientSide()) {
            updateLaunchState();
            if (armed && detectImpact()) {
                detonate();
                return;
            }
            final ServerPlayer cp = getControllingPlayer();
            if (cameraPinned && isSignalLostFor(cp)) {
                releaseCameraFor(cp);
            }
            ensureChunkTicket();
            updateControllerBinding();
            if (tickCount % STATUS_INTERVAL == 0) {
                broadcastStatus();
            }
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        if (this.isControlledByLocalInstance()) {
            return;
        }
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yaw;
        this.lerpXRot = pitch;
        this.lerpSteps = posRotationIncrements;
    }

    public float getVisualRoll(float partialTick) {
        return (float) Mth.lerp(partialTick, bodyRollO, bodyRoll);
    }

    public float getVisualPitch(float partialTick) {
        return (float) Mth.lerp(partialTick, bodyPitchO, bodyPitch);
    }

    private void updateControlTimeout() {
        if (controlTimeout > 0) {
            controlTimeout--;
        } else {
            controlForward = 0.0F;
            controlStrafe = 0.0F;
            controlVertical = 0.0F;
        }
    }

    private void updateFlight() {
        final double dt = TICK_SECONDS;
        float throttle = Mth.clamp(getThrust(), 0.0F, 1.0F);
        
        if (fuelMass > 0.0D && throttle > 0.0F) {
            double burn = Math.pow(throttle, 1.5D) * FUEL_CONSUMPTION_PER_SEC * dt;
            if (throttle < 0.8F) {
                burn *= 0.6D; 
            }
            fuelMass = Math.max(0.0D, fuelMass - burn);
        }
        if (fuelMass <= 0.0D) {
            fuelMass = 0.0D;
            if (throttle > 0.0F) {
                throttle = 0.0F;
                setThrust(0.0F);
            }
        }
        
        final double totalMass = BASE_MASS_KG + fuelMass;
        engineOutput += (throttle - engineOutput) * ENGINE_SPOOL_RATE;
        final double thrustForce = fuelMass <= 0.0D ? 0.0D : computeEffectiveThrust(engineOutput);

        integrateAttitude(dt);

        final double yawRad = Math.toRadians(bodyYaw);
        final double pitchRad = Math.toRadians(bodyPitch);
        final double rollRad = Math.toRadians(bodyRoll);

        final Vec3 forward = new Vec3(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        ).normalize();

        final Vec3 upBase = new Vec3(0, 1, 0);
        final Vec3 right = forward.cross(upBase).normalize();
        
        Vec3 localUp = right.cross(forward).normalize();
        
        final double sr = Math.sin(rollRad);
        final double cr = Math.cos(rollRad);
        localUp = localUp.scale(cr).add(right.scale(sr)).normalize();

        final double speed = linearVelocity.length();
        final double altitude = this.getY();
        final double airDensity = sampleAirDensity(altitude);
        final double q = 0.5D * airDensity * speed * speed;

        final double velocityPitch = speed > 0.01 ? Math.atan2(linearVelocity.y, Math.sqrt(linearVelocity.x * linearVelocity.x + linearVelocity.z * linearVelocity.z)) : -pitchRad;
        
        final double aoa = -pitchRad - velocityPitch;
        
        final double cl = applyStall(resolveLiftCoefficient(aoa), aoa);
        final double liftMagnitude = cl * q * WING_AREA;
        final Vec3 liftVector = localUp.scale(liftMagnitude);

        final double cd = CD_MIN + DRAG_FACTOR * cl * cl;
        final Vec3 dragVector = linearVelocity.normalize().scale(-cd * q * WING_AREA);
        
        final Vec3 thrustVector = forward.scale(thrustForce);
        final Vec3 gravityVector = new Vec3(0, -totalMass * GRAVITY, 0);

        Vec3 netForce = thrustVector.add(liftVector).add(dragVector).add(gravityVector);
        
        final Vec3 acceleration = netForce.scale(1.0D / totalMass);
        linearVelocity = linearVelocity.add(acceleration.scale(dt));

        final double speedCapSq = MAX_AIRSPEED * MAX_AIRSPEED;
        if (linearVelocity.lengthSqr() > speedCapSq) {
            linearVelocity = linearVelocity.normalize().scale(MAX_AIRSPEED);
        }

        applyProjectileDamageEffects(dt);

        final Vec3 velPerTick = linearVelocity.scale(TICK_SECONDS);
        this.setDeltaMovement(velPerTick);
        this.hasImpulse = true;
        this.move(MoverType.SELF, velPerTick);
        updateBoundingBox();
        resolveCollisionVelocity();

        if (linearVelocity.lengthSqr() > 1.0) {
            final double horizontalSpeed = Math.sqrt(linearVelocity.x * linearVelocity.x + linearVelocity.z * linearVelocity.z);
            final double moveYaw = Math.toDegrees(Math.atan2(-linearVelocity.x, linearVelocity.z));
            
            double yawDiff = Mth.wrapDegrees(moveYaw - bodyYaw);
            bodyYaw += yawDiff * 0.1D; 
        }

        telemetry = new FlightTelemetry(
            (float) speed,
            (float) Math.sqrt(linearVelocity.x * linearVelocity.x + linearVelocity.z * linearVelocity.z),
            (float) linearVelocity.y,
            (float) Math.toDegrees(aoa),
            0.0F,
            throttle,
            (float) fuelMass,
            (float) airDensity
        );
    }

    private void integrateAttitude(final double dt) {
        double targetRollRate = controlStrafe * MAX_ROLL_RATE;
        rollRate = approach(rollRate, Math.toRadians(targetRollRate), Math.toRadians(ROLL_ACCEL) * dt);
        
        double targetPitchRate = controlForward * MAX_PITCH_RATE; 
        pitchRate = approach(pitchRate, Math.toRadians(targetPitchRate), Math.toRadians(PITCH_ACCEL) * dt);

        bodyRoll += Math.toDegrees(rollRate * dt);
        bodyPitch += Math.toDegrees(pitchRate * dt);
        
        bodyPitch = Mth.clamp(bodyPitch, -85.0D, 85.0D);
        bodyRoll = Mth.wrapDegrees(bodyRoll);
        bodyYaw = Mth.wrapDegrees(bodyYaw);

        this.setXRot((float) bodyPitch);
        this.setYRot((float) bodyYaw);
        this.setYHeadRot((float) bodyYaw);
    }

    private void applyProjectileDamageEffects(final double dt) {
        if (projectileHitCount <= 0) {
            return;
        }
        final double horizontalSpeed = Math.sqrt(linearVelocity.x * linearVelocity.x + linearVelocity.z * linearVelocity.z);
        if (crippledHorizontalTargetSpeed < 0.0D) {
            crippledHorizontalTargetSpeed = horizontalSpeed;
        }
        final double slowdown = PROJECTILE_DAMAGE_DECEL_PER_SEC * dt;
        crippledHorizontalTargetSpeed = Math.max(0.0D, crippledHorizontalTargetSpeed - slowdown);
        if (horizontalSpeed > crippledHorizontalTargetSpeed) {
            if (horizontalSpeed <= 1.0E-4D || crippledHorizontalTargetSpeed <= 0.0D) {
                linearVelocity = new Vec3(0.0D, linearVelocity.y, 0.0D);
            } else {
                final Vec3 horizontal = new Vec3(linearVelocity.x, 0.0D, linearVelocity.z).normalize();
                final Vec3 clamped = horizontal.scale(crippledHorizontalTargetSpeed);
                linearVelocity = new Vec3(clamped.x, linearVelocity.y, clamped.z);
            }
        }
        spawnDamageSmokeParticles();
    }

    private void spawnDamageSmokeParticles() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        damageSmokeAccumulator += DAMAGE_SMOKE_PARTICLES_PER_TICK;
        final int spawnCount = (int) damageSmokeAccumulator;
        damageSmokeAccumulator -= spawnCount;
        if (spawnCount <= 0) {
            return;
        }
        final RandomSource random = this.random;
        for (int i = 0; i < spawnCount; i++) {
            final boolean large = random.nextFloat() < 0.65F;
            final ParticleOptions particle = large ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE;
            final double offsetX = (random.nextDouble() - 0.5D) * DAMAGE_SMOKE_SPREAD;
            final double offsetZ = (random.nextDouble() - 0.5D) * DAMAGE_SMOKE_SPREAD;
            final double height = 0.6D + random.nextDouble() * 0.6D;
            final double px = getX() + offsetX;
            final double py = getY() + height;
            final double pz = getZ() + offsetZ;
            final double driftX = (random.nextDouble() - 0.5D) * 0.02D;
            final double driftY = 0.02D + random.nextDouble() * 0.05D;
            final double driftZ = (random.nextDouble() - 0.5D) * 0.02D;
            serverLevel.sendParticles(particle, px, py, pz, 1, driftX, driftY, driftZ, 0.0D);
        }
    }

    private static double sampleAirDensity(final double altitude) {
        return RHO_SEA_LEVEL * Math.exp(-Math.max(0.0D, altitude) / ATMOSPHERE_SCALE_HEIGHT);
    }

    private static double resolveLiftCoefficient(final double aoaRad) {
        return Mth.clamp(CL_ZERO + CL_ALPHA * aoaRad, -CL_MAX, CL_MAX);
    }

    private static double applyStall(final double cl, final double aoaRad) {
        final double absAoa = Math.abs(aoaRad);
        if (absAoa <= STALL_ANGLE) {
            return cl;
        }
        final double excess = Math.min(absAoa - STALL_ANGLE, STALL_ANGLE);
        final double stallFactor = Math.max(0.0D, 1.0D - (excess / STALL_ANGLE));
        return cl * stallFactor * stallFactor;
    }

    private double computeEffectiveThrust(final double engineLevel) {
        final double normalizedLevel = Mth.clamp(engineLevel, 0.0D, 1.0D);
        final double throttleResponse = Math.pow(normalizedLevel, THRUST_CURVE_EXPONENT);
        return ENGINE_IDLE_THRUST + throttleResponse * (MAX_THRUST_FORCE - ENGINE_IDLE_THRUST);
    }

    private Vec3 forwardGroundVector() {
        final float yawRad = this.getYRot() * ((float) Math.PI / 180F);
        final double x = -Mth.sin(yawRad);
        final double z = Mth.cos(yawRad);
        return new Vec3(x, 0.0D, z).normalize();
    }

    private void resolveCollisionVelocity() {
        if (this.verticalCollision && linearVelocity.y < 0.0D) {
            linearVelocity = new Vec3(linearVelocity.x * GROUND_FRICTION, 0.0D, linearVelocity.z * GROUND_FRICTION);
        }
        if (this.horizontalCollision) {
            linearVelocity = new Vec3(linearVelocity.x * GROUND_FRICTION, linearVelocity.y, linearVelocity.z * GROUND_FRICTION);
        }
    }

    private void ensureFlightAltitude() {
        if (level() == null) {
            return;
        }
        final int x = Mth.floor(getX());
        final int z = Mth.floor(getZ());
        final int terrainY = level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        final double desiredY = terrainY + 10.0D;
        if (this.getY() >= desiredY) {
            return;
        }
        this.setPos(getX(), desiredY, getZ());
        this.setDeltaMovement(Vec3.ZERO);
        this.linearVelocity = Vec3.ZERO;
        this.bodyPitch = 0.0D;
        this.bodyPitchO = 0.0D;
        this.bodyRoll = 0.0D;
        this.bodyRollO = 0.0D;
        this.setXRot((float) bodyPitch);
        this.launchBaselineY = desiredY;
        updateBoundingBox();
    }

    @Override
    protected void readAdditionalSaveData(final CompoundTag tag) {
        setThrust(tag.getFloat(TAG_THRUST));
        if (tag.contains(TAG_LINEAR_VELOCITY, Tag.TAG_LIST)) {
            final ListTag list = tag.getList(TAG_LINEAR_VELOCITY, Tag.TAG_DOUBLE);
            this.linearVelocity = new Vec3(list.getDouble(0), list.getDouble(1), list.getDouble(2));
        } else if (tag.contains(TAG_MOTION, Tag.TAG_LIST)) {
            final ListTag legacy = tag.getList(TAG_MOTION, Tag.TAG_DOUBLE);
            this.linearVelocity = new Vec3(legacy.getDouble(0) * 20.0D, legacy.getDouble(1) * 20.0D, legacy.getDouble(2) * 20.0D);
        } else {
            this.linearVelocity = Vec3.ZERO;
        }
        if (tag.hasUUID(TAG_OWNER)) {
            this.ownerUUID = tag.getUUID(TAG_OWNER);
        }
        this.ownerViewDistance = Math.max(2, tag.getInt(TAG_OWNER_VIEW));
        this.armed = tag.getBoolean(TAG_ARMED);
        this.launchBaselineY = tag.contains(TAG_LAUNCH_Y) ? tag.getDouble(TAG_LAUNCH_Y) : this.getY();
        this.fuelMass = tag.contains(TAG_FUEL) ? tag.getDouble(TAG_FUEL) : FUEL_CAPACITY_KG;
        this.bodyYaw = tag.contains(TAG_BODY_YAW) ? tag.getDouble(TAG_BODY_YAW) : this.getYRot();
        this.bodyPitch = tag.contains(TAG_BODY_PITCH) ? tag.getDouble(TAG_BODY_PITCH) : this.getXRot();
        this.bodyRoll = tag.contains(TAG_BODY_ROLL) ? tag.getDouble(TAG_BODY_ROLL) : 0.0D;
        this.bodyPitchO = this.bodyPitch;
        this.bodyRollO = this.bodyRoll;
        this.remoteInitialized = tag.getBoolean(TAG_REMOTE_INIT);
        if (tag.contains(TAG_COLOR)) {
            setColor(ShahedColor.byId(tag.getInt(TAG_COLOR)));
        }
        final boolean onLauncher = tag.getBoolean(TAG_ON_LAUNCHER);
        entityData.set(DATA_ON_LAUNCHER, onLauncher);
        if (onLauncher && tag.hasUUID(TAG_LAUNCHER_UUID)) {
            mountedLauncherUuid = tag.getUUID(TAG_LAUNCHER_UUID);
            mountedLauncherId = -1;
        } else {
            mountedLauncherUuid = null;
            mountedLauncherId = -1;
        }
        this.projectileHitCount = tag.getInt(TAG_PROJECTILE_HITS);
        this.crippledHorizontalTargetSpeed = tag.contains(TAG_DAMAGE_TARGET_SPEED) ? tag.getDouble(TAG_DAMAGE_TARGET_SPEED) : -1.0D;
        if (this.projectileHitCount <= 0) {
            this.crippledHorizontalTargetSpeed = -1.0D;
        }
        this.damageSmokeAccumulator = 0.0D;
        
        if (tag.hasUUID(TAG_CONTROLLER)) {
            this.controllingPlayer = tag.getUUID(TAG_CONTROLLER);
            if (tag.contains(TAG_SESS_X)) {
                Vec3 origin = new Vec3(tag.getDouble(TAG_SESS_X), tag.getDouble(TAG_SESS_Y), tag.getDouble(TAG_SESS_Z));
                float yaw = tag.getFloat(TAG_SESS_YAW);
                float pitch = tag.getFloat(TAG_SESS_PITCH);
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString(TAG_SESS_DIM)));
                GameType gm = GameType.byId(tag.getInt(TAG_SESS_GM));
                this.controlSession = new ControlSession(dim, origin, yaw, pitch, gm);
            }
        }

        updateBoundingBox();
    }

    @Override
    protected void addAdditionalSaveData(final CompoundTag tag) {
        tag.putFloat(TAG_THRUST, getThrust());
        final ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(linearVelocity.x));
        list.add(DoubleTag.valueOf(linearVelocity.y));
        list.add(DoubleTag.valueOf(linearVelocity.z));
        tag.put(TAG_LINEAR_VELOCITY, list);
        if (ownerUUID != null) {
            tag.putUUID(TAG_OWNER, ownerUUID);
            tag.putInt(TAG_OWNER_VIEW, ownerViewDistance);
        }
        tag.putBoolean(TAG_ARMED, armed);
        tag.putDouble(TAG_LAUNCH_Y, launchBaselineY);
        tag.putDouble(TAG_FUEL, fuelMass);
        tag.putDouble(TAG_BODY_YAW, bodyYaw);
        tag.putDouble(TAG_BODY_PITCH, bodyPitch);
        tag.putDouble(TAG_BODY_ROLL, bodyRoll);
        tag.putBoolean(TAG_REMOTE_INIT, remoteInitialized);
        tag.putInt(TAG_COLOR, getColor().getId());
        if (isOnLauncher() && mountedLauncherUuid != null) {
            tag.putBoolean(TAG_ON_LAUNCHER, true);
            tag.putUUID(TAG_LAUNCHER_UUID, mountedLauncherUuid);
        }
        if (projectileHitCount > 0) {
            tag.putInt(TAG_PROJECTILE_HITS, projectileHitCount);
            tag.putDouble(TAG_DAMAGE_TARGET_SPEED, Math.max(0.0D, crippledHorizontalTargetSpeed));
        }
        
        if (controllingPlayer != null) {
            tag.putUUID(TAG_CONTROLLER, controllingPlayer);
        }
        if (controlSession != null) {
            tag.putDouble(TAG_SESS_X, controlSession.originPos.x);
            tag.putDouble(TAG_SESS_Y, controlSession.originPos.y);
            tag.putDouble(TAG_SESS_Z, controlSession.originPos.z);
            tag.putFloat(TAG_SESS_YAW, controlSession.originYaw);
            tag.putFloat(TAG_SESS_PITCH, controlSession.originPitch);
            tag.putString(TAG_SESS_DIM, controlSession.originDimension.location().toString());
            tag.putInt(TAG_SESS_GM, controlSession.originalGameType.getId());
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand) {
        final ItemStack heldItem = player.getItemInHand(hand);
        if (heldItem.getItem() instanceof MonitorItem) {
            MonitorItem.setLinkedDrone(heldItem, this.getUUID());
            if (player instanceof ServerPlayer serverPlayer) {
                assignOwner(serverPlayer);
            }
            player.displayClientMessage(Component.translatable("message.fullfud.monitor.linked"), true);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (heldItem.isEmpty() && !level().isClientSide) {
            if (armed) {
                player.displayClientMessage(Component.translatable("message.fullfud.shahed.armed"), true);
                return InteractionResult.FAIL;
            }
            final ItemStack droneStack = new ItemStack(getColor() == ShahedColor.BLACK
                ? FullfudRegistries.SHAHED_BLACK_ITEM.get()
                : FullfudRegistries.SHAHED_ITEM.get());
            if (!player.addItem(droneStack)) {
                spawnAtLocation(droneStack);
            }
            player.displayClientMessage(Component.translatable("message.fullfud.shahed.picked_up"), true);
            discard();
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    public void addViewer(final ServerPlayer player) {
        viewerDistances.put(player.getUUID(), resolveViewDistance(player));
        sendStatusTo(player);
        recalcDesiredChunkRadius();
        ensureChunkTicket();
    }

    public void removeViewer(final ServerPlayer player) {
        viewerDistances.remove(player.getUUID());
        recalcDesiredChunkRadius();
        ensureChunkTicket();
    }

    public void applyControl(final ShahedControlPacket packet, final ServerPlayer sender) {
        if (!Float.isFinite(packet.thrustDelta())) {
            releaseCameraFor(sender);
            return;
        }
        if (!canReceiveControl()) {
            return;
        }
        this.controlForward = Mth.clamp(packet.forward(), -1.0F, 1.0F);
        this.controlStrafe = Mth.clamp(packet.strafe(), -1.0F, 1.0F);
        this.controlVertical = Mth.clamp(packet.vertical(), -1.0F, 1.0F);
        this.controlTimeout = CONTROL_TIMEOUT_TICKS;
        final float newThrust = Mth.clamp(getThrust() + packet.thrustDelta(), 0.0F, 1.0F);
        setThrust(newThrust);
    }

    private boolean canReceiveControl() {
        return !jammerSuppressControls;
    }

    private void updateJammerState() {
        jammerOverride = 0.0F;
        jammerSuppressControls = false;
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final double maxRadius = JAMMER_MAX_RADIUS;
        final AABB searchBox = new AABB(
            getX() - maxRadius, getY() - 5.0D, getZ() - maxRadius,
            getX() + maxRadius, getY() + 5.0D, getZ() + maxRadius
        );
        for (final RebEmitterEntity emitter : serverLevel.getEntitiesOfClass(RebEmitterEntity.class, searchBox)) {
            if (!emitter.hasBattery() || emitter.getChargeTicks() <= 0) {
                continue;
            }
            final double dx = emitter.getX() - getX();
            final double dz = emitter.getZ() - getZ();
            final double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            final float strength = computeJammerStrength(horizontalDist);
            if (strength > jammerOverride) {
                jammerOverride = strength;
            }
        }
        if (jammerOverride >= 1.0F) {
            jammerSuppressControls = true;
            setThrust(0.0F);
        }
    }

    private static float computeJammerStrength(final double horizontalDist) {
        if (horizontalDist >= JAMMER_MAX_RADIUS) {
            return 0.0F;
        }
        if (horizontalDist <= JAMMER_HARD_RADIUS) {
            return 1.0F;
        }
        final double normalized = (horizontalDist - JAMMER_HARD_RADIUS) / (JAMMER_MAX_RADIUS - JAMMER_HARD_RADIUS);
        return (float) (1.0D - 0.99D * normalized);
    }

    private void broadcastStatus() {
        if (viewerDistances.isEmpty() || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (final UUID viewerId : Set.copyOf(viewerDistances.keySet())) {
            final ServerPlayer viewer = serverLevel.getServer().getPlayerList().getPlayer(viewerId);
            if (viewer == null) {
                viewerDistances.remove(viewerId);
                continue;
            }
            sendStatusTo(viewer);
        }
    }

    private void sendStatusTo(final ServerPlayer viewer) {
        final double distance = computeSignalDistance(viewer);
        final float noise = Math.max(computeNoise(distance), jammerOverride);
        final boolean signalLost = distance > 10000.0D;
        final FlightTelemetry data = telemetry == null ? FlightTelemetry.ZERO : telemetry;
        final ShahedStatusPacket packet = new ShahedStatusPacket(
                this.getUUID(),
                this.getX(),
                this.getY(),
                this.getZ(),
                this.getYRot(),
                this.getXRot(),
                getThrust(),
                noise,
                signalLost,
                data.airSpeed(),
                data.groundSpeed(),
                data.verticalSpeed(),
                data.angleOfAttack(),
                data.slipAngle(),
                data.fuelKg(),
                data.airDensity()
        );
        FullfudNetwork.getChannel().send(PacketDistributor.PLAYER.with(() -> viewer), packet);
    }

    @Override
    public void registerControllers(final AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "flight", state -> {
            if (shouldUseRunningAnimation()) {
                state.setAndContinue(RUN_ANIMATION);
            } else {
                state.setAndContinue(IDLE_ANIMATION);
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    private boolean shouldUseRunningAnimation() {
        return getThrust() > 0.2F || linearVelocity.lengthSqr() > 4.0D;
    }

    private void updateBoundingBox() {
        final float width = 3.0F;
        final float height = 1.0F;
        final float halfWidth = width * 0.5F;
        final Vec3 center = position();
        final double yawRad = Math.toRadians(getYRot());
        final double cos = Math.cos(yawRad);
        final double sin = Math.sin(yawRad);
        final Vec3[] corners = new Vec3[] {
                new Vec3(-halfWidth, 0, -halfWidth),
                new Vec3(halfWidth, 0, -halfWidth),
                new Vec3(halfWidth, 0, halfWidth),
                new Vec3(-halfWidth, 0, halfWidth)
        };
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 corner : corners) {
            final double rotX = corner.x * cos - corner.z * sin;
            final double rotZ = corner.x * sin + corner.z * cos;
            minX = Math.min(minX, center.x + rotX);
            minZ = Math.min(minZ, center.z + rotZ);
            maxX = Math.max(maxX, center.x + rotX);
            maxZ = Math.max(maxZ, center.z + rotZ);
        }
        final double minY = getY();
        final double maxY = getY() + height;
        final AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        setBoundingBox(box);
    }

    private double computeSignalDistance(final ServerPlayer viewer) {
        if (controllingPlayer != null && controllingPlayer.equals(viewer.getUUID()) && controlSession != null) {
            if (controlSession.originPos == null) {
                return 0.0D;
            }
            if (!level().dimension().equals(controlSession.originDimension)) {
                return Double.POSITIVE_INFINITY;
            }
            return Math.sqrt(controlSession.originPos.distanceToSqr(this.position()));
        }
        return Math.sqrt(viewer.distanceToSqr(this));
    }

    private static float computeNoise(final double distance) {
        if (distance <= 0.0D) {
            return 0.0F;
        }
        return (float) Math.min(distance / 10000.0D, 0.5D);
    }

    private void setThrust(final float thrust) {
        this.entityData.set(DATA_THRUST, thrust);
    }

    public float getThrust() {
        return this.entityData.get(DATA_THRUST);
    }

    @Override
    public EntityDimensions getDimensions(final Pose pose) {
        return SHAHEED_DIMENSIONS;
    }

    @Override
    public boolean hurt(final DamageSource source, final float amount) {
        if (source.getDirectEntity() instanceof Projectile) {
            if (!level().isClientSide()) {
                handleProjectileImpact();
            }
            return true;
        }
        return super.hurt(source, amount);
    }

    private void handleProjectileImpact() {
        projectileHitCount++;
        if (projectileHitCount >= 2) {
            detonate();
            return;
        }
        final double horizontalSpeed = Math.sqrt(linearVelocity.x * linearVelocity.x + linearVelocity.z * linearVelocity.z);
        crippledHorizontalTargetSpeed = Math.max(horizontalSpeed, 0.0D);
        damageSmokeAccumulator = 0.0D;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    public ShahedColor getColor() {
        return ShahedColor.byId(entityData.get(DATA_COLOR));
    }

    public void setColor(final ShahedColor color) {
        entityData.set(DATA_COLOR, color.getId());
    }

    public void setLaunchVelocity(final Vec3 velocity) {
        this.linearVelocity = velocity;
        setDeltaMovement(linearVelocity.scale(TICK_SECONDS));
    }

    public boolean isOnLauncher() {
        return entityData.get(DATA_ON_LAUNCHER);
    }

    public UUID getLauncherUuid() {
        return mountedLauncherUuid;
    }

    public void mountLauncher(final ShahedLauncherEntity launcher) {
        mountedLauncherId = launcher.getId();
        mountedLauncherUuid = launcher.getUUID();
        entityData.set(DATA_ON_LAUNCHER, true);
        setNoGravity(true);
        linearVelocity = Vec3.ZERO;
        setDeltaMovement(Vec3.ZERO);
        updateLauncherPose(launcher);
    }

    public void launchFromLauncher(final ShahedLauncherEntity launcher) {
        final float yaw = launcher.getYRot();
        final Vec3 forward = Vec3.directionFromRotation(0.0F, yaw).normalize();
        final Vec3 base = launcher.position().add(0.0D, LAUNCHER_VERTICAL_OFFSET, 0.0D);
        final Vec3 spawn = base.add(forward.scale(LAUNCHER_FORWARD_OFFSET)).add(0.0D, LAUNCHER_UP_OFFSET, 0.0D);
        setPos(spawn.x, spawn.y, spawn.z);
        releaseFromLauncher(new Vec3(forward.x * LAUNCHER_LAUNCH_SPEED, 0.0D, forward.z * LAUNCHER_LAUNCH_SPEED), yaw);
    }

    public void releaseFromLauncher(final Vec3 velocity, final float launcherYaw) {
        entityData.set(DATA_ON_LAUNCHER, false);
        setNoGravity(false);
        mountedLauncherId = -1;
        mountedLauncherUuid = null;
        setLaunchVelocity(velocity);
        final float yaw = launcherYaw;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        this.bodyYaw = yaw;
        this.bodyPitch = -10.0D;
        this.bodyRoll = 0.0D;
        this.bodyRollO = 0.0D;
        setXRot((float) this.bodyPitch);
    }

    private void handleLauncherAttachment() {
        setDeltaMovement(Vec3.ZERO);
        final ShahedLauncherEntity launcher = resolveLauncher();
        if (launcher != null) {
            updateLauncherPose(launcher);
            return;
        }
        if (!level().isClientSide) {
            final ItemStack stack = new ItemStack(getColor() == ShahedColor.BLACK
                ? FullfudRegistries.SHAHED_BLACK_ITEM.get()
                : FullfudRegistries.SHAHED_ITEM.get());
            spawnAtLocation(stack);
            discard();
        }
    }

    private ShahedLauncherEntity resolveLauncher() {
        if (mountedLauncherId > 0) {
            final Entity entity = level().getEntity(mountedLauncherId);
            if (entity instanceof ShahedLauncherEntity launcher) {
                return launcher;
            }
        }
        if (mountedLauncherUuid != null && level() instanceof ServerLevel serverLevel) {
            final Entity entity = serverLevel.getEntity(mountedLauncherUuid);
            if (entity instanceof ShahedLauncherEntity launcher) {
                mountedLauncherId = launcher.getId();
                return launcher;
            }
        }
        return null;
    }

    private void updateLauncherPose(final ShahedLauncherEntity launcher) {
        final Vec3 anchor = launcher.position().add(0.0D, LAUNCHER_VERTICAL_OFFSET, 0.0D);
        setPos(anchor.x, anchor.y, anchor.z);
        final float yaw = launcher.getYRot();
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        this.bodyYaw = yaw;
        this.bodyPitch = 0.0D;
        this.bodyRoll = 0.0D;
        this.bodyRollO = 0.0D;
        setXRot(0.0F);
    }

    @Override
    public void remove(final RemovalReason reason) {
        if (!level().isClientSide()) {
            final ServerPlayer controller = getControllingPlayer();
            if (controller != null) {
                forceReturnCamera(controller);
                endRemoteControl(controller);
            } else if (controllingPlayer != null && level() instanceof ServerLevel serverLevel) {
                final ServerPlayer offlineController = serverLevel.getServer().getPlayerList().getPlayer(controllingPlayer);
                if (offlineController != null) {
                    forceReturnCamera(offlineController);
                    endRemoteControl(offlineController);
                }
            }
            if (level() instanceof ServerLevel serverLevel) {
                ShahedLinkData.get(serverLevel).unlink(getUUID());
            }
            viewerDistances.clear();
            releaseChunkTicket();
        }
        super.remove(reason);
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide() && ownerUUID != null && level() instanceof ServerLevel serverLevel) {
            ShahedLinkData.get(serverLevel).link(getUUID(), ownerUUID);
            recalcDesiredChunkRadius();
            ensureChunkTicket();
        }
    }

    public void assignOwner(final ServerPlayer player) {
        this.ownerUUID = player.getUUID();
        this.ownerViewDistance = resolveViewDistance(player);
        if (level() instanceof ServerLevel serverLevel) {
            ShahedLinkData.get(serverLevel).link(getUUID(), ownerUUID);
        }
        recalcDesiredChunkRadius();
        ensureChunkTicket();
    }

    public Optional<UUID> getOwnerUUID() {
        return Optional.ofNullable(ownerUUID);
    }

    private int resolveViewDistance(final ServerPlayer player) {
        return Math.max(2, player.serverLevel().getServer().getPlayerList().getViewDistance());
    }

    private void recalcDesiredChunkRadius() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final int serverCap = Math.max(2, serverLevel.getServer().getPlayerList().getViewDistance());
        int desired = 0;
        for (final int distance : viewerDistances.values()) {
            desired = Math.max(desired, Mth.clamp(distance, 2, serverCap));
        }
        if (ownerUUID != null) {
            desired = Math.max(desired, Mth.clamp(ownerViewDistance, 2, serverCap));
        }
        if (desired == 0) {
            desired = serverCap;
        }
        this.desiredChunkRadius = desired;
    }

    private void ensureChunkTicket() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (desiredChunkRadius <= 0) {
            recalcDesiredChunkRadius();
        }
        if (desiredChunkRadius <= 0) {
            releaseChunkTicket();
            return;
        }
        final ChunkPos chunkPos = this.chunkPosition();
        if (chunkPos.equals(lastTicketPos) && desiredChunkRadius == lastTicketRadius) {
            return;
        }
        releaseChunkTicket();
        final ServerChunkCache chunkSource = serverLevel.getChunkSource();
        chunkSource.addRegionTicket(SHAHED_TICKET, chunkPos, desiredChunkRadius, this.getId());
        lastTicketPos = chunkPos;
        lastTicketRadius = desiredChunkRadius;
    }

    private void releaseChunkTicket() {
        if (!(level() instanceof ServerLevel serverLevel) || lastTicketPos == null) {
            lastTicketPos = null;
            lastTicketRadius = 0;
            return;
        }
        serverLevel.getChunkSource().removeRegionTicket(SHAHED_TICKET, lastTicketPos, Math.max(1, lastTicketRadius), this.getId());
        lastTicketPos = null;
        lastTicketRadius = 0;
    }

    private ServerPlayer getControllingPlayer() {
        if (controllingPlayer == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getServer().getPlayerList().getPlayer(controllingPlayer);
    }

    private void updateControllerBinding() {
        final ServerPlayer player = getControllingPlayer();
        if (player == null) {
            if (avatar != null) {
                removeAvatar();
            }
            return;
        }

        player.setInvisible(true);
        player.setSilent(true);
        player.setNoGravity(true);
        player.noPhysics = true;

        if (controlSession != null && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        }
        
        if (player.level() != level()) {
            player.teleportTo((ServerLevel) level(), getX(), getY() + 1.0D, getZ(), getYRot(), getXRot());
            if (cameraPinned) {
                player.connection.send(new ClientboundSetCameraPacket(this));
            }
        } else if ((this.tickCount & 1) == 0) {
            player.connection.teleport(getX(), getY() + 1.0D, getZ(), player.getYRot(), player.getXRot());
            if (cameraPinned) {
                player.connection.send(new ClientboundSetCameraPacket(this));
            }
        }

        syncAvatar(player);
    }

    private void updateLaunchState() {
        if (!armed) {
            final double altitudeGain = this.getY() - launchBaselineY;
            final double verticalSpeed = Math.abs(linearVelocity.y);
            if (altitudeGain > 1.5D || verticalSpeed > 5.0D) {
                armed = true;
            }
        }
    }

    private boolean detectImpact() {
        if (this.horizontalCollision || this.verticalCollision) {
            return hasDangerousSpeed();
        }
        final BlockState state = level().getBlockState(blockPosition());
        final BlockState below = level().getBlockState(blockPosition().below());
        if (!state.isAir() || !below.isAir()) {
            return hasDangerousSpeed();
        }
        final int x = Mth.floor(getX());
        final int z = Mth.floor(getZ());
        final int terrainY = level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return this.getY() <= terrainY + 0.15D && hasDangerousSpeed();
    }

    private boolean hasDangerousSpeed() {
        return linearVelocity.lengthSqr() > 1.0D;
    }

    private void detonate() {
        if (level().isClientSide()) {
            return;
        }
        final ServerPlayer controller = getControllingPlayer();
        if (controller != null) {
            forceReturnCamera(controller);
            endRemoteControl(controller);
        }
        spawnSecondaryCharges();
        level().explode(this, getX(), getY(), getZ(), 10.0F, ExplosionInteraction.MOB);
        applyBlastDamage();
        discard();
    }

    private void spawnSecondaryCharges() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final RandomSource random = serverLevel.getRandom();
        for (int i = 0; i < 10; i++) {
            final PrimedTnt charge = EntityType.TNT.create(serverLevel);
            if (charge == null) {
                continue;
            }
            final double spread = 2.0D;
            final double offsetX = (random.nextDouble() - 0.5D) * spread;
            final double offsetY = random.nextDouble() * 0.6D;
            final double offsetZ = (random.nextDouble() - 0.5D) * spread;
            charge.moveTo(getX() + offsetX, getY() + offsetY, getZ() + offsetZ, this.getYRot(), this.getXRot());
            charge.setFuse(0);
            serverLevel.addFreshEntity(charge);
        }
    }

    private void applyBlastDamage() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final double radius = 20.0D;
        final AABB area = new AABB(getX() - radius, getY() - 6.0D, getZ() - radius, getX() + radius, getY() + 6.0D, getZ() + radius);
        final var damageSource = serverLevel.damageSources().explosion(this, this);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
            if (target.isInvulnerableTo(damageSource)) {
                continue;
            }
            final double distance = Math.sqrt(target.distanceToSqr(this));
            if (distance <= 15.0D) {
                target.hurt(damageSource, 100.0F);
            } else if (distance <= 20.0D) {
                target.hurt(damageSource, 50.0F);
            }
        }
    }

    public void initializePlacement(final double yPosition) {
        this.launchBaselineY = yPosition;
        this.armed = false;
        this.linearVelocity = Vec3.ZERO;
        this.setDeltaMovement(Vec3.ZERO);
        this.controlForward = 0.0F;
        this.controlStrafe = 0.0F;
        this.controlVertical = 0.0F;
        this.rollRate = 0.0D;
        this.pitchRate = 0.0D;
        this.fuelMass = FUEL_CAPACITY_KG;
        this.telemetry = FlightTelemetry.ZERO;
        this.bodyYaw = this.getYRot();
        this.bodyPitch = 0.0D;
        this.bodyPitchO = 0.0D;
        this.bodyRoll = 0.0D;
        this.bodyRollO = 0.0D;
        this.setXRot((float) bodyPitch);
        this.engineOutput = 0.0D;
        this.remoteInitialized = false;
    }

    public boolean beginRemoteControl(final ServerPlayer player) {
        if (controllingPlayer != null && !controllingPlayer.equals(player.getUUID())) {
            return false;
        }
        if (isOnLauncher()) {
            final ShahedLauncherEntity launcher = resolveLauncher();
            if (launcher != null) {
                launchFromLauncher(launcher);
            } else {
                entityData.set(DATA_ON_LAUNCHER, false);
            }
        }
        if (controlSession == null) {
            controlSession = new ControlSession(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer());
            spawnAvatar(player);
        }
        if (!remoteInitialized) {
            ensureFlightAltitude();
            final double launchSpeed = Math.min(MAX_AIRSPEED * 0.95D, INITIAL_LAUNCH_SPEED);
            this.linearVelocity = forwardGroundVector().scale(launchSpeed);
            setThrust(1.0F);
            this.engineOutput = 1.0D;
            this.remoteInitialized = true;
            this.bodyPitch = -10.0D; 
            this.setXRot((float) bodyPitch);
        }
        controllingPlayer = player.getUUID();
        bindPlayerToDrone(player);
        player.connection.send(new ClientboundSetCameraPacket(this));
        cameraPinned = true;
        return true;
    }

    public void endRemoteControl(final ServerPlayer player) {
        if (controlSession == null) {
            player.setGameMode(GameType.SURVIVAL);
            player.setInvisible(false);
            player.setSilent(false);
            player.setNoGravity(false);
            player.noPhysics = false;
            removeAvatar();
            return;
        }
        
        if (controllingPlayer != null && !controllingPlayer.equals(player.getUUID())) {
            return;
        }
        
        forceReturnCamera(player);
        restorePlayer(player);
        
        controllingPlayer = null;
        controlSession = null;
        removeAvatar();
        cameraPinned = false;
    }

    private void bindPlayerToDrone(final ServerPlayer player) {
        player.setInvisible(true);
        player.setSilent(true);
        player.setNoGravity(true);
        player.noPhysics = true;
        player.setDeltaMovement(Vec3.ZERO);
        if (controlSession != null && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        }
        if (player.level() != level()) {
            player.teleportTo((ServerLevel) level(), getX(), getY() + 1.0D, getZ(), getYRot(), getXRot());
        }
        player.onUpdateAbilities();
    }

    private void restorePlayer(final ServerPlayer player) {
        if (controlSession == null) {
            player.setGameMode(GameType.SURVIVAL);
            player.setInvisible(false);
            player.setSilent(false);
            player.setNoGravity(false);
            player.noPhysics = false;
            return;
        }

        ServerLevel originLevel = player.getServer().getLevel(controlSession.originDimension);
        if (originLevel == null) {
            originLevel = (ServerLevel) level();
        }
        
        final ChunkPos chunkPos = new ChunkPos(net.minecraft.core.BlockPos.containing(controlSession.originPos));
        originLevel.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkPos, 1, player.getId());
        
        player.teleportTo(
            originLevel,
            controlSession.originPos.x,
            controlSession.originPos.y,
            controlSession.originPos.z,
            controlSession.originYaw,
            controlSession.originPitch
        );

        player.setInvisible(false);
        player.setSilent(false);
        player.setNoGravity(false);
        player.noPhysics = false;
        player.setDeltaMovement(Vec3.ZERO);

        if (controlSession.originalGameType != null) {
            player.setGameMode(controlSession.originalGameType);
        } else {
            player.setGameMode(GameType.SURVIVAL);
        }
        
        player.onUpdateAbilities();
        removeAvatar();
    }

    private void spawnAvatar(final ServerPlayer player) {
        if (!(level() instanceof ServerLevel serverLevel) || controlSession == null) {
            return;
        }
        removeAvatar();

        GameProfile profile = new GameProfile(UUID.randomUUID(), player.getGameProfile().getName());
        player.getGameProfile().getProperties().forEach((name, prop) -> {
            profile.getProperties().put(name, new Property(prop.getName(), prop.getValue(), prop.getSignature()));
        });

        avatar = new RemotePilotFakePlayer(serverLevel, profile, player.getUUID());
        avatar.syncFrom(player);
        avatar.setPos(controlSession.originPos.x, controlSession.originPos.y, controlSession.originPos.z);
        avatar.setYRot(controlSession.originYaw);
        avatar.setXRot(controlSession.originPitch);
        avatar.yHeadRot = controlSession.originYaw;
        avatar.yBodyRot = controlSession.originYaw;
        avatar.setDeltaMovement(Vec3.ZERO);
        avatar.setCustomName(Component.literal(player.getName().getString() + " [UAV]"));
        avatar.setCustomNameVisible(true);
        broadcastAvatarInfo(true);
        serverLevel.addFreshEntity(avatar);
    }

    private void syncAvatar(final ServerPlayer player) {
        if (avatar == null || avatar.isRemoved()) {
            spawnAvatar(player);
        } else {
            avatar.syncEquipment(player);
        }
    }

    private void removeAvatar() {
        if (avatar != null) {
            broadcastAvatarInfo(false);
            avatar.discard();
            avatar = null;
        }
    }

    private void dbg(ServerPlayer p, String msg) {
        if (p != null) {
            p.displayClientMessage(Component.literal("[SHAHED] " + msg), true);
        }
    }

    private void releaseCameraFor(final ServerPlayer player) {
        cameraPinned = false;
        forceReturnCamera(player);
    }

    private boolean isSignalLostFor(final ServerPlayer p) {
        return p == null || Math.sqrt(p.distanceToSqr(this)) > 10000.0D;
    }

    private void forceReturnCamera(final ServerPlayer player) {
        if (player == null || player.connection == null) {
            LOG.warn("[DRONE {}] forceReturnCamera: player or connection null", this.getStringUUID());
            return;
        }
        player.connection.send(new ClientboundSetCameraPacket(player));
    }

    private void broadcastAvatarInfo(final boolean add) {
        if (avatar == null || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (add) {
            final ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    avatar
            );
            for (final ServerPlayer viewer : serverLevel.getServer().getPlayerList().getPlayers()) {
                viewer.connection.send(packet);
            }
        } else {
            final ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(java.util.List.of(avatar.getUUID()));
            for (final ServerPlayer viewer : serverLevel.getServer().getPlayerList().getPlayers()) {
                viewer.connection.send(packet);
            }
        }
    }

    private record OrientationBasis(Vec3 forward, Vec3 up, Vec3 right) { }

    private record FlightTelemetry(float airSpeed, float groundSpeed, float verticalSpeed, float angleOfAttack, float slipAngle, float throttle, float fuelKg, float airDensity) {
        static final FlightTelemetry ZERO = new FlightTelemetry(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    private static double approach(final double current, final double target, final double maxStep) {
        final double delta = Mth.clamp(target - current, -maxStep, maxStep);
        return current + delta;
    }

    private static final class ControlSession {
        final ResourceKey<Level> originDimension;
        final Vec3 originPos;
        final float originYaw;
        final float originPitch;
        final GameType originalGameType;

        private ControlSession(final ResourceKey<Level> originDimension, final Vec3 originPos, final float originYaw, final float originPitch, final GameType originalGameType) {
            this.originDimension = originDimension;
            this.originPos = originPos;
            this.originYaw = originYaw;
            this.originPitch = originPitch;
            this.originalGameType = originalGameType;
        }
    }

    private static final class RemotePilotFakePlayer extends FakePlayer {
        private final UUID ownerId;
        private boolean forwardingDamage;

        private RemotePilotFakePlayer(final ServerLevel level, final GameProfile profile, final UUID ownerId) {
            super(level, profile);
            this.ownerId = ownerId;
            this.setNoGravity(true);
            this.noPhysics = true;
        }

        private void syncFrom(final ServerPlayer player) {
            syncEquipment(player);
            this.setHealth(player.getHealth());
            this.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel());
            this.getFoodData().setSaturation(player.getFoodData().getSaturationLevel());
            this.removeAllEffects();
            player.getActiveEffects().forEach(effect -> this.addEffect(new MobEffectInstance(effect)));
        }

        private void syncEquipment(final ServerPlayer player) {
            for (int i = 0; i < this.getInventory().getContainerSize(); ++i) {
                final ItemStack stack = player.getInventory().getItem(i);
                this.getInventory().setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
            for (final EquipmentSlot slot : EquipmentSlot.values()) {
                final ItemStack stack = player.getItemBySlot(slot);
                this.setItemSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
        }

        @Override
        public boolean hurt(final DamageSource source, final float amount) {
            if (!forwardingDamage) {
                forwardingDamage = true;
                final ServerPlayer owner = getOwner();
                if (owner != null && !owner.isDeadOrDying()) {
                    owner.hurt(source, amount);
                }
                forwardingDamage = false;
            } 
            return super.hurt(source, amount);
        }

        @Override
        public void die(final DamageSource source) {
            final ServerPlayer owner = getOwner();
            if (owner != null && !owner.isDeadOrDying()) {
                owner.die(source);
            }
            super.die(source);
        }

        private ServerPlayer getOwner() {
            if (!(this.level() instanceof ServerLevel serverLevel)) {
                return null;
            }
            return serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        }
    }
}