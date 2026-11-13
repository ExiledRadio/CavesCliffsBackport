import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Editions;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;

import java.util.Map;
import java.util.TreeMap;

/**
 * Finishes the chunks around the nearest chunk of a biome (at y 64, searched outwards from 0, 0 by BiomeSource) in a
 * world with the given editions and counts what the newer biomes' features put down: mud, mangrove logs, roots and
 * propagules, cherry logs and leaves, pink petals, bee nests. A look, not a check against vanilla.
 * Usage: NewerFeatureCensus seed editions biome [side] [threads]
 */
public final class NewerFeatureCensus {
    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        int editions = Integer.parseInt(args[1]);
        int target = Biomes.byName(args[2]);
        int side = args.length > 3 ? Integer.parseInt(args[3]) : 8;
        int threads = args.length > 4 ? Integer.parseInt(args[4]) : 4;
        OverworldNoise noise = new OverworldNoise(seed, editions);
        Climate climate = new Climate(editions);
        BiomeSource source = new BiomeSource(noise, new ColumnCache(noise), climate);
        int found = -1;
        int fx = 0;
        int fz = 0;
        search:
        for (int r = 0; r < 400; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int qy = target == Biomes.DEEP_DARK ? -10 : 16;
                    if (source.biome(dx * 4 + 2, qy, dz * 4 + 2) == target) {
                        fx = dx;
                        fz = dz;
                        found = r;
                        break search;
                    }
                }
            }
        }
        if (found < 0) {
            System.out.println("no " + args[2] + " within 400 chunks");
            return;
        }
        System.out.println(args[2] + " at chunk " + fx + "," + fz + " (block " + fx * 16 + "," + fz * 16 + ")");
        Pipeline p = new Pipeline(noise, climate, threads, 4096);
        Map<String, Long> counts = new TreeMap<>();
        int[] biomes = new int[Biomes.COUNT];
        long t = System.nanoTime();
        for (int i = 0; i < side * side; i++) {
            int cx = fx + i / side - side / 2;
            int cz = fz + i % side - side / 2;
            Pipeline.Proto proto = p.finish(cx, cz);
            for (char c : proto.blocks) {
                String n = BlockCodes.name(c);
                if (n.contains("mangrove") || n.contains("mud") || n.contains("cherry") || n.equals("pink_petals")
                        || n.equals("bee_nest") || n.equals("moss_carpet") || n.equals("vine") || n.equals("grass_block")
                        || n.equals("seagrass") || n.equals("lily_pad")) {
                    counts.merge(n, 1L, Long::sum);
                }
            }
            for (int b : proto.surfaceBiomes) {
                biomes[b]++;
            }
        }
        long ms = (System.nanoTime() - t) / 1000000L;
        StringBuilder sb = new StringBuilder();
        for (int b = 0; b < Biomes.COUNT; b++) {
            if (biomes[b] > 0) {
                sb.append(' ').append(Biomes.name(b)).append('=').append(biomes[b]);
            }
        }
        System.out.println("surface biomes:" + sb);
        System.out.println("blocks: " + counts);
        System.out.println(side * side + " chunks in " + ms + " ms, editions " + Editions.name(editions));
        p.shutdown();
    }
}
