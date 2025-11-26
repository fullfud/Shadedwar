package com.fullfud.fullfud.common.entity;

import com.fullfud.fullfud.common.item.FpvControllerItem;
import com.fullfud.fullfud.common.item.FpvGogglesItem;
import com.fullfud.fullfud.core.FullfudRegistries;
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
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;
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
    private static final double MAX_THRUST = 0.12D;
    private static final double ROTATION_RATE_DEG = 5.0D;
    private static final double YAW_RATE_DEG = 4.0D;
    
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

    private int controlTimeout;
    private UUID owner;
    private ControlSession session;
    
    private ChunkPos lastTicketPos;
    private int lastTicketRadius;
    
    private RemotePilotFakePlayer avatar;
    private boolean cameraPinned;
    
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    private boolean wasArmedClient = false;
    private Object clientSoundInstance = null;

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
        if (tag.hasUUID(TAG_OWNER)) {
            owner = tag.getUUID(TAG_OWNER);
        }
        if (tag.hasUUID(TAG_CONTROLLER)) {
            entityData.set(DATA_CONTROLLER, Optional.of(tag.getUUID(TAG_CONTROLLER)));
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
    }

    @Override
    public void tick() {
        super.tick();
        droneRollO = droneRoll;
        
        if (!level().isClientSide()) {
            tickServer();
        } else {
            tickClient();
        }
    }
    
    private void tickClient() {
        throttleOutput = entityData.get(DATA_THRUST);
        float targetRoll = entityData.get(DATA_ROLL);
        
        if (this.lerpSteps > 0) {
            double d0 = this.getX() + (this.lerpX - this.getX()) / (double) this.lerpSteps;
            double d1 = this.getY() + (this.lerpY - this.getY()) / (double) this.lerpSteps;
            double d2 = this.getZ() + (this.lerpZ - this.getZ()) / (double) this.lerpSteps;
            this.setPos(d0, d1, d2);

            float curY = (float) Math.toRadians(-this.getYRot());
            float curX = (float) Math.toRadians(this.getXRot());
            float curR = (float) Math.toRadians(this.droneRoll);
            Quaternionf currentQ = new Quaternionf().rotationYXZ(curY, curX, curR);

            float tarY = (float) Math.toRadians(-this.lerpYRot);
            float tarX = (float) Math.toRadians(this.lerpXRot);
            float tarR = (float) Math.toRadians(targetRoll);
            Quaternionf targetQ = new Quaternionf().rotationYXZ(tarY, tarX, tarR);

            currentQ.slerp(targetQ, 1.0F / (float) this.lerpSteps);

            Vector3f euler = new Vector3f();
            currentQ.getEulerAnglesYXZ(euler);

            this.setYRot((float) Math.toDegrees(-euler.y));
            this.setXRot((float) Math.toDegrees(euler.x));
            this.droneRoll = (float) Math.toDegrees(euler.z);

            this.lerpSteps--;
        } else {
            this.droneRoll = Mth.rotLerp(0.5F, (float)this.droneRoll, targetRoll);
        }
        
        this.setRot(this.getYRot(), this.getXRot());
        
        boolean currentlyArmed = isArmed();
        float currentThrust = getThrust();

        if (currentlyArmed && !wasArmedClient) {
            level().playLocalSound(getX(), getY(), getZ(), 
                FullfudRegistries.FPV_ENGINE_START.get(), 
                net.minecraft.sounds.SoundSource.NEUTRAL, 
                1.0F, 1.0F, false);
        }

        if (!currentlyArmed && wasArmedClient) {
            level().playLocalSound(getX(), getY(), getZ(), 
                FullfudRegistries.FPV_ENGINE_STOP.get(), 
                net.minecraft.sounds.SoundSource.NEUTRAL, 
                1.0F, 1.0F, false);
        }

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (currentlyArmed && currentThrust > 0.01F) {
            if (clientSoundInstance == null || !mc.getSoundManager().isActive((com.fullfud.fullfud.client.sound.FpvEngineSoundInstance) clientSoundInstance)) {
                 com.fullfud.fullfud.client.sound.FpvEngineSoundInstance sound = new com.fullfud.fullfud.client.sound.FpvEngineSoundInstance(this);
                 mc.getSoundManager().play(sound);
                 clientSoundInstance = sound;
            }
        }
        
        wasArmedClient = currentlyArmed;
    }

    private void tickServer() {
        if (!physicsInitialized) {
            updateQuaternionFromEuler();
            physicsInitialized = true;
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
        
        ensureChunkTicket();
        updateSimplePhysics();
        updateControllerBinding();
        
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
        
        throttleOutput = Mth.lerp(0.2F, throttleOutput, targetThrottle);
        entityData.set(DATA_THRUST, throttleOutput);
        entityData.set(DATA_ROLL, (float) droneRoll);
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
        float pInputCurve = applyExpo(inputPitch, 0.4F);
        float rInputCurve = applyExpo(inputRoll, 0.4F);
        float yInputCurve = applyExpo(inputYaw, 0.4F);

        float pRad = (float) Math.toRadians(-pInputCurve * ROTATION_RATE_DEG);
        float rRad = (float) Math.toRadians(-rInputCurve * ROTATION_RATE_DEG);
        float yRad = (float) Math.toRadians(-yInputCurve * YAW_RATE_DEG);
        
        if (!isArmed()) {
            pRad = 0;
            rRad = 0;
            yRad = 0;
        }

        if (Float.isNaN(pRad)) pRad = 0;
        if (Float.isNaN(rRad)) rRad = 0;
        if (Float.isNaN(yRad)) yRad = 0;

        Quaternionf delta = new Quaternionf()
            .rotateY(yRad)
            .rotateX(pRad)
            .rotateZ(rRad);

        qRotation.mul(delta);
        qRotation.normalize();

        Vector3f euler = new Vector3f();
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

        double thrustForce = throttleOutput * MAX_THRUST;
        
        if (getBatteryTicks() <= 0 || !isArmed()) {
            thrustForce = 0;
        }

        Vec3 thrustVec = new Vec3(upVec.x, upVec.y, upVec.z).scale(thrustForce);

        Vec3 motion = getDeltaMovement();
        motion = motion.add(thrustVec);
        motion = motion.add(0, -GRAVITY, 0); 
        motion = motion.scale(AIR_DRAG); 

        setDeltaMovement(motion);
    }

    private float applyExpo(float input, float expo) {
        return input * (Math.abs(input) * expo + (1.0F - expo));
    }
    
    private void ensureChunkTicket() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final ChunkPos currentPos = new ChunkPos(BlockPos.containing(position()));
        final int radius = 2;
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

        player.setInvisible(true);
        player.setSilent(true);
        player.setNoGravity(true);
        player.noPhysics = true;

        if (session != null && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        }
        
        if ((this.tickCount & 1) == 0) {
            player.connection.teleport(getX(), getY() + 1.0D, getZ(), player.getYRot(), player.getXRot());
            if (cameraPinned) {
                player.connection.send(new ClientboundSetCameraPacket(this));
            }
        }

        syncAvatar(player);
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
        if (isArmed()) {
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
        final ServerPlayer controller = getController();
        if (controller != null) {
            endRemoteControl(controller);
        }
        
        level().explode(this, getX(), getY(), getZ(), 4.0F, ExplosionInteraction.TNT);
        discard();
    }

    private void dropAsItem() {
        spawnAtLocation(new ItemStack(FullfudRegistries.FPV_DRONE_ITEM.get()));
    }

    public void applyControl(final FpvControlPacket packet, final ServerPlayer sender) {
        if (!isController(sender)) {
            return;
        }
        
        if (getSignalQuality() < 0.05F) {
            controlTimeout = 20;
            return;
        }

        controlTimeout = 20;
        inputPitch = packet.pitchInput();
        inputRoll = packet.rollInput();
        inputYaw = packet.yawInput();
        targetThrottle = Mth.clamp(packet.throttle(), 0.0F, 1.0F);
        
        if (packet.armAction() == 1) {
            if (getBatteryTicks() > 0) setArmed(true);
        } else if (packet.armAction() == 2) {
            setArmed(false);
            targetThrottle = 0.0F;
        }
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
        if (!hasLinkedGoggles(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.fullfud.fpv.need_goggles"), true);
            return false;
        }
        entityData.set(DATA_CONTROLLER, Optional.of(player.getUUID()));
        controlTimeout = 20;
        session = new ControlSession(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer());
        bindPlayer(player);
        spawnAvatar(player);
        player.connection.send(new ClientboundSetCameraPacket(this));
        cameraPinned = true;
        return true;
    }

    public void endRemoteControl(final ServerPlayer player) {
        if (!entityData.get(DATA_CONTROLLER).isPresent() && session == null) {
            return;
        }
        if (player != null) {
            forceReturnCamera(player);
            restorePlayer(player);
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
        cameraPinned = false;
        releaseChunkTicket();
    }
    
    private void forceReturnCamera(final ServerPlayer player) {
        if (player == null || player.connection == null) return;
        player.connection.send(new ClientboundSetCameraPacket(player));
    }
    
    private boolean isSignalLostFor(final ServerPlayer p) {
        return p == null || getSignalQuality() <= 0.0F;
    }

    private void bindPlayer(final ServerPlayer player) {
        player.setInvisible(true);
        player.setNoGravity(true);
        player.noPhysics = true;
        player.setDeltaMovement(Vec3.ZERO);
        if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        }
        player.onUpdateAbilities();
    }

    private void restorePlayer(final ServerPlayer player) {
        if (session == null) {
            player.setGameMode(GameType.SURVIVAL);
            player.setInvisible(false);
            player.setNoGravity(false);
            player.noPhysics = false;
            return;
        }
        ServerLevel targetLevel = player.server.getLevel(session.dimension);
        if (targetLevel == null) {
            targetLevel = player.serverLevel();
        }
        
        ChunkPos chunkPos = new ChunkPos(BlockPos.containing(session.origin));
        targetLevel.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkPos, 1, player.getId());

        player.teleportTo(targetLevel, session.origin.x, session.origin.y, session.origin.z, session.yaw, session.pitch);
        if (session.gameType != null) {
            player.setGameMode(session.gameType);
        } else {
            player.setGameMode(GameType.SURVIVAL);
        }
        player.setInvisible(false);
        player.setNoGravity(false);
        player.noPhysics = false;
        player.setDeltaMovement(Vec3.ZERO);
        player.onUpdateAbilities();
        removeAvatar();
    }

    private void spawnAvatar(final ServerPlayer player) {
        if (!(level() instanceof ServerLevel serverLevel) || session == null) {
            return;
        }
        removeAvatar();
        GameProfile profile = new GameProfile(UUID.randomUUID(), player.getGameProfile().getName());
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

    private record ControlSession(ResourceKey<Level> dimension, Vec3 origin, float yaw, float pitch, GameType gameType) { }

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