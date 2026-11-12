import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Carvers;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ChunkFiller;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.SurfaceBuilder;

import java.util.Arrays;

/**
 * Times the AIR carvers on one thread over side x side chunks after fill and surface (both timed separately), a
 * fresh core per round so the recorded paths start empty. Arguments: [seed] [side] [rounds] [centerX] [centerZ].
 * Prints medians and a checksum of the carved blocks (comparable with VanillaCarverBench's).
 */
public final class CarverBench {
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        int side = args.length > 1 ? Integer.parseInt(args[1]) : 12;
        int rounds = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        int centerX = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int centerZ = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        byte[] out = new byte[16 * 16 * 384];
        double[] baseMs = new double[rounds];
        double[] carveMs = new double[rounds];
        long checksum = 0L;
        Climate climate = new Climate();
        for (int round = 0; round <= rounds; round++) {
            OverworldNoise noise = new OverworldNoise(seed);
            ColumnCache columns = new ColumnCache(noise);
            ChunkFiller filler = new ChunkFiller(noise, columns);
            BiomeSource biomes = new BiomeSource(noise, columns, climate);
            SurfaceBuilder surface = new SurfaceBuilder(noise, columns, biomes);
            Carvers carvers = new Carvers(noise, columns, surface);
            int x0 = centerX - side / 2;
            int z0 = centerZ - side / 2;
            long baseTime = 0L;
            long carveTime = 0L;
            long sum = 0L;
            for (int i = 0; i < side * side; i++) {
                int cx = x0 + i / side;
                int cz = z0 + i % side;
                long t = System.nanoTime();
                filler.fill(cx, cz, out);
                surface.build(cx, cz, out);
                long t1 = System.nanoTime();
                carvers.carve(cx, cz, out);
                long t2 = System.nanoTime();
                baseTime += t1 - t;
                carveTime += t2 - t1;
                sum = sum * 31L + Arrays.hashCode(out);
            }
            checksum = sum;
            if (round > 0) {
                baseMs[round - 1] = baseTime / 1.0E6D / (side * side);
                carveMs[round - 1] = carveTime / 1.0E6D / (side * side);
            }
        }
        Arrays.sort(baseMs);
        Arrays.sort(carveMs);
        System.out.println(String.format(java.util.Locale.ROOT,
            "seed %d, %dx%d chunks at %d,%d, java %s: fill + surface %.3f, carvers %.3f ms/chunk, checksum %016x",
            seed, side, side, centerX, centerZ, System.getProperty("java.version"), baseMs[rounds / 2],
            carveMs[rounds / 2], checksum));
    }
}
