package net.minecraft.world.level.levelgen;

import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Vanilla 1.18.2's own noise fill, timed on one thread: NoiseBasedChunkGenerator.doFill's loop over a real
 * NoiseChunk (terrain, caves, aquifers, ore veins), with no structures, writing block codes to a byte array the
 * way the Caves and Cliffs Backport's TerrainBench does, so the two checksums and times compare directly.
 * In Mojang's package to reach NoiseChunk's protected parts.
 *
 * Arguments: [seed] [side] [rounds] [centerChunkX] [centerChunkZ]
 */
public final class VanillaFillBench {
    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        int side = args.length > 1 ? Integer.parseInt(args[1]) : 16;
        int rounds = args.length > 2 ? Integer.parseInt(args[2]) : 6;
        int centerX = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int centerZ = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess access = RegistryAccess.builtinCopy();
        Map<BlockState, Byte> codes = new IdentityHashMap<>();
        codes.put(Blocks.AIR.defaultBlockState(), (byte) 0);
        codes.put(Blocks.WATER.defaultBlockState(), (byte) 2);
        codes.put(Blocks.LAVA.defaultBlockState(), (byte) 3);
        codes.put(Blocks.COPPER_ORE.defaultBlockState(), (byte) 4);
        codes.put(Blocks.RAW_COPPER_BLOCK.defaultBlockState(), (byte) 5);
        codes.put(Blocks.GRANITE.defaultBlockState(), (byte) 6);
        codes.put(Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(), (byte) 7);
        codes.put(Blocks.RAW_IRON_BLOCK.defaultBlockState(), (byte) 8);
        codes.put(Blocks.TUFF.defaultBlockState(), (byte) 9);
        LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
        Field pickerField = NoiseBasedChunkGenerator.class.getDeclaredField("globalFluidPicker");
        pickerField.setAccessible(true);
        byte[] out = new byte[16 * 16 * 384];
        double[] ms = new double[rounds];
        long checksum = 0L;
        for (int round = 0; round <= rounds; round++) {
            NoiseBasedChunkGenerator generator = WorldGenSettings.makeDefaultOverworld(access, seed);
            NoiseRouter router = generator.router();
            NoiseGeneratorSettings settings = generator.settings.value();
            Aquifer.FluidPicker picker = (Aquifer.FluidPicker) pickerField.get(generator);
            long sum = 0L;
            long start = System.nanoTime();
            for (int i = 0; i < side * side; i++) {
                ChunkPos pos = new ChunkPos(centerX - side / 2 + i / side, centerZ - side / 2 + i % side);
                ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, height,
                    access.registryOrThrow(Registry.BIOME_REGISTRY), null);
                NoiseChunk noiseChunk = NoiseChunk.forChunk(chunk, router, () -> DensityFunctions.BeardifierMarker.INSTANCE,
                    settings, picker, Blender.empty());
                fill(noiseChunk, pos, codes, out);
                sum = sum * 31L + Arrays.hashCode(out);
            }
            long took = System.nanoTime() - start;
            checksum = sum;
            if (round > 0) {
                ms[round - 1] = took / 1.0E6D / (side * side);
            }
        }
        Arrays.sort(ms);
        double median = ms[rounds / 2];
        System.out.println(String.format(java.util.Locale.ROOT,
            "VANILLA 1.18.2 seed %d, %dx%d chunks at %d,%d, java %s: median %.3f ms/chunk (%.1f chunks/s), best %.3f ms/chunk, checksum %016x",
            seed, side, side, centerX, centerZ, System.getProperty("java.version"), median, 1000.0D / median, ms[0],
            checksum));
    }

    /** NoiseBasedChunkGenerator.doFill's loop. */
    static void fill(NoiseChunk noiseChunk, ChunkPos pos, Map<BlockState, Byte> codes, byte[] out) {
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        noiseChunk.initializeForFirstCellX();
        int cellWidth = 4;
        int cellHeight = 8;
        int minCellY = -8;
        int cellCountY = 48;
        for (int cellX = 0; cellX < 4; cellX++) {
            noiseChunk.advanceCellX(cellX);
            for (int cellZ = 0; cellZ < 4; cellZ++) {
                for (int cellY = cellCountY - 1; cellY >= 0; cellY--) {
                    noiseChunk.selectCellYZ(cellY, cellZ);
                    for (int dy = cellHeight - 1; dy >= 0; dy--) {
                        int y = (minCellY + cellY) * cellHeight + dy;
                        noiseChunk.updateForY(y, (double) dy / (double) cellHeight);
                        for (int dx = 0; dx < cellWidth; dx++) {
                            int x = minX + cellX * cellWidth + dx;
                            noiseChunk.updateForX(x, (double) dx / (double) cellWidth);
                            for (int dz = 0; dz < cellWidth; dz++) {
                                int z = minZ + cellZ * cellWidth + dz;
                                noiseChunk.updateForZ(z, (double) dz / (double) cellWidth);
                                BlockState state = noiseChunk.getInterpolatedState();
                                byte code = 1;
                                if (state != null) {
                                    code = codes.get(state);
                                }
                                out[((x & 15) * 16 + (z & 15)) * 384 + (y + 64)] = code;
                            }
                        }
                    }
                }
            }
            noiseChunk.swapSlices();
        }
        noiseChunk.stopInterpolation();
    }
}
