package com.fullfud.fullfud.common.entity;

import com.fullfud.fullfud.common.item.MonitorItem;
import com.fullfud.fullfud.common.item.ShahedDroneItem;
import com.fullfud.fullfud.core.FullfudRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;



public class ShahedLauncherEntity extends Entity implements GeoEntity {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int storedDroneId = -1;
    private UUID storedDroneUuid;

    public ShahedLauncherEntity(final EntityType<? extends ShahedLauncherEntity> type, final Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData() { }

    @Override
    protected void readAdditionalSaveData(final CompoundTag tag) {
        if (tag.hasUUID("StoredDrone")) {
            storedDroneUuid = tag.getUUID("StoredDrone");
        }
    }

    @Override
    protected void addAdditionalSaveData(final CompoundTag tag) {
        if (storedDroneUuid != null) {
            tag.putUUID("StoredDrone", storedDroneUuid);
        }
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        resolveStoredDrone();
    }

    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand) {
        final ItemStack held = player.getItemInHand(hand);
        if (!(level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        if (held.getItem() instanceof ShahedDroneItem droneItem) {
            if (hasDrone()) {
                return InteractionResult.FAIL;
            }
            final ShahedDroneEntity drone = FullfudRegistries.SHAHED_ENTITY.get().create(serverLevel);
            if (drone == null) {
                return InteractionResult.PASS;
            }
            drone.setColor(droneItem.getColor());
            drone.setSpeedScale(droneItem.getSpeedScale());
            drone.mountLauncher(this);
            serverLevel.addFreshEntity(drone);
            setStoredDrone(drone);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (held.getItem() instanceof MonitorItem) {
            if (!hasDrone()) {
                return InteractionResult.FAIL;
            }
            launchStoredDrone(player, held);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isShiftKeyDown() && held.isEmpty() && hasDrone()) {
            ejectStoredDrone(player);
            return InteractionResult.sidedSuccess(level().isClientSide);
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
        if (level().isClientSide || !isAlive()) {
            return false;
        }
        dropStoredDroneAsItem();
        dropSelf();
        discard();
        return true;
    }

    @Override
    public void remove(final RemovalReason reason) {
        dropStoredDroneAsItem();
        super.remove(reason);
    }

    private boolean hasDrone() {
        return getStoredDrone() != null;
    }

    private void resolveStoredDrone() {
        if (storedDroneId > 0) {
            return;
        }
        if (storedDroneUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        final Entity entity = serverLevel.getEntity(storedDroneUuid);
        if (entity instanceof ShahedDroneEntity drone) {
            storedDroneId = drone.getId();
        } else {
            storedDroneUuid = null;
        }
    }

    private ShahedDroneEntity getStoredDrone() {
        if (storedDroneId > 0) {
            final Entity entity = level().getEntity(storedDroneId);
            if (entity instanceof ShahedDroneEntity drone && drone.isOnLauncher() && drone.getLauncherUuid() != null && drone.getLauncherUuid().equals(getUUID())) {
                return drone;
            }
            storedDroneId = -1;
        }
        if (storedDroneUuid != null && level() instanceof ServerLevel serverLevel) {
            final Entity entity = serverLevel.getEntity(storedDroneUuid);
            if (entity instanceof ShahedDroneEntity drone && drone.isOnLauncher() && drone.getLauncherUuid() != null && drone.getLauncherUuid().equals(getUUID())) {
                storedDroneId = drone.getId();
                return drone;
            }
            storedDroneUuid = null;
        }
        return null;
    }

    private void setStoredDrone(final ShahedDroneEntity drone) {
        storedDroneId = drone.getId();
        storedDroneUuid = drone.getUUID();
    }

    private void clearStoredDrone() {
        storedDroneId = -1;
        storedDroneUuid = null;
    }

    private void launchStoredDrone(final Player player, final ItemStack monitorStack) {
        final ShahedDroneEntity drone = getStoredDrone();
        if (drone == null) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (!drone.assignOwner(serverPlayer)) {
                player.displayClientMessage(Component.translatable("message.fullfud.monitor.in_use"), true);
                return;
            }
        }
        drone.launchFromLauncher(this);
        MonitorItem.setLinkedDrone(monitorStack, drone.getUUID());
        player.displayClientMessage(Component.translatable("message.fullfud.monitor.linked"), true);
        clearStoredDrone();
    }

    private void ejectStoredDrone(final Player player) {
        final ShahedDroneEntity drone = getStoredDrone();
        if (drone == null) {
            return;
        }
        final ItemStack stack = drone.createItemStack();
        if (!player.addItem(stack)) {
            spawnAtLocation(stack);
        }
        drone.discard();
        clearStoredDrone();
    }

    private void dropStoredDroneAsItem() {
        final ShahedDroneEntity drone = getStoredDrone();
        if (drone == null) {
            return;
        }
        final ItemStack stack = drone.createItemStack();
        spawnAtLocation(stack);
        drone.discard();
        clearStoredDrone();
    }

    private void dropSelf() {
        spawnAtLocation(new ItemStack(FullfudRegistries.SHAHED_LAUNCHER_ITEM.get()));
    }

    @Override
    protected void checkFallDamage(final double y, final boolean onGround, final BlockState state, final BlockPos pos) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void registerControllers(final AnimatableManager.ControllerRegistrar controllers) { }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}

