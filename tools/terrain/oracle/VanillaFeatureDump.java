package net.minecraft.world.level.levelgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.ticks.WorldGenTickAccess;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

/**
 * Vanilla 1.18.2 decoration (ChunkGenerator.applyBiomeDecoration's loop, structures left out: none are started
 * here) on chunks after fill, surface and carvers, for the Caves and Cliffs Backport's FeatureCheck. Every placed
 * feature runs through vanilla's own placeWithBiomeCheck on a WorldGenRegion like the FEATURES status builds:
 * reads anywhere within 8 chunks (generated on demand), writes within 1 of the chunk being decorated. Only the
 * placed features named in -Dfeatures=a,b,... run (all if unset); every feature keeps its own index and seed, so a
 * subset decorates exactly as vanilla would with the others producing nothing.
 *
 * Chunks are decorated in a fixed order (area by area, x outer, z inner), since features write into neighbors.
 *
 * Writes feature_tables.txt (per step the featuresPerStep index list, per biome its features per step) and
 * features_SEED.bin.gz: palette (int n, n UTF canonical states "name[prop=value,...]"), int chunks, then per
 * decorated chunk int cx, int cz, 16 * 16 * 384 unsigned shorts (palette index, (x * 16 + z) * 384 + y + 64).
 *
 * Arguments: outDir side [seeds...]   (areas: side x side chunks around 0,0 and around a far point)
 */
public final class VanillaFeatureDump {
    static Set<String> allowed;

    /** A WorldGenRegion for one chunk's FEATURES step, without a ServerLevel (constructor skipped). */
    static final class Region extends WorldGenRegion {
        VanillaWorld world;
        Map<Long, ProtoChunk> chunks;
        ChunkPos centerPos;

        Region() {
            super(null, null, null, 0);
        }

        @Override
        public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) {
            if (Math.abs(x - centerPos.x) > 8 || Math.abs(z - centerPos.z) > 8) {
                throw new IllegalStateException("read outside the region: " + x + "," + z);
            }
            return chunk(world, chunks, x, z);
        }

        @Override
        public boolean hasChunk(int x, int z) {
            return Math.abs(x - centerPos.x) <= 8 && Math.abs(z - centerPos.z) <= 8;
        }

        @Override
        public ChunkPos getCenter() {
            return centerPos;
        }

        @Override
        public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursion) {
            // WorldGenRegion.setBlock without ServerLevel.onBlockStateChange (points of interest only).
            if (!this.ensureCanWrite(pos)) {
                return false;
            }
            ChunkAccess chunk = this.getChunk(pos);
            BlockState old = chunk.setBlockState(pos, state, false);
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
        public RegistryAccess registryAccess() {
            return VanillaWorld.access;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getSeaLevel() {
            return 63;
        }

        @Override
        public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
            return world.coldBiome(x, y, z);
        }

        @Override
        public long getSeed() {
            return world.seed;
        }
    }

    static sun.misc.Unsafe unsafe() throws Exception {
        Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    static void set(Object o, String name, Object value) throws Exception {
        Field f = WorldGenRegion.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, value);
    }

    static Region region(VanillaWorld world, Map<Long, ProtoChunk> chunks, int cx, int cz) throws Exception {
        Region r = (Region) unsafe().allocateInstance(Region.class);
        r.world = world;
        r.chunks = chunks;
        r.centerPos = new ChunkPos(cx, cz);
        ProtoChunk center = chunk(world, chunks, cx, cz);
        set(r, "center", center);
        set(r, "size", 17);
        set(r, "seed", world.seed);
        set(r, "random", new Random(0L));
        set(r, "dimensionType", VanillaWorld.access.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY)
            .getOrThrow(net.minecraft.world.level.dimension.DimensionType.OVERWORLD_LOCATION));
        set(r, "biomeManager", new BiomeManager(r, BiomeManager.obfuscateSeed(world.seed)));
        set(r, "firstPos", new ChunkPos(cx - 8, cz - 8));
        set(r, "lastPos", new ChunkPos(cx + 8, cz + 8));
        set(r, "generatingStatus", ChunkStatus.FEATURES);
        set(r, "writeRadiusCutoff", 1);
        set(r, "blockTicks", new WorldGenTickAccess<net.minecraft.world.level.block.Block>(p -> r.getChunk(p).getBlockTicks()));
        set(r, "fluidTicks", new WorldGenTickAccess<net.minecraft.world.level.material.Fluid>(p -> r.getChunk(p).getFluidTicks()));
        set(r, "subTickCount", new java.util.concurrent.atomic.AtomicLong());
        // Scheduled ticks read the game time: a level data answering zero / false to everything.
        set(r, "levelData", java.lang.reflect.Proxy.newProxyInstance(VanillaFeatureDump.class.getClassLoader(),
            new Class<?>[]{net.minecraft.world.level.storage.LevelData.class}, (proxy, method, a) -> {
                Class<?> t = method.getReturnType();
                if (t == long.class) {
                    return 0L;
                }
                if (t == int.class) {
                    return 0;
                }
                if (t == float.class) {
                    return 0.0F;
                }
                if (t == boolean.class) {
                    return false;
                }
                return null;
            }));
        return r;
    }

    static final byte[] scratch = new byte[16 * 16 * 384];

    /** A chunk after fill, surface and carvers (LIQUID_CARVERS), made on first use. */
    static ProtoChunk chunk(VanillaWorld world, Map<Long, ProtoChunk> chunks, int cx, int cz) {
        return chunks.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> {
            ProtoChunk c = world.filledChunk(cx, cz, scratch);
            world.buildSurface(c);
            world.carve(c);
            c.setStatus(ChunkStatus.LIQUID_CARVERS);
            return c;
        });
    }

    /** ChunkGenerator.applyBiomeDecoration, features only, with the allowlist. */
    static void decorate(VanillaWorld world, Region region, ProtoChunk chunk) {
        ChunkPos chunkpos = chunk.getPos();
        net.minecraft.core.SectionPos sectionpos = net.minecraft.core.SectionPos.of(chunkpos, region.getMinSection());
        BlockPos blockpos = sectionpos.origin();
        net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(
            Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR,
            Heightmap.Types.WORLD_SURFACE));
        List<BiomeSource.StepFeatureData> list = world.biomeSource.featuresPerStep();
        WorldgenRandom worldgenrandom = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.seedUniquifier()));
        long i = worldgenrandom.setDecorationSeed(region.getSeed(), blockpos.getX(), blockpos.getZ());
        Set<Biome> set = new it.unimi.dsi.fastutil.objects.ObjectArraySet<>();
        ChunkPos.rangeClosed(sectionpos.chunk(), 1).forEach(p -> {
            ChunkAccess c = region.getChunk(p.x, p.z);
            for (LevelChunkSection s : c.getSections()) {
                s.getBiomes().getAll(h -> set.add(h.value()));
            }
        });
        Set<Biome> possible = new HashSet<>();
        world.biomeSource.possibleBiomes().forEach(h -> possible.add(h.value()));
        set.retainAll(possible);
        int j = list.size();
        Registry<PlacedFeature> registry = VanillaWorld.access.registryOrThrow(Registry.PLACED_FEATURE_REGISTRY);
        int steps = Math.max(GenerationStep.Decoration.values().length, j);
        for (int k = 0; k < steps; ++k) {
            if (k < j) {
                it.unimi.dsi.fastutil.ints.IntSet intset = new it.unimi.dsi.fastutil.ints.IntArraySet();
                for (Biome biome : set) {
                    List<HolderSet<PlacedFeature>> list1 = biome.getGenerationSettings().features();
                    if (k < list1.size()) {
                        BiomeSource.StepFeatureData data = list.get(k);
                        list1.get(k).stream().map(Holder::value).forEach(pf -> intset.add(data.indexMapping().applyAsInt(pf)));
                    }
                }
                int[] aint = intset.toIntArray();
                Arrays.sort(aint);
                BiomeSource.StepFeatureData data = list.get(k);
                for (int l1 : aint) {
                    PlacedFeature pf = data.features().get(l1);
                    String key = registry.getKey(pf).getPath();
                    if (System.getProperty("debugKeys") != null) {
                        System.out.println("step " + k + " index " + l1 + " " + key + " allowed " + (allowed == null || allowed.contains(key)));
                    }
                    if (allowed != null && !allowed.contains(key)) {
                        continue;
                    }
                    worldgenrandom.setFeatureSeed(i, l1, k);
                    pf.placeWithBiomeCheck(region, world.generator, worldgenrandom, blockpos);
                }
            }
        }
    }

    static String stateName(BlockState s) {
        StringBuilder b = new StringBuilder(Registry.BLOCK.getKey(s.getBlock()).getPath());
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

    private static <T extends Comparable<T>> String value(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }

    static void writeTables(VanillaWorld world, File file) throws Exception {
        Registry<PlacedFeature> registry = VanillaWorld.access.registryOrThrow(Registry.PLACED_FEATURE_REGISTRY);
        try (PrintWriter p = new PrintWriter(file, "UTF-8")) {
            List<BiomeSource.StepFeatureData> list = world.biomeSource.featuresPerStep();
            for (int k = 0; k < list.size(); k++) {
                StringBuilder b = new StringBuilder("step " + k + " " + GenerationStep.Decoration.values()[k].name() + " :");
                for (PlacedFeature pf : list.get(k).features()) {
                    b.append(' ').append(registry.getKey(pf).getPath());
                }
                p.println(b);
            }
            for (int id = 0; id < world.holders.length; id++) {
                List<HolderSet<PlacedFeature>> f = world.holders[id].value().getGenerationSettings().features();
                for (int k = 0; k < f.size(); k++) {
                    StringBuilder b = new StringBuilder("biome " + id + " " + VanillaWorld.BIOME_NAMES.get(id) + " " + k + " :");
                    f.get(k).stream().forEach(h -> b.append(' ').append(registry.getKey(h.value()).getPath()));
                    p.println(b);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
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
        boolean tables = false;
        for (long seed : seeds) {
            VanillaWorld world = new VanillaWorld(seed);
            VanillaWorld.bindBiomeTags();
            VanillaStructureDump.coldSearches(world);
            if (!tables) {
                writeTables(world, new File(out, "feature_tables.txt"));
                tables = true;
            }
            long start = System.nanoTime();
            Map<Long, ProtoChunk> chunks = new HashMap<>();
            Random rnd = new Random(seed * 31L + 5L);
            int[][] centers = {{0, 0}, {rnd.nextInt(20000) - 10000, rnd.nextInt(20000) - 10000}};
            // -Dcenters=x,z/x,z/... replaces the two areas (chunk coordinates of each area's center).
            String given = System.getProperty("centers");
            if (given != null) {
                String[] parts = given.split("/");
                centers = new int[parts.length][];
                for (int c = 0; c < parts.length; c++) {
                    String[] xz = parts[c].split(",");
                    centers[c] = new int[]{Integer.parseInt(xz[0].trim()), Integer.parseInt(xz[1].trim())};
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
                Region region = region(world, chunks, p.x, p.z);
                ProtoChunk target = chunk(world, chunks, p.x, p.z);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        chunk(world, chunks, p.x + dx, p.z + dz);
                    }
                }
                long t = System.nanoTime();
                decorate(world, region, target);
                decorateNanos += System.nanoTime() - t;
            }
            System.out.println(String.format(java.util.Locale.ROOT, "seed %d: vanilla decoration %.3f ms/chunk", seed,
                decorateNanos / 1.0E6D / order.size()));
            Map<BlockState, Integer> palette = new LinkedHashMap<>();
            List<char[]> data = new ArrayList<>();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (ChunkPos p : order) {
                ProtoChunk c = chunks.get(p.toLong());
                char[] blocks = new char[16 * 16 * 384];
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 384; y++) {
                            BlockState s = c.getBlockState(m.set(p.getMinBlockX() + x, y - 64, p.getMinBlockZ() + z));
                            Integer id = palette.get(s);
                            if (id == null) {
                                id = palette.size();
                                palette.put(s, id);
                            }
                            blocks[(x * 16 + z) * 384 + y] = (char) (int) id;
                        }
                    }
                }
                data.add(blocks);
            }
            try (DataOutputStream w = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(
                    new FileOutputStream(new File(out, "features_" + seed + ".bin.gz")), 1 << 16), 1 << 16))) {
                w.writeInt(palette.size());
                for (BlockState s : palette.keySet()) {
                    w.writeUTF(stateName(s));
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
            System.out.println("seed " + seed + ": " + order.size() + " chunks decorated, " + chunks.size() + " generated, "
                + palette.size() + " states, " + (System.nanoTime() - start) / 1000000L + " ms");
        }
    }
}
