import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ChunkFiller;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.SurfaceBuilder;

import java.util.Arrays;

/**
 * Times biomes, noise fill and surface on one thread, in the game's order: the stored biomes of every chunk of
 * the area and a ring around it (only the area's counted), then per chunk fill and surface.
 * Arguments: [seed] [side] [rounds] [centerChunkX] [centerChunkZ]. The first round is warm-up; prints medians
 * and a checksum of the blocks after surface (comparable with VanillaSurfaceBench's).
 */
public final class SurfaceBench {
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        int side = args.length > 1 ? Integer.parseInt(args[1]) : 12;
        int rounds = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        int centerX = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int centerZ = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        byte[] out = new byte[16 * 16 * 384];
        byte[] stored = new byte[1536];
        double[] biomeMs = new double[rounds];
        double[] fillMs = new double[rounds];
        double[] surfaceMs = new double[rounds];
        long checksum = 0L;
        Climate climate = new Climate();
        for (int round = 0; round <= rounds; round++) {
            OverworldNoise noise = new OverworldNoise(seed);
            ColumnCache columns = new ColumnCache(noise);
            ChunkFiller filler = new ChunkFiller(noise, columns);
            BiomeSource biomes = new BiomeSource(noise, columns, climate);
            SurfaceBuilder surface = new SurfaceBuilder(noise, columns, biomes);
            int x0 = centerX - side / 2;
            int z0 = centerZ - side / 2;
            long biomeTime = 0L;
            for (int cx = x0 - 1; cx <= x0 + side; cx++) {
                for (int cz = z0 - 1; cz <= z0 + side; cz++) {
                    boolean inner = cx >= x0 && cx < x0 + side && cz >= z0 && cz < z0 + side;
                    long t = System.nanoTime();
                    biomes.fillChunk(cx, cz, stored);
                    if (inner) {
                        biomeTime += System.nanoTime() - t;
                    }
                }
            }
            long fillTime = 0L;
            long surfaceTime = 0L;
            long sum = 0L;
            for (int i = 0; i < side * side; i++) {
                int cx = x0 + i / side;
                int cz = z0 + i % side;
                long t = System.nanoTime();
                filler.fill(cx, cz, out);
                long t1 = System.nanoTime();
                surface.build(cx, cz, out);
                long t2 = System.nanoTime();
                fillTime += t1 - t;
                surfaceTime += t2 - t1;
                sum = sum * 31L + Arrays.hashCode(out);
            }
            checksum = sum;
            if (round > 0) {
                biomeMs[round - 1] = biomeTime / 1.0E6D / (side * side);
                fillMs[round - 1] = fillTime / 1.0E6D / (side * side);
                surfaceMs[round - 1] = surfaceTime / 1.0E6D / (side * side);
            }
        }
        Arrays.sort(biomeMs);
        Arrays.sort(fillMs);
        Arrays.sort(surfaceMs);
        double total = biomeMs[rounds / 2] + fillMs[rounds / 2] + surfaceMs[rounds / 2];
        System.out.println(String.format(java.util.Locale.ROOT,
            "seed %d, %dx%d chunks at %d,%d, java %s: biomes %.3f + fill %.3f + surface %.3f = %.3f ms/chunk (%.1f chunks/s), checksum %016x",
            seed, side, side, centerX, centerZ, System.getProperty("java.version"), biomeMs[rounds / 2],
            fillMs[rounds / 2], surfaceMs[rounds / 2], total, 1000.0D / total, checksum));
    }
}
