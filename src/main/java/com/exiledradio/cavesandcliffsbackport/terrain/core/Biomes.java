package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * 1.18.2's biomes as int ids: the index in Biomes.java's declaration order (net.minecraft.world.level.biome.Biomes),
 * which is also the order the oracle writes. Only the overworld's 54 (ids 1-53 and the void) come out of the
 * overworld biome source; the nether and end ids are listed so the table is the whole vanilla list.
 */
public final class Biomes {
    private Biomes() {
    }

    public static final int THE_VOID = 0;
    public static final int PLAINS = 1;
    public static final int SUNFLOWER_PLAINS = 2;
    public static final int SNOWY_PLAINS = 3;
    public static final int ICE_SPIKES = 4;
    public static final int DESERT = 5;
    public static final int SWAMP = 6;
    public static final int FOREST = 7;
    public static final int FLOWER_FOREST = 8;
    public static final int BIRCH_FOREST = 9;
    public static final int DARK_FOREST = 10;
    public static final int OLD_GROWTH_BIRCH_FOREST = 11;
    public static final int OLD_GROWTH_PINE_TAIGA = 12;
    public static final int OLD_GROWTH_SPRUCE_TAIGA = 13;
    public static final int TAIGA = 14;
    public static final int SNOWY_TAIGA = 15;
    public static final int SAVANNA = 16;
    public static final int SAVANNA_PLATEAU = 17;
    public static final int WINDSWEPT_HILLS = 18;
    public static final int WINDSWEPT_GRAVELLY_HILLS = 19;
    public static final int WINDSWEPT_FOREST = 20;
    public static final int WINDSWEPT_SAVANNA = 21;
    public static final int JUNGLE = 22;
    public static final int SPARSE_JUNGLE = 23;
    public static final int BAMBOO_JUNGLE = 24;
    public static final int BADLANDS = 25;
    public static final int ERODED_BADLANDS = 26;
    public static final int WOODED_BADLANDS = 27;
    public static final int MEADOW = 28;
    public static final int GROVE = 29;
    public static final int SNOWY_SLOPES = 30;
    public static final int FROZEN_PEAKS = 31;
    public static final int JAGGED_PEAKS = 32;
    public static final int STONY_PEAKS = 33;
    public static final int RIVER = 34;
    public static final int FROZEN_RIVER = 35;
    public static final int BEACH = 36;
    public static final int SNOWY_BEACH = 37;
    public static final int STONY_SHORE = 38;
    public static final int WARM_OCEAN = 39;
    public static final int LUKEWARM_OCEAN = 40;
    public static final int DEEP_LUKEWARM_OCEAN = 41;
    public static final int OCEAN = 42;
    public static final int DEEP_OCEAN = 43;
    public static final int COLD_OCEAN = 44;
    public static final int DEEP_COLD_OCEAN = 45;
    public static final int FROZEN_OCEAN = 46;
    public static final int DEEP_FROZEN_OCEAN = 47;
    public static final int MUSHROOM_FIELDS = 48;
    public static final int DRIPSTONE_CAVES = 49;
    public static final int LUSH_CAVES = 50;
    public static final int NETHER_WASTES = 51;
    public static final int WARPED_FOREST = 52;
    public static final int CRIMSON_FOREST = 53;
    public static final int SOUL_SAND_VALLEY = 54;
    public static final int BASALT_DELTAS = 55;
    public static final int THE_END = 56;
    public static final int END_HIGHLANDS = 57;
    public static final int END_MIDLANDS = 58;
    public static final int SMALL_END_ISLANDS = 59;
    public static final int END_BARRENS = 60;
    /** 1.19's (with Wild Update Backport installed, {@link Editions#WILD}). */
    public static final int MANGROVE_SWAMP = 61;
    /** 1.19's (with Wild Update Backport installed, {@link Editions#WILD}). */
    public static final int DEEP_DARK = 62;
    /** 1.20's (with Trails &amp; Tales Backport installed, {@link Editions#CHERRY}). */
    public static final int CHERRY_GROVE = 63;
    public static final int COUNT = 64;

    /** True for the overworld's biomes: 1.18.2's and the newer ones (not the nether's, the end's or the void). */
    public static boolean overworld(int id) {
        return id >= PLAINS && id <= LUSH_CAVES || id >= MANGROVE_SWAMP && id < COUNT;
    }

    private static final String[] NAMES = {"the_void", "plains", "sunflower_plains", "snowy_plains", "ice_spikes",
        "desert", "swamp", "forest", "flower_forest", "birch_forest", "dark_forest", "old_growth_birch_forest",
        "old_growth_pine_taiga", "old_growth_spruce_taiga", "taiga", "snowy_taiga", "savanna", "savanna_plateau",
        "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "windswept_savanna", "jungle",
        "sparse_jungle", "bamboo_jungle", "badlands", "eroded_badlands", "wooded_badlands", "meadow", "grove",
        "snowy_slopes", "frozen_peaks", "jagged_peaks", "stony_peaks", "river", "frozen_river", "beach",
        "snowy_beach", "stony_shore", "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "ocean", "deep_ocean",
        "cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean", "mushroom_fields", "dripstone_caves",
        "lush_caves", "nether_wastes", "warped_forest", "crimson_forest", "soul_sand_valley", "basalt_deltas",
        "the_end", "end_highlands", "end_midlands", "small_end_islands", "end_barrens", "mangrove_swamp", "deep_dark",
        "cherry_grove"};

    /** The biome's path, as in "minecraft:" + name(id). */
    public static String name(int id) {
        return NAMES[id];
    }

    public static int byName(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    // ---- Biome.getTemperature, for the two biomes surface rules ask (frozen and deep frozen ocean) ----

    private static final PerlinSimplexNoise TEMPERATURE_NOISE = new PerlinSimplexNoise(1234L, 0, 0);
    private static final PerlinSimplexNoise FROZEN_TEMPERATURE_NOISE = new PerlinSimplexNoise(3456L, -2, 0);
    static final PerlinSimplexNoise BIOME_INFO_NOISE = new PerlinSimplexNoise(2345L, 0, 0);

    /**
     * Every biome's Biome.ClimateSettings from 1.18.2's registry, by biome id (oracle: VanillaBiomeInfoDump,
     * biome_info.txt, float bits as vanilla stores them): base temperature, FROZEN temperature modifier, downfall,
     * precipitation (0 none, 1 rain, 2 snow). Ids: the_void plains sunflower_plains snowy_plains ice_spikes desert swamp forest flower_forest birch_forest dark_forest old_growth_birch_forest old_growth_pine_taiga old_growth_spruce_taiga taiga snowy_taiga savanna savanna_plateau windswept_hills windswept_gravelly_hills windswept_forest windswept_savanna jungle sparse_jungle bamboo_jungle badlands eroded_badlands wooded_badlands meadow grove snowy_slopes frozen_peaks jagged_peaks stony_peaks river frozen_river beach snowy_beach stony_shore warm_ocean lukewarm_ocean deep_lukewarm_ocean ocean deep_ocean cold_ocean deep_cold_ocean frozen_ocean deep_frozen_ocean mushroom_fields dripstone_caves lush_caves nether_wastes warped_forest crimson_forest soul_sand_valley basalt_deltas the_end end_highlands end_midlands small_end_islands end_barrens.
     */
    private static final float[] BASE_TEMPERATURE = {Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(0), Float.intBitsToFloat(0), Float.intBitsToFloat(1073741824), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1060320051), Float.intBitsToFloat(1060320051), Float.intBitsToFloat(1058642330), Float.intBitsToFloat(1060320051), Float.intBitsToFloat(1058642330), Float.intBitsToFloat(1050253722), Float.intBitsToFloat(1048576000), Float.intBitsToFloat(1048576000), Float.intBitsToFloat(-1090519040), Float.intBitsToFloat(1073741824), Float.intBitsToFloat(1073741824), Float.intBitsToFloat(1045220557), Float.intBitsToFloat(1045220557), Float.intBitsToFloat(1045220557), Float.intBitsToFloat(1073741824), Float.intBitsToFloat(1064514355), Float.intBitsToFloat(1064514355), Float.intBitsToFloat(1064514355), Float.intBitsToFloat(1073741824), Float.intBitsToFloat(1073741824), Float.intBitsToFloat(1073741824), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(-1102263091), Float.intBitsToFloat(-1097229926), Float.intBitsToFloat(-1087163597), Float.intBitsToFloat(-1087163597), Float.intBitsToFloat(1065353216), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(0), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1028443341), Float.intBitsToFloat(1045220557), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(0), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1063675494), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1073741824), Float.intBitsToFloat(1073741824), Float.intBitsToFloat(1073741824), Float.intBitsToFloat(1073741824), Float.intBitsToFloat(1073741824), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1056964608)};
    private static final boolean[] FROZEN_MODIFIER = {false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, true, true, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false};
    private static final float[] DOWNFALL = {Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1053609165), Float.intBitsToFloat(1053609165), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(0), Float.intBitsToFloat(1063675494), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1058642330), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1058642330), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1053609165), Float.intBitsToFloat(0), Float.intBitsToFloat(0), Float.intBitsToFloat(1050253722), Float.intBitsToFloat(1050253722), Float.intBitsToFloat(1050253722), Float.intBitsToFloat(0), Float.intBitsToFloat(1063675494), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1063675494), Float.intBitsToFloat(0), Float.intBitsToFloat(0), Float.intBitsToFloat(0), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1061997773), Float.intBitsToFloat(1063675494), Float.intBitsToFloat(1063675494), Float.intBitsToFloat(1063675494), Float.intBitsToFloat(1050253722), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1053609165), Float.intBitsToFloat(1050253722), Float.intBitsToFloat(1050253722), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1065353216), Float.intBitsToFloat(1053609165), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(0), Float.intBitsToFloat(0), Float.intBitsToFloat(0), Float.intBitsToFloat(0), Float.intBitsToFloat(0), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1056964608), Float.intBitsToFloat(1063675494), Float.intBitsToFloat(1053609165), Float.intBitsToFloat(1061997773)};
    private static final byte[] PRECIPITATION = {0, 1, 1, 2, 2, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 0, 0, 1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 2, 2, 2, 2, 1, 1, 2, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1};
    public static final int PRECIPITATION_NONE = 0;
    public static final int PRECIPITATION_RAIN = 1;
    public static final int PRECIPITATION_SNOW = 2;

    public static float baseTemperature(int biome) {
        return BASE_TEMPERATURE[biome];
    }

    public static float downfall(int biome) {
        return DOWNFALL[biome];
    }

    public static int precipitation(int biome) {
        return PRECIPITATION[biome];
    }

    /** Biome.getTemperature (getHeightAdjustedTemperature) at a block, for every biome. */
    public static float temperature(int biome, int x, int y, int z) {
        float base = BASE_TEMPERATURE[biome];
        float f = FROZEN_MODIFIER[biome] ? frozenModifier(x, z, base) : base;
        if (y > 80) {
            float f1 = (float) (TEMPERATURE_NOISE.getValue((double) ((float) x / 8.0F), (double) ((float) z / 8.0F)) * 8.0D);
            return f - (f1 + (float) y - 80.0F) * 0.05F / 40.0F;
        }
        return f;
    }

    private static float frozenModifier(int x, int z, float base) {
        double d0 = FROZEN_TEMPERATURE_NOISE.getValue((double) x * 0.05D, (double) z * 0.05D) * 7.0D;
        double d1 = BIOME_INFO_NOISE.getValue((double) x * 0.2D, (double) z * 0.2D);
        double d2 = d0 + d1;
        if (d2 < 0.3D) {
            double d3 = BIOME_INFO_NOISE.getValue((double) x * 0.09D, (double) z * 0.09D);
            if (d3 < 0.8D) {
                return 0.2F;
            }
        }
        return base;
    }

    /** Biome.coldEnoughToSnow: !warmEnoughToRain, temperature below 0.15. */
    public static boolean coldEnoughToSnow(int biome, int x, int y, int z) {
        return !(temperature(biome, x, y, z) >= 0.15F);
    }

    /** Biome.shouldMeltFrozenOceanIcebergSlightly. */
    public static boolean shouldMeltFrozenOceanIcebergSlightly(int biome, int x, int y, int z) {
        return temperature(biome, x, y, z) > 0.1F;
    }
}
