package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * The per-column (2D) values of the noise router, kept by quart position (4x4 blocks), which is the resolution
 * Mojang's flat caches sample them at. They depend only on the seed and the position, so a value computed for
 * one chunk is reused by its neighbors. Direct-mapped: 128x128 quarts (512x512 blocks). One instance per thread.
 */
public final class ColumnCache {
    private static final int BITS = 7;
    private static final int SIZE = 1 << BITS;
    private static final int MASK = SIZE - 1;
    private static final int NO_SURFACE = Integer.MIN_VALUE;

    private final OverworldNoise noise;
    private final long[] keys = new long[SIZE * SIZE];
    private final boolean[] present = new boolean[SIZE * SIZE];
    private final double[] values = new double[SIZE * SIZE * OverworldNoise.COL_SIZE];
    private final int[] surface = new int[SIZE * SIZE];
    private final float[] point = new float[4];

    public ColumnCache(OverworldNoise noise) {
        this.noise = noise;
    }

    private int slot(int qx, int qz) {
        int slot = (qx & MASK) << BITS | (qz & MASK);
        long key = (long) qx << 32 | (qz & 0xFFFFFFFFL);
        if (!present[slot] || keys[slot] != key) {
            noise.column(qx << 2, qz << 2, values, slot * OverworldNoise.COL_SIZE, point);
            keys[slot] = key;
            present[slot] = true;
            surface[slot] = NO_SURFACE;
        }
        return slot;
    }

    /** Copies the column values of the quart holding block x, z into out (OverworldNoise.COL_* indexes). */
    public void get(int x, int z, double[] out) {
        int slot = slot(x >> 2, z >> 2);
        System.arraycopy(values, slot * OverworldNoise.COL_SIZE, out, 0, OverworldNoise.COL_SIZE);
    }

    /** NoiseChunk.preliminarySurfaceLevel. */
    public int preliminarySurfaceLevel(int x, int z) {
        int slot = slot(x >> 2, z >> 2);
        int level = surface[slot];
        if (level == NO_SURFACE) {
            int at = slot * OverworldNoise.COL_SIZE;
            level = OverworldNoise.preliminarySurfaceLevel(values[at + OverworldNoise.COL_OFFSET],
                values[at + OverworldNoise.COL_FACTOR]);
            surface[slot] = level;
        }
        return level;
    }
}
