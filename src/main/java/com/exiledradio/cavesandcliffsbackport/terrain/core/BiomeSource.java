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
    private final double[] column = new double[OverworldNoise.COL_SIZE];

    public BiomeSource(OverworldNoise noise, ColumnCache columns, Climate climate) {
        this.noise = noise;
        this.columns = columns;
        this.climate = climate;
    }

    private int slot(int qx, int qz) {
        int slot = (qx & MASK) << BITS | (qz & MASK);
        long key = (long) qx << 32 | (qz & 0xFFFFFFFFL);
        if (!present[slot] || keys[slot] != key) {
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
            offsets[slot] = column[OverworldNoise.COL_OFFSET];
            keys[slot] = key;
            present[slot] = true;
        }
        return slot;
    }

    /** The biome id at a quart position (any y: the depth function goes on past the world's height). */
    public int biome(int qx, int qy, int qz) {
        int slot = slot(qx, qz);
        long depth = Climate.quantize((float) OverworldNoise.depth(qy << 2, offsets[slot]));
        return climate.biome(cached[slot], depth);
    }

    /**
     * The chunk's stored biomes, as ChunkAccess.fillBiomesFromNoise writes them: out[(qx * 4 + qz) * 96 + qy + 16]
     * for the chunk's quarts qx, qz in 0..3 and quart y -16..79.
     */
    public void fillChunk(int cx, int cz, byte[] out) {
        int qx0 = cx << 2;
        int qz0 = cz << 2;
        for (int qx = 0; qx < 4; qx++) {
            for (int qz = 0; qz < 4; qz++) {
                int slot = slot(qx0 + qx, qz0 + qz);
                Climate.Column col = cached[slot];
                double offset = offsets[slot];
                int at = (qx * 4 + qz) * QUARTS_Y;
                for (int qy = 0; qy < QUARTS_Y; qy++) {
                    long depth = Climate.quantize((float) OverworldNoise.depth((qy + MIN_QUART_Y) << 2, offset));
                    int b = climate.biome(col, depth);
                    out[at + qy] = (byte) b;
                }
            }
        }
    }
}
