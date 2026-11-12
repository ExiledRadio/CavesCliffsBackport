import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.zip.GZIPInputStream;

/**
 * Compares BiomeSource against the oracle's biomes_SEED.bin.gz (VanillaBiomeDump): every quart of every dumped
 * chunk (through fillChunk) and the dumped layer (through biome). Prints ALL IDENTICAL or the first differences.
 * Usage: BiomeCheck <oracle-out> [seeds...]
 */
public final class BiomeCheck {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        long[] seeds = {42L, 12345L, -7346534567L, 8675309L};
        if (args.length > 1) {
            seeds = new long[args.length - 1];
            for (int i = 1; i < args.length; i++) {
                seeds[i - 1] = Long.parseLong(args[i]);
            }
        }
        Climate climate = new Climate();
        long differences = 0L;
        long checked = 0L;
        int[] counts = new int[Biomes.COUNT];
        for (long seed : seeds) {
            OverworldNoise noise = new OverworldNoise(seed);
            BiomeSource source = new BiomeSource(noise, new ColumnCache(noise), climate);
            long seedDifferences = 0L;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(
                    new FileInputStream(new File(dir, "biomes_" + seed + ".bin.gz")), 1 << 16)))) {
                int leaves = in.readInt();
                if (leaves != climate.leafCount()) {
                    System.out.println("seed " + seed + ": vanilla has " + leaves + " leaves, ours " + climate.leafCount());
                    differences++;
                }
                int names = in.readInt();
                int[] map = new int[names];
                for (int i = 0; i < names; i++) {
                    String name = in.readUTF();
                    map[i] = Biomes.byName(name);
                    if (map[i] != i) {
                        System.out.println("biome " + i + " is " + name + " in vanilla, ours " + map[i]);
                        differences++;
                    }
                }
                int chunks = in.readInt();
                byte[] expected = new byte[1536];
                byte[] actual = new byte[1536];
                for (int c = 0; c < chunks; c++) {
                    int cx = in.readInt();
                    int cz = in.readInt();
                    in.readFully(expected);
                    source.fillChunk(cx, cz, actual);
                    for (int i = 0; i < 1536; i++) {
                        checked++;
                        counts[expected[i]]++;
                        if (expected[i] != actual[i]) {
                            if (seedDifferences++ < 10) {
                                int qx = i / 96 / 4;
                                int qz = i / 96 % 4;
                                int qy = i % 96 - 16;
                                System.out.println("seed " + seed + " chunk " + cx + "," + cz + " quart " + (cx * 4 + qx) + ","
                                    + qy + "," + (cz * 4 + qz) + ": vanilla " + Biomes.name(expected[i]) + ", ours "
                                    + Biomes.name(actual[i]));
                            }
                        }
                    }
                }
                int qy = in.readInt();
                int qx0 = in.readInt();
                int qz0 = in.readInt();
                int w = in.readInt();
                int h = in.readInt();
                for (int i = 0; i < w; i++) {
                    for (int j = 0; j < h; j++) {
                        int e = in.readUnsignedByte();
                        int a = source.biome(qx0 + i, qy, qz0 + j);
                        checked++;
                        counts[e]++;
                        if (e != a && seedDifferences++ < 10) {
                            System.out.println("seed " + seed + " layer quart " + (qx0 + i) + "," + qy + "," + (qz0 + j)
                                + ": vanilla " + Biomes.name(e) + ", ours " + Biomes.name(a));
                        }
                    }
                }
            }
            System.out.println("seed " + seed + ": " + seedDifferences + " differences");
            differences += seedDifferences;
        }
        StringBuilder seen = new StringBuilder();
        int kinds = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                kinds++;
                seen.append(' ').append(Biomes.name(i)).append('=').append(counts[i]);
            }
        }
        System.out.println(kinds + " biomes seen:" + seen);
        System.out.println(differences == 0 ? "ALL IDENTICAL (" + checked + " quarts)" : differences + " differences in " + checked + " quarts");
    }
}
