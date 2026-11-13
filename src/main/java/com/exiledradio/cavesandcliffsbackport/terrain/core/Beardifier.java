package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.18.2's Beardifier: the terrain adaptation around structure pieces, added to the final density of every block
 * (NoiseChunk: cacheAllInCell(add(final_density, beardifier))). Pieces bury themselves (strongholds) or get a
 * beard of ground under them and air over them (every other piece of a structure with adapt_noise); jigsaw
 * junctions get a lighter beard.
 *
 * <p>Build one per chunk with {@link #forChunk}, from the pieces of every structure start that adapts noise and
 * touches the chunk, in the order vanilla's StructureFeatureManager.startsForFeature lists the starts and each
 * start lists its pieces (the sum is in that order, and floating-point addition is only commutative two terms at
 * a time). It keeps vanilla's filter: pieces within 12 blocks of the chunk, and of a jigsaw piece only rigid ones,
 * plus its junctions within 12.
 *
 * <p>Every contribution is exactly 0.0 outside a box around its piece or junction ({@link #touches}), so where no
 * box reaches a noise cell the fill keeps its whole-cell shortcuts.
 *
 * <p>{@link #BEARD_BOX} is 1.19's {@code beard_box} (ancient cities, through Backport Core's terrain adaptation): air
 * through the whole of the piece's box above its ground and ground under it, falling off past the box's sides, top
 * and bottom. It and its pieces' junctions use 1.19.2's kernel (the gaussian alone, as a float, times the slope in
 * double); the rest keep 1.18.2's.
 */
public final class Beardifier {
    public static final int NONE = 0;
    public static final int BURY = 1;
    public static final int BEARD = 2;
    /** 1.19's beard_box. */
    public static final int BEARD_BOX = 3;

    private static final float[] KERNEL = new float[24 * 24 * 24];
    /** 1.19.2's BEARD_KERNEL: exp(-lengthSquared(x, y + 0.5, z) / 16). */
    private static final float[] KERNEL_119 = new float[24 * 24 * 24];

    static {
        for (int i = 0; i < 24; ++i) {
            for (int j = 0; j < 24; ++j) {
                for (int k = 0; k < 24; ++k) {
                    KERNEL[i * 24 * 24 + j * 24 + k] = (float) computeBeardContribution(j - 12, k - 12, i - 12);
                    double x = j - 12;
                    double y = (double) (k - 12) + 0.5D;
                    double z = i - 12;
                    KERNEL_119[i * 24 * 24 + j * 24 + k] = (float) Math.pow(Math.E, -(x * x + y * y + z * z) / 16.0D);
                }
            }
        }
    }

    /** A structure piece: its bounding box (inclusive), noise effect and, for a jigsaw piece, its projection. */
    public static final class Piece {
        public final int minX;
        public final int minY;
        public final int minZ;
        public final int maxX;
        public final int maxY;
        public final int maxZ;
        /** StructurePiece.getNoiseEffect: BEARD for every piece but strongholds' (BURY). */
        public final int effect;
        /** PoolElementStructurePiece (jigsaw), with its ground level delta, projection and junctions. */
        public final boolean jigsaw;
        public final int groundLevelDelta;
        public final boolean rigid;
        /** Junctions: source x, source ground y, source z, three ints each. */
        public final int[] junctions;

        public Piece(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int effect) {
            this(minX, minY, minZ, maxX, maxY, maxZ, effect, false, 0, true, new int[0]);
        }

        public Piece(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int effect, boolean jigsaw,
                     int groundLevelDelta, boolean rigid, int[] junctions) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.effect = effect;
            this.jigsaw = jigsaw;
            this.groundLevelDelta = groundLevelDelta;
            this.rigid = rigid;
            this.junctions = junctions;
        }

        /** StructurePiece.isCloseToChunk. */
        boolean isCloseToChunk(int cx, int cz, int distance) {
            int i = cx << 4;
            int j = cz << 4;
            return maxX >= i - distance && minX <= i + 15 + distance && maxZ >= j - distance && minZ <= j + 15 + distance;
        }
    }

    // Rigid pieces: minX minY(+delta) minZ maxX maxZ effect maxY, and the box where they contribute.
    private static final int RIGID = 7;
    private int rigidCount;
    private int[] rigids = new int[RIGID * 4];
    // Junctions: x, ground y, z, and 1 where 1.19.2's kernel applies (a beard_box piece's).
    private static final int JUNCTION = 4;
    private int junctionCount;
    private int[] junctions = new int[JUNCTION * 8];
    /** Per rigid piece and junction: lo x, lo y, lo z, hi x, hi y, hi z (inclusive) outside which it adds 0.0. */
    private int[] reach = new int[6 * 12];
    private int reachCount;

    private Beardifier() {
    }

    /** The Beardifier vanilla builds for chunk cx, cz from these pieces (Beardifier's constructor), or null if none apply. */
    public static Beardifier forChunk(int cx, int cz, List<Piece> pieces) {
        Beardifier b = new Beardifier();
        int i = cx << 4;
        int j = cz << 4;
        List<int[]> junctionList = new ArrayList<>();
        for (Piece p : pieces) {
            if (!p.isCloseToChunk(cx, cz, 12)) {
                continue;
            }
            if (p.jigsaw) {
                if (p.rigid) {
                    b.addRigid(p);
                }
                for (int n = 0; n < p.junctions.length; n += 3) {
                    int k = p.junctions[n];
                    int l = p.junctions[n + 2];
                    if (k > i - 12 && l > j - 12 && k < i + 15 + 12 && l < j + 15 + 12) {
                        junctionList.add(new int[]{p.junctions[n], p.junctions[n + 1], p.junctions[n + 2],
                            p.effect == BEARD_BOX ? 1 : 0});
                    }
                }
            } else {
                b.addRigid(p);
            }
        }
        // Rigid pieces are summed first, then junctions (compute's two loops).
        for (int[] jn : junctionList) {
            b.addJunction(jn[0], jn[1], jn[2], jn[3]);
        }
        return b.rigidCount + b.junctionCount == 0 ? null : b;
    }

    private void addRigid(Piece p) {
        if (rigidCount * RIGID == rigids.length) {
            rigids = java.util.Arrays.copyOf(rigids, rigids.length * 2);
        }
        int baseY = p.minY + (p.jigsaw ? p.groundLevelDelta : 0);
        int at = rigidCount * RIGID;
        rigids[at] = p.minX;
        rigids[at + 1] = baseY;
        rigids[at + 2] = p.minZ;
        rigids[at + 3] = p.maxX;
        rigids[at + 4] = p.maxZ;
        rigids[at + 5] = p.effect;
        rigids[at + 6] = p.maxY;
        rigidCount++;
        if (p.effect == BURY) {
            // length(dx, dy / 2, dz) >= 6 gives 0.0: |dx| < 6, |dy| < 12, |dz| < 6 at most.
            addReach(p.minX - 5, baseY - 11, p.minZ - 5, p.maxX + 5, baseY + 11, p.maxZ + 5);
        } else if (p.effect == BEARD) {
            // The kernel covers offsets -12..11 in each axis (x and z distances are never negative).
            addReach(p.minX - 11, baseY - 12, p.minZ - 11, p.maxX + 11, baseY + 11, p.maxZ + 11);
        } else if (p.effect == BEARD_BOX) {
            // The kernel's y is the distance below the ground or above the top, 0..11; its x and z likewise.
            addReach(p.minX - 11, Math.min(baseY, p.maxY) - 11, p.minZ - 11, p.maxX + 11, Math.max(baseY, p.maxY) + 11,
                    p.maxZ + 11);
        }
    }

    private void addJunction(int x, int groundY, int z, int modern) {
        if (junctionCount * JUNCTION == junctions.length) {
            junctions = java.util.Arrays.copyOf(junctions, junctions.length * 2);
        }
        int at = junctionCount * JUNCTION;
        junctions[at] = x;
        junctions[at + 1] = groundY;
        junctions[at + 2] = z;
        junctions[at + 3] = modern;
        junctionCount++;
        addReach(x - 12, groundY - 12, z - 12, x + 11, groundY + 11, z + 11);
    }

    private void addReach(int x0, int y0, int z0, int x1, int y1, int z1) {
        if (reachCount * 6 == reach.length) {
            reach = java.util.Arrays.copyOf(reach, reach.length * 2);
        }
        int at = reachCount * 6;
        reach[at] = x0;
        reach[at + 1] = y0;
        reach[at + 2] = z0;
        reach[at + 3] = x1;
        reach[at + 4] = y1;
        reach[at + 5] = z1;
        reachCount++;
    }

    /** Whether any contribution can be other than 0.0 in the box x0..x1, y0..y1, z0..z1 (inclusive). */
    public boolean touches(int x0, int y0, int z0, int x1, int y1, int z1) {
        int[] r = reach;
        for (int n = 0, at = 0; n < reachCount; n++, at += 6) {
            if (x1 >= r[at] && x0 <= r[at + 3] && y1 >= r[at + 1] && y0 <= r[at + 4] && z1 >= r[at + 2] && z0 <= r[at + 5]) {
                return true;
            }
        }
        return false;
    }

    /** Beardifier.compute at a block. */
    public double compute(int i, int j, int k) {
        double d0 = 0.0D;
        int[] r = rigids;
        for (int n = 0, at = 0; n < rigidCount; n++, at += RIGID) {
            int l = Math.max(0, Math.max(r[at] - i, i - r[at + 3]));
            int i1 = j - r[at + 1];
            int j1 = Math.max(0, Math.max(r[at + 2] - k, k - r[at + 4]));
            int effect = r[at + 5];
            if (effect == BURY) {
                d0 += getBuryContribution(l, i1, j1);
            } else if (effect == BEARD) {
                d0 += getBeardContribution(l, i1, j1) * 0.8D;
            } else if (effect == BEARD_BOX) {
                int p = Math.max(0, Math.max(r[at + 1] - j, j - r[at + 6]));
                d0 += getBeardContribution119(l, p, j1, i1) * 0.8D;
            }
        }
        int[] jn = junctions;
        for (int n = 0, at = 0; n < junctionCount; n++, at += JUNCTION) {
            int k1 = i - jn[at];
            int l1 = j - jn[at + 1];
            int i2 = k - jn[at + 2];
            d0 += (jn[at + 3] != 0 ? getBeardContribution119(k1, l1, i2, l1) : getBeardContribution(k1, l1, i2)) * 0.4D;
        }
        return d0;
    }

    private static double getBuryContribution(int x, int y, int z) {
        double d0 = Math.sqrt((double) x * (double) x + (double) y / 2.0D * ((double) y / 2.0D) + (double) z * (double) z);
        return McMath.clampedMap(d0, 0.0D, 6.0D, 1.0D, 0.0D);
    }

    private static double getBeardContribution(int x, int y, int z) {
        int i = x + 12;
        int j = y + 12;
        int k = z + 12;
        if (i >= 0 && i < 24 && j >= 0 && j < 24 && k >= 0 && k < 24) {
            return (double) KERNEL[k * 24 * 24 + i * 24 + j];
        }
        return 0.0D;
    }

    /** 1.19.2's getBeardContribution: the kernel at (x, y, z), its slope from the height above the ground. */
    private static double getBeardContribution119(int x, int y, int z, int yToGround) {
        int i = x + 12;
        int j = y + 12;
        int k = z + 12;
        if (i >= 0 && i < 24 && j >= 0 && j < 24 && k >= 0 && k < 24) {
            double d = (double) yToGround + 0.5D;
            double e = (double) x * (double) x + d * d + (double) z * (double) z;
            double f = -d * fastInvSqrt(e / 2.0D) / 2.0D;
            return f * (double) KERNEL_119[k * 24 * 24 + i * 24 + j];
        }
        return 0.0D;
    }

    private static double computeBeardContribution(int x, int y, int z) {
        double d0 = (double) (x * x + z * z);
        double d1 = (double) y + 0.5D;
        double d2 = d1 * d1;
        double d3 = Math.pow(Math.E, -(d2 / 16.0D + d0 / 16.0D));
        double d4 = -d1 * fastInvSqrt(d2 / 2.0D + d0 / 2.0D) / 2.0D;
        return d4 * d3;
    }

    private static double fastInvSqrt(double v) {
        double d0 = 0.5D * v;
        long i = Double.doubleToRawLongBits(v);
        i = 6910469410427058090L - (i >> 1);
        v = Double.longBitsToDouble(i);
        return v * (1.5D - d0 * v * v);
    }

    /** A hash of 1.19.2's kernel, to compare with its BEARD_KERNEL. */
    public static long kernelHash119() {
        long h = 1L;
        for (float f : KERNEL_119) {
            h = h * 31L + Float.floatToIntBits(f);
        }
        return h;
    }

    /** A hash of the kernel, to compare with vanilla's BEARD_KERNEL. */
    public static long kernelHash() {
        long h = 1L;
        for (float f : KERNEL) {
            h = h * 31L + Float.floatToIntBits(f);
        }
        return h;
    }
}
