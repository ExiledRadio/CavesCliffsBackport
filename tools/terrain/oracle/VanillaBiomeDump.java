package net.minecraft.world.level.levelgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

/**
 * Vanilla 1.18.2 overworld biomes (MultiNoiseBiomeSource.getNoiseBiome, cold search) for the Caves and Cliffs
 * Backport's BiomeCheck, gzipped, as biomes_SEED.bin.gz:
 *
 * int leafCount, int nameCount, nameCount UTF biome names (the backport's ids are their indexes);
 * int chunkCount, then per chunk int cx, int cz and 1536 bytes, index (qx * 4 + qz) * 96 + qy + 16 (all quarts
 * of the chunk, quart y -16..79): side x side chunks around 0,0, side x side around a far point, scattered ones;
 * then a layer: int qy, int qx0, int qz0, int w, int h and w * h bytes, index i * h + j for quart qx0 + i, qz0 + j.
 *
 * Also prints how many of the first area's quarts a warm search (each worker thread starting from its last
 * answer, in fillBiomesFromNoise's order) answers differently.
 *
 * Arguments: outDir side scattered layerSide [seeds...]
 */
public final class VanillaBiomeDump {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        int side = Integer.parseInt(args[1]);
        int scattered = Integer.parseInt(args[2]);
        int layer = Integer.parseInt(args[3]);
        long[] seeds = {42L, 12345L, -7346534567L, 8675309L};
        if (args.length > 4) {
            seeds = new long[args.length - 4];
            for (int i = 4; i < args.length; i++) {
                seeds[i - 4] = Long.parseLong(args[i]);
            }
        }
        byte[] chunk = new byte[1536];
        for (long seed : seeds) {
            VanillaWorld world = new VanillaWorld(seed);
            long start = System.nanoTime();
            Random rnd = new Random(seed * 131L + 7L);
            int farX = rnd.nextInt(40000) - 20000;
            int farZ = rnd.nextInt(40000) - 20000;
            int count = side * side * 2 + scattered;
            long warmDiffers = 0L;
            long warmTotal = 0L;
            try (DataOutputStream w = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(
                    new FileOutputStream(new File(out, "biomes_" + seed + ".bin.gz")), 1 << 16), 1 << 16))) {
                w.writeInt(world.leafCount);
                VanillaWorld.writeBiomeNames(w);
                w.writeInt(count);
                for (int i = 0; i < count; i++) {
                    int cx;
                    int cz;
                    if (i < side * side) {
                        cx = i / side - side / 2;
                        cz = i % side - side / 2;
                    } else if (i < side * side * 2) {
                        int j = i - side * side;
                        cx = farX + j / side;
                        cz = farZ + j % side;
                    } else {
                        int span = i % 3 == 0 ? 2000 : i % 3 == 1 ? 60000 : 1870000;
                        cx = rnd.nextInt(span * 2) - span;
                        cz = rnd.nextInt(span * 2) - span;
                    }
                    for (int qx = 0; qx < 4; qx++) {
                        for (int qz = 0; qz < 4; qz++) {
                            for (int qy = -16; qy < 80; qy++) {
                                Holder<Biome> b = world.coldBiome(cx * 4 + qx, qy, cz * 4 + qz);
                                chunk[(qx * 4 + qz) * 96 + qy + 16] = (byte) world.id(b);
                            }
                        }
                    }
                    if (i < side * side) {
                        // LevelChunkSection.fillBiomesFromNoise's order: section by section, then x, y, z.
                        for (int section = 0; section < 24; section++) {
                            for (int qx = 0; qx < 4; qx++) {
                                for (int dy = 0; dy < 4; dy++) {
                                    for (int qz = 0; qz < 4; qz++) {
                                        int qy = -16 + section * 4 + dy;
                                        int warm = world.id(world.warmBiome(cx * 4 + qx, qy, cz * 4 + qz));
                                        warmTotal++;
                                        if (warm != chunk[(qx * 4 + qz) * 96 + qy + 16]) {
                                            warmDiffers++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    w.writeInt(cx);
                    w.writeInt(cz);
                    w.write(chunk);
                }
                int qy = 16;
                int q0 = -layer / 2;
                w.writeInt(qy);
                w.writeInt(q0);
                w.writeInt(q0);
                w.writeInt(layer);
                w.writeInt(layer);
                for (int i = 0; i < layer; i++) {
                    for (int j = 0; j < layer; j++) {
                        w.write(world.id(world.coldBiome(q0 + i, qy, q0 + j)));
                    }
                }
            }
            System.out.println("seed " + seed + ": " + world.leafCount + " leaves, " + count + " chunks and a " + layer
                + "x" + layer + " layer in " + (System.nanoTime() - start) / 1000000L + " ms; warm search differs at "
                + warmDiffers + " of " + warmTotal + " quarts");
        }
    }
}
