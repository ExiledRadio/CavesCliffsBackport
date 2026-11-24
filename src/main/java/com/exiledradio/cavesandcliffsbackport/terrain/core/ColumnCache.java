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

    // ---- blending (an upgraded world's chunks near its old ones) ----

    private static final int BLEND_BITS = 5;
    private static final int BLEND_SIZE = 1 << BLEND_BITS;
    private static final int BLEND_MASK = BLEND_SIZE - 1;
    private Blending.Blender blender;
    /** Bumped whenever the blender changes; 0 while none is set ({@link BiomeSource} keys its columns with it). */
    private int generation;
    private int nextGeneration = 1;
    private final long[] blendKeys = new long[BLEND_SIZE * BLEND_SIZE];
    private final boolean[] blendPresent = new boolean[BLEND_SIZE * BLEND_SIZE];
    private final double[] blendValues = new double[BLEND_SIZE * BLEND_SIZE * OverworldNoise.COL_SIZE];
    private final int[] blendSurface = new int[BLEND_SIZE * BLEND_SIZE];
    private final double[] blendOut = new double[2];

    /**
     * While a chunk with a Blender is produced (null after): every column value is the blended one, as 1.18's
     * NoiseChunk wraps BlendAlpha and BlendOffset for every density function it computes for that chunk.
     */
    public void setBlender(Blending.Blender blender) {
        if (blender == this.blender) {
            return;
        }
        this.blender = blender;
        this.generation = blender == null ? 0 : nextGeneration++;
        if (nextGeneration == Integer.MAX_VALUE) {
            nextGeneration = 1;
        }
        java.util.Arrays.fill(blendPresent, false);
    }

    /** The current blender's generation (0 for none). */
    public int generation() {
        return generation;
    }

    private int blendSlot(int qx, int qz) {
        int slot = (qx & BLEND_MASK) << BLEND_BITS | (qz & BLEND_MASK);
        long key = (long) qx << 32 | (qz & 0xFFFFFFFFL);
        if (!blendPresent[slot] || blendKeys[slot] != key) {
            int from = slot(qx, qz) * OverworldNoise.COL_SIZE;
            int at = slot * OverworldNoise.COL_SIZE;
            System.arraycopy(values, from, blendValues, at, OverworldNoise.COL_SIZE);
            blender.blendOffsetAndFactor(qx << 2, qz << 2, blendOut);
            noise.blend(blendValues, at, blendOut[0], blendOut[1]);
            blendKeys[slot] = key;
            blendPresent[slot] = true;
            blendSurface[slot] = NO_SURFACE;
        }
        return slot;
    }

    /** Copies the column values of the quart holding block x, z into out (OverworldNoise.COL_* indexes). */
    public void get(int x, int z, double[] out) {
        if (blender != null) {
            int slot = blendSlot(x >> 2, z >> 2);
            System.arraycopy(blendValues, slot * OverworldNoise.COL_SIZE, out, 0, OverworldNoise.COL_SIZE);
            return;
        }
        int slot = slot(x >> 2, z >> 2);
        System.arraycopy(values, slot * OverworldNoise.COL_SIZE, out, 0, OverworldNoise.COL_SIZE);
    }

    /** NoiseChunk.preliminarySurfaceLevel. */
    public int preliminarySurfaceLevel(int x, int z) {
        if (blender != null) {
            int slot = blendSlot(x >> 2, z >> 2);
            int level = blendSurface[slot];
            if (level == NO_SURFACE) {
                int at = slot * OverworldNoise.COL_SIZE;
                level = OverworldNoise.preliminarySurfaceLevel(blendValues[at + OverworldNoise.COL_OFFSET],
                    blendValues[at + OverworldNoise.COL_FACTOR]);
                blendSurface[slot] = level;
            }
            return level;
        }
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
