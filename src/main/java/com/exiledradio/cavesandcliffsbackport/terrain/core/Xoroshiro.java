package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 1.18's Xoroshiro128++ random source and its positional factory, bit for bit. */
public final class Xoroshiro {
    private static final long GOLDEN_RATIO_64 = -7046029254386353131L;
    private static final long SILVER_RATIO_64 = 7640891576956012809L;
    private static final float FLOAT_UNIT = 5.9604645E-8F;
    private static final double DOUBLE_UNIT = (double) 1.110223E-16F;

    private long lo;
    private long hi;

    public Xoroshiro(long lo, long hi) {
        this.lo = lo;
        this.hi = hi;
        if ((lo | hi) == 0L) {
            this.lo = GOLDEN_RATIO_64;
            this.hi = SILVER_RATIO_64;
        }
    }

    public static Xoroshiro ofSeed(long seed) {
        long i = seed ^ SILVER_RATIO_64;
        long j = i + GOLDEN_RATIO_64;
        return new Xoroshiro(mixStafford13(i), mixStafford13(j));
    }

    private static long mixStafford13(long v) {
        v = (v ^ v >>> 30) * -4658895280553007687L;
        v = (v ^ v >>> 27) * -7723592293110705685L;
        return v ^ v >>> 31;
    }

    public long nextLong() {
        long i = lo;
        long j = hi;
        long k = Long.rotateLeft(i + j, 17) + i;
        j ^= i;
        lo = Long.rotateLeft(i, 49) ^ j ^ j << 21;
        hi = Long.rotateLeft(j, 28);
        return k;
    }

    public int nextInt() {
        return (int) nextLong();
    }

    public int nextInt(int bound) {
        long i = nextInt() & 0xFFFFFFFFL;
        long j = i * (long) bound;
        long k = j & 4294967295L;
        if (k < (long) bound) {
            for (int l = Integer.remainderUnsigned(~bound + 1, bound); k < (long) l; k = j & 4294967295L) {
                i = nextInt() & 0xFFFFFFFFL;
                j = i * (long) bound;
            }
        }
        return (int) (j >> 32);
    }

    public float nextFloat() {
        return (float) (nextLong() >>> 40) * FLOAT_UNIT;
    }

    public double nextDouble() {
        return (double) (nextLong() >>> 11) * DOUBLE_UNIT;
    }

    public void skip(int count) {
        for (int i = 0; i < count; i++) {
            nextLong();
        }
    }

    public Positional forkPositional() {
        return new Positional(nextLong(), nextLong());
    }

    /** Mth.getSeed. */
    public static long positionSeed(int x, int y, int z) {
        long i = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        i = i * i * 42317861L + i * 11L;
        return i >> 16;
    }

    public static final class Positional {
        public final long seedLo;
        public final long seedHi;

        public Positional(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
        }

        public Xoroshiro at(int x, int y, int z) {
            return new Xoroshiro(positionSeed(x, y, z) ^ seedLo, seedHi);
        }

        public Xoroshiro fromHashOf(String name) {
            byte[] b;
            try {
                b = MessageDigest.getInstance("MD5").digest(name.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            return new Xoroshiro(bytes(b, 0) ^ seedLo, bytes(b, 8) ^ seedHi);
        }

        private static long bytes(byte[] b, int from) {
            long v = 0L;
            for (int i = 0; i < 8; i++) {
                v = v << 8 | (b[from + i] & 0xFFL);
            }
            return v;
        }
    }
}
