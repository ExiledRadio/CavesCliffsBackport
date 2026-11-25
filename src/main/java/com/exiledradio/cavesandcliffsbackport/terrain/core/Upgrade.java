package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * How one chunk of an upgraded world differs from a chunk of a world that was always y -64..319 (1.18's
 * {@code BelowZeroRetrogen} and {@code Blender}): what {@link Pipeline} needs to fill and decorate it.
 *
 * <ul>
 *   <li>An old chunk being upgraded ({@link #upgrading}): its blocks from y 0 up stay as they are; it gets 1.18's
 *       noise, surface, carvers and features below y 0 only. Its old bedrock at y 0..4 becomes deepslate, and a
 *       column with air at y 0 (a hole in the old bedrock) stays air below 0.</li>
 *   <li>Any chunk within 7 of an old one ({@link #blender}): its terrain, biomes and caves blend toward the old
 *       chunks.</li>
 * </ul>
 *
 * Built on the asking thread; read by the fill's worker thread.
 */
public final class Upgrade {
    private static final int MIN_Y = OverworldNoise.MIN_Y;
    private static final int HEIGHT = OverworldNoise.HEIGHT;

    /** The old chunk gets its part below y 0 (BelowZeroRetrogen). */
    public final boolean upgrading;
    /**
     * The old chunk was never populated by 1.12 (TerrainPopulated false, 1.18's status before features): after its
     * part below 0 it is decorated as any chunk, at every height.
     */
    public final boolean decorateAll;
    /** The old chunks within range, or null for none. */
    public final Blending.Blender blender;
    /** The carvers' filter around old chunks (this one, if upgrading, and its old neighbors), or null. */
    public final Blending.CarveMask carveMask;
    /**
     * The old chunk's blocks from y 0 to 255 as block codes, column (x * 16 + z) * 256 + y: what the 1.18 features
     * read there. Dropped once the chunk is filled.
     */
    char[] old;
    /** Columns (x * 16 + z) with air at y 0 (BelowZeroRetrogen's missing bedrock). */
    private final boolean[] holes;
    /** The old chunk's biome per column (x * 16 + z), 1.18 ids, -1 for one 1.18 has no counterpart of. */
    private final byte[] oldBiomes;

    public Upgrade(boolean upgrading, boolean decorateAll, Blending.Blender blender, Blending.CarveMask carveMask, char[] old,
            boolean[] holes, byte[] oldBiomes) {
        this.upgrading = upgrading;
        this.decorateAll = decorateAll;
        this.blender = blender;
        this.carveMask = carveMask;
        this.old = old;
        this.holes = holes;
        this.oldBiomes = oldBiomes;
    }

    /** The chunk's stored biomes after Blender.getBiomeResolver and, upgrading, BelowZeroRetrogen.getBiomeResolver. */
    void applyBiomes(int cx, int cz, byte[] quarts) {
        for (int qx = 0; qx < 4; qx++) {
            for (int qz = 0; qz < 4; qz++) {
                int at = (qx * 4 + qz) * BiomeSource.QUARTS_Y;
                if (blender != null) {
                    int b = blender.blendBiome((cx << 2) + qx, (cz << 2) + qz);
                    if (b >= 0) {
                        java.util.Arrays.fill(quarts, at, at + BiomeSource.QUARTS_Y, (byte) b);
                    }
                }
                if (oldBiomes != null) {
                    column(quarts, at, oldBiomes[(qx * 4) * 16 + qz * 4], upgrading);
                }
            }
        }
    }

    /**
     * BelowZeroRetrogen.getBiomeResolver over a chunk's stored biomes (BiomeSource.fillChunk's layout), from its old
     * biome per column (x * 16 + z, 1.18 ids, -1 for none: that column keeps the noise's): for a chunk whose fill ran
     * before its old blocks were known (a hybrid world's neighbor, {@code terrain.ModernGenerator}).
     */
    public static void retrogenBiomes(byte[] quarts, byte[] oldBiomes) {
        for (int qx = 0; qx < 4; qx++) {
            for (int qz = 0; qz < 4; qz++) {
                column(quarts, (qx * 4 + qz) * BiomeSource.QUARTS_Y, oldBiomes[(qx * 4) * 16 + qz * 4], true);
            }
        }
    }

    private static void column(byte[] quarts, int at, int old, boolean upgrading) {
        if (old < 0) {
            return;
        }
        for (int qy = 0; qy < BiomeSource.QUARTS_Y; qy++) {
            int y = (qy << 2) + MIN_Y;
            int b = quarts[at + qy];
            // Below 0, 1.18's lush and dripstone caves stay (and the deep dark: 1.19's upgrade kept it too); everything
            // else is the old chunk's biome. An old chunk that is not upgrading (only read by its neighbors) keeps its
            // biomes from 0 up.
            if (y >= 0 || upgrading && b != Biomes.LUSH_CAVES && b != Biomes.DRIPSTONE_CAVES && b != Biomes.DEEP_DARK) {
                quarts[at + qy] = (byte) old;
            }
        }
    }

    /** The old blocks from y 0 up, back over whatever the fill or the surface put there. */
    void overlay(char[] blocks) {
        if (old == null) {
            return;
        }
        for (int c = 0; c < 256; c++) {
            System.arraycopy(old, c * 256, blocks, c * HEIGHT - MIN_Y, 256);
        }
    }

    /** BelowZeroRetrogen.replaceOldBedrock: bedrock at y 0..4 becomes deepslate. */
    static void replaceOldBedrock(char[] blocks) {
        for (int c = 0; c < 256; c++) {
            int at = c * HEIGHT - MIN_Y;
            for (int y = 0; y <= 4; y++) {
                if (blocks[at + y] == BlockCodes.BEDROCK) {
                    blocks[at + y] = (char) BlockCodes.DEEPSLATE;
                }
            }
        }
    }

    /** BelowZeroRetrogen.applyBedrockMask: a column with a hole in the old bedrock is air below y 0. */
    void applyBedrockMask(char[] blocks) {
        if (holes == null) {
            return;
        }
        for (int c = 0; c < 256; c++) {
            if (holes[c]) {
                java.util.Arrays.fill(blocks, c * HEIGHT, c * HEIGHT - MIN_Y, (char) BlockCodes.AIR);
            }
        }
    }
}
