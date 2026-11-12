package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * The block codes of the terrain core's chunk arrays (one char, 16 bits, per block), each a 1.18.2 block's default state.
 * 0-9 come out of ChunkFiller (the noise fill), 10-33 out of SurfaceBuilder. The oracle (VanillaWorld) maps
 * vanilla's states to the same numbers; keep the two lists and docs/TERRAIN_CORE.md in step.
 */
public final class BlockCodes {
    private BlockCodes() {
    }

    public static final int AIR = 0;
    public static final int STONE = 1;
    public static final int WATER = 2;
    public static final int LAVA = 3;
    public static final int COPPER_ORE = 4;
    public static final int RAW_COPPER_BLOCK = 5;
    public static final int GRANITE = 6;
    public static final int DEEPSLATE_IRON_ORE = 7;
    public static final int RAW_IRON_BLOCK = 8;
    public static final int TUFF = 9;
    public static final int BEDROCK = 10;
    public static final int DEEPSLATE = 11;
    public static final int DIRT = 12;
    public static final int GRASS_BLOCK = 13;
    public static final int PODZOL = 14;
    public static final int COARSE_DIRT = 15;
    public static final int MYCELIUM = 16;
    public static final int CALCITE = 17;
    public static final int GRAVEL = 18;
    public static final int SAND = 19;
    public static final int SANDSTONE = 20;
    public static final int RED_SAND = 21;
    public static final int RED_SANDSTONE = 22;
    public static final int PACKED_ICE = 23;
    public static final int SNOW_BLOCK = 24;
    public static final int POWDER_SNOW = 25;
    public static final int ICE = 26;
    public static final int TERRACOTTA = 27;
    public static final int WHITE_TERRACOTTA = 28;
    public static final int ORANGE_TERRACOTTA = 29;
    public static final int YELLOW_TERRACOTTA = 30;
    public static final int BROWN_TERRACOTTA = 31;
    public static final int RED_TERRACOTTA = 32;
    public static final int LIGHT_GRAY_TERRACOTTA = 33;
    public static final int COUNT = 34;

    private static final String[] NAMES = {"air", "stone", "water", "lava", "copper_ore", "raw_copper_block", "granite",
        "deepslate_iron_ore", "raw_iron_block", "tuff", "bedrock", "deepslate", "dirt", "grass_block", "podzol",
        "coarse_dirt", "mycelium", "calcite", "gravel", "sand", "sandstone", "red_sand", "red_sandstone", "packed_ice",
        "snow_block", "powder_snow", "ice", "terracotta", "white_terracotta", "orange_terracotta", "yellow_terracotta",
        "brown_terracotta", "red_terracotta", "light_gray_terracotta"};

    public static String name(int code) {
        return code >= 0 && code < NAMES.length ? NAMES[code] : "code " + code;
    }
}
