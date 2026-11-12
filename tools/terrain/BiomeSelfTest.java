import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;

import java.util.Random;

/**
 * Checks Climate's shortcut against its port of Mojang's tree search (cold start) and against trying every leaf,
 * on random climate points, many of them exactly on a parameter border or outside -1..1.
 * Usage: BiomeSelfTest [count]
 */
public final class BiomeSelfTest {
    public static void main(String[] args) {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 2000000;
        long t0 = System.nanoTime();
        Climate climate = new Climate();
        System.out.println(climate.leafCount() + " leaves, built in " + (System.nanoTime() - t0) / 1000000L + " ms");
        float[] borders = {-1.2F, -1.05F, -1.0F, -0.93333334F, -0.7666667F, -0.78F, -0.56666666F, -0.455F, -0.45F,
            -0.4F, -0.375F, -0.35F, -0.26666668F, -0.2225F, -0.19F, -0.15F, -0.11F, -0.1F, -0.05F, 0.0F, 0.03F, 0.05F,
            0.1F, 0.2F, 0.26666668F, 0.3F, 0.4F, 0.45F, 0.55F, 0.56666666F, 0.7F, 0.7666667F, 0.8F, 0.9F, 0.93333334F,
            1.0F};
        Random r = new Random(1);
        Climate.Column col = climate.newColumn();
        long[] target = new long[7];
        int bad = 0;
        int badBrute = 0;
        for (int i = 0; i < count; i++) {
            for (int d = 0; d < 6; d++) {
                int kind = r.nextInt(10);
                float v;
                if (kind < 3) {
                    v = borders[r.nextInt(borders.length)];
                } else if (kind == 3) {
                    v = (r.nextFloat() * 2 - 1) * 1.6F;
                } else {
                    v = r.nextFloat() * 2 - 1;
                }
                target[d] = Climate.quantize(v);
            }
            target[6] = 0L;
            climate.column(col, target[0], target[1], target[2], target[3], target[5]);
            int fast = climate.biome(col, target[4]);
            int tree = climate.searchTree(target);
            if (fast != tree) {
                if (bad++ < 10) {
                    System.out.println("fast " + fast + " tree " + tree + " at " + java.util.Arrays.toString(target));
                }
            }
            if (i % 20 == 0) {
                int brute = climate.searchBruteForce(target);
                if (brute != tree && badBrute++ < 10) {
                    System.out.println("brute " + brute + " tree " + tree + " at " + java.util.Arrays.toString(target));
                }
            }
        }
        System.out.println(bad == 0 && badBrute == 0 ? "ALL IDENTICAL (" + count + " points)"
            : bad + " fast/tree and " + badBrute + " brute/tree differences");
    }
}
