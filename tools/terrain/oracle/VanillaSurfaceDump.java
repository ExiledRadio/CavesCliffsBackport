package net.minecraft.world.level.levelgen;

import net.minecraft.world.level.chunk.ProtoChunk;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * Vanilla 1.18.2 chunks after noise fill and surface (SurfaceSystem.buildSurface with the overworld's rules), for
 * the Caves and Cliffs Backport's SurfaceCheck, gzipped, as surface_SEED.bin.gz: int count, then per chunk int cx,
 * int cz and 16 * 16 * 384 block codes, index (x * 16 + z) * 384 + y + 64 (VanillaWorld's codes).
 *
 * Chunks: side x side around 0,0, side x side around a far point, scattered ones, and for every biome found on a
 * coarse scan out to 16,384 blocks, up to perBiome chunks where it shows up near the surface, so rare surfaces
 * (badlands bands, eroded pillars, icebergs, ice spikes, peaks) are in every seed's set.
 *
 * Arguments: outDir side scattered perBiome [seeds...]
 */
public final class VanillaSurfaceDump {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        int side = Integer.parseInt(args[1]);
        int scattered = Integer.parseInt(args[2]);
        int perBiome = Integer.parseInt(args[3]);
        long[] seeds = {42L, 12345L, -7346534567L, 8675309L};
        if (args.length > 4) {
            seeds = new long[args.length - 4];
            for (int i = 4; i < args.length; i++) {
                seeds[i - 4] = Long.parseLong(args[i]);
            }
        }
        byte[] blocks = new byte[16 * 16 * 384];
        for (long seed : seeds) {
            VanillaWorld world = new VanillaWorld(seed);
            long start = System.nanoTime();
            List<long[]> chunks = chunks(world, side, scattered, perBiome);
            try (DataOutputStream w = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(
                    new FileOutputStream(new File(out, "surface_" + seed + ".bin.gz")), 1 << 16), 1 << 16))) {
                w.writeInt(chunks.size());
                for (long[] c : chunks) {
                    int cx = (int) c[0];
                    int cz = (int) c[1];
                    ProtoChunk chunk = world.filledChunk(cx, cz, blocks);
                    world.buildSurface(chunk);
                    VanillaWorld.read(chunk, blocks);
                    w.writeInt(cx);
                    w.writeInt(cz);
                    w.write(blocks);
                }
            }
            System.out.println("seed " + seed + ": " + chunks.size() + " chunks in "
                + (System.nanoTime() - start) / 1000000L + " ms");
        }
    }

    static List<long[]> chunks(VanillaWorld world, int side, int scattered, int perBiome) {
        Set<Long> set = new LinkedHashSet<>();
        Random rnd = new Random(world.seed * 131L + 11L);
        int farX = rnd.nextInt(40000) - 20000;
        int farZ = rnd.nextInt(40000) - 20000;
        for (int i = 0; i < side * side; i++) {
            set.add(pack(i / side - side / 2, i % side - side / 2));
        }
        for (int i = 0; i < side * side; i++) {
            set.add(pack(farX + i / side, farZ + i % side));
        }
        for (int i = 0; i < scattered; i++) {
            int span = i % 3 == 0 ? 2000 : i % 3 == 1 ? 60000 : 1870000;
            set.add(pack(rnd.nextInt(span * 2) - span, rnd.nextInt(span * 2) - span));
        }
        int[] found = new int[VanillaWorld.BIOME_NAMES.size()];
        int[] quartYs = {12, 15, 16, 18, 22, 28, 36, 46};
        for (int gx = -64; gx < 64; gx++) {
            for (int gz = -64; gz < 64; gz++) {
                int x = gx * 256 + 37;
                int z = gz * 256 + 91;
                for (int qy : quartYs) {
                    int b = world.id(world.coldBiome(x >> 2, qy, z >> 2));
                    if (found[b] < perBiome && set.add(pack(x >> 4, z >> 4))) {
                        found[b]++;
                    }
                }
            }
        }
        List<long[]> list = new ArrayList<>();
        for (long p : set) {
            list.add(new long[]{p >> 32, (int) p});
        }
        return list;
    }

    private static long pack(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }
}
