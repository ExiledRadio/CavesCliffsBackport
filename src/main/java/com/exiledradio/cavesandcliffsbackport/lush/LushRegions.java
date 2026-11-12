package com.exiledradio.cavesandcliffsbackport.lush;

import com.exiledradio.cavesandcliffsbackport.common.CaveRegions;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Where the lush caves are: the lush tail of {@link CaveRegions}, the regions shared with dripstone caves, axolotl
 * spawning and cave music. Dripstone regions are the other tail of the same noise, so the two never overlap.
 * Everything in this package asks only these methods; {@link #patch} is this package's own noise.
 */
public final class LushRegions {

    private LushRegions() {
    }

    /** @return true if lush regions are on and this position of this world is in one (above ground or below) */
    public static boolean isLush(World world, BlockPos pos) {
        return CaveRegions.lush(world, pos.getX(), pos.getY(), pos.getZ());
    }

    /** @return whether a block of a world with this seed is in a lush region, by the noise alone */
    public static boolean isLush(long seed, int x, int y, int z) {
        return CaveRegions.at(seed, x, y, z) == CaveRegions.Region.LUSH;
    }

    /** The region noise, -1 to 1; lush below {@code -CaveRegions.THRESHOLD}. */
    public static double value(long seed, int x, int y, int z) {
        return CaveRegions.noise(seed, x, y, z);
    }

    /** @return false only if no block of the 16x16 area from {@code minX, minZ} between the heights can be lush */
    public static boolean mayContain(long seed, int minX, int minZ, int minY, int maxY) {
        return CaveRegions.mayContain(seed, CaveRegions.Region.LUSH, minX, minZ, minY, maxY);
    }

    /** A 2D noise, 0 to 1, for laying patches (moss, clay, pools) over a region's floors and ceilings. */
    public static double patch(long seed, int salt, int x, int z, double cell) {
        return noise(seed ^ (salt * 0x9E3779B97F4A7C15L), x / cell, salt * 31.7, z / cell);
    }

    // ---------------------------------------------------------------------------------------------
    // Value noise: a random number at every lattice point, smoothly blended between them.

    private static double noise(long seed, double x, double y, double z) {
        int x0 = floor(x), y0 = floor(y), z0 = floor(z);
        double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
        double c00 = lerp(fx, lattice(seed, x0, y0, z0), lattice(seed, x0 + 1, y0, z0));
        double c10 = lerp(fx, lattice(seed, x0, y0 + 1, z0), lattice(seed, x0 + 1, y0 + 1, z0));
        double c01 = lerp(fx, lattice(seed, x0, y0, z0 + 1), lattice(seed, x0 + 1, y0, z0 + 1));
        double c11 = lerp(fx, lattice(seed, x0, y0 + 1, z0 + 1), lattice(seed, x0 + 1, y0 + 1, z0 + 1));
        return lerp(fz, lerp(fy, c00, c10), lerp(fy, c01, c11));
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double lattice(long seed, int x, int y, int z) {
        long h = seed + x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }
}
