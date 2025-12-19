package com.fullfud.fullfud.common.entity;

import com.fullfud.fullfud.common.item.RebBatteryItem;
import com.fullfud.fullfud.core.FullfudRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class RebEmitterEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_HAS_BATTERY = SynchedEntityData.defineId(RebEmitterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_CHARGE_TICKS = SynchedEntityData.defineId(RebEmitterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_STARTUP_DONE = SynchedEntityData.defineId(RebEmitterEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation START_ANIMATION = RawAnimation.begin().then("animation.reb.start", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation IDLE_ANIMATION  = RawAnimation.begin().thenLoop("animation.reb.idle");

    private static final int STARTUP_DURATION_TICKS = 3 * 20;

    private ItemStack battery = ItemStack.EMPTY;
    private int chargeTicks;
    private boolean fallingFromSupport;
    private boolean wasOnGround;
    private int energyTickCounter;
    private int startupTicks;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public RebEmitterEntity(final EntityType<? extends RebEmitterEntity> type, final Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_HAS_BATTERY, false);
        entityData.define(DATA_CHARGE_TICKS, 0);
        entityData.define(DATA_STARTUP_DONE, false);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            final Vec3 motion = getDeltaMovement();
            setDeltaMovement(0.0D, motion.y, 0.0D);

            if (wasOnGround && !onGround()) {
                fallingFromSupport = true;
            }
            if (fallingFromSupport && onGround()) {
                dropContents();
                discard();
            }

            updateStartup();
            drainEnergy();
        }

        wasOnGround = onGround();
    }

    @Override
    protected void readAdditionalSaveData(final CompoundTag tag) {
        if (tag.contains("Battery")) {
            battery = ItemStack.of(tag.getCompound("Battery"));
            chargeTicks = tag.getInt("ChargeTicks");
            entityData.set(DATA_HAS_BATTERY, true);
            entityData.set(DATA_CHARGE_TICKS, chargeTicks);
        }
        startupTicks = tag.getInt("StartupTicks");
        final boolean startupDone = tag.getBoolean("StartupDone");
        entityData.set(DATA_STARTUP_DONE, startupDone);
        if (startupDone) {
            startupTicks = STARTUP_DURATION_TICKS;
        }
    }

    @Override
    protected void addAdditionalSaveData(final CompoundTag tag) {
        if (hasBattery()) {
            tag.put("Battery", battery.save(new CompoundTag()));
            tag.putInt("ChargeTicks", chargeTicks);
        }
        tag.putInt("StartupTicks", startupTicks);
        tag.putBoolean("StartupDone", entityData.get(DATA_STARTUP_DONE));
    }

    @Override
    public boolean hurt(final DamageSource source, final float amount) {
        if (level().isClientSide || !isAlive()) return false;
        dropContents();
        discard();
        return true;
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean isAttackable() { return true; }

    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand) {
        final ItemStack heldItem = player.getItemInHand(hand);
        if (!hasBattery() && heldItem.getItem() == FullfudRegistries.REB_BATTERY_ITEM.get()) {
            if (!level().isClientSide) {
                insertBattery(heldItem, player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (player.isCrouching() && hasBattery() && heldItem.isEmpty()) {
            if (!level().isClientSide) {
                final ItemStack extracted = removeBattery();
                if (!extracted.isEmpty() && !player.addItem(extracted)) {
                    spawnAtLocation(extracted, 0.25F);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        return InteractionResult.PASS;
    }

    public boolean hasBattery() { return entityData.get(DATA_HAS_BATTERY); }
    public int getChargeTicks()  { return entityData.get(DATA_CHARGE_TICKS); }

    private void insertBattery(final ItemStack stack, final Player player) {
        final ItemStack single = stack.copy();
        single.setCount(1);
        chargeTicks = RebBatteryItem.getChargeTicks(single);
        RebBatteryItem.setChargeTicks(single, chargeTicks);
        battery = single;
        syncBatteryState(true, chargeTicks);
        energyTickCounter = 0;

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    private ItemStack removeBattery() {
        if (!hasBattery()) return ItemStack.EMPTY;
        final ItemStack result = battery.copy();
        RebBatteryItem.setChargeTicks(result, chargeTicks);
        clearBattery();
        return result;
    }

    private void clearBattery() {
        battery = ItemStack.EMPTY;
        chargeTicks = 0;
        syncBatteryState(false, 0);
        energyTickCounter = 0;
    }

    private void dropContents() {
        if (level().isClientSide) return;
        spawnAtLocation(new ItemStack(FullfudRegistries.REB_EMITTER_ITEM.get()));
        if (hasBattery()) {
            final ItemStack dropBattery = removeBattery();
            if (!dropBattery.isEmpty()) spawnAtLocation(dropBattery);
        }
    }

    private void drainEnergy() {
        if (!hasBattery()) return;
        energyTickCounter++;
        if (energyTickCounter < 20) return;
        energyTickCounter = 0;
        setChargeTicks(Math.max(0, chargeTicks - 20));
        if (chargeTicks <= 0) {
            final ItemStack discharged = removeBattery();
            if (!discharged.isEmpty()) spawnAtLocation(discharged);
        }
    }

    private void setChargeTicks(final int value) {
        chargeTicks = value;
        entityData.set(DATA_CHARGE_TICKS, chargeTicks);
    }

    private void syncBatteryState(final boolean hasBattery, final int charge) {
        entityData.set(DATA_HAS_BATTERY, hasBattery);
        entityData.set(DATA_CHARGE_TICKS, charge);
    }

    private void updateStartup() {
        if (entityData.get(DATA_STARTUP_DONE)) return;
        startupTicks++;
        if (startupTicks >= STARTUP_DURATION_TICKS) {
            startupTicks = STARTUP_DURATION_TICKS;
            entityData.set(DATA_STARTUP_DONE, true);
        }
    }

    public boolean hasFinishedStartup() { return entityData.get(DATA_STARTUP_DONE); }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void registerControllers(final AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "reb", 0, state -> {
            if (!hasFinishedStartup()) state.setAndContinue(START_ANIMATION);
            else state.setAndContinue(IDLE_ANIMATION);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
