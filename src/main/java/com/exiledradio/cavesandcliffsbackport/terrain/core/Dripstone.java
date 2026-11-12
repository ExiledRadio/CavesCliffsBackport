package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.Arrays;
import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.*;

/**
 * Dripstone caves' features as 1.18.2 places them: large dripstone (LargeDripstoneFeature), dripstone clusters
 * (DripstoneClusterFeature) and pointed dripstone (a SimpleRandomSelector over two inline placed
 * PointedDripstoneFeatures), with DripstoneUtils and Column.scan.
 */
final class Dripstone {
    private Dripstone() {
    }

    static final int DRIPSTONE_BLOCK = register("dripstone_block");
    /** DripstoneThickness order: tip_merge, tip, frustum, middle, base. */
    static final int TIP_MERGE = 0;
    static final int TIP = 1;
    static final int FRUSTUM = 2;
    static final int MIDDLE = 3;
    static final int BASE = 4;
    private static final String[] THICKNESS = {"tip_merge", "tip", "frustum", "middle", "base"};
    /** pointed_dripstone by [thickness][up ? 1 : 0][waterlogged ? 1 : 0]. */
    static final int[][][] POINTED = new int[5][2][2];

    static {
        for (int t = 0; t < 5; t++) {
            for (int u = 0; u < 2; u++) {
                for (int w = 0; w < 2; w++) {
                    POINTED[t][u][w] = register("pointed_dripstone[thickness=" + THICKNESS[t] + ",vertical_direction="
                        + (u == 1 ? "up" : "down") + ",waterlogged=" + (w == 1) + "]");
                }
            }
        }
    }

    static boolean isPointedDripstone(int code) {
        return name(code).equals("pointed_dripstone");
    }

    static List<Placed> placed() {
        return Arrays.asList(
            new Placed("large_dripstone", new LargeDripstone(),
                COUNT_UNIFORM, 10, 48, IN_SQUARE, HEIGHT_UNIFORM, -64, 256, BIOME),
            new Placed("dripstone_cluster", new Cluster(),
                COUNT_UNIFORM, 48, 96, IN_SQUARE, HEIGHT_UNIFORM, -64, 256, BIOME),
            new Placed("pointed_dripstone", new PointedSelector(),
                COUNT_UNIFORM, 192, 256, IN_SQUARE, HEIGHT_UNIFORM, -64, 256, COUNT_UNIFORM, 1, 5, CUSTOM, 0, BIOME)
                .mods(Dripstone::randomOffset));
    }

    /** RandomOffsetPlacement(ClampedNormalInt(0, 3, -10, 10), ClampedNormalInt(0, 0.6, -2, 2)): x, y, then z. */
    private static void randomOffset(Region level, XoroRandom random, Next next, int x, int y, int z) {
        int i = x + clampedNormalInt(random, 0.0F, 3.0F, -10, 10);
        int j = y + clampedNormalInt(random, 0.0F, 0.6F, -2, 2);
        int k = z + clampedNormalInt(random, 0.0F, 3.0F, -10, 10);
        next.at(i, j, k);
    }

    /** ClampedNormalInt.sample. */
    static int clampedNormalInt(XoroRandom random, float mean, float deviation, int min, int max) {
        return (int) McMath.clamp(random.normal(mean, deviation), (float) min, (float) max);
    }

    // ---- DripstoneUtils ----

    static boolean isEmptyOrWater(int code) {
        return isAir(code) || code == WATER;
    }

    static boolean isEmptyOrWaterOrLava(int code) {
        return isAir(code) || code == WATER || code == LAVA;
    }

    static boolean isNeitherEmptyNorWater(int code) {
        return !isAir(code) && code != WATER;
    }

    /** BlockTags.BASE_STONE_OVERWORLD (and DRIPSTONE_REPLACEABLE, which is that tag). */
    static boolean baseStoneOverworld(int code) {
        return code == STONE || code == GRANITE || code == DIORITE || code == ANDESITE || code == TUFF || code == DEEPSLATE;
    }

    static boolean isDripstoneBase(int code) {
        return code == DRIPSTONE_BLOCK || baseStoneOverworld(code);
    }

    static boolean isDripstoneBaseOrLava(int code) {
        return isDripstoneBase(code) || code == LAVA;
    }

    static double getDripstoneHeight(double r, double radius, double scale, double bluntness) {
        if (r < bluntness) {
            r = bluntness;
        }
        double d1 = r / radius * 0.384D;
        double d2 = 0.75D * Math.pow(d1, 1.3333333333333333D);
        double d3 = Math.pow(d1, 0.6666666666666666D);
        double d4 = 0.3333333333333333D * Math.log(d1);
        double d5 = scale * (d2 - d3 - d4);
        d5 = Math.max(d5, 0.0D);
        return d5 / 0.384D * radius;
    }

    static boolean isCircleMostlyEmbeddedInStone(Region level, int x, int y, int z, int radius) {
        if (isEmptyOrWaterOrLava(level.get(x, y, z))) {
            return false;
        }
        float f1 = 6.0F / (float) radius;
        for (float f2 = 0.0F; f2 < (float) Math.PI * 2F; f2 += f1) {
            int i = (int) (McMath.cos(f2) * (float) radius);
            int j = (int) (McMath.sin(f2) * (float) radius);
            if (isEmptyOrWaterOrLava(level.get(x + i, y, z + j))) {
                return false;
            }
        }
        return true;
    }

    /** buildBaseToTipColumn through growPointedDripstone's consumer: base first, at y, then on in the direction. */
    static void growPointedDripstone(Region level, int x, int y, int z, boolean up, int height, boolean merge) {
        int dy = up ? 1 : -1;
        if (!isDripstoneBase(level.get(x, y - dy, z))) {
            return;
        }
        int u = up ? 1 : 0;
        int at = y;
        if (height >= 3) {
            setPointed(level, x, at, z, u, BASE);
            at += dy;
            for (int i = 0; i < height - 3; ++i) {
                setPointed(level, x, at, z, u, MIDDLE);
                at += dy;
            }
        }
        if (height >= 2) {
            setPointed(level, x, at, z, u, FRUSTUM);
            at += dy;
        }
        if (height >= 1) {
            setPointed(level, x, at, z, u, merge ? TIP_MERGE : TIP);
        }
    }

    private static void setPointed(Region level, int x, int y, int z, int up, int thickness) {
        boolean water = (flags(level.get(x, y, z)) & F_WATER) != 0;
        level.set(x, y, z, POINTED[thickness][up][water ? 1 : 0]);
    }

    static boolean placeDripstoneBlockIfPossible(Region level, int x, int y, int z) {
        if (baseStoneOverworld(level.get(x, y, z))) {
            level.set(x, y, z, DRIPSTONE_BLOCK);
            return true;
        }
        return false;
    }

    // ---- Column.scan ----

    static final int EMPTY = Integer.MIN_VALUE;

    interface Test {
        boolean test(int code);
    }

    /** Column.scan's two directions from y (allowed there): {ceiling, floor}, EMPTY where absent; null if not allowed at y. */
    static boolean scan(Region level, int x, int y, int z, int range, Test allowed, Test target, int[] out) {
        if (!allowed.test(level.get(x, y, z))) {
            return false;
        }
        out[0] = scanDirection(level, x, y, z, range, allowed, target, 1);
        out[1] = scanDirection(level, x, y, z, range, allowed, target, -1);
        return true;
    }

    private static int scanDirection(Region level, int x, int y, int z, int range, Test allowed, Test target, int dy) {
        for (int i = 1; i < range && allowed.test(level.get(x, y, z)); ++i) {
            y += dy;
        }
        return target.test(level.get(x, y, z)) ? y : EMPTY;
    }

    // ---- LargeDripstoneFeature ----

    static final class LargeDripstone implements Feature {
        private final int[] column = new int[2];

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            if (!isEmptyOrWater(level.get(x, y, z))) {
                return false;
            }
            if (!scan(level, x, y, z, 30, Dripstone::isEmptyOrWater, Dripstone::isDripstoneBaseOrLava, column)) {
                return false;
            }
            int ceiling = column[0];
            int floor = column[1];
            if (ceiling == EMPTY || floor == EMPTY) {
                return false;
            }
            int height = ceiling - floor - 1;
            if (height < 4) {
                return false;
            }
            int i = (int) ((float) height * 0.33F);
            int j = McMath.clamp(i, 3, 19);
            int k = random.between(3, j);
            Stone stalactite = new Stone(x, ceiling - 1, z, false, k, (double) random.between(0.3F, 0.9F),
                (double) random.between(0.4F, 2.0F));
            Stone stalagmite = new Stone(x, floor + 1, z, true, k, (double) random.between(0.4F, 1.0F),
                (double) random.between(0.4F, 2.0F));
            Wind wind;
            if (stalactite.suitableForWind() && stalagmite.suitableForWind()) {
                wind = new Wind(y, random);
            } else {
                wind = Wind.NONE;
            }
            boolean flag = stalactite.moveBack(level, wind);
            boolean flag1 = stalagmite.moveBack(level, wind);
            if (flag) {
                stalactite.placeBlocks(level, random, wind);
            }
            if (flag1) {
                stalagmite.placeBlocks(level, random, wind);
            }
            return true;
        }
    }

    static final class Stone {
        int rootX;
        int rootY;
        int rootZ;
        final boolean pointingUp;
        int radius;
        final double bluntness;
        final double scale;

        Stone(int x, int y, int z, boolean pointingUp, int radius, double bluntness, double scale) {
            rootX = x;
            rootY = y;
            rootZ = z;
            this.pointingUp = pointingUp;
            this.radius = radius;
            this.bluntness = bluntness;
            this.scale = scale;
        }

        int getHeight() {
            return getHeightAtRadius(0.0F);
        }

        int getHeightAtRadius(float r) {
            return (int) getDripstoneHeight((double) r, (double) radius, scale, bluntness);
        }

        boolean suitableForWind() {
            return radius >= 4 && bluntness >= (double) 0.6F;
        }

        boolean moveBack(Region level, Wind wind) {
            while (radius > 1) {
                int my = rootY;
                int i = Math.min(10, getHeight());
                for (int j = 0; j < i; ++j) {
                    if (level.get(rootX, my, rootZ) == LAVA) {
                        return false;
                    }
                    if (isCircleMostlyEmbeddedInStone(level, wind.x(rootX, my), my, wind.z(rootZ, my), radius)) {
                        rootY = my;
                        return true;
                    }
                    my += pointingUp ? -1 : 1;
                }
                radius /= 2;
            }
            return false;
        }

        void placeBlocks(Region level, XoroRandom random, Wind wind) {
            for (int i = -radius; i <= radius; ++i) {
                for (int j = -radius; j <= radius; ++j) {
                    float f = McMath.sqrt((float) (i * i + j * j));
                    if (!(f > (float) radius)) {
                        int k = getHeightAtRadius(f);
                        if (k > 0) {
                            if ((double) random.nextFloat() < 0.2D) {
                                k = (int) ((float) k * random.between(0.8F, 1.0F));
                            }
                            int mx = rootX + i;
                            int my = rootY;
                            int mz = rootZ + j;
                            boolean flag = false;
                            int l = pointingUp ? level.worldSurfaceWg(mx, mz) : Integer.MAX_VALUE;
                            for (int i1 = 0; i1 < k && my < l; ++i1) {
                                int px = wind.x(mx, my);
                                int pz = wind.z(mz, my);
                                int state = level.get(px, my, pz);
                                if (isEmptyOrWaterOrLava(state)) {
                                    flag = true;
                                    level.set(px, my, pz, DRIPSTONE_BLOCK);
                                } else if (flag && baseStoneOverworld(state)) {
                                    break;
                                }
                                my += pointingUp ? 1 : -1;
                            }
                        }
                    }
                }
            }
        }
    }

    /** LargeDripstoneFeature.WindOffsetter. */
    static final class Wind {
        static final Wind NONE = new Wind();
        final int originY;
        final boolean none;
        final double wx;
        final double wz;

        private Wind() {
            originY = 0;
            none = true;
            wx = 0.0D;
            wz = 0.0D;
        }

        Wind(int originY, XoroRandom random) {
            this.originY = originY;
            none = false;
            float f = random.between(0.0F, 0.3F);
            float f1 = random.between(0.0F, (float) Math.PI);
            wx = (double) (McMath.cos(f1) * f);
            wz = (double) (McMath.sin(f1) * f);
        }

        /** BlockPos.offset(vec.x, 0, vec.z) with vec = wind * (originY - y): new BlockPos floors each double. */
        int x(int x, int y) {
            if (none) {
                return x;
            }
            return McMath.floor((double) x + wx * (double) (originY - y));
        }

        int z(int z, int y) {
            if (none) {
                return z;
            }
            return McMath.floor((double) z + wz * (double) (originY - y));
        }
    }

    // ---- DripstoneClusterFeature ----

    static final class Cluster implements Feature {
        private final int[] column = new int[2];

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            if (!isEmptyOrWater(level.get(x, y, z))) {
                return false;
            }
            int i = random.between(3, 6);
            float f = McMath.clamp(random.normal(0.1F, 0.3F), 0.1F, 0.9F);
            float f1 = random.between(0.3F, 0.7F);
            int j = random.between(2, 8);
            int k = random.between(2, 8);
            for (int l = -j; l <= j; ++l) {
                for (int i1 = -k; i1 <= k; ++i1) {
                    double d0 = chance(j, k, l, i1);
                    placeColumn(level, random, x + l, y, z + i1, l, i1, f, d0, i, f1);
                }
            }
            return true;
        }

        private void placeColumn(Region level, XoroRandom random, int x, int y, int z, int dx, int dz, float wetness,
                                 double chance, int height, float density) {
            if (!scan(level, x, y, z, 12, Dripstone::isEmptyOrWater, Dripstone::isNeitherEmptyNorWater, column)) {
                return;
            }
            int ceiling = column[0];
            int floor0 = column[1];
            if (ceiling == EMPTY && floor0 == EMPTY) {
                return;
            }
            boolean flag = random.nextFloat() < wetness;
            int floor;
            if (flag && floor0 != EMPTY && canPlacePool(level, x, floor0, z)) {
                floor = floor0 - 1;
                level.set(x, floor0, z, WATER);
            } else {
                floor = floor0;
            }
            boolean flag1 = random.nextDouble() < chance;
            int j;
            if (ceiling != EMPTY && flag1 && level.get(x, ceiling, z) != LAVA) {
                int k = random.between(2, 4);
                replaceWithDripstoneBlocks(level, x, ceiling, z, k, 1);
                int l;
                if (floor != EMPTY) {
                    l = Math.min(height, ceiling - floor);
                } else {
                    l = height;
                }
                j = dripstoneHeight(random, dx, dz, density, l);
            } else {
                j = 0;
            }
            boolean flag2 = random.nextDouble() < chance;
            int i3;
            if (floor != EMPTY && flag2 && level.get(x, floor, z) != LAVA) {
                int i1 = random.between(2, 4);
                replaceWithDripstoneBlocks(level, x, floor, z, i1, -1);
                if (ceiling != EMPTY) {
                    i3 = Math.max(0, j + random.between(-1, 1));
                } else {
                    i3 = dripstoneHeight(random, dx, dz, density, height);
                }
            } else {
                i3 = 0;
            }
            int j1;
            int j3;
            if (ceiling != EMPTY && floor != EMPTY && ceiling - j <= floor + i3) {
                int i2 = Math.max(ceiling - j, floor + 1);
                int j2 = Math.min(floor + i3, ceiling - 1);
                int k2 = random.between(i2, j2 + 1);
                int l2 = k2 - 1;
                j3 = ceiling - k2;
                j1 = l2 - floor;
            } else {
                j3 = j;
                j1 = i3;
            }
            // column.getHeight(): only a range (both ends) has one, ceiling - floor - 1.
            boolean flag3 = random.nextBoolean() && j3 > 0 && j1 > 0 && ceiling != EMPTY && floor != EMPTY
                && j3 + j1 == ceiling - floor - 1;
            if (ceiling != EMPTY) {
                growPointedDripstone(level, x, ceiling - 1, z, false, j3, flag3);
            }
            if (floor != EMPTY) {
                growPointedDripstone(level, x, floor + 1, z, true, j1, flag3);
            }
        }

        private int dripstoneHeight(XoroRandom random, int dx, int dz, float density, int max) {
            if (random.nextFloat() > density) {
                return 0;
            }
            int i = Math.abs(dx) + Math.abs(dz);
            float f = (float) McMath.clampedMap((double) i, 0.0D, 8.0D, (double) max / 2.0D, 0.0D);
            return (int) McMath.clamp(random.normal(f, 3.0F), 0.0F, (float) max);
        }

        private static boolean canPlacePool(Region level, int x, int y, int z) {
            int state = level.get(x, y, z);
            if (state == WATER || state == DRIPSTONE_BLOCK || isPointedDripstone(state)) {
                return false;
            }
            if ((flags(level.get(x, y + 1, z)) & F_WATER) != 0) {
                return false;
            }
            return adjacentToWater(level, x, y, z - 1) && adjacentToWater(level, x + 1, y, z)
                && adjacentToWater(level, x, y, z + 1) && adjacentToWater(level, x - 1, y, z)
                && adjacentToWater(level, x, y - 1, z);
        }

        private static boolean adjacentToWater(Region level, int x, int y, int z) {
            int state = level.get(x, y, z);
            return baseStoneOverworld(state) || (flags(state) & F_WATER) != 0;
        }

        private static void replaceWithDripstoneBlocks(Region level, int x, int y, int z, int n, int dy) {
            for (int i = 0; i < n; ++i) {
                if (!placeDripstoneBlockIfPossible(level, x, y, z)) {
                    return;
                }
                y += dy;
            }
        }

        private static double chance(int j, int k, int dx, int dz) {
            int i = j - Math.abs(dx);
            int i1 = k - Math.abs(dz);
            int m = Math.min(i, i1);
            return (double) McMath.clampedMap((float) m, 0.0F, 3.0F, 0.1F, 1.0F);
        }
    }

    // ---- POINTED_DRIPSTONE: SimpleRandomSelector over two inline placed features ----

    static final class PointedSelector implements Feature {
        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            boolean down = random.nextInt(2) == 0;
            // EnvironmentScanPlacement(down ? DOWN : UP, solid, ONLY_IN_AIR_OR_WATER, 12), then a vertical offset
            // of +1 (down) or -1 (up); PlacedFeature.place, then ConfiguredFeature.place's ensureCanWrite.
            int dy = down ? -1 : 1;
            int sy = scanSolid(level, x, y, z, dy);
            if (sy == EMPTY) {
                return false;
            }
            int py = sy - dy;
            return level.canWrite(x, z) && placePointed(level, random, x, py, z);
        }

        private static boolean airOrWater(int code) {
            return code == AIR || code == WATER;
        }

        private static int scanSolid(Region level, int x, int y, int z, int dy) {
            if (!airOrWater(level.get(x, y, z))) {
                return EMPTY;
            }
            for (int i = 0; i < 12; ) {
                if (solid(level.get(x, y, z))) {
                    return y;
                }
                y += dy;
                if (Region.outsideBuildHeight(y)) {
                    return EMPTY;
                }
                if (airOrWater(level.get(x, y, z))) {
                    ++i;
                    continue;
                }
                break;
            }
            return solid(level.get(x, y, z)) ? y : EMPTY;
        }

        /** PointedDripstoneFeature(0.2, 0.7, 0.5, 0.5). */
        private static boolean placePointed(Region level, XoroRandom random, int x, int y, int z) {
            boolean above = isDripstoneBase(level.get(x, y + 1, z));
            boolean below = isDripstoneBase(level.get(x, y - 1, z));
            boolean up;
            if (above && below) {
                up = !random.nextBoolean();
            } else if (above) {
                up = false;
            } else if (below) {
                up = true;
            } else {
                return false;
            }
            int dy = up ? 1 : -1;
            int bx = x;
            int by = y - dy;
            int bz = z;
            placeDripstoneBlockIfPossible(level, bx, by, bz);
            for (int d = 0; d < 4; d++) {
                // Direction.Plane.HORIZONTAL: north, east, south, west.
                if (!(random.nextFloat() > 0.7F)) {
                    int ax = bx + PLANE_X[d];
                    int az = bz + PLANE_Z[d];
                    placeDripstoneBlockIfPossible(level, ax, by, az);
                    if (!(random.nextFloat() > 0.5F)) {
                        int r = random.nextInt(6);
                        int cx = ax + Geodes.DX[r];
                        int cy = by + Geodes.DY[r];
                        int cz = az + Geodes.DZ[r];
                        placeDripstoneBlockIfPossible(level, cx, cy, cz);
                        if (!(random.nextFloat() > 0.5F)) {
                            int r2 = random.nextInt(6);
                            placeDripstoneBlockIfPossible(level, cx + Geodes.DX[r2], cy + Geodes.DY[r2], cz + Geodes.DZ[r2]);
                        }
                    }
                }
            }
            int i = random.nextFloat() < 0.2F && isEmptyOrWater(level.get(x, y + dy, z)) ? 2 : 1;
            growPointedDripstone(level, x, y, z, up, i, false);
            return true;
        }
    }

    static final int[] PLANE_X = {0, 1, 0, -1};
    static final int[] PLANE_Z = {-1, 0, 1, 0};
}
