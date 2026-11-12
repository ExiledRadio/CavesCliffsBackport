package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * 1.18.2's decoration random: a WorldgenRandom over a XoroshiroRandomSource. WorldgenRandom extends
 * java.util.Random and overrides only next(bits), which for a non-legacy source is the Xoroshiro128++ nextLong
 * shifted down, so every other method (nextInt(bound), nextFloat, nextDouble, nextLong, nextBoolean) is
 * java.util.Random's algorithm on top of it. setSeed reseeds the Xoroshiro state as RandomSupport
 * .upgradeSeedTo128bit does (Xoroshiro.ofSeed).
 */
public final class XoroRandom {
    private static final long GOLDEN_RATIO_64 = -7046029254386353131L;
    private static final long SILVER_RATIO_64 = 7640891576956012809L;

    private long lo;
    private long hi;

    public XoroRandom(long seed) {
        setSeed(seed);
    }

    public void setSeed(long seed) {
        long i = seed ^ SILVER_RATIO_64;
        long j = i + GOLDEN_RATIO_64;
        lo = mix(i);
        hi = mix(j);
        if ((lo | hi) == 0L) {
            lo = GOLDEN_RATIO_64;
            hi = SILVER_RATIO_64;
        }
    }

    private static long mix(long v) {
        v = (v ^ v >>> 30) * -4658895280553007687L;
        v = (v ^ v >>> 27) * -7723592293110705685L;
        return v ^ v >>> 31;
    }

    private long nextRaw() {
        long i = lo;
        long j = hi;
        long k = Long.rotateLeft(i + j, 17) + i;
        j ^= i;
        lo = Long.rotateLeft(i, 49) ^ j ^ j << 21;
        hi = Long.rotateLeft(j, 28);
        return k;
    }

    /** WorldgenRandom.next. */
    public int next(int bits) {
        return (int) (nextRaw() >>> 64 - bits);
    }

    public int nextInt() {
        return next(32);
    }

    public int nextInt(int bound) {
        int r = next(31);
        int m = bound - 1;
        if ((bound & m) == 0) {
            return (int) ((long) bound * (long) r >> 31);
        }
        for (int u = r; u - (r = u % bound) + m < 0; u = next(31)) {
        }
        return r;
    }

    public long nextLong() {
        return ((long) next(32) << 32) + (long) next(32);
    }

    public boolean nextBoolean() {
        return next(1) != 0;
    }

    public float nextFloat() {
        return (float) next(24) / (float) (1 << 24);
    }

    public double nextDouble() {
        return (double) (((long) next(26) << 27) + (long) next(27)) * 0x1.0p-53;
    }

    private double nextNextGaussian;
    private boolean haveNextNextGaussian;

    /**
     * java.util.Random.nextGaussian (WorldgenRandom does not override it): the polar method with StrictMath, its
     * second value kept for the next call. WorldgenRandom.setSeed does not call Random.setSeed, so the kept value
     * survives setFeatureSeed and setDecorationSeed; only a new WorldgenRandom (one per chunk's decoration) starts
     * without one: {@link #newRandom}.
     */
    public double nextGaussian() {
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        }
        double v1;
        double v2;
        double s;
        do {
            v1 = 2 * nextDouble() - 1;
            v2 = 2 * nextDouble() - 1;
            s = v1 * v1 + v2 * v2;
        } while (s >= 1 || s == 0);
        double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s) / s);
        nextNextGaussian = v2 * multiplier;
        haveNextNextGaussian = true;
        return v1 * multiplier;
    }

    /** As a freshly constructed WorldgenRandom: no kept Gaussian. */
    public void newRandom() {
        haveNextNextGaussian = false;
    }

    /** Mth.normal. */
    public float normal(float mean, float deviation) {
        return mean + (float) nextGaussian() * deviation;
    }

    /** Mth.randomBetweenInclusive. */
    public int between(int min, int max) {
        return nextInt(max - min + 1) + min;
    }

    /** Mth.randomBetween. */
    public float between(float min, float max) {
        return nextFloat() * (max - min) + min;
    }

    /** WorldgenRandom.setDecorationSeed: returns the seed set. */
    public long setDecorationSeed(long worldSeed, int x, int z) {
        setSeed(worldSeed);
        long i = nextLong() | 1L;
        long j = nextLong() | 1L;
        long k = (long) x * i + (long) z * j ^ worldSeed;
        setSeed(k);
        return k;
    }

    /** WorldgenRandom.setFeatureSeed. */
    public void setFeatureSeed(long decorationSeed, int index, int step) {
        setSeed(decorationSeed + (long) index + (long) (10000 * step));
    }
}
