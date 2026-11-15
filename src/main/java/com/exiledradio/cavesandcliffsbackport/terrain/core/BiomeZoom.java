package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * BiomeManager.getBiome over a BiomeSource's stored biomes: the block-scale biome at a position, as 1.18's F3 screen,
 * mob spawning and the client's colors see it. The same zoom as Decorator.zoomedBiome and SurfaceBuilder's. Used by
 * the 1.12 chunk generator for each column's 1.12 biome (the biome at the column's top block). One per thread, with
 * that thread's BiomeSource.
 */
public final class BiomeZoom {
    private final long zoomSeed;
    private final Quarts biomes;

    /** Stored biomes by quart: BiomeSource.stored, or a chunk store kept elsewhere (the same numbers). */
    public interface Quarts {
        int stored(int qx, int qy, int qz);
    }

    public BiomeZoom(OverworldNoise noise, BiomeSource biomes) {
        this(noise.seed, biomes::stored);
    }

    /** Over stored biomes kept by the caller, for the world seed. */
    public BiomeZoom(long seed, Quarts biomes) {
        this(biomes, SurfaceBuilder.obfuscateSeed(seed));
    }

    private BiomeZoom(Quarts biomes, long zoomSeed) {
        this.zoomSeed = zoomSeed;
        this.biomes = biomes;
    }

    /** Over stored biomes, for a zoom seed ({@link #zoomSeed}) rather than the world seed: a client's. */
    public static BiomeZoom ofZoomSeed(long zoomSeed, Quarts biomes) {
        return new BiomeZoom(biomes, zoomSeed);
    }

    /**
     * BiomeManager.obfuscateSeed: the seed's SHA-256, what 1.18's server sends its clients for this zoom (in the
     * login and respawn packets) in place of the seed.
     */
    public static long zoomSeed(long seed) {
        return SurfaceBuilder.obfuscateSeed(seed);
    }

    /** The biome id at block x, y, z. */
    public int biome(int x, int y, int z) {
        int i = x - 2;
        int j = y - 2;
        int k = z - 2;
        int l = i >> 2;
        int i1 = j >> 2;
        int j1 = k >> 2;
        Quarts s = biomes;
        int b = s.stored(l, i1, j1);
        if (s.stored(l + 1, i1, j1) == b && s.stored(l, i1 + 1, j1) == b && s.stored(l + 1, i1 + 1, j1) == b
                && s.stored(l, i1, j1 + 1) == b && s.stored(l + 1, i1, j1 + 1) == b && s.stored(l, i1 + 1, j1 + 1) == b
                && s.stored(l + 1, i1 + 1, j1 + 1) == b) {
            return b;
        }
        double d0 = (double) (i & 3) / 4.0D;
        double d1 = (double) (j & 3) / 4.0D;
        double d2 = (double) (k & 3) / 4.0D;
        int k1 = 0;
        double d3 = Double.POSITIVE_INFINITY;
        for (int l1 = 0; l1 < 8; ++l1) {
            boolean flag = (l1 & 4) == 0;
            boolean flag1 = (l1 & 2) == 0;
            boolean flag2 = (l1 & 1) == 0;
            double d7 = SurfaceBuilder.fiddledDistance(zoomSeed, flag ? l : l + 1, flag1 ? i1 : i1 + 1, flag2 ? j1 : j1 + 1,
                flag ? d0 : d0 - 1.0D, flag1 ? d1 : d1 - 1.0D, flag2 ? d2 : d2 - 1.0D);
            if (d3 > d7) {
                k1 = l1;
                d3 = d7;
            }
        }
        return biomes.stored((k1 & 4) == 0 ? l : l + 1, (k1 & 2) == 0 ? i1 : i1 + 1, (k1 & 1) == 0 ? j1 : j1 + 1);
    }
}
