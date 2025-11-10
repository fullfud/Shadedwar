package com.fullfud.fullfud.client.hud;

import com.fullfud.fullfud.client.ShahedClientHandler;
import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import com.fullfud.fullfud.common.item.FpvGogglesItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;

import java.util.Optional;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class FpvHudOverlay {
    private FpvHudOverlay() {
    }

    public static void onRenderOverlay(final RenderGuiOverlayEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !ShahedClientHandler.isFpvViewActive() || !FpvGogglesItem.isWearing(player)) {
            return;
        }
        final Optional<UUID> linkedDroneId = findLinkedDroneId(player);
        if (linkedDroneId.isEmpty()) {
            return;
        }
        final UUID droneId = linkedDroneId.get();
        final FpvDroneEntity drone = findDrone(minecraft, droneId).orElse(null);
        renderHud(event.getGuiGraphics(), minecraft.font, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), droneId, drone);
    }

    private static void renderHud(final GuiGraphics guiGraphics, final Font font, final int width, final int height, final UUID droneId, final FpvDroneEntity drone) {
        final int centerX = width / 2;
        final int centerY = height / 2;
        final int accent = 0x88FFFFFF;
        final int text = 0xF0F0F0;

        guiGraphics.hLine(centerX - 20, centerX - 5, centerY, accent);
        guiGraphics.hLine(centerX + 5, centerX + 20, centerY, accent);
        guiGraphics.vLine(centerX, centerY - 20, centerY - 5, accent);
        guiGraphics.vLine(centerX, centerY + 5, centerY + 20, accent);

        final int left = 12;
        final int top = 12;
        guiGraphics.drawString(font, Component.translatable("hud.fullfud.fpv.link", shortId(droneId)).getString(), left, top, text, false);
        if (drone == null) {
            guiGraphics.drawString(font, Component.translatable("hud.fullfud.fpv.no_signal").getString(), left, top + 10, text, false);
            return;
        }

        final int battery = Math.round(drone.getBatteryLevel() * 100.0F);
        final int throttle = Math.round(drone.getThrottle() * 100.0F);
        final String status = drone.isFailsafeActive() ? Component.translatable("hud.fullfud.fpv.status.failsafe").getString() : Component.translatable("hud.fullfud.fpv.status.nominal").getString();

        guiGraphics.drawString(font, Component.translatable("hud.fullfud.fpv.battery", battery).getString(), left, top + 10, text, false);
        guiGraphics.drawString(font, Component.translatable("hud.fullfud.fpv.throttle", throttle).getString(), left, top + 20, text, false);
        guiGraphics.drawString(font, Component.translatable("hud.fullfud.fpv.status", status).getString(), left, top + 30, drone.isFailsafeActive() ? 0xFFFF6060 : text, false);
    }

    private static Optional<UUID> findLinkedDroneId(final LocalPlayer player) {
        final UUID active = ShahedClientHandler.getActiveFpvDrone();
        if (active != null) {
            return Optional.of(active);
        }
        return FpvGogglesItem.getLinkedDrone(player);
    }

    private static Optional<FpvDroneEntity> findDrone(final Minecraft minecraft, final UUID id) {
        for (final Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof FpvDroneEntity drone && drone.getUUID().equals(id)) {
                return Optional.of(drone);
            }
        }
        return Optional.empty();
    }

    private static String shortId(final UUID uuid) {
        final String str = uuid.toString();
        return str.substring(0, 8);
    }
}
