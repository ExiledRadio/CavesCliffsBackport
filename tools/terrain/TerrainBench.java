import com.exiledradio.cavesandcliffsbackport.terrain.core.ChunkFiller;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;

import java.util.Arrays;

/**
 * Times the noise fill (terrain, caves, aquifers, ore veins) on one thread.
 * Arguments: [seed] [side] [rounds] [centerChunkX] [centerChunkZ]. Each round fills side x side chunks, row by
 * row; the first round is warm-up. Prints the median and best round and a checksum of the blocks.
 */
public final class TerrainBench {
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        int side = args.length > 1 ? Integer.parseInt(args[1]) : 16;
        int rounds = args.length > 2 ? Integer.parseInt(args[2]) : 6;
        int centerX = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int centerZ = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        byte[] out = new byte[16 * 16 * 384];
        double[] ms = new double[rounds];
        long checksum = 0L;
        for (int round = 0; round <= rounds; round++) {
            OverworldNoise noise = new OverworldNoise(seed);
            ChunkFiller filler = new ChunkFiller(noise);
            long sum = 0L;
            long start = System.nanoTime();
            for (int i = 0; i < side * side; i++) {
                filler.fill(centerX - side / 2 + i / side, centerZ - side / 2 + i % side, out);
                sum = sum * 31L + Arrays.hashCode(out);
            }
            long took = System.nanoTime() - start;
            checksum = sum;
            if (round > 0) {
                ms[round - 1] = took / 1.0E6D / (side * side);
            }
        }
        System.out.println("corner phase total ms " + ChunkFiller.PROF_CORNERS / 1.0E6D / ((rounds + 1) * side * side) + " per chunk");
        Arrays.sort(ms);
        double median = ms[rounds / 2];
        System.out.println(String.format(java.util.Locale.ROOT,
            "seed %d, %dx%d chunks at %d,%d, java %s: median %.3f ms/chunk (%.1f chunks/s), best %.3f ms/chunk, checksum %016x",
            seed, side, side, centerX, centerZ, System.getProperty("java.version"), median, 1000.0D / median, ms[0],
            checksum));
    }
}
