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
    /** Per code: the F_* bits (Material properties, fluid, leaves). */
    private static short[] flags = new short[256];
    /** Per code: its block's index (states of one block share it). */
    private static int[] blockOf = new int[256];
    private static final Map<String, Integer> BLOCKS = new HashMap<>();

    public static final int F_NOT_AIR = 1;
    /** Material.blocksMotion. */
    public static final int F_BLOCKS_MOTION = 2;
    /** A non-empty fluid state (water, lava, waterlogged, water plants). */
    public static final int F_FLUID = 4;
    /** LeavesBlock. */
    public static final int F_LEAVES = 8;
    /** Material.isSolid. */
    public static final int F_SOLID = 16;
    /** Material.isLiquid. */
    public static final int F_LIQUID = 32;
    /** Material.isReplaceable. */
    public static final int F_REPLACEABLE = 64;
    /** A water fluid state (FluidTags.WATER): water, waterlogged, water plants. */
    public static final int F_WATER = 128;
    /** BlockState.isSolidRender: a full cube that occludes (rock-like blocks that are not in NOT_OCCLUDING). */
    public static final int F_SOLID_RENDER = 256;
    /** Every face full (isFaceSturdy, Block.isFaceFull of the collision shape, canSupportCenter): full collision cubes. */
    public static final int F_STURDY = 512;
    /** The UP face full though the block is not (azalea: its canopy box reaches the top). */
    public static final int F_TOP_FULL = 1024;
    /** The DOWN face full though the block is not (carpets, dirt path, farmland, end portal frame). */
    public static final int F_BOTTOM_FULL = 2048;

    /** Material.REPLACEABLE_PLANT (grass, ferns, dead bush, vines, glow lichen, hanging roots, the tall plants). */
    public static final int F_REPLACEABLE_PLANT = 4096;

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
            blockOf = java.util.Arrays.copyOf(blockOf, count * 2);
        }
        states[count] = state;
        flags[count] = (short) materialFlags(state);
        int bi = state.indexOf('[');
        String block = bi < 0 ? state : state.substring(0, bi);
        Integer b = BLOCKS.get(block);
        if (b == null) {
            b = BLOCKS.size();
            BLOCKS.put(block, b);
        }
        blockOf[count] = b;
        BY_STATE.put(state, count);
        return count++;
    }

    // Material of the blocks that are not like stone (solid, blocks motion, not replaceable, not liquid).
    private static final java.util.Map<String, String> MATERIAL = new java.util.HashMap<>();

    private static void material(String material, String... blocks) {
        for (String b : blocks) {
            MATERIAL.put(b, material);
        }
    }

    static {
        material("plant", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
            "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose",
            "brown_mushroom", "red_mushroom", "sugar_cane", "sweet_berry_bush", "oak_sapling", "spruce_sapling",
            "birch_sapling", "jungle_sapling", "acacia_sapling", "dark_oak_sapling", "cave_vines", "cave_vines_plant",
            "spore_blossom", "big_dripleaf", "big_dripleaf_stem", "small_dripleaf", "moss_carpet", "azalea",
            "flowering_azalea", "lily_pad", "cocoa");
        material("replaceable_plant", "grass", "fern", "dead_bush", "tall_grass", "large_fern", "vine",
            "glow_lichen", "hanging_roots", "sunflower", "lilac", "rose_bush", "peony");
        material("replaceable_water_plant", "seagrass", "tall_seagrass");
        material("water_plant", "kelp", "kelp_plant", "sea_pickle", "tube_coral", "brain_coral", "bubble_coral",
            "fire_coral", "horn_coral", "tube_coral_fan", "brain_coral_fan", "bubble_coral_fan", "fire_coral_fan",
            "horn_coral_fan", "tube_coral_wall_fan", "brain_coral_wall_fan", "bubble_coral_wall_fan",
            "fire_coral_wall_fan", "horn_coral_wall_fan");
        material("liquid", "water", "lava", "bubble_column");
        material("top_snow", "snow");
        material("powder_snow", "powder_snow");
        material("decoration", "torch", "wall_torch");
        material("web", "cobweb");
        material("bamboo_sapling", "bamboo_sapling");
        material("fire", "fire");
    }

    /** Solid, motion-blocking blocks that are no full occluding cube. */
    private static final java.util.Set<String> NOT_OCCLUDING = new java.util.HashSet<>(java.util.Arrays.asList(
        "ice", "spawner", "chest", "glass", "amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud",
        "small_amethyst_bud", "pointed_dripstone", "cactus", "bamboo", "dirt_path", "farmland", "lantern",
        "big_dripleaf", "cobweb", "bamboo_sapling", "end_portal_frame", "snow"));

    /** Motion-blocking blocks without a full collision cube. */
    private static final java.util.Set<String> NOT_STURDY = new java.util.HashSet<>(java.util.Arrays.asList(
        "pointed_dripstone", "amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud",
        "chest", "cactus", "bamboo", "dirt_path", "farmland", "lantern", "end_portal_frame", "cobweb", "bamboo_sapling",
        "bee_nest_none"));

    private static final java.util.Set<String> FLUID_BLOCKS = new java.util.HashSet<>(java.util.Arrays.asList(
        "water", "lava", "seagrass", "tall_seagrass", "kelp", "kelp_plant", "bubble_column"));

    private static int materialFlags(String state) {
        int i = state.indexOf('[');
        String name = i < 0 ? state : state.substring(0, i);
        if (name.equals("air") || name.equals("cave_air") || name.equals("void_air")) {
            return F_REPLACEABLE;
        }
        int f = F_NOT_AIR;
        String m = MATERIAL.get(name);
        if (m == null) {
            f |= F_BLOCKS_MOTION | F_SOLID;
            if (!NOT_OCCLUDING.contains(name) && !name.endsWith("_leaves")) {
                f |= F_SOLID_RENDER;
            }
            if (!NOT_STURDY.contains(name)) {
                f |= F_STURDY;
            }
            if (name.equals("dirt_path") || name.equals("farmland") || name.equals("end_portal_frame")) {
                f |= F_BOTTOM_FULL;
            }
        } else {
            switch (m) {
                case "web":
                case "bamboo_sapling":
                    f |= F_SOLID;
                    break;
                case "liquid":
                    f |= F_LIQUID | F_REPLACEABLE;
                    break;
                case "replaceable_plant":
                    f |= F_REPLACEABLE | F_REPLACEABLE_PLANT;
                    break;
                case "replaceable_water_plant":
                case "top_snow":
                case "fire":
                    f |= F_REPLACEABLE;
                    break;
                default:
                    break;
            }
        }
        if (FLUID_BLOCKS.contains(name) || state.contains("waterlogged=true")) {
            f |= F_FLUID;
            if (!name.equals("lava")) {
                f |= F_WATER;
            }
        }
        if (name.endsWith("_leaves")) {
            f |= F_LEAVES;
        }
        if (name.equals("azalea") || name.equals("flowering_azalea")) {
            f |= F_TOP_FULL;
        }
        if (name.endsWith("_carpet")) {
            f |= F_BOTTOM_FULL;
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

    /** BlockState.is(other.getBlock()): the same block, any properties. */
    public static boolean sameBlock(int a, int b) {
        return blockOf[a] == blockOf[b];
    }

    /** isFaceSturdy on every side (the support shape): full collision cubes but leaves, whose support shape is empty. */
    public static boolean sturdy(int code) {
        return (flags[code] & (F_STURDY | F_LEAVES)) == F_STURDY;
    }

    /**
     * BlockState.isFaceSturdy(face) / Block.canSupportCenter: the support shape, which is the collision shape except
     * for leaves (empty). {@link #faceFull} is the collision shape's face (vines, glow lichen, snow layers).
     */
    public static boolean sturdyFace(int code, int face) {
        return faceFull(code, face) && (flags[code] & F_LEAVES) == 0;
    }

    /**
     * Block.isFaceFull(collision shape, face) / isFaceSturdy(face): face as a Direction ordinal (down, up, north,
     * south, west, east).
     */
    public static boolean faceFull(int code, int face) {
        int f = flags[code];
        return (f & F_STURDY) != 0 || face == 1 && (f & F_TOP_FULL) != 0 || face == 0 && (f & F_BOTTOM_FULL) != 0;
    }

    public static boolean solidRender(int code) {
        return (flags[code] & F_SOLID_RENDER) != 0;
    }

    public static boolean solid(int code) {
        return (flags[code] & F_SOLID) != 0;
    }

    public static boolean liquid(int code) {
        return (flags[code] & F_LIQUID) != 0;
    }

    public static boolean replaceable(int code) {
        return (flags[code] & F_REPLACEABLE) != 0;
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

    /** Registered after the fixed codes (the class initializer above must run first). */
    public static final int CAVE_AIR = register("cave_air");
}
