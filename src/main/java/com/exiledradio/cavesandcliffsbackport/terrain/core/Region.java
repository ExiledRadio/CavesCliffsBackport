package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * The chunks a chunk's decoration reads and writes: WorldGenRegion at FEATURES writes one chunk out (the 3x3) and
 * reads further; the features ported read at most two out (large dripstone: radius 9 plus its wind drift), so this
 * holds the 5x5. Holds each chunk's blocks (ChunkFiller's layout) and its
 * OCEAN_FLOOR_WG and WORLD_SURFACE_WG heightmaps as they stood after carving: from LIQUID_CARVERS on, setBlockState updates only the
 * POST_FEATURES heightmaps, so the worldgen ones stay at that snapshot through decoration.
 *
 * <p>It also keeps each chunk's POST_FEATURES heightmaps (WORLD_SURFACE, OCEAN_FLOOR, MOTION_BLOCKING,
 * MOTION_BLOCKING_NO_LEAVES) as ProtoChunk.setBlockState does: every {@link #set} runs Heightmap.update for the
 * four; section writes ({@link #setRaw}, BulkSectionAccess) move none. Vanilla primes them from the blocks when a
 * chunk is first decorated, read or written at FEATURES. Every setBlock keeps them exact (update only rescans when
 * the top block stops matching) and no section write changes a block's heightmap predicates (ores replace rock
 * with rock), so priming them once after carving ({@link #primeHeightmaps}) gives vanilla's number at every read.
 *
 * <p>Light: during FEATURES no chunk within one of the chunk being decorated has been lit (LIGHT needs all its
 * neighbors at FEATURES), so vanilla's light engine answers block light 0 and sky light 15 everywhere a feature
 * asks (unlit columns have no data layers: BlockLightSectionStorage gives 0, SkyLightSectionStorage 15), and
 * WorldGenRegion's sky darkening is 0. {@link #blockLight} and {@link #rawBrightness} are those answers.
 *
 * Fill it with {@link #set} before Decorator.decorate; the arrays are written in place.
 */
public final class Region {
    private static final int MIN_Y = OverworldNoise.MIN_Y;
    private static final int HEIGHT = OverworldNoise.HEIGHT;

    /** Heightmap types, as indices into a chunk's heights array (type * 256 + x * 16 + z). */
    public static final int WORLD_SURFACE = 0;
    public static final int OCEAN_FLOOR = 1;
    public static final int MOTION_BLOCKING = 2;
    public static final int MOTION_BLOCKING_NO_LEAVES = 3;
    public static final int HEIGHTMAPS = 4;

    /** Chunks held: (2 * READ + 1) squared, index (dx + READ) * SIDE + dz + READ. */
    public static final int READ = 2;
    public static final int SIDE = 2 * READ + 1;
    public static final int CHUNKS = SIDE * SIDE;

    private final char[][] blocks = new char[CHUNKS][];
    private final int[][] oceanFloor = new int[CHUNKS][];
    private final int[][] heights = new int[CHUNKS][];
    /** Each chunk's tick and post-processing marks ({@link Marks}); null entries drop them. */
    private final Marks[] marks = new Marks[CHUNKS];
    int centerX;
    int centerZ;
    private int minX;
    private int minZ;

    /** Recorded writes kinds ({@link #ops}): {@link #set}, {@link #setRaw}, {@link #mark}. */
    static final int OP_SET = 0;
    static final int OP_RAW = 1;
    static final int OP_MARK = 2;
    /**
     * With {@link #recording}, every write and mark as two ints, in order: kind &lt;&lt; 30 | chunk index &lt;&lt; 25 |
     * column (x * 16 + z) &lt;&lt; 17 | y + 64, then the code (or the mark entry). The decoration journal
     * ({@link Pipeline}) replays them onto the same chunks ({@link #apply}).
     */
    int[] ops = new int[4096];
    int opCount;
    boolean recording;

    /**
     * Center chunk cx, cz; for the CHUNKS chunks (index (dx + READ) * SIDE + dz + READ) the blocks, the OCEAN_FLOOR_WG snapshot
     * (worldgenHeights' output) and the live heightmaps (primeHeightmaps' output, HEIGHTMAPS * 256 ints), all
     * kept by the caller from one decoration to the next.
     */
    public void set(int cx, int cz, char[][] blocks9, int[][] oceanFloor9, int[][] heights9) {
        set(cx, cz, blocks9, oceanFloor9, heights9, null);
    }

    /** As {@link #set(int, int, char[][], int[][], int[][])}, with each chunk's marks (null: none kept). */
    public void set(int cx, int cz, char[][] blocks9, int[][] oceanFloor9, int[][] heights9, Marks[] marks9) {
        centerX = cx;
        centerZ = cz;
        minX = (cx - READ) << 4;
        minZ = (cz - READ) << 4;
        for (int i = 0; i < CHUNKS; i++) {
            blocks[i] = blocks9[i];
            oceanFloor[i] = oceanFloor9[i];
            heights[i] = heights9[i];
            marks[i] = marks9 == null ? null : marks9[i];
        }
    }

    /**
     * The worldgen heightmaps of a chunk's blocks as they stand after carving, 512 ints: per column (x * 16 + z)
     * OCEAN_FLOOR_WG (one above the highest block that blocks motion, or -64), then from 256 on WORLD_SURFACE_WG
     * (one above the highest non-air block).
     */
    public static void worldgenHeights(char[] blocks, int[] out) {
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
            h = MIN_Y;
            for (int i = HEIGHT - 1; i >= 0; i--) {
                if (!BlockCodes.isAir(blocks[at + i])) {
                    h = MIN_Y + i + 1;
                    break;
                }
            }
            out[256 + c] = h;
        }
    }

    /** Heightmap.primeHeightmaps for the four POST_FEATURES types: per type and column, one above the top match. */
    public static void primeHeightmaps(char[] blocks, int[] out) {
        for (int c = 0; c < 256; c++) {
            int at = c * HEIGHT;
            for (int t = 0; t < HEIGHTMAPS; t++) {
                int h = MIN_Y;
                for (int i = HEIGHT - 1; i >= 0; i--) {
                    if (matches(t, blocks[at + i])) {
                        h = MIN_Y + i + 1;
                        break;
                    }
                }
                out[t * 256 + c] = h;
            }
        }
    }

    /** Heightmap.Types' predicates. */
    static boolean matches(int type, int code) {
        int f = BlockCodes.flags(code);
        switch (type) {
            case WORLD_SURFACE:
                return (f & BlockCodes.F_NOT_AIR) != 0;
            case OCEAN_FLOOR:
                return (f & BlockCodes.F_BLOCKS_MOTION) != 0;
            case MOTION_BLOCKING:
                return (f & (BlockCodes.F_BLOCKS_MOTION | BlockCodes.F_FLUID)) != 0;
            default:
                return (f & (BlockCodes.F_BLOCKS_MOTION | BlockCodes.F_FLUID)) != 0 && (f & BlockCodes.F_LEAVES) == 0;
        }
    }

    private int chunk(int x, int z) {
        int dx = (x - minX) >> 4;
        int dz = (z - minZ) >> 4;
        if (dx < 0 || dx >= SIDE || dz < 0 || dz >= SIDE) {
            throw new IllegalStateException("outside the region around " + centerX + "," + centerZ + ": " + x + "," + z);
        }
        return dx * SIDE + dz;
    }

    /**
     * The block at x, y, z; air outside the world's height (vanilla: void air, which is air too). Cave air in a
     * section holding only air-like blocks reads as air: ProtoChunk.getBlockState answers AIR for any section whose
     * hasOnlyAir() (no block that is not air), so a lake's cave air above the ground there is plain air to features.
     */
    public int get(int x, int y, int z) {
        if (y < MIN_Y || y >= MIN_Y + HEIGHT) {
            return BlockCodes.AIR;
        }
        char[] b = blocks[chunk(x, z)];
        int c = b[((x & 15) * 16 + (z & 15)) * HEIGHT + y - MIN_Y];
        if (c == BlockCodes.CAVE_AIR && sectionOnlyAir(b, (y - MIN_Y) >> 4)) {
            return BlockCodes.AIR;
        }
        return c;
    }

    /** LevelChunkSection.hasOnlyAir: no block of the 16-high section is anything but air, cave air or void air. */
    public static boolean sectionOnlyAir(char[] blocks, int section) {
        int y0 = section << 4;
        for (int col = 0; col < 256; col++) {
            int at = col * HEIGHT + y0;
            for (int i = 0; i < 16; i++) {
                if (!BlockCodes.isAir(blocks[at + i])) {
                    return false;
                }
            }
        }
        return true;
    }

    /** What ProtoChunk.getBlockState shows of a chunk: cave air in sections holding only air-like blocks as air. */
    public static void readAsVanilla(char[] blocks) {
        for (int s = 0; s < HEIGHT >> 4; s++) {
            if (sectionOnlyAir(blocks, s)) {
                for (int col = 0; col < 256; col++) {
                    int at = col * HEIGHT + (s << 4);
                    for (int i = 0; i < 16; i++) {
                        blocks[at + i] = (char) BlockCodes.AIR;
                    }
                }
            }
        }
    }

    /** A section write (BulkSectionAccess / LevelChunkSection.setBlockState): no heightmap moves. */
    public void setRaw(int x, int y, int z, int code) {
        int k = chunk(x, z);
        int col = (x & 15) * 16 + (z & 15);
        blocks[k][col * HEIGHT + y - MIN_Y] = (char) code;
        if (recording) {
            record(OP_RAW, k, col, y, code);
        }
    }

    private void record(int kind, int k, int col, int y, int value) {
        if (opCount + 2 > ops.length) {
            ops = java.util.Arrays.copyOf(ops, ops.length * 2);
        }
        ops[opCount++] = kind << 30 | k << 25 | col << 17 | (y - MIN_Y);
        ops[opCount++] = value;
    }

    /** One recorded write ({@link #ops}: op, then value) onto the chunk it was made in, as it was made. */
    static void apply(int op, int value, char[] b, int[] h, Marks m) {
        int col = op >> 17 & 255;
        int y = (op & 511) + MIN_Y;
        switch (op >>> 30) {
            case OP_SET:
                place(b, h, m, col, y, value);
                break;
            case OP_RAW:
                b[col * HEIGHT + y - MIN_Y] = (char) value;
                break;
            default:
                m.add(value);
                break;
        }
    }

    /** The chunk index (dx + READ) * SIDE + dz + READ a recorded write ({@link #ops}) was made in. */
    static int opChunk(int op) {
        return op >> 25 & 31;
    }

    /**
     * WorldGenRegion.setBlock: dropped outside the writable 3x3 (ensureCanWrite) or the world's height
     * (ProtoChunk.setBlockState), then Heightmap.update for the four POST_FEATURES heightmaps.
     */
    public boolean set(int x, int y, int z, int code) {
        if (!canWrite(x, z) || outsideBuildHeight(y)) {
            return false;
        }
        int k = chunk(x, z);
        int col = (x & 15) * 16 + (z & 15);
        place(blocks[k], heights[k], marks[k], col, y, code);
        if (recording) {
            record(OP_SET, k, col, y, code);
        }
        return true;
    }

    /** {@link #set} in one chunk's arrays, at column col (x * 16 + z). */
    private static void place(char[] b, int[] h, Marks m, int col, int y, int code) {
        int at = col * HEIGHT - MIN_Y;
        b[at + y] = (char) code;
        if ((BlockCodes.flags(code) & BlockCodes.F_POST) != 0 && m != null) {
            // WorldGenRegion.setBlock: a block with hasPostProcess marks its position.
            m.add(Marks.pack(col >> 4, y, col & 15, Marks.POST));
        }
        for (int t = 0; t < HEIGHTMAPS; t++) {
            int i = h[t * 256 + col];
            if (y <= i - 2) {
                continue;
            }
            if (matches(t, code)) {
                if (y >= i) {
                    h[t * 256 + col] = y + 1;
                }
            } else if (i - 1 == y) {
                int n = MIN_Y;
                for (int j = y - 1; j >= MIN_Y; --j) {
                    if (matches(t, b[at + j])) {
                        n = j + 1;
                        break;
                    }
                }
                h[t * 256 + col] = n;
            }
        }
    }

    /**
     * ChunkAccess.markPosForPostprocessing ({@link Marks#POST}) or WorldGenLevel.scheduleTick(pos, fluid, 0)
     * ({@link Marks#TICK}) on the chunk holding the position. Kept for the writable 3x3 (every mark 1.18.2's
     * overworld features make is at or next to a block they write; one that lands a chunk further out is dropped).
     */
    public void mark(int x, int y, int z, int kind) {
        if (!canWrite(x, z) || outsideBuildHeight(y)) {
            return;
        }
        int k = chunk(x, z);
        Marks m = marks[k];
        if (m != null) {
            int entry = Marks.pack(x, y, z, kind);
            m.add(entry);
            if (recording) {
                record(OP_MARK, k, (x & 15) * 16 + (z & 15), y, entry);
            }
        }
    }

    /** Feature.markAboveForPostProcessing: up to two blocks above, stopping at the first air. */
    public void markAbove(int x, int y, int z) {
        for (int i = 1; i <= 2; i++) {
            if (BlockCodes.isAir(get(x, y + i, z))) {
                return;
            }
            mark(x, y + i, z, Marks.POST);
        }
    }

    /** WorldGenRegion.ensureCanWrite: within one chunk of the center. */
    public boolean canWrite(int x, int z) {
        return Math.abs((x >> 4) - centerX) <= 1 && Math.abs((z >> 4) - centerZ) <= 1;
    }

    /** WorldGenRegion.getHeight(OCEAN_FLOOR_WG, x, z). */
    public int oceanFloor(int x, int z) {
        return oceanFloor[chunk(x, z)][(x & 15) * 16 + (z & 15)];
    }

    /** WorldGenRegion.getHeight(WORLD_SURFACE_WG, x, z). */
    public int worldSurfaceWg(int x, int z) {
        return oceanFloor[chunk(x, z)][256 + (x & 15) * 16 + (z & 15)];
    }

    /** WorldGenRegion.getHeight(type, x, z) for WORLD_SURFACE, OCEAN_FLOOR, MOTION_BLOCKING(_NO_LEAVES). */
    public int height(int type, int x, int z) {
        return heights[chunk(x, z)][type * 256 + (x & 15) * 16 + (z & 15)];
    }

    /** getBrightness(LightLayer.BLOCK, pos) during FEATURES: nothing lit yet. */
    public int blockLight(int x, int y, int z) {
        return 0;
    }

    /** getRawBrightness(pos, amount) during FEATURES: max(block 0, sky 15 - amount). */
    public int rawBrightness(int x, int y, int z, int amount) {
        return Math.max(0, 15 - amount);
    }

    public static boolean outsideBuildHeight(int y) {
        return y < MIN_Y || y >= MIN_Y + HEIGHT;
    }
}
