package net.minecraft.world.level.levelgen;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * One seed's vanilla 1.20.1 overworld for FeatureDump120 (VanillaWorld's port): the generator, its biome source
 * answering from a cold search, chunks through noise fill (doFill), surface and carvers, as the Caves and Cliffs
 * Backport's 1.18 terrain with Editions.WILD | CHERRY builds them. Run on the Mojang-named 1.20.1 client jar and its
 * libraries (Java 17).
 */
final class VanillaWorld120 {
    static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(-64, 384);
    static RegistryAccess access;
    static HolderLookup.Provider lookup;
    static net.minecraft.core.MappedRegistry<Biome> biomeRegistry;
    /** Biome names in the backport's Biomes.java order: 1.18.2's declaration order, then mangrove swamp, deep dark. */
    static final List<String> BIOME_NAMES = new ArrayList<>();

    final long seed;
    final NoiseBasedChunkGenerator generator;
    final NoiseGeneratorSettings settings;
    final RandomState random;
    final Aquifer.FluidPicker picker;
    final MultiNoiseBiomeSource biomeSource;
    final Climate.Sampler sampler;
    final Registry<Biome> biomes;
    private final ThreadLocal<?> lastResult;
    final Map<Biome, Integer> biomeIds = new IdentityHashMap<>();
    final Holder<Biome>[] holders;
    private static Method doFill;
    private static Field noiseChunkField;

    static synchronized void boot() throws Exception {
        if (access != null) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        lookup = VanillaRegistries.createLookup();
        // A registry over the lookup's biomes (ProtoChunk wants one; its palettes keep the holders themselves).
        biomeRegistry = new net.minecraft.core.MappedRegistry<>(Registries.BIOME, com.mojang.serialization.Lifecycle.stable());
        lookup.lookupOrThrow(Registries.BIOME).listElements().forEach(r -> biomeRegistry.register(r.key(), r.value(),
                com.mojang.serialization.Lifecycle.stable()));
        // The static registries and the biomes (SurfaceSystem.topMaterial, which the carvers ask, reads the biome registry).
        List<Registry<?>> registries = new ArrayList<>();
        BuiltInRegistries.REGISTRY.forEach(registries::add);
        registries.add(biomeRegistry);
        access = new RegistryAccess.ImmutableRegistryAccess(registries);
        for (String n : BIOMES_1182) {
            BIOME_NAMES.add(n);
        }
        BIOME_NAMES.add("mangrove_swamp");
        BIOME_NAMES.add("deep_dark");
        BIOME_NAMES.add("cherry_grove");
        doFill = NoiseBasedChunkGenerator.class.getDeclaredMethod("doFill", Blender.class,
                net.minecraft.world.level.StructureManager.class, RandomState.class, ChunkAccess.class, int.class, int.class);
        doFill.setAccessible(true);
        noiseChunkField = ChunkAccess.class.getDeclaredField("noiseChunk");
        noiseChunkField.setAccessible(true);
    }

    /** 1.18.2's Biomes.java declaration order (the backport's biome ids 0..60). */
    static final String[] BIOMES_1182 = {"the_void", "plains", "sunflower_plains", "snowy_plains", "ice_spikes", "desert",
        "swamp", "forest", "flower_forest", "birch_forest", "dark_forest", "old_growth_birch_forest", "old_growth_pine_taiga",
        "old_growth_spruce_taiga", "taiga", "snowy_taiga", "savanna", "savanna_plateau", "windswept_hills",
        "windswept_gravelly_hills", "windswept_forest", "windswept_savanna", "jungle", "sparse_jungle", "bamboo_jungle",
        "badlands", "eroded_badlands", "wooded_badlands", "meadow", "grove", "snowy_slopes", "frozen_peaks", "jagged_peaks",
        "stony_peaks", "river", "frozen_river", "beach", "snowy_beach", "stony_shore", "warm_ocean", "lukewarm_ocean",
        "deep_lukewarm_ocean", "ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean",
        "mushroom_fields", "dripstone_caves", "lush_caves", "nether_wastes", "warped_forest", "crimson_forest",
        "soul_sand_valley", "basalt_deltas", "the_end", "end_highlands", "end_midlands", "small_end_islands", "end_barrens"};

    @SuppressWarnings("unchecked")
    VanillaWorld120(long seed) throws Exception {
        boot();
        this.seed = seed;
        Holder<NoiseGeneratorSettings> settingsHolder = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        biomeSource = MultiNoiseBiomeSource.createFromPreset(lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        generator = new NoiseBasedChunkGenerator(biomeSource, settingsHolder);
        settings = settingsHolder.value();
        random = RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), seed);
        sampler = random.sampler();
        Field pickerField = NoiseBasedChunkGenerator.class.getDeclaredField("globalFluidPicker");
        pickerField.setAccessible(true);
        picker = ((java.util.function.Supplier<Aquifer.FluidPicker>) pickerField.get(generator)).get();
        biomes = biomeRegistry;
        Field parametersField = MultiNoiseBiomeSource.class.getDeclaredField("parameters");
        parametersField.setAccessible(true);
        Object p = parametersField.get(biomeSource);
        // 1.20: an Either of the direct list or the preset's holder.
        Climate.ParameterList<?> parameters = (Climate.ParameterList<?>) ((com.mojang.datafixers.util.Either<?, ?>) p).map(
                l -> l, h -> ((net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList) ((Holder<?>) h).value()).parameters());
        Field indexField = Climate.ParameterList.class.getDeclaredField("index");
        indexField.setAccessible(true);
        Object tree = indexField.get(parameters);
        Field lastField = tree.getClass().getDeclaredField("lastResult");
        lastField.setAccessible(true);
        lastResult = (ThreadLocal<?>) lastField.get(tree);
        holders = new Holder[BIOME_NAMES.size()];
        for (int i = 0; i < BIOME_NAMES.size(); i++) {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, new net.minecraft.resources.ResourceLocation(BIOME_NAMES.get(i)));
            Holder<Biome> holder = lookup.lookupOrThrow(Registries.BIOME).getOrThrow(key);
            holders[i] = holder;
            biomeIds.put(holder.value(), i);
        }
    }

    /** getNoiseBiome from a cold search. */
    Holder<Biome> coldBiome(int qx, int qy, int qz) {
        lastResult.remove();
        return biomeSource.getNoiseBiome(qx, qy, qz, sampler);
    }

    private NoiseChunk noiseChunk(ProtoChunk chunk) throws Exception {
        Object nc = noiseChunkField.get(chunk);
        if (nc == null) {
            nc = NoiseChunk.forChunk(chunk, random, DensityFunctions.BeardifierMarker.INSTANCE, settings, picker, Blender.empty());
            noiseChunkField.set(chunk, nc);
        }
        return (NoiseChunk) nc;
    }

    /** A chunk through noise fill (NoiseBasedChunkGenerator.doFill without structures). Its biomes are cold. */
    ProtoChunk filledChunk(int cx, int cz) throws Exception {
        ChunkPos pos = new ChunkPos(cx, cz);
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, HEIGHT, biomes, null);
        chunk.fillBiomesFromNoise((x, y, z, s) -> coldBiome(x, y, z), sampler);
        noiseChunk(chunk);
        doFill.invoke(generator, Blender.empty(), null, random, chunk, -8, 48);
        return chunk;
    }

    /** NoiseBasedChunkGenerator.buildSurface, the biome look-ups clamped to the world's height (stored biomes). */
    void buildSurface(ProtoChunk chunk) throws Exception {
        int minQ = QuartPos.fromBlock(HEIGHT.getMinBuildHeight());
        int maxQ = minQ + QuartPos.fromBlock(HEIGHT.getHeight()) - 1;
        BiomeManager manager = new BiomeManager((qx, qy, qz) -> coldBiome(qx, Mth.clamp(qy, minQ, maxQ), qz),
                BiomeManager.obfuscateSeed(seed));
        WorldGenerationContext context = new WorldGenerationContext(generator, HEIGHT);
        random.surfaceSystem().buildSurface(random, manager, biomes, settings.useLegacyRandomSource(), context, chunk,
                noiseChunk(chunk), settings.surfaceRule());
    }

    final Map<Long, Holder<Biome>> carverBiomes = new java.util.HashMap<>();

    /** NoiseBasedChunkGenerator.applyCarvers (AIR), the start chunk's carver biome at quart y 0. */
    void carve(ProtoChunk chunk) throws Exception {
        BiomeManager manager = new BiomeManager((qx, qy, qz) -> coldBiome(qx, qy, qz), BiomeManager.obfuscateSeed(seed));
        WorldgenRandom wr = new WorldgenRandom(new LegacyRandomSource(0L));
        NoiseChunk noiseChunk = noiseChunk(chunk);
        Aquifer aquifer = noiseChunk.aquifer();
        net.minecraft.world.level.levelgen.carver.CarvingContext context = new net.minecraft.world.level.levelgen.carver.CarvingContext(
                generator, access, HEIGHT, noiseChunk, random, settings.surfaceRule());
        net.minecraft.world.level.chunk.CarvingMask mask = chunk.getOrCreateCarvingMask(GenerationStep.Carving.AIR);
        ChunkPos pos = chunk.getPos();
        for (int j = -8; j <= 8; ++j) {
            for (int k = -8; k <= 8; ++k) {
                ChunkPos start = new ChunkPos(pos.x + j, pos.z + k);
                Holder<Biome> biome = carverBiomes.computeIfAbsent(start.toLong(),
                        x -> coldBiome(QuartPos.fromBlock(start.getMinBlockX()), 0, QuartPos.fromBlock(start.getMinBlockZ())));
                int l = 0;
                for (Holder<net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver<?>> holder
                        : generator.getBiomeGenerationSettings(biome).getCarvers(GenerationStep.Carving.AIR)) {
                    net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver<?> carver = holder.value();
                    wr.setLargeFeatureSeed(seed + (long) l, start.x, start.z);
                    if (carver.isStartChunk(wr)) {
                        carver.carve(context, chunk, manager::getBiome, wr, aquifer, start, mask);
                    }
                    ++l;
                }
            }
        }
    }

    private static boolean tagsBound;

    /** Binds the jar's data pack tags (worldgen/biome, blocks, fluids), which the builtin registries leave empty. */
    static synchronized void bindTags() throws Exception {
        if (tagsBound) {
            return;
        }
        bindTags(BuiltInRegistries.BLOCK, "blocks");
        bindTags(BuiltInRegistries.FLUID, "fluids");
        tagsBound = true;
    }

    @SuppressWarnings("unchecked")
    private static <T> void bindTags(Registry<T> registry, String dir) throws Exception {
        Map<String, List<String>> raw = new java.util.TreeMap<>();
        java.net.URL root = VanillaWorld120.class.getResource("/data/minecraft/tags/worldgen/biome/is_ocean.json");
        java.net.JarURLConnection connection = (java.net.JarURLConnection) root.openConnection();
        connection.setUseCaches(false);
        try (java.util.jar.JarFile jar = connection.getJarFile()) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            String prefix = "data/minecraft/tags/" + dir + "/";
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry e = entries.nextElement();
                if (e.getName().startsWith(prefix) && e.getName().endsWith(".json")) {
                    String name = e.getName().substring(prefix.length(), e.getName().length() - 5);
                    try (java.io.Reader r = new java.io.InputStreamReader(jar.getInputStream(e), java.nio.charset.StandardCharsets.UTF_8)) {
                        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
                        List<String> values = new ArrayList<>();
                        for (com.google.gson.JsonElement v : o.getAsJsonArray("values")) {
                            values.add(v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString());
                        }
                        raw.put(name, values);
                    }
                }
            }
        }
        Map<net.minecraft.tags.TagKey<T>, List<Holder<T>>> bound = new java.util.HashMap<>();
        for (String name : raw.keySet()) {
            java.util.LinkedHashSet<Holder<T>> out = new java.util.LinkedHashSet<>();
            resolveTag(name, raw, registry, out);
            bound.put(net.minecraft.tags.TagKey.create(registry.key(), new net.minecraft.resources.ResourceLocation(name)), new ArrayList<>(out));
        }
        ((net.minecraft.core.MappedRegistry<T>) registry).bindTags(bound);
    }

    private static <T> void resolveTag(String name, Map<String, List<String>> raw, Registry<T> registry, java.util.Set<Holder<T>> out) {
        for (String v : raw.get(name)) {
            if (v.startsWith("#")) {
                resolveTag(new net.minecraft.resources.ResourceLocation(v.substring(1)).getPath(), raw, registry, out);
            } else {
                registry.getHolder(ResourceKey.create(registry.key(), new net.minecraft.resources.ResourceLocation(v))).ifPresent(out::add);
            }
        }
    }

    static BlockPos.MutableBlockPos scratch() {
        return new BlockPos.MutableBlockPos();
    }
}
