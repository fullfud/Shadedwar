package com.fullfud.fullfud.client;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.common.entity.FpvDroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
    private static final int SYM_AH_CENTER_LINE = 0x72;
    private static final int SYM_AH_CENTER = 0x73;
    private static final int SYM_AH_CENTER_LINE_RIGHT = 0x74;
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
    private static final BfGlyphFont CROSSHAIR_FONT = new BfGlyphFont(
            new ResourceLocation(FullfudMod.MOD_ID, "osd/crosshair_thin.mcm"),
            new ResourceLocation(FullfudMod.MOD_ID, "osd/default.mcm"),
            new ResourceLocation(FullfudMod.MOD_ID, "osd/betaflight.mcm")
    );
    private static final int OSD_FG_COLOR = 0xFFFFFFFF;
    private static final int OSD_BG_COLOR = 0xCC000000;
    private static final int CROSSHAIR_BG_COLOR = OSD_BG_COLOR;
    private static final float OSD_GLYPH_SCALE = 1.0f;
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
        CROSSHAIR_FONT.ensureLoaded();

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

        final float partialTick = minecraft.getPartialTick();
        float rawHeading = normalizeDegrees(drone.getVisualYaw(partialTick));
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

        final float roll = drone.getVisualRoll(partialTick);
        final float pitch = Mth.clamp(drone.getVisualPitch(partialTick), -75.0f, 75.0f);
        final float horizonOffsetPx = -pitch * 2.5f;

        final String craftName = sanitizeCraftName(
                minecraft.player != null ? minecraft.player.getName().getString() : "FULLFUD5"
        );

        final int edgeMarginPx = Math.max(6, guiW / 80);
        final int topRow = 1;
        final int bottomRow = 14;

        final int[] craftCodes = ascii(craftName);
        final int[] acroCodes = ascii("ACRO");
        final int[] batteryCodes = concat(
                new int[]{batterySymbolForPercent(batteryPercent)},
                ascii((batteryDecivolts / 10) + "." + (batteryDecivolts % 10)),
                new int[]{SYM_VOLT}
        );
        final int[] rssiCodes = concat(new int[]{SYM_RSSI}, ascii(String.valueOf(rssi)));
        final int[] timerCodes = concat(new int[]{SYM_ON_M}, ascii(formatTime(timerSeconds)));
        final int[] speedCodes = concat(new int[]{SYM_SPEED}, ascii(String.valueOf(speed)), new int[]{SYM_KPH});
        final int[] altitudeCodes = concat(new int[]{SYM_ALTITUDE}, ascii(String.valueOf(altitude)), new int[]{SYM_METRE});
        final int[] homeCodes = concat(new int[]{homeDirectionSymbol}, ascii(String.valueOf(homeDistance)), new int[]{SYM_METRE});
        final int[] headingCodes = concat(new int[]{headingArrowSymbol}, ascii(String.valueOf(heading)));
        final int[] linkCodes = concat(new int[]{GLYPH_FONT.hasVisiblePixels(SYM_LINK_QUALITY) ? SYM_LINK_QUALITY : SYM_RSSI}, ascii(String.valueOf(linkQuality)));
        final int[] throttleCodes = concat(new int[]{SYM_THR}, ascii(String.valueOf(throttle)));
        final int[] ampCodes = concat(ascii((amperageDeci / 10) + "." + (amperageDeci % 10)), new int[]{SYM_AMP});
        final int[] mahCodes = concat(ascii(String.valueOf(mahUsed)), new int[]{SYM_MAH});
        final int[] tempCodes = concat(new int[]{SYM_TEMPERATURE}, ascii(String.valueOf(temperatureC)), new int[]{SYM_TEMP_C});

        drawCodesAt(
                guiGraphics,
                edgeMarginPx,
                rowToGlyphY(gridY, cellH, topRow, OSD_GLYPH_SCALE),
                craftCodes,
                OSD_GLYPH_SCALE
        );
        drawCodesAt(
                guiGraphics,
                (guiW - codesWidth(acroCodes, OSD_GLYPH_SCALE)) / 2,
                rowToGlyphY(gridY, cellH, topRow, OSD_GLYPH_SCALE),
                acroCodes,
                OSD_GLYPH_SCALE
        );
        drawCodesAt(
                guiGraphics,
                edgeMarginPx,
                rowToGlyphY(gridY, cellH, bottomRow, OSD_GLYPH_SCALE),
                batteryCodes,
                OSD_GLYPH_SCALE
        );
        drawCodesAt(
                guiGraphics,
                guiW - edgeMarginPx - codesWidth(rssiCodes, OSD_GLYPH_SCALE),
                rowToGlyphY(gridY, cellH, topRow, OSD_GLYPH_SCALE),
                rssiCodes,
                OSD_GLYPH_SCALE
        );
        drawCodesAt(
                guiGraphics,
                guiW - edgeMarginPx - codesWidth(timerCodes, OSD_GLYPH_SCALE),
                rowToGlyphY(gridY, cellH, bottomRow, OSD_GLYPH_SCALE),
                timerCodes,
                OSD_GLYPH_SCALE
        );
        drawCodesAt(
                guiGraphics,
                edgeMarginPx,
                rowToGlyphY(gridY, cellH, 3, OSD_GLYPH_SCALE),
                speedCodes,
                OSD_GLYPH_SCALE
        );
        drawCodesAt(
                guiGraphics,
                edgeMarginPx,
                rowToGlyphY(gridY, cellH, 4, OSD_GLYPH_SCALE),
                altitudeCodes,
                OSD_GLYPH_SCALE
        );
        drawCrosshair(guiGraphics, gridX, gridY, cellW, cellH, 14, 7);
        drawHorizon(guiGraphics, gridX, gridY, cellW, cellH, 14, 7, horizonOffsetPx, roll);
        drawCodesAt(
                guiGraphics,
                guiW - edgeMarginPx - codesWidth(homeCodes, OSD_GLYPH_SCALE),
                rowToGlyphY(gridY, cellH, 3, OSD_GLYPH_SCALE),
                homeCodes,
                OSD_GLYPH_SCALE
        );
        drawCodesAt(
                guiGraphics,
                guiW - edgeMarginPx - codesWidth(headingCodes, OSD_GLYPH_SCALE),
                rowToGlyphY(gridY, cellH, 4, OSD_GLYPH_SCALE),
                headingCodes,
                OSD_GLYPH_SCALE
        );
        drawCompassBarSmooth(guiGraphics, gridX, gridY, cellW, cellH, 10, 0, headingSmooth, OSD_GLYPH_SCALE);
        drawCodesAt(
                guiGraphics,
                guiW - edgeMarginPx - codesWidth(linkCodes, OSD_GLYPH_SCALE),
                rowToGlyphY(gridY, cellH, 5, OSD_GLYPH_SCALE),
                linkCodes,
                OSD_GLYPH_SCALE
        );
        drawCodesAt(
                guiGraphics,
                guiW - edgeMarginPx - codesWidth(throttleCodes, OSD_GLYPH_SCALE),
                rowToGlyphY(gridY, cellH, 6, OSD_GLYPH_SCALE),
                throttleCodes,
                OSD_GLYPH_SCALE
        );
        drawCodesAt(
                guiGraphics,
                edgeMarginPx,
                rowToGlyphY(gridY, cellH, 5, OSD_GLYPH_SCALE),
                ampCodes,
                OSD_GLYPH_SCALE
        );
        drawCodesAt(
                guiGraphics,
                edgeMarginPx,
                rowToGlyphY(gridY, cellH, 6, OSD_GLYPH_SCALE),
                mahCodes,
                OSD_GLYPH_SCALE
        );
        drawCodesAt(
                guiGraphics,
                guiW - edgeMarginPx - codesWidth(tempCodes, OSD_GLYPH_SCALE),
                rowToGlyphY(gridY, cellH, 7, OSD_GLYPH_SCALE),
                tempCodes,
                OSD_GLYPH_SCALE
        );
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
        int centerX = x + cellW;
        int centerY = y + cellH;
        int leftGlyphX = centerX - ((BfGlyphFont.GLYPH_WIDTH * 3) / 2);
        int glyphY = centerY - (BfGlyphFont.GLYPH_HEIGHT / 2);
        BfGlyphFont font = CROSSHAIR_FONT.isLoaded() ? CROSSHAIR_FONT : GLYPH_FONT;

        font.drawCode(guiGraphics, SYM_AH_CENTER_LINE, leftGlyphX, glyphY, OSD_FG_COLOR, CROSSHAIR_BG_COLOR);
        font.drawCode(guiGraphics, SYM_AH_CENTER, leftGlyphX + BfGlyphFont.GLYPH_WIDTH, glyphY, OSD_FG_COLOR, CROSSHAIR_BG_COLOR);
        font.drawCode(guiGraphics, SYM_AH_CENTER_LINE_RIGHT, leftGlyphX + BfGlyphFont.GLYPH_WIDTH * 2, glyphY, OSD_FG_COLOR, CROSSHAIR_BG_COLOR);
    }

    private static void drawHorizon(
            final GuiGraphics guiGraphics,
            final int gridX,
            final int gridY,
            final int cellW,
            final int cellH,
            final int baseX,
            final int baseY,
            final float offsetPx,
            final float rollDegrees
    ) {
        final int x = gridX + baseX * cellW;
        final int y = gridY + baseY * cellH;
        final int centerX = x + cellW;
        final int centerY = y + cellH;
        final int glyphW = glyphWidth(1.0f);
        final int glyphH = glyphHeight(1.0f);
        final float radians = (float) Math.toRadians(-rollDegrees);
        final float cos = Mth.cos(radians);
        final float sin = Mth.sin(radians);
        for (int dx = -4; dx <= 4; dx++) {
            final float localX = dx * cellW;
            final float rotatedX = localX * cos - offsetPx * sin;
            final float rotatedY = localX * sin + offsetPx * cos;
            final int glyphX = Math.round(centerX + rotatedX - (glyphW / 2.0f));
            final int glyphY = Math.round(centerY + rotatedY - (glyphH / 2.0f));
            GLYPH_FONT.drawCodeScaled(guiGraphics, SYM_AH_BAR9_0 + 4, glyphX, glyphY, 1.0f, OSD_FG_COLOR, OSD_BG_COLOR);
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
            final float headingDegrees,
            final float glyphScale
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
        int glyphW = Math.max(1, Math.round(BfGlyphFont.GLYPH_WIDTH * glyphScale));
        int glyphH = Math.max(1, Math.round(BfGlyphFont.GLYPH_HEIGHT * glyphScale));
        int glyphY = y + ((cellH - glyphH) / 2);

        for (int i = 0; i < drawCells; i++) {
            int code = COMPASS_BAR_BASE[Math.floorMod(baseIndex + i, COMPASS_BAR_BASE.length)];
            int cellPixelX = x + i * cellW - shiftPx;
            int glyphX = cellPixelX + ((cellW - glyphW) / 2);
            if (glyphX + glyphW <= x || glyphX >= maxX) {
                continue;
            }
            GLYPH_FONT.drawCodeScaled(guiGraphics, code, glyphX, glyphY, glyphScale, OSD_FG_COLOR, OSD_BG_COLOR);
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
            final int[] codes,
            final float glyphScale
    ) {
        for (int i = 0; i < codes.length; i++) {
            drawGlyphInCell(guiGraphics, gridX, gridY, cellW, cellH, startCellX + i, startCellY, codes[i], 0, glyphScale);
        }
    }

    private static int glyphWidth(final float glyphScale) {
        return Math.max(1, Math.round(BfGlyphFont.GLYPH_WIDTH * glyphScale));
    }

    private static int glyphHeight(final float glyphScale) {
        return Math.max(1, Math.round(BfGlyphFont.GLYPH_HEIGHT * glyphScale));
    }

    private static int codesWidth(final int[] codes, final float glyphScale) {
        if (codes == null || codes.length == 0) {
            return 0;
        }
        return codes.length * glyphWidth(glyphScale);
    }

    private static int rowToGlyphY(final int gridY, final int cellH, final int row, final float glyphScale) {
        return gridY + row * cellH + ((cellH - glyphHeight(glyphScale)) / 2);
    }

    private static void drawCodesAt(
            final GuiGraphics guiGraphics,
            final int startX,
            final int y,
            final int[] codes,
            final float glyphScale
    ) {
        if (codes == null || codes.length == 0) {
            return;
        }
        final int step = glyphWidth(glyphScale);
        for (int i = 0; i < codes.length; i++) {
            GLYPH_FONT.drawCodeScaled(
                    guiGraphics,
                    codes[i],
                    startX + i * step,
                    y,
                    glyphScale,
                    OSD_FG_COLOR,
                    OSD_BG_COLOR
            );
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
            final int yPixelOffset,
            final float glyphScale
    ) {
        if (cellX < 0 || cellX >= GRID_COLS || cellY < 0 || cellY >= GRID_ROWS) {
            return;
        }
        final int glyphW = glyphWidth(glyphScale);
        final int glyphH = glyphHeight(glyphScale);
        int glyphX = gridX + cellX * cellW + ((cellW - glyphW) / 2);
        int glyphY = gridY + cellY * cellH + ((cellH - glyphH) / 2) + yPixelOffset;
        GLYPH_FONT.drawCodeScaled(guiGraphics, code, glyphX, glyphY, glyphScale, OSD_FG_COLOR, OSD_BG_COLOR);
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
