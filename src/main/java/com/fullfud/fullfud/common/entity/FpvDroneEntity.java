package com.fullfud.fullfud.common.entity;

import com.fullfud.fullfud.common.item.FpvControllerItem;
import com.fullfud.fullfud.common.item.FpvGogglesItem;
import com.fullfud.fullfud.common.item.MonitorItem;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.network.FullfudNetwork;
import com.fullfud.fullfud.core.network.packet.ShahedControlPacket;
import com.fullfud.fullfud.core.network.packet.ShahedStatusPacket;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class FpvDroneEntity extends Entity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.model.idle");
    private static final double TICK_SECONDS = 1.0D / 20.0D;
    private static final double MASS_KG = 1.35D;
    private static final double GRAVITY = 9.80665D;
    private static final double MAX_THRUST_NEWTONS = 92.0D;
    private static final double MAX_TILT_DEG = 55.0D;
    private static final double MAX_ATTITUDE_RATE_DEG = 360.0D;
    private static final double MAX_YAW_RATE_DEG = 720.0D;
    private static final double DRAG_COEFF = 0.65D;
    private static final double REFERENCE_AREA = 0.032D;
    private static final double BATTERY_CAPACITY_MAH = 4200.0D;
    private static final double BASE_DRAIN_PER_SEC = 18.0D;
    private static final double MOTOR_DRAIN_PER_SEC = 240.0D;
    private static final double MIN_CELL_VOLTAGE = 3.3D;
    private static final double MAX_CELL_VOLTAGE = 4.2D;
    private static final int BATTERY_CELLS = 6;
    private static final int COMMAND_TIMEOUT_TICKS = 40;
    private static final String TAG_VELOCITY = "Velocity";
    private static final String TAG_BATTERY = "Battery";
    private static final String TAG_OPERATOR = "Operator";
    private static final String TAG_ARMED = "Armed";
    private static final String TAG_BODY_YAW = "BodyYaw";
    private static final String TAG_BODY_PITCH = "BodyPitch";
    private static final String TAG_BODY_ROLL = "BodyRoll";

    private static final int STATUS_INTERVAL = 2;

    private static final TicketType<Integer> FPV_TICKET = TicketType.create("fullfud_fpv", Integer::compareTo, 4);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private Vec3 linearVelocity = Vec3.ZERO;
    private ControlInput desiredInput = ControlInput.neutral();
    private ControlInput filteredInput = ControlInput.neutral();
    private double bodyYaw;
    private double bodyPitch;
    private double bodyRoll;
    private double batteryMah = BATTERY_CAPACITY_MAH;
    private double yawRate;
    private long lastCommandTick;
    private UUID operatorUUID;
    private boolean landingOverride;
    private FailsafeReason failsafeReason = FailsafeReason.NONE;
    private float prevVisualPitch;
    private float visualPitch;
    private float prevVisualRoll;
    private float visualRoll;
    private final Set<UUID> viewers = new HashSet<>();
    private final Map<UUID, Integer> viewerDistances = new HashMap<>();
    private int statusTicker = STATUS_INTERVAL;
    private boolean crashed;
    private ChunkPos lastTicketPos;
    private int lastTicketRadius;
    private int desiredChunkRadius;
    private ControlSession controlSession;
    private RemotePilotFakePlayer avatar;

    public FpvDroneEntity(final EntityType<? extends FpvDroneEntity> type, final Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = false;
        this.blocksBuilding = true;
        this.bodyYaw = this.getYRot();
        this.visualPitch = 0.0F;
        this.prevVisualPitch = 0.0F;
        this.visualRoll = 0.0F;
        this.prevVisualRoll = 0.0F;
        this.statusTicker = STATUS_INTERVAL;
        this.crashed = false;
    }

    public static Optional<FpvDroneEntity> find(final ServerLevel level, final UUID uuid) {
        final Entity entity = level.getEntity(uuid);
        if (entity instanceof FpvDroneEntity drone) {
            return Optional.of(drone);
        }
        return Optional.empty();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DroneDataKeys.THRUST, 0.0F);
        entityData.define(DroneDataKeys.BATTERY, 1.0F);
        entityData.define(DroneDataKeys.ARMED, false);
        entityData.define(DroneDataKeys.FAILSAFE, false);
    }

    @Override
    protected void readAdditionalSaveData(final CompoundTag tag) {
        if (tag.contains(TAG_BATTERY)) {
            batteryMah = tag.getDouble(TAG_BATTERY);
            entityData.set(DroneDataKeys.BATTERY, (float) (batteryMah / BATTERY_CAPACITY_MAH));
        }
        if (tag.contains(TAG_ARMED)) {
            entityData.set(DroneDataKeys.ARMED, tag.getBoolean(TAG_ARMED));
        }
        if (tag.contains(TAG_BODY_YAW)) {
            bodyYaw = tag.getDouble(TAG_BODY_YAW);
            setYRot((float) bodyYaw);
            setYHeadRot((float) bodyYaw);
        }
        if (tag.contains(TAG_BODY_PITCH)) {
            bodyPitch = tag.getDouble(TAG_BODY_PITCH);
            setXRot((float) bodyPitch);
        }
        if (tag.contains(TAG_BODY_ROLL)) {
            bodyRoll = tag.getDouble(TAG_BODY_ROLL);
        }
        if (tag.hasUUID(TAG_OPERATOR)) {
            operatorUUID = tag.getUUID(TAG_OPERATOR);
        }
        if (tag.contains(TAG_VELOCITY, Tag.TAG_LIST)) {
            final ListTag list = tag.getList(TAG_VELOCITY, Tag.TAG_DOUBLE);
            if (list.size() == 3) {
                linearVelocity = new Vec3(list.getDouble(0), list.getDouble(1), list.getDouble(2));
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(final CompoundTag tag) {
        tag.putDouble(TAG_BATTERY, batteryMah);
        tag.putBoolean(TAG_ARMED, isArmed());
        tag.putDouble(TAG_BODY_YAW, bodyYaw);
        tag.putDouble(TAG_BODY_PITCH, bodyPitch);
        tag.putDouble(TAG_BODY_ROLL, bodyRoll);
        if (operatorUUID != null) {
            tag.putUUID(TAG_OPERATOR, operatorUUID);
        }
        final ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(linearVelocity.x));
        list.add(DoubleTag.valueOf(linearVelocity.y));
        list.add(DoubleTag.valueOf(linearVelocity.z));
        tag.put(TAG_VELOCITY, list);
    }

    @Override
    public void tick() {
        super.tick();
        prevVisualPitch = visualPitch;
        prevVisualRoll = visualRoll;
        if (level().isClientSide()) {
            visualPitch = (float) bodyPitch;
            visualRoll = (float) bodyRoll;
            return;
        }
        updateCommandLink();
        updateFlightComputer();
        updateVisualPose();
        setDeltaMovement(linearVelocity.scale(TICK_SECONDS));
        move(MoverType.SELF, getDeltaMovement());
        dampenOnCollision();
        stabilizeIdleOrientation();

        if (!level().isClientSide() && !viewers.isEmpty()) {
            if (--statusTicker <= 0) {
                statusTicker = STATUS_INTERVAL;
                broadcastStatus();
            }
        }
    }

    @Override
    public void remove(final RemovalReason reason) {
        releaseChunkTicket();
        final ServerPlayer controller = getControllingPlayer();
        if (controller != null) {
            restorePlayer(controller);
        }
        removeAvatar();
        viewers.clear();
        viewerDistances.clear();
        super.remove(reason);
    }

    private void updateCommandLink() {
        final boolean signalLost = operatorUUID != null && tickCount - lastCommandTick > COMMAND_TIMEOUT_TICKS;
        final boolean lowBattery = batteryMah <= BATTERY_CAPACITY_MAH * 0.05D;
        final double range = operatorDistance();
        final boolean rangeLost = range >= 1000.0D;
        landingOverride = signalLost || lowBattery || rangeLost;
        if (lowBattery) {
            failsafeReason = FailsafeReason.LOW_BATTERY;
        } else if (signalLost || rangeLost) {
            failsafeReason = FailsafeReason.SIGNAL_LOSS;
        } else {
            failsafeReason = FailsafeReason.NONE;
        }
        entityData.set(DroneDataKeys.FAILSAFE, landingOverride);
    }

    private void updateFlightComputer() {
        final double dt = TICK_SECONDS;
        final ControlInput targetInput = landingOverride
            ? new ControlInput(Math.min(desiredInput.throttle(), 0.35F), -0.2F, 0.0F, 0.0F)
            : desiredInput;

        filteredInput = filteredInput.towards(targetInput, dt * 6.0D);
        setThrottle(filteredInput.throttle());
        integrateAttitude(dt);
        integrateVelocity(dt);
        updateBattery(dt, filteredInput.throttle());
        if (!isArmed() && linearVelocity.lengthSqr() < 1.0E-3D) {
            linearVelocity = Vec3.ZERO;
        }
    }

    private void integrateAttitude(final double dt) {
        if (!isArmed()) {
            yawRate = 0.0D;
            bodyYaw = this.getYRot();
            this.setYHeadRot(this.getYRot());
            return;
        }

        final double pitchTarget = filteredInput.pitch() * MAX_TILT_DEG;
        final double rollTarget = filteredInput.roll() * MAX_TILT_DEG;
        final double yawTargetRate = filteredInput.yaw() * MAX_YAW_RATE_DEG;

        bodyPitch = approach(bodyPitch, pitchTarget, MAX_ATTITUDE_RATE_DEG * dt);
        bodyRoll = approach(bodyRoll, rollTarget, MAX_ATTITUDE_RATE_DEG * dt);
        yawRate = approach(yawRate, yawTargetRate, MAX_YAW_RATE_DEG * 2.0D * dt);
        bodyYaw = Mth.wrapDegrees(bodyYaw + yawRate * dt);

        setYRot((float) bodyYaw);
        setYHeadRot((float) bodyYaw);
        setXRot((float) bodyPitch);
    }

    private void integrateVelocity(final double dt) {
        if (!isArmed()) {
            linearVelocity = linearVelocity.scale(0.90D).add(0.0D, -GRAVITY * dt * 0.25D, 0.0D);
            return;
        }
        final OrientationBasis basis = orientationBasis();
        final double thrustNewtons = MAX_THRUST_NEWTONS * filteredInput.throttle();
        final Vec3 thrust = basis.up.scale(thrustNewtons / MASS_KG);
        final Vec3 gravity = new Vec3(0.0D, -GRAVITY, 0.0D);
        final Vec3 drag = linearVelocity.lengthSqr() < 1.0E-6D
            ? Vec3.ZERO
            : linearVelocity.scale(-DRAG_COEFF * linearVelocity.length() * REFERENCE_AREA / MASS_KG);
        final Vec3 acceleration = thrust.add(gravity).add(drag);
        linearVelocity = linearVelocity.add(acceleration.scale(dt));
    }

    private void updateBattery(final double dt, final float throttle) {
        if (!isArmed()) {
            return;
        }
        final double drain = BASE_DRAIN_PER_SEC + MOTOR_DRAIN_PER_SEC * Mth.clamp(throttle, 0.0F, 1.0F);
        batteryMah = Math.max(0.0D, batteryMah - drain * dt);
        entityData.set(DroneDataKeys.BATTERY, (float) (batteryMah / BATTERY_CAPACITY_MAH));
        if (batteryMah <= 0.0D) {
            disarm(true);
        }
    }

    private void updateVisualPose() {
        visualPitch = (float) approach(visualPitch, bodyPitch, 10.0D * TICK_SECONDS);
        visualRoll = (float) approach(visualRoll, bodyRoll, 15.0D * TICK_SECONDS);
    }

    private void dampenOnCollision() {
        if (this.horizontalCollision) {
            linearVelocity = new Vec3(0.0D, linearVelocity.y * 0.5D, 0.0D);
        }
        if (this.verticalCollision && linearVelocity.y < 0.0D) {
            linearVelocity = new Vec3(linearVelocity.x * 0.65D, 0.0D, linearVelocity.z * 0.65D);
        }
        checkCrashExplosion();
    }

    private void checkCrashExplosion() {
        if (crashed || level().isClientSide()) {
            return;
        }
        final double speedSq = linearVelocity.lengthSqr();
        final double threshold = 1.5D * 1.5D;
        if ((horizontalCollision || verticalCollision) && speedSq >= threshold) {
            crashed = true;
            level().explode(this, getX(), getY(), getZ(), 1.5F, Level.ExplosionInteraction.NONE);
            discard();
        }
    }

    private OrientationBasis orientationBasis() {
        final Quaternionf rotation = new Quaternionf()
            .rotationYXZ((float) Math.toRadians(-bodyYaw), (float) Math.toRadians(bodyPitch), (float) Math.toRadians(bodyRoll));
        final Vector3f forwardVec = new Vector3f(0.0F, 0.0F, 1.0F).rotate(rotation).normalize();
        final Vector3f upVec = new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation).normalize();
        final Vector3f rightVec = new Vector3f(1.0F, 0.0F, 0.0F).rotate(rotation).normalize();
        return new OrientationBasis(
            new Vec3(forwardVec.x(), forwardVec.y(), forwardVec.z()),
            new Vec3(upVec.x(), upVec.y(), upVec.z()),
            new Vec3(rightVec.x(), rightVec.y(), rightVec.z())
        );
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
        if (level().isClientSide()) {
            return true;
        }
        if (amount > 6.0F) {
            disarm(false);
        }
        if (amount >= 12.0F || source.is(DamageTypeTags.IS_EXPLOSION)) {
            dropSelf();
            discard();
        }
        return true;
    }

    @Override
    protected void checkFallDamage(final double y, final boolean onGround, final BlockState state, final BlockPos pos) {
    }

    @Override
    protected void playStepSound(final BlockPos pos, final BlockState state) {
    }

    @Override
    public boolean causeFallDamage(final float fallDistance, final float damageMultiplier, final DamageSource source) {
        return false;
    }

    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        final ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof MonitorItem) {
            MonitorItem.setLinkedFpvDrone(stack, getUUID());
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.monitor.linked"), true);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (stack.getItem() instanceof FpvGogglesItem) {
            FpvGogglesItem.setLinkedDrone(stack, getUUID());
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.goggles.linked"), true);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (stack.isEmpty() && player.isShiftKeyDown()) {
            final ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
            if (head.getItem() instanceof FpvGogglesItem) {
                FpvGogglesItem.setLinkedDrone(head, getUUID());
                player.displayClientMessage(Component.translatable("message.fullfud.fpv.goggles.linked"), true);
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }
        if (stack.getItem() instanceof FpvControllerItem) {
            return linkController(serverPlayer, stack);
        }
        if (player.isShiftKeyDown() && stack.isEmpty() && !isArmed()) {
            final ItemStack drop = new ItemStack(FullfudRegistries.FPV_DRONE_ITEM.get());
            if (!player.addItem(drop)) {
                spawnAtLocation(drop);
            }
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.recovered"), true);
            discard();
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown()) {
            assignOperator(serverPlayer);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (!isOperator(serverPlayer)) {
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.not_owner"), true);
            return InteractionResult.FAIL;
        }

        if (isArmed()) {
            disarm(false);
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.disarmed"), true);
            playVoice(serverPlayer, FullfudRegistries.FPV_VOICE_DISARMED.get());
        } else {
            arm();
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.armed"), true);
            playVoice(serverPlayer, FullfudRegistries.FPV_VOICE_ARMED.get());
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    public void assignOperator(final ServerPlayer player) {
        if (operatorUUID != null && !operatorUUID.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.not_owner"), true);
            return;
        }
        if (!hasLinkedController(player)) {
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.controller.required"), true);
            return;
        }
        operatorUUID = player.getUUID();
        lastCommandTick = tickCount;
        player.displayClientMessage(Component.translatable("message.fullfud.fpv.operator_bound"), true);
    }

    public boolean isOperator(final ServerPlayer player) {
        return operatorUUID != null && operatorUUID.equals(player.getUUID());
    }

    public void applyInput(final ServerPlayer player, final ControlInput input) {
        if (!isOperator(player)) {
            return;
        }
        if (!hasLinkedController(player)) {
            if (player.tickCount % 20 == 0) {
                player.displayClientMessage(Component.translatable("message.fullfud.fpv.controller.required"), true);
            }
            return;
        }
        desiredInput = input.clamped();
        lastCommandTick = tickCount;
    }

    private void arm() {
        entityData.set(DroneDataKeys.ARMED, true);
        desiredInput = new ControlInput(0.2F, 0.0F, 0.0F, 0.0F);
    }

    private void disarm(final boolean dueToBattery) {
        entityData.set(DroneDataKeys.ARMED, false);
        desiredInput = ControlInput.neutral();
        filteredInput = ControlInput.neutral();
        if (dueToBattery) {
            landingOverride = true;
            failsafeReason = FailsafeReason.LOW_BATTERY;
        }
    }

    public boolean isArmed() {
        return entityData.get(DroneDataKeys.ARMED);
    }

    public float getThrottle() {
        return entityData.get(DroneDataKeys.THRUST);
    }

    private void setThrottle(final float throttle) {
        entityData.set(DroneDataKeys.THRUST, Mth.clamp(throttle, 0.0F, 1.0F));
    }

    public float getBatteryLevel() {
        return entityData.get(DroneDataKeys.BATTERY);
    }

    public boolean isFailsafeActive() {
        return entityData.get(DroneDataKeys.FAILSAFE);
    }

    public float getVisualPitch(final float partialTick) {
        return Mth.lerp(partialTick, prevVisualPitch, visualPitch);
    }

    public float getVisualRoll(final float partialTick) {
        return Mth.lerp(partialTick, prevVisualRoll, visualRoll);
    }

    public Optional<FailsafeReason> getFailsafeReason() {
        return failsafeReason == FailsafeReason.NONE ? Optional.empty() : Optional.of(failsafeReason);
    }

    public Optional<UUID> getOperatorUUID() {
        return Optional.ofNullable(operatorUUID);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public EntityDimensions getDimensions(final Pose pose) {
        return EntityDimensions.scalable(0.45F, 0.16F);
    }

    @Override
    public void registerControllers(final AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    private void stabilizeIdleOrientation() {
        if (level().isClientSide() || isArmed()) {
            return;
        }
        yawRate = 0.0D;
        filteredInput = ControlInput.neutral();
        desiredInput = ControlInput.neutral();
        bodyYaw = this.getYRot();
        this.setYHeadRot(this.getYRot());
    }

    private void dropSelf() {
        final ItemStack stack = new ItemStack(FullfudRegistries.FPV_DRONE_ITEM.get());
        spawnAtLocation(stack);
    }

    private InteractionResult linkController(final ServerPlayer player, final ItemStack controllerStack) {
        FpvControllerItem.setLinkedDrone(controllerStack, getUUID());
        player.displayClientMessage(Component.translatable("message.fullfud.fpv.controller.paired"), true);
        playVoice(player, FullfudRegistries.FPV_VOICE_LINKED.get());
        assignOperator(player);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    private boolean hasLinkedController(final ServerPlayer player) {
        return controllerMatches(player.getMainHandItem()) || controllerMatches(player.getOffhandItem());
    }

    private boolean hasLinkedGoggles(final ServerPlayer player) {
        return FpvGogglesItem.isLinkedTo(player, getUUID());
    }

    public boolean beginRemoteSession(final ServerPlayer player) {
        if (!hasLinkedController(player)) {
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.controller.required"), true);
            return false;
        }
        if (!hasLinkedGoggles(player)) {
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.goggles.required"), true);
            return false;
        }
        assignOperator(player);
        if (!isOperator(player)) {
            return false;
        }
        if (controlSession == null) {
            controlSession = new ControlSession(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer());
            spawnAvatar(player);
        } else {
            syncAvatar(player);
        }
        bindPlayerToDrone(player);
        crashed = false;
        if (!isArmed()) {
            arm();
            player.displayClientMessage(Component.translatable("message.fullfud.fpv.armed"), true);
        }
        addViewer(player);
        return true;
    }

    public void endRemoteSession(final ServerPlayer player) {
        removeViewer(player);
        if (operatorUUID != null && operatorUUID.equals(player.getUUID())) {
            restorePlayer(player);
            operatorUUID = null;
            controlSession = null;
        }
        if (viewers.isEmpty()) {
            disarm(false);
        }
    }

    public void handleControlPacket(final ShahedControlPacket packet, final ServerPlayer sender) {
        final float forward = Mth.clamp(packet.forward(), -1.0F, 1.0F);
        final float yaw = Mth.clamp(packet.strafe(), -1.0F, 1.0F);
        final float verticalThrust = Mth.clamp(packet.vertical(), -1.0F, 1.0F);
        final float throttleDelta = packet.thrustDelta() + verticalThrust * 0.08F;
        final float throttle = Mth.clamp(getThrottle() + throttleDelta, 0.0F, 1.0F);
        applyInput(sender, new ControlInput(throttle, forward, 0.0F, yaw));
    }

    public void addViewer(final ServerPlayer player) {
        viewers.add(player.getUUID());
        viewerDistances.put(player.getUUID(), resolveViewDistance(player));
        sendStatusTo(player);
        recalcDesiredChunkRadius();
        ensureChunkTicket();
    }

    public void removeViewer(final ServerPlayer player) {
        viewers.remove(player.getUUID());
        viewerDistances.remove(player.getUUID());
        recalcDesiredChunkRadius();
        if (viewerDistances.isEmpty()) {
            releaseChunkTicket();
        } else {
            ensureChunkTicket();
        }
    }

    private void playVoice(final ServerPlayer player, final SoundEvent event) {
        if (player == null || event == null) {
            return;
        }
        player.playSound(event, 0.9F, 1.0F);
    }

    private ServerPlayer getControllingPlayer() {
        if (operatorUUID == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getServer().getPlayerList().getPlayer(operatorUUID);
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
        final GameProfile original = player.getGameProfile();
        final GameProfile profile = new GameProfile(UUID.randomUUID(), original.getName() + " [FPV]");
        original.getProperties().keySet().forEach(key -> {
            for (final Property property : original.getProperties().get(key)) {
                profile.getProperties().put(key, property);
            }
        });
        avatar = new RemotePilotFakePlayer(serverLevel, profile, player.getUUID());
        avatar.syncFrom(player);
        avatar.setPos(controlSession.originPos.x, controlSession.originPos.y, controlSession.originPos.z);
        avatar.setYRot(controlSession.originYaw);
        avatar.setXRot(controlSession.originPitch);
        avatar.yHeadRot = controlSession.originYaw;
        avatar.yBodyRot = controlSession.originYaw;
        avatar.setCustomName(Component.literal(player.getName().getString()));
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

    private record ControlSession(ResourceKey<Level> originDimension, Vec3 originPos, float originYaw, float originPitch, GameType originalGameType) {
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
            if (!(level() instanceof ServerLevel serverLevel)) {
                return null;
            }
            return serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        }

    }

    private int resolveViewDistance(final ServerPlayer player) {
        return Math.max(2, player.serverLevel().getServer().getPlayerList().getViewDistance());
    }

    private void recalcDesiredChunkRadius() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            desiredChunkRadius = 0;
            return;
        }
        final int serverCap = Math.max(2, serverLevel.getServer().getPlayerList().getViewDistance());
        int desired = 0;
        for (final int distance : viewerDistances.values()) {
            desired = Math.max(desired, Mth.clamp(distance, 2, serverCap));
        }
        desiredChunkRadius = desired;
    }

    private void ensureChunkTicket() {
        if (!(level() instanceof ServerLevel serverLevel) || desiredChunkRadius <= 0) {
            releaseChunkTicket();
            return;
        }
        final ChunkPos chunkPos = this.chunkPosition();
        if (chunkPos.equals(lastTicketPos) && desiredChunkRadius == lastTicketRadius) {
            return;
        }
        releaseChunkTicket();
        final ServerChunkCache chunkSource = serverLevel.getChunkSource();
        chunkSource.addRegionTicket(FPV_TICKET, chunkPos, desiredChunkRadius, this.getId());
        lastTicketPos = chunkPos;
        lastTicketRadius = desiredChunkRadius;
    }

    private void releaseChunkTicket() {
        if (!(level() instanceof ServerLevel serverLevel) || lastTicketPos == null) {
            lastTicketPos = null;
            lastTicketRadius = 0;
            return;
        }
        serverLevel.getChunkSource().removeRegionTicket(FPV_TICKET, lastTicketPos, Math.max(1, lastTicketRadius), this.getId());
        lastTicketPos = null;
        lastTicketRadius = 0;
    }

    private boolean controllerMatches(final ItemStack stack) {
        if (!FpvControllerItem.isController(stack)) {
            return false;
        }
        return FpvControllerItem.getLinkedDrone(stack)
            .map(id -> id.equals(getUUID()))
            .orElse(false);
    }

    private void broadcastStatus() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final ShahedStatusPacket packet = composeStatusPacket();
        if (packet == null) {
            return;
        }
        viewers.removeIf(viewerId -> serverLevel.getServer().getPlayerList().getPlayer(viewerId) == null);
        for (final UUID viewerId : viewers) {
            final ServerPlayer viewer = serverLevel.getServer().getPlayerList().getPlayer(viewerId);
            if (viewer != null) {
                FullfudNetwork.getChannel().send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    private void sendStatusTo(final ServerPlayer viewer) {
        final ShahedStatusPacket packet = composeStatusPacket();
        if (packet == null) {
            return;
        }
        FullfudNetwork.getChannel().send(PacketDistributor.PLAYER.with(() -> viewer), packet);
    }

    private ShahedStatusPacket composeStatusPacket() {
        final Vec3 velocity = this.linearVelocity;
        final float airSpeed = (float) velocity.length();
        final float groundSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        final float verticalSpeed = (float) velocity.y;
        final float angleOfAttack = (float) bodyPitch;
        final float slip = (float) Mth.clamp(velocity.x - velocity.z, -5.0D, 5.0D);
        final float fuelKg = (float) (batteryMah / 1000.0D);
        final float airDensity = 1.225F;
        final double operatorRange = operatorDistance();
        final float noiseLevel = noiseLevelForDistance(operatorRange);
        final boolean signalLost = operatorRange >= 1000.0D;
        return new ShahedStatusPacket(
            getUUID(),
            getX(),
            getY(),
            getZ(),
            (float) this.getYRot(),
            (float) bodyPitch,
            getThrottle(),
            noiseLevel,
            signalLost,
            airSpeed,
            groundSpeed,
            verticalSpeed,
            angleOfAttack,
            slip,
            fuelKg,
            airDensity
        );
    }

    private double operatorDistance() {
        if (!(level() instanceof ServerLevel serverLevel) || operatorUUID == null) {
            return Double.POSITIVE_INFINITY;
        }
        final ServerPlayer operator = serverLevel.getServer().getPlayerList().getPlayer(operatorUUID);
        if (operator == null) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.sqrt(operator.distanceToSqr(this));
    }

    private static float noiseLevelForDistance(final double distance) {
        if (!Double.isFinite(distance)) {
            return 1.0F;
        }
        if (distance <= 250.0D) {
            return 0.0F;
        }
        if (distance <= 500.0D) {
            final double t = (distance - 250.0D) / 250.0D;
            return (float) (0.15D + t * 0.25D);
        }
        if (distance <= 750.0D) {
            final double t = (distance - 500.0D) / 250.0D;
            return (float) (0.4D + t * 0.35D);
        }
        if (distance <= 1000.0D) {
            final double t = (distance - 750.0D) / 250.0D;
            return (float) (0.75D + t * 0.25D);
        }
        return 1.0F;
    }

    private static double approach(final double current, final double target, final double maxStep) {
        final double delta = Mth.clamp(target - current, -maxStep, maxStep);
        return current + delta;
    }

    private record OrientationBasis(Vec3 forward, Vec3 up, Vec3 right) {
    }

    public record ControlInput(float throttle, float pitch, float roll, float yaw) {
        public static ControlInput neutral() {
            return new ControlInput(0.0F, 0.0F, 0.0F, 0.0F);
        }

        private ControlInput clamped() {
            return new ControlInput(
                Mth.clamp(throttle, 0.0F, 1.0F),
                Mth.clamp(pitch, -1.0F, 1.0F),
                Mth.clamp(roll, -1.0F, 1.0F),
                Mth.clamp(yaw, -1.0F, 1.0F)
            );
        }

        private ControlInput towards(final ControlInput target, final double rate) {
            final float throttleValue = (float) approach(throttle, target.throttle, rate);
            final float pitchValue = (float) approach(pitch, target.pitch, rate);
            final float rollValue = (float) approach(roll, target.roll, rate);
            final float yawValue = (float) approach(yaw, target.yaw, rate);
            return new ControlInput(throttleValue, pitchValue, rollValue, yawValue);
        }
    }

    public enum FailsafeReason {
        NONE,
        SIGNAL_LOSS,
        LOW_BATTERY
    }

    private static final class DroneDataKeys {
        private static final EntityDataAccessor<Float> THRUST = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
        private static final EntityDataAccessor<Float> BATTERY = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
        private static final EntityDataAccessor<Boolean> ARMED = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> FAILSAFE = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.BOOLEAN);

        private DroneDataKeys() {
        }
    }
}
