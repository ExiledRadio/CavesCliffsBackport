package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.Arrays;

/**
 * 1.18.2's terrain blending between old chunks (made before the world was upgraded to y -64..319) and the chunks the
 * 1.18 generator makes next to them: {@code Blender} and {@code BlendingData}, with no Minecraft classes.
 *
 * <p>An old chunk's {@link Data} holds, along the edges that face new chunks, the height of its terrain, a density
 * column read from its blocks and its biome. A new chunk's {@link Blender} reads the data of the old chunks within
 * seven chunks: its column offset and factor move toward the old heights ({@link Blender#blendOffsetAndFactor}),
 * its density toward the old densities right at the border ({@link Blender#blendDensity}), its biomes toward the old
 * ones ({@link Blender#blendBiome}), and its carvers stay four blocks clear of the old chunks ({@link CarveMask}).
 * Arithmetic as Mojang's, in the same order; the one difference is the order the inverse-distance sums visit the old
 * chunks (Mojang's is its hash map's).
 */
public final class Blending {
    /** BlendingData.AREA_WITH_OLD_GENERATION: y 0 to 255. */
    static final int OLD_MIN_Y = 0;
    static final int OLD_HEIGHT = 256;
    private static final int QUARTS_PER_SECTION = 4;
    private static final int INSIDE_MAX = QUARTS_PER_SECTION - 1;
    private static final int OUTSIDE_MAX = QUARTS_PER_SECTION;
    private static final int INSIDE_COUNT = 2 * INSIDE_MAX + 1;
    private static final int OUTSIDE_COUNT = 2 * OUTSIDE_MAX + 1;
    /** BlendingData.CELL_COLUMN_COUNT. */
    public static final int COLUMNS = INSIDE_COUNT + OUTSIDE_COUNT;
    /** cellCountPerColumn: two cells per section of the old area. */
    static final int CELLS = OLD_HEIGHT / 16 * 2;
    static final double NO_VALUE = Double.MAX_VALUE;

    /** Blender.HEIGHT_BLENDING_RANGE_CELLS: QuartPos.fromSection(7) - 1. */
    static final int HEIGHT_RANGE_CELLS = 7 * 4 - 1;
    /** Blender.HEIGHT_BLENDING_RANGE_CHUNKS: QuartPos.toSection(HEIGHT_BLENDING_RANGE_CELLS + 3). */
    public static final int HEIGHT_RANGE_CHUNKS = (HEIGHT_RANGE_CELLS + 3) >> 2;
    /** Blender.DENSITY_BLENDING_RANGE_CHUNKS: QuartPos.toSection(5). */
    public static final int DENSITY_RANGE_CHUNKS = 5 >> 2;
    private static final double OLD_CHUNK_Y_RADIUS = (double) OLD_HEIGHT / 2.0D;
    private static final double OLD_CHUNK_CENTER_Y = (double) OLD_MIN_Y + OLD_CHUNK_Y_RADIUS;

    /** Direction8, in Mojang's order; bit i of a sides mask is direction i. */
    public static final int NORTH = 0;
    public static final int NORTH_EAST = 1;
    public static final int EAST = 2;
    public static final int SOUTH_EAST = 3;
    public static final int SOUTH = 4;
    public static final int SOUTH_WEST = 5;
    public static final int WEST = 6;
    public static final int NORTH_WEST = 7;
    /** Each direction's chunk step x, z. */
    public static final int[][] STEPS = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

    /** Blender's SHIFT_NOISE: the offset noise from XoroshiroRandomSource(42). Read only, so shared. */
    private static final DoubleNoise SHIFT = new DoubleNoise(Xoroshiro.ofSeed(42L), -3, 1.0D, 1.0D, 1.0D, 0.0D);

    private Blending() {
    }

    /** The blocks of an old chunk, as BlendingData reads them (local x, z; y 0..255). */
    public interface OldBlocks {
        /** BlendingData.getHeightAtXZ: the highest of its surface blocks (podzol, gravel, grass, stone, ...), or 0. */
        int surfaceHeight(int x, int z);

        /** BlendingData.isGround: not air, leaves, logs or mushroom blocks, with a collision shape. */
        boolean ground(int x, int y, int z);

        /** The chunk's biome at block x, z (1.18 id), or -1 for one 1.18 has no counterpart of. */
        int biome(int x, int z);
    }

    /** One old chunk's BlendingData: its edge columns facing new chunks, calculated once (calculateData). */
    public static final class Data {
        final double[] heights = new double[COLUMNS];
        final double[][] densities = new double[COLUMNS][];
        final int[] biomes = new int[COLUMNS];

        /**
         * @param sides the Direction8 bits whose neighbor is not an old chunk (sideByGenerationAge(..., false))
         */
        public Data(OldBlocks blocks, int sides) {
            Arrays.fill(heights, NO_VALUE);
            Arrays.fill(biomes, -1);
            boolean n = (sides & 1 << NORTH) != 0;
            boolean w = (sides & 1 << WEST) != 0;
            boolean e = (sides & 1 << EAST) != 0;
            boolean s = (sides & 1 << SOUTH) != 0;
            if (n || w || (sides & 1 << NORTH_WEST) != 0) {
                add(insideIndex(0, 0), blocks, 0, 0);
            }
            if (n) {
                for (int i = 1; i < QUARTS_PER_SECTION; ++i) {
                    add(insideIndex(i, 0), blocks, 4 * i, 0);
                }
            }
            if (w) {
                for (int j = 1; j < QUARTS_PER_SECTION; ++j) {
                    add(insideIndex(0, j), blocks, 0, 4 * j);
                }
            }
            if (e) {
                for (int k = 1; k < QUARTS_PER_SECTION; ++k) {
                    add(outsideIndex(OUTSIDE_MAX, k), blocks, 15, 4 * k);
                }
            }
            if (s) {
                for (int l = 0; l < QUARTS_PER_SECTION; ++l) {
                    add(outsideIndex(l, OUTSIDE_MAX), blocks, 4 * l, 15);
                }
            }
            if (e && (sides & 1 << NORTH_EAST) != 0) {
                add(outsideIndex(OUTSIDE_MAX, 0), blocks, 15, 0);
            }
            if (e && s && (sides & 1 << SOUTH_EAST) != 0) {
                add(outsideIndex(OUTSIDE_MAX, OUTSIDE_MAX), blocks, 15, 15);
            }
        }

        private void add(int index, OldBlocks blocks, int x, int z) {
            if (heights[index] == NO_VALUE) {
                heights[index] = (double) blocks.surfaceHeight(x, z);
            }
            densities[index] = densityColumn(blocks, x, z, McMath.floor(heights[index]));
            biomes[index] = blocks.biome(x, z);
        }

        private static double read1(OldBlocks blocks, int x, int[] y, int z) {
            y[0]--;
            return blocks.ground(x, y[0], z) ? 1.0D : -1.0D;
        }

        private static double read7(OldBlocks blocks, int x, int[] y, int z) {
            double d0 = 0.0D;
            for (int i = 0; i < 7; ++i) {
                d0 += read1(blocks, x, y, z);
            }
            return d0;
        }

        private static double[] densityColumn(OldBlocks blocks, int x, int z, int height) {
            double[] out = new double[CELLS];
            Arrays.fill(out, -1.0D);
            int[] y = {OLD_MIN_Y + OLD_HEIGHT};
            double d0 = read7(blocks, x, y, z);
            for (int i = out.length - 2; i >= 0; --i) {
                double d1 = read1(blocks, x, y, z);
                double d2 = read7(blocks, x, y, z);
                out[i] = (d0 + d1 + d2) / 15.0D;
                d0 = d2;
            }
            int j = Math.floorDiv(height, 8);
            if (j >= 1 && j < out.length) {
                double d4 = ((double) height + 0.5D) % 8.0D / 8.0D;
                double d5 = (1.0D - d4) / d4;
                double d3 = Math.max(d5, 1.0D) * 0.25D;
                out[j] = -d5 / d3;
                out[j - 1] = 1.0D / d3;
            }
            return out;
        }

        double height(int x, int z) {
            if (x != OUTSIDE_MAX && z != OUTSIDE_MAX) {
                return x != 0 && z != 0 ? NO_VALUE : heights[insideIndex(x, z)];
            }
            return heights[outsideIndex(x, z)];
        }

        private static double density(double[] column, int cellY) {
            if (column == null) {
                return NO_VALUE;
            }
            int i = cellY - columnMinY();
            return i >= 0 && i < column.length ? column[i] * 0.1D : NO_VALUE;
        }

        double density(int x, int cellY, int z) {
            if (cellY == minCellY()) {
                // floorDensities: all 1.0.
                return 1.0D * 0.1D;
            }
            if (x != OUTSIDE_MAX && z != OUTSIDE_MAX) {
                return x != 0 && z != 0 ? NO_VALUE : density(densities[insideIndex(x, z)], cellY);
            }
            return density(densities[outsideIndex(x, z)], cellY);
        }

        /** Whether any edge column was calculated (an old chunk with only old neighbors has none). */
        public boolean hasEdges() {
            for (double h : heights) {
                if (h != NO_VALUE) {
                    return true;
                }
            }
            return false;
        }
    }

    private static int minCellY() {
        return OLD_MIN_Y / 16 * 2;
    }

    private static int columnMinY() {
        return minCellY() + 1;
    }

    private static int insideIndex(int x, int z) {
        return INSIDE_MAX - x + z;
    }

    private static int outsideIndex(int x, int z) {
        return INSIDE_COUNT + x + OUTSIDE_MAX - z;
    }

    private static int zeroIfNegative(int v) {
        return v & ~(v >> 31);
    }

    static int columnX(int i) {
        if (i < INSIDE_COUNT) {
            return zeroIfNegative(INSIDE_MAX - i);
        }
        int j = i - INSIDE_COUNT;
        return OUTSIDE_MAX - zeroIfNegative(OUTSIDE_MAX - j);
    }

    static int columnZ(int i) {
        if (i < INSIDE_COUNT) {
            return zeroIfNegative(i - INSIDE_MAX);
        }
        int j = i - INSIDE_COUNT;
        return OUTSIDE_MAX - zeroIfNegative(j - OUTSIDE_MAX);
    }

    /** Mth.length. */
    private static double length(double a, double b) {
        return Math.sqrt(a * a + b * b);
    }

    private static double length(double a, double b, double c) {
        return Math.sqrt(a * a + b * b + c * c);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : v > max ? max : v;
    }

    /**
     * One chunk's Blender (Blender.of for the chunk being generated): the old chunks' data within
     * {@link #HEIGHT_RANGE_CHUNKS}, the ones within {@link #DENSITY_RANGE_CHUNKS} for the density too.
     * Read only once built, so the fill's worker thread can use it.
     */
    public static final class Blender {
        final int centerX;
        final int centerZ;
        private final int side = 2 * HEIGHT_RANGE_CHUNKS + 1;
        private final Data[] all = new Data[side * side];
        private final int[] allX;
        private final int[] allZ;
        private final Data[] near;
        private final int[] nearX;
        private final int[] nearZ;
        private final int count;
        private final int nearCount;

        /** data: the old chunks' data, data[(dx + 7) * 15 + dz + 7] for dx, dz within 7 of the center (null for none). */
        public Blender(int centerX, int centerZ, Data[] data) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            int n = 0;
            int m = 0;
            for (int i = 0; i < data.length; i++) {
                if (data[i] != null) {
                    n++;
                    int dx = i / side - HEIGHT_RANGE_CHUNKS;
                    int dz = i % side - HEIGHT_RANGE_CHUNKS;
                    if (Math.abs(dx) <= DENSITY_RANGE_CHUNKS && Math.abs(dz) <= DENSITY_RANGE_CHUNKS) {
                        m++;
                    }
                }
            }
            allX = new int[n];
            allZ = new int[n];
            Data[] list = new Data[n];
            near = new Data[m];
            nearX = new int[m];
            nearZ = new int[m];
            n = 0;
            m = 0;
            for (int i = 0; i < data.length; i++) {
                Data d = data[i];
                if (d == null) {
                    continue;
                }
                all[i] = d;
                int dx = i / side - HEIGHT_RANGE_CHUNKS;
                int dz = i % side - HEIGHT_RANGE_CHUNKS;
                list[n] = d;
                allX[n] = centerX + dx;
                allZ[n] = centerZ + dz;
                n++;
                if (Math.abs(dx) <= DENSITY_RANGE_CHUNKS && Math.abs(dz) <= DENSITY_RANGE_CHUNKS) {
                    near[m] = d;
                    nearX[m] = centerX + dx;
                    nearZ[m] = centerZ + dz;
                    m++;
                }
            }
            this.list = list;
            this.count = n;
            this.nearCount = m;
        }

        private final Data[] list;

        private Data at(int chunkX, int chunkZ) {
            int dx = chunkX - centerX + HEIGHT_RANGE_CHUNKS;
            int dz = chunkZ - centerZ + HEIGHT_RANGE_CHUNKS;
            if (dx < 0 || dz < 0 || dx >= side || dz >= side) {
                return null;
            }
            return all[dx * side + dz];
        }

        private interface Getter {
            double get(Data d, int x, int y, int z);
        }

        private static final Getter HEIGHT = (d, x, y, z) -> d.height(x, z);
        private static final Getter DENSITY = Data::density;

        private double value(Getter getter, int qx, int qy, int qz) {
            int i = qx >> 2;
            int j = qz >> 2;
            boolean fx = (qx & 3) == 0;
            boolean fz = (qz & 3) == 0;
            double d0 = value(getter, i, j, qx, qy, qz);
            if (d0 == NO_VALUE) {
                if (fx && fz) {
                    d0 = value(getter, i - 1, j - 1, qx, qy, qz);
                }
                if (d0 == NO_VALUE) {
                    if (fx) {
                        d0 = value(getter, i - 1, j, qx, qy, qz);
                    }
                    if (d0 == NO_VALUE && fz) {
                        d0 = value(getter, i, j - 1, qx, qy, qz);
                    }
                }
            }
            return d0;
        }

        private double value(Getter getter, int chunkX, int chunkZ, int qx, int qy, int qz) {
            Data d = at(chunkX, chunkZ);
            return d != null ? getter.get(d, qx - (chunkX << 2), qy, qz - (chunkZ << 2)) : NO_VALUE;
        }

        /**
         * Blender.blendOffsetAndFactor at block x, z: out[0] the alpha (1: the new terrain alone), out[1] the offset
         * the splines blend toward.
         */
        public void blendOffsetAndFactor(int x, int z, double[] out) {
            int i = x >> 2;
            int j = z >> 2;
            double d0 = value(HEIGHT, i, 0, j);
            if (d0 != NO_VALUE) {
                out[0] = 0.0D;
                out[1] = heightToOffset(d0);
                return;
            }
            double sum = 0.0D;
            double weights = 0.0D;
            double nearest = Double.POSITIVE_INFINITY;
            for (int c = 0; c < count; c++) {
                Data d = list[c];
                int bx = allX[c] << 2;
                int bz = allZ[c] << 2;
                for (int k = 0; k < COLUMNS; k++) {
                    double h = d.heights[k];
                    if (h == NO_VALUE) {
                        continue;
                    }
                    double d3 = length((double) (i - (bx + columnX(k))), (double) (j - (bz + columnZ(k))));
                    if (!(d3 > (double) HEIGHT_RANGE_CELLS)) {
                        if (d3 < nearest) {
                            nearest = d3;
                        }
                        double d4 = 1.0D / (d3 * d3 * d3 * d3);
                        sum += h * d4;
                        weights += d4;
                    }
                }
            }
            if (nearest == Double.POSITIVE_INFINITY) {
                out[0] = 1.0D;
                out[1] = 0.0D;
                return;
            }
            double d1 = sum / weights;
            double d2 = clamp(nearest / (double) (HEIGHT_RANGE_CELLS + 1), 0.0D, 1.0D);
            d2 = 3.0D * d2 * d2 - 2.0D * d2 * d2 * d2;
            out[0] = d2;
            out[1] = heightToOffset(d1);
        }

        private static double heightToOffset(double height) {
            double d1 = height + 0.5D;
            double d2 = ((d1 % 8.0D) + 8.0D) % 8.0D;
            return 1.0D * (32.0D * (d1 - 128.0D) - 3.0D * (d1 - 120.0D) * d2 + 3.0D * d2 * d2) / (128.0D * (32.0D - 3.0D * d2));
        }

        /** Blender.blendDensity: the density at a noise corner, moved toward the old chunks' right at the border. */
        public double blendDensity(int x, int y, int z, double density) {
            int i = x >> 2;
            int j = y / 8;
            int k = z >> 2;
            double d0 = value(DENSITY, i, j, k);
            if (d0 != NO_VALUE) {
                return d0;
            }
            double sum = 0.0D;
            double weights = 0.0D;
            double nearest = Double.POSITIVE_INFINITY;
            int first = Math.max(0, j - 1 - columnMinY());
            int last = Math.min(CELLS, j + 1 - columnMinY());
            for (int c = 0; c < nearCount; c++) {
                Data d = near[c];
                int bx = nearX[c] << 2;
                int bz = nearZ[c] << 2;
                for (int l = 0; l < COLUMNS; l++) {
                    double[] column = d.densities[l];
                    if (column == null) {
                        continue;
                    }
                    int cx = bx + columnX(l);
                    int cz = bz + columnZ(l);
                    for (int k1 = first; k1 < last; ++k1) {
                        int cellY = k1 + columnMinY();
                        double d3 = length((double) (i - cx), (double) ((j - cellY) * 2), (double) (k - cz));
                        if (!(d3 > 2.0D)) {
                            if (d3 < nearest) {
                                nearest = d3;
                            }
                            double d4 = 1.0D / (d3 * d3 * d3 * d3);
                            sum += column[k1] * 0.1D * d4;
                            weights += d4;
                        }
                    }
                }
            }
            if (nearest == Double.POSITIVE_INFINITY) {
                return density;
            }
            double d1 = sum / weights;
            double d2 = clamp(nearest / 3.0D, 0.0D, 1.0D);
            return d1 + d2 * (density - d1);
        }

        /** Blender.blendBiome at quart qx, qz: the nearest old biome within half the range, or -1 for none. */
        public int blendBiome(int qx, int qz) {
            double d0 = (double) qx + SHIFT.getValue((double) qx, 0.0D, (double) qz) * 12.0D;
            double d1 = (double) qz + SHIFT.getValue((double) qz, (double) qx, 0.0D) * 12.0D;
            double nearest = Double.POSITIVE_INFINITY;
            int biome = -1;
            for (int c = 0; c < count; c++) {
                Data d = list[c];
                int bx = allX[c] << 2;
                int bz = allZ[c] << 2;
                for (int k = 0; k < COLUMNS; k++) {
                    // BlendingData.biomes holds a holder wherever a column was calculated; one 1.18 has no
                    // counterpart of (a modded 1.12 biome) still takes the place.
                    if (d.heights[k] == NO_VALUE) {
                        continue;
                    }
                    double d3 = length(d0 - (double) (bx + columnX(k)), d1 - (double) (bz + columnZ(k)));
                    if (!(d3 > (double) HEIGHT_RANGE_CELLS)) {
                        if (d3 < nearest) {
                            biome = d.biomes[k];
                            nearest = d3;
                        }
                    }
                }
            }
            if (nearest == Double.POSITIVE_INFINITY) {
                return -1;
            }
            double d2 = clamp(nearest / (double) (HEIGHT_RANGE_CELLS + 1), 0.0D, 1.0D);
            return d2 > 0.5D ? -1 : biome < 0 ? -2 : biome;
        }

        /** Whether any old chunk in range has edge columns (else blending changes nothing but the old floor). */
        public boolean hasEdges() {
            for (int c = 0; c < count; c++) {
                if (list[c].hasEdges()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Blender.addAroundOldChunksCarvingMaskFilter: carvers leave alone what lies within 4 blocks of an old chunk's
     * 0..255 box (the chunk itself when it is old, and its old neighbors), measured from a jittered point.
     */
    public static final class CarveMask {
        private final double[][] offsets;

        /**
         * @param self the chunk itself is old (it is being upgraded)
         * @param oldSides the Direction8 bits whose neighbor is old (sideByGenerationAge(..., true))
         * @return the mask, or null when neither applies
         */
        public static CarveMask of(boolean self, int oldSides) {
            if (!self && oldSides == 0) {
                return null;
            }
            return new CarveMask(self, oldSides);
        }

        private CarveMask(boolean self, int oldSides) {
            int n = (self ? 1 : 0) + Integer.bitCount(oldSides);
            offsets = new double[n][];
            int i = 0;
            if (self) {
                offsets[i++] = new double[] {0.0D, 0.0D};
            }
            for (int d = 0; d < 8; d++) {
                if ((oldSides & 1 << d) != 0) {
                    offsets[i++] = new double[] {(double) (STEPS[d][0] * 16), (double) (STEPS[d][1] * 16)};
                }
            }
        }

        /** The filter at chunk-local x, z and block y: true leaves the block uncarved. */
        public boolean masked(int x, int y, int z) {
            double d0 = (double) x + 0.5D + SHIFT.getValue((double) x, (double) y, (double) z) * 4.0D;
            double d1 = (double) y + 0.5D + SHIFT.getValue((double) y, (double) z, (double) x) * 4.0D;
            double d2 = (double) z + 0.5D + SHIFT.getValue((double) z, (double) x, (double) y) * 4.0D;
            double best = Double.POSITIVE_INFINITY;
            for (double[] o : offsets) {
                double d = distanceToCube(d0 - 8.0D - o[0], d1 - OLD_CHUNK_CENTER_Y, d2 - 8.0D - o[1], 8.0D, OLD_CHUNK_Y_RADIUS, 8.0D);
                if (d < best) {
                    best = d;
                }
            }
            return best < 4.0D;
        }

        private static double distanceToCube(double x, double y, double z, double rx, double ry, double rz) {
            double d0 = Math.abs(x) - rx;
            double d1 = Math.abs(y) - ry;
            double d2 = Math.abs(z) - rz;
            return length(Math.max(0.0D, d0), Math.max(0.0D, d1), Math.max(0.0D, d2));
        }
    }
}
