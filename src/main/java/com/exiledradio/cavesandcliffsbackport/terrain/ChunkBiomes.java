package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeZoom;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 1.18's biomes in three dimensions, for a world with 1.18 terrain: lush caves and dripstone caves lie under other
 * biomes, and 1.12 keeps one biome per column. Each loaded chunk's stored biomes (1.18's 4x4x4 quarts, y -64 to 319)
 * are kept here: from the pipeline for a chunk just generated, from the noise for one loaded from disk (they are a
 * pure function of the seed and the position). {@link #biome} is 1.18's {@code BiomeManager.getBiome} over them, the
 * biome 1.18's mob spawner reads at a spawn position.
 *
 * <p>{@link #spawns} gives the spawn list for a position: 1.18's lists for the two cave biomes (no animals; bats;
 * glow squid; the common monsters, drowned in dripstone caves; tropical fish where a mod adds a water ambient group),
 * resolved against the mobs installed, unless a mod registers a 1.12 biome under the cave biome's 1.18 name (then
 * its lists); any other biome's 1.12 lists.
 *
 * <p>Server thread.
 */
final class ChunkBiomes implements BiomeZoom.Quarts {

    private final World world;
    private final BiomeMap map;
    private final BiomeSource source;
    private final BiomeZoom zoom;
    private final Map<Long, byte[]> chunks = new HashMap<>();
    private long lastKey = Long.MIN_VALUE;
    private byte[] last;
    /** Per cave biome (0 dripstone, 1 lush), its spawn lists by group; built on first use. */
    private List<Map<EnumCreatureType, List<Biome.SpawnListEntry>>> caves;

    ChunkBiomes(World world, ModernTerrain terrain) {
        this.world = world;
        this.map = terrain.map;
        this.source = new BiomeSource(terrain.noise, new ColumnCache(terrain.noise), terrain.climate);
        this.zoom = new BiomeZoom(terrain.noise.seed, this);
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    /** A generated chunk's stored biomes (BiomeSource.fillChunk's layout); the array is kept. */
    synchronized void put(int cx, int cz, byte[] quarts) {
        chunks.put(key(cx, cz), quarts);
        lastKey = Long.MIN_VALUE;
    }

    synchronized void remove(int cx, int cz) {
        chunks.remove(key(cx, cz));
        lastKey = Long.MIN_VALUE;
    }

    /**
     * A loaded chunk's stored biomes (BiomeSource.fillChunk's layout; the kept array, not to be written), or null for
     * a chunk not loaded.
     */
    synchronized byte[] quarts(int cx, int cz) {
        long k = key(cx, cz);
        byte[] q = chunks.get(k);
        if (q == null && world.getChunkProvider().getLoadedChunk(cx, cz) != null) {
            q = new byte[Pipeline.QUARTS];
            source.fillChunk(cx, cz, q);
            chunks.put(k, q);
            lastKey = Long.MIN_VALUE;
        }
        return q;
    }

    synchronized int size() {
        return chunks.size();
    }

    @Override
    public int stored(int qx, int qy, int qz) {
        int cx = qx >> 2;
        int cz = qz >> 2;
        long k = key(cx, cz);
        byte[] q;
        if (k == lastKey) {
            q = last;
        } else {
            q = chunks.get(k);
            if (q == null && world.getChunkProvider().getLoadedChunk(cx, cz) != null) {
                // A chunk loaded from disk: its biomes from the noise, as they were when it was generated.
                q = new byte[Pipeline.QUARTS];
                source.fillChunk(cx, cz, q);
                chunks.put(k, q);
            }
            lastKey = k;
            last = q;
        }
        if (q == null) {
            return source.stored(qx, qy, qz);
        }
        int y = qy < BiomeSource.MIN_QUART_Y ? 0 : qy >= BiomeSource.MIN_QUART_Y + BiomeSource.QUARTS_Y
                ? BiomeSource.QUARTS_Y - 1 : qy - BiomeSource.MIN_QUART_Y;
        return q[((qx & 3) * 4 + (qz & 3)) * BiomeSource.QUARTS_Y + y];
    }

    /** 1.18's biome id at a block (the zoomed biome, as its mob spawner and F3 screen read it). */
    synchronized int biome(int x, int y, int z) {
        return zoom.biome(x, y, z);
    }

    /** The spawn list for a group at a block: its 1.18 biome's (see the class comment). */
    synchronized List<Biome.SpawnListEntry> spawns(EnumCreatureType type, int x, int y, int z) {
        int b = zoom.biome(x, y, z);
        if (b == Biomes.DEEP_DARK && !map.registered(b)) {
            // 1.19's deep dark spawns nothing (OverworldBiomes.deepDark: an empty MobSpawnSettings).
            return Collections.<Biome.SpawnListEntry>emptyList();
        }
        if ((b == Biomes.LUSH_CAVES || b == Biomes.DRIPSTONE_CAVES) && !map.registered(b)) {
            if (caves == null) {
                caves = new ArrayList<>();
                caves.add(caveLists(false));
                caves.add(caveLists(true));
            }
            List<Biome.SpawnListEntry> list = caves.get(b == Biomes.LUSH_CAVES ? 1 : 0).get(type);
            return list != null ? list : Collections.<Biome.SpawnListEntry>emptyList();
        }
        return map.biome(b).getSpawnableList(type);
    }

    // ---- 1.18.2's spawn settings of the cave biomes

    /**
     * OverworldBiomes.lushCaves / dripstoneCaves: BiomeDefaultFeatures.commonSpawns (caveSpawns and monsters(95, 5,
     * 100)), lush caves' axolotls and tropical fish, dripstone caves' drowned. Axolotls have their own spawner here
     * ({@code axolotl.AxolotlSpawning}, which asks this mod's lush caves), so they are not in a list.
     */
    private static Map<EnumCreatureType, List<Biome.SpawnListEntry>> caveLists(boolean lush) {
        Map<EnumCreatureType, List<Biome.SpawnListEntry>> m = new EnumMap<>(EnumCreatureType.class);
        for (EnumCreatureType t : EnumCreatureType.values()) {
            m.put(t, new ArrayList<Biome.SpawnListEntry>());
        }
        if (lush) {
            add(m, "water_ambient", "tropical_fish", 25, 8, 8);
        }
        add(m, "ambient", "bat", 10, 8, 8);
        add(m, "underground_water_creature", "glow_squid", 10, 4, 6);
        add(m, "monster", "spider", 100, 4, 4);
        add(m, "monster", "zombie", 95, 4, 4);
        add(m, "monster", "zombie_villager", 5, 1, 1);
        add(m, "monster", "skeleton", 100, 4, 4);
        add(m, "monster", "creeper", 100, 4, 4);
        add(m, "monster", "slime", 100, 4, 4);
        add(m, "monster", "enderman", 10, 1, 4);
        add(m, "monster", "witch", 5, 1, 1);
        if (!lush) {
            add(m, "monster", "drowned", 95, 4, 4);
        }
        return m;
    }

    private static void add(Map<EnumCreatureType, List<Biome.SpawnListEntry>> m, String category, String mob, int weight,
                            int min, int max) {
        EnumCreatureType type = group(category);
        Class<? extends EntityLiving> c = mob(mob);
        if (type != null && c != null) {
            m.get(type).add(new Biome.SpawnListEntry(c, weight, min, max));
        }
    }

    /** 1.18's MobCategory as a 1.12 group: vanilla's four, or one a mod added under the 1.18 name (any prefix). */
    private static EnumCreatureType group(String category) {
        String upper = category.toUpperCase(Locale.ROOT);
        for (EnumCreatureType t : EnumCreatureType.values()) {
            if (t.name().equals(upper)) {
                return t;
            }
        }
        for (EnumCreatureType t : EnumCreatureType.values()) {
            if (t.name().endsWith("_" + upper)) {
                return t;
            }
        }
        return null;
    }

    /** A living mob registered under the 1.18 name: vanilla's first, then any mod's (the first by id). */
    @SuppressWarnings("unchecked")
    private static Class<? extends EntityLiving> mob(String name) {
        Class<? extends Entity> best = null;
        String bestId = null;
        for (Map.Entry<ResourceLocation, EntityEntry> e : ForgeRegistries.ENTITIES.getEntries()) {
            ResourceLocation id = e.getKey();
            if (!id.getPath().equals(name)) {
                continue;
            }
            String s = "minecraft".equals(id.getNamespace()) ? "" : id.toString();
            if (bestId == null || s.compareTo(bestId) < 0) {
                best = e.getValue().getEntityClass();
                bestId = s;
            }
        }
        return best != null && EntityLiving.class.isAssignableFrom(best) ? (Class<? extends EntityLiving>) best : null;
    }
}
