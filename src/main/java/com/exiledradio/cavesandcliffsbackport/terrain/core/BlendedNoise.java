package com.exiledradio.cavesandcliffsbackport.terrain.core;

/** 1.18's BlendedNoise for the overworld (the "base 3D noise"): xz and y scale 1, factors 80 and 160, cells 4x8x4. */
public final class BlendedNoise {
    private static final int CELL_WIDTH = 4;
    private static final int CELL_HEIGHT = 8;

    private final ImprovedNoise[] minLimit = new ImprovedNoise[16];
    private final ImprovedNoise[] maxLimit = new ImprovedNoise[16];
    private final ImprovedNoise[] main = new ImprovedNoise[8];
    private final double xzScale;
    private final double yScale;
    private final double xzMainScale;
    private final double yMainScale;
    private final double maxValue;

    public BlendedNoise(Xoroshiro random) {
        OctaveNoise min = OctaveNoise.createLegacy(random, -15);
        OctaveNoise max = OctaveNoise.createLegacy(random, -15);
        OctaveNoise mainNoise = OctaveNoise.createLegacy(random, -7);
        for (int i = 0; i < 16; i++) {
            minLimit[i] = min.octave(i);
            maxLimit[i] = max.octave(i);
        }
        for (int i = 0; i < 8; i++) {
            main[i] = mainNoise.octave(i);
        }
        xzScale = 684.412D * 1.0D;
        yScale = 684.412D * 1.0D;
        xzMainScale = xzScale / 80.0D;
        yMainScale = yScale / 160.0D;
        maxValue = min.maxBrokenValue(yScale);
    }

    public double maxValue() {
        return maxValue;
    }

    public double compute(int blockX, int blockY, int blockZ) {
        int i = Math.floorDiv(blockX, CELL_WIDTH);
        int j = Math.floorDiv(blockY, CELL_HEIGHT);
        int k = Math.floorDiv(blockZ, CELL_WIDTH);
        double d0 = 0.0D;
        double d1 = 0.0D;
        double d2 = 0.0D;
        double d3 = 1.0D;
        for (int l = 0; l < 8; l++) {
            d2 += main[l].noise(OctaveNoise.wrap((double) i * xzMainScale * d3),
                OctaveNoise.wrap((double) j * yMainScale * d3), OctaveNoise.wrap((double) k * xzMainScale * d3),
                yMainScale * d3, (double) j * yMainScale * d3) / d3;
            d3 /= 2.0D;
        }
        double d8 = (d2 / 10.0D + 1.0D) / 2.0D;
        boolean onlyMax = d8 >= 1.0D;
        boolean onlyMin = d8 <= 0.0D;
        d3 = 1.0D;
        for (int i1 = 0; i1 < 16; i1++) {
            double d4 = OctaveNoise.wrap((double) i * xzScale * d3);
            double d5 = OctaveNoise.wrap((double) j * yScale * d3);
            double d6 = OctaveNoise.wrap((double) k * xzScale * d3);
            double d7 = yScale * d3;
            if (!onlyMax) {
                d0 += minLimit[i1].noise(d4, d5, d6, d7, (double) j * d7) / d3;
            }
            if (!onlyMin) {
                d1 += maxLimit[i1].noise(d4, d5, d6, d7, (double) j * d7) / d3;
            }
            d3 /= 2.0D;
        }
        double a = d0 / 512.0D;
        double b = d1 / 512.0D;
        double lerped = d8 < 0.0D ? a : d8 > 1.0D ? b : a + d8 * (b - a);
        return lerped / 128.0D;
    }
}
