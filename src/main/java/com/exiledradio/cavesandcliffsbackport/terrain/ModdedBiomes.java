package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.DoubleNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Xoroshiro;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Other mods' overworld biomes in the 1.18 terrain, the way TerraBlender adds them to 1.18: each modded biome takes the
 * place of the 1.18 biome closest to it (its host: BiomeDictionary types, temperature and rainfall), in some regions
 * of the world. The terrain, caves and structures stay 1.18's; a column whose 1.18 biome is replaced holds the modded
 * biome (spawns, grass and water colors, weather), gets its top and filler blocks, and is decorated by it.
 *
 * <p>The candidates: every biome in Forge's {@code BiomeManager} lists (the ones a Default world places, Defiled Lands,
 * Traverse) and Biomes O' Plenty's (its own world type's, with their climate weights), not vanilla's, not the family's
 * (those are placed by their 1.18 names, {@link BiomeMap}), not ones the config excludes.
 *
 * <p>Regions: Voronoi cells of {@code moddedBiomeRegionSize} blocks with noise-warped borders. Each cell draws a rank,
 * and the rank picks, for every host, one of its modded biomes (by weight) or the 1.18 biome itself: the modded biomes
 * get their weight's share against a vanilla biome's 10, at most {@code moddedBiomesMaxShare} percent. The vanilla
 * share is last, so a cell that keeps one host keeps the others too.
 * With no candidate there is nothing to place and the world is exactly 1.18's.
 *
 * <p>Immutable once built; any thread.
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
        /** The 1.18 biomes it may take the place of. */
        final boolean[] hosts = new boolean[Biomes.COUNT];

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

    private final Entry[] entries;
    /** Per host: the entries that may take its place, in rank order, or null. */
    private final int[][] hostEntries = new int[Biomes.COUNT][];
    /** Per host: the rank each of its entries ends at (the rest is the 1.18 biome's). */
    private final float[][] hostEnds = new float[Biomes.COUNT][];
    private final Map<Biome, Entry> byBiome = new IdentityHashMap<>();

    private final long salt;
    private final double cell;
    private final double warp;
    private final DoubleNoise warpX;
    private final DoubleNoise warpZ;

    /** A vanilla biome's BiomeManager weight. */
    private static final int VANILLA_WEIGHT = 10;

    private ModdedBiomes(long seed, List<Entry> list, int maxShare, int regionSize) {
        this.entries = list.toArray(new Entry[0]);
        Xoroshiro.Positional positional = Xoroshiro.ofSeed(seed).forkPositional();
        Xoroshiro r = positional.fromHashOf(CavesAndCliffsBackport.MOD_ID + ":modded_biome_regions");
        this.salt = r.nextLong();
        this.warpX = new DoubleNoise(positional.fromHashOf(CavesAndCliffsBackport.MOD_ID + ":modded_biome_warp_x"), -6, 1, 1, 1);
        this.warpZ = new DoubleNoise(positional.fromHashOf(CavesAndCliffsBackport.MOD_ID + ":modded_biome_warp_z"), -6, 1, 1, 1);
        this.cell = regionSize;
        this.warp = regionSize * 0.5D;
        for (int host = 0; host < Biomes.COUNT; host++) {
            List<Integer> mine = new ArrayList<>();
            int weights = 0;
            for (int i = 0; i < entries.length; i++) {
                if (entries[i].hosts[host]) {
                    mine.add(i);
                    weights += entries[i].weight;
                }
            }
            if (mine.isEmpty() || weights <= 0 || maxShare <= 0) {
                continue;
            }
            // The modded biomes' share of the host's regions: their weight against a vanilla biome's 10, at most maxShare.
            float share = Math.min((float) weights / (weights + VANILLA_WEIGHT), maxShare / 100.0F);
            int[] idx = new int[mine.size()];
            float[] ends = new float[mine.size()];
            int sum = 0;
            for (int k = 0; k < idx.length; k++) {
                idx[k] = mine.get(k);
                sum += entries[idx[k]].weight;
                ends[k] = share * sum / weights;
            }
            hostEntries[host] = idx;
            hostEnds[host] = ends;
        }
        for (Entry e : entries) {
            byBiome.put(e.biome, e);
        }
    }

    /**
     * The modded biomes of a new world's terrain, or null when the config turns them off or no installed mod has one:
     * then the world is 1.18's.
     */
    @Nullable
    static ModdedBiomes create(long seed) {
        if (!ModConfig.height.moddedBiomes) {
            return null;
        }
        Map<ResourceLocation, Entry> found = new TreeMap<>((a, b) -> a.toString().compareTo(b.toString()));
        Set<String> excluded = new java.util.HashSet<>();
        for (String s : ModConfig.height.moddedBiomesExcluded) {
            if (s != null && !s.trim().isEmpty()) {
                excluded.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        for (BiomeManager.BiomeType type : BiomeManager.BiomeType.values()) {
            Collection<BiomeManager.BiomeEntry> list = BiomeManager.getBiomes(type);
            if (list == null) {
                continue;
            }
            for (BiomeManager.BiomeEntry e : list) {
                offer(found, excluded, e.biome, e.itemWeight);
            }
        }
        biomesOPlenty(found, excluded);
        if (found.isEmpty()) {
            return null;
        }
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
        List<Entry> placed = new ArrayList<>();
        for (Entry e : found.values()) {
            Integer f = forced.get(e.id.toString());
            if (f != null) {
                e.hosts[f] = true;
            } else {
                hosts(e.biome, e.hosts);
            }
            for (boolean h : e.hosts) {
                if (h) {
                    placed.add(e);
                    break;
                }
            }
        }
        if (placed.isEmpty()) {
            return null;
        }
        ModdedBiomes m = new ModdedBiomes(seed, placed, ModConfig.height.moddedBiomesMaxShare,
                ModConfig.height.moddedBiomeRegionSize);
        m.log();
        return m;
    }

    private static void offer(Map<ResourceLocation, Entry> found, Set<String> excluded, @Nullable Biome biome, int weight) {
        if (biome == null || weight <= 0) {
            return;
        }
        ResourceLocation id = biome.getRegistryName();
        if (id == null || FAMILY.contains(id.getNamespace()) || excluded.contains(id.getNamespace())
                || excluded.contains(id.toString()) || ForgeRegistries.BIOMES.getValue(id) != biome) {
            return;
        }
        if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.NETHER) || BiomeDictionary.hasType(biome, BiomeDictionary.Type.END)
                || BiomeDictionary.hasType(biome, BiomeDictionary.Type.VOID)) {
            return;
        }
        Entry old = found.get(id);
        if (old == null || old.weight < weight) {
            found.put(id, new Entry(biome, id, weight));
        }
    }

    /**
     * Biomes O' Plenty places its biomes only in its own world type, from its climates (not BiomeManager): each
     * present overworld biome, weighted by its largest climate weight.
     */
    private static void biomesOPlenty(Map<ResourceLocation, Entry> found, Set<String> excluded) {
        if (!Loader.isModLoaded("biomesoplenty")) {
            return;
        }
        try {
            Class<?> api = Class.forName("biomesoplenty.api.biome.BOPBiomes");
            Object registry = api.getField("REG_INSTANCE").get(null);
            Class<?> registryClass = Class.forName("biomesoplenty.api.biome.BOPBiomes$IBiomeRegistry");
            Collection<?> present = (Collection<?>) registryClass.getMethod("getPresentBiomes").invoke(registry);
            java.lang.reflect.Method extended = registryClass.getMethod("getExtendedBiome", Biome.class);
            Class<?> extendedClass = Class.forName("biomesoplenty.api.biome.IExtendedBiome");
            java.lang.reflect.Method weights = extendedClass.getMethod("getWeightMap");
            for (Object o : present) {
                Biome b = (Biome) o;
                Object ext = extended.invoke(registry, b);
                if (ext == null) {
                    continue;
                }
                int weight = 0;
                for (Map.Entry<?, ?> w : ((Map<?, ?>) weights.invoke(ext)).entrySet()) {
                    if (!"HELL".equals(String.valueOf(w.getKey())) && w.getValue() instanceof Integer) {
                        weight = Math.max(weight, (Integer) w.getValue());
                    }
                }
                offer(found, excluded, b, weight);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CavesAndCliffsBackport.LOGGER.warn("Could not read Biomes O' Plenty's biomes; only BiomeManager's join the 1.18 terrain.", e);
        }
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

    /**
     * The 1.18 biomes a modded biome takes the place of: of the same kind (land, mountain, river, beach, ocean,
     * mushroom), those that score within {@link #MARGIN} of the best on kind tags, temperature, rainfall and the
     * other tags.
     */
    static void hosts(Biome biome, boolean[] out) {
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
        double best = Double.MAX_VALUE;
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
            sc += 0.8D * Math.abs(temp - TEMP[h]) + 0.3D * Math.abs(rain - RAIN[h]) + 0.35D * kindDiff
                    + 0.4D * (1.0D - similarity);
            score[h] = sc;
            best = Math.min(best, sc);
        }
        for (int h = 0; h < Biomes.COUNT; h++) {
            out[h] = score[h] <= best + MARGIN;
        }
    }

    private void log() {
        Map<String, Integer> mods = new TreeMap<>();
        for (Entry e : entries) {
            mods.merge(e.id.getNamespace(), 1, Integer::sum);
        }
        CavesAndCliffsBackport.LOGGER.info("Other mods' biomes join the 1.18 terrain: {} biomes ({}), regions of {} blocks.",
                entries.length, mods.toString().replace("{", "").replace("}", ""), (int) cell);
        for (int h = 0; h < Biomes.COUNT; h++) {
            int[] idx = hostEntries[h];
            if (idx == null) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            float from = 0.0F;
            for (int k = 0; k < idx.length; k++) {
                sb.append(k == 0 ? "" : ", ").append(entries[idx[k]].id).append(' ')
                        .append(Math.round((hostEnds[h][k] - from) * 100.0F)).append('%');
                from = hostEnds[h][k];
            }
            CavesAndCliffsBackport.LOGGER.info("  in place of {}: {}", Biomes.name(h), sb);
        }
    }

    // ---- the regions

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }

    private double hash01(long cx, long cz, long k) {
        long h = mix(salt ^ cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL ^ k * 0x165667B19E3779F9L);
        return (double) (h >>> 11) * 0x1.0p-53;
    }

    /** The rank of the region holding block x, z, in [0, 1). */
    double rank(int x, int z) {
        double qx = x * 0.25D;
        double qz = z * 0.25D;
        double wx = (x + warp * warpX.getValue(qx, 0.0D, qz)) / cell;
        double wz = (z + warp * warpZ.getValue(qx, 0.0D, qz)) / cell;
        long ix = (long) Math.floor(wx);
        long iz = (long) Math.floor(wz);
        double best = Double.MAX_VALUE;
        long bx = ix;
        long bz = iz;
        for (long i = ix - 1; i <= ix + 1; i++) {
            for (long j = iz - 1; j <= iz + 1; j++) {
                double px = i + 0.15D + 0.7D * hash01(i, j, 1);
                double pz = j + 0.15D + 0.7D * hash01(i, j, 2);
                double d = (px - wx) * (px - wx) + (pz - wz) * (pz - wz);
                if (d < best) {
                    best = d;
                    bx = i;
                    bz = j;
                }
            }
        }
        return hash01(bx, bz, 3);
    }

    /** The modded biome in place of 1.18 biome {@code host} at block x, z, or null for the 1.18 biome itself. */
    @Nullable
    Entry at(int x, int z, int host) {
        if (host < 0 || host >= Biomes.COUNT) {
            return null;
        }
        int[] idx = hostEntries[host];
        if (idx == null) {
            return null;
        }
        float r = (float) rank(x, z);
        float[] ends = hostEnds[host];
        for (int k = 0; k < idx.length; k++) {
            if (r < ends[k]) {
                return entries[idx[k]];
            }
        }
        return null;
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

    /** Whether this 1.18 biome has modded biomes that may take its place. */
    boolean hosts(int host) {
        return host >= 0 && host < Biomes.COUNT && hostEntries[host] != null;
    }

    /** The entry of a modded biome placed here, or null for any other biome. */
    @Nullable
    Entry entry(@Nullable Biome biome) {
        return biome == null ? null : byBiome.get(biome);
    }

    /** The modded biomes placed, by id (for the self-test and the census). */
    Map<ResourceLocation, boolean[]> hostsById() {
        Map<ResourceLocation, boolean[]> m = new LinkedHashMap<>();
        for (Entry e : entries) {
            m.put(e.id, e.hosts.clone());
        }
        return m;
    }
}
