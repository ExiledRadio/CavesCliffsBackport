package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.cubic.CubicGenerator;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorldServer;
import net.minecraft.world.WorldServer;

/** The Cubic Chunks calls of {@link CubicSelfTest}, in a class of their own: loaded only with Cubic Chunks installed. */
final class CubicTestOps {

    static final int MIN_CUBE = -4;
    static final int MAX_CUBE = 19;

    private CubicTestOps() {
    }

    static boolean isCubic(WorldServer world) {
        return world instanceof ICubicWorld && ((ICubicWorld) world).isCubicWorld();
    }

    static String limits(WorldServer world) {
        ICubicWorld w = (ICubicWorld) world;
        return w.getMinHeight() + ".." + w.getMaxHeight();
    }

    private static ICubeProviderServer cubes(WorldServer world) {
        return ((ICubicWorldServer) world).getCubeCache();
    }

    static CubicGenerator generator(WorldServer world) {
        return (CubicGenerator) ((ICubicWorldServer) world).getCubeGenerator();
    }

    /** Columns x0..x0+n by z0..z0+n, row by row, each with all its cubes (as the normal world's chunks). */
    static void generate(WorldServer world, int x0, int z0, int n) {
        ICubeProviderServer cubes = cubes(world);
        for (int x = x0; x <= x0 + n; x++) {
            for (int z = z0; z <= z0 + n; z++) {
                cubes.getColumn(x, z, ICubeProviderServer.Requirement.GENERATE);
                for (int y = MIN_CUBE; y <= MAX_CUBE; y++) {
                    cubes.getCube(x, y, z, ICubeProviderServer.Requirement.GENERATE);
                }
            }
        }
    }

    /** Columns x0..x0+n-1 by z0..z0+n-1 complete: their anchors and those to the -x and -z populated. */
    static void populate(WorldServer world, int x0, int z0, int n) {
        ICubeProviderServer cubes = cubes(world);
        for (int x = x0; x < x0 + n; x++) {
            for (int z = z0; z < z0 + n; z++) {
                cubes.getCube(x, 0, z, ICubeProviderServer.Requirement.POPULATE);
            }
        }
    }

    /** Every cube of the square lit (Cubic Chunks' first light, as for a player). */
    static void light(WorldServer world, int x0, int z0, int n) {
        ICubeProviderServer cubes = cubes(world);
        for (int x = x0; x < x0 + n; x++) {
            for (int z = z0; z < z0 + n; z++) {
                for (int y = MIN_CUBE; y <= MAX_CUBE; y++) {
                    cubes.getCube(x, y, z, ICubeProviderServer.Requirement.LIGHT);
                }
            }
        }
    }

    static int held(WorldServer world) {
        return generator(world).heldColumns();
    }

    static long cubesMade(WorldServer world) {
        return generator(world).cubesMade;
    }

    static String stats(WorldServer world) {
        CubicGenerator g = generator(world);
        return String.format(java.util.Locale.ROOT, "cubic columns=%d cubes=%d drained=%d populated=%d cube_ms=%.4f",
                g.columnsMade, g.cubesMade, g.drained, g.populated, g.cubeNanos / 1e6 / Math.max(1, g.cubesMade));
    }
}
