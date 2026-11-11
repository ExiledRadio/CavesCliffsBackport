import com.exiledradio.cavesandcliffsbackport.terrain.core.ChunkFiller;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Times the noise fill on several threads sharing one OverworldNoise, each with its own ChunkFiller.
 * Arguments: [threads] [seed] [side] [rounds]. The first round is warm-up.
 */
public final class TerrainBenchThreads {
    public static void main(String[] args) throws Exception {
        final int threads = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        final long seed = args.length > 1 ? Long.parseLong(args[1]) : 42L;
        final int side = args.length > 2 ? Integer.parseInt(args[2]) : 32;
        int rounds = args.length > 3 ? Integer.parseInt(args[3]) : 5;
        double[] perSecond = new double[rounds];
        for (int round = 0; round <= rounds; round++) {
            final OverworldNoise noise = new OverworldNoise(seed);
            final AtomicInteger next = new AtomicInteger();
            Thread[] pool = new Thread[threads];
            long start = System.nanoTime();
            for (int t = 0; t < threads; t++) {
                pool[t] = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ChunkFiller filler = new ChunkFiller(noise);
                        byte[] out = new byte[16 * 16 * 384];
                        int i;
                        while ((i = next.getAndIncrement()) < side * side) {
                            filler.fill(i / side - side / 2, i % side - side / 2, out);
                        }
                    }
                });
                pool[t].start();
            }
            for (Thread thread : pool) {
                thread.join();
            }
            double seconds = (System.nanoTime() - start) / 1.0E9D;
            if (round > 0) {
                perSecond[round - 1] = side * side / seconds;
            }
        }
        Arrays.sort(perSecond);
        System.out.println(String.format(java.util.Locale.ROOT,
            "%d threads, seed %d, %dx%d chunks, java %s: median %.0f chunks/s (%.0f per thread)", threads, seed, side,
            side, System.getProperty("java.version"), perSecond[rounds / 2], perSecond[rounds / 2] / threads));
    }
}
