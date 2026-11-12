package net.minecraft.world.level.levelgen;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * One seed's vanilla 1.18.2 overworld for the oracle tools: the generator, its biome source answering from a cold
 * search (the RTree's per-thread last result cleared before every query, which is what the Caves and Cliffs
 * Backport's Climate answers; see its comment on ties), real chunks through noise fill and surface, and the
 * block codes the backport's terrain core uses (docs/TERRAIN_CORE.md).
 */
final class VanillaWorld {
    static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(-64, 384);
    static RegistryAccess access;
    static Map<BlockState, Byte> codes;
    static BlockState[] byCode;
    /** Biome names in Biomes.java declaration order: the backport's biome ids. */
    static final List<String> BIOME_NAMES = new ArrayList<>();

    final long seed;
    final NoiseBasedChunkGenerator generator;
    final NoiseRouter router;
    final NoiseGeneratorSettings settings;
    final Aquifer.FluidPicker picker;
    final MultiNoiseBiomeSource biomeSource;
    final Climate.Sampler sampler;
    final Registry<Biome> biomes;
    final SurfaceSystem surfaceSystem;
    private final ThreadLocal<?> lastResult;
    final int leafCount;
    final Map<Biome, Integer> biomeIds = new IdentityHashMap<>();
    final Holder<Biome>[] holders;

    static synchronized void boot() throws Exception {
        if (access != null) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        access = RegistryAccess.builtinCopy();
        Block[] blocks = {Blocks.AIR, Blocks.STONE, Blocks.WATER, Blocks.LAVA, Blocks.COPPER_ORE, Blocks.RAW_COPPER_BLOCK,
            Blocks.GRANITE, Blocks.DEEPSLATE_IRON_ORE, Blocks.RAW_IRON_BLOCK, Blocks.TUFF, Blocks.BEDROCK, Blocks.DEEPSLATE,
            Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.MYCELIUM, Blocks.CALCITE,
            Blocks.GRAVEL, Blocks.SAND, Blocks.SANDSTONE, Blocks.RED_SAND, Blocks.RED_SANDSTONE, Blocks.PACKED_ICE,
            Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW, Blocks.ICE, Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA,
            Blocks.ORANGE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.RED_TERRACOTTA,
            Blocks.LIGHT_GRAY_TERRACOTTA};
        codes = new IdentityHashMap<>();
        byCode = new BlockState[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            codes.put(blocks[i].defaultBlockState(), (byte) i);
            byCode[i] = blocks[i].defaultBlockState();
        }
        for (Field f : net.minecraft.world.level.biome.Biomes.class.getDeclaredFields()) {
            if (f.getType() == ResourceKey.class) {
                ResourceKey<?> key = (ResourceKey<?>) f.get(null);
                BIOME_NAMES.add(key.location().getPath());
            }
        }
    }

    @SuppressWarnings("unchecked")
    VanillaWorld(long seed) throws Exception {
        boot();
        this.seed = seed;
        generator = WorldGenSettings.makeDefaultOverworld(access, seed);
        router = generator.router();
        settings = generator.settings.value();
        Field pickerField = NoiseBasedChunkGenerator.class.getDeclaredField("globalFluidPicker");
        pickerField.setAccessible(true);
        picker = (Aquifer.FluidPicker) pickerField.get(generator);
        Field surfaceField = NoiseBasedChunkGenerator.class.getDeclaredField("surfaceSystem");
        surfaceField.setAccessible(true);
        surfaceSystem = (SurfaceSystem) surfaceField.get(generator);
        biomeSource = (MultiNoiseBiomeSource) generator.getBiomeSource();
        sampler = generator.climateSampler();
        biomes = access.registryOrThrow(Registry.BIOME_REGISTRY);
        Field parametersField = MultiNoiseBiomeSource.class.getDeclaredField("parameters");
        parametersField.setAccessible(true);
        Climate.ParameterList<?> parameters = (Climate.ParameterList<?>) parametersField.get(biomeSource);
        leafCount = parameters.values().size();
        Field indexField = Climate.ParameterList.class.getDeclaredField("index");
        indexField.setAccessible(true);
        Object tree = indexField.get(parameters);
        Field lastField = tree.getClass().getDeclaredField("lastResult");
        lastField.setAccessible(true);
        lastResult = (ThreadLocal<?>) lastField.get(tree);
        holders = new Holder[BIOME_NAMES.size()];
        for (int i = 0; i < BIOME_NAMES.size(); i++) {
            ResourceKey<Biome> key = ResourceKey.create(Registry.BIOME_REGISTRY,
                new net.minecraft.resources.ResourceLocation(BIOME_NAMES.get(i)));
            Holder<Biome> holder = biomes.getHolderOrThrow(key);
            holders[i] = holder;
            biomeIds.put(holder.value(), i);
        }
    }

    int id(Holder<Biome> holder) {
        Integer id = biomeIds.get(holder.value());
        if (id == null) {
            throw new IllegalStateException("unknown biome " + holder);
        }
        return id;
    }

    /** getNoiseBiome from a cold search. */
    Holder<Biome> coldBiome(int qx, int qy, int qz) {
        lastResult.remove();
        return biomeSource.getNoiseBiome(qx, qy, qz, sampler);
    }

    /** getNoiseBiome as a worker thread does it, from its previous answer. */
    Holder<Biome> warmBiome(int qx, int qy, int qz) {
        return biomeSource.getNoiseBiome(qx, qy, qz, sampler);
    }

    static void writeBiomeNames(DataOutputStream w) throws IOException {
        w.writeInt(BIOME_NAMES.size());
        for (String name : BIOME_NAMES) {
            w.writeUTF(name);
        }
    }

    /** A chunk through noise fill (vanilla's NoiseChunk, written into a ProtoChunk). Its biomes are cold. */
    ProtoChunk filledChunk(int cx, int cz, byte[] scratch) {
        ChunkPos pos = new ChunkPos(cx, cz);
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, HEIGHT, biomes, null);
        chunk.fillBiomesFromNoise((x, y, z, s) -> coldBiome(x, y, z), sampler);
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(router, () -> DensityFunctions.BeardifierMarker.INSTANCE,
            settings, picker, Blender.empty());
        VanillaFillBench.fill(noiseChunk, pos, codes, scratch);
        net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int at = (x * 16 + z) * 384;
                for (int y = 0; y < 384; y++) {
                    byte code = scratch[at + y];
                    if (code != 0) {
                        chunk.setBlockState(p.set(minX + x, y - 64, minZ + z), byCode[code], false);
                    }
                }
            }
        }
        return chunk;
    }

    /**
     * NoiseBasedChunkGenerator.buildSurface on a filled chunk. Biome look-ups go to stored biomes (the chunk's
     * and, past its edge, its neighbors', which in vanilla are at BIOMES or later), with ChunkAccess.getNoiseBiome's
     * clamp of quart y to the world's height.
     */
    void buildSurface(ProtoChunk chunk) {
        int minQ = QuartPos.fromBlock(HEIGHT.getMinBuildHeight());
        int maxQ = minQ + QuartPos.fromBlock(HEIGHT.getHeight()) - 1;
        BiomeManager manager = new BiomeManager((qx, qy, qz) -> coldBiome(qx, Mth.clamp(qy, minQ, maxQ), qz),
            BiomeManager.obfuscateSeed(seed));
        WorldGenerationContext context = new WorldGenerationContext(generator, HEIGHT);
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(router, () -> DensityFunctions.BeardifierMarker.INSTANCE,
            settings, picker, Blender.empty());
        surfaceSystem.buildSurface(manager, biomes, settings.useLegacyRandomSource(), context, chunk, noiseChunk,
            settings.surfaceRule());
    }

    /**
     * NoiseBasedChunkGenerator.applyCarvers (GenerationStep.Carving.AIR) on a chunk after surface, with the
     * region's look-ups answered directly: a start chunk's carver biome from the biome source at quart y 0, the
     * biome manager over the biome source (no clamp), the world's obfuscated seed.
     */
    final Map<Long, Holder<Biome>> carverBiomes = new java.util.HashMap<>();

    void carve(ProtoChunk chunk) {
        BiomeManager manager = new BiomeManager((qx, qy, qz) -> coldBiome(qx, qy, qz), BiomeManager.obfuscateSeed(seed));
        net.minecraft.world.level.levelgen.WorldgenRandom random = new net.minecraft.world.level.levelgen.WorldgenRandom(
            new net.minecraft.world.level.levelgen.LegacyRandomSource(0L));
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(router, () -> DensityFunctions.BeardifierMarker.INSTANCE,
            settings, picker, Blender.empty());
        Aquifer aquifer = noiseChunk.aquifer();
        net.minecraft.world.level.levelgen.carver.CarvingContext context =
            new net.minecraft.world.level.levelgen.carver.CarvingContext(generator, access, HEIGHT, noiseChunk);
        net.minecraft.world.level.chunk.CarvingMask mask = chunk.getOrCreateCarvingMask(GenerationStep.Carving.AIR);
        ChunkPos pos = chunk.getPos();
        for (int j = -8; j <= 8; ++j) {
            for (int k = -8; k <= 8; ++k) {
                ChunkPos start = new ChunkPos(pos.x + j, pos.z + k);
                // ChunkAccess.carverBiome: computed once per chunk and kept.
                Holder<Biome> biome = carverBiomes.computeIfAbsent(start.toLong(),
                    x -> coldBiome(QuartPos.fromBlock(start.getMinBlockX()), 0, QuartPos.fromBlock(start.getMinBlockZ())));
                int l = 0;
                for (Holder<net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver<?>> holder
                        : biome.value().getGenerationSettings().getCarvers(GenerationStep.Carving.AIR)) {
                    net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver<?> carver = holder.value();
                    random.setLargeFeatureSeed(seed + (long) l, start.x, start.z);
                    if (carver.isStartChunk(random)) {
                        carver.carve(context, chunk, manager::getBiome, random, aquifer, start, mask);
                    }
                    ++l;
                }
            }
        }
    }

    /** The carver lists of every overworld biome, one line per distinct list (the core assumes one). */
    String carverLists() {
        Map<String, List<String>> lists = new java.util.TreeMap<>();
        for (Holder<Biome> h : holders) {
            StringBuilder b = new StringBuilder();
            for (Holder<net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver<?>> c
                    : h.value().getGenerationSettings().getCarvers(GenerationStep.Carving.AIR)) {
                b.append(c.unwrapKey().map(k -> k.location().getPath()).orElse("?")).append(' ');
            }
            lists.computeIfAbsent(b.toString(), x -> new ArrayList<>()).add(h.unwrapKey().get().location().getPath());
        }
        return lists.toString();
    }

    /** A hash of Mth's sine table, as McMath.sinTableHash. */
    static long sinTableHash() throws Exception {
        Field f = Mth.class.getDeclaredField("SIN");
        f.setAccessible(true);
        float[] table = (float[]) f.get(null);
        long h = 1L;
        for (float v : table) {
            h = h * 31L + Float.floatToIntBits(v);
        }
        return h;
    }

    /** The chunk's blocks as codes, index (x * 16 + z) * 384 + y + 64. */
    static void read(ProtoChunk chunk, byte[] out) {
        net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
        ChunkPos pos = chunk.getPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int at = (x * 16 + z) * 384;
                for (int y = 0; y < 384; y++) {
                    BlockState state = chunk.getBlockState(p.set(pos.getMinBlockX() + x, y - 64, pos.getMinBlockZ() + z));
                    Byte code = codes.get(state);
                    if (code == null) {
                        throw new IllegalStateException("no code for " + state);
                    }
                    out[at + y] = code;
                }
            }
        }
    }
}
