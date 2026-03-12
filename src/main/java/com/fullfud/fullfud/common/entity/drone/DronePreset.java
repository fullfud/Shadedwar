package com.fullfud.fullfud.common.entity.drone;

public enum DronePreset {
    STANDARD_5INCH(
        "5 Inch 4S",
        2400.0f,
        22.0f,
        7.0f,
        4,
        1300,
        5.0f,
        4.6f,
        3,
        500.0f,
        30.0f,
        30.0f,
        200.0f,
        11.0f,
        15.0f,
        5.0f,
        17.0f,
        1.15f, 0.67f, 0.0f,
        1.15f, 0.67f, 0.0f,
        1.15f, 0.67f, 0.0f,
        false
    ),
    TINY_WHOOP(
        "Tiny Whoop",
        13000.0f,
        8.0f,
        2.5f,
        1,
        450,
        1.5f,
        1.5f,
        4,
        111.7263f,
        25.0f,
        25.0f,
        25.0f,
        8.0f,
        5.0f,
        5.0f,
        17.0f,
        1.15f, 0.67f, 0.0f,
        1.15f, 0.67f, 0.0f,
        1.15f, 0.67f, 0.0f,
        false
    ),
    STRIKE_7INCH(
        "7 Inch 6S Strike",
        1300.0f,
        28.0f,
        6.5f,
        6,
        1800,
        7.0f,
        3.5f,
        2,
        1800.0f,
        35.0f,
        40.0f,
        300.0f,
        14.0f,
        20.0f,
        6.0f,
        20.0f,
        1.15f, 0.67f, 0.0f,
        1.15f, 0.67f, 0.0f,
        1.15f, 0.67f, 0.0f,
        false
    );

    public final String displayName;
    public final float motorKv;
    public final float motorWidthMm;
    public final float motorHeightMm;
    public final int batteryCells;
    public final int batteryMah;
    public final float propDiameterInch;
    public final float propPitchInch;
    public final int blades;
    public final float massGrams;
    public final float frameWidthMm;
    public final float frameHeightMm;
    public final float frameLengthMm;
    public final float propWidthMm;
    public final float armWidthMm;
    public final float armThicknessMm;
    public final float antennaLengthMm;
    public final float rollRate;
    public final float rollSuper;
    public final float rollExpo;
    public final float pitchRate;
    public final float pitchSuper;
    public final float pitchExpo;
    public final float yawRate;
    public final float yawSuper;
    public final float yawExpo;
    public final boolean flightMode3d;

    DronePreset(
        final String displayName,
        final float motorKv,
        final float motorWidthMm,
        final float motorHeightMm,
        final int batteryCells,
        final int batteryMah,
        final float propDiameterInch,
        final float propPitchInch,
        final int blades,
        final float massGrams,
        final float frameWidthMm,
        final float frameHeightMm,
        final float frameLengthMm,
        final float propWidthMm,
        final float armWidthMm,
        final float armThicknessMm,
        final float antennaLengthMm,
        final float rollRate,
        final float rollSuper,
        final float rollExpo,
        final float pitchRate,
        final float pitchSuper,
        final float pitchExpo,
        final float yawRate,
        final float yawSuper,
        final float yawExpo,
        final boolean flightMode3d
    ) {
        this.displayName = displayName;
        this.motorKv = motorKv;
        this.motorWidthMm = motorWidthMm;
        this.motorHeightMm = motorHeightMm;
        this.batteryCells = batteryCells;
        this.batteryMah = batteryMah;
        this.propDiameterInch = propDiameterInch;
        this.propPitchInch = propPitchInch;
        this.blades = blades;
        this.massGrams = massGrams;
        this.frameWidthMm = frameWidthMm;
        this.frameHeightMm = frameHeightMm;
        this.frameLengthMm = frameLengthMm;
        this.propWidthMm = propWidthMm;
        this.armWidthMm = armWidthMm;
        this.armThicknessMm = armThicknessMm;
        this.antennaLengthMm = antennaLengthMm;
        this.rollRate = rollRate;
        this.rollSuper = rollSuper;
        this.rollExpo = rollExpo;
        this.pitchRate = pitchRate;
        this.pitchSuper = pitchSuper;
        this.pitchExpo = pitchExpo;
        this.yawRate = yawRate;
        this.yawSuper = yawSuper;
        this.yawExpo = yawExpo;
        this.flightMode3d = flightMode3d;
    }

    public static DronePreset fromOrdinal(final int ordinal) {
        final DronePreset[] values = values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return STANDARD_5INCH;
    }
}
