package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.terrain.TerrainHeights;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain;
import com.exiledradio.cavesandcliffsbackport.terrain.StateMap;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Structures;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;

/**
 * Stream G: the 1.18 chunk generator of a Caves &amp; Cliffs world ({@code sh tools/height/selftest.sh terrain}). In a
 * Default world it only checks that the generator is not in charge there.
 *
 * <ul>
 *   <li>A chunk far from anything generated holds exactly what the terrain core finishes for it (a second pipeline,
 *       one thread, the same order), block for block from y -64 to 319, mapped to 1.12 states; and its biome array is
 *       1.18's biome at each column's top, as 1.12 biomes.</li>
 *   <li>Bedrock at y -64, deepslate below 0.</li>
 *   <li>1.12's structures start where 1.18 puts them ({@code /locate} answers starts the core confirms).</li>
 *   <li>Backport Core's TerrainHeights reads this generator's surface; the 1.17 generators step aside.</li>
 * </ul>
 */
public final class TerrainSelfTest implements SelfTest {

    private static final int CX = 3000;
    private static final int CZ = -3000;

    @Override
    public String name() {
        return "terrain";
    }

    @Override
    public void run(Check check) throws Exception {
        WorldServer world = check.world;
        if (!Heights.isExtended(world)) {
            check.ok(!ModernTerrain.owns(world), "a Default world does not get the 1.18 generator");
            return;
        }
        check.ok(ModernTerrain.owns(world), "a new Caves & Cliffs world gets the 1.18 generator");
        if (!ModernTerrain.owns(world)) {
            return;
        }
        ModernGenerator generator = (ModernGenerator) world.getChunkProvider().chunkGenerator;
        exact(check, world, generator);
        NewerBiomesSelfTest.run(check, world, generator);
        structures(check, world, generator);
        TerrainHeights heights = TerrainHeights.forWorld(world);
        check.ok(heights != null, "Backport Core's TerrainHeights has a sampler for this world");
        if (heights != null) {
            int wrong = 0;
            for (int i = 0; i < 64; i++) {
                int x = i * 97 - 3000;
                int z = i * 61 + 500;
                wrong += heights.height(x, z) != generator.surface(x, z) ? 1 : 0;
            }
            check.eq(0, wrong, "TerrainHeights answers the generator's surface");
        }
        BlockPos spawn = world.getSpawnPoint();
        int top = generator.surface(spawn.getX(), spawn.getZ());
        check.ok(top > 63 && !generator.wet(spawn.getX(), spawn.getZ()), "the spawn is on dry land (" + spawn + ")");
    }

    /** One chunk, block for block, against a second pipeline. */
    private static void exact(Check check, WorldServer world, ModernGenerator generator) {
        Chunk chunk = world.getChunkProvider().provideChunk(CX, CZ);
        int e = generator.editions();
        Pipeline own = new Pipeline(new com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise(world.getSeed(), e),
                new com.exiledradio.cavesandcliffsbackport.terrain.core.Climate(e), 0, 1024);
        own.setBeards(generator.beards());
        Pipeline.Proto p = own.finish(CX, CZ);
        StateMap map = new StateMap();
        map.resolveAll();
        check.ok(map.unresolved.isEmpty(), "every 1.18 state the core makes has a 1.12 block "
                + (map.unresolved.isEmpty() ? "" : map.unresolved.toString()));
        int wrong = 0;
        int lichen = 0;
        String first = null;
        int bedrock = 0;
        int deepslate = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = -64; y < 320; y++) {
                    IBlockState expected = map.state(p.get(x, y, z));
                    IBlockState actual = chunk.getBlockState(x, y, z);
                    if (expected != actual) {
                        // Glow lichen spreads from a direction order every spread in the JVM shuffles (vanilla's too).
                        if (expected.getBlock().getRegistryName().getPath().startsWith("glow_lichen")
                                || actual.getBlock().getRegistryName().getPath().startsWith("glow_lichen")) {
                            lichen++;
                        } else {
                            wrong++;
                            if (first == null) {
                                first = x + "," + y + "," + z + ": " + actual + " for " + expected;
                            }
                        }
                    }
                }
                bedrock += chunk.getBlockState(x, -64, z).getBlock() == Blocks.BEDROCK ? 1 : 0;
                deepslate += chunk.getBlockState(x, -8, z).getBlock().getRegistryName().getPath().equals("deepslate") ? 1 : 0;
            }
        }
        own.shutdown();
        check.eq(0, wrong, "chunk " + CX + "," + CZ + " holds the core's blocks" + (first == null ? "" : " (first: " + first + ")"));
        check.ok(lichen < 16, "glow lichen differs in at most a few blocks (" + lichen + ")");
        check.eq(256, bedrock, "bedrock at y -64 in every column");
        check.ok(deepslate > 0, "deepslate below y 0 (" + deepslate + " columns at y -8)");
        byte[] biomes = chunk.getBiomeArray();
        int biomeWrong = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Biome b = Biome.getBiome(biomes[z << 4 | x] & 255);
                if (b == null) {
                    biomeWrong++;
                }
            }
        }
        check.eq(0, biomeWrong, "every column has a 1.12 biome");
        check.ok(Biomes.name(p.surfaceBiomes[0]) != null, "the core names the column's 1.18 biome");
    }

    /** /locate's answers are starts the core confirms. */
    private static void structures(Check check, WorldServer world, ModernGenerator generator) {
        ColumnCache columns = new ColumnCache(new com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise(world.getSeed()));
        com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise noise =
                new com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise(world.getSeed());
        columns = new ColumnCache(noise);
        Structures core = new Structures(noise, columns,
                new BiomeSource(noise, columns, new com.exiledradio.cavesandcliffsbackport.terrain.core.Climate()));
        String[] names = {"Village", "Temple", "Monument", "Mansion", "Stronghold", "Mineshaft"};
        int[][] sets = {{Structures.VILLAGES}, {Structures.DESERT_PYRAMIDS, Structures.JUNGLE_TEMPLES, Structures.SWAMP_HUTS,
            Structures.IGLOOS}, {Structures.OCEAN_MONUMENTS}, {Structures.WOODLAND_MANSIONS}, {Structures.STRONGHOLDS},
            {Structures.MINESHAFTS}};
        for (int i = 0; i < names.length; i++) {
            BlockPos at = generator.getNearestStructurePos(world, names[i], BlockPos.ORIGIN, false);
            if (!check.ok(at != null, "/locate " + names[i] + " finds one")) {
                continue;
            }
            boolean confirmed = false;
            for (int set : sets[i]) {
                confirmed |= set == Structures.STRONGHOLDS ? core.isFeatureChunk(set, at.getX() >> 4, at.getZ() >> 4)
                        : core.startAt(set, at.getX() >> 4, at.getZ() >> 4) >= 0;
            }
            check.ok(confirmed, names[i] + " at " + at + " is a 1.18 start");
        }
    }
}
