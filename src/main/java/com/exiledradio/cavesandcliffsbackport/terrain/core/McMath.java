package com.exiledradio.cavesandcliffsbackport.terrain.core;

/** The parts of 1.18.2's Mth the carvers and features use, bit for bit (sin and cos from its 65,536-entry table). */
public final class McMath {
    private McMath() {
    }

    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; ++i) {
            SIN[i] = (float) Math.sin((double) i * Math.PI * 2.0D / 65536.0D);
        }
    }

    public static float sin(float v) {
        return SIN[(int) (v * 10430.378F) & 65535];
    }

    public static float cos(float v) {
        return SIN[(int) (v * 10430.378F + 16384.0F) & 65535];
    }

    /** 1.21.11's Mth.sin(double): the same table, indexed from the double with a double factor. */
    public static float sin121(double v) {
        return SIN[(int) ((long) (v * 10430.378350470453D) & 65535L)];
    }

    /** 1.21.11's Mth.cos(double). */
    public static float cos121(double v) {
        return SIN[(int) ((long) (v * 10430.378350470453D + 16384.0D) & 65535L)];
    }

    /** Mth.sin of a float by the feature rules: 1.21 has only Mth.sin(double), which a float widens to. */
    static float sin(int rules, float v) {
        return rules >= Rules.V121 ? sin121(v) : sin(v);
    }

    /** Mth.cos of a float by the feature rules. */
    static float cos(int rules, float v) {
        return rules >= Rules.V121 ? cos121(v) : cos(v);
    }

    /** A hash of the table, to compare with vanilla's (the oracle writes the same hash of Mth.SIN). */
    public static long sinTableHash() {
        long h = 1L;
        for (float f : SIN) {
            h = h * 31L + Float.floatToIntBits(f);
        }
        return h;
    }

    public static int floor(double v) {
        int i = (int) v;
        return v < (double) i ? i - 1 : i;
    }

    public static int floor(float v) {
        int i = (int) v;
        return v < (float) i ? i - 1 : i;
    }

    public static int ceil(double v) {
        int i = (int) v;
        return v > (double) i ? i + 1 : i;
    }

    public static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    public static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    public static double clampedLerp(double a, double b, double t) {
        if (t < 0.0D) {
            return a;
        }
        return t > 1.0D ? b : lerp(t, a, b);
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }

    public static double map(double v, double fromA, double fromB, double toA, double toB) {
        return lerp((v - fromA) / (fromB - fromA), toA, toB);
    }

    public static double clampedMap(double v, double fromA, double fromB, double toA, double toB) {
        return clampedLerp(toA, toB, (v - fromA) / (fromB - fromA));
    }

    /** Mth.clamp(float). */
    public static float clamp(float v, float min, float max) {
        if (v < min) {
            return min;
        }
        return v > max ? max : v;
    }

    /** Mth.clampedLerp(float). */
    public static float clampedLerp(float a, float b, float t) {
        if (t < 0.0F) {
            return a;
        }
        return t > 1.0F ? b : lerp(t, a, b);
    }

    /** Mth.clampedMap(float). */
    public static float clampedMap(float v, float fromA, float fromB, float toA, float toB) {
        return clampedLerp(toA, toB, (v - fromA) / (fromB - fromA));
    }

    /** Mth.sqrt(float). */
    public static float sqrt(float v) {
        return (float) Math.sqrt((double) v);
    }
}
