package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.HeightContent;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * EXTENDED_HEIGHT section 4, "height alone": generates a fixed square of chunks (generation and population) and
 * writes each to NBT as a save would, and logs ms per chunk. Runs only when named ({@code -Pselftest=heightbench});
 * {@code tools/height/bench.sh} runs it with the coremod off, on in a Default world, and on in a Caves & Cliffs world.
 */
public final class HeightBenchSelfTest implements SelfTest {

    /** The square's side in populated chunks, and where it starts (in chunks, far from spawn). */
    private static final int SIDE = 24;
    private static final int START = 1000;

    @Override
    public String name() {
        return "heightbench";
    }

    @Override
    public void run(Check check) throws Exception {
        String asked = System.getProperty(CavesAndCliffsBackport.MOD_ID + ".selftest", "");
        if (!java.util.Arrays.asList(asked.split(",")).contains(name())) {
            return;
        }
        WorldServer world = check.world;
        // Warm-up: the JIT, and every class the generators load.
        square(world, START - 200, 8);
        long t0 = System.nanoTime();
        List<Chunk> chunks = square(world, START, SIDE);
        long gen = System.nanoTime() - t0;

        AnvilChunkLoader loader = (AnvilChunkLoader) world.getChunkProvider().chunkLoader;
        Method write = ObfuscationReflectionHelper.findMethod(AnvilChunkLoader.class, "func_75820_a", void.class,
                Chunk.class, World.class, NBTTagCompound.class);
        for (int i = 0; i < 64; i++) {
            write.invoke(loader, chunks.get(i), world, new NBTTagCompound());
        }
        long t1 = System.nanoTime();
        for (int round = 0; round < 4; round++) {
            for (Chunk c : chunks) {
                write.invoke(loader, c, world, new NBTTagCompound());
            }
        }
        long save = (System.nanoTime() - t1) / 4;
        int n = SIDE * SIDE;
        boolean modern = com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain.owns(world);
        String mode = !HeightContent.allowed() ? "vanilla (coremod off)" : modern ? "1.18 terrain"
                : Heights.isExtended(world) ? "extended" : "coremod on, Default world";
        if (modern) {
            CavesAndCliffsBackport.LOGGER.info("TERRAINSTATS " + ((com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator)
                    world.getChunkProvider().chunkGenerator).stats());
        }
        CavesAndCliffsBackport.LOGGER.info(String.format(java.util.Locale.ROOT,
                "HEIGHTBENCH mode=%s chunks=%d gen_ms_per_chunk=%.3f save_ms_per_chunk=%.3f chunks_per_s=%.1f",
                mode, n, gen / 1e6 / n, save / 1e6 / n, n / (gen / 1e9)));
        check.ok(true, "benchmark ran");
    }

    /** Provides (and so generates and populates) the side x side square starting at chunk (start, start). */
    private static List<Chunk> square(WorldServer world, int start, int side) {
        List<Chunk> out = new ArrayList<Chunk>();
        // One extra row and column, so every chunk of the square is populated.
        for (int x = start; x <= start + side; x++) {
            for (int z = start; z <= start + side; z++) {
                Chunk c = world.getChunkProvider().provideChunk(x, z);
                if (x < start + side && z < start + side) {
                    out.add(c);
                }
            }
        }
        return out;
    }
}
