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
        try {
            if (!phase.equals("restart2")) {
                area(world, X);
            }
            if (!phase.equals("restart1")) {
                area(world, X + SIDE);
            }
        } finally {
            ModernGenerator.onFinished = null;
        }
        File dir = world.getMinecraftServer().getFile(".");
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
        for (int x = x0; x < x0 + SIDE; x++) {
            for (int z = Z; z < Z + SIDE; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }
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
