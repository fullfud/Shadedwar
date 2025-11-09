package com.fullfud.fullfud.client.screen;

import com.fullfud.fullfud.client.ShahedClientHandler;
import com.fullfud.fullfud.common.entity.ShahedDroneEntity;
import com.fullfud.fullfud.common.menu.ShahedMonitorMenu;
import com.fullfud.fullfud.core.network.packet.ShahedStatusPacket;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class ShahedMonitorScreen extends AbstractContainerScreen<ShahedMonitorMenu> {
    private static final int CONTROL_INTERVAL = 1;
    private static final int HUD_MARGIN = 28;
    private final RandomSource noiseRandom = RandomSource.create();

    private int controlTicker;
    private float smoothedNoise;
    private Entity previousCamera;
    private boolean cameraOverridden;
    private boolean hudOverridden;
    private boolean previousHideGui;
    private boolean hasCameraFeed;
    private boolean ascendPressed;
    private boolean descendPressed;
    private boolean strafeLeftPressed;
    private boolean strafeRightPressed;
    private boolean increasePowerPressed;
    private boolean decreasePowerPressed;
    private final SmoothedStatus smoothedStatus = new SmoothedStatus();

    public ShahedMonitorScreen(final ShahedMonitorMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.inventoryLabelX = Integer.MIN_VALUE;
        this.inventoryLabelY = Integer.MIN_VALUE;
    }

    @Override
    protected void init() {
        super.init();
        this.imageWidth = this.width;
        this.imageHeight = this.height;
        ensureCamera();
        hasCameraFeed = false;
        if (minecraft != null) {
            previousHideGui = minecraft.options.hideGui;
            minecraft.options.hideGui = true;
            hudOverridden = true;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ensureCamera();
        if (++controlTicker >= CONTROL_INTERVAL) {
            controlTicker = 0;
            sendControlInput();
        }
    }

    @Override
    public void removed() {
        super.removed();
        resetKeyStates();
        restoreCamera();
    }

    @Override
    public void onClose() {
        super.onClose();
        restoreCamera();
        resetKeyStates();
    }

    @Override
    protected void renderBg(final GuiGraphics graphics, final float partialTick, final int mouseX, final int mouseY) {
        drawFullPanel(graphics);
    }

    @Override
    public void renderBackground(final GuiGraphics graphics) {
        
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawStatusOverlay(graphics, partialTick);
    }

    @Override
    protected void renderLabels(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        
    }

    private void drawFullPanel(final GuiGraphics graphics) {
        final int monitorX = 0;
        final int monitorY = 0;
        final int monitorWidth = width;
        final int monitorHeight = height;

        graphics.fill(monitorX, monitorY, monitorX + monitorWidth, monitorY + monitorHeight, 0x00000000);
        graphics.renderOutline(monitorX, monitorY, monitorWidth, monitorHeight, 0x80FFFFFF);

        final ShahedStatusPacket status = ShahedClientHandler.getLastStatus();
        final boolean liveSignal = hasLiveFeed(status);
        final float noiseLevel = liveSignal ? status.noiseLevel() : 1.0F;
        renderNoise(graphics, monitorX, monitorY, monitorWidth, monitorHeight, noiseLevel);

        if (!liveSignal) {
            graphics.drawString(font, Component.translatable("screen.fullfud.monitor.no_signal"), HUD_MARGIN, HUD_MARGIN, 0xFFFF5555, false);
        }
    }

    private void renderNoise(final GuiGraphics graphics, final int x, final int y, final int width, final int height, final float noiseLevel) {
        if (noiseLevel <= 0.05F) {
            return;
        }

        final long time = this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime() : System.currentTimeMillis() / 50L;
        smoothedNoise = Mth.lerp(0.15F, smoothedNoise, noiseLevel);

        for (int row = 0; row < height; row++) {
            final float wave = (float) Math.sin((row / 6.0F) + (time * 0.08F));
            final int alpha = (int) (20 + (wave + 1.0F) * 30 * smoothedNoise);
            final int grey = 90 + (int) (smoothedNoise * 80);
            final int color = (alpha << 24) | (grey << 16) | (grey << 8) | grey;
            graphics.fill(x, y + row, x + width, y + row + 1, color);
        }

        final int speckles = Mth.clamp((int) (smoothedNoise * 500), 10, 600);
        noiseRandom.setSeed(time * 31);
        for (int i = 0; i < speckles; i++) {
            final int px = x + noiseRandom.nextInt(Math.max(1, width));
            final int py = y + noiseRandom.nextInt(Math.max(1, height));
            final int alpha = 60 + noiseRandom.nextInt(120);
            final int shade = 150 + noiseRandom.nextInt(80);
            final int color = (alpha << 24) | (shade << 16) | (shade << 8) | shade;
            graphics.fill(px, py, px + 1, py + 1, color);
        }
    }

    private void drawStatusOverlay(final GuiGraphics graphics, final float partialTick) {
        final ShahedStatusPacket status = ShahedClientHandler.getLastStatus();

        if (!hasLiveFeed(status)) {
            return;
        }
        final SmoothedStatusSnapshot smoothed = smoothedStatus.sample(status);

        final int padding = 24;

        final int infoWidth = 240;
        final int infoHeight = 52;
        drawOverlayPanel(graphics, padding - 10, padding - 10, infoWidth + 20, infoHeight + 20);
        graphics.drawString(font, Component.literal(String.format("X %.1f  Z %.1f", smoothed.x(), smoothed.z())), padding, padding, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.literal(String.format("Y %.1f", smoothed.y())), padding, padding + 14, 0xFFE6E6E6, false);
        graphics.drawString(font, Component.literal(String.format("Heading %.0f°", smoothed.yaw())), padding, padding + 28, 0xFFE6E6E6, false);

        final int powerPanelWidth = 240;
        final int powerPanelX = width - padding - powerPanelWidth;
        final int powerPanelY = padding - 10;
        drawOverlayPanel(graphics, powerPanelX, powerPanelY, powerPanelWidth, 60);
        drawPowerBar(graphics, powerPanelX + 16, powerPanelY + 32, status.thrust());

        final float relativeYaw = computeRelativeYaw(smoothed.x(), smoothed.z());
        drawCompass(graphics, width / 2, padding + 36, relativeYaw);

        final int telemetryPanelWidth = 260;
        final int telemetryPanelHeight = 96;
        final int telemetryX = padding - 10;
        final int telemetryY = height - padding - telemetryPanelHeight;
        drawOverlayPanel(graphics, telemetryX, telemetryY, telemetryPanelWidth, telemetryPanelHeight);
        drawTelemetry(graphics, telemetryX + 10, telemetryY + 12, status);

        final int slipPanelWidth = 200;
        final int slipPanelHeight = 52;
        final int slipX = width - padding - slipPanelWidth;
        final int slipY = height - padding - slipPanelHeight;
        drawOverlayPanel(graphics, slipX, slipY, slipPanelWidth, slipPanelHeight);
        drawSlipIndicator(graphics, slipX + 12, slipY + slipPanelHeight - 16, status.slipAngle());

        final ShahedDroneEntity drone = resolveDrone();
        if (drone != null) {
            final int previewBoxSize = 132;
            final int previewBoxX = width - padding - previewBoxSize;
            final int previewBoxY = powerPanelY + 70;
            drawOverlayPanel(graphics, previewBoxX, previewBoxY, previewBoxSize, previewBoxSize);
            graphics.drawString(font, Component.literal("Drone Preview"), previewBoxX + 8, previewBoxY + 8, 0xFF9BE6C8, false);
            final int previewCenterY = previewBoxY + (previewBoxSize / 2) + font.lineHeight / 2;
            drawDronePreview(graphics, previewBoxX + previewBoxSize / 2, previewCenterY, 20, drone, partialTick);
        }

        drawReticle(graphics);

        if (status.signalLost()) {
            graphics.drawString(font, Component.translatable("message.fullfud.monitor.turn"), width / 2 - 70, height / 2, 0xFFFF5555, false);
        }
    }

    private void drawPowerBar(final GuiGraphics graphics, final int x, final int y, final float thrust) {
        final int barWidth = 160;
        final int barHeight = 10;
        graphics.renderOutline(x, y, barWidth, barHeight, 0xFF2F2F35);

        int fillWidth = (int) (Math.max(0.03F, thrust) * barWidth);
        fillWidth = Mth.clamp(fillWidth, 2, barWidth - 1);
        final int color = thrust > 0.75F ? 0xFF1FFFD2 : 0xFF00C99C;
        graphics.fill(x + 1, y + 1, x + fillWidth, y + barHeight - 1, color);
        graphics.drawString(font, Component.literal("Power " + Math.round(thrust * 100F) + "%"), x, y - 10, 0xFF00C99C, false);
    }

    private void drawTelemetry(final GuiGraphics graphics, final int baseX, final int startY, final ShahedStatusPacket status) {
        int lineY = startY;
        final float airspeedKph = status.airSpeed() * 3.6F;
        final float groundSpeedKph = status.groundSpeed() * 3.6F;
        graphics.drawString(font, Component.literal(String.format("IAS %3.0f km/h", airspeedKph)), baseX, lineY, 0xFFB8F2FF, false);
        lineY += 12;
        graphics.drawString(font, Component.literal(String.format("GS %3.0f km/h", groundSpeedKph)), baseX, lineY, 0xFF9BE6C8, false);
        lineY += 12;
        graphics.drawString(font, Component.literal(String.format("VSPD %+.1f m/s", status.verticalSpeed())), baseX, lineY, 0xFF9BD8FF, false);
        lineY += 12;
        graphics.drawString(font, Component.literal(String.format("AoA %+.1f deg  Slip %+.1f deg", status.angleOfAttack(), status.slipAngle())), baseX, lineY, 0xFFE6F58C, false);
        lineY += 12;
        graphics.drawString(font, Component.literal(String.format("Fuel %.1f kg  rho %.2f kg/m^3", status.fuelKg(), status.airDensity())), baseX, lineY, 0xFFF8DFA6, false);
    }

    private void drawSlipIndicator(final GuiGraphics graphics, final int x, final int y, final float slipAngle) {
        final int width = 160;
        final int height = 12;
        graphics.drawString(font, Component.literal(String.format("Slip %+.1f deg", slipAngle)), x, y - 12, 0xFFFFE36E, false);
        graphics.renderOutline(x, y, width, height, 0xFF2F2F35);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x40101015);
        final float normalized = Mth.clamp(slipAngle / 15.0F, -1.0F, 1.0F);
        final int markerX = (int) (x + width * 0.5F + normalized * (width * 0.5F - 4));
        graphics.fill(markerX - 3, y + 1, markerX + 3, y + height - 1, 0xFFFFD45C);
    }

    private void drawReticle(final GuiGraphics graphics) {
        final int centerX = this.width / 2;
        final int centerY = this.height / 2;
        final int length = 20;
        final int thickness = 1;
        final int color = 0x80202020;

        graphics.fill(centerX - length, centerY - thickness, centerX + length, centerY + thickness, color);
        graphics.fill(centerX - thickness, centerY - length, centerX + thickness, centerY + length, color);
    }

    private void drawDronePreview(final GuiGraphics graphics, final int centerX, final int centerY, final int scale, final ShahedDroneEntity drone, final float partialTick) {
        final Minecraft mc = this.minecraft;
        if (mc == null || drone == null) {
            return;
        }
        final PoseStack poseStack = graphics.pose();
        final EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        poseStack.pushPose();
        poseStack.translate(centerX, centerY, 1050.0F);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(-drone.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(drone.getVisualPitch(partialTick)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(drone.getVisualRoll(partialTick)));

        Lighting.setupForEntityInInventory();
        dispatcher.overrideCameraOrientation(Axis.XP.rotationDegrees(180.0F));
        dispatcher.setRenderShadow(false);
        final MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        RenderSystem.enableDepthTest();
        dispatcher.render(drone, 0.0D, 0.0D, 0.0D, 0.0F, partialTick, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
        bufferSource.endBatch();
        dispatcher.setRenderShadow(true);
        dispatcher.overrideCameraOrientation(new Quaternionf());
        poseStack.popPose();
        Lighting.setupFor3DItems();
        RenderSystem.disableDepthTest();
    }

    private void drawCompass(final GuiGraphics graphics, final int centerX, final int centerY, final float relativeYaw) {
        final int radius = 26;
        graphics.fill(centerX - radius, centerY - radius, centerX + radius, centerY + radius, 0x40101010);
        graphics.renderOutline(centerX - radius, centerY - radius, radius * 2, radius * 2, 0xFF2E2E35);

        final double angleRad = Math.toRadians(relativeYaw);
        final int arrowX = centerX + (int) (Math.sin(angleRad) * (radius - 4));
        final int arrowY = centerY - (int) (Math.cos(angleRad) * (radius - 4));
        graphics.fill(arrowX - 2, arrowY - 2, arrowX + 2, arrowY + 2, 0xFF00FFD5);
        graphics.drawString(font, Component.literal("N"), centerX - 4, centerY - radius - 10, 0xFFE6F2FF, false);
    }

    private void drawOverlayPanel(final GuiGraphics graphics, final int x, final int y, final int boxWidth, final int boxHeight) {
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0x50000000);
        graphics.renderOutline(x, y, boxWidth, boxHeight, 0x80181818);
    }

    private float computeRelativeYaw(final double droneX, final double droneZ) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return 0.0F;
        }
        final double dx = droneX - this.minecraft.player.getX();
        final double dz = droneZ - this.minecraft.player.getZ();
        final double targetAngle = Math.toDegrees(Math.atan2(dz, dx));
        final float playerYaw = this.minecraft.player.getYRot();
        return (float) Mth.wrapDegrees(targetAngle - (playerYaw + 90.0F));
    }

    private void sendControlInput() {
        if (this.minecraft == null || menu.getDroneId() == null) {
            return;
        }
        final float vertical = boolValue(ascendPressed) - boolValue(descendPressed);
        final float strafe = boolValue(strafeRightPressed) - boolValue(strafeLeftPressed);
        float thrustDelta = 0.0F;
        if (increasePowerPressed) {
            thrustDelta += 0.02F;
        }
        if (decreasePowerPressed) {
            thrustDelta -= 0.02F;
        }

        ShahedClientHandler.sendControlPacket(menu.getDroneId(), 0.0F, strafe, vertical, thrustDelta);
    }

    private static float boolValue(final boolean down) {
        return down ? 1.0F : 0.0F;
    }

    private void ensureCamera() {
        if (minecraft == null || minecraft.level == null) {
            hasCameraFeed = false;
            return;
        }
        final ShahedDroneEntity drone = resolveDrone();
        if (drone != null) {
            if (!cameraOverridden) {
                previousCamera = minecraft.getCameraEntity();
            }
            minecraft.setCameraEntity(drone);
            minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            cameraOverridden = true;
            hasCameraFeed = true;
        } else {
            hasCameraFeed = false;
            restoreCamera();
        }
    }

    private void restoreCamera() {
        if (cameraOverridden && previousCamera != null && minecraft != null) {
            minecraft.setCameraEntity(previousCamera);
        }
        cameraOverridden = false;
        previousCamera = null;
        if (hudOverridden && minecraft != null) {
            minecraft.options.hideGui = previousHideGui;
            hudOverridden = false;
        }
    }

    private ShahedDroneEntity resolveDrone() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        if (menu.getDroneEntityId() > 0) {
            final Entity entity = minecraft.level.getEntity(menu.getDroneEntityId());
            if (entity instanceof ShahedDroneEntity drone) {
                return drone;
            }
        }
        if (menu.getDroneId() != null) {
            for (final Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity instanceof ShahedDroneEntity drone && drone.getUUID().equals(menu.getDroneId())) {
                    return drone;
                }
            }
        }
        return null;
    }

    private boolean hasLiveFeed(final ShahedStatusPacket status) {
        return hasCameraFeed && ShahedClientHandler.hasFreshStatus(2000) && status != null;
    }

    private void resetKeyStates() {
        ascendPressed = false;
        descendPressed = false;
        strafeLeftPressed = false;
        strafeRightPressed = false;
        increasePowerPressed = false;
        decreasePowerPressed = false;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (minecraft != null && minecraft.options.keyTogglePerspective.matches(keyCode, scanCode)) {
            return true;
        }
        if (handleKeyChange(keyCode, scanCode, true)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(final int keyCode, final int scanCode, final int modifiers) {
        if (handleKeyChange(keyCode, scanCode, false)) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private boolean handleKeyChange(final int keyCode, final int scanCode, final boolean pressed) {
        if (minecraft == null) {
            return false;
        }
        final var options = minecraft.options;
        if (matches(options.keyUp, keyCode, scanCode)) {
            ascendPressed = pressed;
            return true;
        }
        if (matches(options.keyDown, keyCode, scanCode)) {
            descendPressed = pressed;
            return true;
        }
        if (matches(options.keyLeft, keyCode, scanCode)) {
            strafeLeftPressed = pressed;
            return true;
        }
        if (matches(options.keyRight, keyCode, scanCode)) {
            strafeRightPressed = pressed;
            return true;
        }
        if (matches(ShahedClientHandler.getPowerUpKey(), keyCode, scanCode)) {
            increasePowerPressed = pressed;
            return true;
        }
        if (matches(ShahedClientHandler.getPowerDownKey(), keyCode, scanCode)) {
            decreasePowerPressed = pressed;
            return true;
        }
        return false;
    }

    private static boolean matches(final net.minecraft.client.KeyMapping mapping, final int keyCode, final int scanCode) {
        return mapping.matches(keyCode, scanCode);
    }

    private static final class SmoothedStatus {
        private static final double MIN_DURATION_SEC = 0.05D;
        private static final double EPSILON = 1.0E-4D;

        private Sample previous;
        private Sample current;
        private long currentStartTimeNs;
        private double currentDurationSec = MIN_DURATION_SEC;

        SmoothedStatusSnapshot sample(final ShahedStatusPacket packet) {
            if (packet == null) {
                return fallbackSnapshot();
            }
            final long now = System.nanoTime();
            if (current == null) {
                current = Sample.from(packet, Vec3.ZERO);
                previous = current;
                currentStartTimeNs = now;
                return current.snapshot();
            }

            if (hasNewData(packet)) {
                final double elapsedSec = Math.max(1.0E-3D, (now - currentStartTimeNs) / 1_000_000_000.0D);
                final Vec3 velocity = computeVelocity(current.pos, new Vec3(packet.x(), packet.y(), packet.z()), elapsedSec);
                previous = current;
                current = Sample.from(packet, velocity);
                currentDurationSec = Math.max(MIN_DURATION_SEC, elapsedSec);
                currentStartTimeNs = now;
            }

            final double elapsed = Math.max(0.0D, (now - currentStartTimeNs) / 1_000_000_000.0D);
            final double t = currentDurationSec <= 0.0D ? 1.0D : Mth.clamp(elapsed / currentDurationSec, 0.0D, 1.0D);
            final Vec3 interpolatedPos = hermite(previous.pos, current.pos, previous.velocity.scale(currentDurationSec), current.velocity.scale(currentDurationSec), t);
            final float yaw = lerpAngle(previous.yaw, current.yaw, t);
            final float pitch = lerpAngle(previous.pitch, current.pitch, t);
            return new SmoothedStatusSnapshot(interpolatedPos.x, interpolatedPos.y, interpolatedPos.z, yaw, pitch);
        }

        private boolean hasNewData(final ShahedStatusPacket packet) {
            return Math.abs(packet.x() - current.pos.x) > EPSILON
                || Math.abs(packet.y() - current.pos.y) > EPSILON
                || Math.abs(packet.z() - current.pos.z) > EPSILON
                || Math.abs(Mth.wrapDegrees(packet.yaw() - current.yaw)) > 0.01F
                || Math.abs(Mth.wrapDegrees(packet.pitch() - current.pitch)) > 0.01F;
        }

        private static Vec3 computeVelocity(final Vec3 from, final Vec3 to, final double durationSec) {
            if (durationSec <= 1.0E-3D) {
                return Vec3.ZERO;
            }
            return to.subtract(from).scale(1.0D / durationSec);
        }

        private static Vec3 hermite(final Vec3 p0, final Vec3 p1, final Vec3 m0, final Vec3 m1, final double t) {
            final double tt = t * t;
            final double ttt = tt * t;
            final double h00 = 2.0D * ttt - 3.0D * tt + 1.0D;
            final double h10 = ttt - 2.0D * tt + t;
            final double h01 = -2.0D * ttt + 3.0D * tt;
            final double h11 = ttt - tt;
            return new Vec3(
                h00 * p0.x + h10 * m0.x + h01 * p1.x + h11 * m1.x,
                h00 * p0.y + h10 * m0.y + h01 * p1.y + h11 * m1.y,
                h00 * p0.z + h10 * m0.z + h01 * p1.z + h11 * m1.z
            );
        }

        private static float lerpAngle(final float from, final float to, final double t) {
            final float delta = Mth.wrapDegrees(to - from);
            return (float) (from + delta * t);
        }

        private SmoothedStatusSnapshot fallbackSnapshot() {
            final Sample sample = current != null ? current : previous;
            if (sample == null) {
                return SmoothedStatusSnapshot.ZERO;
            }
            return sample.snapshot();
        }

        private record Sample(Vec3 pos, float yaw, float pitch, Vec3 velocity) {
            static Sample from(final ShahedStatusPacket packet, final Vec3 velocity) {
                return new Sample(new Vec3(packet.x(), packet.y(), packet.z()), packet.yaw(), packet.pitch(), velocity);
            }

            SmoothedStatusSnapshot snapshot() {
                return new SmoothedStatusSnapshot(pos.x, pos.y, pos.z, yaw, pitch);
            }
        }
    }

    private record SmoothedStatusSnapshot(double x, double y, double z, float yaw, float pitch) {
        static final SmoothedStatusSnapshot ZERO = new SmoothedStatusSnapshot(0.0D, 0.0D, 0.0D, 0.0F, 0.0F);
    }
}
