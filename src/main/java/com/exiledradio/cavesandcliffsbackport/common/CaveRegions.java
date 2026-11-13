package com.exiledradio.cavesandcliffsbackport.common;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;

/**
 * Where the cave regions are: 1.18's lush caves and dripstone caves, which 1.12.2 cannot make biomes because its
 * biomes are whole columns. Streams Dr and L decorate inside them, Ax spawns axolotls in lush ones and Mu plays
 * their music.
 *
 * <h2>The noise</h2>
 * One 3D value noise, stateless and cheap (two octaves, 16 hashes a sample): lattice cells 256 blocks wide and 48
 * tall, with a quarter-weight octave of 96 by 24 for ragged edges. Its value is in -1..1 and centered on 0.
 * <b>Dripstone</b> regions are where it is above {@link #THRESHOLD}, <b>lush</b> regions where it is below
 * {@code -THRESHOLD}: the two tails of one noise, so the two kinds never overlap and never touch. Each tail is
 * 9% of the underground (measured), in blobs two hundred to several hundred blocks across and a few tens tall.
 *
 * <h2>The key</h2>
 * The noise is keyed by the first eight bytes of the SHA-256 of the world seed, not by the seed: clients need the
 * regions (music) and get the key in {@link PacketCaveRegions}, and the seed itself never leaves the server, as in
 * vanilla since 1.15. Methods taking a {@code seed} take the world seed and hash it (one cached entry).
 *
 * <h2>Underground</h2>
 * A position is underground when it is at least {@link #COVER} blocks under the column's height map, which also
 * rules out anything open to the sky (a ravine's floor is its column's height).
 */
public final class CaveRegions {

    public enum Region {
        NONE, LUSH, DRIPSTONE
    }

    public static final double THRESHOLD = 0.40;
    public static final int COVER = 4;

    /**
     * Lattice spacing. Until 2026-11-13 the cells were 128 and 48 wide: lush areas then came out about 2.3 times
     * narrower than 1.18.2's lush caves biome (by moss-bearing chunks, chunk-weighted median 145 against 792), so
     * they read as small pockets. Twice as wide puts them at 360-870 on three seeds with the same 9% share.
     */
    private static final double XZ = 1 / 256.0, Y = 1 / 48.0, XZ2 = 1 / 96.0, Y2 = 1 / 24.0;

    /**
     * The most the noise changes per block along x or z, and along y. Each octave is a trilinear blend of lattice
     * values in -1..1 through smoothstep (slope at most 1.5), so per cell it changes by at most 2 * 1.5 = 3.
     */
    public static final double SLOPE_XZ = 0.75 * 3 * XZ + 0.25 * 3 * XZ2, SLOPE_Y = 0.75 * 3 * Y + 0.25 * 3 * Y2;

    /** The last seed hashed and its key, as one object so threads never see a half-written pair. */
    private static final class Keyed {
        final long seed, key;

        Keyed(long seed, long key) {
            this.seed = seed;
            this.key = key;
        }
    }

    private static volatile Keyed last = new Keyed(0, hash(0));

    /** What the server told this client: whether it has spoken, its key and its two region switches. */
    private static volatile boolean clientKnown;
    private static volatile long clientKey;
    private static volatile boolean clientLush, clientDripstone;

    private CaveRegions() {
    }

    // ---------------------------------------------------------------------------------------------
    // By the noise alone

    /** @return the region key of a world seed */
    public static long key(long seed) {
        Keyed k = last;
        if (k.seed != seed) {
            k = new Keyed(seed, hash(seed));
            last = k;
        }
        return k.key;
    }

    /** @return the region noise at a block of a world with this seed, -1..1 */
    public static double noise(long seed, int x, int y, int z) {
        return noiseKeyed(key(seed), x, y, z);
    }

    /** @return the region a block is in by the noise alone (no config, dimension or depth check) */
    public static Region at(long seed, int x, int y, int z) {
        return atKeyed(key(seed), x, y, z);
    }

    public static double noiseKeyed(long key, int x, int y, int z) {
        return 0.75 * value(key, x * XZ, y * Y, z * XZ) + 0.25 * value(key + 1, x * XZ2, y * Y2, z * XZ2);
    }

    public static Region atKeyed(long key, int x, int y, int z) {
        double n = noiseKeyed(key, x, y, z);
        return n > THRESHOLD ? Region.DRIPSTONE : n < -THRESHOLD ? Region.LUSH : Region.NONE;
    }

    /**
     * A cheap test for a 16x16 area from {@code minX, minZ} between two heights: samples every 8 blocks across and
     * 4 up, so no block is more than 4 + 4 across and 2 up from a sample, and the noise there differs from it by
     * at most {@code 8 * SLOPE_XZ + 2 * SLOPE_Y}.
     *
     * @return false only if no block of it can be in a region of that kind
     */
    public static boolean mayContain(long seed, Region kind, int minX, int minZ, int minY, int maxY) {
        long key = key(seed);
        double margin = 8 * SLOPE_XZ + 2 * SLOPE_Y;
        for (int x = minX; x <= minX + 16; x += 8) {
            for (int z = minZ; z <= minZ + 16; z += 8) {
                for (int y = minY; y <= maxY + 3; y += 4) {
                    double n = noiseKeyed(key, x, y, z);
                    if (kind == Region.LUSH ? n < -THRESHOLD + margin : n > THRESHOLD - margin) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // In a world: config, dimension and world type included; on a client, the server's answer

    /** @return whether a block is in a lush region (config on, the overworld, not a flat world), by the noise */
    public static boolean lush(World world, int x, int y, int z) {
        return in(world, Region.LUSH, x, y, z);
    }

    /** @return whether a block is in a dripstone region (config on, the overworld, not a flat world), by the noise */
    public static boolean dripstone(World world, int x, int y, int z) {
        return in(world, Region.DRIPSTONE, x, y, z);
    }

    /** @return whether a block is under at least {@link #COVER} blocks of its column; the chunk must be loaded */
    public static boolean underground(World world, BlockPos pos) {
        return pos.getY() <= world.getHeight(pos.getX(), pos.getZ()) - COVER;
    }

    private static boolean in(World world, Region kind, int x, int y, int z) {
        if (world.provider.getDimension() != 0 || world.getWorldType() == WorldType.FLAT) {
            return false;
        }
        if (!world.isRemote) {
            // A world with 1.18 terrain: its own lush and dripstone caves, 1.18's biomes in three dimensions.
            int biome = com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain.biomeAt(world, x, y, z);
            if (biome >= 0) {
                return biome == (kind == Region.LUSH ? com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes.LUSH_CAVES
                        : com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes.DRIPSTONE_CAVES);
            }
        }
        long key;
        if (world.isRemote) {
            if (!clientKnown || !(kind == Region.LUSH ? clientLush : clientDripstone)) {
                return false;
            }
            key = clientKey;
        } else {
            if (!(kind == Region.LUSH ? ModConfig.caves.lushRegions : ModConfig.caves.dripstoneRegions)) {
                return false;
            }
            key = key(world.getSeed());
        }
        return atKeyed(key, x, y, z) == kind;
    }

    /** From {@link PacketCaveRegions}, on a client. */
    static void receive(long key, boolean lush, boolean dripstone) {
        clientKey = key;
        clientLush = lush;
        clientDripstone = dripstone;
        clientKnown = true;
    }

    /** On leaving a server: a server without this mod says nothing, so nothing is known. */
    static void forget() {
        clientKnown = false;
    }

    // ---------------------------------------------------------------------------------------------

    private static long hash(long seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ByteBuffer.allocate(8).putLong(seed).array());
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java has SHA-256", e);
        }
    }

    private static double value(long seed, double x, double y, double z) {
        int x0 = MathHelper.floor(x), y0 = MathHelper.floor(y), z0 = MathHelper.floor(z);
        double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
        double c00 = lerp(fx, lattice(seed, x0, y0, z0), lattice(seed, x0 + 1, y0, z0));
        double c10 = lerp(fx, lattice(seed, x0, y0 + 1, z0), lattice(seed, x0 + 1, y0 + 1, z0));
        double c01 = lerp(fx, lattice(seed, x0, y0, z0 + 1), lattice(seed, x0 + 1, y0, z0 + 1));
        double c11 = lerp(fx, lattice(seed, x0, y0 + 1, z0 + 1), lattice(seed, x0 + 1, y0 + 1, z0 + 1));
        return lerp(fz, lerp(fy, c00, c10), lerp(fy, c01, c11));
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** A lattice point's value, -1..1: a 64-bit mix of the key and the three coordinates. */
    private static double lattice(long seed, int x, int y, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * (2.0 / (1L << 53)) - 1.0;
    }
}
