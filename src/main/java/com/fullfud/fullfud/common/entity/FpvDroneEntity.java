package com.fullfud.fullfud.common.entity;

import com.fullfud.fullfud.common.item.FpvControllerItem;
import com.fullfud.fullfud.common.item.FpvGogglesItem;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.DroneExplosionLimiter;
import com.fullfud.fullfud.core.network.FakePlayerNettyChannelFix;
import com.fullfud.fullfud.core.network.FullfudNetwork;
import com.fullfud.fullfud.core.network.packet.DroneAudioLoopPacket;
import com.fullfud.fullfud.core.network.packet.DroneAudioOneShotPacket;
import com.fullfud.fullfud.core.network.packet.FpvControlPacket;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import dev.lazurite.lattice.api.player.LatticeServerPlayer;
import dev.lazurite.lattice.api.point.ViewPoint;
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
import net.minecraft.server.level.TicketType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraftforge.common.util.FakePlayer;
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
    
    private static final EntityDimensions DRONE_SIZE = EntityDimensions.scalable(0.7F, 0.25F);
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUNNING_ANIM = RawAnimation.begin().thenLoop("running");
    
    private static final double GRAVITY = 0.055D;
    private static final double AIR_DRAG = 0.96D; 
    private static final double DISARM_GRAVITY_MULT = 1.6D;
    private static final double DISARM_AIR_DRAG = 0.98D;
    private static final double MAX_THRUST = 0.12D;
    private static final double ROTATION_RATE_DEG = 5.0D;
    private static final double YAW_RATE_DEG = 4.0D;

    private static final double TICK_SECONDS = 1.0D / 20.0D;
    private static final double GRAVITY_ACCEL = GRAVITY * 400.0D; // blocks/sec^2 (from blocks/tick^2)
    private static final double MAX_THRUST_ACCEL = MAX_THRUST * 400.0D; // blocks/sec^2 (from blocks/tick^2)
    private static final double MAX_LINEAR_SPEED = 60.0D; // blocks/sec (prevents runaway at low drag)
    private static final double ANGULAR_ACCEL_DEG_PER_SEC2 = 900.0D;
    
    private static final int MAX_BATTERY_TICKS = 12000;
    
    private static final TicketType<Integer> FPV_TICKET = TicketType.create("fullfud_fpv", Integer::compareTo, 4);
    
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

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final Quaternionf qRotation = new Quaternionf();
    
    private boolean physicsInitialized = false;
    
    private float targetThrottle;
    private float throttleOutput;
    
    private float inputPitch;
    private float inputRoll;
    private float inputYaw;

    private double droneRoll;
    private double droneRollO;

    private Vec3 linearVelocity = Vec3.ZERO; // blocks/sec
    private float pitchRateRadPerSec;
    private float rollRateRadPerSec;
    private float yawRateRadPerSec;

    private int controlTimeout;
    private UUID owner;
    private ControlSession session;

    // Optional mode: keep drone chunks loaded even without a controlling player.
    private boolean keepChunksLoadedWithoutPlayer;
    
    private ChunkPos lastTicketPos;
    private int lastTicketRadius;
    private ChunkPos lastSentViewCenter;
    
    private RemotePilotFakePlayer avatar;
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
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_ARMED, false);
        this.entityData.define(DATA_THRUST, 0.0F);
        this.entityData.define(DATA_ROLL, 0.0F);
        this.entityData.define(DATA_CONTROLLER, Optional.empty());
        this.entityData.define(DATA_BATTERY, MAX_BATTERY_TICKS);
        this.entityData.define(DATA_SIGNAL_QUALITY, 1.0F);
    }

    @Override
    protected void readAdditionalSaveData(final CompoundTag tag) {
        setArmed(tag.getBoolean(TAG_ARMED));
        targetThrottle = tag.getFloat(TAG_THRUST);
        throttleOutput = targetThrottle;
        droneRoll = tag.getDouble(TAG_ROLL);
        droneRollO = droneRoll;
        
        if (tag.contains(TAG_BATTERY)) {
            entityData.set(DATA_BATTERY, tag.getInt(TAG_BATTERY));
        }
        
        updateQuaternionFromEuler();
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
        }
        keepChunksLoadedWithoutPlayer = tag.getBoolean(TAG_KEEP_CHUNKS);
        
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
    }

    @Override
    public void tick() {
        super.tick();
        droneRollO = droneRoll;
        
        if (!level().isClientSide()) {
            tickServer();
        } else {
            ClientSide.tick(this);
        }
    }

    private void tickServer() {
        if (!physicsInitialized) {
            updateQuaternionFromEuler();
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
        }

        if (entityData.get(DATA_CONTROLLER).isPresent()) {
            ServerPlayer controller = getController();
            if (controller == null) {
                entityData.set(DATA_CONTROLLER, Optional.empty());
                setArmed(false);
                removeAvatar();
            } else {
                if (tickCount % 5 == 0) {
                    calculateSignal(controller);
                }
                controlTimeout--;
                if (controlTimeout <= 0) {
                    endRemoteControl(controller);
                }
            }
        }
        
        final boolean shouldForceChunks = keepChunksLoadedWithoutPlayer && entityData.get(DATA_CONTROLLER).isEmpty();
        if (shouldForceChunks) {
            ensureChunkTicket();
        } else if (lastTicketPos != null) {
            releaseChunkTicket();
        }
        updateSimplePhysics();
        updateControllerBinding();
        final ServerPlayer controller = getController();
        if (controller != null && entityData.get(DATA_CONTROLLER).isPresent()) {
            // Ensure the player is still bound to this drone as the active view point.
            if (controller instanceof LatticeServerPlayer lattice) {
                final dev.lazurite.lattice.api.point.ViewPoint current = lattice.getViewPoint();
                if (current != this) {
                    setViewPoint(controller, this);
                    lastSentViewCenter = null;
                    com.fullfud.fullfud.core.RemoteControlFailsafe.forceChunkRefresh(controller);
                }
            }
            syncViewCenter(controller);
        }
        broadcastEngineAudio();
        
        Vec3 preMoveVelocity = getDeltaMovement();
        move(MoverType.SELF, preMoveVelocity);
        
        if (isArmed()) {
            if (horizontalCollision || verticalCollision) {
                double hSpeed = Math.sqrt(preMoveVelocity.x * preMoveVelocity.x + preMoveVelocity.z * preMoveVelocity.z);
                double limit = 0.5D; 
                
                if (hSpeed > limit) {
                    explode();
                    return;
                }
            }
            List<Entity> collisions = level().getEntities(this, getBoundingBox().inflate(0.2D), e -> !e.isSpectator() && e.isPickable());
            for (Entity entity : collisions) {
                if (avatar != null && entity == avatar) continue;
                explode();
                return;
            }
        }

        if (onGround()) {
            setDeltaMovement(getDeltaMovement().multiply(0.5D, 0.5D, 0.5D));
        }

        linearVelocity = getDeltaMovement().scale(20.0D);
        
        throttleOutput = Mth.lerp(0.2F, throttleOutput, targetThrottle);
        entityData.set(DATA_THRUST, throttleOutput);
        entityData.set(DATA_ROLL, (float) droneRoll);
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
        
        double dist = Math.sqrt(this.position().distanceToSqr(start));
        float currentSignal = 1.0F;
        
        if (dist > 600.0D) {
            currentSignal = 0.0F;
        } else if (dist > 500.0D) {
            currentSignal = 0.5F * (1.0F - (float)((dist - 500.0D) / 100.0D));
        } else {
            currentSignal = 1.0F - ((float)dist / 500.0F) * 0.5F;
        }
        
        if (currentSignal > 0.0F) {
            int obstacles = countObstacles(start, end);
            
            if (obstacles >= 15) {
                currentSignal = 0.0F;
            } else if (obstacles > 0) {
                currentSignal *= (1.0F - (obstacles / 15.0F));
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
        
        entityData.set(DATA_SIGNAL_QUALITY, Math.max(0.0F, currentSignal));
    }
    
    private int countObstacles(Vec3 start, Vec3 end) {
        Vec3 vector = end.subtract(start);
        double length = vector.length();
        Vec3 dir = vector.normalize();
        
        double stepSize = 0.5D;
        int steps = (int) (length / stepSize);
        
        int solidCount = 0;
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        
        for (int i = 0; i < steps; i++) {
            Vec3 point = start.add(dir.scale(i * stepSize));
            mPos.set(point.x, point.y, point.z);
            
            if (level().getBlockState(mPos).canOcclude()) {
                solidCount++;
            }
        }
        return solidCount / 2;
    }
    
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yaw;
        this.lerpXRot = pitch;
        this.lerpSteps = posRotationIncrements;
    }

    private void updateQuaternionFromEuler() {
        float yRad = (float) Math.toRadians(-getYRot());
        float xRad = (float) Math.toRadians(getXRot()); 
        float zRad = (float) Math.toRadians(droneRoll);
        qRotation.identity().rotateYXZ(yRad, xRad, zRad);
    }

    private void updateSimplePhysics() {
        final double dt = TICK_SECONDS;
        float pInputCurve = applyExpo(inputPitch, 0.4F);
        float rInputCurve = applyExpo(inputRoll, 0.4F);
        float yInputCurve = applyExpo(inputYaw, 0.4F);

        final float pitchTargetRadPerSec = (float) Math.toRadians(-pInputCurve * ROTATION_RATE_DEG * 20.0D);
        final float rollTargetRadPerSec = (float) Math.toRadians(-rInputCurve * ROTATION_RATE_DEG * 20.0D);
        final float yawTargetRadPerSec = (float) Math.toRadians(-yInputCurve * YAW_RATE_DEG * 20.0D);
        
        if (!isArmed()) {
            pitchRateRadPerSec = 0.0F;
            rollRateRadPerSec = 0.0F;
            yawRateRadPerSec = 0.0F;
        }

        final float maxDelta = (float) (Math.toRadians(ANGULAR_ACCEL_DEG_PER_SEC2) * dt);
        pitchRateRadPerSec = approach(pitchRateRadPerSec, Float.isFinite(pitchTargetRadPerSec) ? pitchTargetRadPerSec : 0.0F, maxDelta);
        rollRateRadPerSec = approach(rollRateRadPerSec, Float.isFinite(rollTargetRadPerSec) ? rollTargetRadPerSec : 0.0F, maxDelta);
        yawRateRadPerSec = approach(yawRateRadPerSec, Float.isFinite(yawTargetRadPerSec) ? yawTargetRadPerSec : 0.0F, maxDelta);

        final float yRad = (float) (yawRateRadPerSec * dt);
        final float pRad = (float) (pitchRateRadPerSec * dt);
        final float rRad = (float) (rollRateRadPerSec * dt);

        final Quaternionf delta = new Quaternionf()
            .rotateY(yRad)
            .rotateX(pRad)
            .rotateZ(rRad);

        qRotation.mul(delta);
        qRotation.normalize();

        final Vector3f euler = new Vector3f();
        qRotation.getEulerAnglesYXZ(euler);

        float newYaw = (float) Math.toDegrees(-euler.y);
        float newPitch = (float) Math.toDegrees(euler.x);
        float newRoll = (float) Math.toDegrees(euler.z);

        if (Float.isNaN(newYaw)) newYaw = this.getYRot();
        if (Float.isNaN(newPitch)) newPitch = this.getXRot();
        if (Float.isNaN(newRoll)) newRoll = (float) this.droneRoll;

        this.setYRot(Mth.wrapDegrees(newYaw));
        this.setXRot(Mth.wrapDegrees(newPitch));
        this.droneRoll = Mth.wrapDegrees(newRoll);

        Vector3f upVec = new Vector3f(0.0F, 1.0F, 0.0F);
        qRotation.transform(upVec);

        double thrustAccel = throttleOutput * MAX_THRUST_ACCEL;
         
        final boolean freeFall = getBatteryTicks() <= 0 || !isArmed();
        if (freeFall) {
            thrustAccel = 0;
        }

        final Vec3 thrustVec = new Vec3(upVec.x, upVec.y, upVec.z).scale(thrustAccel);

        final double gravityAccel = freeFall ? (GRAVITY_ACCEL * DISARM_GRAVITY_MULT) : GRAVITY_ACCEL;
        final double drag = freeFall ? DISARM_AIR_DRAG : AIR_DRAG;

        Vec3 accel = thrustVec.add(0, -gravityAccel, 0);

        linearVelocity = linearVelocity.add(accel.scale(dt));
        linearVelocity = linearVelocity.scale(drag);

        final double speed = linearVelocity.length();
        if (speed > MAX_LINEAR_SPEED) {
            linearVelocity = linearVelocity.scale(MAX_LINEAR_SPEED / speed);
        }

        setDeltaMovement(linearVelocity.scale(dt));
    }

    private float applyExpo(float input, float expo) {
        return input * (Math.abs(input) * expo + (1.0F - expo));
    }

    private static float approach(final float current, final float target, final float maxDelta) {
        final float delta = target - current;
        if (delta > maxDelta) return current + maxDelta;
        if (delta < -maxDelta) return current - maxDelta;
        return target;
    }
    
    private void ensureChunkTicket() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final ChunkPos currentPos = new ChunkPos(BlockPos.containing(position()));
        final int radius = Mth.clamp(serverLevel.getServer().getPlayerList().getViewDistance(), 2, 10);
        if (lastTicketPos == null || !lastTicketPos.equals(currentPos) || lastTicketRadius != radius) {
            if (lastTicketPos != null) {
                serverLevel.getChunkSource().removeRegionTicket(FPV_TICKET, lastTicketPos, lastTicketRadius, getId());
            }
            serverLevel.getChunkSource().addRegionTicket(FPV_TICKET, currentPos, radius, getId());
            lastTicketPos = currentPos;
            lastTicketRadius = radius;
        }
    }
    
    private void releaseChunkTicket() {
        if (!(level() instanceof ServerLevel serverLevel) || lastTicketPos == null) {
            lastTicketPos = null;
            lastTicketRadius = 0;
            return;
        }
        serverLevel.getChunkSource().removeRegionTicket(FPV_TICKET, lastTicketPos, lastTicketRadius, getId());
        lastTicketPos = null;
        lastTicketRadius = 0;
    }

    private void updateControllerBinding() {
        final ServerPlayer player = getController();
        if (player == null) {
            if (avatar != null) {
                removeAvatar();
            }
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
            explode();
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
            }
            releaseChunkTicket();
        }
        super.remove(reason);
    }

    private void explode() {
        if (detonating || isRemoved()) {
            return;
        }
        detonating = true;
        final ServerPlayer controller = getController();
        if (controller != null) {
            endRemoteControl(controller);
        }
        spawnFireballEffect();
        discard();
    }

    private void spawnFireballEffect() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final int power = Math.max(1, Math.round(FPV_FIREBALL_POWER));
        final ServerPlayer controller = getController();
        LargeFireball fireball;
        if (controller != null) {
            fireball = new LargeFireball(serverLevel, controller, 0.0D, 0.0D, 0.0D, power);
        } else {
            final Entity entity = EntityType.FIREBALL.create(serverLevel);
            if (!(entity instanceof LargeFireball created)) {
                return;
            }
            fireball = created;
        }
        fireball.moveTo(getX(), getY(), getZ(), 0.0F, 0.0F);
        fireball.setDeltaMovement(Vec3.ZERO);
        DroneExplosionLimiter.markNoBlockDamage(fireball);
        serverLevel.addFreshEntity(fireball);
        serverLevel.explode(fireball, getX(), getY(), getZ(), FPV_FIREBALL_POWER, net.minecraft.world.level.Level.ExplosionInteraction.MOB);
        fireball.discard();
    }

    private void dropAsItem() {
        spawnAtLocation(new ItemStack(FullfudRegistries.FPV_DRONE_ITEM.get()));
    }

    public void applyControl(final FpvControlPacket packet, final ServerPlayer sender) {
        if (!isController(sender)) {
            return;
        }

        if (!isRemoteStateValidFor(sender)) {
            return;
        }
        
        if (getSignalQuality() < 0.05F) {
            controlTimeout = 20;
            return;
        }

        if (!hasLinkedGoggles(sender) && !ensureLinkedGoggles(sender)) {
            return;
        }

        if (!Float.isFinite(packet.pitchInput())
            || !Float.isFinite(packet.rollInput())
            || !Float.isFinite(packet.yawInput())
            || !Float.isFinite(packet.throttle())) {
            return;
        }

        controlTimeout = 20;
        inputPitch = Mth.clamp(packet.pitchInput(), -1.0F, 1.0F);
        inputRoll = Mth.clamp(packet.rollInput(), -1.0F, 1.0F);
        inputYaw = Mth.clamp(packet.yawInput(), -1.0F, 1.0F);
        targetThrottle = Mth.clamp(packet.throttle(), 0.0F, 1.0F);
        
        if (packet.armAction() == 1) {
            if (getBatteryTicks() > 0) setArmed(true);
        } else if (packet.armAction() == 2) {
            setArmed(false);
            targetThrottle = 0.0F;
        }
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
        if (!hasLinkedGoggles(player) && !ensureLinkedGoggles(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.fullfud.fpv.need_goggles"), true);
            return false;
        }
        entityData.set(DATA_CONTROLLER, Optional.of(player.getUUID()));
        controlTimeout = 20;
        session = new ControlSession(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer());
        writeRemoteTag(player);
        // Force a clean rebind so chunk tracking doesn't get stuck after a prior control session.
        clearViewPoint(player);
        setViewPoint(player, this);
        lastSentViewCenter = null;
        syncViewCenter(player);
        com.fullfud.fullfud.core.RemoteControlFailsafe.forceChunkRefresh(player);
        return true;
    }

    public void endRemoteControl(final ServerPlayer player) {
        if (!entityData.get(DATA_CONTROLLER).isPresent() && session == null) {
            return;
        }
        final UUID controllerId = entityData.get(DATA_CONTROLLER).orElse(null);
        final ServerPlayer controlling = player != null ? player : resolvePlayer(controllerId);
        clearRemoteTag(controlling);
        if (controlling != null) {
            forceReturnCamera(controlling);
            resetViewCenter(controlling);
        }
        
        entityData.set(DATA_CONTROLLER, Optional.empty());
        
        inputPitch = 0;
        inputRoll = 0;
        inputYaw = 0;
        targetThrottle = 0;
        throttleOutput = 0;
        setArmed(false);
        
        controlTimeout = 0;
        session = null;
        removeAvatar();
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

    private static void setViewPoint(final ServerPlayer player, final Entity entity) {
        if (!(player instanceof LatticeServerPlayer lattice)) {
            return;
        }
        if (entity instanceof dev.lazurite.lattice.api.point.ViewPoint viewPoint) {
            lattice.setViewPoint(viewPoint);
        }
        lattice.setCameraWithoutViewPoint(entity);
    }

    private static void clearViewPoint(final ServerPlayer player) {
        if (!(player instanceof LatticeServerPlayer lattice)) {
            return;
        }
        lattice.removeViewPoint();
        lattice.setCameraWithoutViewPoint(player);
        // Ensure the server-side view point graph is re-bound to the player even if the camera entity didn't change.
        if (player instanceof dev.lazurite.lattice.api.point.ViewPoint viewPoint) {
            lattice.setViewPoint(viewPoint);
        }
    }

    private void syncViewCenter(final ServerPlayer player) {
        if (player == null || !(level() instanceof ServerLevel)) {
            return;
        }
        final ChunkPos chunkPos = this.chunkPosition();
        if (lastSentViewCenter == null || !lastSentViewCenter.equals(chunkPos)) {
            player.connection.send(new ClientboundSetChunkCacheCenterPacket(chunkPos.x, chunkPos.z));
            lastSentViewCenter = chunkPos;
        }
        com.fullfud.fullfud.core.RemoteControlFailsafe.forceChunkTracking(player);
    }

    private void resetViewCenter(final ServerPlayer player) {
        if (player == null) {
            return;
        }
        final ChunkPos chunkPos = player.chunkPosition();
        player.connection.send(new ClientboundSetChunkCacheCenterPacket(chunkPos.x, chunkPos.z));
        lastSentViewCenter = null;
        com.fullfud.fullfud.core.RemoteControlFailsafe.ensureLatticePlayerRegistered(player);
        com.fullfud.fullfud.core.RemoteControlFailsafe.forceChunkTracking(player);
        com.fullfud.fullfud.core.RemoteControlFailsafe.forceChunkRefresh(player);
        com.fullfud.fullfud.core.RemoteControlFailsafe.resetViewpointChunksToPlayer(player);
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
        player.getPersistentData().remove(PLAYER_REMOTE_TAG);
    }

    public void forceReleaseControlFor(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        if (!entityData.get(DATA_CONTROLLER).map(playerId::equals).orElse(false)) {
            return;
        }
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

        clearViewPoint(player);
        final ChunkPos chunkPos = player.chunkPosition();
        player.connection.send(new ClientboundSetChunkCacheCenterPacket(chunkPos.x, chunkPos.z));
        com.fullfud.fullfud.core.RemoteControlFailsafe.ensureLatticePlayerRegistered(player);
        com.fullfud.fullfud.core.RemoteControlFailsafe.forceChunkTracking(player);
        com.fullfud.fullfud.core.RemoteControlFailsafe.forceChunkRefresh(player);
        com.fullfud.fullfud.core.RemoteControlFailsafe.resetViewpointChunksToPlayer(player);
    }

    private void spawnAvatar(final ServerPlayer player) {
        if (!(level() instanceof ServerLevel serverLevel) || session == null) {
            return;
        }
        removeAvatar();
        GameProfile profile = new GameProfile(UUID.randomUUID(), makeAvatarProfileName(player.getGameProfile().getName(), "_fpv"));
        player.getGameProfile().getProperties().forEach((name, prop) -> profile.getProperties().put(name, new Property(prop.getName(), prop.getValue(), prop.getSignature())));
        avatar = new RemotePilotFakePlayer(serverLevel, profile, player.getUUID());
        avatar.syncFrom(player);
        avatar.setPos(session.origin.x, session.origin.y, session.origin.z);
        avatar.setYRot(session.yaw);
        avatar.setXRot(session.pitch);
        avatar.yHeadRot = session.yaw;
        avatar.yBodyRot = session.yaw;
        avatar.setDeltaMovement(Vec3.ZERO);
        avatar.setCustomName(Component.literal(player.getName().getString() + " [FPV]"));
        avatar.setCustomNameVisible(true);
        broadcastAvatarInfo(true);
        serverLevel.addFreshEntity(avatar);
    }

    private static String makeAvatarProfileName(final String baseName, final String suffix) {
        String base = (baseName == null || baseName.isBlank()) ? "Player" : baseName;
        String suf = (suffix == null) ? "" : suffix;

        final int max = 16;
        if (suf.length() > max) {
            suf = suf.substring(0, max);
        }
        final int baseMax = max - suf.length();
        if (baseMax <= 0) {
            return suf.substring(0, max);
        }
        if (base.length() > baseMax) {
            base = base.substring(0, baseMax);
        }
        return base + suf;
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
            if (level() instanceof ServerLevel serverLevel) {
                final ClientboundRemoveEntitiesPacket removePacket = new ClientboundRemoveEntitiesPacket(avatar.getId());
                for (final ServerPlayer viewer : serverLevel.getServer().getPlayerList().getPlayers()) {
                    viewer.connection.send(removePacket);
                }
            }
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
            final ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(avatar.getUUID()));
            for (final ServerPlayer viewer : serverLevel.getServer().getPlayerList().getPlayers()) {
                viewer.connection.send(packet);
            }
        }
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
            session = new ControlSession(sender.level().dimension(), sender.position(), sender.getYRot(), sender.getXRot(), sender.gameMode.getGameModeForPlayer());
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

    @OnlyIn(Dist.CLIENT)
    private static final class ClientSide {
        private static void tick(final FpvDroneEntity drone) {
            drone.throttleOutput = drone.entityData.get(DATA_THRUST);
            final float targetRoll = drone.entityData.get(DATA_ROLL);

            if (drone.lerpSteps > 0) {
                final double d0 = drone.getX() + (drone.lerpX - drone.getX()) / (double) drone.lerpSteps;
                final double d1 = drone.getY() + (drone.lerpY - drone.getY()) / (double) drone.lerpSteps;
                final double d2 = drone.getZ() + (drone.lerpZ - drone.getZ()) / (double) drone.lerpSteps;
                drone.setPos(d0, d1, d2);

                final float curY = (float) Math.toRadians(-drone.getYRot());
                final float curX = (float) Math.toRadians(drone.getXRot());
                final float curR = (float) Math.toRadians(drone.droneRoll);
                final Quaternionf currentQ = new Quaternionf().rotationYXZ(curY, curX, curR);

                final float tarY = (float) Math.toRadians(-drone.lerpYRot);
                final float tarX = (float) Math.toRadians(drone.lerpXRot);
                final float tarR = (float) Math.toRadians(targetRoll);
                final Quaternionf targetQ = new Quaternionf().rotationYXZ(tarY, tarX, tarR);

                currentQ.slerp(targetQ, 1.0F / (float) drone.lerpSteps);

                final Vector3f euler = new Vector3f();
                currentQ.getEulerAnglesYXZ(euler);

                drone.setYRot((float) Math.toDegrees(-euler.y));
                drone.setXRot((float) Math.toDegrees(euler.x));
                drone.droneRoll = (float) Math.toDegrees(euler.z);

                drone.lerpSteps--;
            } else {
                drone.droneRoll = Mth.rotLerp(0.5F, (float) drone.droneRoll, targetRoll);
            }

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

    private static final class RemotePilotFakePlayer extends FakePlayer {
        private final UUID ownerId;
        private boolean forwardingDamage;

        private RemotePilotFakePlayer(final ServerLevel level, final GameProfile profile, final UUID ownerId) {
            super(level, profile);
            this.ownerId = ownerId;
            this.setNoGravity(true);
            this.noPhysics = true;
            FakePlayerNettyChannelFix.ensureChannelPresent(this);
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
