package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.Collections;
import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.*;

/** The amethyst geode (CavePlacements / CaveFeatures AMETHYST_GEODE, GeodeFeature) as 1.18.2 places it. */
final class Geodes {
    private Geodes() {
    }

    static final int AMETHYST_BLOCK = register("amethyst_block");
    static final int BUDDING_AMETHYST = register("budding_amethyst");
    static final int SMOOTH_BASALT = register("smooth_basalt");
    /** Direction.values() order. */
    static final String[] DIRECTION_NAMES = {"down", "up", "north", "south", "west", "east"};
    static final int[] DX = {0, 0, 0, 0, -1, 1};
    static final int[] DY = {-1, 1, 0, 0, 0, 0};
    static final int[] DZ = {0, 0, -1, 1, 0, 0};
    /** Inner placements (small, medium, large bud, cluster) by facing and waterlogged: [kind][facing * 2 + wet]. */
    static final int[][] PLACEMENTS = new int[4][12];

    static {
        String[] kinds = {"small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud", "amethyst_cluster"};
        for (int k = 0; k < 4; k++) {
            for (int f = 0; f < 6; f++) {
                for (int w = 0; w < 2; w++) {
                    PLACEMENTS[k][f * 2 + w] = register(kinds[k] + "[facing=" + DIRECTION_NAMES[f] + ",waterlogged="
                        + (w == 1) + "]");
                }
            }
        }
    }

    static List<Placed> placed(long seed) {
        return Collections.singletonList(new Placed("amethyst_geode", new Geode(seed),
            RARITY, 24, IN_SQUARE, HEIGHT_UNIFORM, -58, 30, BIOME));
    }

    /** Mth.fastInvSqrt(double). */
    static double fastInvSqrt(double x) {
        double d0 = 0.5D * x;
        long i = Double.doubleToRawLongBits(x);
        i = 6910469410427058090L - (i >> 1);
        x = Double.longBitsToDouble(i);
        return x * (1.5D - d0 * x * x);
    }

    /** BlockTags.GEODE_INVALID_BLOCKS: bedrock, water, lava, ice, packed ice, blue ice. */
    private static boolean invalid(int code) {
        if (code == BEDROCK || code == WATER || code == LAVA || code == ICE || code == PACKED_ICE) {
            return true;
        }
        String n = name(code);
        return n.equals("blue_ice") || n.equals("water") || n.equals("lava");
    }

    /**
     * GeodeFeature with AMETHYST_GEODE's configuration. Its NormalNoise (a LegacyRandomSource on the world seed,
     * first octave -4, one amplitude) is the same at every placement, so it is made once.
     */
    static final class Geode implements Feature {
        private static final double FILLING = 1.7D;
        private static final double INNER = 2.2D;
        private static final double MIDDLE = 3.2D;
        private static final double OUTER = 4.2D;
        private static final double CRACK_CHANCE = 0.95D;
        private static final double BASE_CRACK = 2.0D;
        private static final int CRACK_OFFSET = 2;
        private static final double POTENTIAL_PLACEMENTS = 0.35D;
        private static final double ALTERNATE_LAYER0 = 0.083D;
        private static final double NOISE_MULTIPLIER = 0.05D;

        private final DoubleNoise noise;
        /** A bound on |noise * NOISE_MULTIPLIER|, with a margin, for skipping positions that cannot reach the outer layer. */
        private final double noiseBound;
        private final int[] px = new int[4];
        private final int[] py = new int[4];
        private final int[] pz = new int[4];
        private final int[] po = new int[4];
        private final int[] cx = new int[3];
        private final int[] cy = new int[3];
        private final int[] cz = new int[3];
        private int[] list2 = new int[3 * 256];

        Geode(long seed) {
            noise = new DoubleNoise(new LegacyRandom(seed), -4, 1.0D);
            noiseBound = noise.maxValue() * NOISE_MULTIPLIER * 1.000001D + 1.0E-9D;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int k = random.between(3, 4);
            double d0 = (double) k / 6.0D;
            double d1 = 1.0D / Math.sqrt(FILLING);
            double d2 = 1.0D / Math.sqrt(INNER + d0);
            double d3 = 1.0D / Math.sqrt(MIDDLE + d0);
            double d4 = 1.0D / Math.sqrt(OUTER + d0);
            double d5 = 1.0D / Math.sqrt(BASE_CRACK + random.nextDouble() / 2.0D + (k > 3 ? d0 : 0.0D));
            boolean flag = (double) random.nextFloat() < CRACK_CHANCE;
            int l = 0;
            for (int i1 = 0; i1 < k; ++i1) {
                int j1 = random.between(4, 6);
                int k1 = random.between(4, 6);
                int l1 = random.between(4, 6);
                int state = level.get(x + j1, y + k1, z + l1);
                if (isAir(state) || invalid(state)) {
                    ++l;
                    if (l > 1) {
                        return false;
                    }
                }
                px[i1] = x + j1;
                py[i1] = y + k1;
                pz[i1] = z + l1;
                po[i1] = random.between(1, 2);
            }
            int cracks = 0;
            if (flag) {
                int i2 = random.nextInt(4);
                int j2 = k * 2 + 1;
                int ox = i2 == 0 || i2 == 2 ? j2 : 0;
                int oz = i2 == 1 || i2 == 2 ? j2 : 0;
                int[] ys = {7, 5, 1};
                for (int c = 0; c < 3; c++) {
                    cx[c] = x + ox;
                    cy[c] = y + ys[c];
                    cz[c] = z + oz;
                }
                cracks = 3;
            }
            int placements = 0;
            for (int bz = z - 16; bz <= z + 16; bz++) {
                for (int by = y - 16; by <= y + 16; by++) {
                    for (int bx = x - 16; bx <= x + 16; bx++) {
                        // Without the noise, d6 is the sum of the inverse roots; each term carries one d8.
                        double base = 0.0D;
                        for (int p = 0; p < k; p++) {
                            base += fastInvSqrt(distSqr(bx, by, bz, px[p], py[p], pz[p]) + (double) po[p]);
                        }
                        if (base + k * noiseBound < d4) {
                            continue;
                        }
                        double d8 = noise.getValue((double) bx, (double) by, (double) bz) * NOISE_MULTIPLIER;
                        double d6 = 0.0D;
                        double d7 = 0.0D;
                        for (int p = 0; p < k; p++) {
                            d6 += fastInvSqrt(distSqr(bx, by, bz, px[p], py[p], pz[p]) + (double) po[p]) + d8;
                        }
                        for (int c = 0; c < cracks; c++) {
                            d7 += fastInvSqrt(distSqr(bx, by, bz, cx[c], cy[c], cz[c]) + (double) CRACK_OFFSET) + d8;
                        }
                        if (!(d6 < d4)) {
                            if (flag && d7 >= d5 && d6 < d1) {
                                safeSet(level, bx, by, bz, AIR);
                                // Scheduled fluid ticks around it: no block change.
                            } else if (d6 >= d1) {
                                safeSet(level, bx, by, bz, AIR);
                            } else if (d6 >= d2) {
                                boolean flag1 = (double) random.nextFloat() < ALTERNATE_LAYER0;
                                safeSet(level, bx, by, bz, flag1 ? BUDDING_AMETHYST : AMETHYST_BLOCK);
                                if (flag1 && (double) random.nextFloat() < POTENTIAL_PLACEMENTS) {
                                    if (placements * 3 == list2.length) {
                                        list2 = java.util.Arrays.copyOf(list2, list2.length * 2);
                                    }
                                    list2[placements * 3] = bx;
                                    list2[placements * 3 + 1] = by;
                                    list2[placements * 3 + 2] = bz;
                                    placements++;
                                }
                            } else if (d6 >= d3) {
                                safeSet(level, bx, by, bz, CALCITE);
                            } else if (d6 >= d4) {
                                safeSet(level, bx, by, bz, SMOOTH_BASALT);
                            }
                        }
                    }
                }
            }
            for (int n = 0; n < placements; n++) {
                int bx = list2[n * 3];
                int by = list2[n * 3 + 1];
                int bz = list2[n * 3 + 2];
                int kind = random.nextInt(4);
                for (int d = 0; d < 6; d++) {
                    int state = level.get(bx + DX[d], by + DY[d], bz + DZ[d]);
                    boolean wet = (flags(state) & F_FLUID) != 0;
                    if (isAir(state) || state == WATER) {
                        safeSet(level, bx + DX[d], by + DY[d], bz + DZ[d], PLACEMENTS[kind][d * 2 + (wet ? 1 : 0)]);
                        break;
                    }
                }
            }
            return true;
        }

        private static double distSqr(int x, int y, int z, int ox, int oy, int oz) {
            double d0 = (double) x - (double) ox;
            double d1 = (double) y - (double) oy;
            double d2 = (double) z - (double) oz;
            return d0 * d0 + d1 * d1 + d2 * d2;
        }

        private static void safeSet(Region level, int x, int y, int z, int state) {
            if (!Caves.featuresCannotReplace(level.get(x, y, z))) {
                level.set(x, y, z, state);
            }
        }
    }
}
