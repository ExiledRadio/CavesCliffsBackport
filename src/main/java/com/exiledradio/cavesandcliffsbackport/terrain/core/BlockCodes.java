package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.HashMap;
import java.util.Map;

/**
 * The block codes of the terrain core's chunk arrays (one char, 16 bits, per block), each one 1.18.2 block state.
 * 0-9 come out of ChunkFiller (the noise fill), 10-33 out of SurfaceBuilder, 34 on out of the features. The older
 * oracle dumps (fill, surface, carvers) use VanillaWorld's byte codes, the same numbers 0-33; the feature dumps
 * name every state (STATES: the block's path, then its properties sorted by name, as VanillaFeatureDump writes
 * them) and the checks map names to codes. Keep this list and docs/TERRAIN_CORE.md in step.
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
    // Ores and the stone blobs (UNDERGROUND_ORES, ore_infested).
    public static final int COAL_ORE = 34;
    public static final int DEEPSLATE_COAL_ORE = 35;
    public static final int IRON_ORE = 36;
    public static final int DEEPSLATE_COPPER_ORE = 37;
    public static final int GOLD_ORE = 38;
    public static final int DEEPSLATE_GOLD_ORE = 39;
    public static final int REDSTONE_ORE = 40;
    public static final int DEEPSLATE_REDSTONE_ORE = 41;
    public static final int EMERALD_ORE = 42;
    public static final int DEEPSLATE_EMERALD_ORE = 43;
    public static final int LAPIS_ORE = 44;
    public static final int DEEPSLATE_LAPIS_ORE = 45;
    public static final int DIAMOND_ORE = 46;
    public static final int DEEPSLATE_DIAMOND_ORE = 47;
    public static final int INFESTED_STONE = 48;
    public static final int INFESTED_DEEPSLATE = 49;
    public static final int DIORITE = 50;
    public static final int ANDESITE = 51;
    public static final int CLAY = 52;
    public static final int COUNT = 53;

    private static final String[] STATES = {"air", "stone", "water[level=0]", "lava[level=0]", "copper_ore",
        "raw_copper_block", "granite", "deepslate_iron_ore", "raw_iron_block", "tuff", "bedrock", "deepslate[axis=y]",
        "dirt", "grass_block[snowy=false]", "podzol[snowy=false]", "coarse_dirt", "mycelium[snowy=false]", "calcite",
        "gravel", "sand", "sandstone", "red_sand", "red_sandstone", "packed_ice", "snow_block", "powder_snow", "ice",
        "terracotta", "white_terracotta", "orange_terracotta", "yellow_terracotta", "brown_terracotta",
        "red_terracotta", "light_gray_terracotta",
        "coal_ore", "deepslate_coal_ore", "iron_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore",
        "redstone_ore[lit=false]", "deepslate_redstone_ore[lit=false]", "emerald_ore", "deepslate_emerald_ore",
        "lapis_ore", "deepslate_lapis_ore", "diamond_ore", "deepslate_diamond_ore", "infested_stone",
        "infested_deepslate[axis=y]", "diorite", "andesite", "clay"};

    private static final Map<String, Integer> BY_STATE = new HashMap<>();

    static {
        if (STATES.length != COUNT) {
            throw new IllegalStateException("BlockCodes: " + STATES.length + " states for " + COUNT + " codes");
        }
        for (int i = 0; i < STATES.length; i++) {
            BY_STATE.put(STATES[i], i);
        }
    }

    /** The code's state: block path, then properties sorted by name ("grass_block[snowy=false]"). */
    public static String state(int code) {
        return code >= 0 && code < STATES.length ? STATES[code] : "code " + code;
    }

    /** The code of a state named as state() names it, or -1. */
    public static int byState(String state) {
        Integer c = BY_STATE.get(state);
        return c == null ? -1 : c;
    }

    /** The code's block (state() without properties). */
    public static String name(int code) {
        String s = state(code);
        int i = s.indexOf('[');
        return i < 0 ? s : s.substring(0, i);
    }

    /** Material.isAir. */
    public static boolean isAir(int code) {
        return code == AIR;
    }

    /** Material.blocksMotion: what OCEAN_FLOOR_WG and MOTION_BLOCKING count (neither air, fluid nor powder snow). */
    public static boolean blocksMotion(int code) {
        return code != AIR && code != WATER && code != LAVA && code != POWDER_SNOW;
    }
}
