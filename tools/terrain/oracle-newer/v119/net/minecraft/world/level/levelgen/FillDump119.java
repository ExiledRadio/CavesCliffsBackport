package net.minecraft.world.level.levelgen;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Vanilla 1.19.2's noise fill (NoiseBasedChunkGenerator.doFill, no structures) in the 1.18.2 oracle's
 * bigchunks_SEED.bin format, for TerrainCheck -Deditions=1 (Caves and Cliffs Backport's 1.19 terrain).
 * Arguments: outDir side scattered [seeds...]
 */
public final class FillDump119 {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        int side = Integer.parseInt(args[1]);
        int scattered = Integer.parseInt(args[2]);
        long[] seeds = {42L, 12345L, -7346534567L, 8675309L};
        if (args.length > 3) {
            seeds = new long[args.length - 3];
            for (int i = 3; i < args.length; i++) {
                seeds[i - 3] = Long.parseLong(args[i]);
            }
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Map<BlockState, Byte> codes = new IdentityHashMap<>();
        codes.put(Blocks.AIR.defaultBlockState(), (byte) 0);
        codes.put(Blocks.STONE.defaultBlockState(), (byte) 1);
        codes.put(Blocks.WATER.defaultBlockState(), (byte) 2);
        codes.put(Blocks.LAVA.defaultBlockState(), (byte) 3);
        codes.put(Blocks.COPPER_ORE.defaultBlockState(), (byte) 4);
        codes.put(Blocks.RAW_COPPER_BLOCK.defaultBlockState(), (byte) 5);
        codes.put(Blocks.GRANITE.defaultBlockState(), (byte) 6);
        codes.put(Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(), (byte) 7);
        codes.put(Blocks.RAW_IRON_BLOCK.defaultBlockState(), (byte) 8);
        codes.put(Blocks.TUFF.defaultBlockState(), (byte) 9);
        LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
        var settingsHolder = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS,
                BuiltinRegistries.NOISE, MultiNoiseBiomeSource.Preset.OVERWORLD.biomeSource(BuiltinRegistries.BIOME),
                settingsHolder);
        Field pickerField = NoiseBasedChunkGenerator.class.getDeclaredField("globalFluidPicker");
        pickerField.setAccessible(true);
        Aquifer.FluidPicker picker = (Aquifer.FluidPicker) pickerField.get(generator);
        Method doFill = NoiseBasedChunkGenerator.class.getDeclaredMethod("doFill", Blender.class,
                net.minecraft.world.level.StructureManager.class, RandomState.class, ChunkAccess.class, int.class, int.class);
        doFill.setAccessible(true);
        Field noiseChunkField = ChunkAccess.class.getDeclaredField("noiseChunk");
        noiseChunkField.setAccessible(true);
        NoiseGeneratorSettings settings = settingsHolder.value();
        byte[] blocks = new byte[16 * 16 * 384];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (long seed : seeds) {
            RandomState random = RandomState.create(settings, BuiltinRegistries.NOISE, seed);
            Random rnd = new Random(seed * 131L + 3L);
            int farX = rnd.nextInt(40000) - 20000;
            int farZ = rnd.nextInt(40000) - 20000;
            int count = side * side * 2 + scattered;
            long start = System.nanoTime();
            try (DataOutputStream w = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(new File(out, "bigchunks_" + seed + ".bin")), 1 << 20))) {
                w.writeInt(count);
                for (int i = 0; i < count; i++) {
                    int cx;
                    int cz;
                    if (i < side * side) {
                        cx = i / side - side / 2;
                        cz = i % side - side / 2;
                    } else if (i < side * side * 2) {
                        int j = i - side * side;
                        cx = farX + j / side;
                        cz = farZ + j % side;
                    } else {
                        int span = i % 3 == 0 ? 2000 : i % 3 == 1 ? 60000 : 1870000;
                        cx = rnd.nextInt(span * 2) - span;
                        cz = rnd.nextInt(span * 2) - span;
                    }
                    ChunkPos pos = new ChunkPos(cx, cz);
                    ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, height, BuiltinRegistries.BIOME, null);
                    noiseChunkField.set(chunk, NoiseChunk.forChunk(chunk, random, DensityFunctions.BeardifierMarker.INSTANCE,
                            settings, picker, Blender.empty()));
                    doFill.invoke(generator, Blender.empty(), null, random, chunk, -8, 48);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = -64; y < 320; y++) {
                                Byte b = codes.get(chunk.getBlockState(p.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z)));
                                blocks[(x * 16 + z) * 384 + y + 64] = b == null ? (byte) 255 : b;
                            }
                        }
                    }
                    w.writeInt(cx);
                    w.writeInt(cz);
                    w.write(blocks);
                }
            }
            System.out.println("seed " + seed + " done: " + count + " chunks in " + (System.nanoTime() - start) / 1000000L + " ms");
        }
    }
}
