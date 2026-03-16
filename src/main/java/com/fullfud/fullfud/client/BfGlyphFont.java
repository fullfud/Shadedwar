package com.fullfud.fullfud.client;

import com.fullfud.fullfud.FullfudMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BfGlyphFont {
    public static final int GLYPH_WIDTH = 12;
    public static final int GLYPH_HEIGHT = 18;

    private static final int GLYPH_COUNT = 256;
    private static final int GLYPH_PIXEL_COUNT = GLYPH_WIDTH * GLYPH_HEIGHT;
    private static final int MCM_CHAR_FIELD_BYTES = 64;
    private static final int MCM_PIXELS_PER_BYTE = 4;
    private static final ResourceLocation[] DEFAULT_FONT_RESOURCES = new ResourceLocation[]{
            new ResourceLocation(FullfudMod.MOD_ID, "osd/default.mcm"),
            new ResourceLocation(FullfudMod.MOD_ID, "osd/betaflight.mcm")
    };

    private final ResourceLocation[] fontResources;
    private final int[][] glyphs = new int[GLYPH_COUNT][GLYPH_PIXEL_COUNT];
    private boolean loaded;
    private boolean failed;

    public BfGlyphFont() {
        this(DEFAULT_FONT_RESOURCES);
    }

    public BfGlyphFont(ResourceLocation... fontResources) {
        if (fontResources == null || fontResources.length == 0) {
            this.fontResources = DEFAULT_FONT_RESOURCES;
            return;
        }
        this.fontResources = Arrays.copyOf(fontResources, fontResources.length);
    }

    public void ensureLoaded() {
        if (loaded || failed) {
            return;
        }

        for (ResourceLocation resource : fontResources) {
            try (InputStream stream = Minecraft.getInstance().getResourceManager().open(resource);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                List<String> lines = reader.lines().collect(Collectors.toList());
                parseMcm(lines);
                loaded = true;
                return;
            } catch (Exception ignored) {
            }
        }

        failed = true;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void drawCode(GuiGraphics guiGraphics, int code, int x, int y, int foregroundColor, int backgroundColor) {
        if (!loaded) {
            return;
        }
        drawGlyph(guiGraphics, code & 0xFF, x, y, foregroundColor, backgroundColor);
    }

    public void drawCodeScaled(
        GuiGraphics guiGraphics,
        int code,
        int x,
        int y,
        float scale,
        int foregroundColor,
        int backgroundColor
    ) {
        if (!loaded) {
            return;
        }
        if (!Float.isFinite(scale) || scale <= 0.0F) {
            return;
        }
        if (Math.abs(scale - 1.0F) < 0.0001F) {
            drawGlyph(guiGraphics, code & 0xFF, x, y, foregroundColor, backgroundColor);
            return;
        }
        drawGlyphScaled(guiGraphics, code & 0xFF, x, y, scale, foregroundColor, backgroundColor);
    }

    public boolean hasVisiblePixels(int code) {
        if (!loaded) {
            return false;
        }
        int[] glyph = glyphs[code & 0xFF];
        for (int value : glyph) {
            if (value == 2) {
                return true;
            }
        }
        return false;
    }

    private void drawGlyph(GuiGraphics guiGraphics, int glyphIndex, int x, int y, int foregroundColor, int backgroundColor) {
        int[] glyph = glyphs[glyphIndex];
        for (int py = 0; py < GLYPH_HEIGHT; py++) {
            int rowStart = py * GLYPH_WIDTH;
            for (int px = 0; px < GLYPH_WIDTH; px++) {
                int value = glyph[rowStart + px];
                if (value == 2) {
                    guiGraphics.fill(x + px, y + py, x + px + 1, y + py + 1, foregroundColor);
                } else if (value == 0) {
                    guiGraphics.fill(x + px, y + py, x + px + 1, y + py + 1, backgroundColor);
                }
            }
        }
    }

    private void drawGlyphScaled(
        GuiGraphics guiGraphics,
        int glyphIndex,
        int x,
        int y,
        float scale,
        int foregroundColor,
        int backgroundColor
    ) {
        int[] glyph = glyphs[glyphIndex];
        for (int py = 0; py < GLYPH_HEIGHT; py++) {
            int y0 = y + Math.round(py * scale);
            int y1 = y + Math.round((py + 1) * scale);
            if (y1 <= y0) {
                y1 = y0 + 1;
            }
            int rowStart = py * GLYPH_WIDTH;
            for (int px = 0; px < GLYPH_WIDTH; px++) {
                int value = glyph[rowStart + px];
                if (value != 2 && value != 0) {
                    continue;
                }
                int x0 = x + Math.round(px * scale);
                int x1 = x + Math.round((px + 1) * scale);
                if (x1 <= x0) {
                    x1 = x0 + 1;
                }
                guiGraphics.fill(x0, y0, x1, y1, value == 2 ? foregroundColor : backgroundColor);
            }
        }
    }

    private void parseMcm(List<String> lines) throws IOException {
        if (lines.isEmpty() || !lines.get(0).trim().equals("MAX7456")) {
            throw new IOException("Invalid MCM header");
        }

        int glyphIndex = 0;
        int[] tempPixels = new int[MCM_CHAR_FIELD_BYTES * MCM_PIXELS_PER_BYTE];
        int tempIndex = 0;

        for (int i = 1; i < lines.size() && glyphIndex < GLYPH_COUNT; i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.length() != 8) {
                throw new IOException("Invalid MCM line length at line " + (i + 1));
            }

            int byteValue = Integer.parseInt(line, 2);
            for (int pair = 0; pair < MCM_PIXELS_PER_BYTE; pair++) {
                int shift = 6 - pair * 2;
                tempPixels[tempIndex++] = (byteValue >> shift) & 0b11;
            }

            if (tempIndex == tempPixels.length) {
                System.arraycopy(tempPixels, 0, glyphs[glyphIndex], 0, GLYPH_PIXEL_COUNT);
                glyphIndex++;
                tempIndex = 0;
            }
        }

        for (int i = glyphIndex; i < GLYPH_COUNT; i++) {
            Arrays.fill(glyphs[i], 1);
        }
    }
}
