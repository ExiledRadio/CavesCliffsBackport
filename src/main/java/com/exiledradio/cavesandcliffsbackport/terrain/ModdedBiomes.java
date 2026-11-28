package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BopRegionBuilder;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.RegionLayer;
import com.exiledradio.cavesandcliffsbackport.terrain.core.RegionTree;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeManager;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Other mods' overworld biomes in the 1.18 terrain, placed the way Biomes O' Plenty places its own from 1.18 on:
 * through TerraBlender's regions. 1.17's layered noise ({@link RegionLayer}) puts each quart column in a region drawn
 * by weight (vanilla's 10, Biomes O' Plenty's 13 and its rare one's 2, TerraBlender's and Biomes O' Plenty's defaults,
 * region size 3); each region has its own climate parameter list, searched with the 1.18 terrain's own climate, so
 * its biomes follow 1.18's climate noise as 1.18's own do.
 * <ul>
 *   <li>Biomes O' Plenty's regions are its 1.18.2 tables ({@link BopRegionBuilder}) over the 1.12 biomes by name;
 *       a 1.18 name 1.12 never had takes a 1.12 biome with no 1.18 slot, by host matching, else falls back as
 *       Biomes O' Plenty does.</li>
 *   <li>Every other mod's biomes (Forge's {@code BiomeManager} lists: Defiled Lands, Traverse) share one more region,
 *       vanilla's parameter list with them in the slots of the 1.18 biomes they fit.</li>
 * </ul>
 * Where a region names a 1.18 biome, the 1.18 terrain's own answer stands (TerraBlender's deferred placeholder), so
 * the family's newer biomes, the cave biomes and vanilla's own layout are kept. A column a modded biome holds gets
 * its biome (spawns, colors, weather), its top and filler blocks and its decoration. With no such mod the world is
 * exactly 1.18's.
 *
 * <p>Immutable once built; any thread (each thread has its own sampler and layer).
 */
public final class ModdedBiomes {

    // ---- the hosts: 1.18.2's surface biomes

    private static final int LAND = 0;
    private static final int MOUNTAIN = 1;
    private static final int RIVER = 2;
    private static final int BEACH = 3;
    private static final int OCEAN = 4;
    private static final int DEEP_OCEAN = 5;
    private static final int MUSHROOM = 6;

    /** The BiomeDictionary types that tell biomes apart here, in bit order. */
    private static final String[] TAGS = {"PLAINS", "FOREST", "CONIFEROUS", "JUNGLE", "SAVANNA", "SWAMP", "SANDY",
            "MESA", "SNOWY", "DENSE", "SPARSE", "MOUNTAIN", "HILLS", "WET", "DRY", "HOT", "COLD", "SPOOKY", "DEAD",
            "WASTELAND", "LUSH", "MAGICAL"};

    private static final float[] TEMP = new float[Biomes.COUNT];
    private static final float[] RAIN = new float[Biomes.COUNT];
    private static final int[] HOST_TAGS = new int[Biomes.COUNT];
    private static final int[] CATEGORY = new int[Biomes.COUNT];
    private static final boolean[] HOST = new boolean[Biomes.COUNT];

    private static void h(int biome, int category, float temp, float rain, String... tags) {
        HOST[biome] = true;
        CATEGORY[biome] = category;
        TEMP[biome] = temp;
        RAIN[biome] = rain;
        HOST_TAGS[biome] = mask(tags);
    }

    static {
        h(Biomes.PLAINS, LAND, 0.8F, 0.4F, "PLAINS");
        h(Biomes.SUNFLOWER_PLAINS, LAND, 0.8F, 0.4F, "PLAINS", "LUSH");
        h(Biomes.SNOWY_PLAINS, LAND, 0.0F, 0.5F, "PLAINS", "SNOWY", "COLD", "WASTELAND");
        h(Biomes.ICE_SPIKES, LAND, 0.0F, 0.5F, "SNOWY", "COLD", "WASTELAND");
        h(Biomes.DESERT, LAND, 2.0F, 0.0F, "SANDY", "HOT", "DRY");
        h(Biomes.SWAMP, LAND, 0.8F, 0.9F, "SWAMP", "WET");
        h(Biomes.FOREST, LAND, 0.7F, 0.8F, "FOREST");
        h(Biomes.FLOWER_FOREST, LAND, 0.7F, 0.8F, "FOREST", "LUSH");
        h(Biomes.BIRCH_FOREST, LAND, 0.6F, 0.6F, "FOREST");
        h(Biomes.DARK_FOREST, LAND, 0.7F, 0.8F, "FOREST", "DENSE", "SPOOKY");
        h(Biomes.OLD_GROWTH_BIRCH_FOREST, LAND, 0.6F, 0.6F, "FOREST", "DENSE");
        h(Biomes.OLD_GROWTH_PINE_TAIGA, LAND, 0.3F, 0.8F, "FOREST", "CONIFEROUS", "COLD", "DENSE");
        h(Biomes.OLD_GROWTH_SPRUCE_TAIGA, LAND, 0.25F, 0.8F, "FOREST", "CONIFEROUS", "COLD", "DENSE");
        h(Biomes.TAIGA, LAND, 0.25F, 0.8F, "FOREST", "CONIFEROUS", "COLD");
        h(Biomes.SNOWY_TAIGA, LAND, -0.5F, 0.4F, "FOREST", "CONIFEROUS", "COLD", "SNOWY");
        h(Biomes.SAVANNA, LAND, 2.0F, 0.0F, "SAVANNA", "PLAINS", "HOT", "DRY", "SPARSE");
        h(Biomes.SAVANNA_PLATEAU, LAND, 2.0F, 0.0F, "SAVANNA", "PLAINS", "HOT", "DRY", "SPARSE", "HILLS");
        h(Biomes.WINDSWEPT_HILLS, MOUNTAIN, 0.2F, 0.3F, "MOUNTAIN", "HILLS");
        h(Biomes.WINDSWEPT_GRAVELLY_HILLS, MOUNTAIN, 0.2F, 0.3F, "MOUNTAIN", "HILLS", "WASTELAND");
        h(Biomes.WINDSWEPT_FOREST, MOUNTAIN, 0.2F, 0.3F, "MOUNTAIN", "HILLS", "FOREST", "CONIFEROUS");
        h(Biomes.WINDSWEPT_SAVANNA, MOUNTAIN, 2.0F, 0.0F, "MOUNTAIN", "HILLS", "SAVANNA", "HOT", "DRY");
        h(Biomes.JUNGLE, LAND, 0.95F, 0.9F, "JUNGLE", "FOREST", "HOT", "WET", "DENSE");
        h(Biomes.SPARSE_JUNGLE, LAND, 0.95F, 0.8F, "JUNGLE", "HOT", "WET", "SPARSE");
        h(Biomes.BAMBOO_JUNGLE, LAND, 0.95F, 0.9F, "JUNGLE", "HOT", "WET", "DENSE");
        h(Biomes.BADLANDS, LAND, 2.0F, 0.0F, "MESA", "SANDY", "HOT", "DRY");
        h(Biomes.ERODED_BADLANDS, LAND, 2.0F, 0.0F, "MESA", "SANDY", "HOT", "DRY");
        h(Biomes.WOODED_BADLANDS, LAND, 2.0F, 0.0F, "MESA", "SANDY", "HOT", "DRY", "SPARSE", "FOREST");
        h(Biomes.MEADOW, MOUNTAIN, 0.5F, 0.8F, "PLAINS", "MOUNTAIN", "LUSH");
        h(Biomes.GROVE, MOUNTAIN, -0.2F, 0.8F, "FOREST", "CONIFEROUS", "SNOWY", "COLD", "MOUNTAIN");
        h(Biomes.SNOWY_SLOPES, MOUNTAIN, -0.3F, 0.9F, "SNOWY", "COLD", "MOUNTAIN");
        h(Biomes.FROZEN_PEAKS, MOUNTAIN, -0.7F, 0.9F, "SNOWY", "COLD", "MOUNTAIN");
        h(Biomes.JAGGED_PEAKS, MOUNTAIN, -0.7F, 0.9F, "SNOWY", "COLD", "MOUNTAIN");
        h(Biomes.STONY_PEAKS, MOUNTAIN, 1.0F, 0.3F, "MOUNTAIN");
        h(Biomes.RIVER, RIVER, 0.5F, 0.5F);
        h(Biomes.FROZEN_RIVER, RIVER, 0.0F, 0.5F, "SNOWY", "COLD");
        h(Biomes.BEACH, BEACH, 0.8F, 0.4F);
        h(Biomes.SNOWY_BEACH, BEACH, 0.05F, 0.3F, "SNOWY", "COLD");
        h(Biomes.STONY_SHORE, BEACH, 0.2F, 0.3F);
        h(Biomes.WARM_OCEAN, OCEAN, 1.0F, 0.5F, "HOT");
        h(Biomes.LUKEWARM_OCEAN, OCEAN, 0.7F, 0.5F);
        h(Biomes.OCEAN, OCEAN, 0.5F, 0.5F);
        h(Biomes.COLD_OCEAN, OCEAN, 0.2F, 0.5F, "COLD");
        h(Biomes.FROZEN_OCEAN, OCEAN, 0.0F, 0.5F, "COLD", "SNOWY");
        h(Biomes.DEEP_LUKEWARM_OCEAN, DEEP_OCEAN, 0.7F, 0.5F);
        h(Biomes.DEEP_OCEAN, DEEP_OCEAN, 0.5F, 0.5F);
        h(Biomes.DEEP_COLD_OCEAN, DEEP_OCEAN, 0.2F, 0.5F, "COLD");
        h(Biomes.DEEP_FROZEN_OCEAN, DEEP_OCEAN, 0.0F, 0.5F, "COLD", "SNOWY");
        h(Biomes.MUSHROOM_FIELDS, MUSHROOM, 0.9F, 1.0F);
    }

    private static int mask(String... tags) {
        int m = 0;
        for (String t : tags) {
            for (int i = 0; i < TAGS.length; i++) {
                if (TAGS[i].equals(t)) {
                    m |= 1 << i;
                }
            }
        }
        return m;
    }

    /** The modids whose biomes {@link BiomeMap} places by their 1.18 names: the family's. */
    static final Set<String> FAMILY = new java.util.HashSet<>(java.util.Arrays.asList("minecraft",
            CavesAndCliffsBackport.MOD_ID, "backportcore", "villagerbackport", "updateaquaticbackport",
            "buzzybeesbackport", "netherupdatebackport", "wildupdatebackport", "trailsandtalesbackport",
            "trickytrialsbackport", "gamedropsbackport"));

    // ---- the candidates

    /** A modded biome placed in the 1.18 terrain. */
    static final class Entry {
        final Biome biome;
        final ResourceLocation id;
        final int weight;
        final byte numericId;
        final IBlockState top;
        final IBlockState filler;
        /** Its floor under water when it names one other than dirt (Biomes O' Plenty's seaFloorBlock), or null. */
        @Nullable
        final IBlockState seaFloor;

        Entry(Biome biome, ResourceLocation id, int weight) {
            this.biome = biome;
            this.id = id;
            // A climate's weights are relative to that climate's biomes (Biomes O' Plenty's wasteland is 50, alone in
            // its own): no more than a vanilla biome's 10 here.
            this.weight = Math.min(weight, 10);
            this.numericId = (byte) Biome.getIdForBiome(biome);
            this.top = biome.topBlock;
            this.filler = biome.fillerBlock;
            this.seaFloor = seaFloor(biome);
        }
    }

    /** Every modded biome a region names. */
    private final Entry[] entries;
    private final Map<Biome, Entry> byBiome = new IdentityHashMap<>();

    // ---- the regions (TerraBlender's)

    /** Region 0, vanilla's: its answers are the 1.18 terrain's own. */
    static final int VANILLA = 0;
    /** Biomes O' Plenty 1.18.2's regions, registered after vanilla's in TerraBlender's order. */
    static final int BOP = 1;
    static final int BOP_RARE = 2;
    /** Every other mod's biomes: vanilla's parameter list with them in their hosts' slots. */
    static final int OTHERS = 3;

    private final long seed;
    private final OverworldNoise noise;
    private final Climate climate;
    /** Per region, its parameter list, or null (vanilla's, or a region without biomes). */
    private final RegionTree[] trees;
    private final int[] regionIds;
    private final int[] regionWeights;
    private final int regionSize;
    private final String plan;

    private final ThreadLocal<Kit> kits = new ThreadLocal<Kit>() {
        @Override
        protected Kit initialValue() {
            return new Kit();
        }
    };

    /** A thread's climate sampler, region layer and per-quart answers. */
    private final class Kit {
        final ColumnCache columns = new ColumnCache(noise);
        final BiomeSource source = new BiomeSource(noise, columns, climate);
        final RegionLayer layer = new RegionLayer(seed, regionIds, regionWeights, regionSize);
        final long[] target = new long[7];
        final long[] keys = new long[4096];
        final int[] values = new int[4096];

        Kit() {
            java.util.Arrays.fill(keys, Long.MIN_VALUE);
        }

        /** The entry a region's parameter list names at a quart column's surface, or -1 (the 1.18 biome). */
        int answer(int qx, int qz) {
            long key = (long) qx << 32 | (qz & 0xFFFFFFFFL);
            int slot = (qx & 63) << 6 | (qz & 63);
            if (keys[slot] == key) {
                return values[slot];
            }
            int v = -1;
            int region = layer.region(qx, qz);
            RegionTree tree = region >= 0 && region < trees.length ? trees[region] : null;
            if (tree != null) {
                int y = columns.preliminarySurfaceLevel(qx << 2, qz << 2);
                source.target(qx, y >> 2, qz, target);
                int found = tree.search(target);
                v = found < 0 ? -1 - found : -1;
            }
            keys[slot] = key;
            values[slot] = v;
            return v;
        }
    }

    private ModdedBiomes(long seed, OverworldNoise noise, Climate climate, List<Entry> list, RegionTree[] trees,
                         int[] regionIds, int[] regionWeights, int regionSize, String plan) {
        this.seed = seed;
        this.noise = noise;
        this.climate = climate;
        this.entries = list.toArray(new Entry[0]);
        this.trees = trees;
        this.regionIds = regionIds;
        this.regionWeights = regionWeights;
        this.regionSize = regionSize;
        this.plan = plan;
        for (Entry e : entries) {
            byBiome.put(e.biome, e);
        }
    }

    /** Biomes O' Plenty 1.18 names whose 1.12 biome has another name. */
    private static final Map<String, String> BOP_ALIASES = new java.util.HashMap<>();

    static {
        BOP_ALIASES.put("lavender_field", "lavender_fields");
        BOP_ALIASES.put("highland_moor", "moor");
        BOP_ALIASES.put("bamboo_grove", "bamboo_forest");
        BOP_ALIASES.put("tropics", "tropical_island");
        BOP_ALIASES.put("origin_valley", "origin_island");
        BOP_ALIASES.put("rainbow_hills", "flower_island");
        BOP_ALIASES.put("dune_beach", "white_beach");
    }

    /**
     * The modded biomes of a new world's terrain, or null when the config turns them off or no installed mod has one:
     * then the world is 1.18's.
     */
    @Nullable
    static ModdedBiomes create(long seed, OverworldNoise noise, Climate climate) {
        if (!ModConfig.height.moddedBiomes) {
            return null;
        }
        Set<String> excluded = new java.util.HashSet<>();
        for (String s : ModConfig.height.moddedBiomesExcluded) {
            if (s != null && !s.trim().isEmpty()) {
                excluded.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        Map<ResourceLocation, Entry> others = new TreeMap<>((a, b) -> a.toString().compareTo(b.toString()));
        for (BiomeManager.BiomeType type : BiomeManager.BiomeType.values()) {
            Collection<BiomeManager.BiomeEntry> list = BiomeManager.getBiomes(type);
            if (list == null) {
                continue;
            }
            for (BiomeManager.BiomeEntry e : list) {
                if (e.biome != null && e.biome.getRegistryName() != null
                        && !"biomesoplenty".equals(e.biome.getRegistryName().getNamespace())) {
                    offer(others, excluded, e.biome, e.itemWeight);
                }
            }
        }
        Map<String, Biome> bop = biomesOPlenty(excluded);
        if (others.isEmpty() && bop.isEmpty()) {
            return null;
        }
        List<Entry> entries = new ArrayList<>();
        Map<Biome, Integer> index = new IdentityHashMap<>();
        StringBuilder plan = new StringBuilder();
        RegionTree[] trees = new RegionTree[4];
        if (!bop.isEmpty()) {
            bopRegions(bop, entries, index, trees, plan);
        }
        if (!others.isEmpty()) {
            trees[OTHERS] = othersRegion(climate, others, entries, index, plan);
        }
        int[] weights = {ModConfig.height.vanillaRegionWeight, ModConfig.height.bopRegionWeight,
                ModConfig.height.bopRareRegionWeight, ModConfig.height.otherModsRegionWeight};
        List<Integer> ids = new ArrayList<>();
        List<Integer> ws = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            // InitialLayer leaves out the regions without biomes; vanilla's is always there.
            if ((r == VANILLA || trees[r] != null) && weights[r] > 0) {
                ids.add(r);
                ws.add(weights[r]);
            }
        }
        if (ids.size() < 2 || entries.isEmpty()) {
            return null;
        }
        int[] regionIds = new int[ids.size()];
        int[] regionWeights = new int[ids.size()];
        for (int i = 0; i < regionIds.length; i++) {
            regionIds[i] = ids.get(i);
            regionWeights[i] = ws.get(i);
        }
        ModdedBiomes m = new ModdedBiomes(seed, noise, climate, entries, trees, regionIds, regionWeights,
                ModConfig.height.terraBlenderRegionSize, plan.toString());
        m.log();
        return m;
    }

    private static int entry(Biome b, List<Entry> entries, Map<Biome, Integer> index) {
        Integer i = index.get(b);
        if (i == null) {
            i = entries.size();
            entries.add(new Entry(b, b.getRegistryName(), 10));
            index.put(b, i);
        }
        return i;
    }

    /**
     * Biomes O' Plenty 1.18.2's common and rare regions ({@link BopRegionBuilder}) over the 1.12 biomes: a 1.18 name
     * takes the 1.12 biome of that name (or its {@link #BOP_ALIASES 1.12 name}); a 1.18 name 1.12 never had takes a
     * 1.12 biome the 1.18 tables have no slot for, the closest to the 1.18 biome the slot falls back to (as the host
     * matching places other mods' biomes); one left over falls back as Biomes O' Plenty's own tables do.
     */
    private static void bopRegions(Map<String, Biome> bop, List<Entry> entries, Map<Biome, Integer> index,
                                   RegionTree[] trees, StringBuilder plan) {
        // Pass 1: which 1.18 names have their 1.12 biome, which do not (and the 1.18 biome each falls back to).
        Set<String> used = new java.util.HashSet<>();
        Map<String, Integer> missing = new TreeMap<>();
        BopRegionBuilder.Resolver probe = (name, home) -> {
            String path = bop.containsKey(name) ? name : BOP_ALIASES.get(name);
            if (path != null && bop.containsKey(path)) {
                used.add(path);
                return -1;
            }
            missing.putIfAbsent(name, home);
            return BopRegionBuilder.MISSING;
        };
        BopRegionBuilder.build(probe, false, new RegionTree.Builder());
        BopRegionBuilder.build(probe, true, new RegionTree.Builder());
        // The 1.12 biomes no 1.18 name is for, by how well each fits each 1.18 biome.
        Map<String, double[]> spare = new TreeMap<>();
        for (Map.Entry<String, Biome> e : bop.entrySet()) {
            if (!used.contains(e.getKey())) {
                spare.put(e.getKey(), scores(e.getValue()));
            }
        }
        Map<String, String> standIn = new TreeMap<>();
        for (Map.Entry<String, Integer> m : missing.entrySet()) {
            String best = null;
            double bestScore = Double.MAX_VALUE;
            for (Map.Entry<String, double[]> s : spare.entrySet()) {
                double sc = s.getValue()[m.getValue()];
                if (sc < bestScore && sc <= min(s.getValue()) + 2 * MARGIN) {
                    bestScore = sc;
                    best = s.getKey();
                }
            }
            if (best != null) {
                standIn.put(m.getKey(), best);
                spare.remove(best);
            }
        }
        BopRegionBuilder.Resolver resolver = (name, home) -> {
            String path = bop.containsKey(name) ? name : BOP_ALIASES.get(name);
            if (path == null || !bop.containsKey(path)) {
                path = standIn.get(name);
            }
            return path == null ? BopRegionBuilder.MISSING : -1 - entry(bop.get(path), entries, index);
        };
        RegionTree.Builder common = new RegionTree.Builder();
        BopRegionBuilder.build(resolver, false, common);
        trees[BOP] = common.build();
        RegionTree.Builder rare = new RegionTree.Builder();
        BopRegionBuilder.build(resolver, true, rare);
        trees[BOP_RARE] = rare.build();
        plan.append("Biomes O' Plenty's 1.18.2 regions: ").append(used.size()).append(" biomes by their 1.18 names");
        if (!standIn.isEmpty()) {
            plan.append("; 1.12 biomes in 1.18 slots: ").append(standIn.toString().replace("{", "").replace("}", ""));
        }
        missing.keySet().removeAll(standIn.keySet());
        if (!missing.isEmpty()) {
            plan.append("; falling back as Biomes O' Plenty does: ").append(String.join(", ", missing.keySet()));
        }
        if (!spare.isEmpty()) {
            plan.append("; no slot: ").append(String.join(", ", spare.keySet()));
        }
        plan.append(". ");
    }

    /**
     * The region for every other mod's biomes: vanilla's parameter list with each modded biome in the slots of the
     * 1.18 biomes it fits ({@link #hosts}), a host's slots shared out among its modded biomes in turn.
     */
    @Nullable
    private static RegionTree othersRegion(Climate climate, Map<ResourceLocation, Entry> others, List<Entry> entries,
                                           Map<Biome, Integer> index, StringBuilder plan) {
        Map<String, Integer> forced = new java.util.HashMap<>();
        for (String s : ModConfig.height.moddedBiomeHosts) {
            int eq = s == null ? -1 : s.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            int host = hostByName(s.substring(eq + 1).trim());
            if (host < 0) {
                CavesAndCliffsBackport.LOGGER.warn("moddedBiomeHosts: no 1.18 surface biome named '{}' ({}).", s.substring(eq + 1), s);
            } else {
                forced.put(s.substring(0, eq).trim().toLowerCase(Locale.ROOT), host);
            }
        }
        List<List<Integer>> perHost = new ArrayList<>();
        for (int h = 0; h < Biomes.COUNT; h++) {
            perHost.add(new ArrayList<Integer>());
        }
        Map<String, List<String>> shown = new TreeMap<>();
        for (Entry e : others.values()) {
            boolean[] hosts = new boolean[Biomes.COUNT];
            Integer f = forced.get(e.id.toString());
            if (f != null) {
                hosts[f] = true;
            } else {
                hosts(e.biome, hosts);
            }
            for (int h = 0; h < Biomes.COUNT; h++) {
                if (hosts[h]) {
                    perHost.get(h).add(entry(e.biome, entries, index));
                    shown.computeIfAbsent(Biomes.name(h), k -> new ArrayList<>()).add(e.id.toString());
                }
            }
        }
        if (shown.isEmpty()) {
            return null;
        }
        int[] turn = new int[Biomes.COUNT];
        RegionTree.Builder b = new RegionTree.Builder();
        for (int i = 0; i < climate.leafCount(); i++) {
            int biome = climate.leafBiome(i);
            long[][] bounds = climate.leafBounds(i);
            List<Integer> mine = biome >= 0 && biome < Biomes.COUNT && HOST[biome] ? perHost.get(biome) : null;
            int value = biome;
            if (mine != null && !mine.isEmpty()) {
                value = -1 - mine.get(turn[biome]++ % mine.size());
            }
            b.add(bounds[0], bounds[1], value);
        }
        plan.append("Other mods' region: ");
        boolean first = true;
        for (Map.Entry<String, List<String>> s : shown.entrySet()) {
            plan.append(first ? "" : "; ").append(s.getKey()).append(" -> ").append(String.join(", ", s.getValue()));
            first = false;
        }
        plan.append(". ");
        return b.build();
    }

    private static void offer(Map<ResourceLocation, Entry> found, Set<String> excluded, @Nullable Biome biome, int weight) {
        if (biome == null || weight <= 0 || !placeable(biome, excluded)) {
            return;
        }
        ResourceLocation id = biome.getRegistryName();
        Entry old = found.get(id);
        if (old == null || old.weight < weight) {
            found.put(id, new Entry(biome, id, weight));
        }
    }

    private static boolean placeable(Biome biome, Set<String> excluded) {
        ResourceLocation id = biome.getRegistryName();
        if (id == null || FAMILY.contains(id.getNamespace()) || excluded.contains(id.getNamespace())
                || excluded.contains(id.toString()) || ForgeRegistries.BIOMES.getValue(id) != biome) {
            return false;
        }
        return !BiomeDictionary.hasType(biome, BiomeDictionary.Type.NETHER) && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.END)
                && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.VOID);
    }

    /** Biomes O' Plenty's present overworld biomes (its config decides which), by registry path. */
    private static Map<String, Biome> biomesOPlenty(Set<String> excluded) {
        Map<String, Biome> out = new TreeMap<>();
        if (!Loader.isModLoaded("biomesoplenty")) {
            return out;
        }
        try {
            Class<?> api = Class.forName("biomesoplenty.api.biome.BOPBiomes");
            Object registry = api.getField("REG_INSTANCE").get(null);
            Class<?> registryClass = Class.forName("biomesoplenty.api.biome.BOPBiomes$IBiomeRegistry");
            Collection<?> present = (Collection<?>) registryClass.getMethod("getPresentBiomes").invoke(registry);
            for (Object o : present) {
                Biome b = (Biome) o;
                ResourceLocation id = b.getRegistryName();
                if (id != null && "biomesoplenty".equals(id.getNamespace()) && placeable(b, excluded)) {
                    out.put(id.getPath(), b);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CavesAndCliffsBackport.LOGGER.warn("Could not read Biomes O' Plenty's biomes; it gets no region.", e);
        }
        return out;
    }

    @Nullable
    private static IBlockState seaFloor(Biome biome) {
        for (Class<?> c = biome.getClass(); c != null && c != Biome.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("seaFloorBlock");
                if (IBlockState.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    IBlockState s = (IBlockState) f.get(biome);
                    return s == null || s.getBlock() == net.minecraft.init.Blocks.DIRT ? null : s;
                }
            } catch (NoSuchFieldException e) {
                // the superclass, then
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private static int hostByName(String name) {
        String n = name.startsWith("minecraft:") ? name.substring(10) : name;
        for (int b = 0; b < Biomes.COUNT; b++) {
            if (HOST[b] && Biomes.name(b).equals(n)) {
                return b;
            }
        }
        return -1;
    }

    /** The tags that say what kind of land a biome is: these must agree before the climate counts. */
    private static final int KIND = mask("PLAINS", "FOREST", "CONIFEROUS", "JUNGLE", "SAVANNA", "SWAMP", "SANDY", "MESA",
            "SNOWY", "MOUNTAIN", "SPOOKY", "WASTELAND");
    /** How much worse than its best host a host may score and still get the modded biome. */
    private static final double MARGIN = 0.15D;

    private static double min(double[] a) {
        double m = Double.MAX_VALUE;
        for (double v : a) {
            m = Math.min(m, v);
        }
        return m;
    }

    /** The 1.18 biomes a modded biome takes the place of: those that score within {@link #MARGIN} of its best. */
    static void hosts(Biome biome, boolean[] out) {
        double[] score = scores(biome);
        double best = min(score);
        for (int h = 0; h < Biomes.COUNT; h++) {
            out[h] = score[h] <= best + MARGIN;
        }
    }

    /**
     * How well a modded biome fits each 1.18 biome (lower is better; MAX_VALUE where it is of another kind: land,
     * mountain, river, beach, ocean, mushroom): kind tags, temperature, rainfall and the other tags.
     */
    static double[] scores(Biome biome) {
        int tags = 0;
        for (BiomeDictionary.Type t : BiomeDictionary.getTypes(biome)) {
            tags |= mask(t.getName().toUpperCase(Locale.ROOT));
        }
        int category;
        if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN)) {
            category = biome.getBaseHeight() <= -1.4F ? DEEP_OCEAN : OCEAN;
        } else if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.RIVER)) {
            category = RIVER;
        } else if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.BEACH)) {
            category = BEACH;
        } else if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.MUSHROOM)) {
            category = MUSHROOM;
        } else {
            category = LAND;
        }
        boolean mountain = (tags & mask("MOUNTAIN")) != 0;
        boolean hills = (tags & mask("HILLS")) != 0;
        float temp = Math.min(biome.getDefaultTemperature(), 2.0F);
        float rain = Math.min(biome.getRainfall(), 1.0F);
        double[] score = new double[Biomes.COUNT];
        for (int h = 0; h < Biomes.COUNT; h++) {
            score[h] = Double.MAX_VALUE;
            if (!HOST[h]) {
                continue;
            }
            double sc;
            if (category == LAND) {
                if (CATEGORY[h] == MOUNTAIN) {
                    if (!mountain && !hills) {
                        continue;
                    }
                    sc = mountain ? 0.0D : 0.2D;
                } else if (CATEGORY[h] == LAND) {
                    sc = mountain ? 0.4D : 0.0D;
                } else {
                    continue;
                }
            } else if (CATEGORY[h] != category) {
                continue;
            } else {
                sc = 0.0D;
            }
            int both = Integer.bitCount(tags & HOST_TAGS[h]);
            int any = Integer.bitCount(tags | HOST_TAGS[h]);
            double similarity = any == 0 ? 1.0D : (double) both / any;
            int kindDiff = Integer.bitCount((tags ^ HOST_TAGS[h]) & KIND & ~mask("MOUNTAIN"));
            score[h] = sc + 0.8D * Math.abs(temp - TEMP[h]) + 0.3D * Math.abs(rain - RAIN[h]) + 0.35D * kindDiff
                    + 0.4D * (1.0D - similarity);
        }
        return score;
    }

    private void log() {
        StringBuilder w = new StringBuilder();
        String[] names = {"vanilla", "Biomes O' Plenty", "Biomes O' Plenty rare", "other mods"};
        for (int i = 0; i < regionIds.length; i++) {
            w.append(i == 0 ? "" : ", ").append(names[regionIds[i]]).append(' ').append(regionWeights[i]);
        }
        CavesAndCliffsBackport.LOGGER.info("Other mods' biomes join the 1.18 terrain in TerraBlender's regions (size {}; "
                + "weights {}): {} biomes.", regionSize, w, entries.length);
        CavesAndCliffsBackport.LOGGER.info("  {}", plan.trim());
    }

    /** The modded biome in place of 1.18 biome {@code host} at block x, z, or null for the 1.18 biome itself. */
    @Nullable
    Entry at(int x, int z, int host) {
        // Only 1.18.2's own surface biomes give way: the family's (cherry groves, mangrove swamps, pale gardens,
        // dappled forests) and the cave biomes stay as the 1.18 terrain has them.
        if (host < 0 || host >= Biomes.COUNT || !HOST[host]) {
            return null;
        }
        int i = kits.get().answer(x >> 2, z >> 2);
        return i < 0 ? null : entries[i];
    }

    /** The region at block x, z (for the probe). */
    int region(int x, int z) {
        return kits.get().layer.region(x >> 2, z >> 2);
    }

    /**
     * The 1.18 trees, cacti and surface plants step aside in a chunk whose center a modded biome holds: that biome's
     * own decoration grows there ({@code ModDecoration}). Asked on the server thread.
     */
    com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.TreeGate gate(final ModernBiomeProvider provider) {
        return (cx, cz, kind) -> {
            if (kind != com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.TreeGate.TREES
                    && kind != com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.TreeGate.CACTUS
                    && kind != com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.TreeGate.PLANTS) {
                return true;
            }
            int x = (cx << 4) + 8;
            int z = (cz << 4) + 8;
            return at(x, z, provider.modernBiome(x, z)) == null;
        };
    }

    /** Whether a modded biome may take this 1.18 biome's place (a 1.18.2 surface biome). */
    boolean hosts(int host) {
        return host >= 0 && host < Biomes.COUNT && HOST[host];
    }

    /** The entry of a modded biome placed here, or null for any other biome. */
    @Nullable
    Entry entry(@Nullable Biome biome) {
        return biome == null ? null : byBiome.get(biome);
    }

}
