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
 * <p>A biome one of the family's backports registers under the 1.18 name (they register 1.13+ biomes under their
 * modern names) is taken first; otherwise the closest 1.12 biome below. The 1.18 mountain biomes map to 1.12's
 * mountains (goats spawn there), the warm oceans to the ocean. Other mods' biomes are not matched by name (Biomes O'
 * Plenty's grove is a temperate forest, not 1.18's snowy grove): {@link ModdedBiomes} places them by climate, and
 * {@link #biome(int, int, int)} answers with them where they take a 1.18 biome's place.
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
        // Game Drops' (always its own when a world has them): plains, as the deep dark and the meadow.
        m(Biomes.SULFUR_CAVES, "plains");
        m(Biomes.DAPPLED_FOREST, "plains");
        // 26.4's ice caves (always Game Drops' when a world has them): 1.12's snowy plains otherwise.
        m(Biomes.ICE_CAVES, "ice_flats");
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

    /** A biome a family backport registered under this 1.18 name, if any (never vanilla 1.12's own, whose names differ). */
    private static Biome modern(String name) {
        Biome best = null;
        String bestId = null;
        for (Map.Entry<ResourceLocation, Biome> e : ForgeRegistries.BIOMES.getEntries()) {
            ResourceLocation id = e.getKey();
            if (id.getPath().equals(name) && !id.getNamespace().equals("minecraft")
                    && ModdedBiomes.FAMILY.contains(id.getNamespace())) {
                if (bestId == null || id.toString().compareTo(bestId) < 0) {
                    best = e.getValue();
                    bestId = id.toString();
                }
            }
        }
        return best;
    }

    /**
     * 1.12's biomes as 1.18 names them after an old world is upgraded (Mojang's renames, with the biomes 1.18 removed
     * as their 1.18 replacements: hills and mutated variants as their base biome, mountains as windswept hills, snowy
     * mountains as snowy plains, the badlands plateaus as badlands).
     */
    private static final java.util.Map<String, Integer> UPGRADED = new java.util.HashMap<>();

    private static void u(String legacy, int modern) {
        UPGRADED.put("minecraft:" + legacy, modern);
    }

    static {
        u("ocean", Biomes.OCEAN);
        u("plains", Biomes.PLAINS);
        u("desert", Biomes.DESERT);
        u("extreme_hills", Biomes.WINDSWEPT_HILLS);
        u("forest", Biomes.FOREST);
        u("taiga", Biomes.TAIGA);
        u("swampland", Biomes.SWAMP);
        u("river", Biomes.RIVER);
        u("hell", Biomes.NETHER_WASTES);
        u("sky", Biomes.THE_END);
        u("frozen_ocean", Biomes.FROZEN_OCEAN);
        u("frozen_river", Biomes.FROZEN_RIVER);
        u("ice_flats", Biomes.SNOWY_PLAINS);
        u("ice_mountains", Biomes.SNOWY_PLAINS);
        u("mushroom_island", Biomes.MUSHROOM_FIELDS);
        u("mushroom_island_shore", Biomes.MUSHROOM_FIELDS);
        u("beaches", Biomes.BEACH);
        u("desert_hills", Biomes.DESERT);
        u("forest_hills", Biomes.FOREST);
        u("taiga_hills", Biomes.TAIGA);
        u("smaller_extreme_hills", Biomes.WINDSWEPT_HILLS);
        u("jungle", Biomes.JUNGLE);
        u("jungle_hills", Biomes.JUNGLE);
        u("jungle_edge", Biomes.SPARSE_JUNGLE);
        u("deep_ocean", Biomes.DEEP_OCEAN);
        u("stone_beach", Biomes.STONY_SHORE);
        u("cold_beach", Biomes.SNOWY_BEACH);
        u("birch_forest", Biomes.BIRCH_FOREST);
        u("birch_forest_hills", Biomes.BIRCH_FOREST);
        u("roofed_forest", Biomes.DARK_FOREST);
        u("taiga_cold", Biomes.SNOWY_TAIGA);
        u("taiga_cold_hills", Biomes.SNOWY_TAIGA);
        u("redwood_taiga", Biomes.OLD_GROWTH_PINE_TAIGA);
        u("redwood_taiga_hills", Biomes.OLD_GROWTH_PINE_TAIGA);
        u("extreme_hills_with_trees", Biomes.WINDSWEPT_FOREST);
        u("savanna", Biomes.SAVANNA);
        u("savanna_rock", Biomes.SAVANNA_PLATEAU);
        u("mesa", Biomes.BADLANDS);
        u("mesa_rock", Biomes.WOODED_BADLANDS);
        u("mesa_clear_rock", Biomes.BADLANDS);
        u("void", Biomes.THE_VOID);
        u("mutated_plains", Biomes.SUNFLOWER_PLAINS);
        u("mutated_desert", Biomes.DESERT);
        u("mutated_extreme_hills", Biomes.WINDSWEPT_GRAVELLY_HILLS);
        u("mutated_forest", Biomes.FLOWER_FOREST);
        u("mutated_taiga", Biomes.TAIGA);
        u("mutated_swampland", Biomes.SWAMP);
        u("mutated_ice_flats", Biomes.ICE_SPIKES);
        u("mutated_jungle", Biomes.JUNGLE);
        u("mutated_jungle_edge", Biomes.SPARSE_JUNGLE);
        u("mutated_birch_forest", Biomes.OLD_GROWTH_BIRCH_FOREST);
        u("mutated_birch_forest_hills", Biomes.OLD_GROWTH_BIRCH_FOREST);
        u("mutated_roofed_forest", Biomes.DARK_FOREST);
        u("mutated_taiga_cold", Biomes.SNOWY_TAIGA);
        u("mutated_redwood_taiga", Biomes.OLD_GROWTH_SPRUCE_TAIGA);
        u("mutated_redwood_taiga_hills", Biomes.OLD_GROWTH_SPRUCE_TAIGA);
        u("mutated_extreme_hills_with_trees", Biomes.WINDSWEPT_GRAVELLY_HILLS);
        u("mutated_savanna", Biomes.WINDSWEPT_SAVANNA);
        u("mutated_savanna_rock", Biomes.WINDSWEPT_SAVANNA);
        u("mutated_mesa", Biomes.ERODED_BADLANDS);
        u("mutated_mesa_rock", Biomes.WOODED_BADLANDS);
        u("mutated_mesa_clear_rock", Biomes.BADLANDS);
    }

    /**
     * The 1.18 biome id an old 1.12 chunk's biome becomes in an upgraded world: vanilla's by Mojang's renames, a
     * family backport's registered under a 1.18 name as that biome, or -1 for another mod's biome.
     */
    public static int upgraded(Biome legacy) {
        if (legacy == null) {
            return -1;
        }
        ResourceLocation id = legacy.getRegistryName();
        if (id == null) {
            return -1;
        }
        Integer v = UPGRADED.get(id.toString());
        if (v != null) {
            return v;
        }
        if (ModdedBiomes.FAMILY.contains(id.getNamespace())) {
            for (int i = 0; i < Biomes.COUNT; i++) {
                if (id.getPath().equals(Biomes.name(i))) {
                    return i;
                }
            }
        }
        return -1;
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

    /** Other mods' biomes in the 1.18 terrain, or null for none (the world is 1.18's). */
    @javax.annotation.Nullable
    private ModdedBiomes modded;

    void modded(@javax.annotation.Nullable ModdedBiomes modded) {
        this.modded = modded;
    }

    @javax.annotation.Nullable
    ModdedBiomes modded() {
        return modded;
    }

    /** The 1.12 biome of 1.18 biome {@code modern} at block x, z: a modded biome where one takes its place. */
    public Biome biome(int modern, int x, int z) {
        ModdedBiomes m = modded;
        ModdedBiomes.Entry e = m == null ? null : m.at(x, z, modern);
        return e != null ? e.biome : biomes[modern];
    }

    /** {@link #biome(int, int, int)}'s numeric id. */
    public byte id(int modern, int x, int z) {
        ModdedBiomes m = modded;
        ModdedBiomes.Entry e = m == null ? null : m.at(x, z, modern);
        return e != null ? e.numericId : ids[modern];
    }
}
