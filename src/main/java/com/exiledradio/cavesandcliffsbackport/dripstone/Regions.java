package com.exiledradio.cavesandcliffsbackport.dripstone;

import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Where the cave regions are: the plan's {@code common/CaveRegions}, written here until the integrator lifts
 * it into {@code common} (streams L and Ax need the same answer).
 *
 * <h2>The noise</h2>
 * One 3D value noise of the world seed, stateless and cheap (two octaves, 16 hashes a sample): lattice cells
 * 128 blocks wide and 48 tall, with a quarter-weight octave of 48 by 24 for ragged edges. Its value is in
 * -1..1 and centered on 0. <b>Dripstone</b> regions are where it is above {@link #THRESHOLD}, <b>lush</b>
 * regions where it is below {@code -THRESHOLD}: the two tails of one noise, so the two kinds never overlap
 * and never touch. Each tail is 9% of the underground (measured), in blobs a hundred to a few hundred blocks
 * across and a few tens tall.
 *
 * <h2>Underground</h2>
 * A position is underground when it is at least {@link #COVER} blocks under the column's height map, which
 * also rules out anything open to the sky (a ravine's floor is its column's height).
 */
public final class Regions {

    public enum Region {
        NONE, LUSH, DRIPSTONE
    }

    public static final double THRESHOLD = 0.40;
    public static final int COVER = 4;

    private static final double XZ = 1 / 128.0, Y = 1 / 48.0, XZ2 = 1 / 48.0, Y2 = 1 / 24.0;

    private Regions() {
    }

    /** @return the region noise at a block, -1..1 */
    public static double noise(long seed, int x, int y, int z) {
        return 0.75 * value(seed, x * XZ, y * Y, z * XZ) + 0.25 * value(seed + 1, x * XZ2, y * Y2, z * XZ2);
    }

    /** @return the region a block is in by the noise alone (no config, dimension or depth check) */
    public static Region at(long seed, int x, int y, int z) {
        double n = noise(seed, x, y, z);
        return n > THRESHOLD ? Region.DRIPSTONE : n < -THRESHOLD ? Region.LUSH : Region.NONE;
    }

    /** @return whether a block of the overworld is in a dripstone region (config on), by the noise alone */
    public static boolean dripstone(World world, int x, int y, int z) {
        return ModConfig.caves.dripstoneRegions && world.provider.getDimension() == 0
                && at(world.getSeed(), x, y, z) == Region.DRIPSTONE;
    }

    /** @return whether a block is under at least {@link #COVER} blocks of its column; the chunk must be loaded */
    public static boolean underground(World world, BlockPos pos) {
        return pos.getY() <= world.getHeight(pos.getX(), pos.getZ()) - COVER;
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

    /** A lattice point's value, -1..1: a 64-bit mix of the seed and the three coordinates. */
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
