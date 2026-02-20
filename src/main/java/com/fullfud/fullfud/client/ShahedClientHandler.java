package com.fullfud.fullfud.client;

import com.fullfud.fullfud.client.render.RebEmitterRenderer;
import com.fullfud.fullfud.client.render.ShahedDroneRenderer;
import com.fullfud.fullfud.client.render.ShahedLauncherRenderer;
import com.fullfud.fullfud.client.screen.ShahedMonitorScreen;
import com.fullfud.fullfud.client.sound.ShahedEngineLoopSound;
import com.fullfud.fullfud.common.entity.RebEmitterEntity;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.common.item.MonitorItem;
import com.fullfud.fullfud.common.item.RebBatteryItem;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.network.FullfudNetwork;
import com.fullfud.fullfud.core.network.packet.ShahedControlPacket;
import com.fullfud.fullfud.core.network.packet.ShahedGhostUpdatePacket;
import com.fullfud.fullfud.core.network.packet.ShahedLinkPacket;
import com.fullfud.fullfud.core.network.packet.ShahedStatusPacket;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class ShahedClientHandler {
    private static final KeyMapping POWER_UP = new KeyMapping(
        "key.fullfud.power_up",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_R,
        "key.categories.fullfud"
    );
    private static final KeyMapping POWER_DOWN = new KeyMapping(
        "key.fullfud.power_down",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_F,
        "key.categories.fullfud"
    );

    private static ShahedStatusPacket lastStatus;
    private static long lastStatusTimestamp;
    private static final Map<UUID, EngineAudioController> ENGINE_AUDIO = new HashMap<>();
    private static final Map<UUID, GhostState> GHOST_STATES = new HashMap<>();
    private static final Map<UUID, ShahedDroneEntity> GHOST_ENTITIES = new HashMap<>();
    private static final int GHOST_TTL_TICKS = 60;
    private static final double GHOST_RENDER_RANGE_BLOCKS = 10000.0D;
    private static final int GHOST_RENDER_LIGHT = 0x00F000F0;

    private ShahedClientHandler() {
    }

    public static void registerClientEvents(final IEventBus modEventBus) {
        modEventBus.addListener(ShahedClientHandler::onRegisterRenderers);
        modEventBus.addListener(ShahedClientHandler::onRegisterKeyMappings);
    }

    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(FullfudRegistries.SHAHED_MONITOR_MENU.get(), ShahedMonitorScreen::new);
            MinecraftForge.EVENT_BUS.addListener(ShahedClientHandler::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(ShahedClientHandler::onRenderLevelStage);
            MinecraftForge.EVENT_BUS.addListener(ShahedClientHandler::onRenderGui);
            MinecraftForge.EVENT_BUS.addListener(ShahedClientHandler::onComputeCameraAngles);
        });
    }

    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FullfudRegistries.SHAHED_ENTITY.get(), ShahedDroneRenderer::new);
        event.registerEntityRenderer(FullfudRegistries.SHAHED_LAUNCHER_ENTITY.get(), ShahedLauncherRenderer::new);
        event.registerEntityRenderer(FullfudRegistries.REB_EMITTER_ENTITY.get(), RebEmitterRenderer::new);
    }

    public static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(POWER_UP);
        event.register(POWER_DOWN);
    }

    public static void handleStatusPacket(final ShahedStatusPacket packet) {
        lastStatus = packet;
        lastStatusTimestamp = System.currentTimeMillis();
    }

    public static void handleGhostPacket(final ShahedGhostUpdatePacket packet) {
        if (packet == null || packet.droneId() == null) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        final long nowTick = minecraft.level.getGameTime();
        GHOST_STATES.compute(packet.droneId(), (droneId, existing) -> {
            if (existing == null) {
                return GhostState.create(packet, nowTick);
            }
            existing.update(packet, nowTick);
            return existing;
        });
    }

    public static void handleLinkPacket(final ShahedLinkPacket packet) {
        if (packet == null || packet.droneId() == null) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        if (packet.linked()) {
            linkMonitorClientSide(minecraft.player, packet.droneId());
        } else {
            unlinkMonitorsClientSide(minecraft.player, packet.droneId());
        }
    }

    public static ShahedStatusPacket getLastStatus() {
        return lastStatus;
    }

    public static KeyMapping getPowerUpKey() {
        return POWER_UP;
    }

    public static KeyMapping getPowerDownKey() {
        return POWER_DOWN;
    }

    public static boolean hasFreshStatus(final int timeoutMs) {
        if (lastStatus == null) {
            return false;
        }
        return System.currentTimeMillis() - lastStatusTimestamp <= timeoutMs;
    }

    public static void sendControlPacket(final UUID droneId, final float forward, final float strafe, final float vertical, final float thrustDelta) {
        if (droneId == null) {
            return;
        }
        FullfudNetwork.getChannel().sendToServer(new ShahedControlPacket(droneId, forward, strafe, vertical, thrustDelta));
    }

    private static void onComputeCameraAngles(final ViewportEvent.ComputeCameraAngles event) {
        if (event.getCamera().getEntity() instanceof ShahedDroneEntity drone) {
            event.setRoll(drone.getVisualRoll((float) event.getPartialTick()));
            event.setPitch(drone.getVisualPitch((float) event.getPartialTick()));
        }
    }

    private static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (minecraft.level == null || minecraft.isPaused()) {
            ENGINE_AUDIO.values().forEach(EngineAudioController::stop);
            ENGINE_AUDIO.clear();
            if (minecraft.level == null) {
                clearGhostState();
            }
            return;
        }
        ENGINE_AUDIO.values().forEach(controller -> controller.seen = false);
        for (final var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof ShahedDroneEntity drone) {
                ENGINE_AUDIO.computeIfAbsent(drone.getUUID(), id -> new EngineAudioController(drone))
                    .updateFromEntity();
            }
        }
        ENGINE_AUDIO.entrySet().removeIf(entry -> entry.getValue().shouldRemove());
        cleanupGhostState(minecraft);
    }

    private static final class EngineAudioController {
        private static final float ACTIVE_THRESHOLD = 0.02F;
        private final ShahedDroneEntity drone;
        private ShahedEngineLoopSound loopSound;
        private float lastThrust;

        private boolean seen;

        private EngineAudioController(final ShahedDroneEntity drone) {
            this.drone = drone;
        }

        private boolean isInvalid() {
            return drone.isRemoved() || !drone.isAlive();
        }

        private void stop() {
            if (loopSound != null) {
                loopSound.stopSound();
                loopSound = null;
            }
        }

        private void updateFromEntity() {
            seen = true;
            update(drone.getThrust());
        }

        private void update(final float thrust) {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.level == null) {
                return;
            }
            if (isInvalid()) {
                stop();
                return;
            }
            final float clamped = Mth.clamp(thrust, 0.0F, 1.0F);
            if (clamped > ACTIVE_THRESHOLD) {
                if (lastThrust <= ACTIVE_THRESHOLD) {
                    playOneShot(FullfudRegistries.SHAHED_ENGINE_START.get(), clamped);
                }
                ensureLoop();
                if (loopSound != null) {
                    loopSound.setEngineMix(clamped);
                }
            } else if (lastThrust > ACTIVE_THRESHOLD) {
                stop();
                playOneShot(FullfudRegistries.SHAHED_ENGINE_END.get(), lastThrust);
            }
            lastThrust = clamped;
            if (clamped <= ACTIVE_THRESHOLD && loopSound != null) {
                loopSound.setEngineMix(0.0F);
            }
        }

        private boolean shouldRemove() {
            if (!seen || isInvalid()) {
                stop();
                return true;
            }
            return false;
        }

        private void ensureLoop() {
            final Minecraft minecraft = Minecraft.getInstance();
            if (loopSound != null && !loopSound.isStopped()) {
                return;
            }
            loopSound = new ShahedEngineLoopSound(drone);
            loopSound.setEngineMix(Math.max(lastThrust, ACTIVE_THRESHOLD));
            minecraft.getSoundManager().play(loopSound);
        }

        private void playOneShot(final SoundEvent event, final float thrust) {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.level == null) {
                return;
            }
            final float volume = 0.25F + 0.75F * thrust;
            final float pitch = 0.9F + 0.2F * thrust;
            minecraft.level.playLocalSound(drone.getX(), drone.getY(), drone.getZ(), event, SoundSource.NEUTRAL, volume, pitch, false);
        }
    }

    private static void clearGhostState() {
        GHOST_STATES.clear();
        GHOST_ENTITIES.clear();
    }

    private static void cleanupGhostState(final Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            clearGhostState();
            return;
        }
        final long nowTick = minecraft.level.getGameTime();
        GHOST_STATES.entrySet().removeIf(entry -> nowTick - entry.getValue().lastUpdateTick > GHOST_TTL_TICKS);
        GHOST_ENTITIES.entrySet().removeIf(entry ->
            !GHOST_STATES.containsKey(entry.getKey()) || entry.getValue() == null || entry.getValue().level() != minecraft.level
        );
    }

    private static void renderGhostShaheds(final RenderLevelStageEvent event, final Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null || GHOST_STATES.isEmpty()) {
            return;
        }

        final double maxRangeSqr = GHOST_RENDER_RANGE_BLOCKS * GHOST_RENDER_RANGE_BLOCKS;
        final var cameraPos = event.getCamera().getPosition();
        final float partialTick = event.getPartialTick();
        final PoseStack poseStack = event.getPoseStack();
        final EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        final MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        final Set<UUID> loadedShaheds = new HashSet<>();
        for (final var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof ShahedDroneEntity drone) {
                loadedShaheds.add(drone.getUUID());
            }
        }
        boolean renderedAny = false;

        for (final Map.Entry<UUID, GhostState> entry : GHOST_STATES.entrySet()) {
            final UUID droneId = entry.getKey();
            final GhostState state = entry.getValue();

            if (loadedShaheds.contains(droneId)) {
                continue;
            }

            final double x = state.x(partialTick);
            final double y = state.y(partialTick);
            final double z = state.z(partialTick);

            final double dx = x - cameraPos.x;
            final double dy = y - cameraPos.y;
            final double dz = z - cameraPos.z;
            final double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > maxRangeSqr) {
                continue;
            }

            final ShahedDroneEntity ghost = getOrCreateGhostEntity(minecraft, droneId);
            ghost.applyClientGhostState(
                x,
                y,
                z,
                state.velocityX(partialTick),
                state.velocityY(partialTick),
                state.velocityZ(partialTick),
                state.yaw(partialTick),
                state.pitch(partialTick),
                state.roll(partialTick),
                state.thrust(partialTick),
                state.colorId,
                state.onLauncher
            );

            dispatcher.render(ghost, dx, dy, dz, ghost.getYRot(), partialTick, poseStack, bufferSource, GHOST_RENDER_LIGHT);
            renderedAny = true;
        }

        if (renderedAny) {
            bufferSource.endBatch();
        }
    }

    private static ShahedDroneEntity getOrCreateGhostEntity(final Minecraft minecraft, final UUID droneId) {
        ShahedDroneEntity ghost = GHOST_ENTITIES.get(droneId);
        if (ghost != null && ghost.level() == minecraft.level) {
            return ghost;
        }
        ghost = new ShahedDroneEntity(FullfudRegistries.SHAHED_ENTITY.get(), minecraft.level);
        ghost.setUUID(droneId);
        ghost.noCulling = true;
        GHOST_ENTITIES.put(droneId, ghost);
        return ghost;
    }

    private static final class GhostState {
        private double prevX;
        private double prevY;
        private double prevZ;
        private double x;
        private double y;
        private double z;
        private double prevVelocityX;
        private double prevVelocityY;
        private double prevVelocityZ;
        private double velocityX;
        private double velocityY;
        private double velocityZ;
        private float prevYaw;
        private float prevPitch;
        private float prevRoll;
        private float prevThrust;
        private float yaw;
        private float pitch;
        private float roll;
        private float thrust;
        private int colorId;
        private boolean onLauncher;
        private long lastUpdateTick;

        private static GhostState create(final ShahedGhostUpdatePacket packet, final long nowTick) {
            final GhostState state = new GhostState();
            state.prevX = packet.x();
            state.prevY = packet.y();
            state.prevZ = packet.z();
            state.x = packet.x();
            state.y = packet.y();
            state.z = packet.z();
            state.prevVelocityX = packet.velocityX();
            state.prevVelocityY = packet.velocityY();
            state.prevVelocityZ = packet.velocityZ();
            state.velocityX = packet.velocityX();
            state.velocityY = packet.velocityY();
            state.velocityZ = packet.velocityZ();
            state.prevYaw = packet.yaw();
            state.prevPitch = packet.pitch();
            state.prevRoll = packet.roll();
            state.prevThrust = packet.thrust();
            state.yaw = packet.yaw();
            state.pitch = packet.pitch();
            state.roll = packet.roll();
            state.thrust = packet.thrust();
            state.colorId = packet.colorId();
            state.onLauncher = packet.onLauncher();
            state.lastUpdateTick = nowTick;
            return state;
        }

        private void update(final ShahedGhostUpdatePacket packet, final long nowTick) {
            prevX = x;
            prevY = y;
            prevZ = z;
            prevVelocityX = velocityX;
            prevVelocityY = velocityY;
            prevVelocityZ = velocityZ;
            prevYaw = yaw;
            prevPitch = pitch;
            prevRoll = roll;
            prevThrust = thrust;
            x = packet.x();
            y = packet.y();
            z = packet.z();
            velocityX = packet.velocityX();
            velocityY = packet.velocityY();
            velocityZ = packet.velocityZ();
            yaw = packet.yaw();
            pitch = packet.pitch();
            roll = packet.roll();
            thrust = packet.thrust();
            colorId = packet.colorId();
            onLauncher = packet.onLauncher();
            lastUpdateTick = nowTick;
        }

        private double x(final float partialTick) {
            return Mth.lerp(partialTick, prevX, x);
        }

        private double y(final float partialTick) {
            return Mth.lerp(partialTick, prevY, y);
        }

        private double z(final float partialTick) {
            return Mth.lerp(partialTick, prevZ, z);
        }

        private double velocityX(final float partialTick) {
            return Mth.lerp(partialTick, prevVelocityX, velocityX);
        }

        private double velocityY(final float partialTick) {
            return Mth.lerp(partialTick, prevVelocityY, velocityY);
        }

        private double velocityZ(final float partialTick) {
            return Mth.lerp(partialTick, prevVelocityZ, velocityZ);
        }

        private float yaw(final float partialTick) {
            return Mth.rotLerp(partialTick, prevYaw, yaw);
        }

        private float pitch(final float partialTick) {
            return Mth.rotLerp(partialTick, prevPitch, pitch);
        }

        private float roll(final float partialTick) {
            return Mth.rotLerp(partialTick, prevRoll, roll);
        }

        private float thrust(final float partialTick) {
            return Mth.lerp(partialTick, prevThrust, thrust);
        }
    }

    private static void onRenderLevelStage(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        renderGhostShaheds(event, minecraft);
        if (!isHoldingBattery(minecraft.player)) {
            return;
        }
        if (!(minecraft.hitResult instanceof EntityHitResult entityHit)) {
            return;
        }
        if (!(entityHit.getEntity() instanceof RebEmitterEntity emitter) || emitter.isRemoved() || emitter.hasBattery()) {
            return;
        }
        final PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        final var cameraPos = event.getCamera().getPosition();
        poseStack.translate(emitter.getX() - cameraPos.x, emitter.getY() - cameraPos.y, emitter.getZ() - cameraPos.z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        final BufferBuilder builder = Tesselator.getInstance().getBuilder();
        final float half = 0.25F;
        final float y = 0.5F;
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(poseStack.last().pose(), -half, y, -half).color(32, 255, 96, 160).endVertex();
        builder.vertex(poseStack.last().pose(), half, y, -half).color(32, 255, 96, 160).endVertex();
        builder.vertex(poseStack.last().pose(), half, y, half).color(32, 255, 96, 160).endVertex();
        builder.vertex(poseStack.last().pose(), -half, y, half).color(32, 255, 96, 160).endVertex();
        final RenderedBuffer renderedBuffer = builder.end();
        BufferUploader.drawWithShader(renderedBuffer);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void onRenderGui(final RenderGuiEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        if (!(minecraft.hitResult instanceof EntityHitResult entityHit)) {
            return;
        }
        if (!(entityHit.getEntity() instanceof RebEmitterEntity emitter) || emitter.isRemoved()) {
            return;
        }
        final Component statusText = resolveEmitterStatus(emitter);
        final Component chargeText = resolveEmitterCharge(emitter);
        final GuiGraphics graphics = event.getGuiGraphics();
        final Font font = minecraft.font;
        final int padding = 6;
        final int spacing = 2;
        final int width = Math.max(font.width(statusText), font.width(chargeText)) + padding * 2;
        final int height = font.lineHeight * 2 + spacing + padding * 2;
        final int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        final int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        final int x = (screenWidth - width) / 2;
        final int y = screenHeight / 2 + 20;
        graphics.fill(x, y, x + width, y + height, 0x88000000);
        graphics.drawString(font, statusText, x + padding, y + padding, 0xFFFFFFFF, false);
        graphics.drawString(font, chargeText, x + padding, y + padding + font.lineHeight + spacing, 0xFFB0B0B0, false);
    }

    private static boolean isHoldingBattery(final Player player) {
        return player.getMainHandItem().is(FullfudRegistries.REB_BATTERY_ITEM.get()) ||
            player.getOffhandItem().is(FullfudRegistries.REB_BATTERY_ITEM.get());
    }

    private static Component resolveEmitterStatus(final RebEmitterEntity emitter) {
        if (!emitter.hasBattery()) {
            return Component.translatable("status.fullfud.reb.no_battery");
        }
        if (!emitter.hasFinishedStartup()) {
            return Component.translatable("status.fullfud.reb.starting");
        }
        final int chargeTicks = emitter.getChargeTicks();
        if (chargeTicks <= 0) {
            return Component.translatable("status.fullfud.reb.low_power");
        }
        final float percent = chargeTicks / (float) RebBatteryItem.MAX_CHARGE_TICKS;
        if (percent <= 0.05F) {
            return Component.translatable("status.fullfud.reb.low_power");
        }
        return Component.translatable("status.fullfud.reb.working");
    }

    private static Component resolveEmitterCharge(final RebEmitterEntity emitter) {
        final int chargeTicks = emitter.hasBattery() ? emitter.getChargeTicks() : 0;
        final int percent = Mth.clamp(Math.round((chargeTicks / (float) RebBatteryItem.MAX_CHARGE_TICKS) * 100.0F), 0, 100);
        return Component.translatable("status.fullfud.reb.charge", percent);
    }

    private static void linkMonitorClientSide(final Player player, final UUID droneId) {
        if (player == null || droneId == null || player.getInventory() == null) {
            return;
        }
        final ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof MonitorItem) {
            MonitorItem.setLinkedDrone(main, droneId);
            return;
        }
        final ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof MonitorItem) {
            MonitorItem.setLinkedDrone(off, droneId);
            return;
        }
        for (final ItemStack stack : player.getInventory().items) {
            if (!(stack.getItem() instanceof MonitorItem)) {
                continue;
            }
            final Optional<UUID> linked = MonitorItem.getLinkedDrone(stack);
            if (linked.isEmpty() || linked.get().equals(droneId)) {
                MonitorItem.setLinkedDrone(stack, droneId);
                return;
            }
        }
    }

    private static void unlinkMonitorsClientSide(final Player player, final UUID droneId) {
        if (player == null || droneId == null || player.getInventory() == null) {
            return;
        }
        clearLinkIfMatches(player.getMainHandItem(), droneId);
        clearLinkIfMatches(player.getOffhandItem(), droneId);
        for (final ItemStack stack : player.getInventory().items) {
            clearLinkIfMatches(stack, droneId);
        }
    }

    private static void clearLinkIfMatches(final ItemStack stack, final UUID droneId) {
        if (stack == null || !(stack.getItem() instanceof MonitorItem)) {
            return;
        }
        if (MonitorItem.getLinkedDrone(stack).filter(droneId::equals).isPresent()) {
            MonitorItem.clearLinkedDrone(stack);
        }
    }
}
