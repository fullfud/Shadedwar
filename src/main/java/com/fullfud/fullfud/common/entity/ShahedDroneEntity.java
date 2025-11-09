package com.fullfud.fullfud.common.entity;

import com.fullfud.fullfud.common.item.MonitorItem;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.data.ShahedLinkData;
import com.fullfud.fullfud.core.network.FullfudNetwork;
import com.fullfud.fullfud.core.network.packet.ShahedControlPacket;
import com.fullfud.fullfud.core.network.packet.ShahedStatusPacket;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ShahedDroneEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Float> DATA_THRUST = SynchedEntityData.defineId(ShahedDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final String TAG_THRUST = "Thrust";
    private static final String TAG_MOTION = "Motion";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_VIEW = "OwnerView";
    private static final String TAG_ARMED = "Armed";
    private static final String TAG_LAUNCH_Y = "LaunchY";
    private static final String TAG_BODY_YAW = "BodyYaw";
    private static final String TAG_BODY_PITCH = "BodyPitch";
    private static final String TAG_REMOTE_INIT = "RemoteInit";
    private static final int STATUS_INTERVAL = 1;
    private static final int CONTROL_TIMEOUT_TICKS = 20;
    private static final double TICK_SECONDS = 1.0D / 20.0D;
    private static final double BASE_MASS_KG = 210.0D;
    private static final double FUEL_CAPACITY_KG = 45.0D;
    private static final double FUEL_CONSUMPTION_PER_SEC = 0.32D;
    private static final double MAX_THRUST_FORCE = 600.0D;
    private static final double BOOST_MULTIPLIER = 1.15D;
    private static final double RHO_SEA_LEVEL = 1.225D;
    private static final double ATMOSPHERE_SCALE_HEIGHT = 8500.0D;
    private static final double WING_AREA = 3.3D;
    private static final double ASPECT_RATIO = 4.6D;
    private static final double CL_ALPHA = 5.0D;
    private static final double CL_MAX = 1.5D;
    private static final double CL_ZERO = 0.20D;
    private static final double CD_MIN = 0.052D;
    private static final double DRAG_FACTOR = 0.055D;
    private static final double CY_BETA = -0.85D;
    private static final double CONTROL_EAS_MIN = 12.0D;
    private static final double CONTROL_EAS_MAX = 45.0D;
    private static final double GRAVITY = 4.0D;
    private static final double MAX_MANUAL_YAW_RATE = 150.0D;
    private static final double MAX_MANUAL_PITCH_RATE = 75.0D;
    private static final double YAW_ACCEL_LIMIT = 720.0D;
    private static final double PITCH_ACCEL_LIMIT = 540.0D;
    private static final double HEADING_HOLD_GAIN = 0.0D;
    private static final double GROUND_FRICTION = 0.62D;
    private static final double MAX_AIRSPEED = 36.111D; 
    private static final double ENGINE_IDLE_THRUST = 0.0D;
    private static final double ENGINE_SPOOL_RATE = 0.05D;
    private static final double MAX_ELEVATOR_AOA = Math.toRadians(11.0D);
    private static final double SLIP_DAMPING = 2.0D;
    private static final double PITCH_DAMPING = 0.12D;
    private static final double DEFAULT_TRIM_PITCH = 6.0D;
    private static final double STALL_ANGLE = Math.toRadians(17.0D);
    private static final double MAX_VISUAL_ROLL = 55.0D;
    private static final double VISUAL_ROLL_ACCEL = 540.0D;
    private static final double VISUAL_PITCH_ACCEL = 360.0D;
    private static final int CLIENT_INTERPOLATION_FACTOR = 100;
    private static final int CONTROL_HISTORY_TICKS = 100;
    private static final String TAG_LINEAR_VELOCITY = "LinearVelocity";
    private static final String TAG_FUEL = "FuelMass";
    private static final TicketType<Integer> SHAHED_TICKET = TicketType.create("fullfud_shahed", Integer::compareTo, 4);

    private final Map<UUID, Integer> viewerDistances = new HashMap<>();
    private float controlForward;
    private float controlStrafe;
    private float controlVertical;
    private float resolvedVerticalInput;
    private Vec3 linearVelocity = Vec3.ZERO;
    private boolean boostActive;
    private int controlTimeout;
    private double yawRate;
    private double pitchRate;
    private UUID ownerUUID;
    private int ownerViewDistance = 8;
    private ChunkPos lastTicketPos;
    private int lastTicketRadius;
    private int desiredChunkRadius;
    private boolean armed;
    private double launchBaselineY;
    private UUID controllingPlayer;
    private ControlSession controlSession;
    private RemotePilotFakePlayer avatar;
    private double fuelMass = FUEL_CAPACITY_KG;
    private FlightTelemetry telemetry = FlightTelemetry.ZERO;
    private double bodyYaw;
    private double bodyPitch;
    private double engineOutput;
    private boolean remoteInitialized;
    private double visualRoll;
    private double prevVisualRoll;
    private double visualPitch;
    private double prevVisualPitch;
    private final float[] strafeHistory = new float[CONTROL_HISTORY_TICKS];
    private int strafeHistoryIndex;
    private int strafeHistorySize;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("animation.model.idle");
    private static final RawAnimation RUN_ANIMATION = RawAnimation.begin().thenLoop("animation.model.running");

    public ShahedDroneEntity(final EntityType<? extends ShahedDroneEntity> entityType, final Level level) {
        super(entityType, level);
        this.noPhysics = false;
        this.setNoGravity(true);
        this.launchBaselineY = this.getY();
        this.bodyYaw = this.getYRot();
        this.bodyPitch = DEFAULT_TRIM_PITCH;
        this.setXRot((float) bodyPitch);
        this.engineOutput = 0.0D;
        this.visualRoll = 0.0D;
        this.prevVisualRoll = 0.0D;
        this.visualPitch = bodyPitch;
        this.prevVisualPitch = bodyPitch;
        resetStrafeHistory();
        this.remoteInitialized = false;
        this.visualPitch = bodyPitch;
        this.prevVisualPitch = bodyPitch;
        this.visualRoll = 0.0D;
        this.prevVisualRoll = 0.0D;
        this.remoteInitialized = false;
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
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide()) {
            updateControlTimeout();
            updateFlight();
            updateLaunchState();
            if (armed && detectImpact()) {
                detonate();
                return;
            }
            ensureChunkTicket();
            updateControllerBinding();
            if (tickCount % STATUS_INTERVAL == 0) {
                broadcastStatus();
            }
        }
    }

    private void updateControlTimeout() {
        if (controlTimeout > 0) {
            controlTimeout--;
        } else {
            controlForward = 0.0F;
            controlStrafe = 0.0F;
            controlVertical = 0.0F;
            resolvedVerticalInput = 0.0F;
            boostActive = false;
        }
    }

    private void updateFlight() {
        final double dt = TICK_SECONDS;
        float throttle = Mth.clamp(getThrust(), 0.0F, 1.0F);
        boolean boost = boostActive && throttle > 0.01F;
        if (fuelMass > 0.0D && throttle > 0.0F) {
            final double burn = throttle * FUEL_CONSUMPTION_PER_SEC * dt;
            fuelMass = Math.max(0.0D, fuelMass - burn);
        }
        if (fuelMass <= 0.0D) {
            fuelMass = 0.0D;
            if (throttle > 0.0F) {
                throttle = 0.0F;
                setThrust(0.0F);
            }
            boost = false;
            boostActive = false;
        }
        final double totalMass = BASE_MASS_KG + fuelMass;

        final OrientationBasis basis = orientationBasis();
        final Vec3 forward = basis.forward();
        final Vec3 up = basis.up();
        final Vec3 right = basis.right();

        final double speed = linearVelocity.length();
        final double pitchRad = Math.toRadians(bodyPitch);
        final double horizontalSpeed = Math.sqrt(linearVelocity.x * linearVelocity.x + linearVelocity.z * linearVelocity.z);
        final double flightYaw = speed > 1.0E-3D ? Math.atan2(linearVelocity.x, linearVelocity.z) : Math.toRadians(bodyYaw);
        final double flightPitch = speed > 1.0E-3D ? Math.atan2(linearVelocity.y, Math.max(1.0E-3D, horizontalSpeed)) : pitchRad;
        final double altitude = this.getY();
        final double airDensity = sampleAirDensity(altitude);
        final double q = 0.5D * airDensity * speed * speed;

        engineOutput += (throttle - engineOutput) * ENGINE_SPOOL_RATE;
        final double thrustMagnitude = fuelMass <= 0.0D ? 0.0D : computeEffectiveThrust(engineOutput, boost);

        final double forwardSpeedBody = linearVelocity.dot(forward);
        final double verticalSpeedBody = linearVelocity.dot(up);
        final double lateralSpeedBody = linearVelocity.dot(right);
        final double baseAoa = Math.atan2(verticalSpeedBody, Math.max(1.0D, forwardSpeedBody));
        final double orientationBias = Math.toRadians(-6.0D + 8.0D * throttle) + Math.toRadians(-bodyPitch) * 0.18D;
        this.resolvedVerticalInput = resolveVerticalInput(baseAoa, throttle);

        final double authority = 0.2D + 0.8D * throttle;
        final double trimmedInput = Mth.clamp(-resolvedVerticalInput * authority, -1.0D, 1.0D);

        double glideTrim = 0.0D;
        final double idleThreshold = 0.18D;
        if (Math.abs(controlVertical) < 0.05F && throttle < idleThreshold && linearVelocity.y > 0.05D) {
            final double idleFactor = (idleThreshold - throttle) / idleThreshold;
            final double climbRatio = Mth.clamp(linearVelocity.y / 10.0D, 0.0D, 1.0D);
            glideTrim = idleFactor * climbRatio; 
        }

        final double effectiveAoa = baseAoa + orientationBias + trimmedInput * MAX_ELEVATOR_AOA - glideTrim * MAX_ELEVATOR_AOA;
        final double cl = applyStall(resolveLiftCoefficient(effectiveAoa), effectiveAoa);
        final double lift = cl * q * WING_AREA;
        final double cd = CD_MIN + DRAG_FACTOR * cl * cl;
        final Vec3 liftForce = up.scale(lift);
        final Vec3 dragForce = speed > 1.0E-4D ? linearVelocity.normalize().scale(-cd * q * WING_AREA) : Vec3.ZERO;

        final double slip = speed > 1.0E-3D ? wrapRadians(Math.atan2(lateralSpeedBody, Math.max(1.0D, forwardSpeedBody))) : 0.0D;
        final Vec3 lateralForce = right.scale(CY_BETA * slip * q * WING_AREA);
        final Vec3 thrustForce = forward.scale(thrustMagnitude);
        final Vec3 weightForce = new Vec3(0.0D, -totalMass * GRAVITY, 0.0D);

        Vec3 netForce = thrustForce.add(liftForce).add(dragForce).add(lateralForce).add(weightForce);
        if (throttle < 0.15F) {
            final double penalty = 0.8D + (0.2D - throttle) * 3.0D;
            final Vec3 dragFalloff = linearVelocity.lengthSqr() > 1.0E-4D ? linearVelocity.normalize().scale(-penalty) : Vec3.ZERO;
            netForce = netForce.add(dragFalloff);
        }
        final Vec3 acceleration = netForce.scale(1.0D / totalMass);
        linearVelocity = linearVelocity.add(acceleration.scale(dt));
        final double speedCapSq = MAX_AIRSPEED * MAX_AIRSPEED;
        if (linearVelocity.lengthSqr() > speedCapSq) {
            linearVelocity = linearVelocity.normalize().scale(MAX_AIRSPEED);
        }

        final Vec3 displacement = linearVelocity.scale(dt);
        this.setDeltaMovement(displacement);
        this.move(MoverType.SELF, displacement);
        resolveCollisionVelocity();

        final double updatedHorizontalSpeed = Math.sqrt(linearVelocity.x * linearVelocity.x + linearVelocity.z * linearVelocity.z);
        final double updatedSpeed = linearVelocity.length();
        final double updatedFlightYaw = updatedSpeed > 1.0E-3D ? Math.atan2(linearVelocity.x, linearVelocity.z) : Math.toRadians(bodyYaw);
        final double updatedFlightPitch = updatedSpeed > 1.0E-3D ? Math.atan2(linearVelocity.y, Math.max(1.0E-3D, updatedHorizontalSpeed)) : Math.toRadians(bodyPitch);
        final double updatedAoa = Mth.clamp(Math.toRadians(bodyPitch) - updatedFlightPitch, Math.toRadians(-18.0D), Math.toRadians(22.0D));
        final double updatedSlip = updatedSpeed > 1.0E-3D ? wrapRadians(Math.toRadians(bodyYaw) - updatedFlightYaw) : 0.0D;

        integrateAttitude(baseAoa, updatedSlip, updatedSpeed, dt);

        final double flightPitchDeg = Math.toDegrees(updatedFlightPitch);
        final double visualPitchTarget = Mth.clamp(flightPitchDeg, -85.0D, 85.0D);
        recordStrafe(controlStrafe);
        final double strafeBias = computeStrafeBias();
        updateVisualPitch(visualPitchTarget, dt);
        updateVisualRoll(strafeBias, dt);

        telemetry = new FlightTelemetry(
            (float) updatedSpeed,
            (float) updatedHorizontalSpeed,
            (float) linearVelocity.y,
            (float) Math.toDegrees(baseAoa),
            (float) Math.toDegrees(updatedSlip),
            throttle,
            (float) fuelMass,
            (float) airDensity
        );
    }

    private float resolveVerticalInput(final double baseAoa, final float throttle) {
        final float manualThreshold = 0.05F;
        float input = controlVertical;
        if (Math.abs(input) > manualThreshold) {
            return input;
        }

        final float idleThreshold = 0.35F;
        if (throttle >= idleThreshold) {
            return 0.0F;
        }

        final double idleFactor = (idleThreshold - throttle) / idleThreshold;
        final double aoaExcess = Math.max(0.0D, baseAoa - Math.toRadians(2.5D));
        if (aoaExcess <= 1.0E-4D) {
            return 0.0F;
        }

        final double trim = Math.min(1.0D, aoaExcess / Math.toRadians(24.0D)) * idleFactor;
        return (float) Mth.clamp(trim, 0.0D, 1.0D);
    }

    private OrientationBasis orientationBasis() {
        Vec3 forward = Vec3.directionFromRotation((float) bodyPitch, (float) bodyYaw);
        if (forward.lengthSqr() < 1.0E-5D) {
            forward = new Vec3(0.0D, 0.0D, 1.0D);
        }
        forward = forward.normalize();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = forward.cross(up);
        if (right.lengthSqr() < 1.0E-5D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        up = right.cross(forward).normalize();
        return new OrientationBasis(forward, up, right);
    }

    private static double sampleAirDensity(final double altitude) {
        return RHO_SEA_LEVEL * Math.exp(-Math.max(0.0D, altitude) / ATMOSPHERE_SCALE_HEIGHT);
    }

    private static double resolveLiftCoefficient(final double aoaRad) {
        return Mth.clamp(CL_ZERO + CL_ALPHA * aoaRad, -CL_MAX, CL_MAX);
    }

    private static double resolveDragCoefficient(final double cl) {
        return CD_MIN + DRAG_FACTOR * cl * cl;
    }

    private static double wrapRadians(final double radians) {
        return Math.toRadians(Mth.wrapDegrees(Math.toDegrees(radians)));
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

    private double computeEffectiveThrust(final double engineLevel, final boolean boost) {
        double thrust = ENGINE_IDLE_THRUST + engineLevel * (MAX_THRUST_FORCE - ENGINE_IDLE_THRUST);
        if (boost) {
            thrust *= BOOST_MULTIPLIER;
        }
        if (bodyPitch < -4.0D) {
            final double climbRatio = Mth.clamp((-bodyPitch - 4.0D) / 22.0D, 0.0D, 1.0D);
            thrust *= Mth.lerp(1.0D, 0.55D, climbRatio);
        }
        return thrust;
    }

    private Vec3 forwardGroundVector() {
        final float yawRad = this.getYRot() * ((float) Math.PI / 180F);
        final double x = -Mth.sin(yawRad);
        final double z = Mth.cos(yawRad);
        return new Vec3(x, 0.0D, z).normalize();
    }

    private void integrateAttitude(final double aoaRad, final double slipRad, final double airspeed, final double dt) {
        final double controlFactor = Mth.clamp((airspeed - CONTROL_EAS_MIN) / (CONTROL_EAS_MAX - CONTROL_EAS_MIN), 0.1D, 1.0D);

        final double yawAuthority = Math.max(0.2D, controlFactor);
        final double manualInput = controlStrafe * 0.5D;
        final double manualComponent = Math.toRadians(manualInput * MAX_MANUAL_YAW_RATE * yawAuthority);
        final double desiredYawRate = manualComponent;
        final double yawRateStep = Math.toRadians(YAW_ACCEL_LIMIT) * dt;
        yawRate = approach(yawRate, desiredYawRate, yawRateStep);
        yawRate = Mth.clamp(yawRate, -Math.toRadians(MAX_MANUAL_YAW_RATE), Math.toRadians(MAX_MANUAL_YAW_RATE));
        bodyYaw = Mth.wrapDegrees(bodyYaw + Math.toDegrees(yawRate * dt));
        this.setYRot((float) bodyYaw);
        this.yRotO = this.getYRot();
        this.setYHeadRot(this.getYRot());

        final double pitchInput = Mth.clamp(-resolvedVerticalInput, -1.0D, 1.0D);
        final double commandedPitchRate = Math.toRadians(pitchInput * MAX_MANUAL_PITCH_RATE * controlFactor);
        final double desiredPitchRate = commandedPitchRate - pitchRate * PITCH_DAMPING;
        final double pitchRateStep = Math.toRadians(PITCH_ACCEL_LIMIT) * dt;
        pitchRate = approach(pitchRate, desiredPitchRate, pitchRateStep);
        pitchRate = Mth.clamp(pitchRate, -Math.toRadians(MAX_MANUAL_PITCH_RATE), Math.toRadians(MAX_MANUAL_PITCH_RATE));
        bodyPitch = Mth.clamp(bodyPitch + Math.toDegrees(pitchRate * dt), -85.0D, 85.0D);
        this.setXRot((float) bodyPitch);
        this.xRotO = this.getXRot();
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
        final double desiredY = terrainY + 100.0D;
        if (this.getY() >= desiredY) {
            return;
        }
        this.setPos(getX(), desiredY, getZ());
        this.setDeltaMovement(Vec3.ZERO);
        this.linearVelocity = Vec3.ZERO;
        this.bodyPitch = DEFAULT_TRIM_PITCH;
        this.setXRot((float) bodyPitch);
        this.launchBaselineY = desiredY;
        this.visualPitch = bodyPitch;
        this.prevVisualPitch = bodyPitch;
        resetStrafeHistory();
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
        this.visualPitch = bodyPitch;
        this.prevVisualPitch = bodyPitch;
        this.remoteInitialized = tag.getBoolean(TAG_REMOTE_INIT);
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
        tag.putBoolean(TAG_REMOTE_INIT, remoteInitialized);
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
            final ItemStack droneStack = new ItemStack(FullfudRegistries.SHAHED_ITEM.get());
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
        this.controlForward = Mth.clamp(packet.forward(), -1.0F, 1.0F);
        this.controlStrafe = Mth.clamp(packet.strafe(), -1.0F, 1.0F);
        this.controlVertical = Mth.clamp(packet.vertical(), -1.0F, 1.0F);
        this.boostActive = packet.boost();
        this.controlTimeout = CONTROL_TIMEOUT_TICKS;

        final float newThrust = Mth.clamp(getThrust() + packet.thrustDelta(), 0.0F, 1.0F);
        setThrust(newThrust);
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
        final double distance = Math.sqrt(viewer.distanceToSqr(this));
        final float noise = computeNoise(distance);
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

    public float getVisualRoll(final float partialTick) {
        return (float) Mth.lerp(partialTick, prevVisualRoll, visualRoll);
    }

    private void updateVisualRoll(final double strafeBias, final double dt) {
        final double inputComponent = controlStrafe * 25.0D;
        final double biasComponent = strafeBias * MAX_VISUAL_ROLL;
        double targetRollDeg = Mth.clamp(biasComponent + inputComponent, -MAX_VISUAL_ROLL, MAX_VISUAL_ROLL);
        final double step = VISUAL_ROLL_ACCEL * dt;
        prevVisualRoll = visualRoll;
        visualRoll = approach(visualRoll, targetRollDeg, step);
    }

    public float getVisualPitch(final float partialTick) {
        return (float) Mth.lerp(partialTick, prevVisualPitch, visualPitch);
    }

    private void updateVisualPitch(final double targetPitch, final double dt) {
        final double step = VISUAL_PITCH_ACCEL * dt;
        prevVisualPitch = visualPitch;
        visualPitch = approach(visualPitch, targetPitch, step);
    }

    private double computeStrafeBias() {
        if (strafeHistorySize == 0) {
            return controlStrafe;
        }
        double sum = 0.0D;
        for (int i = 0; i < strafeHistorySize; i++) {
            sum += strafeHistory[i];
        }
        return Mth.clamp(sum / strafeHistorySize, -1.0D, 1.0D);
    }

    private void recordStrafe(final float strafe) {
        strafeHistory[strafeHistoryIndex] = strafe;
        strafeHistoryIndex = (strafeHistoryIndex + 1) % CONTROL_HISTORY_TICKS;
        if (strafeHistorySize < CONTROL_HISTORY_TICKS) {
            strafeHistorySize++;
        }
    }

    private void resetStrafeHistory() {
        Arrays.fill(strafeHistory, 0.0F);
        strafeHistoryIndex = 0;
        strafeHistorySize = 0;
    }

    @Override
    public void lerpTo(final double x, final double y, final double z, final float yaw, final float pitch, final int steps, final boolean teleport) {
        final int baseSteps = Math.max(steps, 1);
        final int smoothSteps = baseSteps * CLIENT_INTERPOLATION_FACTOR;
        super.lerpTo(x, y, z, yaw, pitch, smoothSteps, teleport);
    }

    private static float computeNoise(final double distance) {
        if (distance <= 1000.0D) {
            return 0.2F;
        }
        if (distance <= 2500.0D) {
            return 0.45F;
        }
        if (distance <= 5000.0D) {
            return 0.7F;
        }
        if (distance <= 10000.0D) {
            return 0.9F;
        }
        return 1.0F;
    }

    private void setThrust(final float thrust) {
        this.entityData.set(DATA_THRUST, thrust);
    }

    public float getThrust() {
        return this.entityData.get(DATA_THRUST);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void remove(final RemovalReason reason) {
        if (!level().isClientSide() && level() instanceof ServerLevel serverLevel) {
            ShahedLinkData.get(serverLevel).unlink(getUUID());
        }
        final ServerPlayer controller = getControllingPlayer();
        if (controller != null) {
            endRemoteControl(controller);
        }
        viewerDistances.clear();
        releaseChunkTicket();
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
            controllingPlayer = null;
            return;
        }
        bindPlayerToDrone(player);
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
            endRemoteControl(controller);
        }
        spawnSecondaryCharges();
        level().explode(this, getX(), getY(), getZ(), 10.0F, ExplosionInteraction.MOB);
        discard();
    }

    private void spawnSecondaryCharges() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final RandomSource random = serverLevel.getRandom();
        for (int i = 0; i < 8; i++) {
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

    public void initializePlacement(final double yPosition) {
        this.launchBaselineY = yPosition;
        this.armed = false;
        this.linearVelocity = Vec3.ZERO;
        this.setDeltaMovement(Vec3.ZERO);
        this.controlForward = 0.0F;
        this.controlStrafe = 0.0F;
        this.controlVertical = 0.0F;
        this.resolvedVerticalInput = 0.0F;
        this.yawRate = 0.0D;
        this.pitchRate = 0.0D;
        this.fuelMass = FUEL_CAPACITY_KG;
        this.telemetry = FlightTelemetry.ZERO;
        this.bodyYaw = this.getYRot();
        this.bodyPitch = DEFAULT_TRIM_PITCH;
        this.setXRot((float) bodyPitch);
        this.engineOutput = 0.0D;
        this.visualRoll = 0.0D;
        this.prevVisualRoll = 0.0D;
        this.visualPitch = bodyPitch;
        this.prevVisualPitch = bodyPitch;
        this.remoteInitialized = false;
    }

    public boolean beginRemoteControl(final ServerPlayer player) {
        if (controllingPlayer != null && !controllingPlayer.equals(player.getUUID())) {
            return false;
        }
        if (controlSession == null) {
            controlSession = new ControlSession(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer());
            spawnAvatar(player);
        }
        if (!remoteInitialized) {
            ensureFlightAltitude();
            this.linearVelocity = forwardGroundVector().scale(24.0D);
            this.setDeltaMovement(this.linearVelocity.scale(TICK_SECONDS));
            setThrust(1.0F);
            this.engineOutput = 1.0D;
            this.remoteInitialized = true;
        }
        controllingPlayer = player.getUUID();
        bindPlayerToDrone(player);
        return true;
    }

    public void endRemoteControl(final ServerPlayer player) {
        if (controlSession == null || (controllingPlayer != null && !controllingPlayer.equals(player.getUUID()))) {
            return;
        }
        restorePlayer(player);
        controllingPlayer = null;
        controlSession = null;
        removeAvatar();
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
        } else {
            player.connection.teleport(getX(), getY() + 1.0D, getZ(), getYRot(), getXRot());
        }
        player.onUpdateAbilities();
    }

    private void restorePlayer(final ServerPlayer player) {
        if (controlSession == null) {
            return;
        }
        final ServerLevel origin = player.getServer().getLevel(controlSession.originDimension);
        if (origin != null) {
            player.teleportTo(origin, controlSession.originPos.x, controlSession.originPos.y, controlSession.originPos.z, controlSession.originYaw, controlSession.originPitch);
        }
        player.setInvisible(false);
        player.setSilent(false);
        player.setNoGravity(false);
        player.noPhysics = false;
        player.setDeltaMovement(Vec3.ZERO);
        if (controlSession.originalGameType != null && player.gameMode.getGameModeForPlayer() != controlSession.originalGameType) {
            player.setGameMode(controlSession.originalGameType);
        }
        player.onUpdateAbilities();
        removeAvatar();
    }

    private void spawnAvatar(final ServerPlayer player) {
        if (!(level() instanceof ServerLevel serverLevel) || controlSession == null) {
            return;
        }
        removeAvatar();
        final GameProfile profile = new GameProfile(UUID.randomUUID(), player.getGameProfile().getName() + "[UAV]");
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

    private record OrientationBasis(Vec3 forward, Vec3 up, Vec3 right) {
    }

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
