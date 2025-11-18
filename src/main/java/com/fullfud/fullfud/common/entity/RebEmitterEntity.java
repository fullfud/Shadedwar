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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.nio.ShortBuffer;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

@OnlyIn(value = Dist.CLIENT)
class _ALGuard {
    // Маркер, чтобы сборщики не сносили LWJGL OpenAL импорты под сервер
}

public class RebEmitterEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_HAS_BATTERY = SynchedEntityData.defineId(RebEmitterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_CHARGE_TICKS = SynchedEntityData.defineId(RebEmitterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_STARTUP_DONE = SynchedEntityData.defineId(RebEmitterEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation START_ANIMATION = RawAnimation.begin().then("animation.reb.start", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation IDLE_ANIMATION  = RawAnimation.begin().thenLoop("animation.reb.idle");

    private static final int STARTUP_DURATION_TICKS = 3 * 20;

    // ===== Белый шум: параметры =====
    private static final float NOISE_MAX_VOLUME = 0.20f;  // 20% в упор
    private static final float NOISE_RADIUS     = 15.0f;  // 15 блоков до нуля
    private static final int   SAMPLE_RATE_HZ   = 22050;  // частота дискретизации
    private static final int   BUFFER_SAMPLES   = 2048;   // размер одного буфера
    private static final int   NUM_BUFFERS      = 4;      // кольцевые буферы

    private ItemStack battery = ItemStack.EMPTY;
    private int chargeTicks;
    private boolean fallingFromSupport;
    private boolean wasOnGround;
    private int energyTickCounter;
    private int startupTicks;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    // ===== Клиентские флаги для переходов (без пакетов) =====
    private boolean cPrevActiveCondition;

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
        } else {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> clientTickNoise());
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
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (level() != null && level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientWhiteNoiseManager.stop(this));
        }
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

    // ===============================
    // КЛИЕНТ: процедурный белый шум (OpenAL)
    // ===============================
    private void clientTickNoise() {
        final boolean shouldPlay = hasBattery() && hasFinishedStartup() && chargeTicks > 0 && !this.isRemoved();
        if (shouldPlay) {
            ClientWhiteNoiseManager.ensure(this);
            ClientWhiteNoiseManager.update(this);
        } else {
            ClientWhiteNoiseManager.stop(this);
        }
        cPrevActiveCondition = shouldPlay;
    }

    // ===============================
    // ВНУТРЕННИЙ КЛИЕНТСКИЙ МЕНЕДЖЕР ШУМА
    // ===============================
    @OnlyIn(Dist.CLIENT)
    private static final class ClientWhiteNoiseManager {
        // Один поток на сущность (weak для автоочистки)
        private static final Map<RebEmitterEntity, WhiteNoiseALStream> STREAMS = new WeakHashMap<>();

        static void ensure(RebEmitterEntity e) {
            STREAMS.computeIfAbsent(e, k -> new WhiteNoiseALStream(e));
        }

        static void update(RebEmitterEntity e) {
            final WhiteNoiseALStream s = STREAMS.get(e);
            if (s != null) s.tick();
        }

        static void stop(RebEmitterEntity e) {
            final WhiteNoiseALStream s = STREAMS.remove(e);
            if (s != null) s.stopAndDispose();
        }
    }

    // ===============================
    // ПРОЦЕДУРНЫЙ СТРИМИНГ БЕЛОГО ШУМА В OPENAL
    // ===============================
    @OnlyIn(Dist.CLIENT)
    private static final class WhiteNoiseALStream {
        private final RebEmitterEntity emitter;

        private int sourceId = 0;
        private final int[] buffers = new int[NUM_BUFFERS];
        private boolean disposed = false;

        // переиспользуемый буфер для данных
        private ShortBuffer scratch;

        WhiteNoiseALStream(RebEmitterEntity emitter) {
            this.emitter = emitter;
            initAL();
        }

        private void initAL() {
            try {
                // Инициализация — используем контекст OpenAL, созданный самим Minecraft.
                sourceId = org.lwjgl.openal.AL10.alGenSources();
                org.lwjgl.openal.AL10.alSourcef(sourceId, org.lwjgl.openal.AL10.AL_PITCH, 1.0f);
                org.lwjgl.openal.AL10.alSourcef(sourceId, org.lwjgl.openal.AL10.AL_GAIN, 0.0f);
                org.lwjgl.openal.AL10.alSource3f(sourceId, org.lwjgl.openal.AL10.AL_POSITION, 0, 0, 0);
                org.lwjgl.openal.AL10.alSource3f(sourceId, org.lwjgl.openal.AL10.AL_VELOCITY, 0, 0, 0);
                org.lwjgl.openal.AL10.alSourcei(sourceId, org.lwjgl.openal.AL10.AL_LOOPING, org.lwjgl.openal.AL10.AL_FALSE);

                // Буферы
                for (int i = 0; i < NUM_BUFFERS; i++) buffers[i] = org.lwjgl.openal.AL10.alGenBuffers();

                // Scratch-буфер
                scratch = org.lwjgl.system.MemoryUtil.memAllocShort(BUFFER_SAMPLES);

                // Предзаполняем
                for (int i = 0; i < NUM_BUFFERS; i++) {
                    fillWhiteNoisePCM(scratch, BUFFER_SAMPLES);
                    org.lwjgl.openal.AL10.alBufferData(buffers[i], org.lwjgl.openal.AL10.AL_FORMAT_MONO16, scratch, SAMPLE_RATE_HZ);
                    org.lwjgl.openal.AL10.alSourceQueueBuffers(sourceId, buffers[i]);
                }

                org.lwjgl.openal.AL10.alSourcePlay(sourceId);
            } catch (Throwable t) {
                // На случай если у окружения нет OpenAL
                disposeNative();
            }
        }

        void tick() {
            if (disposed || emitter.isRemoved() || emitter.level() == null || !emitter.level().isClientSide) {
                stopAndDispose();
                return;
            }

            // позиция источника: к эмиттеру
            org.lwjgl.openal.AL10.alSource3f(sourceId, org.lwjgl.openal.AL10.AL_POSITION, (float) emitter.getX(), (float) emitter.getY(), (float) emitter.getZ());

            // дистанция до игрока
            final Player p = net.minecraft.client.Minecraft.getInstance().player;
            if (p == null) { stopAndDispose(); return; }
            final double dx = p.getX() - emitter.getX();
            final double dy = p.getEyeY() - (emitter.getY() + 0.5);
            final double dz = p.getZ() - emitter.getZ();
            final double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);

            // линейная огибающая громкости
            float gain = 0.0f;
            if (dist < NOISE_RADIUS) {
                gain = NOISE_MAX_VOLUME * (1.0f - (float)(dist / NOISE_RADIUS));
            }
            org.lwjgl.openal.AL10.alSourcef(sourceId, org.lwjgl.openal.AL10.AL_GAIN, gain);

            // Обновление очереди буферов: докидываем новый шум в обработанные
            int processed = org.lwjgl.openal.AL10.alGetSourcei(sourceId, org.lwjgl.openal.AL10.AL_BUFFERS_PROCESSED);
            while (processed-- > 0) {
                int buf = org.lwjgl.openal.AL10.alSourceUnqueueBuffers(sourceId);
                if (buf == org.lwjgl.openal.AL10.AL_INVALID_VALUE) break;
                fillWhiteNoisePCM(scratch, BUFFER_SAMPLES);
                org.lwjgl.openal.AL10.alBufferData(buf, org.lwjgl.openal.AL10.AL_FORMAT_MONO16, scratch, SAMPLE_RATE_HZ);
                org.lwjgl.openal.AL10.alSourceQueueBuffers(sourceId, buf);
            }

            // Если по какой-либо причине остановился — перезапустим
            int state = org.lwjgl.openal.AL10.alGetSourcei(sourceId, org.lwjgl.openal.AL10.AL_SOURCE_STATE);
            if (state != org.lwjgl.openal.AL10.AL_PLAYING) {
                org.lwjgl.openal.AL10.alSourcePlay(sourceId);
            }
        }

        void stopAndDispose() {
            if (disposed) return;
            disposed = true;
            try {
                if (sourceId != 0) {
                    org.lwjgl.openal.AL10.alSourceStop(sourceId);
                    // снять всё из очереди
                    int queued = org.lwjgl.openal.AL10.alGetSourcei(sourceId, org.lwjgl.openal.AL10.AL_BUFFERS_QUEUED);
                    while (queued-- > 0) {
                        org.lwjgl.openal.AL10.alSourceUnqueueBuffers(sourceId);
                    }
                }
                disposeNative();
            } catch (Throwable ignored) {}
        }

        private void disposeNative() {
            try {
                for (int i = 0; i < NUM_BUFFERS; i++) {
                    if (buffers[i] != 0) org.lwjgl.openal.AL10.alDeleteBuffers(buffers[i]);
                }
                if (sourceId != 0) org.lwjgl.openal.AL10.alDeleteSources(sourceId);
                if (scratch != null) {
                    org.lwjgl.system.MemoryUtil.memFree(scratch);
                    scratch = null;
                }
            } catch (Throwable ignored) {}
        }

        /**
         * Заполняет ShortBuffer белым шумом mono16 [-32768..32767]
         */
        private void fillWhiteNoisePCM(ShortBuffer out, int samples) {
            out.clear();
            final ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = 0; i < samples; i++) {
                // Равномерный шум [-1,1) -> short
                float f = rnd.nextFloat() * 2f - 1f;
                short s = (short) (f * 32767);
                out.put(s);
            }
            out.flip();
        }
    }
}
