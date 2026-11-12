package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.Arrays;
import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.*;

/**
 * Fossils (CavePlacements FOSSIL_UPPER / FOSSIL_LOWER, CaveFeatures FOSSIL_COAL / FOSSIL_DIAMONDS) as 1.18.2 places
 * them: FossilFeature with its two structure templates (FossilTables), StructurePlaceSettings with a random rotation
 * and the feature's random (so BlockRotProcessor draws from it, one nextFloat per template block), and
 * StructureTemplate.placeInWorld: every block of a template goes through the processors before any is placed, so
 * ProtectedBlockProcessor reads the world as it stood before that template.
 */
final class Fossils {
    private Fossils() {
    }

    /** bone_block[axis=x/y/z]. */
    static final int[] BONE = {register("bone_block[axis=x]"), register("bone_block[axis=y]"), register("bone_block[axis=z]")};

    private static final int[][] BASE_CODES = codes(FossilTables.BASE_STATES, FossilTables.BASE_STATE);
    private static final int[][] COAL_CODES = codes(FossilTables.COAL_STATES, FossilTables.COAL_STATE);

    private static int[][] codes(String[][] states, byte[][] index) {
        int[][] out = new int[index.length][];
        for (int t = 0; t < index.length; t++) {
            out[t] = new int[index[t].length];
            for (int b = 0; b < index[t].length; b++) {
                out[t][b] = register(states[t][index[t][b]]);
            }
        }
        return out;
    }

    static List<Placed> placed() {
        return Arrays.asList(
            new Placed("fossil_upper", new Fossil(false), RARITY, 64, IN_SQUARE, HEIGHT_UNIFORM, 0, 319, BIOME),
            new Placed("fossil_lower", new Fossil(true), RARITY, 64, IN_SQUARE, HEIGHT_UNIFORM, -64, -8, BIOME));
    }

    // Rotation.values(): NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90.
    private static final int NONE = 0;
    private static final int CW90 = 1;
    private static final int CW180 = 2;
    private static final int CCW90 = 3;

    /** BlockState.rotate for bone blocks (RotatedPillarBlock: a quarter turn swaps x and z). */
    private static int rotate(int code, int rotation) {
        if (rotation == CW90 || rotation == CCW90) {
            if (code == BONE[0]) {
                return BONE[2];
            }
            if (code == BONE[2]) {
                return BONE[0];
            }
        }
        return code;
    }

    /** ProtectedBlockProcessor(features_cannot_replace). */
    private static boolean protectedBlock(int code) {
        String n = name(code);
        return code == BEDROCK || n.equals("spawner") || n.equals("chest") || n.equals("end_portal_frame");
    }

    static final class Fossil implements Feature {
        final boolean diamonds;
        private int[] px = new int[64];
        private int[] py = new int[64];
        private int[] pz = new int[64];
        private int[] ps = new int[64];

        Fossil(boolean diamonds) {
            this.diamonds = diamonds;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int rotation = random.nextInt(4);
            int t = random.nextInt(FossilTables.BASE_SIZE.length);
            int[] size = FossilTables.BASE_SIZE[t];
            int minX = (x >> 4 << 4) - 16;
            int minZ = (z >> 4 << 4) - 16;
            int maxX = (x >> 4 << 4) + 15 + 16;
            int maxZ = (z >> 4 << 4) + 15 + 16;
            boolean quarter = rotation == CW90 || rotation == CCW90;
            int sx = quarter ? size[2] : size[0];
            int sz = quarter ? size[0] : size[2];
            int bx = x - sx / 2;
            int bz = z - sz / 2;
            int j = y;
            for (int k = 0; k < sx; ++k) {
                for (int l = 0; l < sz; ++l) {
                    j = Math.min(j, level.oceanFloor(bx + k, bz + l));
                }
            }
            int i1 = Math.max(j - 15 - random.nextInt(10), -64 + 10);
            // getZeroPositionWithTransform(bx, i1, bz, NONE, rotation) with the template's own size.
            int p91 = size[0] - 1;
            int p92 = size[2] - 1;
            int zx = bx;
            int zz = bz;
            switch (rotation) {
                case CCW90:
                    zz += p91;
                    break;
                case CW90:
                    zx += p92;
                    break;
                case CW180:
                    zx += p91;
                    zz += p92;
                    break;
                default:
                    break;
            }
            int zy = i1;
            // The template's bounding box: corners (0,0,0) and the transformed (size - 1), moved to the zero position.
            int ex = transformX(p91, p92, rotation);
            int ez = transformZ(p91, p92, rotation);
            int x0 = zx + Math.min(0, ex);
            int x1 = zx + Math.max(0, ex);
            int z0 = zz + Math.min(0, ez);
            int z1 = zz + Math.max(0, ez);
            int y0 = zy;
            int y1 = zy + size[1] - 1;
            int empty = 0;
            for (int c = 0; c < 8; c++) {
                int s = level.get((c & 1) == 0 ? x0 : x1, (c & 2) == 0 ? y0 : y1, (c & 4) == 0 ? z0 : z1);
                if (isAir(s) || s == LAVA || s == WATER) {
                    empty++;
                }
            }
            if (empty > 4) {
                return false;
            }
            placeInWorld(level, random, FossilTables.BASE_POS[t], BASE_CODES[t], 0.9F, false, rotation, zx, zy, zz,
                minX, maxX, minZ, maxZ);
            placeInWorld(level, random, FossilTables.COAL_POS[t], COAL_CODES[t], 0.1F, diamonds, rotation, zx, zy, zz,
                minX, maxX, minZ, maxZ);
            return true;
        }

        private static int transformX(int x, int z, int rotation) {
            switch (rotation) {
                case CCW90:
                    return z;
                case CW90:
                    return -z;
                case CW180:
                    return -x;
                default:
                    return x;
            }
        }

        private static int transformZ(int x, int z, int rotation) {
            switch (rotation) {
                case CCW90:
                    return -x;
                case CW90:
                    return x;
                case CW180:
                    return -z;
                default:
                    return z;
            }
        }

        private void placeInWorld(Region level, XoroRandom random, int[] pos, int[] codes, float integrity, boolean diamonds,
                                  int rotation, int zx, int zy, int zz, int minX, int maxX, int minZ, int maxZ) {
            // getRandomPalette: one palette, still a draw.
            random.nextInt(1);
            int n = 0;
            if (px.length < codes.length) {
                px = new int[codes.length];
                py = new int[codes.length];
                pz = new int[codes.length];
                ps = new int[codes.length];
            }
            // processBlockInfos: every block through BlockRotProcessor, (RuleProcessor), ProtectedBlockProcessor first.
            for (int b = 0; b < codes.length; b++) {
                int x = zx + transformX(pos[b * 3], pos[b * 3 + 2], rotation);
                int y = zy + pos[b * 3 + 1];
                int z = zz + transformZ(pos[b * 3], pos[b * 3 + 2], rotation);
                if (!(random.nextFloat() <= integrity)) {
                    continue;
                }
                int state = codes[b];
                if (diamonds && state == COAL_ORE) {
                    state = DEEPSLATE_DIAMOND_ORE;
                }
                if (protectedBlock(level.get(x, y, z))) {
                    continue;
                }
                px[n] = x;
                py[n] = y;
                pz[n] = z;
                ps[n] = state;
                n++;
            }
            for (int b = 0; b < n; b++) {
                int x = px[b];
                int z = pz[b];
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    level.set(x, py[b], z, rotate(ps[b], rotation));
                }
            }
        }
    }
}
