package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.Arrays;
import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.*;

/**
 * Lava lakes (MiscOverworldPlacements LAKE_LAVA_UNDERGROUND / LAKE_LAVA_SURFACE, Feature.LAKE) and monster rooms
 * (CavePlacements MONSTER_ROOM / MONSTER_ROOM_DEEP, MonsterRoomFeature) as 1.18.2 places them.
 */
final class Caves {
    private Caves() {
    }

    static final int CAVE_AIR = register("cave_air");
    static final int COBBLESTONE = register("cobblestone");
    static final int MOSSY_COBBLESTONE = register("mossy_cobblestone");
    static final int SPAWNER = register("spawner");
    /** chest[facing=north|east|south|west,type=single,waterlogged=false], by Direction's 2D data value order. */
    static final int[] CHEST = new int[4];
    private static final String[] HORIZONTAL_NAMES = {"south", "west", "north", "east"};

    static {
        for (int i = 0; i < 4; i++) {
            CHEST[i] = register("chest[facing=" + HORIZONTAL_NAMES[i] + ",type=single,waterlogged=false]");
        }
    }

    // Direction.Plane.HORIZONTAL order: north, east, south, west, with 2D data values 2, 3, 0, 1.
    private static final int[] PLANE = {2, 3, 0, 1};
    private static final int[] DX = {0, -1, 0, 1};
    private static final int[] DZ = {1, 0, -1, 0};

    private static int opposite(int d) {
        return (d + 2) & 3;
    }

    private static int clockWise(int d) {
        return (d + 1) & 3;
    }

    private static final int TOP = 319;

    static List<Placed> placed() {
        Lake lake = new Lake(LAVA, STONE);
        return Arrays.asList(
            new Placed("lake_lava_underground", lake,
                RARITY, 9, IN_SQUARE, HEIGHT_UNIFORM, 0, TOP, CUSTOM, 0,
                SURFACE_THRESHOLD_OCEAN_FLOOR, Integer.MIN_VALUE, -5, BIOME).mods(Caves::scanDownToNotAir),
            new Placed("lake_lava_surface", lake,
                RARITY, 200, IN_SQUARE, HEIGHTMAP, WG_WORLD_SURFACE, BIOME),
            new Placed("monster_room", new MonsterRoom(),
                Decorator.COUNT, 10, IN_SQUARE, HEIGHT_UNIFORM, 0, TOP, BIOME),
            new Placed("monster_room_deep", new MonsterRoom(),
                Decorator.COUNT, 4, IN_SQUARE, HEIGHT_UNIFORM, -58, -1, BIOME));
    }

    /**
     * EnvironmentScanPlacement.scanningFor(DOWN, allOf(not(ONLY_IN_AIR), insideWorld(0, -5, 0)), 32), the search
     * condition always true: up to 32 steps down, then one last test.
     */
    private static void scanDownToNotAir(Region level, XoroRandom random, Next next, int x, int y, int z) {
        for (int i = 0; i < 32; i++) {
            if (notAirAndFiveAboveBottom(level, x, y, z)) {
                next.at(x, y, z);
                return;
            }
            --y;
            if (Region.outsideBuildHeight(y)) {
                return;
            }
        }
        if (notAirAndFiveAboveBottom(level, x, y, z)) {
            next.at(x, y, z);
        }
    }

    private static boolean notAirAndFiveAboveBottom(Region level, int x, int y, int z) {
        return level.get(x, y, z) != AIR && !Region.outsideBuildHeight(y - 5);
    }

    /** BlockTags.FEATURES_CANNOT_REPLACE: bedrock, spawner, chest, end portal frame. */
    static boolean featuresCannotReplace(int code) {
        return code == BEDROCK || code == SPAWNER || name(code).equals("chest") || name(code).equals("end_portal_frame");
    }

    /** BlockTags.LAVA_POOL_STONE_CANNOT_REPLACE: features_cannot_replace, leaves, logs. */
    private static boolean lavaPoolStoneCannotReplace(int code) {
        if (featuresCannotReplace(code)) {
            return true;
        }
        String n = name(code);
        return n.endsWith("_leaves") || n.endsWith("_log") || n.endsWith("_wood") || n.endsWith("_stem")
            || n.endsWith("_hyphae");
    }

    /** LakeFeature with simple fluid and barrier providers (no draws for either). */
    static final class Lake implements Feature {
        final int fluid;
        final int barrier;
        private final boolean[] grid = new boolean[2048];

        Lake(int fluid, int barrier) {
            this.fluid = fluid;
            this.barrier = barrier;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            if (y <= -64 + 4) {
                return false;
            }
            y -= 4;
            boolean[] aboolean = grid;
            Arrays.fill(aboolean, false);
            int i = random.nextInt(4) + 4;
            for (int j = 0; j < i; ++j) {
                double d0 = random.nextDouble() * 6.0D + 3.0D;
                double d1 = random.nextDouble() * 4.0D + 2.0D;
                double d2 = random.nextDouble() * 6.0D + 3.0D;
                double d3 = random.nextDouble() * (16.0D - d0 - 2.0D) + 1.0D + d0 / 2.0D;
                double d4 = random.nextDouble() * (8.0D - d1 - 4.0D) + 2.0D + d1 / 2.0D;
                double d5 = random.nextDouble() * (16.0D - d2 - 2.0D) + 1.0D + d2 / 2.0D;
                for (int l = 1; l < 15; ++l) {
                    for (int i1 = 1; i1 < 15; ++i1) {
                        for (int j1 = 1; j1 < 7; ++j1) {
                            double d6 = ((double) l - d3) / (d0 / 2.0D);
                            double d7 = ((double) j1 - d4) / (d1 / 2.0D);
                            double d8 = ((double) i1 - d5) / (d2 / 2.0D);
                            double d9 = d6 * d6 + d7 * d7 + d8 * d8;
                            if (d9 < 1.0D) {
                                aboolean[(l * 16 + i1) * 8 + j1] = true;
                            }
                        }
                    }
                }
            }
            for (int k1 = 0; k1 < 16; ++k1) {
                for (int k = 0; k < 16; ++k) {
                    for (int l2 = 0; l2 < 8; ++l2) {
                        if (edge(aboolean, k1, k, l2)) {
                            int state = level.get(x + k1, y + l2, z + k);
                            if (l2 >= 4 && liquid(state)) {
                                return false;
                            }
                            if (l2 < 4 && !solid(state) && state != fluid) {
                                return false;
                            }
                        }
                    }
                }
            }
            for (int l1 = 0; l1 < 16; ++l1) {
                for (int i2 = 0; i2 < 16; ++i2) {
                    for (int i3 = 0; i3 < 8; ++i3) {
                        if (aboolean[(l1 * 16 + i2) * 8 + i3]) {
                            if (!featuresCannotReplace(level.get(x + l1, y + i3, z + i2))) {
                                // flag1: the top half is cave air, then a scheduled tick of the air block (which
                                // does nothing) and post-processing marks above it.
                                level.set(x + l1, y + i3, z + i2, i3 >= 4 ? CAVE_AIR : fluid);
                                if (i3 >= 4) {
                                    level.markAbove(x + l1, y + i3, z + i2);
                                }
                            }
                        }
                    }
                }
            }
            if (!isAir(barrier)) {
                for (int j2 = 0; j2 < 16; ++j2) {
                    for (int j3 = 0; j3 < 16; ++j3) {
                        for (int l3 = 0; l3 < 8; ++l3) {
                            if (edge(aboolean, j2, j3, l3) && (l3 < 4 || random.nextInt(2) != 0)) {
                                int state = level.get(x + j2, y + l3, z + j3);
                                if (solid(state) && !lavaPoolStoneCannotReplace(state)) {
                                    level.set(x + j2, y + l3, z + j3, barrier);
                                    level.markAbove(x + j2, y + l3, z + j3);
                                }
                            }
                        }
                    }
                }
            }
            // The water lakes' freezing pass: the overworld's only lakes are lava.
            return true;
        }

        private static boolean edge(boolean[] a, int x, int z, int y) {
            return !a[(x * 16 + z) * 8 + y] && (x < 15 && a[((x + 1) * 16 + z) * 8 + y] || x > 0 && a[((x - 1) * 16 + z) * 8 + y]
                || z < 15 && a[(x * 16 + z + 1) * 8 + y] || z > 0 && a[(x * 16 + (z - 1)) * 8 + y]
                || y < 7 && a[(x * 16 + z) * 8 + y + 1] || y > 0 && a[(x * 16 + z) * 8 + (y - 1)]);
        }
    }

    /** MonsterRoomFeature (the spawner's mob drawn last: no block depends on it, the later attempts' positions do). */
    static final class MonsterRoom implements Feature {
        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int j = random.nextInt(2) + 2;
            int k = -j - 1;
            int l = j + 1;
            int k1 = random.nextInt(2) + 2;
            int l1 = -k1 - 1;
            int i2 = k1 + 1;
            int j2 = 0;
            for (int k2 = k; k2 <= l; ++k2) {
                for (int l2 = -1; l2 <= 4; ++l2) {
                    for (int i3 = l1; i3 <= i2; ++i3) {
                        boolean flag = solid(level.get(x + k2, y + l2, z + i3));
                        if (l2 == -1 && !flag) {
                            return false;
                        }
                        if (l2 == 4 && !flag) {
                            return false;
                        }
                        if ((k2 == k || k2 == l || i3 == l1 || i3 == i2) && l2 == 0 && isAir(level.get(x + k2, y, z + i3))
                                && isAir(level.get(x + k2, y + 1, z + i3))) {
                            ++j2;
                        }
                    }
                }
            }
            if (j2 < 1 || j2 > 5) {
                return false;
            }
            for (int k3 = k; k3 <= l; ++k3) {
                for (int i4 = 3; i4 >= -1; --i4) {
                    for (int k4 = l1; k4 <= i2; ++k4) {
                        int bx = x + k3;
                        int by = y + i4;
                        int bz = z + k4;
                        int state = level.get(bx, by, bz);
                        if (k3 != k && i4 != -1 && k4 != l1 && k3 != l && i4 != 4 && k4 != i2) {
                            if (!isChest(state) && state != SPAWNER) {
                                safeSet(level, bx, by, bz, CAVE_AIR);
                            }
                        } else if (by >= -64 && !solid(level.get(bx, by - 1, bz))) {
                            level.set(bx, by, bz, CAVE_AIR);
                        } else if (solid(state) && !isChest(state)) {
                            if (i4 == -1 && random.nextInt(4) != 0) {
                                safeSet(level, bx, by, bz, MOSSY_COBBLESTONE);
                            } else {
                                safeSet(level, bx, by, bz, COBBLESTONE);
                            }
                        }
                    }
                }
            }
            for (int l3 = 0; l3 < 2; ++l3) {
                for (int j4 = 0; j4 < 3; ++j4) {
                    int l4 = x + random.nextInt(j * 2 + 1) - j;
                    int j5 = z + random.nextInt(k1 * 2 + 1) - k1;
                    if (isAir(level.get(l4, y, j5))) {
                        int j3 = 0;
                        for (int d : PLANE) {
                            if (solid(level.get(l4 + DX[d], y, j5 + DZ[d]))) {
                                ++j3;
                            }
                        }
                        if (j3 == 1) {
                            safeSet(level, l4, y, j5, reorientChest(level, l4, y, j5));
                            // RandomizableContainerBlockEntity.setLootTable: the chest's loot seed.
                            if (isChest(level.get(l4, y, j5))) {
                                random.nextLong();
                            }
                            break;
                        }
                    }
                }
            }
            safeSet(level, x, y, z, SPAWNER);
            // The spawner's mob: vanilla's Util.getRandom(MOBS, random), one nextInt(4) (Forge's DungeonHooks draws
            // nextInt(400) from the same random instead: the same one draw). No block depends on it, but it moves
            // every later attempt of this placed feature in the chunk.
            if (level.get(x, y, z) == SPAWNER) {
                random.nextInt(4);
            }
            return true;
        }

        private static void safeSet(Region level, int x, int y, int z, int state) {
            if (!featuresCannotReplace(level.get(x, y, z))) {
                level.set(x, y, z, state);
            }
        }
    }

    static boolean isChest(int code) {
        return code == CHEST[0] || code == CHEST[1] || code == CHEST[2] || code == CHEST[3];
    }

    /** StructurePiece.reorient for a chest facing north. */
    private static int reorientChest(Region level, int x, int y, int z) {
        int direction = -1;
        for (int d : PLANE) {
            int s = level.get(x + DX[d], y, z + DZ[d]);
            if (isChest(s)) {
                return CHEST[2];
            }
            if (solidRender(s)) {
                if (direction != -1) {
                    direction = -1;
                    break;
                }
                direction = d;
            }
        }
        if (direction != -1) {
            return CHEST[opposite(direction)];
        }
        int d2 = 2;
        if (solidRender(level.get(x + DX[d2], y, z + DZ[d2]))) {
            d2 = opposite(d2);
        }
        if (solidRender(level.get(x + DX[d2], y, z + DZ[d2]))) {
            d2 = clockWise(d2);
        }
        if (solidRender(level.get(x + DX[d2], y, z + DZ[d2]))) {
            d2 = opposite(d2);
        }
        return CHEST[d2];
    }
}
