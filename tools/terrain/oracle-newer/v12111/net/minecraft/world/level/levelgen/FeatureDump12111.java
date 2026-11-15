package net.minecraft.world.level.levelgen;

import com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Carvers;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ChunkFiller;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.SurfaceBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.WorldGenTickAccess;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

/**
 * Vanilla 1.21.11's decoration (ChunkGenerator.applyBiomeDecoration's feature loop, no structures) for Caves and
 * Cliffs Backport's FeatureCheck -Deditions=7, in VanillaFeatureDump's features_SEED.bin.gz format.
 *
 * <p>The chunks before decoration (fill, surface, carvers) come from the backport's terrain core with editions 7:
 * that part is checked against vanilla on its own (TerrainCheck, SurfaceCheck, CarverCheck on 1.18.2, the 1.20.1 and
 * 1.21.4 fills), and 1.21.4's noise, surface rules and carvers are 1.20.1's but for the aquifer's update marks.
 * Their biomes are vanilla 1.21.4's (a cold search, as NewerBiomeCheck). Every placed feature then runs through
 * vanilla's own placeWithBiomeCheck on a WorldGenRegion (allocated without its constructor: no ServerLevel), with
 * the jar's block tags bound. Only the placed features named in -Dfeatures=a,b,... run (every one keeps its index and
 * seed). State names as VanillaFeatureDump writes them, with two renames for the core's 1.18 names: short_grass is
 * grass, and leaves drop waterlogged=false.
 *
 * <p>Arguments: outDir side [seeds...]; -Dcenters=seed:cx,cz/cx,cz;seed:... the areas' center chunks per seed
 * (default 0,0 and FeatureCheck's far point). Classpath: the 1.21.4 named client jar, its libraries and the core's
 * classes (build/terrain-tools).
 */
public final class FeatureDump12111 {
    static final int EDITIONS = Integer.getInteger("editions", 7);
    static HolderLookup.Provider lookup;
    static MappedRegistry<Biome> biomeRegistry;
    static final Map<String, BlockState> STATES = new HashMap<>();
    static Set<String> allowed;
    static DimensionType overworld;

    static void boot() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        lookup = VanillaRegistries.createLookup();
        bindBlockTags();
        biomeRegistry = new MappedRegistry<>(Registries.BIOME, com.mojang.serialization.Lifecycle.stable());
        lookup.lookupOrThrow(Registries.BIOME).listElements().forEach(r ->
            biomeRegistry.register(r.key(), r.value(), net.minecraft.core.RegistrationInfo.BUILT_IN));
        overworld = lookup.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD).value();
        for (Block b : BuiltInRegistries.BLOCK) {
            for (BlockState s : b.getStateDefinition().getPossibleStates()) {
                STATES.put(stateName(s), s);
            }
        }
    }

    /** The jar's data/minecraft/tags/block, resolved, bound to the block registry. */
    @SuppressWarnings("unchecked")
    static void bindBlockTags() throws Exception {
        Map<String, List<String>> raw = new TreeMap<>();
        java.net.URL root = FeatureDump12111.class.getResource("/data/minecraft/tags/block/leaves.json");
        java.net.JarURLConnection connection = (java.net.JarURLConnection) root.openConnection();
        connection.setUseCaches(false);
        try (java.util.jar.JarFile jar = connection.getJarFile()) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            String prefix = "data/minecraft/tags/block/";
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
        Map<TagKey<Block>, List<Holder<Block>>> bound = new HashMap<>();
        for (String name : raw.keySet()) {
            java.util.LinkedHashSet<Holder<Block>> out = new java.util.LinkedHashSet<>();
            resolve(name, raw, out);
            bound.put(TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(name)), new ArrayList<>(out));
        }
        ((MappedRegistry<Block>) BuiltInRegistries.BLOCK).prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK, bound)).apply();
    }

    private static void resolve(String name, Map<String, List<String>> raw, Set<Holder<Block>> out) {
        for (String v : raw.get(name)) {
            if (v.startsWith("#")) {
                resolve(Identifier.parse(v.substring(1)).getPath(), raw, out);
            } else {
                BuiltInRegistries.BLOCK.get(ResourceKey.create(Registries.BLOCK, Identifier.parse(v))).ifPresent(out::add);
            }
        }
    }

    static String stateName(BlockState s) {
        StringBuilder b = new StringBuilder(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath());
        Collection<Property<?>> props = s.getProperties();
        if (!props.isEmpty()) {
            TreeMap<String, String> sorted = new TreeMap<>();
            for (Property<?> p : props) {
                sorted.put(p.getName(), value(s, p));
            }
            b.append('[');
            boolean first = true;
            for (Map.Entry<String, String> e : sorted.entrySet()) {
                if (!first) {
                    b.append(',');
                }
                first = false;
                b.append(e.getKey()).append('=').append(e.getValue());
            }
            b.append(']');
        }
        return b.toString();
    }

    /** The core's name for a 1.21.4 state (short_grass is 1.18's grass; leaves without waterlogged=false). */
    static String coreName(BlockState s) {
        String n = stateName(s);
        if (n.equals("short_grass")) {
            return "grass";
        }
        if (n.contains("_leaves[")) {
            n = n.replace(",waterlogged=false", "");
        }
        return n;
    }

    private static <T extends Comparable<T>> String value(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }

    // ---- one seed's world ----

    static final class World {
        final long seed;
        final RandomState randomState;
        final MultiNoiseBiomeSource biomeSource;
        final Climate.Sampler sampler;
        final NoiseBasedChunkGenerator generator;
        final ThreadLocal<?> lastResult;
        final OverworldNoise noise;
        final ColumnCache columns;
        final ChunkFiller filler;
        final SurfaceBuilder surface;
        final Carvers carvers;
        final Map<Long, ProtoChunk> chunks = new HashMap<>();
        final LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
        final char[] blocks = new char[16 * 16 * 384];

        World(long seed) throws Exception {
            this.seed = seed;
            randomState = RandomState.create(lookup, NoiseGeneratorSettings.OVERWORLD, seed);
            sampler = randomState.sampler();
            biomeSource = MultiNoiseBiomeSource.createFromPreset(lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
            generator = new NoiseBasedChunkGenerator(biomeSource,
                lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD));
            java.lang.reflect.Method pm = MultiNoiseBiomeSource.class.getDeclaredMethod("parameters");
            pm.setAccessible(true);
            Climate.ParameterList<?> parameters = (Climate.ParameterList<?>) pm.invoke(biomeSource);
            Field indexField = Climate.ParameterList.class.getDeclaredField("index");
            indexField.setAccessible(true);
            Object tree = indexField.get(parameters);
            Field lastField = tree.getClass().getDeclaredField("lastResult");
            lastField.setAccessible(true);
            lastResult = (ThreadLocal<?>) lastField.get(tree);
            noise = new OverworldNoise(seed, EDITIONS);
            columns = new ColumnCache(noise);
            filler = new ChunkFiller(noise, columns);
            com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource core =
                new com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource(noise, columns,
                    new com.exiledradio.cavesandcliffsbackport.terrain.core.Climate(EDITIONS));
            surface = new SurfaceBuilder(noise, columns, core);
            carvers = new Carvers(noise, columns, surface);
        }

        Holder<Biome> coldBiome(int qx, int qy, int qz) {
            lastResult.remove();
            return biomeSource.getNoiseBiome(qx, qy, qz, sampler);
        }

        /** A chunk after the core's fill, surface and carvers, with vanilla's biomes, at CARVERS; made on first use. */
        ProtoChunk chunk(int cx, int cz) {
            return chunks.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> {
                ChunkPos pos = new ChunkPos(cx, cz);
                ProtoChunk c = new ProtoChunk(pos, UpgradeData.EMPTY, height, net.minecraft.world.level.chunk.PalettedContainerFactory.create(new RegistryAccess.ImmutableRegistryAccess(java.util.List.of(biomeRegistry))), null);
                c.fillBiomesFromNoise((x, y, z, s) -> coldBiome(x, y, z), sampler);
                Arrays.fill(blocks, (char) 0);
                filler.fill(cx, cz, blocks);
                surface.build(cx, cz, blocks);
                carvers.carve(cx, cz, blocks);
                LevelChunkSection[] sections = c.getSections();
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int i = 0; i < 384; i++) {
                            int code = blocks[(x * 16 + z) * 384 + i];
                            if (code != 0) {
                                BlockState s = STATES.get(BlockCodes.state(code));
                                if (s == null) {
                                    throw new IllegalStateException("no 1.21.4 state for " + BlockCodes.state(code));
                                }
                                sections[i >> 4].setBlockState(x, i & 15, z, s, false);
                            }
                        }
                    }
                }
                Heightmap.primeHeightmaps(c, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
                c.setPersistedStatus(ChunkStatus.CARVERS);
                return c;
            });
        }
    }

    // ---- the region ----

    /** A ServerLevel whose chunk source answers the generator (PaleMossDecorator's way to its patch feature). */
    static class FakeLevel extends net.minecraft.server.level.ServerLevel {
        FakeChunkSource source;

        FakeLevel() {
            super(null, null, null, null, null, null, false, 0L, null, false, null);
        }

        @Override
        public net.minecraft.server.level.ServerChunkCache getChunkSource() {
            return source;
        }
    }

    static class FakeChunkSource extends net.minecraft.server.level.ServerChunkCache {
        net.minecraft.world.level.chunk.ChunkGenerator generator;

        FakeChunkSource() {
            super(null, null, null, null, null, null, 0, 0, false, null, null);
        }

        @Override
        public net.minecraft.world.level.chunk.ChunkGenerator getGenerator() {
            return generator;
        }
    }

    static class Region extends WorldGenRegion {
        World world;
        ChunkPos centerPos;
        RandomSource regionRandom;
        BiomeManager manager;
        FakeLevel fakeLevel;
        WorldGenTickAccess<Block> ticks;
        WorldGenTickAccess<net.minecraft.world.level.material.Fluid> fluidTicks2;
        long subTicks;

        Region() {
            super(null, null, null, null);
        }

        @Override
        public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) {
            if (Math.abs(x - centerPos.x) > 8 || Math.abs(z - centerPos.z) > 8) {
                throw new IllegalStateException("read outside the region: " + x + "," + z);
            }
            return world.chunk(x, z);
        }

        @Override
        public boolean hasChunk(int x, int z) {
            return Math.abs(x - centerPos.x) <= 1 && Math.abs(z - centerPos.z) <= 1;
        }

        @Override
        public ChunkPos getCenter() {
            return centerPos;
        }

        @Override
        public boolean ensureCanWrite(BlockPos pos) {
            return Math.abs((pos.getX() >> 4) - centerPos.x) <= 1 && Math.abs((pos.getZ() >> 4) - centerPos.z) <= 1;
        }

        @Override
        public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursion) {
            if (!ensureCanWrite(pos)) {
                return false;
            }
            ChunkAccess chunk = getChunk(pos);
            BlockState old = chunk.setBlockState(pos, state);
            if (state.hasBlockEntity()) {
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                tag.putInt("x", pos.getX());
                tag.putInt("y", pos.getY());
                tag.putInt("z", pos.getZ());
                tag.putString("id", "DUMMY");
                chunk.setBlockEntityNbt(tag);
            } else if (old != null && old.hasBlockEntity()) {
                chunk.removeBlockEntity(pos);
            }
            if (state.hasPostProcess(this, pos)) {
                chunk.markPosForPostprocessing(pos);
            }
            return true;
        }

        @Override
        public net.minecraft.server.level.ServerLevel getLevel() {
            return fakeLevel;
        }

        @Override
        public RandomSource getRandom() {
            return regionRandom;
        }

        @Override
        public long getSeed() {
            return world.seed;
        }

        @Override
        public int getSeaLevel() {
            return 63;
        }

        @Override
        public int getMinY() {
            return -64;
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public net.minecraft.world.level.chunk.ChunkSource getChunkSource() {
            return fakeLevel.source;
        }

        @Override
        public DimensionType dimensionType() {
            return overworld;
        }

        @Override
        public BiomeManager getBiomeManager() {
            return manager;
        }

        @Override
        public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
            return world.coldBiome(x, y, z);
        }

        @Override
        public LevelTickAccess<Block> getBlockTicks() {
            return ticks;
        }

        @Override
        public LevelTickAccess<net.minecraft.world.level.material.Fluid> getFluidTicks() {
            return fluidTicks2;
        }

        @Override
        public long nextSubTickCount() {
            return subTicks++;
        }

        @Override
        public net.minecraft.world.level.storage.LevelData getLevelData() {
            return (net.minecraft.world.level.storage.LevelData) java.lang.reflect.Proxy.newProxyInstance(
                FeatureDump12111.class.getClassLoader(), new Class<?>[]{net.minecraft.world.level.storage.LevelData.class},
                (proxy, method, a) -> {
                    Class<?> t = method.getReturnType();
                    return t == long.class ? (Object) 0L : t == int.class ? (Object) 0 : t == float.class ? (Object) 0.0F
                        : t == boolean.class ? (Object) false : null;
                });
        }

        @Override
        public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
            return net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS;
        }

        @Override
        public RegistryAccess registryAccess() {
            return REGISTRIES;
        }

        // Light at FEATURES: block light 0, sky light 15, no sky darkening (as VanillaFeatureDump).
        @Override
        public int getBrightness(net.minecraft.world.level.LightLayer layer, BlockPos pos) {
            return layer == net.minecraft.world.level.LightLayer.SKY ? 15 : 0;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return Math.max(0, 15 - amount);
        }
    }

    /**
     * A RegistryAccess over the lookup: lookup(key) answers a Registry proxy whose get(ResourceKey) and similar
     * calls go to the lookup's RegistryLookup (the only use: PaleMossDecorator finding pale_moss_patch).
     */
    static final RegistryAccess REGISTRIES = (RegistryAccess) java.lang.reflect.Proxy.newProxyInstance(
        FeatureDump12111.class.getClassLoader(), new Class<?>[]{RegistryAccess.class}, (proxy, method, a) -> {
            if (method.getName().equals("lookup") && a != null && a.length == 1 && a[0] instanceof ResourceKey) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                HolderLookup.RegistryLookup<?> l = lookup.lookupOrThrow((ResourceKey) a[0]);
                return Optional.of(java.lang.reflect.Proxy.newProxyInstance(FeatureDump12111.class.getClassLoader(),
                    new Class<?>[]{Registry.class}, (p2, m2, a2) -> {
                        java.lang.reflect.Method m = HolderLookup.RegistryLookup.class.getMethod(m2.getName(), m2.getParameterTypes());
                        return m.invoke(l, a2);
                    }));
            }
            if (method.getName().equals("lookupOrThrow") && a != null && a.length == 1) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object l = lookup.lookupOrThrow((ResourceKey) a[0]);
                return l;
            }
            throw new UnsupportedOperationException("RegistryAccess." + method.getName());
        });

    static sun.misc.Unsafe unsafe() throws Exception {
        Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    static Region region(World world, int cx, int cz) throws Exception {
        Region r = (Region) unsafe().allocateInstance(Region.class);
        r.world = world;
        r.centerPos = new ChunkPos(cx, cz);
        // WorldGenRegion's random: RandomState.getOrCreateRandomFactory(worldgen_region_random) at the center.
        r.regionRandom = world.randomState.getOrCreateRandomFactory(Identifier.withDefaultNamespace("worldgen_region_random"))
            .at(r.centerPos.getWorldPosition());
        r.manager = new BiomeManager(r, BiomeManager.obfuscateSeed(world.seed));
        r.ticks = new WorldGenTickAccess<>(p -> r.getChunk(p).getBlockTicks());
        r.fluidTicks2 = new WorldGenTickAccess<>(p -> r.getChunk(p).getFluidTicks());
        FakeLevel level = (FakeLevel) unsafe().allocateInstance(FakeLevel.class);
        FakeChunkSource source = (FakeChunkSource) unsafe().allocateInstance(FakeChunkSource.class);
        source.generator = world.generator;
        level.source = source;
        r.fakeLevel = level;
        return r;
    }

    /** ChunkGenerator.applyBiomeDecoration's feature loop (no structures), with the allowlist. */
    @SuppressWarnings("unchecked")
    static void decorate(World world, Region region, ProtoChunk chunk, List<FeatureSorter.StepFeatureData> steps,
                         Map<PlacedFeature, String> names) {
        ChunkPos chunkpos = chunk.getPos();
        SectionPos sectionpos = SectionPos.of(chunkpos, region.getMinSectionY());
        BlockPos origin = sectionpos.origin();
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(world.seed, origin.getX(), origin.getZ());
        Set<Holder<Biome>> set = new it.unimi.dsi.fastutil.objects.ObjectArraySet<>();
        ChunkPos.rangeClosed(sectionpos.chunk(), 1).forEach(p -> {
            ChunkAccess c = region.getChunk(p.x, p.z);
            for (LevelChunkSection s : c.getSections()) {
                s.getBiomes().getAll(set::add);
            }
        });
        // possibleBiomes holds the lookup's holders; the chunks hold them too (the resolver's answers).
        set.retainAll(world.biomeSource.possibleBiomes());
        int j = steps.size();
        int total = Math.max(GenerationStep.Decoration.values().length, j);
        for (int k = 0; k < total; ++k) {
            if (k < j) {
                it.unimi.dsi.fastutil.ints.IntSet intset = new it.unimi.dsi.fastutil.ints.IntArraySet();
                FeatureSorter.StepFeatureData data = steps.get(k);
                for (Holder<Biome> biome : set) {
                    List<HolderSet<PlacedFeature>> list1 = biome.value().getGenerationSettings().features();
                    if (k < list1.size()) {
                        list1.get(k).stream().map(Holder::value).forEach(pf -> intset.add(data.indexMapping().applyAsInt(pf)));
                    }
                }
                int[] aint = intset.toIntArray();
                Arrays.sort(aint);
                for (int l1 : aint) {
                    PlacedFeature pf = data.features().get(l1);
                    String key = names.get(pf);
                    if (allowed != null && !allowed.contains(key)) {
                        continue;
                    }
                    random.setFeatureSeed(decorationSeed, l1, k);
                    pf.placeWithBiomeCheck(region, world.generator, random, origin);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        int side = Integer.parseInt(args[1]);
        long[] seeds = {42L, 12345L, -7346534567L, 8675309L};
        if (args.length > 2) {
            seeds = new long[args.length - 2];
            for (int i = 2; i < args.length; i++) {
                seeds[i - 2] = Long.parseLong(args[i]);
            }
        }
        String only = System.getProperty("features");
        if (only != null) {
            allowed = new HashSet<>(Arrays.asList(only.split(",")));
        }
        boot();
        Map<PlacedFeature, String> names = new java.util.IdentityHashMap<>();
        lookup.lookupOrThrow(Registries.PLACED_FEATURE).listElements().forEach(r -> names.put(r.value(), r.key().identifier().getPath()));
        for (long seed : seeds) {
            long start = System.nanoTime();
            World world = new World(seed);
            Field fps = net.minecraft.world.level.chunk.ChunkGenerator.class.getDeclaredField("featuresPerStep");
            fps.setAccessible(true);
            List<FeatureSorter.StepFeatureData> steps =
                ((java.util.function.Supplier<List<FeatureSorter.StepFeatureData>>) fps.get(world.generator)).get();
            Random rnd = new Random(seed * 31L + 5L);
            int[][] centers = {{0, 0}, {rnd.nextInt(20000) - 10000, rnd.nextInt(20000) - 10000}};
            for (String part : System.getProperty("centers", "").split(";")) {
                String[] kv = part.split(":");
                if (kv.length == 2 && Long.parseLong(kv[0]) == seed) {
                    String[] areas = kv[1].split("/");
                    centers = new int[areas.length][];
                    for (int c = 0; c < areas.length; c++) {
                        String[] xz = areas[c].split(",");
                        centers[c] = new int[]{Integer.parseInt(xz[0].trim()), Integer.parseInt(xz[1].trim())};
                    }
                }
            }
            List<ChunkPos> order = new ArrayList<>();
            for (int[] c : centers) {
                for (int i = 0; i < side * side; i++) {
                    order.add(new ChunkPos(c[0] - side / 2 + i / side, c[1] - side / 2 + i % side));
                }
            }
            long decorateNanos = 0L;
            for (ChunkPos p : order) {
                Region region = region(world, p.x, p.z);
                ProtoChunk target = world.chunk(p.x, p.z);
                long t = System.nanoTime();
                decorate(world, region, target, steps, names);
                decorateNanos += System.nanoTime() - t;
            }
            Map<String, Integer> palette = new LinkedHashMap<>();
            Map<String, Long> census = new TreeMap<>();
            List<char[]> data = new ArrayList<>();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (ChunkPos p : order) {
                ProtoChunk c = world.chunks.get(p.toLong());
                char[] blocks = new char[16 * 16 * 384];
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 384; y++) {
                            String s = coreName(c.getBlockState(m.set(p.getMinBlockX() + x, y - 64, p.getMinBlockZ() + z)));
                            Integer id = palette.get(s);
                            if (id == null) {
                                id = palette.size();
                                palette.put(s, id);
                            }
                            blocks[(x * 16 + z) * 384 + y] = (char) (int) id;
                            int bi = s.indexOf('[');
                            census.merge(bi < 0 ? s : s.substring(0, bi), 1L, Long::sum);
                        }
                    }
                }
                data.add(blocks);
            }
            try (DataOutputStream w = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(
                    new FileOutputStream(new File(out, "features_" + seed + ".bin.gz")), 1 << 16), 1 << 16))) {
                w.writeInt(palette.size());
                for (String s : palette.keySet()) {
                    w.writeUTF(s);
                }
                w.writeInt(order.size());
                for (int n = 0; n < order.size(); n++) {
                    w.writeInt(order.get(n).x);
                    w.writeInt(order.get(n).z);
                    for (char ch : data.get(n)) {
                        w.writeChar(ch);
                    }
                }
            }
            StringBuilder pale = new StringBuilder();
            for (String b : new String[]{"pale_oak_log", "pale_oak_leaves", "pale_moss_block", "pale_moss_carpet",
                    "pale_hanging_moss", "closed_eyeblossom", "creaking_heart"}) {
                pale.append(' ').append(b).append('=').append(census.getOrDefault(b, 0L));
            }
            System.out.println(String.format(java.util.Locale.ROOT, "seed %d: %d chunks decorated (%.3f ms/chunk), %d made, "
                + "%d states, %d ms;%s", seed, order.size(), decorateNanos / 1.0E6D / order.size(), world.chunks.size(),
                palette.size(), (System.nanoTime() - start) / 1000000L, pale));
        }
    }
}
