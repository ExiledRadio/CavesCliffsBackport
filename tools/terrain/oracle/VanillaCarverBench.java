package net.minecraft.world.level.levelgen;

import net.minecraft.world.level.chunk.ProtoChunk;

import java.util.Arrays;

/**
 * Times vanilla 1.18.2's AIR carvers (VanillaWorld.carve, applyCarvers' loop with carver biomes kept per chunk as
 * ChunkAccess.carverBiome does) on one thread, over side x side chunks after fill and surface (not timed).
 * Arguments: seed side rounds [centerX centerZ]. Prints the median and a checksum of the carved blocks
 * (comparable with CarverBench's).
 */
public final class VanillaCarverBench {
    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        int side = args.length > 1 ? Integer.parseInt(args[1]) : 12;
        int rounds = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        int centerX = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int centerZ = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        double[] carveMs = new double[rounds];
        long checksum = 0L;
        byte[] blocks = new byte[16 * 16 * 384];
        for (int round = 0; round <= rounds; round++) {
            VanillaWorld world = new VanillaWorld(seed);
            int x0 = centerX - side / 2;
            int z0 = centerZ - side / 2;
            long carveTime = 0L;
            long sum = 0L;
            for (int i = 0; i < side * side; i++) {
                int cx = x0 + i / side;
                int cz = z0 + i % side;
                ProtoChunk chunk = world.filledChunk(cx, cz, blocks);
                world.buildSurface(chunk);
                long t = System.nanoTime();
                world.carve(chunk);
                carveTime += System.nanoTime() - t;
                VanillaWorld.read(chunk, blocks);
                sum = sum * 31L + Arrays.hashCode(blocks);
            }
            checksum = sum;
            if (round > 0) {
                carveMs[round - 1] = carveTime / 1.0E6D / (side * side);
            }
        }
        Arrays.sort(carveMs);
        System.out.println(String.format(java.util.Locale.ROOT,
            "VANILLA 1.18.2 seed %d, %dx%d chunks at %d,%d, java %s: carvers median %.3f ms/chunk, checksum %016x",
            seed, side, side, centerX, centerZ, System.getProperty("java.version"), carveMs[rounds / 2], checksum));
    }
}
