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
}
