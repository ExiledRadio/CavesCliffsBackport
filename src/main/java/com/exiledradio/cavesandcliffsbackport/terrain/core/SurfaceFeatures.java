package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.Collections;
import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.*;

/** Surface features as 1.18.2 places them: freeze_top_layer (SnowAndFreezeFeature) so far. */
final class SurfaceFeatures {
    private SurfaceFeatures() {
    }

    static final int SNOW = register("snow[layers=1]");
    static final int GRASS_BLOCK_SNOWY = register("grass_block[snowy=true]");
    static final int PODZOL_SNOWY = register("podzol[snowy=true]");
    static final int MYCELIUM_SNOWY = register("mycelium[snowy=true]");

    static List<Placed> placed(Decorator decorator) {
        return Collections.singletonList(new Placed("freeze_top_layer", new FreezeTopLayer(decorator), BIOME));
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
}
