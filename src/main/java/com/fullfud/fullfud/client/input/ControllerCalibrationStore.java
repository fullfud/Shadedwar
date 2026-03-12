package com.fullfud.fullfud.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the locally calibrated controller profile in the Minecraft config folder.
 */
public final class ControllerCalibrationStore {
    private static final String FILE_NAME = "fpv_controller.snbt";

    private ControllerCalibrationStore() {
    }

    public static void loadInto(final ControllerCalibration calibration) {
        if (calibration == null) {
            return;
        }
        calibration.reset();

        final Path path = resolvePath();
        if (path == null || !Files.exists(path)) {
            return;
        }

        try {
            final String snbt = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (snbt.isEmpty()) {
                return;
            }
            final CompoundTag tag = TagParser.parseTag(snbt);
            calibration.load(tag);
        } catch (Exception ignored) {
        }
    }

    public static void save(final ControllerCalibration calibration) {
        if (calibration == null) {
            return;
        }

        final Path path = resolvePath();
        if (path == null) {
            return;
        }

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, calibration.save().toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static Path resolvePath() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) {
            return null;
        }
        return minecraft.gameDirectory.toPath()
            .resolve("config")
            .resolve("fullfud")
            .resolve(FILE_NAME);
    }
}
