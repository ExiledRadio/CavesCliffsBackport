package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.Random;

/**
 * 1.18.2's PerlinSimplexNoise (2D only) for a contiguous octave list firstOctave..0, seeded the way Biome's static
 * noises are: new WorldgenRandom(new LegacyRandomSource(seed)), which draws exactly like java.util.Random.
 */
final class PerlinSimplexNoise {
    private final SimplexNoise[] levels;
    private final double highestFreqInputFactor;
    private final double highestFreqValueFactor;

    PerlinSimplexNoise(long seed, int firstOctave, int lastOctave) {
        if (lastOctave != 0 || firstOctave > 0) {
            throw new IllegalArgumentException("only octaves firstOctave..0 are ported");
        }
        Random random = new Random(seed);
        int k = -firstOctave + 1;
        levels = new SimplexNoise[k];
        levels[0] = new SimplexNoise(random);
        for (int i = 1; i < k; i++) {
            levels[i] = new SimplexNoise(random);
        }
        highestFreqInputFactor = Math.pow(2.0D, 0.0D);
        highestFreqValueFactor = 1.0D / (Math.pow(2.0D, (double) k) - 1.0D);
    }

    /** getValue(x, y, false). */
    double getValue(double x, double y) {
        double d0 = 0.0D;
        double d1 = highestFreqInputFactor;
        double d2 = highestFreqValueFactor;
        for (SimplexNoise noise : levels) {
            d0 += noise.getValue(x * d1, y * d1) * d2;
            d1 /= 2.0D;
            d2 *= 2.0D;
        }
        return d0;
    }

    /** SimplexNoise, the 2D half. */
    static final class SimplexNoise {
        private static final int[][] GRADIENT = {{1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1},
            {1, 0, -1}, {-1, 0, -1}, {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}, {1, 1, 0}, {0, -1, 1}, {-1, 1, 0},
            {0, -1, -1}};
        private static final double SQRT_3 = Math.sqrt(3.0D);
        private static final double F2 = 0.5D * (SQRT_3 - 1.0D);
        private static final double G2 = (3.0D - SQRT_3) / 6.0D;
        private final int[] p = new int[512];
        final double xo;
        final double yo;
        final double zo;

        SimplexNoise(Random random) {
            xo = random.nextDouble() * 256.0D;
            yo = random.nextDouble() * 256.0D;
            zo = random.nextDouble() * 256.0D;
            for (int i = 0; i < 256; i++) {
                p[i] = i;
            }
            for (int l = 0; l < 256; ++l) {
                int j = random.nextInt(256 - l);
                int k = p[l];
                p[l] = p[j + l];
                p[j + l] = k;
            }
        }

        private int p(int i) {
            return p[i & 255];
        }

        private static double corner(int g, double x, double y, double z, double r) {
            double d1 = r - x * x - y * y - z * z;
            if (d1 < 0.0D) {
                return 0.0D;
            }
            d1 *= d1;
            int[] grad = GRADIENT[g];
            return d1 * d1 * ((double) grad[0] * x + (double) grad[1] * y + (double) grad[2] * z);
        }

        double getValue(double x, double y) {
            double d0 = (x + y) * F2;
            int i = floor(x + d0);
            int j = floor(y + d0);
            double d1 = (double) (i + j) * G2;
            double d2 = (double) i - d1;
            double d3 = (double) j - d1;
            double d4 = x - d2;
            double d5 = y - d3;
            int k;
            int l;
            if (d4 > d5) {
                k = 1;
                l = 0;
            } else {
                k = 0;
                l = 1;
            }
            double d6 = d4 - (double) k + G2;
            double d7 = d5 - (double) l + G2;
            double d8 = d4 - 1.0D + 2.0D * G2;
            double d9 = d5 - 1.0D + 2.0D * G2;
            int i1 = i & 255;
            int j1 = j & 255;
            int k1 = p(i1 + p(j1)) % 12;
            int l1 = p(i1 + k + p(j1 + l)) % 12;
            int i2 = p(i1 + 1 + p(j1 + 1)) % 12;
            double d10 = corner(k1, d4, d5, 0.0D, 0.5D);
            double d11 = corner(l1, d6, d7, 0.0D, 0.5D);
            double d12 = corner(i2, d8, d9, 0.0D, 0.5D);
            return 70.0D * (d10 + d11 + d12);
        }

        private static int floor(double v) {
            int i = (int) v;
            return v < (double) i ? i - 1 : i;
        }
    }
}
