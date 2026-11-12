package net.minecraft.world.level.levelgen;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla 1.18.2's biome and surface steps timed on one thread, the way a worker thread runs them:
 * NoiseBasedChunkGenerator.createBiomes (the chunk's NoiseChunk and fillBiomesFromNoise through its cached
 * climate sampler, warm search) and buildSurface (SurfaceSystem with the overworld rules, biomes read from the
 * stored biomes of the chunk and its neighbors as WorldGenRegion does). The noise fill in between is done but not
 * timed (VanillaFillBench times it). The checksum is over the blocks after surface.
 *
 * Arguments: [seed] [side] [rounds] [centerChunkX] [centerChunkZ]
 */
public final class VanillaSurfaceBench {
    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        int side = args.length > 1 ? Integer.parseInt(args[1]) : 12;
        int rounds = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        int centerX = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int centerZ = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        double[] biomeMs = new double[rounds];
        double[] surfaceMs = new double[rounds];
        long checksum = 0L;
        byte[] blocks = new byte[16 * 16 * 384];
        int minQ = QuartPos.fromBlock(VanillaWorld.HEIGHT.getMinBuildHeight());
        int maxQ = minQ + QuartPos.fromBlock(VanillaWorld.HEIGHT.getHeight()) - 1;
        for (int round = 0; round <= rounds; round++) {
            VanillaWorld world = new VanillaWorld(seed);
            int x0 = centerX - side / 2;
            int z0 = centerZ - side / 2;
            Map<Long, ProtoChunk> chunks = new HashMap<>();
            long biomeTime = 0L;
            for (int cx = x0 - 1; cx <= x0 + side; cx++) {
                for (int cz = z0 - 1; cz <= z0 + side; cz++) {
                    boolean inner = cx >= x0 && cx < x0 + side && cz >= z0 && cz < z0 + side;
                    long t = System.nanoTime();
                    ProtoChunk chunk = new ProtoChunk(new ChunkPos(cx, cz), UpgradeData.EMPTY, VanillaWorld.HEIGHT,
                        world.biomes, null);
                    NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(world.router,
                        () -> DensityFunctions.BeardifierMarker.INSTANCE, world.settings, world.picker, Blender.empty());
                    chunk.fillBiomesFromNoise(world.biomeSource::getNoiseBiome, noiseChunk.cachedClimateSampler(world.router));
                    if (inner) {
                        biomeTime += System.nanoTime() - t;
                    }
                    chunks.put(ChunkPos.asLong(cx, cz), chunk);
                }
            }
            BiomeManager manager = new BiomeManager((qx, qy, qz) -> {
                ProtoChunk c = chunks.get(ChunkPos.asLong(QuartPos.toSection(qx), QuartPos.toSection(qz)));
                return c != null ? c.getNoiseBiome(qx, qy, qz) : world.warmBiome(qx, Mth.clamp(qy, minQ, maxQ), qz);
            }, BiomeManager.obfuscateSeed(seed));
            WorldGenerationContext context = new WorldGenerationContext(world.generator, VanillaWorld.HEIGHT);
            long surfaceTime = 0L;
            long sum = 0L;
            for (int i = 0; i < side * side; i++) {
                int cx = x0 + i / side;
                int cz = z0 + i % side;
                ProtoChunk chunk = chunks.get(ChunkPos.asLong(cx, cz));
                fill(world, chunk, blocks);
                long t = System.nanoTime();
                NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(world.router,
                    () -> DensityFunctions.BeardifierMarker.INSTANCE, world.settings, world.picker, Blender.empty());
                world.surfaceSystem.buildSurface(manager, world.biomes, world.settings.useLegacyRandomSource(), context,
                    chunk, noiseChunk, world.settings.surfaceRule());
                surfaceTime += System.nanoTime() - t;
                VanillaWorld.read(chunk, blocks);
                sum = sum * 31L + Arrays.hashCode(blocks);
            }
            checksum = sum;
            if (round > 0) {
                biomeMs[round - 1] = biomeTime / 1.0E6D / (side * side);
                surfaceMs[round - 1] = surfaceTime / 1.0E6D / (side * side);
            }
        }
        Arrays.sort(biomeMs);
        Arrays.sort(surfaceMs);
        System.out.println(String.format(java.util.Locale.ROOT,
            "VANILLA 1.18.2 seed %d, %dx%d chunks at %d,%d, java %s: biomes median %.3f ms/chunk, surface median %.3f ms/chunk, checksum %016x",
            seed, side, side, centerX, centerZ, System.getProperty("java.version"), biomeMs[rounds / 2],
            surfaceMs[rounds / 2], checksum));
    }

    private static void fill(VanillaWorld world, ProtoChunk chunk, byte[] blocks) {
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(world.router,
            () -> DensityFunctions.BeardifierMarker.INSTANCE, world.settings, world.picker, Blender.empty());
        ChunkPos pos = chunk.getPos();
        VanillaFillBench.fill(noiseChunk, pos, VanillaWorld.codes, blocks);
        net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int at = (x * 16 + z) * 384;
                for (int y = 0; y < 384; y++) {
                    byte code = blocks[at + y];
                    if (code != 0) {
                        chunk.setBlockState(p.set(pos.getMinBlockX() + x, y - 64, pos.getMinBlockZ() + z),
                            VanillaWorld.byCode[code], false);
                    }
                }
            }
        }
    }
}
