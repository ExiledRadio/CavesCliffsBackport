package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.terrain.DecorationSource;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Editions;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.HashMap;
import java.util.Map;

/**
 * The newer biomes in a 1.18 terrain world (part of {@code terrain}): which ones the world got (Wild Update's blocks
 * give 1.19's mangrove swamp and deep dark, Trails &amp; Tales' cherry wood 1.20's cherry grove), that their features
 * generate with the backports' blocks, that the deep dark spawns nothing, that 1.18's bee nests (Buzzy Bees') have
 * their bees, and what the generator tells the backports' 1.12 hooks through Backport Core's DecorationSource.
 */
final class NewerBiomesSelfTest {

    private NewerBiomesSelfTest() {
    }

    static void run(Check check, WorldServer world, ModernGenerator generator) {
        int e = generator.editions();
        boolean wild = registered("mud") && registered("mangrove_log") && registered("mangrove_propagule");
        boolean cherry = registered("cherry_log") && registered("pink_petals");
        check.eq((wild ? Editions.WILD : 0) | (cherry ? Editions.CHERRY : 0), e,
                "the world's newer biomes are the ones the installed backports have blocks for (" + Editions.name(e) + ")");
        check.ok(DecorationSource.places(world, "bee_nest"), "the generator says it places bee nests (1.12 nest hooks step aside)");
        check.ok(DecorationSource.places(world, "trees_plains"), "the generator says it places trees_plains");
        check.eq((e & Editions.WILD) != 0, DecorationSource.places(world, "trees_mangrove"),
                "the generator places trees_mangrove only with the 1.19 biomes");
        check.eq((e & Editions.CHERRY) != 0, DecorationSource.places(world, "trees_cherry"),
                "the generator places trees_cherry only with the 1.20 biomes");
        OverworldNoise noise = new OverworldNoise(world.getSeed(), e);
        BiomeSource source = new BiomeSource(noise, new ColumnCache(noise), new Climate(e));
        if ((e & Editions.WILD) != 0) {
            int[] c = find(source, Biomes.MANGROVE_SWAMP, 16);
            check.ok(c != null, "a mangrove swamp within 300 chunks");
            if (c != null) {
                Map<String, Integer> n = census(world, c[0], c[1]);
                ok(check, n.getOrDefault("mud", 0) > 100, "mangrove swamp: mud (" + n.get("mud") + ")");
                ok(check, n.getOrDefault("mangrove_log", 0) > 20, "mangrove swamp: mangrove logs (" + n.get("mangrove_log") + ")");
                ok(check, n.getOrDefault("mangrove_leaves", 0) > 100, "mangrove swamp: mangrove leaves (" + n.get("mangrove_leaves") + ")");
                ok(check, n.getOrDefault("mangrove_roots", 0) + n.getOrDefault("muddy_mangrove_roots", 0) > 10,
                        "mangrove swamp: roots (" + n.get("mangrove_roots") + " and " + n.get("muddy_mangrove_roots") + " muddy)");
                ok(check, n.getOrDefault("mangrove_propagule", 0) > 0, "mangrove swamp: hanging propagules (" + n.get("mangrove_propagule") + ")");
            }
            int[] d = find(source, Biomes.DEEP_DARK, -8);
            check.ok(d != null, "a deep dark within 300 chunks");
            if (d != null) {
                BlockPos p = new BlockPos(d[0] * 16 + 8, -32, d[1] * 16 + 8);
                world.getChunkProvider().provideChunk(d[0], d[1]);
                check.ok(generator.biome3d(p.getX(), p.getY(), p.getZ()) == Biomes.DEEP_DARK, "the deep dark is where the core says");
                check.ok(generator.getPossibleCreatures(EnumCreatureType.MONSTER, p).isEmpty()
                        && generator.getPossibleCreatures(EnumCreatureType.CREATURE, p).isEmpty()
                        && generator.getPossibleCreatures(EnumCreatureType.AMBIENT, p).isEmpty(), "the deep dark spawns nothing");
            }
        }
        if ((e & Editions.CHERRY) != 0) {
            int[] c = find(source, Biomes.CHERRY_GROVE, 16);
            check.ok(c != null, "a cherry grove within 300 chunks");
            if (c != null) {
                Map<String, Integer> n = census(world, c[0], c[1]);
                ok(check, n.getOrDefault("cherry_log", 0) > 5, "cherry grove: cherry logs (" + n.get("cherry_log") + ")");
                ok(check, n.getOrDefault("cherry_leaves", 0) > 50, "cherry grove: cherry leaves (" + n.get("cherry_leaves") + ")");
                ok(check, n.getOrDefault("pink_petals", 0) > 20, "cherry grove: pink petals (" + n.get("pink_petals") + ")");
            }
        }
        if (registered("bee_nest")) {
            // Meadows' trees are the super birch with a beehive every time (1.0) or fancy oaks with one.
            int[] c = find(source, Biomes.MEADOW, 16);
            check.ok(c != null, "a meadow within 300 chunks");
            if (c != null) {
                int nests = 0;
                int withBees = 0;
                int wrongBees = 0;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        world.getChunkProvider().provideChunk(c[0] + dx, c[1] + dz);
                    }
                }
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (TileEntity te : world.getChunk(c[0] + dx, c[1] + dz).getTileEntityMap().values()) {
                            ResourceLocation id = world.getBlockState(te.getPos()).getBlock().getRegistryName();
                            if (id == null || !id.getPath().equals("bee_nest")) {
                                continue;
                            }
                            nests++;
                            int bees = te.writeToNBT(new NBTTagCompound()).getTagList("Bees", 10).tagCount();
                            if (bees > 0) {
                                withBees++;
                            }
                            if (bees < 2 || bees > 3) {
                                wrongBees++;
                            }
                        }
                    }
                }
                // The core's own nests in the same chunks: more in the world would be a 1.12 hook's second nest.
                com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline own =
                        new com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline(noise, new Climate(e), 0, 1024);
                own.setBeards(generator.beards());
                int coreNests = 0;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (char code : own.finish(c[0] + dx, c[1] + dz).blocks) {
                            if (com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.name(code).equals("bee_nest")) {
                                coreNests++;
                            }
                        }
                    }
                }
                own.shutdown();
                ok(check, nests == coreNests, "the world has exactly the core's bee nests, no 1.12 hook added more (world "
                        + nests + ", core " + coreNests + ")");
                // Tile entities a chunk makes lazily do not count: the generator makes the nests' own.
                ok(check, nests > 0, "meadow trees have bee nests (" + nests + " in 25 chunks)");
                check.eq(nests, withBees, "every generated bee nest has bees");
                check.eq(0, wrongBees, "every generated bee nest has 2 or 3 bees");
            }
        }
    }

    private static boolean ok(Check check, boolean condition, String what) {
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info("SELFTEST INFO {}", what);
        return check.ok(condition, what);
    }

    private static boolean registered(String path) {
        for (ResourceLocation id : net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.getKeys()) {
            if (id.getPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    /** The nearest chunk to 0, 0 (rings, up to 300 out) whose centre quart at quart y qy is the biome. */
    private static int[] find(BiomeSource source, int biome, int qy) {
        for (int r = 0; r < 300; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r && source.biome(dx * 4 + 2, qy, dz * 4 + 2) == biome) {
                        return new int[]{dx, dz};
                    }
                }
            }
        }
        return null;
    }

    /** Block counts by registry path over the 3 x 3 chunks around one, generated with their neighbors. */
    private static Map<String, Integer> census(WorldServer world, int cx, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getChunkProvider().provideChunk(cx + dx, cz + dz);
            }
        }
        Map<String, Integer> n = new HashMap<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = (cx - 1) * 16; x < (cx + 2) * 16; x++) {
            for (int z = (cz - 1) * 16; z < (cz + 2) * 16; z++) {
                for (int y = 40; y < 140; y++) {
                    IBlockState s = world.getBlockState(p.setPos(x, y, z));
                    ResourceLocation id = s.getBlock().getRegistryName();
                    if (id != null && !id.getNamespace().equals("minecraft")) {
                        n.merge(id.getPath(), 1, Integer::sum);
                    }
                }
            }
        }
        return n;
    }
}
