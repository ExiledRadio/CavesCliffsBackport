package net.minecraft.world.level.levelgen;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
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
 * Vanilla 1.21.11's noise fill (NoiseBasedChunkGenerator.doFill, no structures) in the 1.18.2 oracle's
 * bigchunks_SEED.bin format, for TerrainCheck -Deditions=7 (Caves and Cliffs Backport's 1.21.4 terrain).
 * Arguments: outDir side scattered [seeds...]
 */
public final class FillDump12111 {
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
        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        var settingsHolder = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var biomeLookup = lookup.lookupOrThrow(Registries.BIOME);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(
                net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromList(
                        new net.minecraft.world.level.biome.Climate.ParameterList<>(java.util.List.of(
                                com.mojang.datafixers.util.Pair.of(net.minecraft.world.level.biome.Climate.parameters(0, 0, 0, 0, 0, 0, 0),
                                        (net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>) biomeLookup.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS))))),
                settingsHolder);
        net.minecraft.core.MappedRegistry<net.minecraft.world.level.biome.Biome> biomes = new net.minecraft.core.MappedRegistry<>(
                Registries.BIOME, com.mojang.serialization.Lifecycle.stable());
        biomes.register(net.minecraft.world.level.biome.Biomes.PLAINS, biomeLookup.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS).value(),
                net.minecraft.core.RegistrationInfo.BUILT_IN);
        Field pickerField = NoiseBasedChunkGenerator.class.getDeclaredField("globalFluidPicker");
        pickerField.setAccessible(true);
        Aquifer.FluidPicker picker = ((java.util.function.Supplier<Aquifer.FluidPicker>) pickerField.get(generator)).get();
        Method doFill = NoiseBasedChunkGenerator.class.getDeclaredMethod("doFill", Blender.class,
                net.minecraft.world.level.StructureManager.class, RandomState.class, ChunkAccess.class, int.class, int.class);
        doFill.setAccessible(true);
        Field noiseChunkField = ChunkAccess.class.getDeclaredField("noiseChunk");
        noiseChunkField.setAccessible(true);
        NoiseGeneratorSettings settings = settingsHolder.value();
        byte[] blocks = new byte[16 * 16 * 384];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (long seed : seeds) {
            RandomState random = RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), seed);
            Random rnd = new Random(seed * 131L + 3L);
            int farX = rnd.nextInt(40000) - 20000;
            int farZ = rnd.nextInt(40000) - 20000;
            int count = side * side * 2 + scattered;
            long start = System.nanoTime();
            // marks_SEED.bin: per chunk int cx, int cz, int n, n * (int x, int y, int z): the fluids doFill marked for
            // post-processing (aquifer.shouldScheduleFluidUpdate), for FluidMarkCheck.
            try (DataOutputStream w = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(new File(out, "bigchunks_" + seed + ".bin")), 1 << 20));
                 DataOutputStream mw = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(new File(out, "marks_" + seed + ".bin")), 1 << 20))) {
                w.writeInt(count);
                mw.writeInt(count);
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
                    ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, height, net.minecraft.world.level.chunk.PalettedContainerFactory.create(new net.minecraft.core.RegistryAccess.ImmutableRegistryAccess(java.util.List.of(biomes))), null);
                    noiseChunkField.set(chunk, NoiseChunk.forChunk(chunk, random, DensityFunctions.BeardifierMarker.INSTANCE,
                            settings, picker, Blender.empty()));
                    doFill.invoke(generator, Blender.empty(), null, random, chunk, -8, 48);
                    it.unimi.dsi.fastutil.shorts.ShortList[] post = chunk.getPostProcessing();
                    java.util.List<int[]> marks = new java.util.ArrayList<>();
                    for (int si = 0; si < post.length; si++) {
                        if (post[si] != null) {
                            for (short packed : post[si]) {
                                BlockPos at = ProtoChunk.unpackOffsetCoordinates(packed, chunk.getSectionYFromSectionIndex(si), pos);
                                marks.add(new int[]{at.getX(), at.getY(), at.getZ()});
                            }
                        }
                    }
                    mw.writeInt(cx);
                    mw.writeInt(cz);
                    mw.writeInt(marks.size());
                    for (int[] m : marks) {
                        mw.writeInt(m[0]);
                        mw.writeInt(m[1]);
                        mw.writeInt(m[2]);
                    }
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
