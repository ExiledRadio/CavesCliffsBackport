package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * The 3x3 chunks a chunk's decoration reads and writes (WorldGenRegion at FEATURES: writes reach one chunk out;
 * the features ported so far read no further either). Holds each chunk's blocks (ChunkFiller's layout) and its
 * OCEAN_FLOOR_WG heightmap as it stood after carving: from LIQUID_CARVERS on, setBlockState updates only the
 * POST_FEATURES heightmaps, so the worldgen ones stay at that snapshot through decoration.
 *
 * Fill it with {@link #set} before Decorator.decorate; the arrays are written in place.
 */
public final class Region {
    private static final int MIN_Y = OverworldNoise.MIN_Y;
    private static final int HEIGHT = OverworldNoise.HEIGHT;

    private final char[][] blocks = new char[9][];
    private final int[][] oceanFloor = new int[9][];
    int centerX;
    int centerZ;
    private int minX;
    private int minZ;

    /** Center chunk cx, cz; blocks and oceanFloor (oceanFloorHeights' output) for the 9 chunks, index (dx + 1) * 3 + dz + 1. */
    public void set(int cx, int cz, char[][] blocks9, int[][] oceanFloor9) {
        centerX = cx;
        centerZ = cz;
        minX = (cx - 1) << 4;
        minZ = (cz - 1) << 4;
        for (int i = 0; i < 9; i++) {
            blocks[i] = blocks9[i];
            oceanFloor[i] = oceanFloor9[i];
        }
    }

    /** OCEAN_FLOOR_WG of a chunk's blocks: per column (x * 16 + z), one above the highest block that blocks motion, or -64. */
    public static void oceanFloorHeights(char[] blocks, int[] out) {
        for (int c = 0; c < 256; c++) {
            int at = c * HEIGHT;
            int h = MIN_Y;
            for (int i = HEIGHT - 1; i >= 0; i--) {
                if (BlockCodes.blocksMotion(blocks[at + i])) {
                    h = MIN_Y + i + 1;
                    break;
                }
            }
            out[c] = h;
        }
    }

    private int chunk(int x, int z) {
        int dx = (x - minX) >> 4;
        int dz = (z - minZ) >> 4;
        if (dx < 0 || dx > 2 || dz < 0 || dz > 2) {
            throw new IllegalStateException("outside the 3x3 region around " + centerX + "," + centerZ + ": " + x + "," + z);
        }
        return dx * 3 + dz;
    }

    /** The block at x, y, z; air outside the world's height (vanilla: void air, which is air too). */
    public int get(int x, int y, int z) {
        if (y < MIN_Y || y >= MIN_Y + HEIGHT) {
            return BlockCodes.AIR;
        }
        return blocks[chunk(x, z)][((x & 15) * 16 + (z & 15)) * HEIGHT + y - MIN_Y];
    }

    /** A section write (BulkSectionAccess / LevelChunkSection.setBlockState): no heightmap moves. */
    public void setRaw(int x, int y, int z, int code) {
        blocks[chunk(x, z)][((x & 15) * 16 + (z & 15)) * HEIGHT + y - MIN_Y] = (char) code;
    }

    /**
     * WorldGenRegion.setBlock: dropped outside the writable 3x3 (ensureCanWrite) or the world's height
     * (ProtoChunk.setBlockState). The POST_FEATURES heightmaps it would move are not kept yet: no ported feature
     * reads them.
     */
    public boolean set(int x, int y, int z, int code) {
        if (!canWrite(x, z) || outsideBuildHeight(y)) {
            return false;
        }
        setRaw(x, y, z, code);
        return true;
    }

    /** WorldGenRegion.ensureCanWrite: within one chunk of the center. */
    public boolean canWrite(int x, int z) {
        return Math.abs((x >> 4) - centerX) <= 1 && Math.abs((z >> 4) - centerZ) <= 1;
    }

    /** WorldGenRegion.getHeight(OCEAN_FLOOR_WG, x, z). */
    public int oceanFloor(int x, int z) {
        return oceanFloor[chunk(x, z)][(x & 15) * 16 + (z & 15)];
    }

    public static boolean outsideBuildHeight(int y) {
        return y < MIN_Y || y >= MIN_Y + HEIGHT;
    }
}
