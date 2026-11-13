package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.terrain.FluidUpdates;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import net.minecraft.block.BlockLog;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Loader;

import java.util.Map;
import java.util.TreeMap;

/**
 * Other backports' newer placed features in the 1.18 terrain ({@code -Pselftest=modernfeatures}, in a Caves &amp;
 * Cliffs world): with Tricky Trials installed, its 1.21.5 spring plants grow in the biomes that have them (bushes in
 * plains, wildflowers in birch forests, leaf litter in forests and dark forests, dry grass and flowered cacti in
 * deserts, firefly bushes in swamps), fallen trees lie in the forests, and every chunk ran its requests once its
 * neighbors were in. Without it the generator has none of them (and the terrain test's chunk holds exactly 1.18.2's
 * blocks).
 */
final class ModernFeaturesSelfTest implements SelfTest {

    private static final String TRICKY = "trickytrialsbackport";

    @Override
    public String name() {
        return "modernfeatures";
    }

    @Override
    public void run(Check check) {
        WorldServer world = check.world;
        if (!Heights.isExtended(world) || !ModernTerrain.owns(world)) {
            check.ok(true, "modernfeatures runs in a Caves & Cliffs world only");
            return;
        }
        ModernGenerator generator = (ModernGenerator) world.getChunkProvider().chunkGenerator;
        boolean tricky = Loader.isModLoaded(TRICKY);
        check.eq(tricky, generator.hasModernFeatures(), "the generator has newer features exactly when Tricky Trials is installed");
        if (!tricky) {
            return;
        }
        OverworldNoise noise = new OverworldNoise(world.getSeed(), generator.editions());
        BiomeSource source = new BiomeSource(noise, new ColumnCache(noise), new Climate(generator.editions()));
        long before = FluidUpdates.modernRuns;
        Map<String, Integer> all = new TreeMap<>();
        area(check, world, source, Biomes.PLAINS, all, "bush");
        area(check, world, source, Biomes.BIRCH_FOREST, all, "wildflowers");
        area(check, world, source, Biomes.FOREST, all, "leaf_litter");
        area(check, world, source, Biomes.DARK_FOREST, all, "leaf_litter");
        area(check, world, source, Biomes.DESERT, all, "short_dry_grass", "cactus_flower");
        area(check, world, source, Biomes.SWAMP, all, "firefly_bush");
        long runs = FluidUpdates.modernRuns - before;
        info("chunks that ran their requests: " + runs + "; all areas: " + all);
        check.ok(runs >= 6 * 25, "every chunk of the areas' 5x5 ran its newer features (" + runs + ")");
        check.ok(all.getOrDefault("fallen logs", 0) > 0, "fallen trees lie in the areas (" + all.get("fallen logs") + " logs)");
    }

    /**
     * The 5x5 chunks at the nearest place of the biome, generated with a ring around so each has its neighbors, then
     * the requests run; the census of Tricky Trials' blocks and fallen logs in them.
     */
    private static void area(Check check, WorldServer world, BiomeSource source, int biome, Map<String, Integer> all,
                             String... expected) {
        int[] c = find(source, biome);
        String name = Biomes.name(biome);
        if (!check.ok(c != null, "a " + name + " within 200 chunks")) {
            return;
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                world.getChunkProvider().provideChunk(c[0] + dx, c[1] + dz);
            }
        }
        FluidUpdates.run(world);
        Map<String, Integer> n = new TreeMap<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = (c[0] - 2) * 16; x < (c[0] + 3) * 16; x++) {
            for (int z = (c[1] - 2) * 16; z < (c[1] + 3) * 16; z++) {
                for (int y = 50; y < 200; y++) {
                    IBlockState s = world.getBlockState(p.setPos(x, y, z));
                    ResourceLocation id = s.getBlock().getRegistryName();
                    if (id == null) {
                        continue;
                    }
                    if (id.getNamespace().equals(TRICKY)) {
                        n.merge(id.getPath(), 1, Integer::sum);
                    } else if (s.getBlock() instanceof BlockLog && s.getPropertyKeys().contains(BlockLog.LOG_AXIS)
                            && s.getValue(BlockLog.LOG_AXIS) != BlockLog.EnumAxis.Y
                            && s.getValue(BlockLog.LOG_AXIS) != BlockLog.EnumAxis.NONE) {
                        // Lying on the ground (a fancy oak's branches are up in its crown).
                        IBlockState below = world.getBlockState(p.down());
                        if (below.isOpaqueCube() && !(below.getBlock() instanceof BlockLog)
                                && !below.getBlock().isLeaves(below, world, p.down())) {
                            n.merge("fallen logs", 1, Integer::sum);
                        }
                    } else if (id.getPath().equals("cactus")) {
                        n.merge("cactus", 1, Integer::sum);
                    }
                }
            }
        }
        info(name + " at chunk " + c[0] + ", " + c[1] + ": " + n);
        for (String what : expected) {
            check.ok(n.getOrDefault(what, 0) > 0, name + ": " + what + " (" + n.get(what) + ")");
        }
        for (Map.Entry<String, Integer> e : n.entrySet()) {
            all.merge(e.getKey(), e.getValue(), Integer::sum);
        }
    }

    private static void info(String what) {
        CavesAndCliffsBackport.LOGGER.info("SELFTEST INFO modernfeatures {}", what);
    }

    /** The nearest chunk to 0, 0 (30 out or more: past the spawn area) whose 5x5 around has the biome at every centre quart at sea level. */
    private static int[] find(BiomeSource source, int biome) {
        for (int r = 30; r < 200; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r && all(source, biome, dx, dz)) {
                        return new int[]{dx, dz};
                    }
                }
            }
        }
        return null;
    }

    private static boolean all(BiomeSource source, int biome, int cx, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (source.biome((cx + dx) * 4 + 2, 16, (cz + dz) * 4 + 2) != biome) {
                    return false;
                }
            }
        }
        return true;
    }
}
