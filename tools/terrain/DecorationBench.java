import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Carvers;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ChunkFiller;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Region;
import com.exiledradio.cavesandcliffsbackport.terrain.core.SurfaceBuilder;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Times decoration alone on one thread: side x side chunks around a center (and a ring around them) are filled,
 * surfaced and carved once, untimed; each round copies them and decorates the inner ones in order with a fresh
 * Decorator. Arguments: seed side rounds centerX centerZ [features,comma,separated|all]. Prints the median and a
 * checksum of the decorated blocks.
 */
public final class DecorationBench {
    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        int side = Integer.parseInt(args[1]);
        int rounds = Integer.parseInt(args[2]);
        int centerX = Integer.parseInt(args[3]);
        int centerZ = Integer.parseInt(args[4]);
        String only = args.length > 5 ? args[5] : "all";
        OverworldNoise noise = new OverworldNoise(seed);
        ColumnCache columns = new ColumnCache(noise);
        Climate climate = new Climate();
        BiomeSource biomes = new BiomeSource(noise, columns, climate);
        ChunkFiller filler = new ChunkFiller(noise, columns);
        SurfaceBuilder surface = new SurfaceBuilder(noise, columns, biomes);
        Carvers carvers = new Carvers(noise, columns, surface);
        int n = side + 2;
        int x0 = centerX - side / 2 - 1;
        int z0 = centerZ - side / 2 - 1;
        char[][] pristine = new char[n * n][];
        int[][] floors = new int[n * n][];
        for (int i = 0; i < n * n; i++) {
            pristine[i] = new char[16 * 16 * 384];
            floors[i] = new int[256];
            filler.fill(x0 + i / n, z0 + i % n, pristine[i]);
            surface.build(x0 + i / n, z0 + i % n, pristine[i]);
            carvers.carve(x0 + i / n, z0 + i % n, pristine[i]);
            Region.oceanFloorHeights(pristine[i], floors[i]);
        }
        double[] ms = new double[rounds];
        long checksum = 0L;
        char[][] work = new char[n * n][];
        Region region = new Region();
        char[][] b9 = new char[9][];
        int[][] h9 = new int[9][];
        for (int round = 0; round <= rounds; round++) {
            for (int i = 0; i < n * n; i++) {
                work[i] = pristine[i].clone();
            }
            Decorator decorator = new Decorator(noise, new BiomeSource(noise, columns, climate));
            decorator.only(only.equals("all") ? null : new HashSet<>(Arrays.asList(only.split(","))));
            long t = System.nanoTime();
            for (int a = 1; a <= side; a++) {
                for (int b = 1; b <= side; b++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            b9[(dx + 1) * 3 + dz + 1] = work[(a + dx) * n + b + dz];
                            h9[(dx + 1) * 3 + dz + 1] = floors[(a + dx) * n + b + dz];
                        }
                    }
                    region.set(x0 + a, z0 + b, b9, h9);
                    decorator.decorate(region);
                }
            }
            long elapsed = System.nanoTime() - t;
            long sum = 0L;
            for (char[] c : work) {
                sum = sum * 31L + Arrays.hashCode(c);
            }
            checksum = sum;
            if (round > 0) {
                ms[round - 1] = elapsed / 1.0E6D / (side * side);
            }
        }
        Arrays.sort(ms);
        System.out.println(String.format(java.util.Locale.ROOT,
            "seed %d, %dx%d chunks at %d,%d, java %s: decoration %.3f ms/chunk, checksum %016x", seed, side, side,
            centerX, centerZ, System.getProperty("java.version"), ms[rounds / 2], checksum));
    }
}
