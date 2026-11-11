package com.exiledradio.cavesandcliffsbackport.axolotl;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Where axolotls may spawn naturally: the one question {@link AxolotlSpawning} asks about a place, beyond the
 * axolotl's own rule (water, with clay right below).
 *
 * <p>In 1.18 the answer is "inside a lush cave". The lush regions belong to {@code common/CaveRegions}, which
 * this stream does not link against: the integrator sets {@link AxolotlSpawning#habitat} to an implementation
 * that asks it. Until then {@link DarkUndergroundWater} stands in.
 */
public interface AxolotlHabitat {

    /**
     * @param pos a water block with clay below it, in a loaded chunk
     * @return true if axolotls spawn there
     */
    boolean canSpawnAt(World world, BlockPos pos);

    /** The stand-in: underground (below the top solid block of its column) and completely dark. */
    final class DarkUndergroundWater implements AxolotlHabitat {
        @Override
        public boolean canSpawnAt(World world, BlockPos pos) {
            return pos.getY() < world.getTopSolidOrLiquidBlock(pos).getY() && world.getLightFromNeighbors(pos) == 0;
        }
    }
}
