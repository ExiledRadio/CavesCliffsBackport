package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;

/**
 * 1.18.2's decoration (ChunkGenerator.applyBiomeDecoration) for one chunk on its 3x3 Region, after every chunk of
 * the region has been filled, surfaced and carved (and decorated already where its turn came first: features
 * write into neighbors, so the order chunks are decorated in matters, in vanilla as here).
 *
 * <p>The loop is vanilla's: a WorldgenRandom over Xoroshiro (XoroRandom), setDecorationSeed(world seed, chunk
 * min x, min z); per step, the union over every biome stored in the 3x3 chunks of that biome's features for the
 * step, as indices into the step's global order (FeatureTables.STEPS, BiomeSource.featuresPerStep), sorted; each
 * placed with setFeatureSeed(decoration seed, index, step) through its placement modifiers, in the order vanilla's
 * lazy streams run them (each position goes all the way through the remaining modifiers and the feature before
 * the next one is drawn). Structure pieces, which vanilla places first in each step, are not here yet.
 *
 * <p>Features not ported yet are skipped. A subset decorates exactly as vanilla does with the rest disabled (each
 * feature's random is its own), which is how the checks test each stream; {@link #only} restricts to one.
 *
 * One instance per thread.
 */
public final class Decorator {
    private static final int MIN_Y = OverworldNoise.MIN_Y;
    private static final int HEIGHT = OverworldNoise.HEIGHT;
    private static final int STEPS = FeatureTables.STEPS.length;

    private final long seed;
    private final long zoomSeed;
    private final BiomeSource biomes;
    private final XoroRandom random = new XoroRandom(0L);
    /** Per step, per index: the ported feature, or null. */
    private final Placed[][] features = new Placed[STEPS][];
    /** Per biome, per step: the indices of its features. */
    private final int[][][] biomeSteps;
    /** Per biome: every feature it has, any step (BiomeGenerationSettings.hasFeature), by Placed.id. */
    private final boolean[][] biomeHas;
    private Set<String> only;
    private final PaleGarden pale;
    private TreeGate treeGate;
    /** While a TREES or CACTUS feature runs under a gate: its kind, asked per column ({@link TreeGate#allowsAt}). */
    private int positionKind;
    private final int[] gateAnswers = new int[TreeGate.KINDS];
    private final byte[] stored = new byte[1536];
    private final boolean[] present = new boolean[256];
    private final boolean[] chosen = new boolean[512];

    /** Every placed feature the overworld's biomes list (ported or not) with these editions, by name, any step. */
    public static java.util.Set<String> placedFeatureNames(int editions) {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String[] step : steps(editions)) {
            names.addAll(Arrays.asList(step));
        }
        return names;
    }

    /**
     * 26.3's dappled forest's features per step (Editions.GAME_DROPS), less its vegetal step's trees, mushrooms, shrubs
     * and grass: Game Drops' PoplarFeatures.vegetation places those at population (ModernGenerator). Names the
     * editions' step order does not hold are left out, as for any biome.
     */
    private static final String[][] DAPPLED_FOREST = {
        {},
        {"lake_lava_underground", "lake_lava_surface"},
        {"amethyst_geode"},
        {"monster_room", "monster_room_deep"},
        {},
        {},
        {"ore_dirt", "ore_gravel", "ore_granite_upper", "ore_granite_lower", "ore_diorite_upper", "ore_diorite_lower", "ore_andesite_upper", "ore_andesite_lower", "ore_tuff", "ore_coal_upper", "ore_coal_lower", "ore_iron_upper", "ore_iron_middle", "ore_iron_small", "ore_gold", "ore_gold_lower", "ore_redstone", "ore_redstone_lower", "ore_diamond", "ore_diamond_medium", "ore_diamond_large", "ore_diamond_buried", "ore_lapis", "ore_lapis_buried", "ore_copper", "underwater_magma", "disk_sand", "disk_clay", "disk_gravel"},
        {},
        {"spring_water", "spring_lava"},
        {"glow_lichen"},
        {"freeze_top_layer"}};

    /**
     * 26.2's sulfur caves' features per step (Editions.GAME_DROPS). Its springs, pools and spikes are Game Drops'
     * own, placed at population; here they are names no step order holds, so they are left out.
     */
    private static final String[][] SULFUR_CAVES = {
        {},
        {"lake_lava_underground", "lake_lava_surface", "rooted_sulfur_spring", "sulfur_pool"},
        {"amethyst_geode"},
        {"monster_room", "monster_room_deep"},
        {},
        {},
        {"ore_dirt", "ore_gravel", "ore_granite_upper", "ore_granite_lower", "ore_diorite_upper", "ore_diorite_lower", "ore_andesite_upper", "ore_andesite_lower", "ore_tuff", "ore_coal_upper", "ore_coal_lower", "ore_iron_upper", "ore_iron_middle", "ore_iron_small", "ore_gold", "ore_gold_lower", "ore_redstone", "ore_redstone_lower", "ore_diamond", "ore_diamond_medium", "ore_diamond_large", "ore_diamond_buried", "ore_lapis", "ore_lapis_buried", "ore_copper", "underwater_magma", "disk_sand", "disk_clay", "disk_gravel"},
        {"sulfur_spike_cluster", "sulfur_spike"},
        {"spring_water", "spring_lava"},
        {"glow_lichen", "patch_tall_grass_2"},
        {"freeze_top_layer"}};

    /** The bit of a biome in a chunk's biome mask: 64 on the void's, 65 and 66 on two nether biomes' (never stored). */
    private static int bit(int biome) {
        return biome < 64 ? biome : biome == 64 ? 0 : Biomes.NETHER_WASTES + biome - 65;
    }

    /** FeatureSorter's per-step order for the editions' biomes. */
    static String[][] steps(int editions) {
        // Game Drops' two biomes bring no step order of their own (see biomeFeatures).
        editions &= ~Editions.GAME_DROPS;
        if ((editions & Editions.SPRING) != 0) {
            switch (editions & ~Editions.SPRING) {
                case Editions.PALE | Editions.WILD | Editions.CHERRY:
                    return FeatureTables12111.STEPS_ALL;
                case Editions.PALE | Editions.WILD:
                    return FeatureTables12111.STEPS_WILD;
                case Editions.PALE | Editions.CHERRY:
                    return FeatureTables12111.STEPS_CHERRY;
                default:
                    return FeatureTables12111.STEPS_PALE;
            }
        }
        switch (editions) {
            case Editions.PALE | Editions.WILD | Editions.CHERRY:
                return FeatureTables1214.STEPS_ALL;
            case Editions.PALE | Editions.WILD:
                return FeatureTables1214.STEPS_WILD;
            case Editions.PALE | Editions.CHERRY:
                return FeatureTables1214.STEPS_CHERRY;
            case Editions.PALE:
                return FeatureTables1214.STEPS_PALE;
            case Editions.WILD:
                return NewerFeatureTables.STEPS_WILD;
            case Editions.CHERRY:
                return NewerFeatureTables.STEPS_CHERRY;
            case Editions.WILD | Editions.CHERRY:
                return NewerFeatureTables.STEPS_BOTH;
            default:
                return FeatureTables.STEPS;
        }
    }

    /**
     * A biome's placed features per step (the deep dark's are 1.19.2's or, with the cherry grove, 1.20.1's; with the
     * pale garden every biome's are 1.21.4's).
     */
    static String[][] biomeFeatures(int biome, int editions) {
        if (biome == Biomes.SULFUR_CAVES) {
            return SULFUR_CAVES;
        }
        if (biome == Biomes.DAPPLED_FOREST) {
            return DAPPLED_FOREST;
        }
        if ((editions & Editions.SPRING) != 0) {
            return FeatureTables12111.BIOMES[biome];
        }
        if ((editions & Editions.PALE) != 0) {
            // 1.21.4's lists for every biome.
            return FeatureTables1214.BIOMES[biome];
        }
        switch (biome) {
            case Biomes.MANGROVE_SWAMP:
                return NewerFeatureTables.MANGROVE_SWAMP;
            case Biomes.DEEP_DARK:
                return (editions & Editions.CHERRY) != 0 ? NewerFeatureTables.DEEP_DARK : NewerFeatureTables.DEEP_DARK_119;
            case Biomes.CHERRY_GROVE:
                return NewerFeatureTables.CHERRY_GROVE;
            default:
                return biome < FeatureTables.BIOMES.length ? FeatureTables.BIOMES[biome] : new String[0][];
        }
    }

    public Decorator(OverworldNoise noise, BiomeSource biomes) {
        this.seed = noise.seed;
        this.zoomSeed = SurfaceBuilder.obfuscateSeed(noise.seed);
        this.biomes = biomes;
        Map<String, Placed> ported = new HashMap<>();
        for (Placed p : Ores.placed()) {
            if (p.name.startsWith("ore_granite_")) {
                p.kind = TreeGate.GRANITE;
            } else if (p.name.startsWith("ore_diorite_")) {
                p.kind = TreeGate.DIORITE;
            } else if (p.name.startsWith("ore_andesite_")) {
                p.kind = TreeGate.ANDESITE;
            }
            ported.put(p.name, p);
        }
        for (Placed p : Geodes.placed(noise.seed)) {
            ported.put(p.name, p);
        }
        for (Placed p : SurfaceFeatures.placed(this)) {
            if ("forest_rock".equals(p.name)) {
                p.kind = TreeGate.PLANTS;
            }
            ported.put(p.name, p);
        }
        for (Placed p : LushCaves.placed()) {
            ported.put(p.name, p);
        }
        for (Placed p : Dripstone.placed()) {
            ported.put(p.name, p);
        }
        for (Placed p : Caves.placed()) {
            ported.put(p.name, p);
        }
        for (Placed p : Trees.placed()) {
            // The azalea tree grows from a lush cave's ceiling, not a forest: no 1.12 mod replaces it.
            p.kind = "rooted_azalea_tree".equals(p.name) ? 0 : TreeGate.TREES;
            ported.put(p.name, p);
        }
        for (Placed p : Fossils.placed()) {
            ported.put(p.name, p);
        }
        for (Placed p : Vegetation.placed()) {
            if (p.name.startsWith("patch_cactus")) {
                p.kind = TreeGate.CACTUS;
            } else if (!p.name.startsWith("seagrass") && !p.name.startsWith("kelp") && !"sea_pickle".equals(p.name)
                    && !"warm_ocean_vegetation".equals(p.name)) {
                p.kind = TreeGate.PLANTS;
            }
            ported.put(p.name, p);
        }
        for (Placed p : Misc.placed()) {
            ported.put(p.name, p);
        }
        // 1.21.4's pale garden (Editions.PALE): its features only appear in that edition's tables.
        pale = new PaleGarden(noise);
        for (Placed p : pale.placed()) {
            ported.put(p.name, p);
        }
        String[][] order = steps(noise.editions);
        Map<String, Integer> ids = new HashMap<>();
        for (int k = 0; k < STEPS; k++) {
            String[] names = order[k];
            features[k] = new Placed[names.length];
            for (int i = 0; i < names.length; i++) {
                if (!ids.containsKey(names[i])) {
                    ids.put(names[i], ids.size());
                }
                Placed p = ported.get(names[i]);
                if (p != null) {
                    p.id = ids.get(names[i]);
                    features[k][i] = p;
                }
            }
        }
        int n = Biomes.COUNT;
        biomeSteps = new int[n][][];
        biomeHas = new boolean[n][ids.size()];
        for (int b = 0; b < n; b++) {
            String[][] steps = biomeFeatures(b, noise.editions);
            biomeSteps[b] = new int[steps.length][];
            for (int k = 0; k < steps.length; k++) {
                // Biomes outside the overworld's biome source (nether, end) list features no step order holds.
                int[] idx = new int[steps[k].length];
                int m = 0;
                for (int i = 0; i < idx.length; i++) {
                    int at = k < STEPS ? Arrays.asList(order[k]).indexOf(steps[k][i]) : -1;
                    if (at >= 0) {
                        idx[m++] = at;
                        biomeHas[b][ids.get(steps[k][i])] = true;
                    }
                }
                biomeSteps[b][k] = Arrays.copyOf(idx, m);
            }
        }
    }

    /** The ported placed features' names, in step and index order (for the oracle's -Dfeatures). */
    public java.util.List<String> ported() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Placed[] step : features) {
            for (Placed p : step) {
                if (p != null) {
                    names.add(p.name);
                }
            }
        }
        return names;
    }

    /** Runs only the named placed features (null: every ported one), as VanillaFeatureDump's -Dfeatures does. */
    public void only(Set<String> names) {
        only = names;
    }

    private int rules = Rules.V118;

    /**
     * The newest installed version whose changes to 1.18.2's own features apply ({@link Rules}): with Wild Update
     * Backport 1.19.2's, Trails &amp; Tales 1.20.1's, Tricky Trials 1.21.11's. 1.18.2's by default.
     */
    public void rules(int version) {
        rules = version;
    }

    /**
     * Asked at most once per decorated chunk and kind, when the chunk has a feature of that kind to place: false
     * leaves out that chunk's features of the kind ({@link #TREES}: every surface tree feature, huge mushrooms
     * included, not the azalea tree; {@link #CACTUS}: the cactus patches). Every feature keeps its own seed, so the
     * rest of the chunk is exactly as without a gate. The game answers it for a mod that grows its own trees
     * instead (Dynamic Trees), or its own granite, diorite and andesite ({@link #GRANITE}, {@link #DIORITE},
     * {@link #ANDESITE}: the two ore features of each, for Quark's stone revamp), or where another mod's biome decorates
     * the chunk ({@link #PLANTS}: the surface plants, flowers, grass, berries, bamboo, vines, lily pads, mossy
     * boulders; not the sea's). Null: everything, as vanilla.
     */
    public interface TreeGate {
        int TREES = 1;
        int CACTUS = 2;
        int GRANITE = 3;
        int DIORITE = 4;
        int ANDESITE = 5;
        int PLANTS = 6;
        int KINDS = 7;

        boolean allows(int cx, int cz, int kind);

        /**
         * Asked for each {@link #TREES} or {@link #CACTUS} feature where it is placed, once {@link #allows} let the
         * chunk have them, with the 1.18 biome its biome filter read there: false leaves that one out (Dynamic Trees,
         * by that biome, as its own trees pick theirs). The feature's later draws in the chunk are then not the same
         * as without it.
         */
        default boolean allowsAt(int x, int z, int biome, int kind) {
            return true;
        }
    }

    public void treeGate(TreeGate gate) {
        treeGate = gate;
    }

    /**
     * A newer version's tree selector for one of 1.18.2's tree features, as another backport gives it (1.21.5's
     * fallen trees and leaf litter, from Tricky Trials): its entries in order, each a chance and either one of
     * 1.18.2's entries ({@link #TERRAIN}: in their order, with their chances) or a fallen tree (its index, 0 to 7).
     */
    public static final class ModernTrees {
        public static final int TERRAIN = -1;
        final float[] chances;
        final int[] fallen;
        final boolean litter;

        /** @param litter the selector's trees (not its fallen trees or huge mushrooms) drop leaf litter */
        public ModernTrees(float[] chances, int[] fallen, boolean litter) {
            this.chances = chances.clone();
            this.fallen = fallen.clone();
            this.litter = litter;
        }
    }

    /**
     * Gives 1.18.2's tree features a newer version's selectors (keyed by the 1.18.2 placed feature's name): a fallen
     * tree picked leaves a {@link Marks#FALLEN} request at its position, a tree that drops leaf litter a
     * {@link Marks#LITTER} request at its foot once it stands. The placements (count, square, filters) stay 1.18.2's.
     * Never called for a world without that backport, so its decoration stays exactly 1.18.2's.
     *
     * @return the names that did not match 1.18.2's entries (left as they were)
     */
    public java.util.List<String> modernTrees(Map<String, ModernTrees> selectors) {
        java.util.List<String> skipped = new java.util.ArrayList<>();
        for (Map.Entry<String, ModernTrees> e : selectors.entrySet()) {
            boolean found = false;
            for (Placed[] step : features) {
                for (int i = 0; i < step.length; i++) {
                    Placed p = step[i];
                    if (p == null || !p.name.equals(e.getKey())) {
                        continue;
                    }
                    found = true;
                    Feature f = Trees.modern(p.feature, e.getValue());
                    if (f == null) {
                        skipped.add(p.name);
                        continue;
                    }
                    Placed q = new Placed(p.name, f, p.ops).mods(p.mods);
                    q.id = p.id;
                    q.kind = p.kind;
                    step[i] = q;
                }
            }
            if (!found) {
                skipped.add(e.getKey());
            }
        }
        return skipped;
    }

    // ---- the loop ----

    private Region region;

    /** Decorates the region's center chunk. */
    public void decorate(Region region) {
        this.region = region;
        region.rules = rules;
        int cx = region.centerX;
        int cz = region.centerZ;
        int ox = cx << 4;
        int oz = cz << 4;
        random.newRandom();
        long decorationSeed = random.setDecorationSeed(seed, ox, oz);
        pale.begin(cx, cz);
        long mask = 0L;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                mask |= storedBiomes(cx + dx, cz + dz);
            }
        }
        // bit(b): the pale garden (64) shares the void's bit, the sulfur caves and the dappled forest two nether
        // biomes'; none of those lists an overworld feature.
        for (int b = 0; b < Biomes.COUNT; b++) {
            present[b] = (mask >>> bit(b) & 1L) != 0L;
        }
        // Per kind: 1 allowed, 0 denied, -1 not asked yet.
        int[] gate = this.gateAnswers;
        Arrays.fill(gate, treeGate == null ? 1 : -1);
        for (int k = 0; k < STEPS; k++) {
            Placed[] list = features[k];
            Arrays.fill(chosen, 0, list.length, false);
            for (int b = 0; b < present.length; b++) {
                if (present[b] && k < biomeSteps[b].length) {
                    for (int i : biomeSteps[b][k]) {
                        chosen[i] = true;
                    }
                }
            }
            for (int i = 0; i < list.length; i++) {
                Placed p = list[i];
                if (chosen[i] && p != null && (only == null || only.contains(p.name))) {
                    if (p.kind != 0 && gate[p.kind] != 1) {
                        if (gate[p.kind] == -1) {
                            gate[p.kind] = treeGate.allows(cx, cz, p.kind) ? 1 : 0;
                        }
                        if (gate[p.kind] == 0) {
                            continue;
                        }
                    }
                    random.setFeatureSeed(decorationSeed, i, k);
                    positionKind = treeGate != null && (p.kind == TreeGate.TREES || p.kind == TreeGate.CACTUS) ? p.kind : 0;
                    run(p, 0, ox, MIN_Y, oz);
                    positionKind = 0;
                }
            }
        }
        this.region = null;
    }

    // Per chunk, the set of biome ids it stores (by bit(id)), direct-mapped: each chunk is in
    // nine decorations' 3x3 unions.
    private static final int CACHE = 64;
    private final long[] maskKeys = new long[CACHE * CACHE];
    private final long[] masks = new long[CACHE * CACHE];
    private final boolean[] maskPresent = new boolean[CACHE * CACHE];

    private long storedBiomes(int cx, int cz) {
        int slot = (cx & (CACHE - 1)) * CACHE + (cz & (CACHE - 1));
        long key = (long) cx << 32 | (cz & 0xFFFFFFFFL);
        if (!maskPresent[slot] || maskKeys[slot] != key) {
            biomes.fillChunk(cx, cz, stored);
            long m = 0L;
            for (byte b : stored) {
                m |= 1L << bit(b);
            }
            masks[slot] = m;
            maskKeys[slot] = key;
            maskPresent[slot] = true;
        }
        return masks[slot];
    }

    // ---- placement (levelgen/placement) ----

    static final int COUNT = 0;
    static final int COUNT_UNIFORM = 1;
    static final int RARITY = 2;
    static final int IN_SQUARE = 3;
    static final int HEIGHT_UNIFORM = 4;
    static final int HEIGHT_TRAPEZOID = 5;
    static final int BIOME = 6;
    static final int HEIGHTMAP_OCEAN_FLOOR = 7;
    static final int SURFACE_THRESHOLD_OCEAN_FLOOR = 8;
    static final int HEIGHT_VERY_BIASED = 9;
    /** HeightmapPlacement, argument: Region's heightmap type, or WG_OCEAN_FLOOR / WG_WORLD_SURFACE. */
    static final int HEIGHTMAP = 10;
    /** A Modifier from Placed.mods, argument: its index. */
    static final int CUSTOM = 11;

    /** CountPlacement over PlacementUtils.countExtra: arguments base, total weight (1 / chance), extra. */
    static final int COUNT_EXTRA = 12;
    /** SurfaceWaterDepthFilter, argument: max depth (live WORLD_SURFACE - OCEAN_FLOOR). */
    static final int WATER_DEPTH = 13;

    static final int WG_OCEAN_FLOOR = 4;
    static final int WG_WORLD_SURFACE = 5;

    /** A placed feature: its placement modifiers as ops (opcode, then its arguments) and its feature. */
    static final class Placed {
        final String name;
        final int[] ops;
        final Feature feature;
        Modifier[] mods = new Modifier[0];
        int id;
        /** 0, or the {@link TreeGate} kind that may leave this feature out. */
        int kind;

        Placed(String name, Feature feature, int... ops) {
            this.name = name;
            this.feature = feature;
            this.ops = ops;
        }

        /** The modifiers CUSTOM ops refer to, by index. */
        Placed mods(Modifier... mods) {
            this.mods = mods;
            return this;
        }
    }

    /** A placement modifier that is not an op: it calls next.at(x, y, z) for each position it gives. */
    interface Modifier {
        void apply(Region region, XoroRandom random, Next next, int x, int y, int z);
    }

    /** The rest of a placed feature's modifiers after a Modifier. */
    static final class Next {
        Decorator decorator;
        Placed placed;
        int at;

        void at(int x, int y, int z) {
            decorator.run(placed, at, x, y, z);
        }
    }

    interface Feature {
        /** ConfiguredFeature.place at x, y, z (Feature.place's ensureCanWrite of the origin included). */
        boolean place(Region region, XoroRandom random, int x, int y, int z);
    }


    /** PlacedFeature.placeWithContext: each modifier's positions flow lazily through the rest, depth first. */
    private void run(Placed p, int at, int x, int y, int z) {
        int[] ops = p.ops;
        if (at == ops.length) {
            if (region.canWrite(x, z) && (positionKind == 0 || treeGate.allowsAt(x, z, zoomedBiome(x, y, z), positionKind))) {
                p.feature.place(region, random, x, y, z);
            }
            return;
        }
        switch (ops[at]) {
            case COUNT: {
                int n = ops[at + 1];
                for (int i = 0; i < n; i++) {
                    run(p, at + 2, x, y, z);
                }
                return;
            }
            case COUNT_UNIFORM: {
                int n = random.between(ops[at + 1], ops[at + 2]);
                for (int i = 0; i < n; i++) {
                    run(p, at + 3, x, y, z);
                }
                return;
            }
            case RARITY:
                if (random.nextFloat() < 1.0F / (float) ops[at + 1]) {
                    run(p, at + 2, x, y, z);
                }
                return;
            case IN_SQUARE: {
                int i = random.nextInt(16) + x;
                int j = random.nextInt(16) + z;
                run(p, at + 1, i, y, j);
                return;
            }
            case HEIGHT_UNIFORM:
                run(p, at + 3, x, uniformHeight(ops[at + 1], ops[at + 2]), z);
                return;
            case HEIGHT_TRAPEZOID:
                run(p, at + 4, x, trapezoidHeight(ops[at + 1], ops[at + 2], ops[at + 3]), z);
                return;
            case BIOME:
                if (biomeHas[zoomedBiome(x, y, z)][p.id]) {
                    run(p, at + 1, x, y, z);
                }
                return;
            case HEIGHTMAP_OCEAN_FLOOR: {
                // HeightmapPlacement(OCEAN_FLOOR_WG)
                int k = region.oceanFloor(x, z);
                if (k > MIN_Y) {
                    run(p, at + 1, x, k, z);
                }
                return;
            }
            case SURFACE_THRESHOLD_OCEAN_FLOOR: {
                // SurfaceRelativeThresholdFilter(OCEAN_FLOOR_WG, min, max), in longs as vanilla
                long i = (long) region.oceanFloor(x, z);
                long j = i + (long) ops[at + 1];
                long k = i + (long) ops[at + 2];
                if (j <= (long) y && (long) y <= k) {
                    run(p, at + 3, x, y, z);
                }
                return;
            }
            case HEIGHT_VERY_BIASED:
                run(p, at + 4, x, veryBiasedHeight(ops[at + 1], ops[at + 2], ops[at + 3]), z);
                return;
            case HEIGHTMAP: {
                int t = ops[at + 1];
                int k = t == WG_OCEAN_FLOOR ? region.oceanFloor(x, z) : t == WG_WORLD_SURFACE ? region.worldSurfaceWg(x, z)
                    : region.height(t, x, z);
                if (k > MIN_Y) {
                    run(p, at + 2, x, k, z);
                }
                return;
            }
            case COUNT_EXTRA: {
                // WeightedListInt: nextInt(total weight), (base, total - 1) then (base + extra, 1).
                int n = random.nextInt(ops[at + 2]) < ops[at + 2] - 1 ? ops[at + 1] : ops[at + 1] + ops[at + 3];
                for (int i = 0; i < n; i++) {
                    run(p, at + 4, x, y, z);
                }
                return;
            }
            case WATER_DEPTH:
                if (region.height(Region.WORLD_SURFACE, x, z) - region.height(Region.OCEAN_FLOOR, x, z) <= ops[at + 1]) {
                    run(p, at + 2, x, y, z);
                }
                return;
            case CUSTOM: {
                Next n = new Next();
                n.decorator = this;
                n.placed = p;
                n.at = at + 2;
                p.mods[ops[at + 1]].apply(region, random, n, x, y, z);
                return;
            }
            default:
                throw new IllegalStateException("op " + ops[at]);
        }
    }

    /** UniformHeight.sample (anchors already resolved). */
    private int uniformHeight(int i, int j) {
        return i > j ? i : random.between(i, j);
    }

    /** VeryBiasedToBottomHeight.sample (Mth.nextInt: min when min >= max). */
    private int veryBiasedHeight(int i, int j, int inner) {
        if (j - i - inner + 1 <= 0) {
            return i;
        }
        int k = nextInt(i + inner, j);
        int l = nextInt(i, k - 1);
        return nextInt(i, l - 1 + inner);
    }

    private int nextInt(int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    /** TrapezoidHeight.sample. */
    private int trapezoidHeight(int i, int j, int plateau) {
        if (i > j) {
            return i;
        }
        int k = j - i;
        if (plateau >= k) {
            return random.between(i, j);
        }
        int l = (k - plateau) / 2;
        int i1 = k - l;
        return i + random.between(0, i1) + random.between(0, l);
    }

    // ---- biomes ----

    /** BiomeManager.getBiome over the region's stored biomes (quart y clamped to the world). */
    int zoomedBiome(int x, int y, int z) {
        int i = x - 2;
        int j = y - 2;
        int k = z - 2;
        int l = i >> 2;
        int i1 = j >> 2;
        int j1 = k >> 2;
        // The zoom picks one of the 8 quarts around the position; where they all hold one biome, that is it.
        BiomeSource s = biomes;
        int b = s.stored(l, i1, j1);
        if (s.stored(l + 1, i1, j1) == b && s.stored(l, i1 + 1, j1) == b && s.stored(l + 1, i1 + 1, j1) == b
                && s.stored(l, i1, j1 + 1) == b && s.stored(l + 1, i1, j1 + 1) == b && s.stored(l, i1 + 1, j1 + 1) == b
                && s.stored(l + 1, i1 + 1, j1 + 1) == b) {
            return b;
        }
        double d0 = (double) (i & 3) / 4.0D;
        double d1 = (double) (j & 3) / 4.0D;
        double d2 = (double) (k & 3) / 4.0D;
        int k1 = 0;
        double d3 = Double.POSITIVE_INFINITY;
        for (int l1 = 0; l1 < 8; ++l1) {
            boolean flag = (l1 & 4) == 0;
            boolean flag1 = (l1 & 2) == 0;
            boolean flag2 = (l1 & 1) == 0;
            double d7 = SurfaceBuilder.fiddledDistance(zoomSeed, flag ? l : l + 1, flag1 ? i1 : i1 + 1, flag2 ? j1 : j1 + 1,
                flag ? d0 : d0 - 1.0D, flag1 ? d1 : d1 - 1.0D, flag2 ? d2 : d2 - 1.0D);
            if (d3 > d7) {
                k1 = l1;
                d3 = d7;
            }
        }
        return biomes.stored((k1 & 4) == 0 ? l : l + 1, (k1 & 2) == 0 ? i1 : i1 + 1, (k1 & 1) == 0 ? j1 : j1 + 1);
    }

    // ---- OreFeature ----

    static final int RULE_NATURAL_STONE = 0;
    static final int RULE_STONE_ORE_REPLACEABLES = 1;
    static final int RULE_DEEPSLATE_ORE_REPLACEABLES = 2;

    /** TagMatchTest on base_stone_overworld, stone_ore_replaceables, deepslate_ore_replaceables (the jar's block tags). */
    static boolean matches(int rule, int code) {
        switch (rule) {
            case RULE_NATURAL_STONE:
                return code == STONE || code == GRANITE || code == DIORITE || code == ANDESITE || code == TUFF || code == DEEPSLATE;
            case RULE_STONE_ORE_REPLACEABLES:
                return code == STONE || code == GRANITE || code == DIORITE || code == ANDESITE;
            default:
                return code == DEEPSLATE || code == TUFF;
        }
    }

    /** Feature.ORE with an OreConfiguration: targets (rule, code pairs), size, discard chance on air exposure. */
    static final class Ore implements Feature {
        final int[] targets;
        final int size;
        final float discard;

        Ore(int size, float discard, int... targets) {
            this.size = size;
            this.discard = discard;
            this.targets = targets;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            float f = random.nextFloat() * (float) Math.PI;
            float f1 = (float) size / 8.0F;
            int i = McMath.ceil((double) (((float) size / 16.0F * 2.0F + 1.0F) / 2.0F));
            double d0 = (double) x + Math.sin((double) f) * (double) f1;
            double d1 = (double) x - Math.sin((double) f) * (double) f1;
            double d2 = (double) z + Math.cos((double) f) * (double) f1;
            double d3 = (double) z - Math.cos((double) f) * (double) f1;
            double d4 = (double) (y + random.nextInt(3) - 2);
            double d5 = (double) (y + random.nextInt(3) - 2);
            int k = x - McMath.ceil((double) f1) - i;
            int l = y - 2 - i;
            int i1 = z - McMath.ceil((double) f1) - i;
            int j1 = 2 * (McMath.ceil((double) f1) + i);
            int k1 = 2 * (2 + i);
            for (int l1 = k; l1 <= k + j1; ++l1) {
                for (int i2 = i1; i2 <= i1 + j1; ++i2) {
                    if (l <= level.oceanFloor(l1, i2)) {
                        return doPlace(level, random, d0, d1, d2, d3, d4, d5, k, l, i1, j1, k1);
                    }
                }
            }
            return false;
        }

        private boolean doPlace(Region level, XoroRandom random, double p0, double p1, double p2, double p3,
                                double p4, double p5, int minX, int minY, int minZ, int width, int height) {
            int placed = 0;
            java.util.BitSet bitset = new java.util.BitSet(width * height * width);
            int j = size;
            double[] adouble = new double[j * 4];
            for (int k = 0; k < j; ++k) {
                float f = (float) k / (float) j;
                double d0 = McMath.lerp((double) f, p0, p1);
                double d1 = McMath.lerp((double) f, p4, p5);
                double d2 = McMath.lerp((double) f, p2, p3);
                double d3 = random.nextDouble() * (double) j / 16.0D;
                double d4 = ((double) (McMath.sin(level.rules, (float) Math.PI * f) + 1.0F) * d3 + 1.0D) / 2.0D;
                adouble[k * 4] = d0;
                adouble[k * 4 + 1] = d1;
                adouble[k * 4 + 2] = d2;
                adouble[k * 4 + 3] = d4;
            }
            for (int l3 = 0; l3 < j - 1; ++l3) {
                if (!(adouble[l3 * 4 + 3] <= 0.0D)) {
                    for (int i4 = l3 + 1; i4 < j; ++i4) {
                        if (!(adouble[i4 * 4 + 3] <= 0.0D)) {
                            double d8 = adouble[l3 * 4] - adouble[i4 * 4];
                            double d10 = adouble[l3 * 4 + 1] - adouble[i4 * 4 + 1];
                            double d12 = adouble[l3 * 4 + 2] - adouble[i4 * 4 + 2];
                            double d14 = adouble[l3 * 4 + 3] - adouble[i4 * 4 + 3];
                            if (d14 * d14 > d8 * d8 + d10 * d10 + d12 * d12) {
                                if (d14 > 0.0D) {
                                    adouble[i4 * 4 + 3] = -1.0D;
                                } else {
                                    adouble[l3 * 4 + 3] = -1.0D;
                                }
                            }
                        }
                    }
                }
            }
            for (int j4 = 0; j4 < j; ++j4) {
                double d9 = adouble[j4 * 4 + 3];
                if (!(d9 < 0.0D)) {
                    double d11 = adouble[j4 * 4];
                    double d13 = adouble[j4 * 4 + 1];
                    double d15 = adouble[j4 * 4 + 2];
                    int k4 = Math.max(McMath.floor(d11 - d9), minX);
                    int l = Math.max(McMath.floor(d13 - d9), minY);
                    int i1 = Math.max(McMath.floor(d15 - d9), minZ);
                    int j1 = Math.max(McMath.floor(d11 + d9), k4);
                    int k1 = Math.max(McMath.floor(d13 + d9), l);
                    int l1 = Math.max(McMath.floor(d15 + d9), i1);
                    for (int i2 = k4; i2 <= j1; ++i2) {
                        double d5 = ((double) i2 + 0.5D - d11) / d9;
                        if (d5 * d5 < 1.0D) {
                            for (int j2 = l; j2 <= k1; ++j2) {
                                double d6 = ((double) j2 + 0.5D - d13) / d9;
                                if (d5 * d5 + d6 * d6 < 1.0D) {
                                    for (int k2 = i1; k2 <= l1; ++k2) {
                                        double d7 = ((double) k2 + 0.5D - d15) / d9;
                                        if (d5 * d5 + d6 * d6 + d7 * d7 < 1.0D && !Region.outsideBuildHeight(j2)) {
                                            int l2 = i2 - minX + (j2 - minY) * width + (k2 - minZ) * width * height;
                                            if (!bitset.get(l2)) {
                                                bitset.set(l2);
                                                if (level.canWrite(i2, k2)) {
                                                    int state = level.get(i2, j2, k2);
                                                    for (int t = 0; t < targets.length; t += 2) {
                                                        if (canPlace(level, random, state, targets[t], i2, j2, k2)) {
                                                            level.setRaw(i2, j2, k2, targets[t + 1]);
                                                            ++placed;
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return placed > 0;
        }

        /** OreFeature.canPlaceOre. */
        private boolean canPlace(Region level, XoroRandom random, int state, int rule, int x, int y, int z) {
            if (!matches(rule, state)) {
                return false;
            }
            if (discard <= 0.0F) {
                return true;
            }
            if (!(discard >= 1.0F) && random.nextFloat() >= discard) {
                return true;
            }
            return !adjacentToAir(level, x, y, z);
        }
    }

    /** Feature.isAdjacentToAir. */
    static boolean adjacentToAir(Region level, int x, int y, int z) {
        return isAir(level.get(x, y - 1, z)) || isAir(level.get(x, y + 1, z)) || isAir(level.get(x, y, z - 1))
            || isAir(level.get(x, y, z + 1)) || isAir(level.get(x - 1, y, z)) || isAir(level.get(x + 1, y, z));
    }
}
