/**
 * The fluid ticks and post-processing marks the pipeline hands the game ({@code Proto.updates}) over side x side
 * finished chunks from cx0, cz0: counts by kind and by the block at the position. Arguments: seed cx0 cz0 side.
 */
import com.exiledradio.cavesandcliffsbackport.terrain.core.*;
public class MarkStats {
    public static void main(String[] a) {
        long seed = Long.parseLong(a[0]); int cx0 = Integer.parseInt(a[1]), cz0 = Integer.parseInt(a[2]), side = Integer.parseInt(a[3]);
        Pipeline p = new Pipeline(seed, 0, 4096);
        long[] kinds = new long[2]; long fluidPost = 0; int max = 0; long n = 0; long t0 = System.nanoTime();
        java.util.Map<String,Integer> by = new java.util.TreeMap<>();
        for (int x = 0; x < side; x++) for (int z = 0; z < side; z++) {
            Pipeline.Proto c = p.finish(cx0 + x, cz0 + z);
            n++; max = Math.max(max, c.updates.length);
            for (int e : c.updates) { kinds[Marks.kind(e)]++;
                int code = c.get(Marks.x(e), Marks.y(e), Marks.z(e));
                String nm = BlockCodes.name(code) + (Marks.kind(e)==0?"/tick":"/post");
                by.merge(nm, 1, Integer::sum); }
        }
        System.out.println("chunks " + n + " tick " + kinds[0] + " post " + kinds[1] + " max/chunk " + max + " ms " + (System.nanoTime()-t0)/1e6);
        System.out.println(by);
        p.shutdown();
    }
}
