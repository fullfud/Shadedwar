package com.fullfud.fullfud.client.input;

import net.minecraft.nbt.CompoundTag;

/**
 * Stores a locally calibrated controller profile:
 * physical axis bindings, optional arm binding, inversion, and axis ranges.
 */
public class ControllerCalibration {

    public static final int AXIS_ROLL = 0;
    public static final int AXIS_PITCH = 1;
    public static final int AXIS_YAW = 2;
    public static final int AXIS_THROTTLE = 3;
    public static final int AXIS_COUNT = 4;

    public static final int NO_ARM_BINDING = Integer.MIN_VALUE;

    private static final String[] AXIS_NAMES = {"Roll", "Pitch", "Yaw", "Throttle"};

    private final float[] axisMin = new float[AXIS_COUNT];
    private final float[] axisMax = new float[AXIS_COUNT];
    private final float[] axisCenter = new float[AXIS_COUNT];
    private final int[] axisMapping = new int[AXIS_COUNT];
    private final boolean[] axisInverted = new boolean[AXIS_COUNT];

    private boolean calibrated;
    private boolean sampling;
    private final float[] sampleMin = new float[AXIS_COUNT];
    private final float[] sampleMax = new float[AXIS_COUNT];

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
        for (int i = 0; i < AXIS_COUNT; i++) {
            axisMin[i] = -1.0F;
            axisMax[i] = 1.0F;
            axisCenter[i] = 0.0F;
            axisMapping[i] = -1;
            axisInverted[i] = false;
            sampleMin[i] = Float.MAX_VALUE;
            sampleMax[i] = -Float.MAX_VALUE;
        }
        calibrated = false;
        sampling = false;
        armBinding = NO_ARM_BINDING;
        armInverted = false;
        controllerName = "";
    }

    public void startCalibration() {
        for (int i = 0; i < AXIS_COUNT; i++) {
            sampleMin[i] = Float.MAX_VALUE;
            sampleMax[i] = -Float.MAX_VALUE;
        }
        sampling = true;
        calibrated = false;
    }

    public void sampleAxes(final float roll, final float pitch, final float yaw, final float throttle) {
        if (!sampling) {
            return;
        }
        final float[] values = {roll, pitch, yaw, throttle};
        for (int i = 0; i < AXIS_COUNT; i++) {
            if (values[i] < sampleMin[i]) {
                sampleMin[i] = values[i];
            }
            if (values[i] > sampleMax[i]) {
                sampleMax[i] = values[i];
            }
        }
    }

    public void recordCenter(final float roll, final float pitch, final float yaw, final float throttle) {
        axisCenter[AXIS_ROLL] = roll;
        axisCenter[AXIS_PITCH] = pitch;
        axisCenter[AXIS_YAW] = yaw;
        axisCenter[AXIS_THROTTLE] = throttle;
    }

    public void finishCalibration() {
        for (int i = 0; i < AXIS_COUNT; i++) {
            if (sampleMin[i] < Float.MAX_VALUE) {
                axisMin[i] = sampleMin[i];
            }
            if (sampleMax[i] > -Float.MAX_VALUE) {
                axisMax[i] = sampleMax[i];
            }
        }
        sampling = false;
        calibrated = hasCompleteAxisMapping();
    }

    public void cancelCalibration() {
        sampling = false;
    }

    public boolean hasCompleteAxisMapping() {
        for (int axis : axisMapping) {
            if (axis < 0) {
                return false;
            }
        }
        return true;
    }

    public boolean hasArmBinding() {
        return armBinding != NO_ARM_BINDING;
    }

    public boolean isReady() {
        return calibrated && hasCompleteAxisMapping() && hasArmBinding();
    }

    public boolean isReadyForController(final String name) {
        return isReady() && matchesController(name);
    }

    public boolean matchesController(final String name) {
        if (controllerName == null || controllerName.isBlank()) {
            return false;
        }
        return controllerName.equals(name == null ? "" : name);
    }

    public void setControllerName(final String name) {
        controllerName = name == null ? "" : name;
    }

    public String getControllerName() {
        return controllerName;
    }

    public void setAxisMapping(final int logicalAxis, final int physicalAxis) {
        if (logicalAxis >= 0 && logicalAxis < AXIS_COUNT) {
            axisMapping[logicalAxis] = Math.max(-1, physicalAxis);
        }
    }

    public int getAxisMapping(final int logicalAxis) {
        return logicalAxis >= 0 && logicalAxis < AXIS_COUNT ? axisMapping[logicalAxis] : -1;
    }

    public void setAxisInverted(final int logicalAxis, final boolean inverted) {
        if (logicalAxis >= 0 && logicalAxis < AXIS_COUNT) {
            axisInverted[logicalAxis] = inverted;
        }
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

    public float readMappedAxis(final float[] rawAxes, final int logicalAxis) {
        final int physicalAxis = getAxisMapping(logicalAxis);
        if (rawAxes == null || physicalAxis < 0 || physicalAxis >= rawAxes.length) {
            return 0.0F;
        }
        final float value = rawAxes[physicalAxis];
        return Float.isFinite(value) ? value : 0.0F;
    }

    public float normalizeMappedAxis(final float[] rawAxes, final int logicalAxis) {
        final float raw = readMappedAxis(rawAxes, logicalAxis);
        float normalized = normalize(logicalAxis, raw);
        if (isAxisInverted(logicalAxis)) {
            normalized = -normalized;
        }
        return normalized;
    }

    public float normalizeMappedThrottle(final float[] rawAxes) {
        final float raw = readMappedAxis(rawAxes, AXIS_THROTTLE);
        float normalized = normalizeThrottle(raw);
        if (isAxisInverted(AXIS_THROTTLE)) {
            normalized = 1.0F - normalized;
        }
        return normalized;
    }

    public float normalize(final int logicalAxis, final float raw) {
        if (logicalAxis < 0 || logicalAxis >= AXIS_COUNT || !calibrated) {
            return raw;
        }
        final float center = axisCenter[logicalAxis];
        final float min = axisMin[logicalAxis];
        final float max = axisMax[logicalAxis];

        if (raw < center) {
            final float range = center - min;
            if (range < 0.01F) {
                return 0.0F;
            }
            return Math.max(-1.0F, (raw - center) / range);
        }

        final float range = max - center;
        if (range < 0.01F) {
            return 0.0F;
        }
        return Math.min(1.0F, (raw - center) / range);
    }

    public float normalizeThrottle(final float raw) {
        if (!calibrated) {
            return (raw + 1.0F) * 0.5F;
        }
        final float min = axisMin[AXIS_THROTTLE];
        final float max = axisMax[AXIS_THROTTLE];
        final float range = max - min;
        if (range < 0.01F) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (raw - min) / range));
    }

    public boolean isCalibrated() {
        return calibrated;
    }

    public boolean isSampling() {
        return sampling;
    }

    public float getAxisMin(final int logicalAxis) {
        return logicalAxis >= 0 && logicalAxis < AXIS_COUNT ? axisMin[logicalAxis] : -1.0F;
    }

    public float getAxisMax(final int logicalAxis) {
        return logicalAxis >= 0 && logicalAxis < AXIS_COUNT ? axisMax[logicalAxis] : 1.0F;
    }

    public float getAxisCenter(final int logicalAxis) {
        return logicalAxis >= 0 && logicalAxis < AXIS_COUNT ? axisCenter[logicalAxis] : 0.0F;
    }

    public float getSampleMin(final int logicalAxis) {
        return logicalAxis >= 0 && logicalAxis < AXIS_COUNT ? sampleMin[logicalAxis] : -1.0F;
    }

    public float getSampleMax(final int logicalAxis) {
        return logicalAxis >= 0 && logicalAxis < AXIS_COUNT ? sampleMax[logicalAxis] : 1.0F;
    }

    public static String getAxisName(final int logicalAxis) {
        return logicalAxis >= 0 && logicalAxis < AXIS_COUNT ? AXIS_NAMES[logicalAxis] : "Unknown";
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("Calibrated", calibrated);
        tag.putString("ControllerName", controllerName);
        tag.putInt("ArmBinding", armBinding);
        tag.putBoolean("ArmInverted", armInverted);
        for (int i = 0; i < AXIS_COUNT; i++) {
            tag.putFloat("Min" + i, axisMin[i]);
            tag.putFloat("Max" + i, axisMax[i]);
            tag.putFloat("Center" + i, axisCenter[i]);
            tag.putInt("Mapping" + i, axisMapping[i]);
            tag.putBoolean("Inverted" + i, axisInverted[i]);
        }
        return tag;
    }

    public void load(final CompoundTag tag) {
        reset();
        if (tag == null) {
            return;
        }
        calibrated = tag.getBoolean("Calibrated");
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
            if (tag.contains("Min" + i)) {
                axisMin[i] = tag.getFloat("Min" + i);
            }
            if (tag.contains("Max" + i)) {
                axisMax[i] = tag.getFloat("Max" + i);
            }
            if (tag.contains("Center" + i)) {
                axisCenter[i] = tag.getFloat("Center" + i);
            }
            if (tag.contains("Mapping" + i)) {
                axisMapping[i] = tag.getInt("Mapping" + i);
            }
            if (tag.contains("Inverted" + i)) {
                axisInverted[i] = tag.getBoolean("Inverted" + i);
            }
        }
        sampling = false;
    }
}
