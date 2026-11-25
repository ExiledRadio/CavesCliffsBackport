package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Editions;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * 26.3's dappled forest's vegetal step in the 1.18 terrain ({@link Editions#GAME_DROPS}): Game Drops Backport's
 * {@code PoplarFeatures.vegetation} (its poplars, fallen poplars and spruces, brown mushrooms, red shrubs and grass),
 * called by name at population, where it has its world. It draws its positions in the square offset by 8 that 1.12
 * populates and checks the biome at each, so it runs for a chunk whose square has the dappled forest's biome in it.
 * The rest of the biome's features (ores, lakes, springs, glow lichen) are the terrain core's, by 26.3's list.
 */
final class DappledForest {

    private final Method vegetation;
    private final Biome biome;
    private final byte id;

    private DappledForest(Method vegetation, Biome biome) {
        this.vegetation = vegetation;
        this.biome = biome;
        this.id = (byte) Biome.getIdForBiome(biome);
    }

    /** Game Drops' vegetation, or null for a world without its edition (or a Game Drops without the method). */
    @Nullable
    static DappledForest of(int editions) {
        if ((editions & Editions.GAME_DROPS) == 0) {
            return null;
        }
        Biome biome = ForgeRegistries.BIOMES.getValue(new ResourceLocation("gamedropsbackport", "dappled_forest"));
        if (biome == null) {
            return null;
        }
        try {
            Method m = Class.forName("com.exiledradio.gamedropsbackport.poplar.PoplarFeatures")
                .getMethod("vegetation", World.class, Random.class, BlockPos.class, Biome.class);
            return new DappledForest(m, biome);
        } catch (ReflectiveOperationException | LinkageError e) {
            CavesAndCliffsBackport.LOGGER.warn("Could not reach Game Drops' dappled forest vegetation; its trees will "
                + "not grow in the 1.18 terrain.", e);
            return null;
        }
    }

    /** The vegetal step for population of chunk x, z (the square from x * 16 + 8, z * 16 + 8). */
    void populate(World world, int x, int z) {
        if (!holds(world, x, z) && !holds(world, x + 1, z) && !holds(world, x, z + 1) && !holds(world, x + 1, z + 1)) {
            return;
        }
        Random rand = new Random(world.getSeed() ^ ((long) x * 341873128712L + (long) z * 132897987541L) ^ 0x2603DFL);
        try {
            vegetation.invoke(null, world, rand, new BlockPos(x << 4, 0, z << 4), biome);
        } catch (ReflectiveOperationException | RuntimeException e) {
            CavesAndCliffsBackport.LOGGER.warn("Game Drops' dappled forest vegetation failed at chunk {}, {}.", x, z, e);
        }
    }

    private boolean holds(World world, int x, int z) {
        Chunk c = world.getChunkProvider().getLoadedChunk(x, z);
        if (c == null) {
            return false;
        }
        for (byte b : com.exiledradio.cavesandcliffsbackport.height.IdsCompat.biomeBytes(c)) {
            if (b == id) {
                return true;
            }
        }
        return false;
    }
}
