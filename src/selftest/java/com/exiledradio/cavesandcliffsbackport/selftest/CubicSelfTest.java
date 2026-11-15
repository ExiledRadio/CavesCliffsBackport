package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.cubic.CubicSupport;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Cubic Chunks: a cubic Caves &amp; Cliffs world holds the same blocks as a normal (extended) one of the same seed.
 * Each phase runs only when named, on a server of its own ({@code tools/cubic/compare.sh}):
 * <ul>
 *   <li>{@code cubic} (Cubic Chunks installed, fresh Caves &amp; Cliffs world): the world's limits and every height
 *       question; then the area's columns generated in a fixed order (every cube) and populated, and its blocks from
 *       -64 to 319 written to {@code eclipse/cubic-genpop.dat}; at every world save the area as saved goes to
 *       {@code eclipse/cubic-saved.dat}.</li>
 *   <li>{@code cubicreload} (same world, next start): the area read back from disk equals {@code cubic-saved.dat},
 *       and no cube was generated again.</li>
 *   <li>{@code cubicref} (no Cubic Chunks, fresh Caves &amp; Cliffs world, same seed): the same area generated and
 *       populated in the same order by the normal generator, block for block against {@code cubic-genpop.dat}.</li>
 *   <li>{@code cubicbench}: generation and population of a square, in either kind of world.</li>
 * </ul>
 */
public final class CubicSelfTest implements SelfTest {

    /** The area: columns X0..X0+N by Z0..Z0+N generated, X0..X0+N-1 populated, X0+1..X0+N-1 compared. */
    static final int X0 = 300;
    static final int Z0 = -200;
    static final int N = 12;
    static final int MIN_Y = -64;
    static final int MAX_Y = 320;
    private static final int[] LICHEN = {0, 1, 2, 3, 4, 5};

    private final String name;

    public CubicSelfTest(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void run(Check check) throws Exception {
        String asked = System.getProperty(CavesAndCliffsBackport.MOD_ID + ".selftest", "");
        if (!java.util.Arrays.asList(asked.split(",")).contains(name)) {
            return;
        }
        WorldServer world = check.world;
        File dir = world.getMinecraftServer().getFile(".");
        switch (name) {
            case "cubic":
                cubic(check, world, dir);
                break;
            case "cubicreload":
                reload(check, world, dir);
                break;
            case "cubicref":
                reference(check, world, dir);
                break;
            case "cubicbench":
                bench(check, world);
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static void cubic(Check check, WorldServer world, File dir) throws IOException {
        if (!check.ok(CubicSupport.on() && CubicTestOps.isCubic(world), "the world is a cubic Caves & Cliffs world "
                + "(run with -Pcubicchunks, level-type=cavesandcliffs, a fresh world)")) {
            return;
        }
        check.eq(-64, Heights.minY(world), "Heights.minY");
        check.eq(320, Heights.maxY(world), "Heights.maxY");
        check.ok(Heights.isTall(world), "Heights.isTall");
        check.ok(!Heights.isExtended(world), "not the coremod's section layout");
        check.eq(-64, com.exiledradio.backportcore.terrain.WorldHeights.minY(world), "Core's WorldHeights.minY");
        check.eq(320, com.exiledradio.backportcore.terrain.WorldHeights.maxYExclusive(world), "Core's WorldHeights.maxYExclusive");
        check.ok(!com.exiledradio.backportcore.terrain.WorldHeights.isCavesAndCliffsExtended(world),
                "Core keeps Fluidlogged API's own (cubic) fluid storage");
        check.eq("-64..320", CubicTestOps.limits(world), "Cubic Chunks' own limits");
        check.ok(ModernTerrain.owns(world), "the 1.18 generator owns the world");
        check.ok(world.isOutsideBuildHeight(new BlockPos(0, -65, 0)) && world.isOutsideBuildHeight(new BlockPos(0, 320, 0))
                && !world.isOutsideBuildHeight(new BlockPos(0, -64, 0)) && !world.isOutsideBuildHeight(new BlockPos(0, 319, 0)),
                "build height is -64 to 319");
        ModernGenerator gen = (ModernGenerator) world.getChunkProvider().chunkGenerator;
        check.ok(gen == ModernTerrain.generator(world), "one 1.18 generator for the world");

        // Glow lichen's direction order is the JVM's (every spread shuffles it), and the two worlds' spawn areas
        // differ in size; both phases start the area from the same order.
        com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline.setLichenOrder(LICHEN);
        long t = System.nanoTime();
        CubicTestOps.generate(world, X0, Z0, N);
        CubicTestOps.populate(world, X0, Z0, N);
        CavesAndCliffsBackport.LOGGER.info("CUBIC area generated and populated in {} ms; {}", (System.nanoTime() - t) / 1000000,
                CubicTestOps.stats(world));
        check.eq(0, CubicTestOps.held(world), "every finished column's cubes are made");
        Dump d = Dump.read(world);
        d.write(new File(dir, "cubic-genpop.dat"));

        // Bedrock at -64, deepslate under 0, air at 319 over the area's middle; blocks at the limits.
        BlockPos mid = new BlockPos((X0 + N / 2) * 16 + 8, 0, (Z0 + N / 2) * 16 + 8);
        check.eq(Blocks.BEDROCK, world.getBlockState(new BlockPos(mid.getX(), -64, mid.getZ())).getBlock(), "bedrock at y -64");
        int deep = 0;
        for (int y = -60; y < -8; y++) {
            deep += String.valueOf(world.getBlockState(new BlockPos(mid.getX(), y, mid.getZ())).getBlock().getRegistryName())
                    .contains("deepslate") ? 1 : 0;
        }
        check.ok(deep > 0, "deepslate below 0 (" + deep + " of 52)");
        BlockPos low = new BlockPos(mid.getX() + 1, -64, mid.getZ());
        BlockPos high = new BlockPos(mid.getX() + 1, 319, mid.getZ());
        world.setBlockState(low, Blocks.GOLD_BLOCK.getDefaultState());
        world.setBlockState(high, Blocks.GOLD_BLOCK.getDefaultState());
        check.ok(world.getBlockState(low).getBlock() == Blocks.GOLD_BLOCK && world.getBlockState(high).getBlock() == Blocks.GOLD_BLOCK,
                "blocks set at y -64 and 319");
        check.ok(!world.setBlockState(new BlockPos(mid.getX(), 320, mid.getZ()), Blocks.STONE.getDefaultState()),
                "no block at y 320");
        world.setBlockState(low, Blocks.BEDROCK.getDefaultState());
        world.setBlockState(high, Blocks.AIR.getDefaultState());
        // Light at the top under open sky.
        check.eq(15, world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY, high), "sky light at y 319");

        // The world's systems that ask the height.
        int biome = ModernTerrain.biomeAt(world, mid.getX(), -40, mid.getZ());
        check.ok(biome >= 0, "1.18's 3D biome answers below 0 (" + biome + ")");
        BlockPos spawn = world.getSpawnPoint();
        IBlockState under = world.getBlockState(spawn.down());
        check.ok(under.getMaterial().blocksMovement() && !under.getMaterial().isLiquid(), "spawn on solid ground: " + spawn + " " + under);
        int[] climate = gen.climateSpawn();
        check.ok(Math.abs((spawn.getX() >> 4) - (climate[0] >> 4)) <= 5 && Math.abs((spawn.getZ() >> 4) - (climate[1] >> 4)) <= 5,
                "spawn within 1.18's search of the climate's spawn " + climate[0] + ", " + climate[1]);
        BlockPos village = world.findNearestStructure("Village", mid, false);
        check.ok(village != null, "/locate Village answers: " + village);
        BlockPos mineshaft = world.findNearestStructure("Mineshaft", mid, false);
        check.ok(mineshaft != null, "/locate Mineshaft answers: " + mineshaft);
        com.exiledradio.backportcore.structure.StartRule rule = gen.startRule(world,
                com.exiledradio.backportcore.structure.Structures.SHIPWRECK);
        check.ok(rule != null, "Core's start rules come from the generator");

        // Saves: the area as each save writes it (the last one is the shutdown's, after which nothing ticks).
        MinecraftForge.EVENT_BUS.register(new SavedDump(world, new File(dir, "cubic-saved.dat")));
        check.ok(true, "area dumped (" + d.size() + " blocks, " + d.palette.size() + " states)");
    }

    /** Writes the area at every save of the overworld, as it is saved. */
    public static final class SavedDump {
        private final WorldServer world;
        private final File file;

        SavedDump(WorldServer world, File file) {
            this.world = world;
            this.file = file;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onSave(WorldEvent.Save event) {
            if (event.getWorld() != world) {
                return;
            }
            try {
                Dump.read(world).write(file);
                CavesAndCliffsBackport.LOGGER.info("CUBIC area written as saved");
            } catch (IOException e) {
                CavesAndCliffsBackport.LOGGER.error("CUBIC saved dump failed", e);
            }
        }
    }

    private static void reload(Check check, WorldServer world, File dir) throws IOException {
        if (!check.ok(CubicSupport.on() && CubicTestOps.isCubic(world), "the world is a cubic Caves & Cliffs world")) {
            return;
        }
        File file = new File(dir, "cubic-saved.dat");
        if (!check.ok(file.isFile(), "cubic-saved.dat from the cubic phase")) {
            return;
        }
        long made = CubicTestOps.cubesMade(world);
        Dump now = Dump.read(world);
        check.eq(made, CubicTestOps.cubesMade(world), "no cube of the area generated again");
        compare(check, Dump.load(file), now, "reloaded");
        check.eq("-64..320", CubicTestOps.limits(world), "Cubic Chunks' limits kept");
    }

    private static void reference(Check check, WorldServer world, File dir) throws IOException {
        if (!check.ok(!CubicSupport.PRESENT && Heights.isExtended(world) && ModernTerrain.owns(world),
                "a normal Caves & Cliffs world (no Cubic Chunks, fresh)")) {
            return;
        }
        File file = new File(dir, "cubic-genpop.dat");
        if (!check.ok(file.isFile(), "cubic-genpop.dat from the cubic phase")) {
            return;
        }
        // The same order: every column of the area, row by row; 1.12 populates each as its +x, +z neighbors arrive.
        com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline.setLichenOrder(LICHEN);
        long t = System.nanoTime();
        for (int x = X0; x <= X0 + N; x++) {
            for (int z = Z0; z <= Z0 + N; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }
        CavesAndCliffsBackport.LOGGER.info("CUBICREF area generated and populated in {} ms", (System.nanoTime() - t) / 1000000);
        Dump now = Dump.read(world);
        now.write(new File(dir, "cubic-reference.dat"));
        compare(check, Dump.load(file), now, "normal world against cubic");
    }

    /** Block for block, every block equal; differences are logged by state pair (glow lichen counted apart). */
    private static void compare(Check check, Dump expected, Dump actual, String what) {
        int diff = 0;
        int lichen = 0;
        Map<String, Integer> pairs = new LinkedHashMap<>();
        List<String> where = new ArrayList<>();
        int i = 0;
        for (int x = 0; x < Dump.SIDE * 16; x++) {
            for (int z = 0; z < Dump.SIDE * 16; z++) {
                for (int y = MIN_Y; y < MAX_Y; y++, i++) {
                    String a = expected.palette.get(expected.data[i]);
                    String b = actual.palette.get(actual.data[i]);
                    if (!a.equals(b)) {
                        diff++;
                        if (a.contains("glow_lichen") || b.contains("glow_lichen")) {
                            lichen++;
                        }
                        pairs.merge(a + " -> " + b, 1, Integer::sum);
                        if (where.size() < 12 && !(a.contains("glow_lichen") || b.contains("glow_lichen"))) {
                            where.add(((X0 + 1) * 16 + x) + "," + y + "," + ((Z0 + 1) * 16 + z));
                        }
                    }
                }
            }
        }
        CavesAndCliffsBackport.LOGGER.info("CUBIC compare {}: {} of {} blocks differ ({} glow lichen)", what, diff, i, lichen);
        int n = 0;
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(pairs.entrySet());
        sorted.sort((p, q) -> Boolean.compare(p.getKey().contains("glow_lichen"), q.getKey().contains("glow_lichen")));
        for (Map.Entry<String, Integer> e : sorted) {
            if (n++ < 25) {
                CavesAndCliffsBackport.LOGGER.info("CUBIC   {} x{}", e.getKey(), e.getValue());
            }
        }
        if (!where.isEmpty()) {
            CavesAndCliffsBackport.LOGGER.info("CUBIC   first at {}", where);
        }
        check.ok(diff == 0, what + ": every block equal (" + diff + " differ, " + lichen + " glow lichen; pairs " + (pairs.size() > 6 ? new ArrayList<>(pairs.keySet()).subList(0, 6) : pairs.keySet()) + ")");
    }

    // ---------------------------------------------------------------------------------------------

    /** The benchmark square: SIDE x SIDE columns generated and populated at chunk START (EXTENDED_HEIGHT 4's). */
    private static final int BENCH_SIDE = 24;
    private static final int BENCH_START = 1000;

    private static void bench(Check check, WorldServer world) {
        // Cubic Chunks' own VanillaCubic type too (level-type=VanillaCubic), for its lighting's cost on its terrain.
        boolean cubic = CubicSupport.PRESENT && CubicTestOps.isCubic(world);
        ModernGenerator gen = world.getChunkProvider().chunkGenerator instanceof ModernGenerator
                ? (ModernGenerator) world.getChunkProvider().chunkGenerator : null;
        // Warm-up: the JIT, and every class the generators load.
        square(world, cubic, BENCH_START - 200, 8);
        long t0 = System.nanoTime();
        square(world, cubic, BENCH_START, BENCH_SIDE);
        long t1 = System.nanoTime();
        long light = 0;
        if (cubic) {
            CubicTestOps.light(world, BENCH_START, BENCH_START, BENCH_SIDE);
            light = System.nanoTime() - t1;
        }
        int n = BENCH_SIDE * BENCH_SIDE;
        if (gen != null) {
            CavesAndCliffsBackport.LOGGER.info("TERRAINSTATS " + gen.stats() + (cubic ? " " + CubicTestOps.stats(world) : ""));
        }
        CavesAndCliffsBackport.LOGGER.info(String.format(java.util.Locale.ROOT,
                "CUBICBENCH mode=%s chunks=%d gen_ms_per_chunk=%.3f chunks_per_s=%.1f light_ms_per_chunk=%.3f",
                gen == null ? "cubic-chunks-own(" + world.getWorldType().getName() + ")" : cubic ? "cubic" : "extended", n, (t1 - t0) / 1e6 / n, n / ((t1 - t0) / 1e9), light / 1e6 / n));
        check.ok(true, "benchmark ran");
    }

    /** Generates and populates the side x side square at chunk (start, start), with one more row and column. */
    private static void square(WorldServer world, boolean cubic, int start, int side) {
        if (cubic) {
            CubicTestOps.generate(world, start, start, side);
            CubicTestOps.populate(world, start, start, side);
            return;
        }
        for (int x = start; x <= start + side; x++) {
            for (int z = start; z <= start + side; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** The compared area's blocks, x, z, then y from -64 up, as indices into a palette of state strings. */
    static final class Dump {
        static final int SIDE = N - 1;
        final List<String> palette = new ArrayList<>();
        final short[] data;

        Dump(int size) {
            data = new short[size];
        }

        int size() {
            return data.length;
        }

        static Dump read(WorldServer world) {
            Dump d = new Dump(SIDE * 16 * SIDE * 16 * (MAX_Y - MIN_Y));
            Map<IBlockState, Short> ids = new HashMap<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int i = 0;
            int bx = (X0 + 1) * 16;
            int bz = (Z0 + 1) * 16;
            for (int x = 0; x < SIDE * 16; x++) {
                for (int z = 0; z < SIDE * 16; z++) {
                    Chunk chunk = world.getChunk((bx + x) >> 4, (bz + z) >> 4);
                    for (int y = MIN_Y; y < MAX_Y; y++) {
                        pos.setPos(bx + x, y, bz + z);
                        // As saved: properties only getActualState knows (grass's snowy, a plant's upper half) are
                        // not kept by a save in any world.
                        IBlockState s = chunk.getBlockState(pos);
                        s = s.getBlock().getStateFromMeta(s.getBlock().getMetaFromState(s));
                        Short id = ids.get(s);
                        if (id == null) {
                            id = (short) d.palette.size();
                            d.palette.add(s.toString());
                            ids.put(s, id);
                        }
                        d.data[i++] = id;
                    }
                }
            }
            return d;
        }

        void write(File file) throws IOException {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))))) {
                out.writeInt(palette.size());
                for (String s : palette) {
                    out.writeUTF(s);
                }
                out.writeInt(data.length);
                for (short v : data) {
                    out.writeShort(v);
                }
            }
        }

        static Dump load(File file) throws IOException {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
                int p = in.readInt();
                List<String> palette = new ArrayList<>();
                for (int i = 0; i < p; i++) {
                    palette.add(in.readUTF());
                }
                Dump d = new Dump(in.readInt());
                d.palette.addAll(palette);
                for (int i = 0; i < d.data.length; i++) {
                    d.data[i] = in.readShort();
                }
                return d;
            }
        }
    }
}
