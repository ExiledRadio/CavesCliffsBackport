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
    // Springs, disks, underwater magma.
    public static final int MAGMA_BLOCK = 53;
    /** The fixed codes above; features add theirs with {@link #register} (in class initializers) from here on. */
    public static final int FIXED = 54;

    private static final String[] STATES = {"air", "stone", "water[level=0]", "lava[level=0]", "copper_ore",
        "raw_copper_block", "granite", "deepslate_iron_ore", "raw_iron_block", "tuff", "bedrock", "deepslate[axis=y]",
        "dirt", "grass_block[snowy=false]", "podzol[snowy=false]", "coarse_dirt", "mycelium[snowy=false]", "calcite",
        "gravel", "sand", "sandstone", "red_sand", "red_sandstone", "packed_ice", "snow_block", "powder_snow", "ice",
        "terracotta", "white_terracotta", "orange_terracotta", "yellow_terracotta", "brown_terracotta",
        "red_terracotta", "light_gray_terracotta",
        "coal_ore", "deepslate_coal_ore", "iron_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore",
        "redstone_ore[lit=false]", "deepslate_redstone_ore[lit=false]", "emerald_ore", "deepslate_emerald_ore",
        "lapis_ore", "deepslate_lapis_ore", "diamond_ore", "deepslate_diamond_ore", "infested_stone",
        "infested_deepslate[axis=y]", "diorite", "andesite", "clay", "magma_block"};

    private static final Map<String, Integer> BY_STATE = new HashMap<>();
    private static String[] states = new String[256];
    private static int count;
    /** Per code: NOT_AIR, BLOCKS_MOTION, FLUID, LEAVES bits (the heightmap predicates' inputs). */
    private static byte[] flags = new byte[256];

    public static final int F_NOT_AIR = 1;
    /** Material.blocksMotion. */
    public static final int F_BLOCKS_MOTION = 2;
    /** A non-empty fluid state (water, lava, waterlogged, water plants). */
    public static final int F_FLUID = 4;
    /** LeavesBlock. */
    public static final int F_LEAVES = 8;

    /** How many codes exist so far. */
    public static int count() {
        return count;
    }

    /**
     * The code of a vanilla state named as state() names it (block path, properties sorted), registering it if new.
     * Call from class initializers only (not thread safe).
     */
    public static synchronized int register(String state) {
        Integer c = BY_STATE.get(state);
        if (c != null) {
            return c;
        }
        if (count == states.length) {
            states = java.util.Arrays.copyOf(states, count * 2);
            flags = java.util.Arrays.copyOf(flags, count * 2);
        }
        states[count] = state;
        flags[count] = (byte) materialFlags(state);
        BY_STATE.put(state, count);
        return count++;
    }

    /** Blocks whose Material does not block motion (besides air and fluids): plants, snow layers, decorations. */
    private static final java.util.Set<String> NO_MOTION = new java.util.HashSet<>(java.util.Arrays.asList(
        "powder_snow", "snow", "grass", "fern", "tall_grass", "large_fern", "dead_bush", "seagrass", "tall_seagrass",
        "kelp", "kelp_plant", "sea_pickle", "lily_pad", "vine", "glow_lichen", "cave_vines", "cave_vines_plant",
        "spore_blossom", "big_dripleaf", "big_dripleaf_stem", "small_dripleaf", "hanging_roots", "moss_carpet",
        "azalea", "flowering_azalea", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
        "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley",
        "wither_rose", "sunflower", "lilac", "rose_bush", "peony", "brown_mushroom", "red_mushroom", "sugar_cane",
        "sweet_berry_bush", "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling",
        "dark_oak_sapling", "bamboo_sapling", "torch", "wall_torch", "cobweb", "fire", "bubble_column",
        "tube_coral", "brain_coral", "bubble_coral", "fire_coral", "horn_coral", "tube_coral_fan",
        "brain_coral_fan", "bubble_coral_fan", "fire_coral_fan", "horn_coral_fan", "tube_coral_wall_fan",
        "brain_coral_wall_fan", "bubble_coral_wall_fan", "fire_coral_wall_fan", "horn_coral_wall_fan",
        "structure_void", "light"));
    private static final java.util.Set<String> FLUID_BLOCKS = new java.util.HashSet<>(java.util.Arrays.asList(
        "water", "lava", "seagrass", "tall_seagrass", "kelp", "kelp_plant", "bubble_column"));

    private static int materialFlags(String state) {
        int i = state.indexOf('[');
        String name = i < 0 ? state : state.substring(0, i);
        if (name.equals("air") || name.equals("cave_air") || name.equals("void_air")) {
            return 0;
        }
        int f = F_NOT_AIR;
        if (!NO_MOTION.contains(name) && !FLUID_BLOCKS.contains(name)) {
            f |= F_BLOCKS_MOTION;
        }
        if (FLUID_BLOCKS.contains(name) || state.contains("waterlogged=true")) {
            f |= F_FLUID;
        }
        if (name.endsWith("_leaves")) {
            f |= F_LEAVES;
        }
        return f;
    }

    /** The code's heightmap flags (F_*). */
    public static int flags(int code) {
        return flags[code];
    }

    /** The code's state: block path, then properties sorted by name ("grass_block[snowy=false]"). */
    public static String state(int code) {
        return code >= 0 && code < count ? states[code] : "code " + code;
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
        return (flags[code] & F_NOT_AIR) == 0;
    }

    /** Material.blocksMotion: what OCEAN_FLOOR_WG and MOTION_BLOCKING count (neither air, fluid nor powder snow). */
    public static boolean blocksMotion(int code) {
        return (flags[code] & F_BLOCKS_MOTION) != 0;
    }

    // Last: register() needs the sets above.
    static {
        for (String st : STATES) {
            register(st);
        }
        if (count != FIXED) {
            throw new IllegalStateException("BlockCodes: " + count + " states for " + FIXED + " codes");
        }
    }
}
