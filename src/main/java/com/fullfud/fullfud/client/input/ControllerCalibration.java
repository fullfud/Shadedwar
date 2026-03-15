package com.fullfud.fullfud.client.input;

import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;
import java.util.Locale;

/**
 * Stores the locally selected controller, manual channel mapping, inversion flags,
 * and physical axis ranges used to normalize controller input.
 */
public class ControllerCalibration {

    public static final int AXIS_ROLL = 0;
    public static final int AXIS_PITCH = 1;
    public static final int AXIS_YAW = 2;
    public static final int AXIS_THROTTLE = 3;
    public static final int AXIS_COUNT = 4;

    public static final int NO_ARM_BINDING = Integer.MIN_VALUE;

    private static final String[] AXIS_NAMES = {"Roll", "Pitch", "Yaw", "Throttle"};

    private final int[] axisMapping = new int[AXIS_COUNT];
    private final boolean[] axisInverted = new boolean[AXIS_COUNT];

    private float[] rangeMin = new float[0];
    private float[] rangeMax = new float[0];
    private float[] sampleMin = new float[0];
    private float[] sampleMax = new float[0];
    private boolean sampling;

    private int armBinding = NO_ARM_BINDING;
    private boolean armInverted;
    private String controllerName = "";

    public ControllerCalibration() {
        reset();
    }

    public ControllerCalibration copy() {
        final ControllerCalibration copy = new ControllerCalibration();
        copy.load(this.save());
        return copy;
    }

    public void copyFrom(final ControllerCalibration other) {
        if (other == null) {
            reset();
            return;
        }
        load(other.save());
    }

    public void reset() {
        Arrays.fill(axisMapping, -1);
        Arrays.fill(axisInverted, false);
        rangeMin = new float[0];
        rangeMax = new float[0];
        sampleMin = new float[0];
        sampleMax = new float[0];
        sampling = false;
        armBinding = NO_ARM_BINDING;
        armInverted = false;
        controllerName = "";
    }

    public void setControllerName(final String name) {
        controllerName = name == null ? "" : name;
    }

    public String getControllerName() {
        return controllerName;
    }

    public void setAxisMapping(final int logicalAxis, final int physicalAxis) {
        if (logicalAxis < 0 || logicalAxis >= AXIS_COUNT) {
            return;
        }
        axisMapping[logicalAxis] = Math.max(-1, physicalAxis);
    }

    public int getAxisMapping(final int logicalAxis) {
        return logicalAxis >= 0 && logicalAxis < AXIS_COUNT ? axisMapping[logicalAxis] : -1;
    }

    public void setAxisInverted(final int logicalAxis, final boolean inverted) {
        if (logicalAxis < 0 || logicalAxis >= AXIS_COUNT) {
            return;
        }
        axisInverted[logicalAxis] = inverted;
    }

    public boolean isAxisInverted(final int logicalAxis) {
        return logicalAxis >= 0 && logicalAxis < AXIS_COUNT && axisInverted[logicalAxis];
    }

    public void setArmBinding(final int binding, final boolean inverted) {
        armBinding = binding;
        armInverted = inverted;
    }

    public int getArmBinding() {
        return armBinding;
    }

    public boolean hasArmBinding() {
        return armBinding != NO_ARM_BINDING;
    }

    public boolean isArmBindingAxis() {
        return armBinding < 0 && armBinding != NO_ARM_BINDING;
    }

    public int getArmButtonIndex() {
        return armBinding >= 0 ? armBinding : -1;
    }

    public int getArmAxisIndex() {
        return isArmBindingAxis() ? (-armBinding - 1) : -1;
    }

    public boolean isArmInverted() {
        return armInverted;
    }

    public boolean hasCompleteAxisMapping() {
        for (final int mapping : axisMapping) {
            if (mapping < 0) {
                return false;
            }
        }
        return true;
    }

    public boolean hasRangeCalibration() {
        return rangeMin.length > 0 && rangeMin.length == rangeMax.length;
    }

    public int getRangeAxisCount() {
        return rangeMin.length;
    }

    public boolean hasRangeForAxis(final int physicalAxis) {
        if (physicalAxis < 0 || physicalAxis >= rangeMin.length || physicalAxis >= rangeMax.length) {
            return false;
        }
        return (rangeMax[physicalAxis] - rangeMin[physicalAxis]) > 1.0E-4F;
    }

    public boolean isReady() {
        if (!hasCompleteAxisMapping() || !hasArmBinding()) {
            return false;
        }
        for (final int mapping : axisMapping) {
            if (!hasRangeForAxis(mapping)) {
                return false;
            }
        }
        return true;
    }

    public boolean matchesController(final String name) {
        if (controllerName == null || controllerName.isBlank() || name == null || name.isBlank()) {
            return false;
        }
        if (controllerName.equalsIgnoreCase(name)) {
            return true;
        }
        return normalizeControllerName(controllerName).equals(normalizeControllerName(name));
    }

    public boolean isReadyForController(final String name) {
        return isReady() && matchesController(name);
    }

    public void startRangeSampling(final int axisCount, final float[] currentAxes) {
        if (axisCount <= 0) {
            sampleMin = new float[0];
            sampleMax = new float[0];
            sampling = false;
            return;
        }
        sampleMin = new float[axisCount];
        sampleMax = new float[axisCount];
        for (int i = 0; i < axisCount; i++) {
            final float value = currentAxes != null && i < currentAxes.length && Float.isFinite(currentAxes[i])
                ? currentAxes[i]
                : 0.0F;
            sampleMin[i] = value;
            sampleMax[i] = value;
        }
        sampling = true;
    }

    public void sampleAxes(final float[] rawAxes) {
        if (!sampling || rawAxes == null) {
            return;
        }
        final int count = Math.min(rawAxes.length, sampleMin.length);
        for (int i = 0; i < count; i++) {
            final float value = Float.isFinite(rawAxes[i]) ? rawAxes[i] : 0.0F;
            if (value < sampleMin[i]) {
                sampleMin[i] = value;
            }
            if (value > sampleMax[i]) {
                sampleMax[i] = value;
            }
        }
    }

    public void finishRangeSampling() {
        if (!sampling) {
            return;
        }
        rangeMin = sampleMin.clone();
        rangeMax = sampleMax.clone();
        sampling = false;
    }

    public void cancelRangeSampling() {
        sampling = false;
    }

    public boolean isSampling() {
        return sampling;
    }

    public float readMappedAxis(final float[] rawAxes, final int logicalAxis) {
        final int physicalAxis = getAxisMapping(logicalAxis);
        if (rawAxes == null || physicalAxis < 0 || physicalAxis >= rawAxes.length) {
            return 0.0F;
        }
        final float value = rawAxes[physicalAxis];
        return Float.isFinite(value) ? value : 0.0F;
    }

    public float normalizeMappedAxis(final float[] rawAxes, final int logicalAxis) {
        final int physicalAxis = getAxisMapping(logicalAxis);
        final float raw = readMappedAxis(rawAxes, logicalAxis);
        float normalized = normalizePhysicalAxis(raw, physicalAxis);
        if (isAxisInverted(logicalAxis)) {
            normalized = -normalized;
        }
        return normalized;
    }

    public float normalizeMappedThrottle(final float[] rawAxes) {
        float value = normalizeMappedAxis(rawAxes, AXIS_THROTTLE);
        return Math.max(0.0F, Math.min(1.0F, (value + 1.0F) * 0.5F));
    }

    public float getRangeMin(final int physicalAxis) {
        if (physicalAxis < 0 || physicalAxis >= rangeMin.length) {
            return -1.0F;
        }
        return rangeMin[physicalAxis];
    }

    public float getRangeMax(final int physicalAxis) {
        if (physicalAxis < 0 || physicalAxis >= rangeMax.length) {
            return 1.0F;
        }
        return rangeMax[physicalAxis];
    }

    public float getSampleMin(final int physicalAxis) {
        if (physicalAxis < 0 || physicalAxis >= sampleMin.length) {
            return -1.0F;
        }
        return sampleMin[physicalAxis];
    }

    public float getSampleMax(final int physicalAxis) {
        if (physicalAxis < 0 || physicalAxis >= sampleMax.length) {
            return 1.0F;
        }
        return sampleMax[physicalAxis];
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("ControllerName", controllerName);
        tag.putInt("ArmBinding", armBinding);
        tag.putBoolean("ArmInverted", armInverted);
        for (int i = 0; i < AXIS_COUNT; i++) {
            tag.putInt("Mapping" + i, axisMapping[i]);
            tag.putBoolean("Inverted" + i, axisInverted[i]);
        }
        tag.putInt("RangeAxisCount", rangeMin.length);
        for (int i = 0; i < rangeMin.length; i++) {
            tag.putFloat("RangeMin" + i, rangeMin[i]);
            tag.putFloat("RangeMax" + i, rangeMax[i]);
        }
        return tag;
    }

    public void load(final CompoundTag tag) {
        reset();
        if (tag == null) {
            return;
        }

        if (tag.contains("ControllerName")) {
            controllerName = tag.getString("ControllerName");
        }
        if (tag.contains("ArmBinding")) {
            armBinding = tag.getInt("ArmBinding");
        }
        if (tag.contains("ArmInverted")) {
            armInverted = tag.getBoolean("ArmInverted");
        }
        for (int i = 0; i < AXIS_COUNT; i++) {
            if (tag.contains("Mapping" + i)) {
                axisMapping[i] = tag.getInt("Mapping" + i);
            }
            if (tag.contains("Inverted" + i)) {
                axisInverted[i] = tag.getBoolean("Inverted" + i);
            }
        }

        if (tag.contains("RangeAxisCount")) {
            final int axisCount = Math.max(0, tag.getInt("RangeAxisCount"));
            rangeMin = new float[axisCount];
            rangeMax = new float[axisCount];
            for (int i = 0; i < axisCount; i++) {
                rangeMin[i] = tag.contains("RangeMin" + i) ? tag.getFloat("RangeMin" + i) : -1.0F;
                rangeMax[i] = tag.contains("RangeMax" + i) ? tag.getFloat("RangeMax" + i) : 1.0F;
            }
            return;
        }

        // Backward compatibility with the old logical-axis calibration format.
        int maxMappedAxis = -1;
        for (final int mapping : axisMapping) {
            maxMappedAxis = Math.max(maxMappedAxis, mapping);
        }
        if (maxMappedAxis < 0) {
            return;
        }

        rangeMin = new float[maxMappedAxis + 1];
        rangeMax = new float[maxMappedAxis + 1];
        Arrays.fill(rangeMin, -1.0F);
        Arrays.fill(rangeMax, 1.0F);
        for (int logicalAxis = 0; logicalAxis < AXIS_COUNT; logicalAxis++) {
            final int physicalAxis = axisMapping[logicalAxis];
            if (physicalAxis < 0 || physicalAxis >= rangeMin.length) {
                continue;
            }
            if (tag.contains("Min" + logicalAxis)) {
                rangeMin[physicalAxis] = tag.getFloat("Min" + logicalAxis);
            }
            if (tag.contains("Max" + logicalAxis)) {
                rangeMax[physicalAxis] = tag.getFloat("Max" + logicalAxis);
            }
        }
    }

    public static String getAxisName(final int logicalAxis) {
        return logicalAxis >= 0 && logicalAxis < AXIS_COUNT ? AXIS_NAMES[logicalAxis] : "Unknown";
    }

    private float normalizePhysicalAxis(final float raw, final int physicalAxis) {
        if (!Float.isFinite(raw)) {
            return 0.0F;
        }
        if (!hasRangeForAxis(physicalAxis)) {
            return Math.max(-1.0F, Math.min(1.0F, raw));
        }

        final float min = rangeMin[physicalAxis];
        final float max = rangeMax[physicalAxis];
        if (raw <= min) {
            return -1.0F;
        }
        if (raw >= max) {
            return 1.0F;
        }
        return ((raw - min) / (max - min)) * 2.0F - 1.0F;
    }

    private static String normalizeControllerName(final String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]+", "");
    }
}
