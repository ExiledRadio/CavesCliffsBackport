package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.Map;

/**
 * 1.18's biomes as 1.12 biomes: what a column's biome array holds (mob spawning, grass and water colors, rain and snow)
 * and what the biome provider answers before a chunk exists (1.12 structures, the spawn point).
 *
 * <p>A biome some installed mod registers under the 1.18 name (the family's backports register 1.13+ biomes under
 * their modern names, any namespace) is taken first; otherwise the closest 1.12 biome below. The 1.18 mountain biomes
 * map to 1.12's mountains (goats spawn there), the warm oceans to the ocean.
 */
public final class BiomeMap {

    private static final String[] LEGACY = new String[Biomes.COUNT];

    private static void m(int modern, String legacy) {
        LEGACY[modern] = legacy;
    }

    static {
        m(Biomes.THE_VOID, "void");
        m(Biomes.PLAINS, "plains");
        m(Biomes.SUNFLOWER_PLAINS, "mutated_plains");
        m(Biomes.SNOWY_PLAINS, "ice_flats");
        m(Biomes.ICE_SPIKES, "mutated_ice_flats");
        m(Biomes.DESERT, "desert");
        m(Biomes.SWAMP, "swampland");
        m(Biomes.FOREST, "forest");
        m(Biomes.FLOWER_FOREST, "mutated_forest");
        m(Biomes.BIRCH_FOREST, "birch_forest");
        m(Biomes.DARK_FOREST, "roofed_forest");
        m(Biomes.OLD_GROWTH_BIRCH_FOREST, "mutated_birch_forest");
        m(Biomes.OLD_GROWTH_PINE_TAIGA, "redwood_taiga");
        m(Biomes.OLD_GROWTH_SPRUCE_TAIGA, "mutated_redwood_taiga");
        m(Biomes.TAIGA, "taiga");
        m(Biomes.SNOWY_TAIGA, "taiga_cold");
        m(Biomes.SAVANNA, "savanna");
        m(Biomes.SAVANNA_PLATEAU, "savanna_rock");
        m(Biomes.WINDSWEPT_HILLS, "extreme_hills");
        m(Biomes.WINDSWEPT_GRAVELLY_HILLS, "mutated_extreme_hills");
        m(Biomes.WINDSWEPT_FOREST, "extreme_hills_with_trees");
        m(Biomes.WINDSWEPT_SAVANNA, "mutated_savanna");
        m(Biomes.JUNGLE, "jungle");
        m(Biomes.SPARSE_JUNGLE, "jungle_edge");
        m(Biomes.BAMBOO_JUNGLE, "jungle");
        m(Biomes.BADLANDS, "mesa");
        m(Biomes.ERODED_BADLANDS, "mutated_mesa");
        m(Biomes.WOODED_BADLANDS, "mesa_rock");
        m(Biomes.MEADOW, "plains");
        m(Biomes.GROVE, "taiga_cold");
        m(Biomes.SNOWY_SLOPES, "ice_mountains");
        m(Biomes.FROZEN_PEAKS, "ice_mountains");
        m(Biomes.JAGGED_PEAKS, "ice_mountains");
        m(Biomes.STONY_PEAKS, "extreme_hills");
        m(Biomes.RIVER, "river");
        m(Biomes.FROZEN_RIVER, "frozen_river");
        m(Biomes.BEACH, "beaches");
        m(Biomes.SNOWY_BEACH, "cold_beach");
        m(Biomes.STONY_SHORE, "stone_beach");
        m(Biomes.WARM_OCEAN, "ocean");
        m(Biomes.LUKEWARM_OCEAN, "ocean");
        m(Biomes.DEEP_LUKEWARM_OCEAN, "deep_ocean");
        m(Biomes.OCEAN, "ocean");
        m(Biomes.DEEP_OCEAN, "deep_ocean");
        m(Biomes.COLD_OCEAN, "ocean");
        m(Biomes.DEEP_COLD_OCEAN, "deep_ocean");
        m(Biomes.FROZEN_OCEAN, "frozen_ocean");
        m(Biomes.DEEP_FROZEN_OCEAN, "deep_ocean");
        m(Biomes.MUSHROOM_FIELDS, "mushroom_island");
        m(Biomes.DRIPSTONE_CAVES, "extreme_hills");
        m(Biomes.LUSH_CAVES, "forest");
        for (int i = Biomes.NETHER_WASTES; i <= Biomes.END_BARRENS; i++) {
            m(i, i < Biomes.THE_END ? "hell" : "sky");
        }
        // The newer versions' (Editions), when no mod registers them: a mangrove swamp is 1.12's swamp (slimes,
        // witch huts' kind of place), the deep dark (always underground) the cave biomes' plains of the column above
        // stands in only for 1.12 code that asks, the cherry grove (a mountain meadow) plains as the meadow is.
        m(Biomes.MANGROVE_SWAMP, "swampland");
        m(Biomes.DEEP_DARK, "plains");
        m(Biomes.CHERRY_GROVE, "plains");
        // The pale garden (always Tricky Trials Backport's when a world has it): 1.12's roofed forest otherwise.
        m(Biomes.PALE_GARDEN, "roofed_forest");
    }

    private final Biome[] biomes = new Biome[Biomes.COUNT];
    private final byte[] ids = new byte[Biomes.COUNT];
    /** Whether a mod registers a 1.12 biome under the 1.18 name (so it is that biome, spawns and all). */
    private final boolean[] registered = new boolean[Biomes.COUNT];

    public BiomeMap() {
        for (int i = 0; i < Biomes.COUNT; i++) {
            Biome b = modern(Biomes.name(i));
            registered[i] = b != null;
            if (b == null) {
                b = ForgeRegistries.BIOMES.getValue(new ResourceLocation("minecraft", LEGACY[i]));
            }
            if (b == null) {
                b = net.minecraft.init.Biomes.PLAINS;
            }
            biomes[i] = b;
            ids[i] = (byte) Biome.getIdForBiome(b);
        }
    }

    /** A biome another mod registered under this 1.18 name, if any (never vanilla 1.12's own, whose names differ). */
    private static Biome modern(String name) {
        Biome best = null;
        String bestId = null;
        for (Map.Entry<ResourceLocation, Biome> e : ForgeRegistries.BIOMES.getEntries()) {
            ResourceLocation id = e.getKey();
            if (id.getPath().equals(name) && !id.getNamespace().equals("minecraft")) {
                if (bestId == null || id.toString().compareTo(bestId) < 0) {
                    best = e.getValue();
                    bestId = id.toString();
                }
            }
        }
        return best;
    }

    /** The 1.12 biome of a 1.18 biome id. */
    public Biome biome(int modern) {
        return biomes[modern];
    }

    /** Whether some mod registers a 1.12 biome under this 1.18 biome's name (not a nearest stand-in). */
    public boolean registered(int modern) {
        return registered[modern];
    }

    /** Its numeric 1.12 id, as a chunk's biome array holds it. */
    public byte id(int modern) {
        return ids[modern];
    }
}
