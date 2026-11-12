package net.minecraft.world.level.levelgen;

import net.minecraft.world.level.chunk.ProtoChunk;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Vanilla 1.18.2 chunks after noise fill, surface and the AIR carvers (VanillaWorld.carve), for the Caves and
 * Cliffs Backport's CarverCheck, gzipped, as carved_SEED.bin.gz: long hash of Mth's sine table, int count, then per
 * chunk int cx, int cz and 16 * 16 * 384 block codes (VanillaSurfaceDump's layout and chunk choice).
 *
 * Arguments: outDir side scattered perBiome [seeds...]
 */
public final class VanillaCarverDump {
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
            System.out.println("carver lists: " + world.carverLists());
            long start = System.nanoTime();
            List<long[]> chunks = VanillaSurfaceDump.chunks(world, side, scattered, perBiome);
            try (DataOutputStream w = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(
                    new FileOutputStream(new File(out, "carved_" + seed + ".bin.gz")), 1 << 16), 1 << 16))) {
                w.writeLong(VanillaWorld.sinTableHash());
                w.writeInt(chunks.size());
                for (long[] c : chunks) {
                    int cx = (int) c[0];
                    int cz = (int) c[1];
                    ProtoChunk chunk = world.filledChunk(cx, cz, blocks);
                    world.buildSurface(chunk);
                    world.carve(chunk);
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
}
