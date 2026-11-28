package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * 1.18.2's overworld MultiNoiseBiomeSource.getNoiseBiome(quartX, quartY, quartZ, sampler) on the noise router's
 * climate functions (Climate.Sampler: temperature, vegetation, continents, erosion, depth, ridges at the quart's
 * block corner, each cast to float and quantized). Answers from a cold search; see Climate on ties.
 *
 * Climate columns are kept by quart position, direct-mapped 128x128 like ColumnCache, so neighboring chunks and
 * the surface's look-ups around a chunk reuse them. One instance per thread.
 */
public final class BiomeSource {
    private static final int BITS = 7;
    private static final int SIZE = 1 << BITS;
    private static final int MASK = SIZE - 1;
    public static final int MIN_QUART_Y = OverworldNoise.MIN_Y >> 2;
    public static final int QUARTS_Y = OverworldNoise.HEIGHT >> 2;

    private final OverworldNoise noise;
    private final ColumnCache columns;
    private final Climate climate;
    private final long[] keys = new long[SIZE * SIZE];
    private final boolean[] present = new boolean[SIZE * SIZE];
    private final Climate.Column[] cached = new Climate.Column[SIZE * SIZE];
    private final double[] offsets = new double[SIZE * SIZE];
    /** Per column, its five quantized climate values (temperature, humidity, continents, erosion, weirdness). */
    private final long[] climates = new long[SIZE * SIZE * 5];
    /** Per column, the biome at each quart y of the world (-16..79), or -1 until asked. */
    private final byte[] stored = new byte[SIZE * SIZE * QUARTS_Y];
    private final double[] column = new double[OverworldNoise.COL_SIZE];
    /** The column cache's blender generation each slot was computed under (blended offsets differ per chunk). */
    private final int[] generations = new int[SIZE * SIZE];
    /**
     * An upgraded world's biomes that are not the climate's (Pipeline.Upgrades): per chunk, its stored quarts
     * (fillChunk's layout) as blending and the upgrade made them. Null in every other world.
     */
    java.util.Map<Long, byte[]> overrides;

    /** The overriding quarts of the chunk holding quart qx, qz, or null. */
    private byte[] override(int qx, int qz) {
        java.util.Map<Long, byte[]> o = overrides;
        return o == null ? null : o.get((long) (qx >> 2) << 32 | ((qz >> 2) & 0xFFFFFFFFL));
    }

    public BiomeSource(OverworldNoise noise, ColumnCache columns, Climate climate) {
        this.noise = noise;
        this.columns = columns;
        this.climate = climate;
    }

    private int slot(int qx, int qz) {
        int slot = (qx & MASK) << BITS | (qz & MASK);
        long key = (long) qx << 32 | (qz & 0xFFFFFFFFL);
        if (!present[slot] || keys[slot] != key || generations[slot] != columns.generation()) {
            generations[slot] = columns.generation();
            int x = qx << 2;
            int z = qz << 2;
            columns.get(x, z, column);
            double sx = column[OverworldNoise.COL_SHIFT_X];
            double sz = column[OverworldNoise.COL_SHIFT_Z];
            long t = Climate.quantize((float) noise.shifted(noise.temperature, x, z, sx, sz));
            long h = Climate.quantize((float) noise.shifted(noise.vegetation, x, z, sx, sz));
            long c = Climate.quantize((float) column[OverworldNoise.COL_CONTINENTS]);
            long e = Climate.quantize((float) column[OverworldNoise.COL_EROSION]);
            long w = Climate.quantize((float) column[OverworldNoise.COL_RIDGES]);
            Climate.Column col = cached[slot];
            if (col == null) {
                col = climate.newColumn();
                cached[slot] = col;
            }
            climate.column(col, t, h, c, e, w);
            int at = slot * 5;
            climates[at] = t;
            climates[at + 1] = h;
            climates[at + 2] = c;
            climates[at + 3] = e;
            climates[at + 4] = w;
            offsets[slot] = column[OverworldNoise.COL_OFFSET];
            java.util.Arrays.fill(stored, slot * QUARTS_Y, (slot + 1) * QUARTS_Y, (byte) -1);
            keys[slot] = key;
            present[slot] = true;
        }
        return slot;
    }

    /**
     * The climate target at a quart position (Climate.Sampler.sample: the six quantized values in Mojang's order and
     * offset 0), into out (7 longs): what a TerraBlender region's own parameter list is searched with.
     */
    public void target(int qx, int qy, int qz, long[] out) {
        int slot = slot(qx, qz);
        int at = slot * 5;
        out[Climate.TEMPERATURE] = climates[at];
        out[Climate.HUMIDITY] = climates[at + 1];
        out[Climate.CONTINENTALNESS] = climates[at + 2];
        out[Climate.EROSION] = climates[at + 3];
        out[Climate.DEPTH] = Climate.quantize((float) OverworldNoise.depth(qy << 2, offsets[slot]));
        out[Climate.WEIRDNESS] = climates[at + 4];
        out[Climate.OFFSET] = 0L;
    }

    /** The biome id at a quart position (any y: the depth function goes on past the world's height). */
    public int biome(int qx, int qy, int qz) {
        if (overrides != null && qy >= MIN_QUART_Y && qy < MIN_QUART_Y + QUARTS_Y) {
            byte[] o = override(qx, qz);
            if (o != null) {
                return o[((qx & 3) * 4 + (qz & 3)) * QUARTS_Y + qy - MIN_QUART_Y];
            }
        }
        int slot = slot(qx, qz);
        long depth = Climate.quantize((float) OverworldNoise.depth(qy << 2, offsets[slot]));
        return climate.biome(cached[slot], depth);
    }

    /**
     * ChunkAccess.getNoiseBiome: the biome a chunk stores at a quart, quart y clamped to the world's height.
     */
    public int stored(int qx, int qy, int qz) {
        if (overrides != null) {
            byte[] o = override(qx, qz);
            if (o != null) {
                int y = qy < MIN_QUART_Y ? 0 : qy >= MIN_QUART_Y + QUARTS_Y ? QUARTS_Y - 1 : qy - MIN_QUART_Y;
                return o[((qx & 3) * 4 + (qz & 3)) * QUARTS_Y + y];
            }
        }
        return storedInSlot(slot(qx, qz), qy);
    }

    /**
     * The cache slot holding quart column qx, qz, computing it if needed. It stays valid until a look-up of a
     * column 128 quarts away in x or z (direct-mapped).
     */
    public int slotOf(int qx, int qz) {
        return slot(qx, qz);
    }

    /** stored() for a column already in the given slot. */
    public int storedInSlot(int slot, int qy) {
        int y = qy < MIN_QUART_Y ? 0 : qy >= MIN_QUART_Y + QUARTS_Y ? QUARTS_Y - 1 : qy - MIN_QUART_Y;
        int at = slot * QUARTS_Y + y;
        int b = stored[at];
        if (b < 0) {
            long depth = Climate.quantize((float) OverworldNoise.depth((y + MIN_QUART_Y) << 2, offsets[slot]));
            b = climate.biome(cached[slot], depth);
            stored[at] = (byte) b;
        }
        return b;
    }

    /**
     * The chunk's stored biomes, as ChunkAccess.fillBiomesFromNoise writes them: out[(qx * 4 + qz) * 96 + qy + 16]
     * for the chunk's quarts qx, qz in 0..3 and quart y -16..79.
     */
    public void fillChunk(int cx, int cz, byte[] out) {
        byte[] o = override(cx << 2, cz << 2);
        if (o != null && o != out) {
            System.arraycopy(o, 0, out, 0, o.length);
            return;
        }
        int qx0 = cx << 2;
        int qz0 = cz << 2;
        for (int qx = 0; qx < 4; qx++) {
            for (int qz = 0; qz < 4; qz++) {
                int slot = slot(qx0 + qx, qz0 + qz);
                Climate.Column col = cached[slot];
                double offset = offsets[slot];
                int at = (qx * 4 + qz) * QUARTS_Y;
                int from = slot * QUARTS_Y;
                for (int qy = 0; qy < QUARTS_Y; qy++) {
                    int b = stored[from + qy];
                    if (b < 0) {
                        long depth = Climate.quantize((float) OverworldNoise.depth((qy + MIN_QUART_Y) << 2, offset));
                        b = climate.biome(col, depth);
                        stored[from + qy] = (byte) b;
                    }
                    out[at + qy] = (byte) b;
                }
            }
        }
    }
}
