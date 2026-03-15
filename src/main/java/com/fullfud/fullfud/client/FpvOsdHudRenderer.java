package com.fullfud.fullfud.client;

import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.UUID;

public final class FpvOsdHudRenderer {
    private static final int GRID_COLS = 30;
    private static final int GRID_ROWS = 16;

    private static final int SYM_VOLT = 0x06;
    private static final int SYM_RSSI = 0x01;
    private static final int SYM_ON_M = 0x9B;
    private static final int SYM_KPH = 0x9E;
    private static final int SYM_METRE = 0x0C;
    private static final int SYM_SPEED = 0x70;
    private static final int SYM_ALTITUDE = 0x7F;
    private static final int SYM_AMP = 0x9A;
    private static final int SYM_MAH = 0x07;
    private static final int SYM_HOMEFLAG = 0x11;
    private static final int SYM_ARROW_EAST = 0x64;
    private static final int SYM_ARROW_SOUTH = 0x60;
    private static final int SYM_LINK_QUALITY = 0x7B;
    private static final int SYM_THR = 0x04;
    private static final int SYM_TEMPERATURE = 0x7A;
    private static final int SYM_TEMP_C = 0x0E;
    private static final int SYM_AH_LEFT = 0x03;
    private static final int SYM_AH_RIGHT = 0x02;
    private static final int SYM_AH_BAR9_0 = 0x80;
    private static final int SYM_AH_DECORATION = 0x13;
    private static final int SYM_HEADING_LINE = 0x1D;
    private static final int SYM_HEADING_DIVIDED_LINE = 0x1C;
    private static final int SYM_HEADING_N = 0x18;
    private static final int SYM_HEADING_S = 0x19;
    private static final int SYM_HEADING_E = 0x1A;
    private static final int SYM_HEADING_W = 0x1B;
    private static final int SYM_BATT_FULL = 0x90;
    private static final int SYM_BATT_EMPTY = 0x96;

    private static final int[] COMPASS_BAR_BASE = new int[]{
            SYM_HEADING_W, SYM_HEADING_LINE, SYM_HEADING_DIVIDED_LINE, SYM_HEADING_LINE,
            SYM_HEADING_N, SYM_HEADING_LINE, SYM_HEADING_DIVIDED_LINE, SYM_HEADING_LINE,
            SYM_HEADING_E, SYM_HEADING_LINE, SYM_HEADING_DIVIDED_LINE, SYM_HEADING_LINE,
            SYM_HEADING_S, SYM_HEADING_LINE, SYM_HEADING_DIVIDED_LINE, SYM_HEADING_LINE
    };

    private static final BfGlyphFont GLYPH_FONT = new BfGlyphFont();
    private static final int OSD_FG_COLOR = 0xFFFFFFFF;
    private static final int OSD_BG_COLOR = 0xCC000000;
    private static final int BATTERY_MAX_TICKS = 12000;
    private static final int BATTERY_CAPACITY_MAH = 1500;

    private static UUID sessionDroneId;
    private static long sessionStartMillis;
    private static long lastFrameNanos;
    private static int lastBatteryTicks = -1;
    private static int batteryWindowStartTicks = -1;
    private static long batteryWindowStartNanos;
    private static float batteryWindowAmps;
    private static int mahUsed;
    private static float headingSmooth;
    private static float horizonOffsetSmoothPx;
    private static float ampSmooth;
    private static float tempSmooth;
    private static double launchX;
    private static double launchY;
    private static double launchZ;
    private static double lastDroneY;

    private FpvOsdHudRenderer() {
    }

    public static void render(
            final GuiGraphics guiGraphics,
            final Minecraft minecraft,
            final FpvDroneEntity drone,
            final double speedMs,
            final double groundSpeedKmh,
            final double distanceToPilot,
            final float throttleDisplayMax
    ) {
        if (guiGraphics == null || minecraft == null || drone == null) {
            return;
        }

        GLYPH_FONT.ensureLoaded();
        if (!GLYPH_FONT.isLoaded()) {
            return;
        }

        final long nowMillis = System.currentTimeMillis();
        final long nowNanos = System.nanoTime();
        if (sessionDroneId == null || !sessionDroneId.equals(drone.getUUID())) {
            sessionDroneId = drone.getUUID();
            sessionStartMillis = nowMillis;
            lastFrameNanos = nowNanos;
            lastBatteryTicks = Mth.clamp(drone.getBatteryTicks(), 0, BATTERY_MAX_TICKS);
            batteryWindowStartTicks = lastBatteryTicks;
            batteryWindowStartNanos = nowNanos;
            batteryWindowAmps = 0.0f;
            mahUsed = 0;
            headingSmooth = 0.0f;
            horizonOffsetSmoothPx = 0.0f;
            ampSmooth = 0.0f;
            launchX = drone.getX();
            launchY = drone.getY();
            launchZ = drone.getZ();
            lastDroneY = launchY;
            tempSmooth = 22.0f;
        }

        final double dtSeconds = lastFrameNanos == 0L ? 0.0 : (nowNanos - lastFrameNanos) / 1_000_000_000.0;
        lastFrameNanos = nowNanos;

        final int guiW = minecraft.getWindow().getGuiScaledWidth();
        final int guiH = minecraft.getWindow().getGuiScaledHeight();
        final int cellH = Mth.clamp(guiH / 20, 14, 18);
        final int cellW = Math.round(cellH * (17.0f / 18.0f));
        final int gridW = GRID_COLS * cellW;
        final int gridH = GRID_ROWS * cellH;
        final int gridX = (guiW - gridW) / 2;
        final int gridY = (guiH - gridH) / 2;

        final int batteryPercent = Mth.clamp(drone.getBatteryPercent(), 0, 100);
        final int batteryDecivolts = Mth.clamp(Math.round(140.0f + batteryPercent * 0.28f), 100, 260);
        final int rssi = Mth.clamp(Math.round(drone.getSignalQuality() * 100.0f), 0, 100);
        final int linkQuality = rssi;
        final int speed = Mth.clamp((int) Math.round(groundSpeedKmh), 0, 999);
        final int altitude = (int) Math.round(drone.getY());
        final int timerSeconds = sessionStartMillis > 0L ? (int) ((nowMillis - sessionStartMillis) / 1000L) : 0;
        final double homeDx = launchX - drone.getX();
        final double homeDz = launchZ - drone.getZ();
        final int homeDistance = Mth.clamp((int) Math.round(Math.sqrt(homeDx * homeDx + homeDz * homeDz)), 0, 99999);
        final float displayMax = Math.max(throttleDisplayMax, 0.01f);
        final int throttle = Mth.clamp(Math.round(Mth.clamp(drone.getThrust() / displayMax, 0.0f, 1.0f) * 100.0f), 0, 100);
        final int batteryTicks = Mth.clamp(drone.getBatteryTicks(), 0, BATTERY_MAX_TICKS);
        if (lastBatteryTicks < 0) {
            lastBatteryTicks = batteryTicks;
        }
        final int consumedTicks = Math.max(0, lastBatteryTicks - batteryTicks);
        lastBatteryTicks = batteryTicks;
        if (batteryWindowStartTicks < 0) {
            batteryWindowStartTicks = batteryTicks;
            batteryWindowStartNanos = nowNanos;
        }
        final long batteryWindowElapsedNanos = nowNanos - batteryWindowStartNanos;
        if (batteryWindowElapsedNanos >= 500_000_000L) {
            final int drainedTicks = Math.max(0, batteryWindowStartTicks - batteryTicks);
            final double windowSeconds = batteryWindowElapsedNanos / 1_000_000_000.0;
            if (windowSeconds > 0.0) {
                final double mahPerSecond = (drainedTicks * (double) BATTERY_CAPACITY_MAH) / (BATTERY_MAX_TICKS * windowSeconds);
                batteryWindowAmps = (float) (mahPerSecond * 3.6);
            }
            batteryWindowStartTicks = batteryTicks;
            batteryWindowStartNanos = nowNanos;
        }

        final double ampFromFlight = 4.0 + drone.getThrust() * 24.0 + speedMs * 0.32;
        final double ampTarget = batteryWindowAmps > 0.0f
                ? (batteryWindowAmps * 0.55 + ampFromFlight * 0.45)
                : ampFromFlight;
        final float ampAlpha = dtSeconds > 0.0 ? (float) Mth.clamp(dtSeconds * 2.2, 0.02, 0.10) : 0.06f;
        ampSmooth += (float) ((ampTarget - ampSmooth) * ampAlpha);
        final int amperageDeci = Mth.clamp(Math.round(ampSmooth * 10.0f), 0, 800);

        mahUsed = Mth.clamp(Math.round(((BATTERY_MAX_TICKS - batteryTicks) / (float) BATTERY_MAX_TICKS) * BATTERY_CAPACITY_MAH), 0, 99999);

        final double verticalSpeed = dtSeconds > 0.0001 ? (drone.getY() - lastDroneY) / dtSeconds : 0.0;
        lastDroneY = drone.getY();
        float ambientTemp = 22.0f;
        if (minecraft.level != null) {
            final BlockPos dronePos = BlockPos.containing(drone.getX(), drone.getY(), drone.getZ());
            final float biomeBase = minecraft.level.getBiome(dronePos).value().getBaseTemperature();
            ambientTemp = 8.0f + biomeBase * 18.0f;
        }
        final float tempTarget = (float) (ambientTemp + ampSmooth * 0.75f + drone.getThrust() * 10.0f - speedMs * 0.28f + Math.max(0.0, verticalSpeed) * 0.12f);
        tempSmooth += (tempTarget - tempSmooth) * 0.07f;
        final int temperatureC = Mth.clamp(Math.round(tempSmooth), -20, 120);

        float rawHeading = normalizeDegrees(drone.getCameraOrientation(minecraft.getPartialTick()).yaw());
        if (!Float.isFinite(rawHeading)) {
            rawHeading = 0.0f;
        }
        if (!Float.isFinite(headingSmooth)) {
            headingSmooth = rawHeading;
        }
        headingSmooth = lerpAngleDegrees(headingSmooth, rawHeading, 0.22f);
        final int heading = Math.floorMod(Math.round(headingSmooth), 360);
        final int headingArrowSymbol = directionArrowSymbol(headingSmooth);

        final float bearingToHome = (float) Math.toDegrees(Math.atan2(-homeDx, homeDz));
        final float relativeHomeHeading = Mth.wrapDegrees(bearingToHome - headingSmooth);
        final int homeDirectionSymbol = directionArrowSymbol(relativeHomeHeading);

        final float pitch = Mth.clamp(drone.getVisualPitch(minecraft.getPartialTick()), -75.0f, 75.0f);
        final float targetHorizonOffsetPx = -(pitch / 75.0f) * (cellH * 2.0f);
        horizonOffsetSmoothPx += (targetHorizonOffsetPx - horizonOffsetSmoothPx) * 0.18f;

        final String craftName = sanitizeCraftName(
                minecraft.player != null ? minecraft.player.getName().getString() : "FULLFUD5"
        );

        drawCodes(guiGraphics, gridX, gridY, cellW, cellH, 1, 1, ascii(craftName));
        drawCodes(guiGraphics, gridX, gridY, cellW, cellH, 13, 1, ascii("ACRO"));
        drawCodes(guiGraphics, gridX, gridY, cellW, cellH, 1, 14,
                concat(new int[]{batterySymbolForPercent(batteryPercent)}, ascii((batteryDecivolts / 10) + "." + (batteryDecivolts % 10)), new int[]{SYM_VOLT}));
        drawCodes(guiGraphics, gridX, gridY, cellW, cellH, 23, 1, concat(new int[]{SYM_RSSI}, ascii(String.valueOf(rssi))));
        drawCodes(guiGraphics, gridX, gridY, cellW, cellH, 22, 14, concat(new int[]{SYM_ON_M}, ascii(formatTime(timerSeconds))));
        drawCodes(guiGraphics, gridX, gridY, cellW, cellH, 1, 3, concat(new int[]{SYM_SPEED}, ascii(String.valueOf(speed)), new int[]{SYM_KPH}));
        drawCodes(guiGraphics, gridX, gridY, cellW, cellH, 1, 4, concat(new int[]{SYM_ALTITUDE}, ascii(String.valueOf(altitude)), new int[]{SYM_METRE}));
        drawCrosshair(guiGraphics, gridX, gridY, cellW, cellH, 14, 7);
        drawHorizon(guiGraphics, gridX, gridY, cellW, cellH, 14, 7, horizonOffsetSmoothPx);
        drawCodes(guiGraphics, gridX, gridY, cellW, cellH, 22, 3, concat(new int[]{homeDirectionSymbol}, ascii(String.valueOf(homeDistance)), new int[]{SYM_METRE}));
        drawCodes(guiGraphics, gridX, gridY, cellW, cellH, 22, 4, concat(new int[]{headingArrowSymbol}, ascii(String.valueOf(heading))));
        drawCompassBarSmooth(guiGraphics, gridX, gridY, cellW, cellH, 10, 0, headingSmooth);
        int linkSym = GLYPH_FONT.hasVisiblePixels(SYM_LINK_QUALITY) ? SYM_LINK_QUALITY : SYM_RSSI;
        drawCodes(guiGraphics, gridX, gridY, cellW, cellH, 22, 5, concat(new int[]{linkSym}, ascii(String.valueOf(linkQuality))));
        drawCodes(guiGraphics, gridX, gridY, cellW, cellH, 22, 6, concat(new int[]{SYM_THR}, ascii(String.valueOf(throttle))));
        drawCodes(guiGraphics, gridX, gridY, cellW, cellH, 1, 5, concat(ascii((amperageDeci / 10) + "." + (amperageDeci % 10)), new int[]{SYM_AMP}));
        drawCodes(guiGraphics, gridX, gridY, cellW, cellH, 1, 6, concat(ascii(String.valueOf(mahUsed)), new int[]{SYM_MAH}));
        drawCodes(guiGraphics, gridX, gridY, cellW, cellH, 22, 7, concat(new int[]{SYM_TEMPERATURE}, ascii(String.valueOf(temperatureC)), new int[]{SYM_TEMP_C}));
    }

    private static void drawCrosshair(
            final GuiGraphics guiGraphics,
            final int gridX,
            final int gridY,
            final int cellW,
            final int cellH,
            final int cellX,
            final int cellY
    ) {
        int x = gridX + cellX * cellW;
        int y = gridY + cellY * cellH;
        int cx = x + cellW;
        int cy = y + cellH;
        int arm = Math.max(3, cellH / 5);

        guiGraphics.fill(cx - 1, cy - arm - 1, cx + 1, cy + arm + 2, 0xAA000000);
        guiGraphics.fill(cx - arm - 1, cy - 1, cx + arm + 2, cy + 1, 0xAA000000);
        guiGraphics.fill(cx, cy - arm, cx + 1, cy + arm + 1, OSD_FG_COLOR);
        guiGraphics.fill(cx - arm, cy, cx + arm + 1, cy + 1, OSD_FG_COLOR);
    }

    private static void drawHorizon(
            final GuiGraphics guiGraphics,
            final int gridX,
            final int gridY,
            final int cellW,
            final int cellH,
            final int baseX,
            final int baseY,
            final float offsetPx
    ) {
        int hudWidth = 7;
        int hudHeight = 3;
        for (int dy = -hudHeight; dy <= hudHeight; dy++) {
            drawGlyphInCell(guiGraphics, gridX, gridY, cellW, cellH, baseX - hudWidth, baseY + dy, SYM_AH_DECORATION, 0);
            drawGlyphInCell(guiGraphics, gridX, gridY, cellW, cellH, baseX + hudWidth, baseY + dy, SYM_AH_DECORATION, 0);
        }
        drawGlyphInCell(guiGraphics, gridX, gridY, cellW, cellH, baseX - hudWidth + 1, baseY, SYM_AH_LEFT, 0);
        drawGlyphInCell(guiGraphics, gridX, gridY, cellW, cellH, baseX + hudWidth - 1, baseY, SYM_AH_RIGHT, 0);

        int totalOffsetPx = (cellH / 2) + Math.round(offsetPx);
        int lineCellOffset = totalOffsetPx / cellH;
        int linePixelOffset = totalOffsetPx - (lineCellOffset * cellH);
        int lineY = baseY + lineCellOffset;
        for (int dx = -4; dx <= 4; dx++) {
            drawGlyphInCell(guiGraphics, gridX, gridY, cellW, cellH, baseX + dx, lineY, SYM_AH_BAR9_0 + 4, linePixelOffset);
        }
    }

    private static void drawCompassBarSmooth(
            final GuiGraphics guiGraphics,
            final int gridX,
            final int gridY,
            final int cellW,
            final int cellH,
            final int cellX,
            final int cellY,
            final float headingDegrees
    ) {
        int x = gridX + cellX * cellW;
        int y = gridY + cellY * cellH;
        float direction = normalizeDegrees(headingDegrees) * (16.0f / 360.0f);
        int baseIndex = Mth.floor(direction);
        float frac = direction - baseIndex;
        int shiftPx = Math.round(frac * cellW);
        int visibleCells = 9;
        int drawCells = visibleCells + 1;
        int maxX = x + visibleCells * cellW;
        int glyphY = y + ((cellH - BfGlyphFont.GLYPH_HEIGHT) / 2);

        for (int i = 0; i < drawCells; i++) {
            int code = COMPASS_BAR_BASE[Math.floorMod(baseIndex + i, COMPASS_BAR_BASE.length)];
            int cellPixelX = x + i * cellW - shiftPx;
            int glyphX = cellPixelX + ((cellW - BfGlyphFont.GLYPH_WIDTH) / 2);
            if (glyphX + BfGlyphFont.GLYPH_WIDTH <= x || glyphX >= maxX) {
                continue;
            }
            GLYPH_FONT.drawCode(guiGraphics, code, glyphX, glyphY, OSD_FG_COLOR, OSD_BG_COLOR);
        }
    }

    private static void drawCodes(
            final GuiGraphics guiGraphics,
            final int gridX,
            final int gridY,
            final int cellW,
            final int cellH,
            final int startCellX,
            final int startCellY,
            final int[] codes
    ) {
        for (int i = 0; i < codes.length; i++) {
            drawGlyphInCell(guiGraphics, gridX, gridY, cellW, cellH, startCellX + i, startCellY, codes[i], 0);
        }
    }

    private static void drawGlyphInCell(
            final GuiGraphics guiGraphics,
            final int gridX,
            final int gridY,
            final int cellW,
            final int cellH,
            final int cellX,
            final int cellY,
            final int code,
            final int yPixelOffset
    ) {
        if (cellX < 0 || cellX >= GRID_COLS || cellY < 0 || cellY >= GRID_ROWS) {
            return;
        }
        int glyphX = gridX + cellX * cellW + ((cellW - BfGlyphFont.GLYPH_WIDTH) / 2);
        int glyphY = gridY + cellY * cellH + ((cellH - BfGlyphFont.GLYPH_HEIGHT) / 2) + yPixelOffset;
        GLYPH_FONT.drawCode(guiGraphics, code, glyphX, glyphY, OSD_FG_COLOR, OSD_BG_COLOR);
    }

    private static int batterySymbolForPercent(int percent) {
        float t = Mth.clamp(percent / 100.0f, 0.0f, 1.0f);
        int step = Math.round((1.0f - t) * 6.0f);
        return Mth.clamp(SYM_BATT_FULL + step, SYM_BATT_FULL, SYM_BATT_EMPTY);
    }

    private static String sanitizeCraftName(String value) {
        String upper = value == null ? "" : value.toUpperCase(Locale.ROOT);
        StringBuilder clean = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c >= 32 && c <= 126) {
                clean.append(c);
            }
        }
        String out = clean.toString().trim();
        if (out.isEmpty()) {
            out = "FULLFUD5";
        }
        return out.length() > 12 ? out.substring(0, 12) : out;
    }

    private static float normalizeDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        return wrapped < 0.0f ? wrapped + 360.0f : wrapped;
    }

    private static float lerpAngleDegrees(float current, float target, float alpha) {
        float delta = Mth.wrapDegrees(target - current);
        return normalizeDegrees(current + delta * Mth.clamp(alpha, 0.0f, 1.0f));
    }

    private static int directionArrowSymbol(float headingDegrees) {
        int heading = Math.round(normalizeDegrees(headingDegrees));
        int direction = (heading * 16 + 180) / 360;
        direction = Math.floorMod(direction, 16);
        direction = 16 - direction;
        direction = (direction + 8) % 16;
        return SYM_ARROW_SOUTH + direction;
    }

    private static String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    private static int[] ascii(String text) {
        int[] out = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            out[i] = text.charAt(i) & 0xFF;
        }
        return out;
    }

    private static int[] concat(int[]... arrays) {
        int total = 0;
        for (int[] array : arrays) {
            total += array.length;
        }
        int[] result = new int[total];
        int offset = 0;
        for (int[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
