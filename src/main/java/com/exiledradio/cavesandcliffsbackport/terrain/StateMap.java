package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.lush.BlockGlowLichen;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes;
import com.google.common.base.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The terrain core's block codes (each a 1.18.2 block state, {@link BlockCodes#state}) as 1.12 block states.
 *
 * <p>Resolved once per code, the first time it is asked for: a table of the vanilla 1.18 blocks whose 1.12 name or
 * variant differs, else a block registered under the 1.18 name (this mod's first, then {@code minecraft}, then any
 * other mod's: the family's backports register 1.13+ blocks under their modern names). Properties with the same name
 * on the 1.12 block are copied over; the rest are dropped. {@code waterlogged=true} becomes a flag: the water is
 * placed through Backport Core's waterlogging once the chunk is in the world. A state nothing resolves becomes air (a
 * plant, a fluid plant) or water (a waterlogged one), logged once.
 *
 * <p>Codes are registered by the core's feature classes as they are initialized; {@link #state} resolves new ones as
 * they appear, so the table grows with the core. Not thread safe: one instance per generator, used on one thread.
 */
public final class StateMap {

    /** Flag: the block holds a water source (1.18's waterlogged=true, or a water plant). */
    public static final int WATER = 1;
    /** Flag: a chest (the monster room's), which gets the simple dungeon loot table. */
    public static final int CHEST = 2;
    /** Flag: a mob spawner (the monster room's), which gets a dungeon mob. */
    public static final int SPAWNER = 4;
    /** Flag: the 1.12 block has a tile entity other than the above; the chunk makes it when first asked. */
    public static final int TILE = 8;
    /** Flag: a bee nest (1.18's beehive decorator), which gets its bees ({@link Nests}). */
    public static final int NEST = 16;

    private IBlockState[] states = new IBlockState[0];
    /** The states that resolved to nothing (air or water instead), for the self-test. */
    public final List<String> unresolved = new ArrayList<>();
    private byte[] flags = new byte[0];

    private static final Map<String, String> VANILLA = new HashMap<>();
    /** Used only when no installed mod has the 1.18 block: the nearest 1.12 one. */
    private static final Map<String, String> FALLBACK = new HashMap<>();
    /** 1.18 slabs: the 1.12 single slab and double slab, with the variant. */
    private static final Map<String, String[]> SLABS = new HashMap<>();
    private static final List<String> LOGGED = new ArrayList<>();

    private static void v(String modern, String legacy) {
        VANILLA.put(modern, legacy);
    }

    static {
        v("cave_air", "minecraft:air");
        v("void_air", "minecraft:air");
        v("granite", "minecraft:stone[variant=granite]");
        v("diorite", "minecraft:stone[variant=diorite]");
        v("andesite", "minecraft:stone[variant=andesite]");
        v("polished_granite", "minecraft:stone[variant=smooth_granite]");
        v("polished_diorite", "minecraft:stone[variant=smooth_diorite]");
        v("polished_andesite", "minecraft:stone[variant=smooth_andesite]");
        v("grass_block", "minecraft:grass");
        v("podzol", "minecraft:dirt[variant=podzol]");
        v("coarse_dirt", "minecraft:dirt[variant=coarse_dirt]");
        v("red_sand", "minecraft:sand[variant=red_sand]");
        v("snow_block", "minecraft:snow");
        v("snow", "minecraft:snow_layer");
        v("terracotta", "minecraft:hardened_clay");
        v("magma_block", "minecraft:magma");
        v("infested_stone", "minecraft:monster_egg[variant=stone]");
        v("infested_cobblestone", "minecraft:monster_egg[variant=cobblestone]");
        v("infested_stone_bricks", "minecraft:monster_egg[variant=stone_brick]");
        v("spawner", "minecraft:mob_spawner");
        v("grass", "minecraft:tallgrass[type=tall_grass]");
        v("fern", "minecraft:tallgrass[type=fern]");
        v("dead_bush", "minecraft:deadbush");
        v("tall_grass", "minecraft:double_plant[variant=double_grass]");
        v("large_fern", "minecraft:double_plant[variant=double_fern]");
        v("sunflower", "minecraft:double_plant[variant=sunflower]");
        v("lilac", "minecraft:double_plant[variant=syringa]");
        v("rose_bush", "minecraft:double_plant[variant=double_rose]");
        v("peony", "minecraft:double_plant[variant=paeonia]");
        v("dandelion", "minecraft:yellow_flower");
        v("poppy", "minecraft:red_flower[type=poppy]");
        v("blue_orchid", "minecraft:red_flower[type=blue_orchid]");
        v("allium", "minecraft:red_flower[type=allium]");
        v("azure_bluet", "minecraft:red_flower[type=houstonia]");
        v("red_tulip", "minecraft:red_flower[type=red_tulip]");
        v("orange_tulip", "minecraft:red_flower[type=orange_tulip]");
        v("white_tulip", "minecraft:red_flower[type=white_tulip]");
        v("pink_tulip", "minecraft:red_flower[type=pink_tulip]");
        v("oxeye_daisy", "minecraft:red_flower[type=oxeye_daisy]");
        v("sugar_cane", "minecraft:reeds");
        v("lily_pad", "minecraft:waterlily");
        v("melon", "minecraft:melon_block");
        v("pumpkin", "minecraft:pumpkin");
        v("cobweb", "minecraft:web");
        v("dirt_path", "minecraft:grass_path");
        v("stone_bricks", "minecraft:stonebrick[variant=stonebrick]");
        v("mossy_stone_bricks", "minecraft:stonebrick[variant=mossy_stonebrick]");
        v("cracked_stone_bricks", "minecraft:stonebrick[variant=cracked_stonebrick]");
        v("chiseled_stone_bricks", "minecraft:stonebrick[variant=chiseled_stonebrick]");
        v("bone_block", "minecraft:bone_block");
        v("oak_planks", "minecraft:planks[variant=oak]");
        v("spruce_planks", "minecraft:planks[variant=spruce]");
        v("birch_planks", "minecraft:planks[variant=birch]");
        v("jungle_planks", "minecraft:planks[variant=jungle]");
        v("acacia_planks", "minecraft:planks[variant=acacia]");
        v("dark_oak_planks", "minecraft:planks[variant=dark_oak]");
        v("oak_log", "minecraft:log[variant=oak]");
        v("spruce_log", "minecraft:log[variant=spruce]");
        v("birch_log", "minecraft:log[variant=birch]");
        v("jungle_log", "minecraft:log[variant=jungle]");
        v("acacia_log", "minecraft:log2[variant=acacia]");
        v("dark_oak_log", "minecraft:log2[variant=dark_oak]");
        v("oak_wood", "minecraft:log[variant=oak,axis=none]");
        v("spruce_wood", "minecraft:log[variant=spruce,axis=none]");
        v("birch_wood", "minecraft:log[variant=birch,axis=none]");
        v("jungle_wood", "minecraft:log[variant=jungle,axis=none]");
        v("acacia_wood", "minecraft:log2[variant=acacia,axis=none]");
        v("dark_oak_wood", "minecraft:log2[variant=dark_oak,axis=none]");
        v("oak_leaves", "minecraft:leaves[variant=oak,check_decay=false,decayable=true]");
        v("spruce_leaves", "minecraft:leaves[variant=spruce,check_decay=false,decayable=true]");
        v("birch_leaves", "minecraft:leaves[variant=birch,check_decay=false,decayable=true]");
        v("jungle_leaves", "minecraft:leaves[variant=jungle,check_decay=false,decayable=true]");
        v("acacia_leaves", "minecraft:leaves2[variant=acacia,check_decay=false,decayable=true]");
        v("dark_oak_leaves", "minecraft:leaves2[variant=dark_oak,check_decay=false,decayable=true]");
        v("oak_sapling", "minecraft:sapling[type=oak]");
        v("spruce_sapling", "minecraft:sapling[type=spruce]");
        v("birch_sapling", "minecraft:sapling[type=birch]");
        v("jungle_sapling", "minecraft:sapling[type=jungle]");
        v("acacia_sapling", "minecraft:sapling[type=acacia]");
        v("dark_oak_sapling", "minecraft:sapling[type=dark_oak]");
        v("oak_fence", "minecraft:fence");
        v("cut_sandstone", "minecraft:sandstone[type=smooth_sandstone]");
        v("chiseled_sandstone", "minecraft:sandstone[type=chiseled_sandstone]");
        v("cut_red_sandstone", "minecraft:red_sandstone[type=smooth_red_sandstone]");
        v("chiseled_red_sandstone", "minecraft:red_sandstone[type=chiseled_red_sandstone]");
        v("nether_bricks", "minecraft:nether_brick");
        v("water", "minecraft:water");
        v("lava", "minecraft:lava");
        v("bubble_column", "minecraft:water");
        FALLBACK.put("blue_ice", "minecraft:packed_ice");
        FALLBACK.put("tuff", "minecraft:stone[variant=andesite]");
        FALLBACK.put("calcite", "minecraft:stone[variant=diorite]");
        // Plants from family backports (Village & Pillage, Buzzy Bees, Update Aquatic), when none is installed: the
        // nearest 1.12 flower, nothing for a plant 1.12 cannot hold up (bamboo, a nest under leaves), the water a
        // water plant stands in, and a coral reef's shape in terracotta of the coral's color.
        FALLBACK.put("cornflower", "minecraft:red_flower[type=blue_orchid]");
        FALLBACK.put("lily_of_the_valley", "minecraft:red_flower[type=oxeye_daisy]");
        FALLBACK.put("sweet_berry_bush", "minecraft:tallgrass[type=fern]");
        FALLBACK.put("bamboo", "minecraft:air");
        FALLBACK.put("bee_nest", "minecraft:air");
        // The newer biomes' blocks (Wild Update's mangrove swamp, Trails & Tales' cherry grove). A world gets those
        // biomes only when the backport with the blocks is installed, so these show only if it is removed later.
        FALLBACK.put("mud", "minecraft:dirt");
        FALLBACK.put("muddy_mangrove_roots", "minecraft:dirt");
        FALLBACK.put("mangrove_roots", "minecraft:air");
        FALLBACK.put("mangrove_propagule", "minecraft:air");
        FALLBACK.put("mangrove_log", "minecraft:log[variant=jungle]");
        FALLBACK.put("mangrove_leaves", "minecraft:leaves[variant=jungle,check_decay=false,decayable=true]");
        FALLBACK.put("cherry_log", "minecraft:log[variant=birch]");
        FALLBACK.put("cherry_leaves", "minecraft:leaves[variant=birch,check_decay=false,decayable=true]");
        FALLBACK.put("pink_petals", "minecraft:air");
        for (String plant : new String[]{"kelp", "kelp_plant", "seagrass", "tall_seagrass", "sea_pickle"}) {
            FALLBACK.put(plant, "minecraft:water");
        }
        String[][] corals = {{"tube", "blue"}, {"brain", "pink"}, {"bubble", "magenta"}, {"fire", "red"},
            {"horn", "yellow"}};
        for (String[] c : corals) {
            FALLBACK.put(c[0] + "_coral_block", "minecraft:stained_hardened_clay[color=" + c[1] + "]");
            FALLBACK.put(c[0] + "_coral", "minecraft:water");
            FALLBACK.put(c[0] + "_coral_fan", "minecraft:water");
            FALLBACK.put(c[0] + "_coral_wall_fan", "minecraft:water");
        }
        SLABS.put("sandstone_slab", new String[]{"minecraft:stone_slab[variant=sand]", "minecraft:double_stone_slab[variant=sand]"});
        SLABS.put("cobblestone_slab", new String[]{"minecraft:stone_slab[variant=cobblestone]", "minecraft:double_stone_slab[variant=cobblestone]"});
        SLABS.put("smooth_stone_slab", new String[]{"minecraft:stone_slab[variant=stone]", "minecraft:double_stone_slab[variant=stone]"});
        SLABS.put("stone_brick_slab", new String[]{"minecraft:stone_slab[variant=smooth_brick]", "minecraft:double_stone_slab[variant=smooth_brick]"});
        SLABS.put("brick_slab", new String[]{"minecraft:stone_slab[variant=brick]", "minecraft:double_stone_slab[variant=brick]"});
        SLABS.put("red_sandstone_slab", new String[]{"minecraft:stone_slab2[variant=red_sandstone]", "minecraft:double_stone_slab2[variant=red_sandstone]"});
        for (String wood : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"}) {
            SLABS.put(wood + "_slab", new String[]{"minecraft:wooden_slab[variant=" + wood + "]", "minecraft:double_wooden_slab[variant=" + wood + "]"});
        }
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
            "cyan", "purple", "blue", "brown", "green", "red", "black"};
        for (String c : colors) {
            String legacy = c.equals("light_gray") ? "silver" : c;
            v(c + "_terracotta", "minecraft:stained_hardened_clay[color=" + legacy + "]");
            v(c + "_wool", "minecraft:wool[color=" + legacy + "]");
            v(c + "_carpet", "minecraft:carpet[color=" + legacy + "]");
            v(c + "_stained_glass", "minecraft:stained_glass[color=" + legacy + "]");
            v(c + "_concrete", "minecraft:concrete[color=" + legacy + "]");
        }
    }

    /** The 1.12 state of a code. */
    public IBlockState state(int code) {
        if (code >= states.length || states[code] == null) {
            resolve(code);
        }
        return states[code];
    }

    /** The code's flags ({@link #WATER}, {@link #CHEST}, ...). */
    public int flags(int code) {
        if (code >= states.length || states[code] == null) {
            resolve(code);
        }
        return flags[code];
    }

    /** Resolves every code the core has registered so far (the generator does this once, up front). */
    public void resolveAll() {
        for (int c = 0; c < BlockCodes.count(); c++) {
            state(c);
        }
    }

    private void resolve(int code) {
        if (code >= states.length) {
            int n = Math.max(code + 1, Math.max(64, BlockCodes.count()));
            states = java.util.Arrays.copyOf(states, n);
            flags = java.util.Arrays.copyOf(flags, n);
        }
        String full = BlockCodes.state(code);
        int bi = full.indexOf('[');
        String name = bi < 0 ? full : full.substring(0, bi);
        Map<String, String> props = props(bi < 0 ? "" : full.substring(bi + 1, full.length() - 1));
        int f = 0;
        if ("true".equals(props.remove("waterlogged")) || (BlockCodes.flags(code) & BlockCodes.F_WATER) != 0
                && !name.equals("water") && !name.equals("bubble_column")) {
            f |= WATER;
        }
        IBlockState state = special(name, props);
        if (state == null) {
            String legacy = VANILLA.get(name);
            if (legacy != null) {
                int lb = legacy.indexOf('[');
                Block block = block(new ResourceLocation(lb < 0 ? legacy : legacy.substring(0, lb)));
                state = block == null ? null : block.getDefaultState();
                if (state != null && lb >= 0) {
                    state = apply(state, props(legacy.substring(lb + 1, legacy.length() - 1)));
                }
            } else {
                Block block = byModernName(name);
                state = block == null ? null : block.getDefaultState();
            }
            if (state != null) {
                state = apply(state, props);
            } else if (FALLBACK.containsKey(name)) {
                state = parse(FALLBACK.get(name));
            }
        }
        if (state == null) {
            unresolved.add(full);
            synchronized (LOGGED) {
                if (!LOGGED.contains(name)) {
                    LOGGED.add(name);
                    CavesAndCliffsBackport.LOGGER.warn("1.18 terrain: no 1.12 block for {}; it generates as {}.", full,
                            (f & WATER) != 0 ? "water" : "air");
                }
            }
            state = (f & WATER) != 0 ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
            f &= ~WATER;
        }
        Block b = state.getBlock();
        if (b == Blocks.WATER || b == Blocks.FLOWING_WATER) {
            f &= ~WATER;
        }
        if (b == Blocks.CHEST) {
            f |= CHEST;
        } else if (b == Blocks.MOB_SPAWNER) {
            f |= SPAWNER;
        } else if (name.equals("bee_nest") && b.hasTileEntity(state)) {
            f |= NEST;
        } else if (b.hasTileEntity(state)) {
            f |= TILE;
        }
        states[code] = state;
        flags[code] = (byte) f;
    }

    /** Blocks whose 1.18 properties do not carry over by name. */
    private static IBlockState special(String name, Map<String, String> props) {
        switch (name) {
            case "glow_lichen": {
                if (BlockGlowLichen.of(1).getBlock() == Blocks.AIR) {
                    return null;
                }
                // Face bits by EnumFacing index: down 0, up 1, north 2, south 3, west 4, east 5.
                String[] faces = {"down", "up", "north", "south", "west", "east"};
                int mask = 0;
                for (int i = 0; i < 6; i++) {
                    if ("true".equals(props.get(faces[i]))) {
                        mask |= 1 << i;
                    }
                }
                props.clear();
                return BlockGlowLichen.of(mask);
            }
            case "kelp": {
                // 1.18's kelp top has ages 0-25 (generated 20-23) and grows until 25. A backport's kelp with fewer
                // ages (Update Aquatic's 0-15) gets the age that leaves it as many growth steps to its top.
                Block block = byModernName(name);
                if (block == null) {
                    return null;
                }
                IBlockState s = block.getDefaultState();
                String age = props.remove("age");
                for (IProperty<?> p : s.getPropertyKeys()) {
                    if (p.getName().equals("age") && age != null) {
                        int max = 0;
                        for (Object v : p.getAllowedValues()) {
                            max = Math.max(max, ((Number) v).intValue());
                        }
                        int mapped = Math.max(0, Math.min(max, max - (25 - Integer.parseInt(age))));
                        s = apply(s, single("age", String.valueOf(mapped)));
                    }
                }
                return apply(s, props);
            }
            case "small_dripleaf": {
                String half = props.remove("half");
                Block block = byModernName(name);
                if (block == null) {
                    return null;
                }
                IBlockState s = apply(block.getDefaultState(), props);
                props.clear();
                return apply(s, single("upper", String.valueOf("upper".equals(half))));
            }
            case "brown_mushroom_block":
            case "red_mushroom_block":
            case "mushroom_stem": {
                boolean stem = name.equals("mushroom_stem");
                String variant = mushroomVariant(stem, props);
                props.clear();
                Block block = block(new ResourceLocation(stem ? "minecraft:brown_mushroom_block" : "minecraft:" + name));
                return block == null ? null : apply(block.getDefaultState(), single("variant", variant));
            }
            case "water":
            case "lava": {
                int level = props.containsKey("level") ? Integer.parseInt(props.remove("level")) : 0;
                Block still = name.equals("water") ? Blocks.WATER : Blocks.LAVA;
                Block flowing = name.equals("water") ? Blocks.FLOWING_WATER : Blocks.LAVA;
                return level == 0 ? still.getDefaultState()
                        : apply(flowing.getDefaultState(), single("level", String.valueOf(level)));
            }
            default:
                String[] slab = SLABS.get(name);
                if (slab != null) {
                    String type = props.remove("type");
                    if ("double".equals(type)) {
                        props.clear();
                        return parse(slab[1]);
                    }
                    IBlockState s = parse(slab[0]);
                    props.clear();
                    return s == null ? null : apply(s, single("half", "top".equals(type) ? "top" : "bottom"));
                }
                if (name.endsWith("_leaves")) {
                    // 1.18's distance/persistent: generated leaves decay normally in 1.12.
                    props.remove("distance");
                    props.remove("persistent");
                }
                return null;
        }
    }

    /**
     * 1.12's huge mushroom variant for 1.18's face mask (a face is true where it shows the cap, or the stem's bark).
     * 1.12 names a cap block by the compass corner or edge it sits on (the top shows cap on each); a mask 1.12 has no
     * name for (both of two opposite sides, three sides) is all_outside, and red's side blocks without a top (up=false)
     * take their compass variant.
     */
    private static String mushroomVariant(boolean stem, Map<String, String> props) {
        boolean up = "true".equals(props.get("up"));
        boolean down = "true".equals(props.get("down"));
        boolean n = "true".equals(props.get("north"));
        boolean s = "true".equals(props.get("south"));
        boolean w = "true".equals(props.get("west"));
        boolean e = "true".equals(props.get("east"));
        if (stem) {
            return up && down ? "all_stem" : "stem";
        }
        int sides = (n ? 1 : 0) + (s ? 1 : 0) + (w ? 1 : 0) + (e ? 1 : 0);
        if (sides == 0) {
            return up || down ? "center" : "all_inside";
        }
        if (sides > 2 || n && s || w && e) {
            return "all_outside";
        }
        String ns = n ? "north" : s ? "south" : "";
        String we = w ? "west" : e ? "east" : "";
        return ns.isEmpty() ? we : we.isEmpty() ? ns : ns + "_" + we;
    }

    /** A 1.12 state written as "namespace:block[prop=value,...]", or null if the block is not there. */
    private static IBlockState parse(String legacy) {
        int lb = legacy.indexOf('[');
        Block block = block(new ResourceLocation(lb < 0 ? legacy : legacy.substring(0, lb)));
        if (block == null) {
            return null;
        }
        IBlockState state = block.getDefaultState();
        return lb < 0 ? state : apply(state, props(legacy.substring(lb + 1, legacy.length() - 1)));
    }

    private static Map<String, String> single(String k, String v) {
        Map<String, String> m = new HashMap<>();
        m.put(k, v);
        return m;
    }

    /**
     * A block registered under this 1.18 name: this mod's, then vanilla's, then any other mod's (Village &amp; Pillage's
     * bamboo, sweet berry bush, cornflower and lily of the valley; Buzzy Bees' bee nest; Update Aquatic's kelp and
     * coral). Of two mods' copies, the one Backport Core's duplicate hiding keeps (Village &amp; Pillage's or Future
     * MC's sweet berry bush, as its settings say), else the first by id.
     */
    private static Block byModernName(String name) {
        Block b = block(new ResourceLocation(CavesAndCliffsBackport.MOD_ID, name));
        if (b != null) {
            return b;
        }
        b = block(new ResourceLocation("minecraft", name));
        if (b != null) {
            return b;
        }
        Block best = null;
        String bestId = null;
        boolean bestHidden = true;
        for (Map.Entry<ResourceLocation, Block> e : ForgeRegistries.BLOCKS.getEntries()) {
            if (e.getKey().getPath().equals(name)) {
                String id = e.getKey().toString();
                boolean hidden = com.exiledradio.backportcore.duplicate.DuplicateBlocks.isHidden(e.getValue());
                if (bestId == null || bestHidden && !hidden || bestHidden == hidden && id.compareTo(bestId) < 0) {
                    best = e.getValue();
                    bestId = id;
                    bestHidden = hidden;
                }
            }
        }
        return best;
    }

    private static Block block(ResourceLocation id) {
        if (!ForgeRegistries.BLOCKS.containsKey(id)) {
            return null;
        }
        Block b = ForgeRegistries.BLOCKS.getValue(id);
        return b == Blocks.AIR && !id.getPath().equals("air") ? null : b;
    }

    private static Map<String, String> props(String s) {
        Map<String, String> m = new LinkedHashMap<>();
        if (s.isEmpty()) {
            return m;
        }
        for (String kv : s.split(",")) {
            int eq = kv.indexOf('=');
            m.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        return m;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState apply(IBlockState state, Map<String, String> props) {
        for (Map.Entry<String, String> e : props.entrySet()) {
            for (IProperty<?> p : state.getPropertyKeys()) {
                if (p.getName().equals(e.getKey())) {
                    Optional<?> v = p.parseValue(e.getValue());
                    if (v.isPresent()) {
                        state = state.withProperty((IProperty) p, (Comparable) v.get());
                    }
                    break;
                }
            }
        }
        return state;
    }
}
