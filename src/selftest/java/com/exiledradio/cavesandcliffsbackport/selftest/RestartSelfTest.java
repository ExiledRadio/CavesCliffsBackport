package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Decoration across a restart (stream G's held chunks): {@code sh tools/terrain/restart.sh} runs three servers in a
 * Caves &amp; Cliffs world.
 *
 * <ol>
 *   <li>{@code restartwhole}, fresh world: generates area A (12x12 chunks at 2000, 2000) and then area B (the 12x12
 *       east of it) in one run, and writes every chunk's finished 1.18 blocks' hash ({@code restart-whole.txt}).</li>
 *   <li>{@code restart1}, fresh world: generates A, then the server stops (and saves).</li>
 *   <li>{@code restart2}, the same world: generates B, writes {@code restart-split.txt}, and checks that every chunk
 *       of A and B is the same as in the run that never stopped: the features crossing from A into B, decorated
 *       before the restart into chunks that were only held, came back from the store.</li>
 * </ol>
 *
 * <p>Then the same across a crash ({@code crash1}, {@code crash2}): the server is killed ({@code Runtime.halt}, no
 * save, no shutdown) at a moment a crash can come at, and the next run must still decorate as the run that never
 * stopped.
 * <ol>
 *   <li>{@code crash1}, fresh world: generates the west half of A; the world saves (chunks, then the held chunks);
 *       generates the east half of A; 1.12 writes its chunks to disk as an autosave or unloading does, but the server
 *       dies before the held chunks are saved; it first generates the two west columns of B, which never reach the
 *       disk. Writes {@code restart-first.txt} and its report, then halts.</li>
 *   <li>{@code crash2}, the same world: generates B (its two west columns again) and compares as {@code restart2}
 *       does.</li>
 * </ol>
 */
public final class RestartSelfTest implements SelfTest {

    private static final int X = 2000;
    private static final int Z = 2000;
    private static final int SIDE = 12;

    private final String phase;

    RestartSelfTest(String phase) {
        this.phase = phase;
    }

    @Override
    public String name() {
        return phase;
    }

    @Override
    public void run(Check check) throws Exception {
        WorldServer world = check.world;
        if (!check.ok(ModernTerrain.owns(world), phase + " runs in a world with 1.18 terrain")) {
            return;
        }
        final Map<Long, Long> hashes = new TreeMap<>();
        ModernGenerator.onFinished = (key, blocks) -> {
            long h = 1125899906842597L;
            for (char c : blocks) {
                h = 31 * h + c;
            }
            hashes.put(key, h);
        };
        File dir = world.getMinecraftServer().getFile(".");
        if (phase.equals("crash1")) {
            try {
                area(world, X, X + SIDE / 2);
                world.saveAllChunks(true, null);
                waitForChunkWrites();
                area(world, X + SIDE / 2, X + SIDE);
                world.getChunkProvider().saveChunks(true);
                waitForChunkWrites();
                area(world, X + SIDE, X + SIDE + 2);
            } finally {
                ModernGenerator.onFinished = null;
            }
            check.eq(SIDE * SIDE, inArea(hashes, X), "area A generated (" + hashes.size() + " chunks in all)");
            write(new File(dir, "restart-first.txt"), hashes);
            check.report(world.getMinecraftServer().getFile("selftest-report.txt"));
            // A crash: no world save, no shutdown hooks, nothing more written (Runtime.halt, through FML, which
            // traps a mod's own call).
            net.minecraftforge.fml.common.FMLCommonHandler.instance().exitJava(0, true);
            return;
        }
        try {
            if (!phase.equals("restart2") && !phase.equals("crash2")) {
                area(world, X);
            }
            if (!phase.equals("restart1")) {
                area(world, X + SIDE);
            }
        } finally {
            ModernGenerator.onFinished = null;
        }
        if (phase.equals("restart1")) {
            check.eq(SIDE * SIDE, inArea(hashes, X), "area A generated (" + hashes.size() + " chunks in all)");
            write(new File(dir, "restart-first.txt"), hashes);
            return;
        }
        if (phase.equals("restartwhole")) {
            check.eq(2 * SIDE * SIDE, inArea(hashes, X) + inArea(hashes, X + SIDE), "areas A and B generated in one run ("
                    + hashes.size() + " chunks in all)");
            write(new File(dir, "restart-whole.txt"), hashes);
            return;
        }
        check.eq(SIDE * SIDE, inArea(hashes, X + SIDE), "area B generated after the restart (" + hashes.size()
                + " chunks in all)");
        Map<Long, Long> split = read(new File(dir, "restart-first.txt"));
        split.putAll(hashes);
        write(new File(dir, "restart-split.txt"), split);
        File whole = new File(dir, "restart-whole.txt");
        if (!check.ok(whole.isFile(), "restart-whole.txt from the run that never stopped")) {
            return;
        }
        Map<Long, Long> expected = read(whole);
        int differ = 0;
        String first = null;
        for (Map.Entry<Long, Long> e : expected.entrySet()) {
            if (!e.getValue().equals(split.get(e.getKey()))) {
                differ++;
                if (first == null) {
                    ChunkPos c = new ChunkPos((int) (long) e.getKey(), (int) (e.getKey() >> 32));
                    first = c.x + "," + c.z;
                }
            }
        }
        check.eq(expected.size(), split.size(), "the same chunks in both runs");
        check.eq(0, differ, "every chunk of both areas is the same across a restart as in one run"
                + (first == null ? "" : " (first: " + first + ")"));
    }

    /** How many of the hashed chunks are in the 12x12 area at (x0, Z). */
    private static int inArea(Map<Long, Long> hashes, int x0) {
        int n = 0;
        for (long k : hashes.keySet()) {
            int x = (int) k;
            int z = (int) (k >> 32);
            n += x >= x0 && x < x0 + SIDE && z >= Z && z < Z + SIDE ? 1 : 0;
        }
        return n;
    }

    /**
     * Generates the 12x12 chunks at (x, Z) row by row, as a player walking east would ask for them (populating them
     * generates a few chunks around too, the same in every run; those are compared as well).
     */
    private static void area(WorldServer world, int x0) {
        area(world, x0, x0 + SIDE);
    }

    /** Columns x0 to x1 (exclusive) of the area's rows, as {@link #area(WorldServer, int)}. */
    private static void area(WorldServer world, int x0, int x1) {
        for (int x = x0; x < x1; x++) {
            for (int z = Z; z < Z + SIDE; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }
    }

    /** Waits until 1.12's file thread has written every chunk handed to it (the chunks are then on disk). */
    private static void waitForChunkWrites() throws InterruptedException {
        net.minecraft.world.storage.ThreadedFileIOBase.getThreadedIOInstance().waitForFinish();
    }

    private static void write(File file, Map<Long, Long> hashes) throws Exception {
        try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
            for (Map.Entry<Long, Long> e : hashes.entrySet()) {
                out.println(e.getKey() + " " + e.getValue());
            }
        }
    }

    private static Map<Long, Long> read(File file) throws Exception {
        Map<Long, Long> m = new TreeMap<>();
        if (!file.isFile()) {
            return m;
        }
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        for (String line : lines) {
            String[] p = line.trim().split(" ");
            if (p.length == 2) {
                m.put(Long.parseLong(p[0]), Long.parseLong(p[1]));
            }
        }
        return m;
    }
}
