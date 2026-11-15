package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.Collections;
import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.*;

/** Surface features as 1.18.2 places them: icebergs, forest rock, ice spikes and patches, the desert well, blue ice, freeze_top_layer. */
final class SurfaceFeatures {
    private SurfaceFeatures() {
    }

    static final int SNOW = register("snow[layers=1]");
    static final int GRASS_BLOCK_SNOWY = register("grass_block[snowy=true]");
    static final int PODZOL_SNOWY = register("podzol[snowy=true]");
    static final int MYCELIUM_SNOWY = register("mycelium[snowy=true]");

    static final int BLUE_ICE = register("blue_ice");
    static final int MOSSY_COBBLESTONE = register("mossy_cobblestone");
    static final int SANDSTONE_SLAB = register("sandstone_slab[type=bottom,waterlogged=false]");

    static List<Placed> placed(Decorator decorator) {
        int mb = Region.MOTION_BLOCKING;
        return java.util.Arrays.asList(
            new Placed("freeze_top_layer", new FreezeTopLayer(decorator), BIOME),
            new Placed("iceberg_packed", new Iceberg(PACKED_ICE), RARITY, 16, IN_SQUARE, BIOME),
            new Placed("iceberg_blue", new Iceberg(BLUE_ICE), RARITY, 200, IN_SQUARE, BIOME),
            new Placed("forest_rock", new BlockBlob(MOSSY_COBBLESTONE), Decorator.COUNT, 2, IN_SQUARE, HEIGHTMAP, mb, BIOME),
            new Placed("ice_spike", new IceSpike(), Decorator.COUNT, 3, IN_SQUARE, HEIGHTMAP, mb, BIOME),
            // 1.19 adds RandomOffsetPlacement.vertical(-1) and a snow block filter before the biome filter.
            new Placed("ice_patch", new IcePatch(), Decorator.COUNT, 2, IN_SQUARE, HEIGHTMAP, mb, CUSTOM, 0, BIOME)
                .mods((level, random, next, x, y, z) -> {
                    if (level.rules < Rules.V119) {
                        next.at(x, y, z);
                    } else if (level.get(x, y - 1, z) == SNOW_BLOCK) {
                        next.at(x, y - 1, z);
                    }
                }),
            new Placed("desert_well", new DesertWell(), RARITY, 1000, IN_SQUARE, HEIGHTMAP, mb, BIOME),
            new Placed("blue_ice", new BlueIce(), COUNT_UNIFORM, 0, 19, IN_SQUARE, HEIGHT_UNIFORM, 30, 61, BIOME));
    }

    /**
     * SnowAndFreezeFeature over the chunk's 16x16 columns at MOTION_BLOCKING: ice where Biome.shouldFreeze (still
     * water source, not warm enough to rain at that block, block light below 10, which it always is while
     * decorating), then a snow layer where Biome.shouldSnow (air, and SnowLayerBlock.canSurvive on the block below,
     * after the ice), and the block below snowy if it has the property. The biome is the zoomed one at the top block.
     */
    static final class FreezeTopLayer implements Feature {
        private final Decorator decorator;

        FreezeTopLayer(Decorator decorator) {
            this.decorator = decorator;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            for (int i = 0; i < 16; ++i) {
                for (int j = 0; j < 16; ++j) {
                    int k = x + i;
                    int l = z + j;
                    int i1 = level.height(Region.MOTION_BLOCKING, k, l);
                    int biome = decorator.zoomedBiome(k, i1, l);
                    int by = i1 - 1;
                    if (!Region.outsideBuildHeight(by) && Biomes.coldEnoughToSnow(biome, k, by, l)
                            && level.blockLight(k, by, l) < 10 && level.get(k, by, l) == WATER) {
                        level.set(k, by, l, ICE);
                    }
                    if (!Region.outsideBuildHeight(i1) && Biomes.coldEnoughToSnow(biome, k, i1, l)
                            && level.blockLight(k, i1, l) < 10 && isAir(level.get(k, i1, l)) && snowSurvives(level.get(k, by, l))) {
                        level.set(k, i1, l, SNOW);
                        int below = level.get(k, by, l);
                        int snowy = below == GRASS_BLOCK ? GRASS_BLOCK_SNOWY : below == PODZOL ? PODZOL_SNOWY
                            : below == MYCELIUM ? MYCELIUM_SNOWY : -1;
                        if (snowy >= 0) {
                            level.set(k, by, l, snowy);
                        }
                    }
                }
            }
            return true;
        }

        /** SnowLayerBlock.canSurvive on the block below (outside the world: void air). */
        private static boolean snowSurvives(int below) {
            if (below == ICE || below == PACKED_ICE || name(below).equals("barrier")) {
                return false;
            }
            String n = name(below);
            if (n.equals("honey_block") || n.equals("soul_sand")) {
                return true;
            }
            return faceFull(below, 1) || state(below).equals("snow[layers=8]");
        }
    }

    // ---- IcebergFeature (iceberg_packed, iceberg_blue) ----

    private static boolean icebergState(int s) {
        return s == PACKED_ICE || s == SNOW_BLOCK || s == BLUE_ICE;
    }

    /** IcebergFeature at sea level, with its config state (packed or blue ice). */
    static final class Iceberg implements Feature {
        final int state;

        Iceberg(int state) {
            this.state = state;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int ox, int oy, int oz) {
            int py = 63;
            boolean flag = random.nextDouble() > 0.7D;
            double d0 = random.nextDouble() * 2.0D * Math.PI;
            int i = 11 - random.nextInt(5);
            int j = 3 + random.nextInt(3);
            boolean flag1 = random.nextDouble() > 0.7D;
            int l = flag1 ? random.nextInt(6) + 6 : random.nextInt(15) + 3;
            if (!flag1 && random.nextDouble() > 0.9D) {
                l += random.nextInt(19) + 7;
            }
            int i1 = Math.min(l + random.nextInt(11), 18);
            int j1 = Math.min(l + random.nextInt(7) - random.nextInt(5), 11);
            int k1 = flag1 ? i : 11;
            for (int l1 = -k1; l1 < k1; ++l1) {
                for (int i2 = -k1; i2 < k1; ++i2) {
                    for (int j2 = 0; j2 < l; ++j2) {
                        int k2 = flag1 ? radiusEllipse(j2, l, j1) : radiusRound(random, j2, l, j1);
                        if (flag1 || l1 < k2) {
                            block(level, random, ox, py, oz, l, l1, j2, i2, k2, k1, flag1, j, d0, flag);
                        }
                    }
                }
            }
            smooth(level, ox, py, oz, j1, l, flag1, i);
            for (int i3 = -k1; i3 < k1; ++i3) {
                for (int j3 = -k1; j3 < k1; ++j3) {
                    for (int k3 = -1; k3 > -i1; --k3) {
                        int l3 = flag1 ? McMath.ceil((double) ((float) k1 * (1.0F - (float) Math.pow((double) k3, 2.0D) / ((float) i1 * 8.0F)))) : k1;
                        int l2 = radiusSteep(random, -k3, i1, j1);
                        if (i3 < l2) {
                            block(level, random, ox, py, oz, i1, i3, k3, j3, l2, l3, flag1, j, d0, flag);
                        }
                    }
                }
            }
            boolean flag2 = flag1 ? random.nextDouble() > 0.1D : random.nextDouble() > 0.7D;
            if (flag2) {
                cutOut(random, level, j1, l, ox, py, oz, flag1, i, d0, j);
            }
            return true;
        }

        private void cutOut(XoroRandom random, Region level, int p2, int p3, int ox, int oy, int oz, boolean p5, int p6, double p7, int p8) {
            int i = random.nextBoolean() ? -1 : 1;
            int j = random.nextBoolean() ? -1 : 1;
            int k = random.nextInt(Math.max(p2 / 2 - 2, 1));
            if (random.nextBoolean()) {
                k = p2 / 2 + 1 - random.nextInt(Math.max(p2 - p2 / 2 - 1, 1));
            }
            int l = random.nextInt(Math.max(p2 / 2 - 2, 1));
            if (random.nextBoolean()) {
                l = p2 / 2 + 1 - random.nextInt(Math.max(p2 - p2 / 2 - 1, 1));
            }
            if (p5) {
                k = l = random.nextInt(Math.max(p6 - 5, 1));
            }
            int cx = i * k;
            int cz = j * l;
            double d0 = p5 ? p7 + (Math.PI / 2D) : random.nextDouble() * 2.0D * Math.PI;
            for (int i1 = 0; i1 < p3 - 3; ++i1) {
                int j1 = radiusRound(random, i1, p3, p2);
                carve(j1, i1, ox, oy, oz, level, false, d0, cx, cz, p6, p8);
            }
            for (int k1 = -1; k1 > -p3 + random.nextInt(5); --k1) {
                int l1 = radiusSteep(random, -k1, p3, p2);
                carve(l1, k1, ox, oy, oz, level, true, d0, cx, cz, p6, p8);
            }
        }

        private void carve(int p0, int p1, int ox, int oy, int oz, Region level, boolean water, double angle, int cx, int cz, int p7, int p8) {
            int i = p0 + 1 + p7 / 3;
            int j = Math.min(p0 - 3, 3) + p8 / 2 - 1;
            for (int k = -i; k < i; ++k) {
                for (int l = -i; l < i; ++l) {
                    double d0 = ellipse(k, l, cx, cz, i, j, angle);
                    if (d0 < 0.0D) {
                        int bx = ox + k;
                        int by = oy + p1;
                        int bz = oz + l;
                        int s = level.get(bx, by, bz);
                        if (icebergState(s) || s == SNOW_BLOCK) {
                            if (water) {
                                level.set(bx, by, bz, WATER);
                            } else {
                                level.set(bx, by, bz, AIR);
                                if (sameBlock(level.get(bx, by + 1, bz), SNOW)) {
                                    level.set(bx, by + 1, bz, AIR);
                                }
                            }
                        }
                    }
                }
            }
        }

        private void block(Region level, XoroRandom random, int ox, int oy, int oz, int p3, int p4, int p5, int p6, int p7,
                           int p8, boolean p9, int p10, double p11, boolean p12) {
            double d0 = p9 ? ellipse(p4, p6, 0, 0, p8, ellipseC(p5, p3, p10), p11) : circle(p4, p6, p7, random);
            if (d0 < 0.0D) {
                double d1 = p9 ? -0.5D : (double) (-6 - random.nextInt(3));
                if (d0 > d1 && random.nextDouble() > 0.9D) {
                    return;
                }
                setIceberg(ox + p4, oy + p5, oz + p6, level, random, p3 - p5, p3, p9, p12);
            }
        }

        private void setIceberg(int x, int y, int z, Region level, XoroRandom random, int p3, int p4, boolean p5, boolean p6) {
            int s = level.get(x, y, z);
            if (isAir(s) || s == SNOW_BLOCK || s == ICE || s == WATER) {
                boolean flag = !p5 || random.nextDouble() > 0.05D;
                int i = p5 ? 3 : 2;
                if (p6 && s != WATER && (double) p3 <= (double) random.nextInt(Math.max(1, p4 / i)) + (double) p4 * 0.6D && flag) {
                    level.set(x, y, z, SNOW_BLOCK);
                } else {
                    level.set(x, y, z, state);
                }
            }
        }

        private static int ellipseC(int p0, int p1, int p2) {
            int i = p2;
            if (p0 > 0 && p1 - p0 <= 3) {
                i = p2 - (4 - (p1 - p0));
            }
            return i;
        }

        private static double circle(int p0, int p1, int p3, XoroRandom random) {
            float f = 10.0F * McMath.clamp(random.nextFloat(), 0.2F, 0.8F) / (float) p3;
            return (double) f + Math.pow((double) p0, 2.0D) + Math.pow((double) p1, 2.0D) - Math.pow((double) p3, 2.0D);
        }

        private static double ellipse(int p0, int p1, int cx, int cz, int p3, int p4, double p5) {
            return Math.pow(((double) (p0 - cx) * Math.cos(p5) - (double) (p1 - cz) * Math.sin(p5)) / (double) p3, 2.0D)
                + Math.pow(((double) (p0 - cx) * Math.sin(p5) + (double) (p1 - cz) * Math.cos(p5)) / (double) p4, 2.0D) - 1.0D;
        }

        private static int radiusRound(XoroRandom random, int p1, int p2, int p3) {
            float f = 3.5F - random.nextFloat();
            float f1 = (1.0F - (float) Math.pow((double) p1, 2.0D) / ((float) p2 * f)) * (float) p3;
            if (p2 > 15 + random.nextInt(5)) {
                int i = p1 < 3 + random.nextInt(6) ? p1 / 2 : p1;
                f1 = (1.0F - (float) i / ((float) p2 * f * 0.4F)) * (float) p3;
            }
            return McMath.ceil((double) (f1 / 2.0F));
        }

        private static int radiusEllipse(int p0, int p1, int p2) {
            float f1 = (1.0F - (float) Math.pow((double) p0, 2.0D) / ((float) p1 * 1.0F)) * (float) p2;
            return McMath.ceil((double) (f1 / 2.0F));
        }

        private static int radiusSteep(XoroRandom random, int p1, int p2, int p3) {
            float f = 1.0F + random.nextFloat() / 2.0F;
            float f1 = (1.0F - (float) p1 / ((float) p2 * f)) * (float) p3;
            return McMath.ceil((double) (f1 / 2.0F));
        }

        private static void smooth(Region level, int ox, int oy, int oz, int p2, int p3, boolean p4, int p5) {
            int i = p4 ? p5 : p2 / 2;
            for (int j = -i; j <= i; ++j) {
                for (int k = -i; k <= i; ++k) {
                    for (int l = 0; l <= p3; ++l) {
                        int x = ox + j;
                        int y = oy + l;
                        int z = oz + k;
                        int s = level.get(x, y, z);
                        if (icebergState(s) || sameBlock(s, SNOW)) {
                            if (isAir(level.get(x, y - 1, z))) {
                                level.set(x, y, z, AIR);
                                level.set(x, y + 1, z, AIR);
                            } else if (icebergState(s)) {
                                int i1 = 0;
                                if (!icebergState(level.get(x - 1, y, z))) {
                                    ++i1;
                                }
                                if (!icebergState(level.get(x + 1, y, z))) {
                                    ++i1;
                                }
                                if (!icebergState(level.get(x, y, z - 1))) {
                                    ++i1;
                                }
                                if (!icebergState(level.get(x, y, z + 1))) {
                                    ++i1;
                                }
                                if (i1 >= 3) {
                                    level.set(x, y, z, AIR);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- BlockBlobFeature (forest_rock) ----

    static final class BlockBlob implements Feature {
        final int state;

        BlockBlob(int state) {
            this.state = state;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            for (; y > -64 + 3; --y) {
                int b = level.get(x, y - 1, z);
                if (!isAir(b) && (LushCaves.dirt(b) || Dripstone.baseStoneOverworld(b))) {
                    break;
                }
            }
            if (y <= -64 + 3) {
                return false;
            }
            for (int n = 0; n < 3; ++n) {
                int i = random.nextInt(2);
                int j = random.nextInt(2);
                int k = random.nextInt(2);
                float f = (float) (i + j + k) * 0.333F + 0.5F;
                double r = (double) (f * f);
                for (int bz = z - k; bz <= z + k; ++bz) {
                    for (int by = y - j; by <= y + j; ++by) {
                        for (int bx = x - i; bx <= x + i; ++bx) {
                            int dx = bx - x;
                            int dy = by - y;
                            int dz = bz - z;
                            if ((double) (dx * dx) + (double) (dy * dy) + (double) (dz * dz) <= r) {
                                level.set(bx, by, bz, state);
                            }
                        }
                    }
                }
                x += -1 + random.nextInt(2);
                y += -random.nextInt(2);
                z += -1 + random.nextInt(2);
            }
            return true;
        }
    }

    // ---- IceSpikeFeature ----

    private static boolean spikeReplaceable(int s) {
        return isAir(s) || LushCaves.dirt(s) || s == SNOW_BLOCK || s == ICE;
    }

    static final class IceSpike implements Feature {
        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            while (isAir(level.get(x, y, z)) && y > -64 + 2) {
                --y;
            }
            if (level.get(x, y, z) != SNOW_BLOCK) {
                return false;
            }
            y += random.nextInt(4);
            int i = random.nextInt(4) + 7;
            int j = i / 4 + random.nextInt(2);
            if (j > 1 && random.nextInt(60) == 0) {
                y += 10 + random.nextInt(30);
            }
            for (int k = 0; k < i; ++k) {
                float f = (1.0F - (float) k / (float) i) * (float) j;
                int l = McMath.ceil((double) f);
                for (int i1 = -l; i1 <= l; ++i1) {
                    float f1 = (float) Math.abs(i1) - 0.25F;
                    for (int j1 = -l; j1 <= l; ++j1) {
                        float f2 = (float) Math.abs(j1) - 0.25F;
                        if ((i1 == 0 && j1 == 0 || !(f1 * f1 + f2 * f2 > f * f))
                                && (i1 != -l && i1 != l && j1 != -l && j1 != l || !(random.nextFloat() > 0.75F))) {
                            if (spikeReplaceable(level.get(x + i1, y + k, z + j1))) {
                                level.set(x + i1, y + k, z + j1, PACKED_ICE);
                            }
                            if (k != 0 && l > 1) {
                                if (spikeReplaceable(level.get(x + i1, y - k, z + j1))) {
                                    level.set(x + i1, y - k, z + j1, PACKED_ICE);
                                }
                            }
                        }
                    }
                }
            }
            int k1 = j - 1;
            if (k1 < 0) {
                k1 = 0;
            } else if (k1 > 1) {
                k1 = 1;
            }
            for (int l1 = -k1; l1 <= k1; ++l1) {
                for (int i2 = -k1; i2 <= k1; ++i2) {
                    int by = y - 1;
                    int j2 = 50;
                    if (Math.abs(l1) == 1 && Math.abs(i2) == 1) {
                        j2 = random.nextInt(5);
                    }
                    while (by > 50) {
                        int s = level.get(x + l1, by, z + i2);
                        if (!spikeReplaceable(s) && s != PACKED_ICE) {
                            break;
                        }
                        level.set(x + l1, by, z + i2, PACKED_ICE);
                        --by;
                        --j2;
                        if (j2 <= 0) {
                            by -= random.nextInt(5) + 1;
                            j2 = random.nextInt(5);
                        }
                    }
                }
            }
            return true;
        }
    }

    // ---- IcePatchFeature: down to a snow block, then BaseDiskFeature (packed ice, radius 2..3, half height 1) ----

    static final class IcePatch implements Feature {
        private final Misc.Disk disk = new Misc.Disk(PACKED_ICE, 2, 3, 1, false, DIRT, GRASS_BLOCK, PODZOL, COARSE_DIRT,
            MYCELIUM, SNOW_BLOCK, ICE);

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            if (level.rules >= Rules.V119) {
                // 1.19's DiskFeature, at the snow block the placement found.
                return disk.columns(level, random, x, y, z);
            }
            while (isAir(level.get(x, y, z)) && y > -64 + 2) {
                --y;
            }
            if (level.get(x, y, z) != SNOW_BLOCK) {
                return false;
            }
            return disk.columns(level, random, x, y, z);
        }
    }

    // ---- BlueIceFeature ----

    static final class BlueIce implements Feature {
        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            if (y > 63 - 1) {
                return false;
            }
            if (level.get(x, y, z) != WATER && level.get(x, y - 1, z) != WATER) {
                return false;
            }
            boolean flag = false;
            for (int d = 1; d < 6; d++) {
                if (level.get(x + LushCaves.DX[d], y + LushCaves.DY[d], z + LushCaves.DZ[d]) == PACKED_ICE) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                return false;
            }
            level.set(x, y, z, BLUE_ICE);
            for (int i = 0; i < 200; ++i) {
                int j = random.nextInt(5) - random.nextInt(6);
                int k = 3;
                if (j < 2) {
                    k += j / 2;
                }
                if (k >= 1) {
                    int bx = x + random.nextInt(k) - random.nextInt(k);
                    int by = y + j;
                    int bz = z + random.nextInt(k) - random.nextInt(k);
                    int s = level.get(bx, by, bz);
                    if (isAir(s) || s == WATER || s == PACKED_ICE || s == ICE) {
                        for (int d = 0; d < 6; d++) {
                            if (level.get(bx + LushCaves.DX[d], by + LushCaves.DY[d], bz + LushCaves.DZ[d]) == BLUE_ICE) {
                                level.set(bx, by, bz, BLUE_ICE);
                                break;
                            }
                        }
                    }
                }
            }
            return true;
        }
    }

    // ---- DesertWellFeature ----

    static final class DesertWell implements Feature {
        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            for (y = y + 1; isAir(level.get(x, y, z)) && y > -64 + 2; --y) {
            }
            if (level.get(x, y, z) != SAND) {
                return false;
            }
            for (int i = -2; i <= 2; ++i) {
                for (int j = -2; j <= 2; ++j) {
                    if (isAir(level.get(x + i, y - 1, z + j)) && isAir(level.get(x + i, y - 2, z + j))) {
                        return false;
                    }
                }
            }
            for (int l = -1; l <= 0; ++l) {
                for (int l1 = -2; l1 <= 2; ++l1) {
                    for (int k = -2; k <= 2; ++k) {
                        level.set(x + l1, y + l, z + k, SANDSTONE);
                    }
                }
            }
            level.set(x, y, z, WATER);
            // Direction.Plane.HORIZONTAL: north, east, south, west.
            level.set(x, y, z - 1, WATER);
            level.set(x + 1, y, z, WATER);
            level.set(x, y, z + 1, WATER);
            level.set(x - 1, y, z, WATER);
            for (int i1 = -2; i1 <= 2; ++i1) {
                for (int i2 = -2; i2 <= 2; ++i2) {
                    if (i1 == -2 || i1 == 2 || i2 == -2 || i2 == 2) {
                        level.set(x + i1, y + 1, z + i2, SANDSTONE);
                    }
                }
            }
            level.set(x + 2, y + 1, z, SANDSTONE_SLAB);
            level.set(x - 2, y + 1, z, SANDSTONE_SLAB);
            level.set(x, y + 1, z + 2, SANDSTONE_SLAB);
            level.set(x, y + 1, z - 2, SANDSTONE_SLAB);
            for (int j1 = -1; j1 <= 1; ++j1) {
                for (int j2 = -1; j2 <= 1; ++j2) {
                    level.set(x + j1, y + 4, z + j2, j1 == 0 && j2 == 0 ? SANDSTONE : SANDSTONE_SLAB);
                }
            }
            for (int k1 = 1; k1 <= 3; ++k1) {
                level.set(x - 1, y + k1, z - 1, SANDSTONE);
                level.set(x - 1, y + k1, z + 1, SANDSTONE);
                level.set(x + 1, y + k1, z - 1, SANDSTONE);
                level.set(x + 1, y + k1, z + 1, SANDSTONE);
            }
            return true;
        }
    }
}
