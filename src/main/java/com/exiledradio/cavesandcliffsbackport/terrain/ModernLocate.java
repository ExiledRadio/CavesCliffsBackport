package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.backportcore.locate.BiomeLocate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;

import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;

/**
 * {@code /locate biome} in the 1.18 terrain, through Backport Core's biome search: the cave biomes (dripstone caves,
 * lush caves, the deep dark, sulfur caves) where 1.18's noise puts them in three dimensions, as 26.x's
 * {@code findClosestBiome3d} reads {@code getNoiseBiome} at each quart it asks; every other biome from the world's own
 * biome provider (the surface map, other mods' biomes in it included), through the sources before this one.
 *
 * <p>Without it the search only had the surface map: no cave biome could be found in a Caves &amp; Cliffs world. Each
 * search gets its own {@link BiomeSource}, so it shares no cache with the world's generator threads.</p>
 */
public final class ModernLocate {

    private ModernLocate() {
    }

    public static void register() {
        BiomeLocate.id("minecraft:dripstone_caves");
        BiomeLocate.id("minecraft:lush_caves");
        BiomeLocate.source(new BiomeLocate.SamplerFactory() {
            @Nullable
            @Override
            public BiomeLocate.Sampler forWorld(WorldServer world) {
                return sampler(this, world);
            }
        });
    }

    @Nullable
    static BiomeLocate.Sampler sampler(BiomeLocate.SamplerFactory self, WorldServer world) {
        Object generator = world.getChunkProvider().chunkGenerator;
        if (!(generator instanceof ModernGenerator)) {
            return null;
        }
        ModernTerrain terrain = ((ModernGenerator) generator).terrain();
        BiomeSource source = new BiomeSource(terrain.noise, new ColumnCache(terrain.noise), terrain.climate);
        BiomeLocate.Sampler surface = BiomeLocate.next(self, world);
        return (x, y, z) -> {
            int b = source.biome(x >> 2, y >> 2, z >> 2);
            return cave(b) ? "minecraft:" + Biomes.name(b) : surface.biome(x, y, z);
        };
    }

    /** @return whether 1.18's noise biome is one under the ground, which the surface map never shows */
    static boolean cave(int biome) {
        return biome == Biomes.DRIPSTONE_CAVES || biome == Biomes.LUSH_CAVES || biome == Biomes.DEEP_DARK
                || biome == Biomes.SULFUR_CAVES;
    }
}
