package com.exiledradio.cavesandcliffsbackport.axolotl;

import com.exiledradio.cavesandcliffsbackport.common.CaveRegions;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Where axolotls may spawn naturally: the one question {@link AxolotlSpawning} asks about a place, beyond the
 * axolotl's own rule (water, with clay right below).
 *
 * <p>In 1.18 the answer is "inside a lush cave": {@link LushCaves}, the default. {@link DarkUndergroundWater}
 * was the stand-in before the cave regions existed.
 */
public interface AxolotlHabitat {

    /**
     * @param pos a water block with clay below it, in a loaded chunk
     * @return true if axolotls spawn there
     */
    boolean canSpawnAt(World world, BlockPos pos);

    /** 1.18's: in a lush region of {@link CaveRegions}, underground. */
    final class LushCaves implements AxolotlHabitat {
        @Override
        public boolean canSpawnAt(World world, BlockPos pos) {
            return CaveRegions.lush(world, pos.getX(), pos.getY(), pos.getZ()) && CaveRegions.underground(world, pos);
        }
    }

    /** The stand-in: underground (below the top solid block of its column) and completely dark. */
    final class DarkUndergroundWater implements AxolotlHabitat {
        @Override
        public boolean canSpawnAt(World world, BlockPos pos) {
            return pos.getY() < world.getTopSolidOrLiquidBlock(pos).getY() && world.getLightFromNeighbors(pos) == 0;
        }
    }
}
