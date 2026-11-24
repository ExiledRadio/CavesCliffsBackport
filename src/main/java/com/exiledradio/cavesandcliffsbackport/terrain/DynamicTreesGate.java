package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.backportcore.compat.DynamicTrees;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import javax.annotation.Nullable;

/**
 * Dynamic Trees grows its own trees in a 1.12 world and cancels 1.12's TREE and BIG_SHROOM decoration (and CACTUS,
 * unless {@code vanillaCactusWorldGen}) in every biome its biome database says to. The 1.18 terrain places its trees
 * itself, so each 1.18 tree (huge mushroom, cactus patch) asks Backport Core the same question for the biome its own
 * biome filter read where it lands ({@link DynamicTrees#replacesTrees}, the 1.18 biome as the 1.12 biome the world
 * stores for it) and steps aside where Dynamic Trees grows its own; Dynamic Trees' world generator then grows its
 * trees there, each by the biome at its own spot. Asked per tree, not per chunk: a chunk across a biome border has
 * both kinds of biome, and a chunk-wide answer put the 1.18 trees beside Dynamic Trees' on one side of the border or
 * left the other side bare.
 *
 * <p>Per chunk ({@link #allows}), only the cactus patches 1.21.5's spring plants place (Tricky Trials) are asked, by
 * the biome at the middle of the chunk's decoration square, as 1.12.2 decorates.
 *
 * <p>Asked directly, not through the decoration event: the 1.18 trees are placed while a chunk is generated, before it
 * is in the world, and other mods generate in that event (Biomes O' Plenty places its trees in it, which reads the
 * world, asks for the chunk being generated, and recurses until the stack overflows).
 */
final class DynamicTreesGate implements Decorator.TreeGate {

    private final World world;
    private final BiomeMap map;

    private DynamicTreesGate(World world, BiomeMap map) {
        this.world = world;
        this.map = map;
    }

    /** The gate for a world, or null when Dynamic Trees is not installed. */
    @Nullable
    static Decorator.TreeGate of(World world, BiomeMap map) {
        if (!DynamicTrees.loaded()) {
            return null;
        }
        CavesAndCliffsBackport.LOGGER.info("Dynamic Trees is installed: the 1.18 trees step aside in the biomes where it "
                + "grows its own.");
        return new DynamicTreesGate(world, map);
    }

    @Override
    public boolean allows(int cx, int cz, int kind) {
        if (kind != CACTUS) {
            return true;
        }
        // The chunk being generated is not in the world yet, so this is the biome provider's answer, the column's own.
        return !DynamicTrees.replacesCactus(this.world, new BlockPos((cx << 4) + 16, 0, (cz << 4) + 16));
    }

    @Override
    public boolean allowsAt(int x, int z, int biome, int kind) {
        if (kind != TREES && kind != CACTUS) {
            return true;
        }
        Biome legacy = this.map.biome(biome, x, z);
        return kind == TREES ? !DynamicTrees.replacesTrees(this.world, legacy) : !DynamicTrees.replacesCactus(this.world, legacy);
    }
}
