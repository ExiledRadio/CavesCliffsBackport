package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.terrain.ModernGeneration;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain;
import com.exiledradio.cavesandcliffsbackport.terrain.StateMap;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Other backports' world generation in a world with 1.18 terrain ({@code sh tools/height/selftest.sh terrainocean};
 * with Update Aquatic in {@code eclipse/mods} it tests that mod's generators). In a Default world it only checks that
 * the generator is not Backport Core's {@code ModernGeneration} there.
 *
 * <ul>
 *   <li>The generator is Backport Core's {@code ModernGeneration} for 1.18.2, and its 3D biome is the core's.</li>
 *   <li>Ocean areas (warm, cold, frozen, and with seed -5767635369185270459 the plain ocean at -700, -869 where Update
 *       Aquatic once grew a reef) are generated and populated by the world, every other mod's world generators
 *       included, then compared block for block with a second pipeline that finishes the same chunks in the same
 *       order. Coral, kelp, seagrass and sea pickles are exactly 1.18's: nothing added (no doubled kelp, no reef in
 *       a plain ocean) and nothing missing. The only other changes allowed are inside structures, glow lichen (its
 *       direction order) and bubble columns that stand on magma (1.18.2 post-processes underwater magma).</li>
 *   <li>Every coral block is in or next to a 1.18 warm ocean; kelp keeps as many growth steps as 1.18's.</li>
 *   <li>With Update Aquatic: its {@code OceanKind} answers 1.18's ocean, in three dimensions.</li>
 * </ul>
 */
public final class TerrainOceanSelfTest implements SelfTest {

    /** The seed of the world where a reef grew in a plain ocean. */
    private static final long REEF_SEED = -5767635369185270459L;

    /** Chunks compared per area side; the world loads one more on each side so all of them are populated. */
    private static final int INNER = 5;

    private static final String[] VANILLA_STRUCTURES = {"Stronghold", "Mansion", "Monument", "Village", "Mineshaft",
            "Temple"};

    @Override
    public String name() {
        return "terrainocean";
    }

    /** The order the world asks its generator for chunks, which the second pipeline must repeat. */
    private final List<long[]> order = new ArrayList<>();

    @SubscribeEvent
    public void onLoad(ChunkEvent.Load event) {
        if (!event.getWorld().isRemote && event.getWorld().provider.getDimension() == 0) {
            order.add(new long[]{event.getChunk().x, event.getChunk().z});
        }
    }

    @Override
    public void run(Check check) throws Exception {
        WorldServer world = check.world;
        if (!Heights.isExtended(world) || !ModernTerrain.owns(world)) {
            check.ok(ModernGeneration.of(world) == null, "a world without 1.18 terrain is no ModernGeneration");
            return;
        }
        ModernGenerator generator = (ModernGenerator) world.getChunkProvider().chunkGenerator;
        check.ok(ModernGeneration.of(world) == generator, "the 1.18 generator is Backport Core's ModernGeneration");
        check.eq("1.18.2", generator.generationVersion(), "its generation version");
        int idWrong = 0;
        for (int i = 0; i < 64; i++) {
            int x = i * 131 - 4000;
            int y = (i * 37) % 380 - 64;
            int z = i * 89 - 2000;
            String expected = "minecraft:" + com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes.name(
                    generator.biome3d(x, y, z));
            idWrong += expected.equals(ModernGeneration.biomeId(world, new BlockPos(x, y, z))) ? 0 : 1;
        }
        check.eq(0, idWrong, "ModernGeneration.biomeId is the 3D biome");

        List<int[]> areas = new ArrayList<>();
        List<String> names = new ArrayList<>();
        if (world.getSeed() == REEF_SEED) {
            areas.add(new int[]{-700 >> 4, -869 >> 4});
            names.add("plain ocean at -700, -869");
        }
        for (String biome : new String[]{"warm_ocean", "lukewarm_ocean", "cold_ocean", "frozen_ocean"}) {
            int[] at = find(generator, biome);
            if (check.ok(at != null, "a " + biome + " within 8000 blocks")) {
                areas.add(new int[]{at[0] >> 4, at[1] >> 4});
                names.add(biome + " at " + at[0] + ", " + at[1]);
            }
        }

        // The world generates and populates each area (and runs every IWorldGenerator) as it would for a player.
        order.clear();
        MinecraftForge.EVENT_BUS.register(this);
        try {
            for (int[] a : areas) {
                for (int cx = a[0] - 1; cx <= a[0] + INNER; cx++) {
                    for (int cz = a[1] - 1; cz <= a[1] + INNER; cz++) {
                        world.getChunkProvider().provideChunk(cx, cz);
                    }
                }
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(this);
        }

        collectBoxes(world, generator);
        StateMap map = new StateMap();
        map.resolveAll();
        Pipeline own = new Pipeline(world.getSeed(), 0, 4096);
        own.setBeards(generator.beards());
        Census total = new Census();
        try {
            for (long[] c : order) {
                int cx = (int) c[0];
                int cz = (int) c[1];
                Pipeline.Proto p = own.finish(cx, cz);
                for (int i = 0; i < areas.size(); i++) {
                    int[] a = areas.get(i);
                    if (cx >= a[0] && cx < a[0] + INNER && cz >= a[1] && cz < a[1] + INNER) {
                        compare(world, generator, map, p, cx, cz, total, names.get(i));
                    }
                }
            }
        } finally {
            own.shutdown();
        }
        CavesAndCliffsBackport.LOGGER.info("SELFTEST terrainocean census over {} chunks: {}", total.chunks, total);
        CavesAndCliffsBackport.LOGGER.info("SELFTEST terrainocean other differences by block: {}", total.pairs);
        for (String sample : total.samples) {
            CavesAndCliffsBackport.LOGGER.info("SELFTEST terrainocean differs: {}", sample);
        }
        check.ok(total.chunks >= areas.size() * INNER * INNER, "every area chunk compared (" + total.chunks + ")");
        check.eq(0, total.plantsWrong, "coral, kelp, seagrass and sea pickles are exactly 1.18's"
                + (total.firstPlant == null ? "" : " (first: " + total.firstPlant + ")"));
        check.eq(0, total.otherWrong, "nothing else differs outside structures"
                + (total.firstOther == null ? "" : " (first: " + total.firstOther + ")"));
        // Glow lichen spreads in a direction order every spread in the JVM shuffles (vanilla's too), so the second
        // pipeline's lichen differs from the world's wherever it spreads.
        check.ok(total.lichen < 8 * total.chunks, "glow lichen differs in at most a few blocks a chunk (" + total.lichen
                + ")");
        check.eq(0, total.columnsUngrounded, "every bubble column stands on magma, soul sand or a column");
        check.eq(0, total.coralAway, "every coral block is in or next to a 1.18 warm ocean");
        // Without a backport that adds them, coral is terracotta and kelp and seagrass the water they stand in.
        if (modded("tube_coral_block")) {
            check.ok(total.coral > 0, "the warm ocean has reefs (" + total.coral + " coral blocks)");
        }
        if (modded("kelp") && modded("seagrass")) {
            check.ok(total.kelp > 0 && total.seagrass > 0, "the oceans have kelp (" + total.kelp + ") and seagrass ("
                    + total.seagrass + ")");
        }
        check.eq(0, total.kelpAgeWrong, "generated kelp keeps 1.18's growth steps to its top");
        if (world.getSeed() == REEF_SEED) {
            reefSpot(check, world, total);
        }
        oceanKind(check, world, generator);
    }

    /** The column where the reef grew: 1.18's plain ocean, and no coral in its chunk. */
    private static void reefSpot(Check check, WorldServer world, Census total) {
        check.eq("minecraft:ocean", ModernGeneration.biomeId(world, new BlockPos(-700, 62, -869)),
                "1.18's biome at -700, 62, -869");
        check.eq(0, total.reefSpotCoral, "no coral in the chunk at -700, -869");
    }

    /** Update Aquatic's OceanKind answers 1.18's oceans (reflection: no dependency on it). */
    private static void oceanKind(Check check, WorldServer world, ModernGenerator generator) throws Exception {
        if (!Loader.isModLoaded("updateaquaticbackport")) {
            return;
        }
        Method at = Class.forName("com.exiledradio.updateaquaticbackport.common.OceanKind")
                .getMethod("at", World.class, BlockPos.class);
        String[][] kinds = {{"warm_ocean", "WARM"}, {"lukewarm_ocean", "LUKEWARM"}, {"deep_lukewarm_ocean", "LUKEWARM"},
                {"ocean", "NORMAL"}, {"deep_ocean", "NORMAL"}, {"cold_ocean", "COLD"}, {"deep_cold_ocean", "COLD"},
                {"frozen_ocean", "FROZEN"}, {"deep_frozen_ocean", "FROZEN"}, {"lush_caves", "NONE"},
                {"plains", "NONE"}};
        int wrong = 0;
        int seen = 0;
        String first = null;
        for (int i = 0; i < 4000; i++) {
            int x = (i % 64) * 197 - 6000;
            int z = (i / 64) * 211 - 6000;
            int y = i % 3 == 0 ? 20 : 60;
            BlockPos pos = new BlockPos(x, y, z);
            String id = ModernGeneration.biomeId(world, pos).substring("minecraft:".length());
            for (String[] k : kinds) {
                if (k[0].equals(id)) {
                    seen++;
                    String got = String.valueOf(at.invoke(null, world, pos));
                    if (!k[1].equals(got)) {
                        wrong++;
                        if (first == null) {
                            first = pos + " " + id + " is " + got;
                        }
                    }
                }
            }
        }
        check.ok(seen > 100, "positions in oceans and other biomes sampled (" + seen + ")");
        check.eq(0, wrong, "Update Aquatic's OceanKind is 1.18's ocean in three dimensions"
                + (first == null ? "" : " (first: " + first + ")"));
    }

    /** @return true if some mod registers a block under this 1.18 name */
    private static boolean modded(String name) {
        for (Block b : Block.REGISTRY) {
            if (b.getRegistryName() != null && b.getRegistryName().getPath().equals(name)
                    && !b.getRegistryName().getNamespace().equals("minecraft")) {
                return true;
            }
        }
        return false;
    }

    /** A column of this 1.18 biome at sea level whose surroundings (48 blocks each way) are the same biome. */
    private static int[] find(ModernGenerator generator, String biome) {
        String id = "minecraft:" + biome;
        for (int r = 0; r <= 8000; r += 96) {
            for (int dx = -r; dx <= r; dx += 96) {
                for (int dz = -r; dz <= r; dz += 96) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = dx + 2000;
                    int z = dz + 2000;
                    if (id.equals(generator.biomeId(x, 60, z)) && id.equals(generator.biomeId(x + 48, 60, z + 48))
                            && id.equals(generator.biomeId(x + 96, 60, z + 96))
                            && id.equals(generator.biomeId(x, 60, z + 96)) && id.equals(generator.biomeId(x + 96, 60, z))) {
                        return new int[]{x, z};
                    }
                }
            }
        }
        return null;
    }

    private static final class Census {
        int chunks;
        int plantsWrong;
        int otherWrong;
        int structure;
        int lichen;
        int columns;
        int columnsUngrounded;
        int coral;
        int coralAway;
        int kelp;
        int kelpAgeWrong;
        int seagrass;
        int pickles;
        int expectedCoral;
        int expectedKelp;
        int expectedSeagrass;
        int reefSpotCoral;
        String firstPlant;
        String firstOther;
        final java.util.Map<String, Integer> pairs = new java.util.TreeMap<>();
        final List<String> samples = new ArrayList<>();

        @Override
        public String toString() {
            return "coral " + coral + " (1.18: " + expectedCoral + "), kelp " + kelp + " (1.18: " + expectedKelp
                    + "), seagrass " + seagrass + " (1.18: " + expectedSeagrass + "), sea pickles " + pickles
                    + ", bubble columns " + columns + ", plants wrong " + plantsWrong + ", other wrong " + otherWrong
                    + ", in structures " + structure + ", lichen " + lichen;
        }
    }

    /** What kind of plant a block is: 0 none, 1 coral, 2 kelp, 3 seagrass, 4 sea pickle. */
    private static int plant(IBlockState state) {
        Block b = state.getBlock();
        if (b.getRegistryName() == null) {
            return 0;
        }
        String path = b.getRegistryName().getPath();
        if (path.contains("coral")) {
            return path.startsWith("dead_") ? 0 : 1;
        }
        if (path.equals("kelp") || path.equals("kelp_plant")) {
            return 2;
        }
        if (path.equals("seagrass") || path.equals("tall_seagrass")) {
            return 3;
        }
        return path.equals("sea_pickle") ? 4 : 0;
    }

    private static boolean isColumn(IBlockState state) {
        return state.getBlock().getRegistryName() != null
                && state.getBlock().getRegistryName().getPath().equals("bubble_column");
    }

    /** The whole boxes of every structure start (a piece that failed to build is dropped from its start's list). */
    private static List<net.minecraft.world.gen.structure.StructureBoundingBox> boxes;

    private static void collectBoxes(WorldServer world, ModernGenerator generator) throws Exception {
        boxes = new ArrayList<>();
        List<net.minecraft.world.gen.structure.MapGenStructure> gens = new ArrayList<>();
        for (java.lang.reflect.Field f : ModernGenerator.class.getDeclaredFields()) {
            if (net.minecraft.world.gen.structure.MapGenStructure.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                gens.add((net.minecraft.world.gen.structure.MapGenStructure) f.get(generator));
            }
        }
        for (String kind : ScatteredStructure.kinds()) {
            ScatteredStructure s = ScatteredStructure.of(world, kind);
            if (s != null) {
                gens.add(s);
            }
        }
        java.lang.reflect.Field map = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
                net.minecraft.world.gen.structure.MapGenStructure.class, "structureMap", "field_75053_d");
        for (net.minecraft.world.gen.structure.MapGenStructure g : gens) {
            if (g == null) {
                continue;
            }
            for (Object o : ((java.util.Map<?, ?>) map.get(g)).values()) {
                net.minecraft.world.gen.structure.StructureStart start = (net.minecraft.world.gen.structure.StructureStart) o;
                if (start.isSizeableStructure()) {
                    boxes.add(start.getBoundingBox());
                }
            }
        }
        CavesAndCliffsBackport.LOGGER.info("SELFTEST terrainocean {} structure starts from {} generators", boxes.size(),
                gens.size());
    }

    private static boolean inStructure(WorldServer world, ModernGenerator generator, BlockPos pos) {
        for (net.minecraft.world.gen.structure.StructureBoundingBox box : boxes) {
            if (box.isVecInside(pos)) {
                return true;
            }
        }
        for (String name : VANILLA_STRUCTURES) {
            if (generator.isInsideStructure(world, name, pos)) {
                return true;
            }
        }
        for (String kind : ScatteredStructure.kinds()) {
            if (ScatteredStructure.isInside(world, kind, pos)) {
                return true;
            }
        }
        return false;
    }

    private static void compare(WorldServer world, ModernGenerator generator, StateMap map, Pipeline.Proto p, int cx,
                                int cz, Census census, String area) {
        census.chunks++;
        Chunk chunk = world.getChunk(cx, cz);
        boolean reefSpot = cx == -700 >> 4 && cz == -869 >> 4;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = -64; y < 320; y++) {
                    IBlockState expected = map.state(p.get(x, y, z));
                    IBlockState actual = chunk.getBlockState(x, y, z);
                    int was = plant(expected);
                    int is = plant(actual);
                    census.expectedCoral += was == 1 ? 1 : 0;
                    census.expectedKelp += was == 2 ? 1 : 0;
                    census.expectedSeagrass += was == 3 ? 1 : 0;
                    BlockPos pos = new BlockPos(cx * 16 + x, y, cz * 16 + z);
                    if (is == 1) {
                        census.coral++;
                        census.reefSpotCoral += reefSpot ? 1 : 0;
                        if (!nearWarm(generator, pos)) {
                            census.coralAway++;
                        }
                    } else if (is == 2) {
                        census.kelp++;
                        if (actual == expected && actual.getBlock().getRegistryName().getPath().equals("kelp")
                                && !kelpAgeRight(actual, p.get(x, y, z), map)) {
                            census.kelpAgeWrong++;
                        }
                    } else if (is == 3) {
                        census.seagrass++;
                    } else if (is == 4) {
                        census.pickles++;
                    }
                    if (isColumn(actual)) {
                        census.columns++;
                        IBlockState below = chunk.getBlockState(x, y - 1, z);
                        if (!isColumn(below) && below.getBlock() != Blocks.MAGMA && below.getBlock() != Blocks.SOUL_SAND) {
                            census.columnsUngrounded++;
                        }
                    }
                    if (expected == actual) {
                        continue;
                    }
                    if (isColumn(actual) && expected.getBlock() == Blocks.WATER) {
                        continue;
                    }
                    String what = area + ", " + pos + ": " + actual + " for " + expected;
                    if (path(expected).startsWith("glow_lichen") || path(actual).startsWith("glow_lichen")) {
                        census.lichen++;
                    } else if (inStructure(world, generator, pos)) {
                        census.structure++;
                    } else if (was != 0 || is != 0) {
                        census.plantsWrong++;
                        if (census.firstPlant == null) {
                            census.firstPlant = what;
                        }
                    } else {
                        census.otherWrong++;
                        if (census.firstOther == null) {
                            census.firstOther = what;
                        }
                        String pair = path(actual) + " for " + path(expected);
                        census.pairs.put(pair, census.pairs.getOrDefault(pair, 0) + 1);
                        if (census.samples.size() < 30) {
                            census.samples.add(what);
                        }
                    }
                }
            }
        }
    }

    private static String path(IBlockState state) {
        return state.getBlock().getRegistryName() == null ? "" : state.getBlock().getRegistryName().getPath();
    }

    /** In or within 12 blocks of a 1.18 warm ocean at this height (a reef reaches across a biome border). */
    private static boolean nearWarm(ModernGenerator generator, BlockPos pos) {
        for (int dx = -12; dx <= 12; dx += 4) {
            for (int dz = -12; dz <= 12; dz += 4) {
                if ("minecraft:warm_ocean".equals(generator.biomeId(pos.getX() + dx, pos.getY(), pos.getZ() + dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A kelp top with fewer ages than 1.18's 0-25 has the same number of growth steps left as 1.18's. */
    private static boolean kelpAgeRight(IBlockState state, int code, StateMap map) {
        String full = com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.state(code);
        int at = full.indexOf("age=");
        if (at < 0) {
            return true;
        }
        int end = at + 4;
        while (end < full.length() && Character.isDigit(full.charAt(end))) {
            end++;
        }
        int modern = Integer.parseInt(full.substring(at + 4, end));
        for (IProperty<?> prop : state.getPropertyKeys()) {
            if (prop.getName().equals("age")) {
                int max = 0;
                for (Object v : prop.getAllowedValues()) {
                    max = Math.max(max, ((Number) v).intValue());
                }
                int age = ((Number) state.getValue(prop)).intValue();
                return max - age == Math.min(max, 25 - modern);
            }
        }
        return true;
    }
}
