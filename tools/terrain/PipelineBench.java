import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;

import java.util.Arrays;

/**
 * Times the 1.12 generator's pipeline without a game: side x side chunks around 0,0 finished in a spiral from the
 * middle (the order a 1.12 server asks for spawn chunks), each finish followed by a prefetch as the generator does.
 * Arguments: seed side threads [prefetchRadius] [cap]; -Deditions=N, -Dcenter=cx,cz. Prints ms/chunk, the split between fill and decoration, and a
 * checksum over every finished chunk, which must not depend on the thread count.
 */
public final class PipelineBench {
    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        int side = Integer.parseInt(args[1]);
        int threads = Integer.parseInt(args[2]);
        int prefetch = args.length > 3 ? Integer.parseInt(args[3]) : 6;
        int cap = args.length > 4 ? Integer.parseInt(args[4]) : 4096;
        int[][] order = spiral(side);
        // -Dcenter=cx,cz: the square around that chunk instead of 0,0 (a pale garden, say).
        String center = System.getProperty("center");
        if (center != null) {
            int ox = Integer.parseInt(center.split(",")[0].trim());
            int oz = Integer.parseInt(center.split(",")[1].trim());
            for (int[] c : order) {
                c[0] += ox;
                c[1] += oz;
            }
        }
        // Warm-up elsewhere, so class loading and the JIT are not in the timing.
        // -Deditions=N: a world with the newer biomes (Editions flags).
        int e = Integer.getInteger("editions", 0);
        Pipeline warm = pipeline(seed, e, threads, cap);
        for (int i = 0; i < 64; i++) {
            warm.finish(1000 + order[i][0], 1000 + order[i][1]);
        }
        warm.shutdown();
        Pipeline p = pipeline(seed, e, threads, cap);
        long checksum = 0L;
        long t = System.nanoTime();
        for (int[] c : order) {
            Pipeline.Proto proto = p.finish(c[0], c[1]);
            checksum = checksum * 31L + Arrays.hashCode(proto.blocks);
            p.prefetch(c[0], c[1], prefetch);
        }
        long elapsed = System.nanoTime() - t;
        int n = order.length;
        System.out.println(String.format(java.util.Locale.ROOT,
            "seed %d, %d chunks, %d threads, java %s: %.3f ms/chunk (%.1f chunks/s); fill %.3f ms x %d, decorate %.3f ms,"
                + " waited %.3f ms/chunk, %d fills on the asking thread, held %d; checksum %016x",
            seed, n, threads, System.getProperty("java.version"), elapsed / 1e6 / n, n / (elapsed / 1e9),
            p.fillNanos.get() / 1e6 / Math.max(1, p.filled.get()), p.filled.get(), p.decorateNanos / 1e6 / Math.max(1, p.decorated),
            p.waitNanos / 1e6 / n, p.stolen, p.held(), checksum));
        p.shutdown();
    }

    static Pipeline pipeline(long seed, int editions, int threads, int cap) {
        return editions == 0 ? new Pipeline(seed, threads, cap) : new Pipeline(
            new com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise(seed, editions),
            new com.exiledradio.cavesandcliffsbackport.terrain.core.Climate(editions), threads, cap);
    }

    /** Chunks of a side x side square around 0,0, from the middle outwards, ring by ring. */
    static int[][] spiral(int side) {
        int[][] out = new int[side * side][];
        int n = 0;
        int half = side / 2;
        for (int r = 0; n < out.length; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r && dx >= -half && dx < side - half && dz >= -half
                        && dz < side - half) {
                        out[n++] = new int[]{dx, dz};
                    }
                }
            }
        }
        return out;
    }
}
