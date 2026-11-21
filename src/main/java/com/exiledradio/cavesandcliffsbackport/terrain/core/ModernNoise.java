package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * 26.x's NormalNoise (with its default normalization) for the two noises only 26.x's surface rules ask
 * ({@link Editions#GAME_DROPS}): small_patch (the dappled forest's coarse dirt) and sulfur_cave_gradient (the sulfur
 * caves' bands). 26.x rebuilt the noise library: each octave's amplitude comes from the noise's base amplitude and a
 * normalization over the octaves' expected deviation, and the Perlin samples are float. The seeding is 1.18's (two
 * positional forks of the noise's random, each octave by "octave_" + index), so the lattices are the same as
 * {@link ImprovedNoise}'s; the arithmetic below is 26.3's PerlinNoise.get and NoiseStack.get, float for float.
 */
public final class ModernNoise {
    private static final double INPUT_FACTOR = 1.0181268882175227D;
    private static final double HALF_ROUND_OFF = Math.nextDown(1.6777216E7D);
    private static final int[][] GRADIENT = {{1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1},
        {1, 0, -1}, {-1, 0, -1}, {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}, {1, 1, 0}, {0, -1, 1}, {-1, 1, 0},
        {0, -1, -1}};

    /** Two layers per octave (first, second), with their frequencies and float amplitudes. */
    private final Octave[] layers;
    private final double[] frequencies;
    private final float[] amplitudes;

    /**
     * NormalNoise.Parameters: base amplitude, base octave, octave count and amplitude modifiers (empty for all 1).
     *
     * @param random the noise's random (the positional factory's fromHashOf("minecraft:" + name))
     */
    public ModernNoise(Xoroshiro random, double baseAmplitude, int baseOctave, int octaveCount, double... modifiers) {
        int n = 0;
        double[] freq = new double[octaveCount];
        double[] amp = new double[octaveCount];
        int[] index = new int[octaveCount];
        // buildOctaves, normalized.
        double frequency = Math.pow(2.0D, (double) baseOctave);
        double amplitude = baseAmplitude * (Math.pow(0.5D, (double) (-(octaveCount - 1))) / (Math.pow(0.5D, (double) (-octaveCount)) - 1.0D));
        for (int i = 0; i < octaveCount; i++) {
            double m = modifiers.length == 0 ? 1.0D : modifiers[i];
            if (m != 0.0D) {
                freq[n] = frequency;
                amp[n] = amplitude * m;
                index[n] = baseOctave + i;
                n++;
            }
            frequency *= 2.0D;
            amplitude *= 0.5D;
        }
        // computeNormalizationFactor.
        double target = 0.0D;
        double variance = 0.0D;
        for (int i = 0; i < n; i++) {
            target += Math.abs(amp[i]);
            double d = 0.2702247831245211D * Math.abs(amp[i]);
            variance += d * d;
        }
        double deviation = Math.sqrt(variance);
        double factor = deviation == 0.0D ? 0.0D : target * 0.3333333333333333D / (deviation * Math.sqrt(2.0D));
        Xoroshiro.Positional first = random.forkPositional();
        Xoroshiro.Positional second = random.forkPositional();
        layers = new Octave[n * 2];
        frequencies = new double[n * 2];
        amplitudes = new float[n * 2];
        for (int i = 0; i < n; i++) {
            float valueFactor = (float) (factor * amp[i]);
            layers[2 * i] = new Octave(first.fromHashOf("octave_" + index[i]));
            frequencies[2 * i] = freq[i];
            amplitudes[2 * i] = valueFactor;
            layers[2 * i + 1] = new Octave(second.fromHashOf("octave_" + index[i]));
            frequencies[2 * i + 1] = freq[i] * INPUT_FACTOR;
            amplitudes[2 * i + 1] = valueFactor;
        }
    }

    /** NoiseStack.get(x, y, z). */
    public float get(double x, double y, double z) {
        float value = 0.0F;
        for (int i = 0; i < layers.length; i++) {
            double f = frequencies[i];
            value += amplitudes[i] * layers[i].get(x * f, y * f, z * f);
        }
        return value;
    }

    /** 26.3's GradientNoise.wrap. */
    static double wrap(double x) {
        return x >= -HALF_ROUND_OFF && x < HALF_ROUND_OFF ? x : x - Math.floor(x / 3.3554432E7D + 0.5D) * 3.3554432E7D;
    }

    private static float smoothstep(float x) {
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    private static float lerp(float a, float p0, float p1) {
        return p0 + a * (p1 - p0);
    }

    private static float dot(int hash, float x, float y, float z) {
        int[] g = GRADIENT[hash & 15];
        return (float) g[0] * x + (float) g[1] * y + (float) g[2] * z;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < (double) i ? i - 1 : i;
    }

    /** 26.3's PerlinNoise (one octave): GradientNoise's offsets and permutation, as 1.18's ImprovedNoise draws them. */
    static final class Octave {
        final double offsetX;
        final double offsetY;
        final double offsetZ;
        private final int[] perms = new int[256];

        Octave(Xoroshiro random) {
            offsetX = random.nextDouble() * 256.0D;
            offsetY = random.nextDouble() * 256.0D;
            offsetZ = random.nextDouble() * 256.0D;
            byte[] t = new byte[256];
            for (int i = 0; i < 256; i++) {
                t[i] = (byte) i;
            }
            for (int i = 0; i < 256; i++) {
                int offset = random.nextInt(256 - i);
                byte b = t[i];
                t[i] = t[offset + i];
                t[offset + i] = b;
            }
            for (int i = 0; i < 256; i++) {
                perms[i] = t[i] & 255;
            }
        }

        private int permute(int x) {
            return perms[x & 255];
        }

        float get(double px, double py, double pz) {
            double x = wrap(px) + offsetX;
            double y = wrap(py) + offsetY;
            double z = wrap(pz) + offsetZ;
            int fx = floor(x);
            int fy = floor(y);
            int fz = floor(z);
            float rx = (float) (x - (double) fx);
            float ry = (float) (y - (double) fy);
            float rz = (float) (z - (double) fz);
            int x0 = permute(fx);
            int x1 = permute(fx + 1);
            int xy00 = permute(x0 + fy);
            int xy01 = permute(x0 + fy + 1);
            int xy10 = permute(x1 + fy);
            int xy11 = permute(x1 + fy + 1);
            float d000 = dot(permute(xy00 + fz), rx, ry, rz);
            float d100 = dot(permute(xy10 + fz), rx - 1.0F, ry, rz);
            float d010 = dot(permute(xy01 + fz), rx, ry - 1.0F, rz);
            float d110 = dot(permute(xy11 + fz), rx - 1.0F, ry - 1.0F, rz);
            float d001 = dot(permute(xy00 + fz + 1), rx, ry, rz - 1.0F);
            float d101 = dot(permute(xy10 + fz + 1), rx - 1.0F, ry, rz - 1.0F);
            float d011 = dot(permute(xy01 + fz + 1), rx, ry - 1.0F, rz - 1.0F);
            float d111 = dot(permute(xy11 + fz + 1), rx - 1.0F, ry - 1.0F, rz - 1.0F);
            float ax = smoothstep(rx);
            float ay = smoothstep(ry);
            float az = smoothstep(rz);
            return lerp(az, lerp(ay, lerp(ax, d000, d100), lerp(ax, d010, d110)),
                lerp(ay, lerp(ax, d001, d101), lerp(ax, d011, d111)));
        }
    }
}
