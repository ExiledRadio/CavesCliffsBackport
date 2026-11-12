import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Structures;

import java.util.Arrays;

/**
 * Times Structures.startAt for all 14 overworld sets at every chunk of a side x side area (what a chunk generator
 * asks per chunk), a fresh core per round, strongholds' rings computed once per round and timed apart.
 * Arguments: [seed] [side] [rounds] [centerX] [centerZ]. Prints medians and how many starts were found.
 */
public final class StructureBench {
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        int side = args.length > 1 ? Integer.parseInt(args[1]) : 64;
        int rounds = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        int centerX = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int centerZ = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        Climate climate = new Climate();
        double[] perChunk = new double[rounds];
        double[] ringMs = new double[rounds];
        int found = 0;
        for (int round = 0; round <= rounds; round++) {
            OverworldNoise noise = new OverworldNoise(seed);
            ColumnCache columns = new ColumnCache(noise);
            BiomeSource biomes = new BiomeSource(noise, columns, climate);
            Structures structures = new Structures(noise, columns, biomes);
            long t0 = System.nanoTime();
            structures.ringPositions();
            long t1 = System.nanoTime();
            int n = 0;
            for (int i = 0; i < side * side; i++) {
                int cx = centerX - side / 2 + i / side;
                int cz = centerZ - side / 2 + i % side;
                for (int set = 0; set < Structures.SETS; set++) {
                    if (structures.startAt(set, cx, cz) >= 0) {
                        n++;
                    }
                }
            }
            long t2 = System.nanoTime();
            found = n;
            if (round > 0) {
                ringMs[round - 1] = (t1 - t0) / 1.0E6D;
                perChunk[round - 1] = (t2 - t1) / 1.0E6D / (side * side);
            }
        }
        Arrays.sort(perChunk);
        Arrays.sort(ringMs);
        System.out.println(String.format(java.util.Locale.ROOT,
            "seed %d, %dx%d chunks at %d,%d, java %s: all 14 sets %.4f ms/chunk, %d starts; stronghold rings %.1f ms",
            seed, side, side, centerX, centerZ, System.getProperty("java.version"), perChunk[rounds / 2], found,
            ringMs[rounds / 2]));
    }
}
