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

    /**
     * Dynamic Trees - Backport's trees (cherry, mangrove, pale oak) where the 1.18 terrain places them, by the chunk
     * whose population grows them: the chunk being generated is not in the world yet, so each is grown once its spot
     * is populated ({@link #growHanded}), through Backport Core as the backports' own decoration grows them.
     */
    private final java.util.Map<Long, java.util.List<Object[]>> handed = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Boolean> species = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public boolean hand(String name, int x, int y, int z) {
        if (!this.species.computeIfAbsent(name, DynamicTreesGate::installed)) {
            return false;
        }
        // Population of chunk (cx, cz) decorates the square from (cx * 16 + 8, cz * 16 + 8): the chunk whose
        // population covers this spot grows it, with every chunk the tree can reach in the world.
        long key = net.minecraft.util.math.ChunkPos.asLong((x - 8) >> 4, (z - 8) >> 4);
        this.handed.computeIfAbsent(key, k -> java.util.Collections.synchronizedList(new java.util.ArrayList<Object[]>()))
                .add(new Object[] {new BlockPos(x, y, z), name});
        return true;
    }

    /** Whether Dynamic Trees - Backport has this species: made only with the backport that owns the tree. */
    private static boolean installed(String name) {
        int colon = name.indexOf(':');
        String family = name.substring(colon + 1).replace("tall", "");
        net.minecraft.block.Block branch = net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.getValue(
                new net.minecraft.util.ResourceLocation(name.substring(0, colon), family + "branch"));
        return branch != null && branch != net.minecraft.init.Blocks.AIR;
    }

    /** Trees whose population came while another chunk's trees were growing: grown after those, not inside them. */
    private final java.util.ArrayDeque<Object[]> waiting = new java.util.ArrayDeque<Object[]>();
    private boolean growing;

    /**
     * From the chunk's population (server thread): grows the trees handed over in its square. A tree that reaches a
     * chunk not generated yet makes the game generate and populate it from inside this call; that chunk's trees wait
     * until this chunk's are done, so the populations never nest deeper than one.
     */
    void growHanded(int cx, int cz, java.util.Random random) {
        java.util.List<Object[]> trees = this.handed.remove(net.minecraft.util.math.ChunkPos.asLong(cx, cz));
        if (trees == null) {
            return;
        }
        synchronized (trees) {
            this.waiting.addAll(trees);
        }
        if (this.growing) {
            return;
        }
        this.growing = true;
        try {
            Object[] t;
            while ((t = this.waiting.poll()) != null) {
                DynamicTrees.grow(this.world, (BlockPos) t[0], new java.util.Random(this.world.getSeed() ^ ((BlockPos) t[0]).toLong()),
                        (String) t[1]);
            }
        } finally {
            this.growing = false;
        }
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
