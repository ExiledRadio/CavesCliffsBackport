package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.backportcore.compat.DynamicTrees;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Dynamic Trees grows its own trees in a 1.12 world and cancels 1.12's TREE and BIG_SHROOM decoration (and CACTUS,
 * unless {@code vanillaCactusWorldGen}) in every biome its biome database says to. The 1.18 terrain places its trees
 * itself, so each 1.18 tree (huge mushroom, cactus patch) asks Backport Core the same question for the column it
 * lands in ({@link DynamicTrees#replacesTrees}) and steps aside where Dynamic Trees grows its own; Dynamic Trees'
 * world generator then grows its trees there, each by the biome at its own spot. Asked per column, not per chunk: a
 * chunk across a biome border has both kinds of biome, and a chunk-wide answer put the 1.18 trees beside Dynamic Trees'
 * on one side of the border or left the other side bare.
 *
 * <p>Asked directly, not through the decoration event: the 1.18 trees are placed while a chunk is generated, before it
 * is in the world, and other mods generate in that event (Biomes O' Plenty places its trees in it, which reads the
 * world, asks for the chunk being generated, and recurses until the stack overflows).
 */
final class DynamicTreesGate implements Decorator.TreeGate {

    private final World world;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private DynamicTreesGate(World world) {
        this.world = world;
    }

    /** The gate for a world, or null when Dynamic Trees is not installed. */
    @Nullable
    static Decorator.TreeGate of(World world) {
        if (!DynamicTrees.loaded()) {
            return null;
        }
        CavesAndCliffsBackport.LOGGER.info("Dynamic Trees is installed: the 1.18 trees step aside in the biomes where it "
                + "grows its own.");
        return new DynamicTreesGate(world);
    }

    @Override
    public boolean allows(int cx, int cz, int kind) {
        return true;
    }

    @Override
    public boolean allowsAt(int x, int z, int kind) {
        if (kind != TREES && kind != CACTUS) {
            return true;
        }
        // The chunk being generated is not in the world yet, so this is the biome provider's answer, the column's own.
        this.pos.setPos(x, 0, z);
        return kind == TREES ? !DynamicTrees.replacesTrees(this.world, this.pos)
                : !DynamicTrees.replacesCactus(this.world, this.pos);
    }
}
