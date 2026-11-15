package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeZoom;

import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * A client's copy of 1.18's biomes in three dimensions, in a world with 1.18 terrain: each chunk's stored biomes as
 * the server sends them ({@link PacketChunkBiomes}, after the chunk itself), and 1.18's zoom over them. This is what
 * 1.18's client reads at the camera ({@code Level.getBiome}: BiomeManager over the chunks' biomes from the chunk
 * packet), for the biome music.
 *
 * <p>Common code, no client types; the client thread only (the packet handler schedules onto it).
 */
public final class ClientBiomes {

    /** The dimension the chunks are from, or {@link Integer#MIN_VALUE} while nothing has arrived. */
    private static int dimension = Integer.MIN_VALUE;
    private static long zoomSeed;
    private static final Map<Long, byte[]> CHUNKS = new HashMap<>();
    private static BiomeZoom zoom;

    private static long lastKey = Long.MIN_VALUE;
    private static byte[] last;

    private static final BiomeZoom.Quarts QUARTS = (qx, qy, qz) -> {
        int cx = qx >> 2;
        int cz = qz >> 2;
        long k = key(cx, cz);
        byte[] q;
        if (k == lastKey) {
            q = last;
        } else {
            q = CHUNKS.get(k);
            lastKey = k;
            last = q;
        }
        if (q == null) {
            // 1.18's client: a chunk it has not got is an EmptyLevelChunk (plains), no music biome.
            return -1;
        }
        // LevelChunk.getNoiseBiome: the quart's y clamped into the chunk's sections.
        int y = qy < BiomeSource.MIN_QUART_Y ? 0 : qy >= BiomeSource.MIN_QUART_Y + BiomeSource.QUARTS_Y
                ? BiomeSource.QUARTS_Y - 1 : qy - BiomeSource.MIN_QUART_Y;
        return q[((qx & 3) * 4 + (qz & 3)) * BiomeSource.QUARTS_Y + y];
    };

    private ClientBiomes() {
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    /** From the packet: a chunk's stored biomes (the array is kept). */
    static void put(int dim, long seed, int cx, int cz, byte[] quarts) {
        if (dim != dimension || seed != zoomSeed || zoom == null) {
            forget();
            dimension = dim;
            zoomSeed = seed;
            zoom = BiomeZoom.ofZoomSeed(seed, QUARTS);
        }
        CHUNKS.put(key(cx, cz), quarts);
        lastKey = Long.MIN_VALUE;
    }

    /** The client unloaded a chunk. */
    static void remove(int cx, int cz) {
        CHUNKS.remove(key(cx, cz));
        lastKey = Long.MIN_VALUE;
    }

    /** The client left its world (a dimension change, a disconnect). */
    static void forget() {
        CHUNKS.clear();
        dimension = Integer.MIN_VALUE;
        zoom = null;
        lastKey = Long.MIN_VALUE;
        last = null;
    }

    /** @return whether the server sends 1.18's biomes for this client world */
    public static boolean active(World world) {
        return zoom != null && world.provider.getDimension() == dimension;
    }

    /** @return whether the client holds a chunk's biomes (for the self-test) */
    public static boolean has(int cx, int cz) {
        return CHUNKS.containsKey(key(cx, cz));
    }

    /** @return how many chunks' biomes the client holds (for the self-test) */
    public static int size() {
        return CHUNKS.size();
    }

    /**
     * 1.18's biome id at a block ({@code core.Biomes}), as 1.18's client reads it; -1 where this world has no 1.18
     * biomes or the chunk has not arrived.
     */
    public static int biome(World world, int x, int y, int z) {
        if (!active(world)) {
            return -1;
        }
        return zoom.biome(x, y, z);
    }
}
