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

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;

/**
 * A larger set of vanilla 1.18.2 noise-fill chunks through the real NoiseChunk (VanillaFillBench's loop), in
 * Oracle1182Terrain's chunks_SEED.bin format, under the name bigchunks_SEED.bin: per seed, side x side chunks
 * around 0,0, side x side around a far point, and scattered single chunks out to 30 million blocks.
 *
 * Arguments: outDir side scattered [seeds...]
 */
public final class VanillaFillDump {
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
        byte[] blocks = new byte[16 * 16 * 384];
        for (long seed : seeds) {
            NoiseBasedChunkGenerator generator = WorldGenSettings.makeDefaultOverworld(access, seed);
            NoiseRouter router = generator.router();
            NoiseGeneratorSettings settings = generator.settings.value();
            Aquifer.FluidPicker picker = (Aquifer.FluidPicker) pickerField.get(generator);
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
                    ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, height,
                        access.registryOrThrow(Registry.BIOME_REGISTRY), null);
                    NoiseChunk noiseChunk = NoiseChunk.forChunk(chunk, router,
                        () -> DensityFunctions.BeardifierMarker.INSTANCE, settings, picker, Blender.empty());
                    VanillaFillBench.fill(noiseChunk, pos, codes, blocks);
                    w.writeInt(cx);
                    w.writeInt(cz);
                    w.write(blocks);
                }
            }
            System.out.println("seed " + seed + " done: " + count + " chunks in "
                + (System.nanoTime() - start) / 1000000L + " ms");
        }
    }
}
