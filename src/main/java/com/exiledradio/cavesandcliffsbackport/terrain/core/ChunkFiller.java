package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.Arrays;

/**
 * 1.18.2's noise fill for one chunk (NoiseBasedChunkGenerator.doFill with NoiseChunk, the aquifer and the ore
 * veinifier): stone, air, water, lava and ore veins from y -64 to 319, before surface rules, carvers and features.
 *
 * One instance per thread; it keeps its scratch arrays between chunks.
 */
public final class ChunkFiller {
    public static final byte AIR = 0;
    public static final byte STONE = 1;
    public static final byte WATER = 2;
    public static final byte LAVA = 3;
    public static final byte COPPER_ORE = 4;
    public static final byte RAW_COPPER_BLOCK = 5;
    public static final byte GRANITE = 6;
    public static final byte DEEPSLATE_IRON_ORE = 7;
    public static final byte RAW_IRON_BLOCK = 8;
    public static final byte TUFF = 9;

    private static final int MIN_Y = OverworldNoise.MIN_Y;
    private static final int HEIGHT = OverworldNoise.HEIGHT;
    private static final int CELLS_Y = HEIGHT / 8;
    private static final int CORNERS_Y = CELLS_Y + 1;
    private static final int CORNERS = 5 * 5 * CORNERS_Y;

    private static final int F_MAIN = 0;
    private static final int F_NOODLE = 1;
    private static final int F_NOODLE_THICKNESS = 2;
    private static final int F_NOODLE_A = 3;
    private static final int F_NOODLE_B = 4;
    private static final int F_VEIN = 5;
    private static final int F_VEIN_A = 6;
    private static final int F_VEIN_B = 7;
    private static final int FUNCTIONS = 8;

    private final OverworldNoise noise;
    private final ColumnCache columns;
    private final Aquifer aquifer;
    private final double[][] corners = new double[FUNCTIONS][CORNERS];
    private final double[] column = new double[OverworldNoise.COL_SIZE];
    // The 8 corners of the cell being filled, per function: n000 n100 n010 n110 n001 n101 n011 n111.
    private final double[] cell = new double[FUNCTIONS * 8];
    private final int[] cellCorner = new int[8];

    // Corners are computed only as far as something reads them. Per corner: sloped cheese (the terrain shape
    // without caves) and what is known from it, then one bit per group of functions computed so far.
    private final double[] sloped = new double[CORNERS];
    private final byte[] known = new byte[CORNERS];
    private final byte[] done = new byte[CORNERS];
    /** Sloped cheese is negative whatever the 3D noise says (far above the ground): not even that is computed. */
    private static final byte SKY = 0;
    /** Sloped cheese is computed and negative, which makes the main density negative: caves are not computed. */
    private static final byte NEGATIVE = 1;
    /** Sloped cheese is computed; the main density needs the caves. */
    private static final byte GROUND = 2;
    private static final int DONE_MAIN = 1;
    private static final int DONE_NOODLE_TOGGLE = 2;
    private static final int DONE_NOODLE = 4;
    private static final int DONE_VEIN_TOGGLE = 8;
    private static final int DONE_VEIN = 16;
    /** The base 3D noise stays inside this: 16 octaves of at most 2 each, weighted to sum under 2. */
    private static final double BASE_3D_BOUND = 2.001D;
    private final double[] columnOffset = new double[25];
    private final double[] columnFactor = new double[25];
    private final double[] columnJaggedness = new double[25];
    private int baseX;
    private int baseZ;

    public ChunkFiller(OverworldNoise noise) {
        this(noise, new ColumnCache(noise));
    }

    public ChunkFiller(OverworldNoise noise, ColumnCache columns) {
        this.noise = noise;
        this.columns = columns;
        this.aquifer = new Aquifer(noise, columns);
    }

    private static int corner(int xi, int zi, int yi) {
        return (xi * 5 + zi) * CORNERS_Y + yi;
    }

    /**
     * Fills out (16 * 16 * 384 bytes, index (x * 16 + z) * 384 + (y + 64)) with the block codes of chunk cx, cz.
     */
    public void fill(int cx, int cz, byte[] out) {
        OverworldNoise noise = this.noise;
        baseX = cx << 4;
        baseZ = cz << 4;
        aquifer.begin(cx, cz);
        double jaggedMax = noise.jaggedMax();
        for (int xi = 0; xi <= 4; xi++) {
            int x = baseX + xi * 4;
            for (int zi = 0; zi <= 4; zi++) {
                int z = baseZ + zi * 4;
                columns.get(x, z, column);
                double offset = column[OverworldNoise.COL_OFFSET];
                double factor = column[OverworldNoise.COL_FACTOR];
                double jaggedness = column[OverworldNoise.COL_JAGGEDNESS];
                columnOffset[xi * 5 + zi] = offset;
                columnFactor[xi * 5 + zi] = factor;
                columnJaggedness[xi * 5 + zi] = jaggedness;
                int at = corner(xi, zi, 0);
                for (int yi = 0; yi < CORNERS_Y; yi++) {
                    int y = MIN_Y + yi * 8;
                    done[at + yi] = 0;
                    // From y -40 up the bottom slide leaves the density alone, and the top slide only pulls it
                    // toward a negative value, so a negative sloped cheese means a negative main density.
                    if (y >= -40 && OverworldNoise.slopedCheeseWithoutNoise(y, offset, factor, jaggedness * jaggedMax)
                            + BASE_3D_BOUND < 0.0D) {
                        known[at + yi] = SKY;
                        continue;
                    }
                    double v = noise.slopedCheese(x, y, z, offset, factor, jaggedness);
                    sloped[at + yi] = v;
                    known[at + yi] = y >= -40 && v < -2.0D * SURE ? NEGATIVE : GROUND;
                }
            }
        }
        int[] cc = cellCorner;
        for (int xi = 0; xi < 4; xi++) {
            for (int zi = 0; zi < 4; zi++) {
                for (int yi = CELLS_Y - 1; yi >= 0; yi--) {
                    cc[0] = corner(xi, zi, yi);
                    cc[1] = corner(xi + 1, zi, yi);
                    cc[2] = cc[0] + 1;
                    cc[3] = cc[1] + 1;
                    cc[4] = corner(xi, zi + 1, yi);
                    cc[5] = corner(xi + 1, zi + 1, yi);
                    cc[6] = cc[4] + 1;
                    cc[7] = cc[5] + 1;
                    fillCell(baseX + xi * 4, MIN_Y + yi * 8, baseZ + zi * 4, xi * 4, zi * 4, out);
                }
            }
        }
    }

    /** Makes sure the given function group is computed at the cell's 8 corners and loads it into cell[]. */
    private void need(int bit) {
        OverworldNoise noise = this.noise;
        int[] cc = cellCorner;
        for (int i = 0; i < 8; i++) {
            int c = cc[i];
            if ((done[c] & bit) == 0) {
                done[c] |= bit;
                int column = c / CORNERS_Y;
                int x = baseX + (column / 5) * 4;
                int z = baseZ + (column % 5) * 4;
                int y = MIN_Y + (c % CORNERS_Y) * 8;
                switch (bit) {
                    case DONE_MAIN: {
                        double v = known[c] == SKY
                            ? noise.slopedCheese(x, y, z, columnOffset[column], columnFactor[column], columnJaggedness[column])
                            : sloped[c];
                        corners[F_MAIN][c] = noise.mainDensity(x, y, z, v);
                        break;
                    }
                    case DONE_NOODLE_TOGGLE:
                        corners[F_NOODLE][c] = noise.noodleToggle(x, y, z);
                        break;
                    case DONE_NOODLE:
                        corners[F_NOODLE_THICKNESS][c] = noise.noodleThickness(x, y, z);
                        corners[F_NOODLE_A][c] = noise.noodleRidgeA(x, y, z);
                        corners[F_NOODLE_B][c] = noise.noodleRidgeB(x, y, z);
                        break;
                    case DONE_VEIN_TOGGLE:
                        corners[F_VEIN][c] = noise.veinToggle(x, y, z);
                        break;
                    default:
                        corners[F_VEIN_A][c] = noise.veinA(x, y, z);
                        corners[F_VEIN_B][c] = noise.veinB(x, y, z);
                        break;
                }
            }
        }
        switch (bit) {
            case DONE_MAIN:
                load(F_MAIN);
                break;
            case DONE_NOODLE_TOGGLE:
                load(F_NOODLE);
                break;
            case DONE_NOODLE:
                load(F_NOODLE_THICKNESS);
                load(F_NOODLE_A);
                load(F_NOODLE_B);
                break;
            case DONE_VEIN_TOGGLE:
                load(F_VEIN);
                break;
            default:
                load(F_VEIN_A);
                load(F_VEIN_B);
                break;
        }
    }

    private void load(int f) {
        double[] v = corners[f];
        double[] c = cell;
        int[] cc = cellCorner;
        int o = f * 8;
        for (int i = 0; i < 8; i++) {
            c[o + i] = v[cc[i]];
        }
    }

    /** Mth.lerp3 on the cell's corners of function f (X, then Y, then Z), as the cell cache computes it. */
    private double lerp3(int f, double tx, double ty, double tz) {
        double[] c = cell;
        int o = f * 8;
        double a = c[o] + tx * (c[o + 1] - c[o]);
        double b = c[o + 2] + tx * (c[o + 3] - c[o + 2]);
        double d = c[o + 4] + tx * (c[o + 5] - c[o + 4]);
        double e = c[o + 6] + tx * (c[o + 7] - c[o + 6]);
        double ab = a + ty * (b - a);
        double de = d + ty * (e - d);
        return ab + tz * (de - ab);
    }

    /** The interpolator's running value (Y, then X, then Z), as the ore veinifier reads it. */
    private double lerpYXZ(int f, double tx, double ty, double tz) {
        double[] c = cell;
        int o = f * 8;
        double xz00 = c[o] + ty * (c[o + 2] - c[o]);
        double xz10 = c[o + 1] + ty * (c[o + 3] - c[o + 1]);
        double xz01 = c[o + 4] + ty * (c[o + 6] - c[o + 4]);
        double xz11 = c[o + 5] + ty * (c[o + 7] - c[o + 5]);
        double z0 = xz00 + tx * (xz10 - xz00);
        double z1 = xz01 + tx * (xz11 - xz01);
        return z0 + tz * (z1 - z0);
    }

    /**
     * An interpolated value lies between the smallest and largest of its 8 corners, give or take rounding far
     * smaller than this. Corners all above it make every block of the cell positive; all below minus it, negative.
     */
    private static final double SURE = 1.0E-6D;

    private static double min8(double[] c, int o) {
        return Math.min(Math.min(Math.min(c[o], c[o + 1]), Math.min(c[o + 2], c[o + 3])),
            Math.min(Math.min(c[o + 4], c[o + 5]), Math.min(c[o + 6], c[o + 7])));
    }

    private static double max8(double[] c, int o) {
        return Math.max(Math.max(Math.max(c[o], c[o + 1]), Math.max(c[o + 2], c[o + 3])),
            Math.max(Math.max(c[o + 4], c[o + 5]), Math.max(c[o + 6], c[o + 7])));
    }

    private static void fillCellWith(byte[] out, int localX, int localZ, int cellY, byte code) {
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                int at = ((localX + dx) * 16 + localZ + dz) * HEIGHT + (cellY - MIN_Y);
                Arrays.fill(out, at, at + 8, code);
            }
        }
    }

    private void fillCell(int cellX, int cellY, int cellZ, int localX, int localZ, byte[] out) {
        double[] c = cell;
        int[] cc = cellCorner;
        boolean empty = true;
        for (int i = 0; i < 8; i++) {
            int corner = cc[i];
            if ((done[corner] & DONE_MAIN) != 0 ? !(corners[F_MAIN][corner] < -SURE) : known[corner] == GROUND) {
                empty = false;
                break;
            }
        }
        if (empty) {
            // Nothing solid from the terrain: air or fluid, unless a barrier between aquifers fills in.
            int all = aquifer.quickCell(cellX, cellY, cellZ);
            if (all != Aquifer.UNDECIDED) {
                fillCellWith(out, localX, localZ, cellY, (byte) all);
                return;
            }
        }
        need(DONE_MAIN);
        empty = max8(c, F_MAIN * 8) < -SURE;
        boolean noodles = false;
        boolean veins = false;
        boolean inVeinRange = cellY <= 50 && cellY + 7 >= -60;
        if (!empty) {
            // The noodle caves are off wherever their toggle is below 0.
            need(DONE_NOODLE_TOGGLE);
            noodles = !(max8(c, F_NOODLE * 8) < -SURE);
            if (noodles) {
                need(DONE_NOODLE);
            }
            // Ore veins need the toggle strength to reach 0.4, between y -60 and 50.
            if (inVeinRange) {
                need(DONE_VEIN_TOGGLE);
                veins = Math.max(max8(c, F_VEIN * 8), -min8(c, F_VEIN * 8)) >= 0.4D - SURE;
                if (veins) {
                    need(DONE_VEIN);
                }
            }
            if (min8(c, F_MAIN * 8) > SURE && !noodles) {
                // Solid throughout.
                if (!veins) {
                    fillCellWith(out, localX, localZ, cellY, STONE);
                    return;
                }
                for (int dx = 0; dx < 4; dx++) {
                    for (int dz = 0; dz < 4; dz++) {
                        int at = ((localX + dx) * 16 + localZ + dz) * HEIGHT + (cellY - MIN_Y);
                        for (int dy = 0; dy < 8; dy++) {
                            out[at + dy] = (byte) vein(cellX + dx, cellY + dy, cellZ + dz, (double) dx / 4.0D,
                                (double) dy / 8.0D, (double) dz / 4.0D);
                        }
                    }
                }
                return;
            }
        }
        emptyNoodlesPending = empty;
        boolean emptyVeinsChecked = false;
        double c0 = c[0];
        double c1 = c[1];
        double c2 = c[2];
        double c3 = c[3];
        double c4 = c[4];
        double c5 = c[5];
        double c6 = c[6];
        double c7 = c[7];
        for (int dx = 0; dx < 4; dx++) {
            int x = cellX + dx;
            double tx = (double) dx / 4.0D;
            // Mth.lerp3: X first, then Y, then Z.
            double a0 = c0 + tx * (c1 - c0);
            double a1 = c2 + tx * (c3 - c2);
            double a2 = c4 + tx * (c5 - c4);
            double a3 = c6 + tx * (c7 - c6);
            for (int dy = 0; dy < 8; dy++) {
                int y = cellY + dy;
                double ty = (double) dy / 8.0D;
                double b0 = a0 + ty * (a1 - a0);
                double b1 = a2 + ty * (a3 - a2);
                for (int dz = 0; dz < 4; dz++) {
                    int z = cellZ + dz;
                    double tz = (double) dz / 4.0D;
                    int code;
                    if (empty) {
                        code = aquifer.quick(x, y, z);
                        if (code == Aquifer.UNDECIDED) {
                            if (emptyNoodlesPending) {
                                emptyNoodlesPending = false;
                                need(DONE_NOODLE_TOGGLE);
                                noodles = !(max8(c, F_NOODLE * 8) < -SURE);
                                if (noodles) {
                                    need(DONE_NOODLE);
                                }
                            }
                            code = aquifer.full(x, y, z, density(b0 + tz * (b1 - b0), noodles, tx, ty, tz));
                        }
                    } else {
                        code = aquifer.computeSubstance(x, y, z, density(b0 + tz * (b1 - b0), noodles, tx, ty, tz));
                    }
                    if (code < 0) {
                        if (empty && inVeinRange && !emptyVeinsChecked) {
                            // A barrier between aquifers made rock in a cell with none: it can still be a vein.
                            emptyVeinsChecked = true;
                            need(DONE_VEIN_TOGGLE);
                            veins = Math.max(max8(c, F_VEIN * 8), -min8(c, F_VEIN * 8)) >= 0.4D - SURE;
                            if (veins) {
                                need(DONE_VEIN);
                            }
                        }
                        code = veins ? vein(x, y, z, tx, ty, tz) : STONE;
                    }
                    out[((localX + dx) * 16 + localZ + dz) * HEIGHT + (y - MIN_Y)] = (byte) code;
                }
            }
        }
    }

    private boolean emptyNoodlesPending;

    /** final_density at a block of the cell, from the interpolated main density. */
    private double density(double main, boolean noodles, double tx, double ty, double tz) {
        double noodle = noodles
            ? OverworldNoise.noodleDensity(lerp3(F_NOODLE, tx, ty, tz), lerp3(F_NOODLE_THICKNESS, tx, ty, tz),
                lerp3(F_NOODLE_A, tx, ty, tz), lerp3(F_NOODLE_B, tx, ty, tz))
            : 64.0D;
        return OverworldNoise.finalDensity(main, noodle);
    }

    /** OreVeinifier: the block of a solid position. */
    private int vein(int x, int y, int z, double tx, double ty, double tz) {
        double toggle = lerpYXZ(F_VEIN, tx, ty, tz);
        boolean copper = toggle > 0.0D;
        double strength = Math.abs(toggle);
        int above = (copper ? 50 : -8) - y;
        int below = y - (copper ? 0 : -60);
        if (below < 0 || above < 0) {
            return STONE;
        }
        int edge = Math.min(above, below);
        double fade = OverworldNoise.clampedMap((double) edge, 0.0D, 20.0D, -0.2D, 0.0D);
        if (strength + fade < (double) 0.4F) {
            return STONE;
        }
        Xoroshiro random = noise.oreRandom.at(x, y, z);
        if (random.nextFloat() > 0.7F) {
            return STONE;
        }
        if (OverworldNoise.veinRidged(lerpYXZ(F_VEIN_A, tx, ty, tz), lerpYXZ(F_VEIN_B, tx, ty, tz)) >= 0.0D) {
            return STONE;
        }
        double richness = OverworldNoise.clampedMap(strength, (double) 0.4F, (double) 0.6F, (double) 0.1F, (double) 0.3F);
        if ((double) random.nextFloat() < richness && noise.veinGap(x, y, z) > (double) -0.3F) {
            if (random.nextFloat() < 0.02F) {
                return copper ? RAW_COPPER_BLOCK : RAW_IRON_BLOCK;
            }
            return copper ? COPPER_ORE : DEEPSLATE_IRON_ORE;
        }
        return copper ? GRANITE : TUFF;
    }

    /** NoiseBasedAquifer, for one chunk at a time. */
    static final class Aquifer {
        private static final int WAY_BELOW_MIN_Y = -2032 << 4;
        private static final int LAVA_LEVEL = -54;
        private static final double FLOWING_UPDATE_SIMILARITY = similarity(10 * 10, 12 * 12);
        private static final int[][] SURFACE_SAMPLING_OFFSETS_IN_CHUNKS = {{-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {-3, 0},
            {-2, 0}, {-1, 0}, {0, 0}, {1, 0}, {-2, 1}, {-1, 1}, {0, 1}, {1, 1}};
        private static final int NOT_COMPUTED = Integer.MIN_VALUE;

        private final OverworldNoise noise;
        private final ColumnCache columns;
        private int minGridX;
        private int minGridY;
        private int minGridZ;
        private int gridSizeX;
        private int gridSizeZ;
        private long[] locations = new long[0];
        private boolean[] located = new boolean[0];
        /** Fluid status per grid cell: the level, and whether its fluid is lava. */
        private int[] statusLevel = new int[0];
        private boolean[] statusLava = new boolean[0];

        Aquifer(OverworldNoise noise, ColumnCache columns) {
            this.noise = noise;
            this.columns = columns;
        }

        void begin(int cx, int cz) {
            minGridX = Math.floorDiv(cx << 4, 16) - 1;
            int maxGridX = Math.floorDiv((cx << 4) + 15, 16) + 1;
            gridSizeX = maxGridX - minGridX + 1;
            minGridY = Math.floorDiv(MIN_Y, 12) - 1;
            int maxGridY = Math.floorDiv(MIN_Y + HEIGHT, 12) + 1;
            int gridSizeY = maxGridY - minGridY + 1;
            minGridZ = Math.floorDiv(cz << 4, 16) - 1;
            int maxGridZ = Math.floorDiv((cz << 4) + 15, 16) + 1;
            gridSizeZ = maxGridZ - minGridZ + 1;
            int size = gridSizeX * gridSizeY * gridSizeZ;
            if (locations.length != size) {
                locations = new long[size];
                located = new boolean[size];
                statusLevel = new int[size];
                statusLava = new boolean[size];
            }
            Arrays.fill(located, false);
            Arrays.fill(statusLevel, NOT_COMPUTED);
            if (summaryState.length != gridSizeY * 4) {
                summaryState = new byte[gridSizeY * 4];
                summaryLevel = new int[gridSizeY * 4];
                summaryLava = new boolean[gridSizeY * 4];
            }
            Arrays.fill(summaryState, (byte) 0);
        }

        private int index(int gx, int gy, int gz) {
            return ((gy - minGridY) * gridSizeZ + (gz - minGridZ)) * gridSizeX + (gx - minGridX);
        }

        private static double similarity(int a, int b) {
            return 1.0D - (double) Math.abs(b - a) / 25.0D;
        }

        private static long pack(int x, int y, int z) {
            return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
        }

        private static int unpackX(long v) {
            return (int) (v >> 38);
        }

        private static int unpackY(long v) {
            return (int) (v << 52 >> 52);
        }

        private static int unpackZ(long v) {
            return (int) (v << 26 >> 38);
        }

        /** The global fluid picker: lava below y -54, the sea above. True for lava. */
        private static boolean globalLava(int y) {
            return y < LAVA_LEVEL;
        }

        private static int globalLevel(int y) {
            return y < LAVA_LEVEL ? LAVA_LEVEL : OverworldNoise.SEA_LEVEL;
        }

        // Summaries of the 12 aquifer centers a position looks at, by (gx0, gy0, gz0). A block's answer comes
        // from the nearest three of them, so when all 12 agree nothing else needs computing:
        // - all the same fluid status: every pressure between them is 0, the block is that status at y;
        // - all levels at least 6 below y: every pressure is negative without the barrier noise, the block is air.
        private byte[] summaryState = new byte[0];
        private int[] summaryLevel = new int[0];
        private boolean[] summaryLava = new boolean[0];
        private static final byte SUMMARY_UNIFORM = 1;
        private static final byte SUMMARY_MIXED = 2;
        /** quick(): the answer needs the full search and the density. */
        static final int UNDECIDED = -2;

        private int summary(int gx0, int gy0, int gz0) {
            int key = ((gy0 - minGridY) * 2 + (gz0 - minGridZ)) * 2 + (gx0 - minGridX);
            if (summaryState[key] == 0) {
                boolean uniform = true;
                int level = 0;
                int max = Integer.MIN_VALUE;
                boolean lava = false;
                boolean first = true;
                for (int ox = 0; ox <= 1; ox++) {
                    for (int oy = -1; oy <= 1; oy++) {
                        for (int oz = 0; oz <= 1; oz++) {
                            int s = status(location(gx0 + ox, gy0 + oy, gz0 + oz));
                            int l = statusLevel[s];
                            boolean v = statusLava[s];
                            if (first) {
                                level = l;
                                lava = v;
                                first = false;
                            } else if (l != level || v != lava) {
                                uniform = false;
                            }
                            max = Math.max(max, l);
                        }
                    }
                }
                summaryState[key] = uniform ? SUMMARY_UNIFORM : SUMMARY_MIXED;
                summaryLevel[key] = uniform ? level : max;
                summaryLava[key] = lava;
            }
            return key;
        }

        /** The block of a position whose density is not above 0, when the summaries decide it; else UNDECIDED. */
        int quick(int x, int y, int z) {
            if (y < LAVA_LEVEL) {
                // The global status below -54 is lava up to -54.
                return LAVA;
            }
            int key = summary(Math.floorDiv(x - 5, 16), Math.floorDiv(y + 1, 12), Math.floorDiv(z - 5, 16));
            if (summaryState[key] == SUMMARY_UNIFORM) {
                return y < summaryLevel[key] ? (summaryLava[key] ? LAVA : WATER) : AIR;
            }
            return y - 6 >= summaryLevel[key] ? AIR : UNDECIDED;
        }

        /**
         * For a whole cell (4x8x4 from x, y, z) of densities not above 0: AIR, WATER or LAVA when every block in
         * it is that, else UNDECIDED.
         */
        int quickCell(int x, int y, int z) {
            if (y + 7 < LAVA_LEVEL) {
                return LAVA;
            }
            if (y < LAVA_LEVEL) {
                return UNDECIDED;
            }
            int gxa = Math.floorDiv(x - 5, 16);
            int gxb = Math.floorDiv(x + 3 - 5, 16);
            int gya = Math.floorDiv(y + 1, 12);
            int gyb = Math.floorDiv(y + 7 + 1, 12);
            int gza = Math.floorDiv(z - 5, 16);
            int gzb = Math.floorDiv(z + 3 - 5, 16);
            int result = UNDECIDED;
            for (int gx = gxa; gx <= gxb; gx++) {
                for (int gy = gya; gy <= gyb; gy++) {
                    for (int gz = gza; gz <= gzb; gz++) {
                        int key = summary(gx, gy, gz);
                        int level = summaryLevel[key];
                        int here;
                        if (summaryState[key] == SUMMARY_UNIFORM) {
                            if (level <= y) {
                                here = AIR;
                            } else if (level > y + 7) {
                                here = summaryLava[key] ? LAVA : WATER;
                            } else {
                                return UNDECIDED;
                            }
                        } else if (y - 6 >= level) {
                            here = AIR;
                        } else {
                            return UNDECIDED;
                        }
                        if (result == UNDECIDED) {
                            result = here;
                        } else if (result != here) {
                            return UNDECIDED;
                        }
                    }
                }
            }
            return result;
        }

        private long location(int gx, int gy, int gz) {
            int index = index(gx, gy, gz);
            if (located[index]) {
                return locations[index];
            }
            Xoroshiro random = noise.aquiferRandom.at(gx, gy, gz);
            int px = gx * 16 + random.nextInt(10);
            int py = gy * 12 + random.nextInt(9);
            int pz = gz * 16 + random.nextInt(10);
            long pos = pack(px, py, pz);
            locations[index] = pos;
            located[index] = true;
            return pos;
        }

        /** Returns the block code, or -1 for solid (the vein check and stone follow). */
        int computeSubstance(int x, int y, int z, double density) {
            if (density > 0.0D) {
                return -1;
            }
            int quick = quick(x, y, z);
            return quick != UNDECIDED ? quick : full(x, y, z, density);
        }

        /** The aquifer's full answer for a density not above 0 at y -54 or higher. */
        int full(int x, int y, int z, double density) {
            int gx0 = Math.floorDiv(x - 5, 16);
            int gy0 = Math.floorDiv(y + 1, 12);
            int gz0 = Math.floorDiv(z - 5, 16);
            int dist1 = Integer.MAX_VALUE;
            int dist2 = Integer.MAX_VALUE;
            int dist3 = Integer.MAX_VALUE;
            long pos1 = 0L;
            long pos2 = 0L;
            long pos3 = 0L;
            for (int ox = 0; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    for (int oz = 0; oz <= 1; oz++) {
                        int gx = gx0 + ox;
                        int gy = gy0 + oy;
                        int gz = gz0 + oz;
                        long pos = location(gx, gy, gz);
                        int ddx = unpackX(pos) - x;
                        int ddy = unpackY(pos) - y;
                        int ddz = unpackZ(pos) - z;
                        int dist = ddx * ddx + ddy * ddy + ddz * ddz;
                        if (dist1 >= dist) {
                            pos3 = pos2;
                            pos2 = pos1;
                            pos1 = pos;
                            dist3 = dist2;
                            dist2 = dist1;
                            dist1 = dist;
                        } else if (dist2 >= dist) {
                            pos3 = pos2;
                            pos2 = pos;
                            dist3 = dist2;
                            dist2 = dist;
                        } else if (dist3 >= dist) {
                            pos3 = pos;
                            dist3 = dist;
                        }
                    }
                }
            }
            int s1 = status(pos1);
            int level1 = statusLevel[s1];
            boolean lava1 = statusLava[s1];
            double d1 = similarity(dist1, dist2);
            int block = y < level1 ? (lava1 ? LAVA : WATER) : AIR;
            if (d1 <= 0.0D) {
                return block;
            }
            if (block == WATER && globalLava(y - 1)) {
                // globalFluidPicker.computeFluid(x, y - 1, z).at(y - 1) is lava exactly when y - 1 < -54.
                return block;
            }
            barrier = Double.NaN;
            int s2 = status(pos2);
            int level2 = statusLevel[s2];
            boolean lava2 = statusLava[s2];
            double d2 = d1 * pressure(x, y, z, level1, lava1, level2, lava2);
            if (density + d2 > 0.0D) {
                return -1;
            }
            int s3 = status(pos3);
            int level3 = statusLevel[s3];
            boolean lava3 = statusLava[s3];
            double d0 = similarity(dist1, dist3);
            if (d0 > 0.0D) {
                double d3 = d1 * d0 * pressure(x, y, z, level1, lava1, level3, lava3);
                if (density + d3 > 0.0D) {
                    return -1;
                }
            }
            double d4 = similarity(dist2, dist3);
            if (d4 > 0.0D) {
                double d5 = d1 * d4 * pressure(x, y, z, level2, lava2, level3, lava3);
                if (density + d5 > 0.0D) {
                    return -1;
                }
            }
            return block;
        }

        /** The barrier noise at the block being decided, computed at most once (NaN until then). */
        private double barrier;

        /** calculatePressure between two aquifers' fluid statuses. */
        private double pressure(int x, int y, int z, int levelA, boolean lavaA, int levelB, boolean lavaB) {
            int ba = y < levelA ? (lavaA ? LAVA : WATER) : AIR;
            int bb = y < levelB ? (lavaB ? LAVA : WATER) : AIR;
            if ((ba == LAVA && bb == WATER) || (ba == WATER && bb == LAVA)) {
                return 2.0D;
            }
            int diff = Math.abs(levelA - levelB);
            if (diff == 0) {
                return 0.0D;
            }
            double mid = 0.5D * (double) (levelA + levelB);
            double d1 = (double) y + 0.5D - mid;
            double half = (double) diff / 2.0D;
            double d9 = half - Math.abs(d1);
            double shape;
            if (d1 > 0.0D) {
                double d11 = 0.0D + d9;
                shape = d11 > 0.0D ? d11 / 1.5D : d11 / 2.5D;
            } else {
                double d15 = 3.0D + d9;
                shape = d15 > 0.0D ? d15 / 3.0D : d15 / 10.0D;
            }
            double n;
            if (!(shape < -2.0D) && !(shape > 2.0D)) {
                if (Double.isNaN(barrier)) {
                    barrier = noise.barrier(x, y, z);
                }
                n = barrier;
            } else {
                n = 0.0D;
            }
            return 2.0D * (n + shape);
        }

        /** getAquiferStatus: the index of the grid cell holding this aquifer center, its status computed. */
        private int status(long pos) {
            int x = unpackX(pos);
            int y = unpackY(pos);
            int z = unpackZ(pos);
            int index = index(Math.floorDiv(x, 16), Math.floorDiv(y, 12), Math.floorDiv(z, 16));
            if (statusLevel[index] == NOT_COMPUTED) {
                computeFluid(x, y, z, index);
            }
            return index;
        }

        private void set(int index, int level, boolean lava) {
            statusLevel[index] = level;
            statusLava[index] = lava;
        }

        private void computeFluid(int x, int y, int z, int index) {
            int globalLevel = globalLevel(y);
            boolean globalLava = globalLava(y);
            int minSurface = Integer.MAX_VALUE;
            int above = y + 12;
            int below = y - 12;
            boolean surfaceFluid = false;
            for (int[] offset : SURFACE_SAMPLING_OFFSETS_IN_CHUNKS) {
                int sx = x + (offset[0] << 4);
                int sz = z + (offset[1] << 4);
                int surface = columns.preliminarySurfaceLevel(sx, sz);
                int top = surface + 8;
                boolean center = offset[0] == 0 && offset[1] == 0;
                if (center && below > top) {
                    set(index, globalLevel, globalLava);
                    return;
                }
                boolean higher = above > top;
                if (higher || center) {
                    // globalFluidPicker.computeFluid(sx, top, sz).at(top) is not air when top is under its level.
                    int level = globalLevel(top);
                    if (top < level) {
                        if (center) {
                            surfaceFluid = true;
                        }
                        if (higher) {
                            set(index, level, globalLava(top));
                            return;
                        }
                    }
                }
                minSurface = Math.min(minSurface, surface);
            }
            int depth = minSurface + 8 - y;
            double d2 = surfaceFluid ? OverworldNoise.clampedMap((double) depth, 0.0D, 64.0D, 1.0D, 0.0D) : 0.0D;
            double d3 = OverworldNoise.clamp(noise.floodedness(x, y, z), -1.0D, 1.0D);
            double d4 = map(d2, 1.0D, 0.0D, -0.3D, 0.8D);
            if (d3 > d4) {
                set(index, globalLevel, globalLava);
                return;
            }
            double d5 = map(d2, 1.0D, 0.0D, -0.8D, 0.4D);
            if (d3 <= d5) {
                set(index, WAY_BELOW_MIN_Y, globalLava);
                return;
            }
            int i2 = Math.floorDiv(x, 16);
            int j2 = Math.floorDiv(y, 40);
            int k2 = Math.floorDiv(z, 16);
            int l2 = j2 * 40 + 20;
            double d0 = noise.spread(i2, j2, k2) * 10.0D;
            int j3 = floor(d0 / 3.0D) * 3;
            int k3 = l2 + j3;
            int l3 = Math.min(minSurface, k3);
            if (k3 <= -10) {
                double d1 = noise.lava(Math.floorDiv(x, 64), Math.floorDiv(y, 40), Math.floorDiv(z, 64));
                if (Math.abs(d1) > 0.3D) {
                    set(index, l3, true);
                    return;
                }
            }
            set(index, l3, globalLava);
        }

        private static double map(double v, double fromA, double fromB, double toA, double toB) {
            return toA + ((v - fromA) / (fromB - fromA)) * (toB - toA);
        }

        private static int floor(double v) {
            int i = (int) v;
            return v < (double) i ? i - 1 : i;
        }
    }
}
