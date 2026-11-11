package com.exiledradio.cavesandcliffsbackport.lush;

import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;

/**
 * Where the lush caves are.
 *
 * <p>1.12 biomes are whole columns, so a cave biome cannot be one. This is a smooth 3D noise over the world,
 * made from the world seed alone (no saved state, the same answer on server and client): where it is above
 * {@link #THRESHOLD} the underground is lush. Its cells are {@value #CELL_XZ} blocks wide and
 * {@value #CELL_Y} tall, which gives regions one to two hundred blocks across and a few tens tall, over
 * about a tenth of the underground.
 *
 * <p><b>For the integrator:</b> this stands in for the planned {@code common/CaveRegions}, which did not
 * exist when stream L was built. Everything in this package asks only {@link #isLush(long, int, int, int)},
 * {@link #isLush(World, BlockPos)} and {@link #mayContain}; moving them onto the shared class (and making
 * lush and dripstone regions exclude each other there) touches this file alone.
 */
public final class LushRegions {

    public static final int CELL_XZ = 144;
    public static final int CELL_Y = 48;
    public static final double THRESHOLD = 0.74;

    /** Mixed into the world seed so other region kinds made the same way do not line up with this one. */
    private static final long SALT = 0x4C555348L;

    private LushRegions() {
    }

    /** @return true if lush regions are on and this position of this world is in one (above ground or below) */
    public static boolean isLush(World world, BlockPos pos) {
        return ModConfig.caves.lushRegions && world.provider.getDimension() == 0 && world.getWorldType() != WorldType.FLAT
                && isLush(world.getSeed(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean isLush(long seed, int x, int y, int z) {
        return value(seed, x, y, z) > THRESHOLD;
    }

    /** The region noise, 0 to 1. */
    public static double value(long seed, int x, int y, int z) {
        return noise(seed ^ SALT, x / (double) CELL_XZ, y / (double) CELL_Y, z / (double) CELL_XZ);
    }

    /**
     * A cheap test for a whole 16x16 column of blocks from {@code minX, minZ}, between two heights.
     *
     * @return false only if no block of it can be lush (the noise changes by at most 0.15 between samples)
     */
    public static boolean mayContain(long seed, int minX, int minZ, int minY, int maxY) {
        for (int x = minX; x <= minX + 16; x += 8) {
            for (int z = minZ; z <= minZ + 16; z += 8) {
                for (int y = minY; y <= maxY + 3; y += 4) {
                    if (value(seed, x, y, z) > THRESHOLD - 0.15) {
                        return true;
                    }
                }
            }
        }
        return false;
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
