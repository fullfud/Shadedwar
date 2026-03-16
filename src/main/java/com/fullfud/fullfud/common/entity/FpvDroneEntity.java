package com.fullfud.fullfud.common.entity;

import com.fullfud.fullfud.common.entity.drone.DronePhysics;
import com.fullfud.fullfud.common.entity.drone.DronePreset;
import com.fullfud.fullfud.common.item.FpvConfiguratorItem;
import com.fullfud.fullfud.common.item.FpvControllerItem;
import com.fullfud.fullfud.common.item.FpvGogglesItem;
import com.mojang.datafixers.util.Pair;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.DroneExplosionEffects;
import com.fullfud.fullfud.core.DroneExplosionLimiter;
import com.fullfud.fullfud.core.PlayerDecoyManager;
import com.fullfud.fullfud.core.RemotePlayerProtection;
import com.fullfud.fullfud.core.network.FullfudNetwork;
import com.fullfud.fullfud.core.network.packet.DroneAudioLoopPacket;
import com.fullfud.fullfud.core.network.packet.DroneAudioOneShotPacket;
import com.fullfud.fullfud.core.network.packet.FpvControlPacket;
import com.fullfud.fullfud.core.network.packet.OpenFpvConfiguratorPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import com.fullfud.fullfud.core.ChunkLoadManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

public class FpvDroneEntity extends Entity implements GeoEntity {
    public static final String PLAYER_REMOTE_TAG = "fullfud_fpv_remote";

    private static final String PLAYER_TAG_DRONE = "Drone";
    private static final String PLAYER_TAG_ORIGIN_DIM = "OriginDim";
    private static final String PLAYER_TAG_ORIGIN_X = "OriginX";
    private static final String PLAYER_TAG_ORIGIN_Y = "OriginY";
    private static final String PLAYER_TAG_ORIGIN_Z = "OriginZ";
    private static final String PLAYER_TAG_ORIGIN_YAW = "OriginYaw";
    private static final String PLAYER_TAG_ORIGIN_PITCH = "OriginPitch";
    private static final String PLAYER_TAG_ORIGIN_GM = "OriginGM";
    private static final EntityDataAccessor<Boolean> DATA_ARMED = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_THRUST = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_CONTROLLER = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_BATTERY = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SIGNAL_QUALITY = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SIGNAL_RANGE_SCALE = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SIGNAL_PENETRATION_SCALE = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QX = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QY = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QZ = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QW = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    
    private static final EntityDimensions DRONE_SIZE = EntityDimensions.scalable(0.7F, 0.25F);
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUNNING_ANIM = RawAnimation.begin().thenLoop("running");
    
    private static final float YAW_SINGULARITY_PITCH_DEG = 89.0F;
    private static final double YAW_SINGULARITY_HORIZ_EPS = 1.0E-3D;
    private static final int CLIENT_MIN_LERP_STEPS = 6;
    private static final float CLIENT_ROTATION_SMOOTH_ALPHA = 0.35F;

    private static final double TICK_SECONDS = 1.0D / 20.0D;
    
    private static final int MAX_BATTERY_TICKS = 12000;
    
    private static final int FPV_CHUNK_RADIUS = 3;
    private static final int CONTROL_TIMEOUT_TICKS = 40;
    private static final int SIGNAL_CALC_INTERVAL_TICKS = 2;
    private static final int MAX_OCCLUSION_STEPS = 256;
    private static final double REMOTE_PROTECTION_RADIUS = 16.0D;
    
    private static final String TAG_ARMED = "Armed";
    private static final String TAG_THRUST = "Thrust";
    private static final String TAG_ROLL = "Roll";
    private static final String TAG_VELOCITY = "Velocity";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_CONTROLLER = "Controller";
    private static final String TAG_BATTERY = "Battery";
    
    private static final String TAG_SESSION_DIM = "SessDim";
    private static final String TAG_SESSION_X = "SessX";
    private static final String TAG_SESSION_Y = "SessY";
    private static final String TAG_SESSION_Z = "SessZ";
    private static final String TAG_SESSION_YAW = "SessYaw";
    private static final String TAG_SESSION_PITCH = "SessPitch";
    private static final String TAG_SESSION_GAMEMODE = "SessGM";
    private static final String TAG_KEEP_CHUNKS = "KeepChunks";
    private static final String TAG_SIGNAL_RANGE_SCALE = "SignalRangeScale";
    private static final String TAG_SIGNAL_PENETRATION_SCALE = "SignalPenetrationScale";
    private static final String TAG_PRESET = "Preset";

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final Quaternionf qRotation = new Quaternionf();
    private final Quaternionf qRotationO = new Quaternionf();
    private final Quaternionf syncedQuaternionScratch = new Quaternionf();
    private final Quaternionf zeroRollQuaternionScratch = new Quaternionf();
    private final Vector3f cameraForwardScratch = new Vector3f();
    private final Vector3f cameraUpScratch = new Vector3f();
    private final Vector3f zeroRollUpScratch = new Vector3f();
    private final Vector3f rollCrossScratch = new Vector3f();

    private boolean physicsInitialized = false;

    private DronePreset dronePreset = DronePreset.STANDARD_STRIKE;
    private final DronePhysics dronePhysics = new DronePhysics();

    private float targetThrottle;
    private float throttleOutput;
    
    private float inputPitch;
    private float inputRoll;
    private float inputYaw;
    private float inputMousePitchDelta;
    private float inputMouseRollDelta;

    private double droneRoll;
    private double droneRollO;
    private float headingYaw;

    private Vec3 linearVelocity = Vec3.ZERO; // blocks/sec
    private float pitchRateRadPerSec;
    private float rollRateRadPerSec;
    private float yawRateRadPerSec;

    private UUID owner;
    private ControlSession session;

    private FpvControlPacket queuedControl;
    private UUID queuedControllerId;
    private int controlTimeoutTicks;
    // Optional mode: keep drone chunks loaded even without a controlling player.
    private boolean keepChunksLoadedWithoutPlayer;

    private double signalRangeScale = 1.0D;
    private double signalPenetrationScale = 1.0D;

    private double lastSignalDistance;
    private float lastSignalQuality;
    private double lastSignalOcclusion;
    private int lastSignalSteps;
    private int lastSignalLoadedSteps;
    private int lastSignalSolidSteps;
    private long lastSignalCalcNanos;
    
    private ChunkPos lastSentViewCenter;
    
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    private boolean wasArmedClient = false;
    private Object clientSoundInstance = null;
    private boolean lastArmedAudio;
    private boolean lastLoopAudio;
    private boolean detonating;

    private static final byte AUDIO_TYPE_FPV = 0;
    private static final byte AUDIO_KIND_START = 1;
    private static final byte AUDIO_KIND_STOP = 2;
    private static final float FPV_AUDIO_VOLUME_MULT = 0.2F;
    private static final double FPV_AUDIO_RANGE_BLOCKS = 96.0D;
    private static final float FPV_FIREBALL_POWER = 3.0F;

    public FpvDroneEntity(final EntityType<? extends FpvDroneEntity> type, final Level level) {
        super(type, level);
        this.noPhysics = false;
        this.setNoGravity(true);
        this.refreshDimensions();
        this.dronePhysics.applyPreset(this.dronePreset);
    }

    public void setDronePreset(DronePreset preset) {
        this.dronePreset = preset;
        this.dronePhysics.applyPreset(preset);
    }

    public DronePreset getDronePreset() {
        return this.dronePreset;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_ARMED, false);
        this.entityData.define(DATA_THRUST, 0.0F);
        this.entityData.define(DATA_ROLL, 0.0F);
        this.entityData.define(DATA_CONTROLLER, Optional.empty());
        this.entityData.define(DATA_BATTERY, MAX_BATTERY_TICKS);
        this.entityData.define(DATA_SIGNAL_QUALITY, 1.0F);
        this.entityData.define(DATA_SIGNAL_RANGE_SCALE, 1.0F);
        this.entityData.define(DATA_SIGNAL_PENETRATION_SCALE, 1.0F);
        this.entityData.define(DATA_QX, 0.0F);
        this.entityData.define(DATA_QY, 0.0F);
        this.entityData.define(DATA_QZ, 0.0F);
        this.entityData.define(DATA_QW, 1.0F);
    }

    public void setSignalScales(final double rangeScale, final double penetrationScale) {
        this.signalRangeScale = sanitizeScale(rangeScale);
        this.signalPenetrationScale = sanitizeScale(penetrationScale);
        this.entityData.set(DATA_SIGNAL_RANGE_SCALE, (float) this.signalRangeScale);
        this.entityData.set(DATA_SIGNAL_PENETRATION_SCALE, (float) this.signalPenetrationScale);
    }

    public double getSignalRangeScale() {
        return this.entityData.get(DATA_SIGNAL_RANGE_SCALE);
    }

    public double getSignalPenetrationScale() {
        return this.entityData.get(DATA_SIGNAL_PENETRATION_SCALE);
    }

    public int getDistance() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return 0;
        }
        return Math.max(2, serverLevel.getServer().getPlayerList().getViewDistance());
    }

    private static double sanitizeScale(final double scale) {
        if (!Double.isFinite(scale)) {
            return 1.0D;
        }
        return Math.max(0.1D, scale);
    }

    @Override
    protected void readAdditionalSaveData(final CompoundTag tag) {
        if (tag.contains(TAG_PRESET)) {
            setDronePreset(DronePreset.fromOrdinal(tag.getInt(TAG_PRESET)));
        }
        setArmed(tag.getBoolean(TAG_ARMED));
        targetThrottle = tag.getFloat(TAG_THRUST);
        throttleOutput = targetThrottle;
        droneRoll = tag.getDouble(TAG_ROLL);
        droneRollO = droneRoll;
        
        if (tag.contains(TAG_BATTERY)) {
            entityData.set(DATA_BATTERY, tag.getInt(TAG_BATTERY));
        }
        
        updateQuaternionFromEuler();
        syncQuaternionToData();
        physicsInitialized = true;
        
        if (tag.contains(TAG_VELOCITY, Tag.TAG_LIST)) {
            final ListTag list = tag.getList(TAG_VELOCITY, Tag.TAG_DOUBLE);
            if (list.size() == 3) {
                setDeltaMovement(list.getDouble(0), list.getDouble(1), list.getDouble(2));
            }
        }
        linearVelocity = getDeltaMovement().scale(20.0D);
        if (tag.hasUUID(TAG_OWNER)) {
            owner = tag.getUUID(TAG_OWNER);
        }
        if (tag.hasUUID(TAG_CONTROLLER)) {
            entityData.set(DATA_CONTROLLER, Optional.of(tag.getUUID(TAG_CONTROLLER)));
            controlTimeoutTicks = CONTROL_TIMEOUT_TICKS;
        }
        keepChunksLoadedWithoutPlayer = tag.getBoolean(TAG_KEEP_CHUNKS);
        if (tag.contains(TAG_SIGNAL_RANGE_SCALE, Tag.TAG_DOUBLE) || tag.contains(TAG_SIGNAL_PENETRATION_SCALE, Tag.TAG_DOUBLE)) {
            final double rangeScale = tag.contains(TAG_SIGNAL_RANGE_SCALE, Tag.TAG_DOUBLE)
                ? tag.getDouble(TAG_SIGNAL_RANGE_SCALE)
                : signalRangeScale;
            final double penetrationScale = tag.contains(TAG_SIGNAL_PENETRATION_SCALE, Tag.TAG_DOUBLE)
                ? tag.getDouble(TAG_SIGNAL_PENETRATION_SCALE)
                : signalPenetrationScale;
            setSignalScales(rangeScale, penetrationScale);
        }
        
        if (tag.contains(TAG_SESSION_DIM)) {
            final var dimId = net.minecraft.resources.ResourceLocation.tryParse(tag.getString(TAG_SESSION_DIM));
            if (dimId != null) {
                session = new ControlSession(
                    ResourceKey.create(Registries.DIMENSION, dimId),
                    new Vec3(tag.getDouble(TAG_SESSION_X), tag.getDouble(TAG_SESSION_Y), tag.getDouble(TAG_SESSION_Z)),
                    tag.getFloat(TAG_SESSION_YAW),
                    tag.getFloat(TAG_SESSION_PITCH),
                    GameType.byId(tag.getInt(TAG_SESSION_GAMEMODE))
                );
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(final CompoundTag tag) {
        tag.putInt(TAG_PRESET, dronePreset.ordinal());
        tag.putBoolean(TAG_ARMED, isArmed());
        tag.putFloat(TAG_THRUST, targetThrottle);
        tag.putDouble(TAG_ROLL, droneRoll);
        tag.putInt(TAG_BATTERY, getBatteryTicks());
        
        Vec3 vel = getDeltaMovement();
        final ListTag velocityList = new ListTag();
        velocityList.add(DoubleTag.valueOf(vel.x));
        velocityList.add(DoubleTag.valueOf(vel.y));
        velocityList.add(DoubleTag.valueOf(vel.z));
        tag.put(TAG_VELOCITY, velocityList);
        
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
        }
        entityData.get(DATA_CONTROLLER).ifPresent(id -> tag.putUUID(TAG_CONTROLLER, id));
        
        if (session != null) {
            tag.putString(TAG_SESSION_DIM, session.dimension.location().toString());
            tag.putDouble(TAG_SESSION_X, session.origin.x);
            tag.putDouble(TAG_SESSION_Y, session.origin.y);
            tag.putDouble(TAG_SESSION_Z, session.origin.z);
            tag.putFloat(TAG_SESSION_YAW, session.yaw);
            tag.putFloat(TAG_SESSION_PITCH, session.pitch);
            tag.putInt(TAG_SESSION_GAMEMODE, session.gameType.getId());
        }
        tag.putBoolean(TAG_KEEP_CHUNKS, keepChunksLoadedWithoutPlayer);
        tag.putDouble(TAG_SIGNAL_RANGE_SCALE, getSignalRangeScale());
        tag.putDouble(TAG_SIGNAL_PENETRATION_SCALE, getSignalPenetrationScale());
    }

    @Override
    public void tick() {
        super.tick();
        droneRollO = droneRoll;
        qRotationO.set(qRotation);
        
        if (!level().isClientSide()) {
            tickServer();
        } else {
            ClientSide.tick(this);
        }
    }

    private void tickServer() {
        if (!physicsInitialized) {
            updateQuaternionFromEuler();
            syncQuaternionToData();
            physicsInitialized = true;
            linearVelocity = getDeltaMovement().scale(20.0D);
        }

        if (isArmed()) {
            int currentBat = getBatteryTicks();
            if (currentBat > 0) {
                int drain = 1 + (int)(throttleOutput * 3.0F);
                entityData.set(DATA_BATTERY, Math.max(0, currentBat - drain));
            }
        } else {
            targetThrottle = 0.0F;
            inputPitch = 0.0F;
            inputRoll = 0.0F;
            inputYaw = 0.0F;
            inputMousePitchDelta = 0.0F;
            inputMouseRollDelta = 0.0F;
        }

        if (entityData.get(DATA_CONTROLLER).isPresent()) {
            ServerPlayer controller = getController();
            if (controller == null) {
                endRemoteControl(null);
            } else {
                if (controlTimeoutTicks > 0) {
                    controlTimeoutTicks--;
                } else {
                    endRemoteControl(controller);
                    controller = null;
                }

                if (controller != null && queuedControl != null && controller.getUUID().equals(queuedControllerId)) {
                    applyControl(queuedControl, controller);
                    queuedControl = null;
                    queuedControllerId = null;
                }
                if (controller != null && ((tickCount % SIGNAL_CALC_INTERVAL_TICKS) == 0 || tickCount <= 5)) {
                    calculateSignal(controller);
                }
            }
        }
        
        final boolean shouldForceChunks = keepChunksLoadedWithoutPlayer || owner != null || entityData.get(DATA_CONTROLLER).isPresent();
        if (shouldForceChunks) {
            ensureChunkTicket();
        } else {
            releaseChunkTicket();
        }
        updateSimplePhysics();
        updateControllerBinding();
        final ServerPlayer controller = getController();
        if (controller != null && entityData.get(DATA_CONTROLLER).isPresent()) {
            syncRemoteController(controller);
            syncViewCenter(controller);
        }
        broadcastEngineAudio();
        
        Vec3 preMoveVelocity = getDeltaMovement();
        Vec3 moveStart = position();
        move(MoverType.SELF, preMoveVelocity);
        Vec3 moveEnd = position();

        if (isArmed()) {
            if (horizontalCollision || verticalCollision) {
                double speed = linearVelocity.length();
                double crashThreshold = 10.0D; // blocks/sec — above this, drone explodes

                if (speed > crashThreshold) {
                    destroyOnImpact(resolveBlockImpactOrigin(moveStart, moveEnd));
                    return;
                }

                // Bounce via physics engine
                Vec3 actualMovement = getDeltaMovement();
                Vec3 newVel = dronePhysics.handleCollision(preMoveVelocity, actualMovement);
                linearVelocity = newVel;
                dronePhysics.setVelocity(newVel);
            }
            List<Entity> collisions = level().getEntities(this, getBoundingBox().inflate(0.2D), e -> !e.isSpectator() && e.isPickable());
            for (Entity entity : collisions) {
                final PlayerDecoyEntity decoy = PlayerDecoyManager.getDecoyByDrone(this.getUUID());
                if (decoy != null && entity == decoy) continue;
                if (entity instanceof ServerPlayer sp && entityData.get(DATA_CONTROLLER).map(sp.getUUID()::equals).orElse(false)) continue;
                if (isExplosivePreset() && level() instanceof ServerLevel serverLevel) {
                    DroneExplosionEffects.applyDirectImpactVehicleDamage(serverLevel, this, controller, entity);
                }
                destroyOnImpact(resolveEntityImpactOrigin(moveStart, moveEnd, entity));
                return;
            }
        }

        if (onGround()) {
            linearVelocity = linearVelocity.multiply(0.5D, 0.5D, 0.5D);
            dronePhysics.setVelocity(linearVelocity);
        }
        
        throttleOutput = targetThrottle;
        entityData.set(DATA_THRUST, throttleOutput);
        entityData.set(DATA_ROLL, (float) droneRoll);
        syncQuaternionToData();
    }

    private void broadcastEngineAudio() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (tickCount % 4 != 0) {
            return;
        }

        final boolean armed = isArmed();
        final float thrust = Mth.clamp(getThrust(), 0.0F, 1.0F);
        final boolean loopActive = armed;

        final double range = FPV_AUDIO_RANGE_BLOCKS;
        final double rangeSqr = range * range;

        if (armed != lastArmedAudio) {
            final byte kind = armed ? AUDIO_KIND_START : AUDIO_KIND_STOP;
            for (final ServerPlayer player : serverLevel.players()) {
                final boolean controlling = entityData.get(DATA_CONTROLLER).map(player.getUUID()::equals).orElse(false);
                final double distSqr = controlling ? 0.0D : player.distanceToSqr(this);
                if (!controlling && distSqr > rangeSqr) {
                    continue;
                }
                final float distanceFactor = distanceFactor(distSqr, range, 1.6D);
                float volume = FPV_AUDIO_VOLUME_MULT * distanceFactor;
                if (!controlling && isOccluded(serverLevel, player)) {
                    volume *= 0.35F;
                }
                if (volume <= 0.001F) {
                    continue;
                }
                FullfudNetwork.getChannel().send(PacketDistributor.PLAYER.with(() -> player),
                    new DroneAudioOneShotPacket(AUDIO_TYPE_FPV, kind, getUUID(), getX(), getY(), getZ(), volume, 1.0F));
            }
        }

        if (loopActive) {
            final float pitch = 0.8F + thrust * 0.7F;
            final float engine = 0.10F + 0.90F * thrust;
            for (final ServerPlayer player : serverLevel.players()) {
                final boolean controlling = entityData.get(DATA_CONTROLLER).map(player.getUUID()::equals).orElse(false);
                final double distSqr = controlling ? 0.0D : player.distanceToSqr(this);
                if (!controlling && distSqr > rangeSqr) {
                    continue;
                }
                final float distanceFactor = distanceFactor(distSqr, range, 1.6D);
                float volume = FPV_AUDIO_VOLUME_MULT * engine * distanceFactor;
                if (!controlling && isOccluded(serverLevel, player)) {
                    volume *= 0.35F;
                }
                FullfudNetwork.getChannel().send(PacketDistributor.PLAYER.with(() -> player),
                    new DroneAudioLoopPacket(AUDIO_TYPE_FPV, getUUID(), getX(), getY(), getZ(), volume, pitch, true));
            }
        } else if (lastLoopAudio) {
            for (final ServerPlayer player : serverLevel.players()) {
                final boolean controlling = entityData.get(DATA_CONTROLLER).map(player.getUUID()::equals).orElse(false);
                final double distSqr = controlling ? 0.0D : player.distanceToSqr(this);
                if (!controlling && distSqr > rangeSqr) {
                    continue;
                }
                FullfudNetwork.getChannel().send(PacketDistributor.PLAYER.with(() -> player),
                    new DroneAudioLoopPacket(AUDIO_TYPE_FPV, getUUID(), getX(), getY(), getZ(), 0.0F, 1.0F, false));
            }
        }

        lastArmedAudio = armed;
        lastLoopAudio = loopActive;
    }

    private static float distanceFactor(final double distSqr, final double range, final double exponent) {
        final double dist = Math.sqrt(Math.max(0.0D, distSqr));
        final double norm = Mth.clamp(dist / range, 0.0D, 1.0D);
        return (float) Math.pow(1.0D - norm, exponent);
    }

    private boolean isOccluded(final ServerLevel level, final ServerPlayer player) {
        if (level == null || player == null) {
            return false;
        }
        final Vec3 from = position().add(0.0D, 0.5D, 0.0D);
        final Vec3 to = player.position().add(0.0D, player.getEyeHeight(), 0.0D);
        final BlockHitResult result = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return result.getType() != HitResult.Type.MISS;
    }
    
    private void calculateSignal(ServerPlayer controller) {
        Vec3 start = (session != null) ? session.origin.add(0, controller.getEyeHeight(), 0) : controller.getEyePosition();
        Vec3 end = this.position().add(0, 0.25D, 0);
        
        final long calcStart = System.nanoTime();
        double dist = Math.sqrt(this.position().distanceToSqr(start));
        float currentSignal = 1.0F;

        final double rangeScale = sanitizeScale(getSignalRangeScale());
        final double penetrationScale = sanitizeScale(getSignalPenetrationScale());
        final double maxRange = 600.0D * rangeScale;
        final double fadeStart = 500.0D * rangeScale;
        
        if (dist > maxRange) {
            currentSignal = 0.0F;
        } else if (dist > fadeStart) {
            currentSignal = 0.5F * (1.0F - (float)((dist - fadeStart) / (maxRange - fadeStart)));
        } else {
            currentSignal = 1.0F - ((float)dist / (float)fadeStart) * 0.5F;
        }
        
        if (currentSignal > 0.0F) {
            final double occlusion = getOcclusionRatio(start, end);
            if (occlusion > 0.0D) {
                final double penalty = occlusion / Math.max(0.1D, penetrationScale);
                final double clamped = Mth.clamp(penalty, 0.0D, 1.0D);
                currentSignal *= (1.0F - (float) clamped);
            }
        }
        
        if (currentSignal > 0.0F) {
            List<RebEmitterEntity> rebs = level().getEntitiesOfClass(RebEmitterEntity.class, 
                this.getBoundingBox().inflate(300.0D), 
                e -> e.hasBattery() && e.hasFinishedStartup());
                
            float maxJamming = 0.0F;
            for (RebEmitterEntity reb : rebs) {
                double d = Math.sqrt(this.distanceToSqr(reb));
                if (d < 150.0D) {
                    maxJamming = 1.0F;
                    break;
                } else if (d < 300.0D) {
                    float jam = 1.0F - (float)((d - 150.0D) / 150.0D);
                    if (jam > maxJamming) maxJamming = jam;
                }
            }
            currentSignal *= (1.0F - maxJamming);
        }
        
        lastSignalCalcNanos = System.nanoTime() - calcStart;
        lastSignalDistance = dist;
        lastSignalQuality = Math.max(0.0F, currentSignal);
        entityData.set(DATA_SIGNAL_QUALITY, lastSignalQuality);
    }
    
    private double getOcclusionRatio(final Vec3 start, final Vec3 end) {
        final Vec3 vector = end.subtract(start);
        final double length = vector.length();
        if (length <= 1.0E-6D) {
            return 0.0D;
        }
        final Vec3 dir = vector.normalize();

        double stepSize = 2.0D;
        if (length > 1200.0D) {
            stepSize = 4.0D;
        } else if (length > 600.0D) {
            stepSize = 3.0D;
        }

        final int steps = Math.max(1, Math.min(MAX_OCCLUSION_STEPS, (int) (length / stepSize)));
        lastSignalSteps = steps;
        lastSignalLoadedSteps = 0;
        lastSignalSolidSteps = 0;
        int solidCount = 0;
        final BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < steps; i++) {
            final Vec3 point = start.add(dir.scale(i * stepSize));
            mPos.set(point.x, point.y, point.z);
            if (!level().hasChunkAt(mPos)) {
                continue;
            }
            lastSignalLoadedSteps++;
            if (level().getBlockState(mPos).canOcclude()) {
                solidCount++;
            }
        }

        lastSignalSolidSteps = solidCount;
        lastSignalOcclusion = solidCount / (double) steps;
        return lastSignalOcclusion;
    }
    
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yaw;
        this.lerpXRot = pitch;
        this.lerpSteps = Math.max(CLIENT_MIN_LERP_STEPS, posRotationIncrements);
    }

    private void updateQuaternionFromEuler() {
        float yRad = (float) Math.toRadians(-getYRot());
        float xRad = (float) Math.toRadians(getXRot()); 
        float zRad = (float) Math.toRadians(droneRoll);
        qRotation.identity().rotateYXZ(yRad, xRad, zRad);
        headingYaw = getYRot();
    }

    private void syncQuaternionToData() {
        this.entityData.set(DATA_QX, this.qRotation.x);
        this.entityData.set(DATA_QY, this.qRotation.y);
        this.entityData.set(DATA_QZ, this.qRotation.z);
        this.entityData.set(DATA_QW, this.qRotation.w);
    }

    private Quaternionf readQuaternionFromData() {
        return new Quaternionf(
            this.entityData.get(DATA_QX),
            this.entityData.get(DATA_QY),
            this.entityData.get(DATA_QZ),
            this.entityData.get(DATA_QW)
        ).normalize();
    }

    private Quaternionf readQuaternionFromData(final Quaternionf destination) {
        return destination.set(
            this.entityData.get(DATA_QX),
            this.entityData.get(DATA_QY),
            this.entityData.get(DATA_QZ),
            this.entityData.get(DATA_QW)
        ).normalize();
    }

    private void applyVisualOrientationFromQuaternion(final Quaternionf orientationQuaternion) {
        cameraForwardScratch.set(0.0F, 0.0F, 1.0F);
        cameraUpScratch.set(0.0F, 1.0F, 0.0F);
        orientationQuaternion.transform(cameraForwardScratch);
        orientationQuaternion.transform(cameraUpScratch);

        final double horiz = Math.sqrt(
            cameraForwardScratch.x * cameraForwardScratch.x + cameraForwardScratch.z * cameraForwardScratch.z
        );

        float newPitch = (float) Math.toDegrees(Math.atan2(-cameraForwardScratch.y, horiz));
        float newYaw = resolveStableYaw(cameraForwardScratch, newPitch);

        zeroRollQuaternionScratch.rotationYXZ(
            (float) Math.toRadians(-newYaw),
            (float) Math.toRadians(newPitch),
            0.0F
        );
        zeroRollUpScratch.set(0.0F, 1.0F, 0.0F);
        zeroRollQuaternionScratch.transform(zeroRollUpScratch);

        rollCrossScratch.set(zeroRollUpScratch).cross(cameraUpScratch);
        float newRoll = (float) Math.toDegrees(
            Math.atan2(rollCrossScratch.dot(cameraForwardScratch), zeroRollUpScratch.dot(cameraUpScratch))
        );

        if (Float.isNaN(newYaw)) {
            newYaw = this.getYRot();
        }
        if (Float.isNaN(newPitch)) {
            newPitch = this.getXRot();
        }
        if (Float.isNaN(newRoll)) {
            newRoll = (float) this.droneRoll;
        }

        this.setYRot(Mth.wrapDegrees(newYaw));
        this.setXRot(Mth.wrapDegrees(newPitch));
        this.droneRoll = Mth.wrapDegrees(newRoll);
    }

    private void updateSimplePhysics() {
        final float dt = (float) TICK_SECONDS;

        final boolean freeFall = getBatteryTicks() <= 0 || !isArmed();

        float throttle = freeFall ? 0.0F : throttleOutput;
        float pInput = freeFall ? 0.0F : inputPitch;
        float rInput = freeFall ? 0.0F : inputRoll;
        float yInput = freeFall ? 0.0F : inputYaw;
        float mousePitchDelta = freeFall ? 0.0F : inputMousePitchDelta;
        float mouseRollDelta = freeFall ? 0.0F : inputMouseRollDelta;

        // Sync current velocity into physics engine
        dronePhysics.setVelocity(linearVelocity);
        dronePhysics.setOrientation(qRotation);

        // Run RK4 physics simulation
        Vec3 displacement = dronePhysics.simulate(
            dt,
            throttle,
            rInput,
            pInput,
            yInput,
            mousePitchDelta,
            mouseRollDelta,
            getDronePreset().flightMode3d
        );
        inputMousePitchDelta = 0.0F;
        inputMouseRollDelta = 0.0F;

        // Read back state from physics engine
        Quaternionf physQ = dronePhysics.getOrientation();
        qRotation.set(physQ);
        qRotation.normalize();

        Vector3f physVel = dronePhysics.getVelocity();
        linearVelocity = new Vec3(physVel.x, physVel.y, physVel.z);

        final Vector3f forward = dronePhysics.getForward();
        final Vector3f up = dronePhysics.getUp();
        final double horiz = Math.sqrt(forward.x * forward.x + forward.z * forward.z);

        float newPitch = (float) Math.toDegrees(Math.atan2(-forward.y, horiz));
        float newYaw = resolveStableYaw(forward, newPitch);
        final Quaternionf zeroRoll = new Quaternionf().rotateYXZ(
            (float) Math.toRadians(-newYaw),
            (float) Math.toRadians(newPitch),
            0.0F
        );
        final Vector3f zeroRollUp = new Vector3f(0.0F, 1.0F, 0.0F);
        zeroRoll.transform(zeroRollUp);
        final Vector3f cross = new Vector3f(zeroRollUp).cross(up);
        float newRoll = (float) Math.toDegrees(Math.atan2(cross.dot(forward), zeroRollUp.dot(up)));

        if (Float.isNaN(newYaw)) newYaw = this.getYRot();
        if (Float.isNaN(newPitch)) newPitch = this.getXRot();
        if (Float.isNaN(newRoll)) newRoll = (float) this.droneRoll;

        this.setYRot(Mth.wrapDegrees(newYaw));
        this.setXRot(Mth.wrapDegrees(newPitch));
        this.droneRoll = Mth.wrapDegrees(newRoll);

        setDeltaMovement(displacement);
    }

    private float resolveStableYaw(final Vector3f forward, final float newPitch) {
        final double horiz = Math.sqrt(forward.x * forward.x + forward.z * forward.z);
        if (horiz > YAW_SINGULARITY_HORIZ_EPS && Math.abs(newPitch) < YAW_SINGULARITY_PITCH_DEG) {
            final float computed = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
            headingYaw = Mth.wrapDegrees(computed);
            return headingYaw;
        }
        return headingYaw;
    }

    private static float approach(final float current, final float target, final float maxDelta) {
        final float delta = target - current;
        if (delta > maxDelta) return current + maxDelta;
        if (delta < -maxDelta) return current - maxDelta;
        return target;
    }
    
    private void ensureChunkTicket() {
        if (level() instanceof ServerLevel serverLevel) {
            final int radius = Math.max(FPV_CHUNK_RADIUS, getDistance() + 1);
            ChunkLoadManager.ensureChunksLoaded(serverLevel, getId(), chunkPosition(), radius);
        }
    }
    
    private void releaseChunkTicket() {
        if (level() instanceof ServerLevel serverLevel) {
            ChunkLoadManager.releaseChunks(serverLevel, getId());
        }
    }

    private void updateControllerBinding() {
        final ServerPlayer player = getController();
        if (player == null) {
            return;
        }

        if (!hasLinkedGoggles(player) && !ensureLinkedGoggles(player)) {
            endRemoteControl(player);
        }
    }

    @Override
    protected void checkFallDamage(final double y, final boolean onGround, final net.minecraft.world.level.block.state.BlockState state, final net.minecraft.core.BlockPos pos) {
    }

    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand) {
        final ItemStack held = player.getItemInHand(hand);
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!canAccess(player)) {
            return InteractionResult.FAIL;
        }
        if (held.getItem() instanceof FpvConfiguratorItem) {
            if (player instanceof ServerPlayer serverPlayer) {
                FullfudNetwork.getChannel().send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new OpenFpvConfiguratorPacket(getUUID())
                );
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        if (held.getItem() instanceof FpvControllerItem controller) {
            controller.link(held, this, player);
            if (player instanceof ServerPlayer serverPlayer && !player.isShiftKeyDown()) {
                beginControl(serverPlayer);
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown() && held.isEmpty() && !isArmed()) {
            dropAsItem();
            discard();
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
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
    public boolean hurt(final DamageSource source, final float amount) {
        if (level().isClientSide() || isRemoved()) {
            return false;
        }
        if (detonating) {
            return false;
        }
        if (isArmed() || source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile) {
            final Entity directEntity = source.getDirectEntity();
            destroyOnImpact(directEntity != null ? directEntity.position() : position());
            return true;
        }
        dropAsItem();
        discard();
        return true;
    }

    @Override
    public void remove(final RemovalReason reason) {
        if (!level().isClientSide()) {
            final ServerPlayer controller = getController();
            if (controller != null) {
                forceReturnCamera(controller);
                endRemoteControl(controller);
            } else if (entityData.get(DATA_CONTROLLER).isPresent() || session != null) {
                endRemoteControl(null);
            } else {
                PlayerDecoyManager.removeDecoyByDrone(getUUID());
            }
            releaseChunkTicket();
        }
        super.remove(reason);
    }

    private void destroyOnImpact() {
        destroyOnImpact(position());
    }

    private void destroyOnImpact(final Vec3 impactOrigin) {
        setPos(impactOrigin.x, impactOrigin.y, impactOrigin.z);
        if (isExplosivePreset()) {
            explode();
        } else {
            crashAndDrop();
        }
    }

    private boolean isExplosivePreset() {
        return getDronePreset() == DronePreset.STRIKE_7INCH;
    }

    private void prepareForDestruction() {
        if (detonating || isRemoved()) {
            return;
        }
        detonating = true;
        final ServerPlayer controller = getController();
        if (controller != null) {
            endRemoteControl(controller);
        } else if (entityData.get(DATA_CONTROLLER).isPresent() || session != null) {
            endRemoteControl(null);
        } else {
            PlayerDecoyManager.removeDecoyByDrone(getUUID());
        }
    }

    private void crashAndDrop() {
        if (detonating || isRemoved()) {
            return;
        }
        prepareForDestruction();
        dropAsItem();
        discard();
    }

    private void explode() {
        if (detonating || isRemoved()) {
            return;
        }
        final ServerPlayer controller = getController();
        final DronePreset preset = getDronePreset();
        final Vec3 explosionDirection = resolveExplosionDirection();
        prepareForDestruction();
        spawnTntEffect(controller, preset, explosionDirection);
        discard();
    }

    private void spawnTntEffect(
        @javax.annotation.Nullable final ServerPlayer controller,
        final DronePreset preset,
        final Vec3 explosionDirection
    ) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final PrimedTnt tnt = new PrimedTnt(serverLevel, getX(), getY(), getZ(), controller);
        tnt.setFuse(0);
        RemotePlayerProtection.markHazard(tnt, this);
        DroneExplosionLimiter.markNoBlockDamage(tnt);
        DroneExplosionLimiter.markNoEntityDamage(tnt);
        serverLevel.addFreshEntity(tnt);
        serverLevel.explode(tnt, getX(), getY(), getZ(), FPV_FIREBALL_POWER, net.minecraft.world.level.Level.ExplosionInteraction.MOB);
        DroneExplosionEffects.afterFpvExplosion(serverLevel, tnt, controller, preset, explosionDirection);
        tnt.discard();
    }

    private Vec3 resolveBlockImpactOrigin(final Vec3 start, final Vec3 end) {
        if (start.distanceToSqr(end) < 1.0E-6D) {
            return end;
        }
        final HitResult hitResult = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            return hitResult.getLocation();
        }
        return end;
    }

    private Vec3 resolveEntityImpactOrigin(final Vec3 start, final Vec3 end, final Entity target) {
        if (target == null || start.distanceToSqr(end) < 1.0E-6D) {
            return end;
        }
        return target.getBoundingBox().inflate(0.05D).clip(start, end).orElse(end);
    }

    private Vec3 resolveExplosionDirection() {
        final Vector3f forward = dronePhysics.getForward();
        final Vec3 forwardVec = new Vec3(forward.x, forward.y, forward.z);
        if (forwardVec.lengthSqr() > 1.0E-6D) {
            return forwardVec.normalize();
        }
        if (linearVelocity.lengthSqr() > 1.0E-6D) {
            return linearVelocity.normalize();
        }
        final Vec3 motion = getDeltaMovement();
        if (motion.lengthSqr() > 1.0E-6D) {
            return motion.normalize();
        }
        return new Vec3(0.0D, 0.0D, 1.0D);
    }

    private void dropAsItem() {
        spawnAtLocation(new ItemStack(resolveDropItem()));
    }

    private int resolveSignalMultiplier() {
        final double rangeScale = getSignalRangeScale();
        if (rangeScale >= 3.0D) {
            return 4;
        }
        if (rangeScale >= 1.5D) {
            return 2;
        }
        return 1;
    }

    private Item resolveDropItem() {
        final int signalMultiplier = resolveSignalMultiplier();
        return switch (getDronePreset()) {
            case TINY_WHOOP -> switch (signalMultiplier) {
                case 4 -> FullfudRegistries.FPV_DRONE_WHOOP_ITEM_X4.get();
                case 2 -> FullfudRegistries.FPV_DRONE_WHOOP_ITEM_X2.get();
                default -> FullfudRegistries.FPV_DRONE_WHOOP_ITEM.get();
            };
            case STRIKE_7INCH -> switch (signalMultiplier) {
                case 4 -> FullfudRegistries.FPV_DRONE_STRIKE_ITEM_X4.get();
                case 2 -> FullfudRegistries.FPV_DRONE_STRIKE_ITEM_X2.get();
                default -> FullfudRegistries.FPV_DRONE_STRIKE_ITEM.get();
            };
            case STANDARD_STRIKE -> switch (signalMultiplier) {
                case 4 -> FullfudRegistries.FPV_DRONE_ITEM_X4.get();
                case 2 -> FullfudRegistries.FPV_DRONE_ITEM_X2.get();
                default -> FullfudRegistries.FPV_DRONE_ITEM.get();
            };
        };
    }

    public void applyControl(final FpvControlPacket packet, final ServerPlayer sender) {
        if (!isController(sender)) {
            return;
        }

        if (!isRemoteStateValidFor(sender)) {
            return;
        }

        controlTimeoutTicks = CONTROL_TIMEOUT_TICKS;
        
        if (getSignalQuality() < 0.05F) {
            inputPitch = 0.0F;
            inputRoll = 0.0F;
            inputYaw = 0.0F;
            inputMousePitchDelta = 0.0F;
            inputMouseRollDelta = 0.0F;
            targetThrottle = Math.max(0.0F, targetThrottle - 0.08F);
            return;
        }

        if (!hasLinkedGoggles(sender) && !ensureLinkedGoggles(sender)) {
            return;
        }

        if (!Float.isFinite(packet.pitchInput())
            || !Float.isFinite(packet.rollInput())
            || !Float.isFinite(packet.yawInput())
            || !Float.isFinite(packet.mousePitchDelta())
            || !Float.isFinite(packet.mouseRollDelta())
            || !Float.isFinite(packet.throttle())) {
            return;
        }

        inputPitch = Mth.clamp(packet.pitchInput(), -1.0F, 1.0F);
        inputRoll = Mth.clamp(packet.rollInput(), -1.0F, 1.0F);
        inputYaw = Mth.clamp(packet.yawInput(), -1.0F, 1.0F);
        inputMousePitchDelta = Mth.clamp(packet.mousePitchDelta(), -0.5F, 0.5F);
        inputMouseRollDelta = Mth.clamp(packet.mouseRollDelta(), -0.5F, 0.5F);
        targetThrottle = Mth.clamp(packet.throttle(), 0.0F, 1.0F);
        
        if (packet.armAction() == 1) {
            if (getBatteryTicks() > 0) setArmed(true);
        } else if (packet.armAction() == 2) {
            setArmed(false);
            inputMousePitchDelta = 0.0F;
            inputMouseRollDelta = 0.0F;
            targetThrottle = 0.0F;
        }
    }

    public void queueControl(final FpvControlPacket packet, final ServerPlayer sender) {
        if (packet == null || sender == null) {
            return;
        }
        if (!isController(sender)) {
            return;
        }
        queuedControl = packet;
        queuedControllerId = sender.getUUID();
    }

    private boolean isRemoteStateValidFor(final ServerPlayer sender) {
        if (sender == null) {
            return false;
        }
        final CompoundTag root = sender.getPersistentData();
        if (!root.contains(PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)) {
            return tryRestoreRemoteTag(sender);
        }
        final CompoundTag tag = root.getCompound(PLAYER_REMOTE_TAG);
        if (!tag.hasUUID(PLAYER_TAG_DRONE)) {
            return tryRestoreRemoteTag(sender);
        }
        if (!tag.getUUID(PLAYER_TAG_DRONE).equals(this.getUUID())) {
            return tryRestoreRemoteTag(sender);
        }
        return true;
    }

    public void requestRelease(final ServerPlayer sender) {
        if (!isController(sender)) {
            return;
        }
        if (sender.isShiftKeyDown()) {
            return;
        }
        endRemoteControl(sender);
    }

    private boolean isController(final ServerPlayer player) {
        return entityData.get(DATA_CONTROLLER).map(player.getUUID()::equals).orElse(false);
    }

    public boolean beginControl(final ServerPlayer player) {
        if (entityData.get(DATA_CONTROLLER).isPresent() && !isController(player)) {
            return false;
        }
        if (owner != null && !owner.equals(player.getUUID())) {
            return false;
        }
        if (owner == null) {
            owner = player.getUUID();
        }
        final CompoundTag root = player.getPersistentData();
        if (root.contains(PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)) {
            final CompoundTag tag = root.getCompound(PLAYER_REMOTE_TAG);
            if (!tag.hasUUID(PLAYER_TAG_DRONE) || !getUUID().equals(tag.getUUID(PLAYER_TAG_DRONE))) {
                forceReleaseFromPersistentData(player.getServer(), player.getUUID(), tag);
            }
        }
        if (!isWithinPlayerChunkRange(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.fullfud.fpv.out_of_range"), true);
            return false;
        }
        if (!hasLinkedGoggles(player) && !ensureLinkedGoggles(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.fullfud.fpv.need_goggles"), true);
            return false;
        }
        entityData.set(DATA_CONTROLLER, Optional.of(player.getUUID()));
        controlTimeoutTicks = CONTROL_TIMEOUT_TICKS;
        session = new ControlSession(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer());
        writeRemoteTag(player);
        RemotePlayerProtection.touch(player, this, REMOTE_PROTECTION_RADIUS);
        PlayerDecoyManager.createDecoy(player, this);
        syncRemoteController(player);
        lastSentViewCenter = null;
        syncViewCenter(player);
        return true;
    }

    private boolean isWithinPlayerChunkRange(final ServerPlayer player) {
        if (player == null || !(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (player.level() != level()) {
            return false;
        }
        final int viewDistance = Math.max(2, serverLevel.getServer().getPlayerList().getViewDistance());
        final ChunkPos playerChunk = player.chunkPosition();
        final ChunkPos droneChunk = this.chunkPosition();
        final int dx = Math.abs(playerChunk.x - droneChunk.x);
        final int dz = Math.abs(playerChunk.z - droneChunk.z);
        return Math.max(dx, dz) <= viewDistance;
    }

    public void endRemoteControl(final ServerPlayer player) {
        if (!entityData.get(DATA_CONTROLLER).isPresent() && session == null) {
            return;
        }
        final UUID controllerId = entityData.get(DATA_CONTROLLER).orElse(null);
        final ServerPlayer controlling = player != null ? player : resolvePlayer(controllerId);
        final ControlSession endedSession = session;
        if (controllerId != null) {
            PlayerDecoyManager.removeDecoy(controllerId);
        } else {
            PlayerDecoyManager.removeDecoyByDrone(getUUID());
        }
        if (controlling != null) {
            restoreRemoteController(controlling, endedSession);
            clearRemoteTag(controlling);
        }
        
        entityData.set(DATA_CONTROLLER, Optional.empty());
        
        inputPitch = 0;
        inputRoll = 0;
        inputYaw = 0;
        inputMousePitchDelta = 0;
        inputMouseRollDelta = 0;
        targetThrottle = 0;
        throttleOutput = 0;
        queuedControl = null;
        queuedControllerId = null;
        controlTimeoutTicks = 0;
        setArmed(false);
        
        session = null;
        releaseChunkTicket();
    }

    private ServerPlayer resolvePlayer(final UUID playerId) {
        if (playerId == null) {
            return null;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getServer().getPlayerList().getPlayer(playerId);
    }

    private void forceReturnCamera(final ServerPlayer player) {
        if (player == null) return;
        clearViewPoint(player);
    }

    private static void clearViewPoint(final ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.setCamera(player);
    }

    private void syncViewCenter(final ServerPlayer player) {
        if (player == null || !(level() instanceof ServerLevel)) {
            return;
        }
        final ChunkPos chunkPos = this.chunkPosition();
        final boolean centerChanged = lastSentViewCenter == null || !lastSentViewCenter.equals(chunkPos);
        if (centerChanged) {
            player.connection.send(new ClientboundSetChunkCacheCenterPacket(chunkPos.x, chunkPos.z));
            lastSentViewCenter = chunkPos;
        }
    }

    private void resetViewCenter(final ServerPlayer player) {
        if (player == null) {
            return;
        }
        final ChunkPos chunkPos = player.chunkPosition();
        player.connection.send(new ClientboundSetChunkCacheCenterPacket(chunkPos.x, chunkPos.z));
        lastSentViewCenter = null;
    }

    private void syncRemoteController(final ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        player.teleportTo(serverLevel, getX(), getY() + (double) getBbHeight() + 4.0D, getZ(), player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.hasImpulse = false;
        player.fallDistance = 0.0F;
        player.setNoGravity(true);
        player.setInvisible(true);
        player.setSilent(true);
        player.noPhysics = true;
        player.hurtMarked = true;
        RemotePlayerProtection.touch(player, this, REMOTE_PROTECTION_RADIUS);
        syncRemotePlayerVisibility(player, true);
        syncRemotePlayerEquipment(player, true);
        if (this.tickCount % 20 == 0) {
            PlayerDecoyManager.syncDecoyEquipment(player);
            PlayerDecoyManager.syncDecoyHealth(player);
        }
    }

    private void restoreRemoteController(final ServerPlayer player, final ControlSession controlSession) {
        if (player == null || controlSession == null) {
            return;
        }
        clearViewPoint(player);
        RemotePlayerProtection.clear(player);
        player.setInvisible(false);
        player.setSilent(false);
        player.setNoGravity(false);
        player.noPhysics = false;
        syncRemotePlayerVisibility(player, false);
        syncRemotePlayerEquipment(player, false);
        final MinecraftServer server = player.getServer();
        final ServerLevel targetLevel = server != null ? server.getLevel(controlSession.dimension()) : player.serverLevel();
        if (targetLevel != null) {
            final ChunkPos chunkPos = new ChunkPos(BlockPos.containing(controlSession.origin()));
            targetLevel.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.POST_TELEPORT, chunkPos, 1, player.getId());
            final Vec3 origin = controlSession.origin();
            player.teleportTo(targetLevel, origin.x, origin.y, origin.z, controlSession.yaw(), controlSession.pitch());
            player.fallDistance = 0.0F;
        }
        resetViewCenter(player);
    }

    private static void restorePlayerFromRemoteTag(final ServerPlayer player, final CompoundTag tag) {
        if (player == null || tag == null) {
            return;
        }

        clearViewPoint(player);
        RemotePlayerProtection.clear(player);
        player.setInvisible(false);
        player.setSilent(false);
        player.setNoGravity(false);
        player.noPhysics = false;
        syncRemotePlayerVisibility(player, false);
        syncRemotePlayerEquipment(player, false);
        if (player.getServer() != null && tag.contains(PLAYER_TAG_ORIGIN_DIM, Tag.TAG_STRING)) {
            final ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(PLAYER_TAG_ORIGIN_DIM));
            final ServerLevel targetLevel = dimensionId != null ? player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId)) : null;
            if (targetLevel != null) {
                final double x = tag.getDouble(PLAYER_TAG_ORIGIN_X);
                final double y = tag.getDouble(PLAYER_TAG_ORIGIN_Y);
                final double z = tag.getDouble(PLAYER_TAG_ORIGIN_Z);
                final float yaw = tag.getFloat(PLAYER_TAG_ORIGIN_YAW);
                final float pitch = tag.getFloat(PLAYER_TAG_ORIGIN_PITCH);
                final ChunkPos chunkPos = new ChunkPos(BlockPos.containing(x, y, z));
                targetLevel.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.POST_TELEPORT, chunkPos, 1, player.getId());
                player.teleportTo(targetLevel, x, y, z, yaw, pitch);
                player.fallDistance = 0.0F;
            }
        }
        final ChunkPos chunkPos = player.chunkPosition();
        player.connection.send(new ClientboundSetChunkCacheCenterPacket(chunkPos.x, chunkPos.z));
    }

    private static void syncRemotePlayerVisibility(final ServerPlayer player, final boolean hidden) {
        if (player == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (final ServerPlayer viewer : serverLevel.players()) {
            if (viewer == player) {
                continue;
            }
            if (hidden) {
                viewer.connection.send(new ClientboundRemoveEntitiesPacket(player.getId()));
            } else {
                viewer.connection.send(player.getAddEntityPacket());
            }
        }
    }

    private static void syncRemotePlayerEquipment(final ServerPlayer player, final boolean hidden) {
        if (player == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
        for (final EquipmentSlot slot : EquipmentSlot.values()) {
            final ItemStack stack = hidden ? ItemStack.EMPTY : player.getItemBySlot(slot).copy();
            equipment.add(Pair.of(slot, stack));
        }
        final ClientboundSetEquipmentPacket packet = new ClientboundSetEquipmentPacket(player.getId(), equipment);
        for (final ServerPlayer viewer : serverLevel.players()) {
            if (viewer == player) {
                continue;
            }
            viewer.connection.send(packet);
        }
    }
    
    private boolean isSignalLostFor(final ServerPlayer p) {
        return p == null || getSignalQuality() <= 0.0F;
    }

    private void writeRemoteTag(final ServerPlayer player) {
        if (player == null || session == null) {
            return;
        }
        final CompoundTag tag = new CompoundTag();
        tag.putUUID(PLAYER_TAG_DRONE, this.getUUID());
        tag.putString(PLAYER_TAG_ORIGIN_DIM, session.dimension.location().toString());
        tag.putDouble(PLAYER_TAG_ORIGIN_X, session.origin.x);
        tag.putDouble(PLAYER_TAG_ORIGIN_Y, session.origin.y);
        tag.putDouble(PLAYER_TAG_ORIGIN_Z, session.origin.z);
        tag.putFloat(PLAYER_TAG_ORIGIN_YAW, session.yaw);
        tag.putFloat(PLAYER_TAG_ORIGIN_PITCH, session.pitch);
        if (session.gameType != null) {
            tag.putInt(PLAYER_TAG_ORIGIN_GM, session.gameType.getId());
        }
        player.getPersistentData().put(PLAYER_REMOTE_TAG, tag);
    }

    private static void clearRemoteTag(final ServerPlayer player) {
        if (player == null) {
            return;
        }
        final CompoundTag root = player.getPersistentData();
        root.remove(PLAYER_REMOTE_TAG);
    }

    public void forceReleaseControlFor(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        if (!entityData.get(DATA_CONTROLLER).map(playerId::equals).orElse(false)) {
            return;
        }
        PlayerDecoyManager.removeDecoy(playerId);
        endRemoteControl(null);
    }

    public static void forceReleaseFromPersistentData(final MinecraftServer server, final UUID playerId, final CompoundTag tag) {
        if (server == null || playerId == null || tag == null || !tag.hasUUID(PLAYER_TAG_DRONE)) {
            return;
        }
        final UUID droneId = tag.getUUID(PLAYER_TAG_DRONE);
        for (final ServerLevel level : server.getAllLevels()) {
            final Entity entity = level.getEntity(droneId);
            if (entity instanceof FpvDroneEntity drone) {
                drone.forceReleaseControlFor(playerId);
                return;
            }
        }
    }

    public static void forceRestoreFromPersistentData(final ServerPlayer player, final CompoundTag tag) {
        if (player == null || player.getServer() == null || tag == null) {
            return;
        }

        forceReleaseFromPersistentData(player.getServer(), player.getUUID(), tag);

        restorePlayerFromRemoteTag(player, tag);
    }

    public static boolean isRemoteControlActive(final MinecraftServer server, final UUID playerId, final CompoundTag tag) {
        if (server == null || playerId == null || tag == null || !tag.hasUUID(PLAYER_TAG_DRONE)) {
            return false;
        }
        final UUID droneId = tag.getUUID(PLAYER_TAG_DRONE);
        for (final ServerLevel level : server.getAllLevels()) {
            final Entity entity = level.getEntity(droneId);
            if (entity instanceof FpvDroneEntity drone) {
                return playerId.equals(drone.getControllerId()) && drone.session != null;
            }
        }
        return false;
    }

    private ServerPlayer getController() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return entityData.get(DATA_CONTROLLER)
            .map(serverLevel.getServer().getPlayerList()::getPlayer)
            .orElse(null);
    }

    private boolean hasLinkedGoggles(final ServerPlayer player) {
        final ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof FpvGogglesItem)) {
            return false;
        }
        return FpvGogglesItem.getLinked(head).filter(id -> id.equals(getUUID())).isPresent();
    }

    private boolean ensureLinkedGoggles(final ServerPlayer player) {
        if (player == null) {
            return false;
        }
        final ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof FpvGogglesItem)) {
            return false;
        }
        final Optional<UUID> linked = FpvGogglesItem.getLinked(head);
        if (linked.isPresent() && linked.get().equals(getUUID())) {
            return true;
        }
        FpvGogglesItem.setLinked(head, getUUID());
        return true;
    }

    private boolean tryRestoreRemoteTag(final ServerPlayer sender) {
        if (sender == null || !isController(sender)) {
            return false;
        }
        if (session == null) {
            return false;
        }
        writeRemoteTag(sender);
        return true;
    }

    public boolean isArmed() {
        return entityData.get(DATA_ARMED);
    }

    public float getThrust() {
        return entityData.get(DATA_THRUST);
    }
    
    public int getBatteryTicks() {
        return entityData.get(DATA_BATTERY);
    }
    
    public int getBatteryPercent() {
        return (int) ((getBatteryTicks() / (float) MAX_BATTERY_TICKS) * 100);
    }
    
    public float getSignalQuality() {
        return entityData.get(DATA_SIGNAL_QUALITY);
    }

    public UUID getControllerId() {
        return entityData.get(DATA_CONTROLLER).orElse(null);
    }

    public void setOwner(final ServerPlayer player) {
        owner = player.getUUID();
    }

    public boolean isKeepChunksLoadedWithoutPlayer() {
        return keepChunksLoadedWithoutPlayer;
    }

    public void setKeepChunksLoadedWithoutPlayer(final boolean keep) {
        this.keepChunksLoadedWithoutPlayer = keep;
        if (!keep) {
            releaseChunkTicket();
        }
    }

    public boolean hasOwner(final UUID playerId) {
        return owner != null && owner.equals(playerId);
    }

    private boolean canAccess(final Player player) {
        return owner == null || owner.equals(player.getUUID());
    }

    private void setArmed(final boolean armed) {
        entityData.set(DATA_ARMED, armed);
    }
    
    public float getCameraRoll(float partialTick) {
        return (float) Mth.rotLerp(partialTick, (float)droneRollO, (float)droneRoll);
    }

    public float getCameraPitch(float partialTick) {
        return (float) Mth.rotLerp(partialTick, xRotO, getXRot());
    }

    public Quaternionf getCameraQuaternion(final float partialTick) {
        final float clampedPartial = Mth.clamp(partialTick, 0.0F, 1.0F);
        return new Quaternionf(this.qRotationO).slerp(this.qRotation, clampedPartial);
    }

    public CameraOrientation getCameraOrientation(final float partialTick) {
        final float clampedPartial = Mth.clamp(partialTick, 0.0F, 1.0F);
        final Quaternionf interpolated = getCameraQuaternion(clampedPartial);

        final Vector3f forwardVec = new Vector3f(0.0F, 0.0F, 1.0F);
        final Vector3f upVec = new Vector3f(0.0F, 1.0F, 0.0F);
        final Vector3f rightVec = new Vector3f(1.0F, 0.0F, 0.0F);
        interpolated.transform(forwardVec);
        interpolated.transform(upVec);
        interpolated.transform(rightVec);

        final double horiz = Math.sqrt(forwardVec.x * forwardVec.x + forwardVec.z * forwardVec.z);
        final float pitch = (float) Math.toDegrees(Math.atan2(-forwardVec.y, horiz));

        final float fallbackYaw = Mth.rotLerp(clampedPartial, this.yRotO, this.getYRot());
        final float yaw;
        if (horiz > YAW_SINGULARITY_HORIZ_EPS && Math.abs(pitch) < YAW_SINGULARITY_PITCH_DEG) {
            yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-forwardVec.x, forwardVec.z)));
        } else {
            yaw = fallbackYaw;
        }

        final Quaternionf zeroRoll = new Quaternionf().rotationYXZ(
            (float) Math.toRadians(-yaw),
            (float) Math.toRadians(pitch),
            0.0F
        );
        final Vector3f zeroRollUp = new Vector3f(0.0F, 1.0F, 0.0F);
        zeroRoll.transform(zeroRollUp);

        final Vector3f cross = new Vector3f(zeroRollUp).cross(upVec);
        float roll = (float) Math.toDegrees(Math.atan2(cross.dot(forwardVec), zeroRollUp.dot(upVec)));
        if (!Float.isFinite(roll)) {
            roll = (float) Mth.rotLerp(clampedPartial, (float) this.droneRollO, (float) this.droneRoll);
        }

        return new CameraOrientation(
            Mth.wrapDegrees(yaw),
            Mth.wrapDegrees(pitch),
            Mth.wrapDegrees(roll),
            new Vec3(forwardVec.x, forwardVec.y, forwardVec.z),
            new Vec3(upVec.x, upVec.y, upVec.z),
            new Vec3(rightVec.x, rightVec.y, rightVec.z)
        );
    }

    public float getVisualRoll(final float partialTick) {
        return (float) Mth.rotLerp(partialTick, (float)droneRollO, (float)droneRoll);
    }

    public float getVisualPitch(final float partialTick) {
        return (float) Mth.rotLerp(partialTick, this.xRotO, this.getXRot());
    }

    @Override
    public EntityDimensions getDimensions(final Pose pose) {
        return DRONE_SIZE;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void registerControllers(final AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<FpvDroneEntity> event) {
        if (!this.isArmed()) {
            return PlayState.STOP;
        }

        if (this.getThrust() > 0.1F) {
            return event.setAndContinue(RUNNING_ANIM);
        }

        return event.setAndContinue(IDLE_ANIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    public record CameraOrientation(float yaw, float pitch, float roll, Vec3 forward, Vec3 up, Vec3 right) { }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientSide {
        private static void tick(final FpvDroneEntity drone) {
            drone.throttleOutput = drone.entityData.get(DATA_THRUST);
            final Quaternionf syncedQ = drone.readQuaternionFromData(drone.syncedQuaternionScratch);

            if (drone.lerpSteps > 0) {
                final double d0 = drone.getX() + (drone.lerpX - drone.getX()) / (double) drone.lerpSteps;
                final double d1 = drone.getY() + (drone.lerpY - drone.getY()) / (double) drone.lerpSteps;
                final double d2 = drone.getZ() + (drone.lerpZ - drone.getZ()) / (double) drone.lerpSteps;
                drone.setPos(d0, d1, d2);
                drone.lerpSteps--;
            }

            final float dot = drone.qRotation.x * syncedQ.x
                + drone.qRotation.y * syncedQ.y
                + drone.qRotation.z * syncedQ.z
                + drone.qRotation.w * syncedQ.w;
            if (dot < 0.0F) {
                syncedQ.mul(-1.0F);
            }

            final float absDot = Math.abs(dot);
            if (absDot < 0.2F) {
                drone.qRotation.set(syncedQ);
            } else {
                drone.qRotation.slerp(syncedQ, CLIENT_ROTATION_SMOOTH_ALPHA);
                drone.qRotation.normalize();
            }
            drone.applyVisualOrientationFromQuaternion(drone.qRotation);
            drone.setRot(drone.getYRot(), drone.getXRot());

            final boolean currentlyArmed = drone.isArmed();
            final float currentThrust = drone.getThrust();

            final float volumeMult = 0.2F;

            if (currentlyArmed && !drone.wasArmedClient) {
                drone.level().playLocalSound(drone.getX(), drone.getY(), drone.getZ(),
                    FullfudRegistries.FPV_ENGINE_START.get(),
                    net.minecraft.sounds.SoundSource.NEUTRAL,
                    1.0F * volumeMult, 1.0F, false);
            }

            if (!currentlyArmed && drone.wasArmedClient) {
                drone.level().playLocalSound(drone.getX(), drone.getY(), drone.getZ(),
                    FullfudRegistries.FPV_ENGINE_STOP.get(),
                    net.minecraft.sounds.SoundSource.NEUTRAL,
                    1.0F * volumeMult, 1.0F, false);
            }

            final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (currentlyArmed && currentThrust > 0.01F) {
                if (drone.clientSoundInstance == null || !mc.getSoundManager().isActive((com.fullfud.fullfud.client.sound.FpvEngineSoundInstance) drone.clientSoundInstance)) {
                    final com.fullfud.fullfud.client.sound.FpvEngineSoundInstance sound = new com.fullfud.fullfud.client.sound.FpvEngineSoundInstance(drone);
                    mc.getSoundManager().play(sound);
                    drone.clientSoundInstance = sound;
                }
            }

            drone.wasArmedClient = currentlyArmed;

        }
    }

    private record ControlSession(ResourceKey<Level> dimension, Vec3 origin, float yaw, float pitch, GameType gameType) { }

    public void stopClientSound() {
        if (!level().isClientSide) {
            return;
        }
        if (clientSoundInstance instanceof com.fullfud.fullfud.client.sound.FpvEngineSoundInstance sound) {
            sound.stopSound();
        }
        clientSoundInstance = null;
    }

}
