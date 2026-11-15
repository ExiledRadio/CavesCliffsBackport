package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.Arrays;
import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.*;

/**
 * Springs (MiscOverworldPlacements / Features SPRING_WATER, SPRING_LAVA, SPRING_LAVA_FROZEN), disks (DISK_SAND,
 * DISK_CLAY, DISK_GRAVEL: Feature.DISK, DiskReplaceFeature over BaseDiskFeature) and underwater magma
 * (CavePlacements / CaveFeatures UNDERWATER_MAGMA) as 1.18.2 places them.
 */
final class Misc {
    private Misc() {
    }

    private static final int BOTTOM = -64;
    private static final int TOP = 319;

    static List<Placed> placed() {
        boolean[] waterRock = set(STONE, GRANITE, DIORITE, ANDESITE, DEEPSLATE, TUFF, CALCITE, DIRT, SNOW_BLOCK,
            POWDER_SNOW, PACKED_ICE);
        boolean[] lavaRock = set(STONE, GRANITE, DIORITE, ANDESITE, DEEPSLATE, TUFF, CALCITE, DIRT);
        boolean[] frozenRock = set(SNOW_BLOCK, POWDER_SNOW, PACKED_ICE);
        int[] veryBiased = {HEIGHT_VERY_BIASED, BOTTOM, TOP - 8, 8};
        return Arrays.asList(
            new Placed("spring_water", new Spring(WATER, waterRock),
                Decorator.COUNT, 25, IN_SQUARE, HEIGHT_UNIFORM, BOTTOM, 192, BIOME),
            new Placed("spring_lava", new Spring(LAVA, lavaRock),
                Decorator.COUNT, 20, IN_SQUARE, veryBiased[0], veryBiased[1], veryBiased[2], veryBiased[3], BIOME),
            new Placed("spring_lava_frozen", new Spring(LAVA, frozenRock),
                Decorator.COUNT, 20, IN_SQUARE, veryBiased[0], veryBiased[1], veryBiased[2], veryBiased[3], BIOME),
            new Placed("disk_sand", new Disk(SAND, 2, 6, 2, true, DIRT, GRASS_BLOCK),
                Decorator.COUNT, 3, IN_SQUARE, HEIGHTMAP_OCEAN_FLOOR, BIOME),
            new Placed("disk_clay", new Disk(CLAY, 2, 3, 1, false, DIRT, CLAY),
                IN_SQUARE, HEIGHTMAP_OCEAN_FLOOR, BIOME),
            new Placed("disk_gravel", new Disk(GRAVEL, 2, 5, 2, true, DIRT, GRASS_BLOCK),
                IN_SQUARE, HEIGHTMAP_OCEAN_FLOOR, BIOME),
            // 1.19.2: MiscOverworldPlacements.DISK_GRASS: count 1, in square, HEIGHTMAP_TOP_SOLID, 1 down, on mud, biome.
            new Placed("disk_grass", new GrassDisk(), Decorator.COUNT, 1, IN_SQUARE, HEIGHTMAP_OCEAN_FLOOR, CUSTOM, 0, BIOME)
                .mods((level, random, next, x, y, z) -> {
                    if (sameBlock(level.get(x, y - 1, z), MUD)) {
                        next.at(x, y - 1, z);
                    }
                }),
            new Placed("underwater_magma", new UnderwaterMagma(5, 1, 0.5F),
                COUNT_UNIFORM, 44, 52, IN_SQUARE, HEIGHT_UNIFORM, BOTTOM, 256,
                SURFACE_THRESHOLD_OCEAN_FLOOR, Integer.MIN_VALUE, -2, BIOME));
    }

    private static boolean[] set(int... codes) {
        boolean[] b = new boolean[1 << 16];
        for (int c : codes) {
            b[c] = true;
        }
        return b;
    }

    /** SpringFeature with requiresBlockBelow, rock count 4, hole count 1. */
    static final class Spring implements Feature {
        final int fluid;
        final boolean[] valid;

        Spring(int fluid, boolean[] valid) {
            this.fluid = fluid;
            this.valid = valid;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            if (!valid[level.get(x, y + 1, z)]) {
                return false;
            }
            if (!valid[level.get(x, y - 1, z)]) {
                return false;
            }
            int state = level.get(x, y, z);
            if (!isAir(state) && !valid[state]) {
                return false;
            }
            int j = 0;
            if (valid[level.get(x - 1, y, z)]) {
                ++j;
            }
            if (valid[level.get(x + 1, y, z)]) {
                ++j;
            }
            if (valid[level.get(x, y, z - 1)]) {
                ++j;
            }
            if (valid[level.get(x, y, z + 1)]) {
                ++j;
            }
            if (valid[level.get(x, y - 1, z)]) {
                ++j;
            }
            int k = 0;
            if (isAir(level.get(x - 1, y, z))) {
                ++k;
            }
            if (isAir(level.get(x + 1, y, z))) {
                ++k;
            }
            if (isAir(level.get(x, y, z - 1))) {
                ++k;
            }
            if (isAir(level.get(x, y, z + 1))) {
                ++k;
            }
            if (isAir(level.get(x, y - 1, z))) {
                ++k;
            }
            if (j == 4 && k == 1) {
                level.set(x, y, z, fluid);
                level.mark(x, y, z, Marks.TICK);
                return true;
            }
            return false;
        }
    }

    /** DiskReplaceFeature: only from water; BaseDiskFeature's columns, sandstone under falling blocks over air. */
    static final int MUD = register("mud");

    /**
     * 1.19's disk_grass: DiskFeature (1.19.2: every block of the column from halfHeight above to halfHeight below that
     * matches the target gets the provider's state, top down) on mud, radius 2-6, half height 2; RuleBasedBlockState
     * provider: grass block where the block above is neither Material.isSolid nor water (Fluids.WATER), else dirt.
     */
    static final class GrassDisk implements Feature {
        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            boolean placed = false;
            int top = y + 2;
            int bottom = y - 2 - 1;
            int r = random.between(2, 6);
            for (int dz = -r; dz <= r; ++dz) {
                for (int dx = -r; dx <= r; ++dx) {
                    if (dx * dx + dz * dz > r * r) {
                        continue;
                    }
                    boolean run = false;
                    for (int yy = top; yy > bottom; --yy) {
                        if (sameBlock(level.get(x + dx, yy, z + dz), MUD)) {
                            int above = level.get(x + dx, yy + 1, z + dz);
                            boolean water = above == WATER || (flags(above) & F_WATER) != 0 && !name(above).equals("water");
                            level.set(x + dx, yy, z + dz, (flags(above) & F_SOLID) != 0 || water ? DIRT : GRASS_BLOCK);
                            // DiskFeature marks the block above for post-processing (1.21: the top of each run only).
                            if (!run || level.rules < Rules.V121) {
                                level.markAbove(x + dx, yy, z + dz);
                            }
                            placed = true;
                            run = true;
                        } else {
                            run = false;
                        }
                    }
                }
            }
            return placed;
        }
    }

    static final class Disk implements Feature {
        final int state;
        final int minRadius;
        final int maxRadius;
        final int halfHeight;
        final boolean falling;
        final int[] targets;

        Disk(int state, int minRadius, int maxRadius, int halfHeight, boolean falling, int... targets) {
            this.state = state;
            this.minRadius = minRadius;
            this.maxRadius = maxRadius;
            this.halfHeight = halfHeight;
            this.falling = falling;
            this.targets = targets;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int origin = level.get(x, y, z);
            if (level.rules >= Rules.V119) {
                // 1.19: the placement's BlockPredicate.matchesFluids(Fluids.WATER), source water only.
                if (!Vegetation.sourceWater(origin)) {
                    return false;
                }
            } else if ((flags(origin) & F_WATER) == 0) {
                // getFluidState(origin).is(FluidTags.WATER): water, waterlogged blocks and water plants.
                return false;
            }
            return columns(level, random, x, y, z);
        }

        /** BaseDiskFeature.place, or from 1.19 on DiskFeature.place. */
        boolean columns(Region level, XoroRandom random, int x, int y, int z) {
            if (level.rules >= Rules.V119) {
                return columns119(level, random, x, y, z);
            }
            boolean flag = false;
            int j = y + halfHeight;
            int k = y - halfHeight - 1;
            int l = random.between(minRadius, maxRadius);
            for (int i1 = x - l; i1 <= x + l; ++i1) {
                for (int j1 = z - l; j1 <= z + l; ++j1) {
                    int k1 = i1 - x;
                    int l1 = j1 - z;
                    if (k1 * k1 + l1 * l1 <= l * l) {
                        boolean flag2 = false;
                        for (int i2 = j; i2 >= k; --i2) {
                            int block = level.get(i1, i2, j1);
                            boolean flag3 = false;
                            if (i2 > k) {
                                for (int t : targets) {
                                    if (sameBlock(t, block)) {
                                        level.set(i1, i2, j1, state);
                                        level.markAbove(i1, i2, j1);
                                        flag = true;
                                        flag3 = true;
                                        break;
                                    }
                                }
                            }
                            if (falling && flag2 && isAir(block)) {
                                level.set(i1, i2 + 1, j1, state == RED_SAND ? RED_SANDSTONE : SANDSTONE);
                            }
                            flag2 = flag3;
                        }
                    }
                }
            }
            return flag;
        }

        /**
         * 1.19.2's DiskFeature: per column (BlockPos.betweenClosed, x fastest; the columns are independent), top down
         * from halfHeight above to halfHeight below, each target block gets the RuleBasedBlockStateProvider's state:
         * disk_sand is sandstone where the block below is air (the air block only), every other disk its block.
         */
        private boolean columns119(Region level, XoroRandom random, int x, int y, int z) {
            boolean placed = false;
            int top = y + halfHeight;
            int bottom = y - halfHeight - 1;
            int r = random.between(minRadius, maxRadius);
            for (int dz = -r; dz <= r; ++dz) {
                for (int dx = -r; dx <= r; ++dx) {
                    if (dx * dx + dz * dz > r * r) {
                        continue;
                    }
                    // 1.21 marks only the top of each run of placed blocks for post-processing.
                    boolean above = false;
                    for (int yy = top; yy > bottom; --yy) {
                        int block = level.get(x + dx, yy, z + dz);
                        boolean hit = false;
                        for (int t : targets) {
                            if (sameBlock(t, block)) {
                                int s = state == SAND && level.get(x + dx, yy - 1, z + dz) == AIR ? SANDSTONE : state;
                                level.set(x + dx, yy, z + dz, s);
                                if (!above || level.rules < Rules.V121) {
                                    level.markAbove(x + dx, yy, z + dz);
                                }
                                placed = true;
                                hit = true;
                                break;
                            }
                        }
                        above = hit;
                    }
                }
            }
            return placed;
        }
    }

    /** UnderwaterMagmaFeature: the floor under the water column, then a box around it, a draw per position. */
    static final class UnderwaterMagma implements Feature {
        final int floorSearchRange;
        final int radius;
        final float probability;

        UnderwaterMagma(int floorSearchRange, int radius, float probability) {
            this.floorSearchRange = floorSearchRange;
            this.radius = radius;
            this.probability = probability;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            // Column.scan(level, origin, range, water, not water).getFloor(): the down scan's end if not water.
            if (level.get(x, y, z) != WATER) {
                return false;
            }
            int cy = y;
            for (int i = 1; i < floorSearchRange && level.get(x, cy, z) == WATER; ++i) {
                --cy;
            }
            if (level.get(x, cy, z) == WATER) {
                return false;
            }
            int placed = 0;
            for (int bz = z - radius; bz <= z + radius; ++bz) {
                for (int by = cy - radius; by <= cy + radius; ++by) {
                    for (int bx = x - radius; bx <= x + radius; ++bx) {
                        if (random.nextFloat() < probability && valid(level, bx, by, bz)) {
                            level.set(bx, by, bz, MAGMA_BLOCK);
                            placed++;
                        }
                    }
                }
            }
            return placed > 0;
        }

        private static boolean waterOrAir(Region level, int x, int y, int z) {
            int s = level.get(x, y, z);
            return s == WATER || isAir(s);
        }

        private static boolean valid(Region level, int x, int y, int z) {
            if (level.rules >= Rules.V121) {
                // 1.21.11: below and the four sides must hide their face (a full occlusion face), not just be solid.
                return !waterOrAir(level, x, y, z) && solidRender(level.get(x, y - 1, z)) && solidRender(level.get(x, y, z - 1))
                    && solidRender(level.get(x + 1, y, z)) && solidRender(level.get(x, y, z + 1)) && solidRender(level.get(x - 1, y, z));
            }
            if (waterOrAir(level, x, y, z) || waterOrAir(level, x, y - 1, z)) {
                return false;
            }
            return !waterOrAir(level, x, y, z - 1) && !waterOrAir(level, x + 1, y, z) && !waterOrAir(level, x, y, z + 1)
                && !waterOrAir(level, x - 1, y, z);
        }
    }
}
