package com.fullfud.fullfud.common.entity;

import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.PlayerDecoyManager;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.buffer.Unpooled;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class PlayerDecoyEntity extends LivingEntity {
    private static final EntityDataAccessor<String> OWNER_UUID = SynchedEntityData.defineId(PlayerDecoyEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(PlayerDecoyEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> PLAYER_MODEL_PARTS = SynchedEntityData.defineId(PlayerDecoyEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> IS_CROUCHING = SynchedEntityData.defineId(PlayerDecoyEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private GameProfile cachedProfile;
    @Nullable
    private UUID ownerUUID;
    private final NonNullList<ItemStack> armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
    private final NonNullList<ItemStack> handItems = NonNullList.withSize(2, ItemStack.EMPTY);
    private float storedHealth = 20.0F;
    private int syncCooldown;
    private boolean profileSent;

    public PlayerDecoyEntity(final EntityType<? extends PlayerDecoyEntity> type, final Level level) {
        super(type, level);
        this.setNoGravity(false);
        this.noPhysics = false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.0D)
            .add(Attributes.ATTACK_DAMAGE, 1.0D)
            .add(Attributes.ATTACK_SPEED, 0.0D)
            .add(Attributes.ATTACK_KNOCKBACK, 0.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER_UUID, "");
        this.entityData.define(OWNER_NAME, "Steve");
        this.entityData.define(PLAYER_MODEL_PARTS, (byte) 127);
        this.entityData.define(IS_CROUCHING, false);
    }

    public void initFromPlayer(final ServerPlayer player) {
        this.ownerUUID = player.getUUID();
        this.entityData.set(OWNER_UUID, player.getStringUUID());
        this.entityData.set(OWNER_NAME, player.getName().getString());
        this.cachedProfile = copyGameProfile(player.getGameProfile(), createDecoyProfileId(player.getUUID()));
        copyEquipment(player);
        copyPlayerState(player);
        syncHealthFromOwner(player.getHealth());
        this.entityData.set(PLAYER_MODEL_PARTS, getPlayerModelCustomisation(player));
    }

    private static UUID createDecoyProfileId(final UUID ownerId) {
        return UUID.nameUUIDFromBytes(("wrbdrones:decoy:" + ownerId).getBytes(StandardCharsets.UTF_8));
    }

    private static GameProfile copyGameProfile(final GameProfile original, final UUID decoyProfileId) {
        final GameProfile copy = new GameProfile(decoyProfileId, original.getName());
        final Collection<Property> textures = original.getProperties().get("textures");
        for (final Property property : textures) {
            copy.getProperties().put("textures", property);
        }
        return copy;
    }

    public void copyEquipment(final Player player) {
        for (int i = 0; i < 4; ++i) {
            final EquipmentSlot slot = EquipmentSlot.values()[i + 2];
            this.armorItems.set(i, player.getItemBySlot(slot).copy());
        }
        this.handItems.set(0, player.getMainHandItem().copy());
        this.handItems.set(1, player.getOffhandItem().copy());
    }

    public void copyPlayerState(final Player player) {
        this.setPos(player.getX(), player.getY(), player.getZ());
        this.setYRot(player.getYRot());
        this.setXRot(player.getXRot());
        this.yHeadRot = player.yHeadRot;
        this.yBodyRot = player.yBodyRot;
        final boolean crouching = player.isCrouching();
        this.entityData.set(IS_CROUCHING, crouching);
        this.setPose(crouching ? Pose.CROUCHING : Pose.STANDING);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }

        final ServerLevel serverLevel = (ServerLevel) this.level();
        final ServerPlayer owner = getOwnerPlayer(serverLevel);
        if (owner == null || owner.isRemoved()) {
            this.discard();
            return;
        }
        if (!isOwnerRemoteActive(owner)) {
            this.discard();
            return;
        }

        if (this.syncCooldown > 0) {
            --this.syncCooldown;
        }
        if (this.syncCooldown == 0) {
            copyEquipment(owner);
            this.syncCooldown = 20;
        }

        if (!this.profileSent && serverLevel.getGameTime() % 5L == 0L) {
            broadcastPlayerInfo(serverLevel);
            this.profileSent = true;
        }
    }

    private static byte getPlayerModelCustomisation(final Player player) {
        try {
            final Field field = Player.class.getDeclaredField("DATA_PLAYER_MODE_CUSTOMISATION");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            final EntityDataAccessor<Byte> accessor = (EntityDataAccessor<Byte>) field.get(null);
            return player.getEntityData().get(accessor);
        } catch (final Exception ignored) {
            return (byte) 127;
        }
    }

    private void broadcastPlayerInfo(final ServerLevel level) {
        if (this.cachedProfile == null) {
            return;
        }

        for (final ServerPlayer player : level.players()) {
            try {
                final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeEnumSet(EnumSet.of(Action.ADD_PLAYER), Action.class);
                buf.writeVarInt(1);
                buf.writeUUID(this.cachedProfile.getId());
                buf.writeUtf(this.cachedProfile.getName(), 16);
                buf.writeGameProfileProperties(this.cachedProfile.getProperties());
                player.connection.send(new ClientboundPlayerInfoUpdatePacket(buf));
            } catch (final Exception ignored) {
            }
        }
    }

    @Override
    public boolean hurt(final DamageSource source, final float amount) {
        if (this.level().isClientSide()) {
            return false;
        }
        if (this.isInvulnerableTo(source)) {
            return false;
        }

        final ServerPlayer owner = getOwnerPlayer((ServerLevel) this.level());
        if (owner != null && !owner.isDeadOrDying()) {
            DamageSource forwardedSource = source;
            if (!(source.getDirectEntity() instanceof PlayerDecoyEntity)) {
                forwardedSource = new DamageSource(source.typeHolder(), source.getEntity(), this, source.getSourcePosition());
            }
            final boolean damaged = owner.hurt(forwardedSource, amount);
            if (damaged) {
                syncHealthFromOwner(owner.getHealth());
                this.hurtMarked = true;
                this.hurtDuration = 10;
                this.invulnerableTime = 10;
            }
            if (owner.isDeadOrDying()) {
                this.discard();
            }
            return damaged;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(final DamageSource source) {
        if (!this.level().isClientSide()) {
            final ServerPlayer owner = getOwnerPlayer((ServerLevel) this.level());
            if (owner != null && !owner.isDeadOrDying()) {
                owner.hurt(source, Float.MAX_VALUE);
            }
        }
        super.die(source);
    }

    @Nullable
    private ServerPlayer getOwnerPlayer(final ServerLevel level) {
        if (this.ownerUUID == null) {
            final String uuidStr = this.entityData.get(OWNER_UUID);
            if (uuidStr.isEmpty()) {
                return null;
            }
            try {
                this.ownerUUID = UUID.fromString(uuidStr);
            } catch (final IllegalArgumentException ignored) {
                return null;
            }
        }
        final Player player = level.getPlayerByUUID(this.ownerUUID);
        return player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    @Override
    public @NotNull Iterable<ItemStack> getArmorSlots() {
        return this.armorItems;
    }

    @Override
    public @NotNull ItemStack getItemBySlot(final EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> this.handItems.get(slot.getIndex());
            case ARMOR -> this.armorItems.get(slot.getIndex());
        };
    }

    @Override
    public void setItemSlot(final EquipmentSlot slot, final @NotNull ItemStack stack) {
        this.verifyEquippedItem(stack);
        switch (slot.getType()) {
            case HAND -> this.handItems.set(slot.getIndex(), stack);
            case ARMOR -> this.armorItems.set(slot.getIndex(), stack);
        }
    }

    @Override
    public @NotNull HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void addAdditionalSaveData(final @NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("OwnerUUID", this.entityData.get(OWNER_UUID));
        tag.putString("OwnerName", this.entityData.get(OWNER_NAME));
        tag.putByte("ModelParts", this.entityData.get(PLAYER_MODEL_PARTS));
        tag.putFloat("StoredHealth", this.storedHealth);

        final ListTag armorTag = new ListTag();
        for (final ItemStack stack : this.armorItems) {
            armorTag.add(stack.save(new CompoundTag()));
        }
        tag.put("ArmorItems", armorTag);

        final ListTag handTag = new ListTag();
        for (final ItemStack stack : this.handItems) {
            handTag.add(stack.save(new CompoundTag()));
        }
        tag.put("HandItems", handTag);

        if (this.cachedProfile != null) {
            final CompoundTag profileTag = new CompoundTag();
            profileTag.putString("Id", this.cachedProfile.getId().toString());
            profileTag.putString("Name", this.cachedProfile.getName());
            final Iterator<Property> iterator = this.cachedProfile.getProperties().get("textures").iterator();
            if (iterator.hasNext()) {
                final Property texture = iterator.next();
                profileTag.putString("TextureValue", texture.getValue());
                if (texture.getSignature() != null) {
                    profileTag.putString("TextureSignature", texture.getSignature());
                }
            }
            tag.put("GameProfile", profileTag);
        }
    }

    @Override
    public void readAdditionalSaveData(final @NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("OwnerUUID")) {
            final String uuid = tag.getString("OwnerUUID");
            this.entityData.set(OWNER_UUID, uuid);
            if (!uuid.isEmpty()) {
                try {
                    this.ownerUUID = UUID.fromString(uuid);
                } catch (final IllegalArgumentException ignored) {
                    this.ownerUUID = null;
                }
            }
        }
        if (tag.contains("OwnerName")) {
            this.entityData.set(OWNER_NAME, tag.getString("OwnerName"));
        }
        if (tag.contains("ModelParts")) {
            this.entityData.set(PLAYER_MODEL_PARTS, tag.getByte("ModelParts"));
        }
        if (tag.contains("StoredHealth")) {
            syncHealthFromOwner(tag.getFloat("StoredHealth"));
        }

        if (tag.contains("ArmorItems")) {
            final ListTag armorTag = tag.getList("ArmorItems", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(armorTag.size(), 4); ++i) {
                this.armorItems.set(i, ItemStack.of(armorTag.getCompound(i)));
            }
        }
        if (tag.contains("HandItems")) {
            final ListTag handTag = tag.getList("HandItems", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(handTag.size(), 2); ++i) {
                this.handItems.set(i, ItemStack.of(handTag.getCompound(i)));
            }
        }
        if (tag.contains("GameProfile")) {
            final CompoundTag profileTag = tag.getCompound("GameProfile");
            final UUID profileId = UUID.fromString(profileTag.getString("Id"));
            final String profileName = profileTag.getString("Name");
            this.cachedProfile = new GameProfile(profileId, profileName);
            if (profileTag.contains("TextureValue")) {
                final String value = profileTag.getString("TextureValue");
                final String signature = profileTag.contains("TextureSignature") ? profileTag.getString("TextureSignature") : null;
                this.cachedProfile.getProperties().put("textures", new Property("textures", value, signature));
            }
        }
    }

    public GameProfile getGameProfile() {
        if (this.cachedProfile != null) {
            return this.cachedProfile;
        }
        final String uuid = this.entityData.get(OWNER_UUID);
        final String name = this.entityData.get(OWNER_NAME);
        if (!uuid.isEmpty()) {
            try {
                return new GameProfile(createDecoyProfileId(UUID.fromString(uuid)), name);
            } catch (final IllegalArgumentException ignored) {
            }
        }
        return new GameProfile(UUID.randomUUID(), name);
    }

    public byte getPlayerModelParts() {
        return this.entityData.get(PLAYER_MODEL_PARTS);
    }

    public boolean isDecoyCrouching() {
        return this.entityData.get(IS_CROUCHING);
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public void syncHealthFromOwner(final float health) {
        this.setHealth(health);
        this.storedHealth = health;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(final Entity attacker) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(final DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? false : false;
    }

    @Override
    protected void doPush(final Entity entity) {
    }

    @Override
    public void push(final Entity entity) {
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void remove(final @NotNull RemovalReason reason) {
        PlayerDecoyManager.unregisterDecoy(this.ownerUUID, this.getUUID());
        if (!this.level().isClientSide() && this.cachedProfile != null) {
            final ServerLevel level = (ServerLevel) this.level();
            for (final ServerPlayer player : level.players()) {
                player.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(this.cachedProfile.getId())));
            }
        }
        super.remove(reason);
    }

    private static boolean isOwnerRemoteActive(final ServerPlayer owner) {
        if (owner == null) {
            return false;
        }
        final CompoundTag root = owner.getPersistentData();
        if (root.contains(FpvDroneEntity.PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)
            && FpvDroneEntity.isRemoteControlActive(owner.getServer(), owner.getUUID(), root.getCompound(FpvDroneEntity.PLAYER_REMOTE_TAG))) {
            return true;
        }
        return root.contains(ShahedDroneEntity.PLAYER_REMOTE_TAG, Tag.TAG_COMPOUND)
            && ShahedDroneEntity.isRemoteControlActive(owner.getServer(), owner.getUUID(), root.getCompound(ShahedDroneEntity.PLAYER_REMOTE_TAG));
    }
}
