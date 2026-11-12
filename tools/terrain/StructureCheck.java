import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Structures;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Compares Structures.starts against the oracle's structures_SEED.bin.gz (VanillaStructureDump: vanilla's own
 * createStructures choice and ConfiguredStructureFeature.generate over the same candidates), set by set, and
 * times each set. Usage: StructureCheck <oracle-out> region everyChunkRegion [seeds...]
 */
public final class StructureCheck {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        int region = Integer.parseInt(args[1]);
        int everyRegion = Integer.parseInt(args[2]);
        long[] seeds = {42L, 12345L, -7346534567L, 8675309L};
        if (args.length > 3) {
            seeds = new long[args.length - 3];
            for (int i = 3; i < args.length; i++) {
                seeds[i - 3] = Long.parseLong(args[i]);
            }
        }
        Climate climate = new Climate();
        long differences = 0L;
        long startsChecked = 0L;
        for (long seed : seeds) {
            OverworldNoise noise = new OverworldNoise(seed);
            ColumnCache columns = new ColumnCache(noise);
            BiomeSource biomes = new BiomeSource(noise, columns, climate);
            Structures structures = new Structures(noise, columns, biomes);
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(
                    new FileInputStream(new File(dir, "structures_" + seed + ".bin.gz")), 1 << 16)))) {
                int sets = in.readInt();
                for (int s = 0; s < sets; s++) {
                    String name = in.readUTF();
                    int count = in.readInt();
                    List<int[]> expected = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        expected.add(new int[]{in.readInt(), in.readInt(), in.readInt()});
                    }
                    int set = -1;
                    for (int i = 0; i < Structures.SETS; i++) {
                        if (Structures.SET_NAMES[i].equals(name)) {
                            set = i;
                        }
                    }
                    int r = name.equals("buried_treasures") || name.equals("mineshafts") ? everyRegion : region;
                    long t = System.nanoTime();
                    List<int[]> actual = structures.starts(set, -r, -r, r, r);
                    long ms = (System.nanoTime() - t) / 1000000L;
                    int bad = 0;
                    int n = Math.max(expected.size(), actual.size());
                    for (int i = 0; i < n; i++) {
                        int[] e = i < expected.size() ? expected.get(i) : null;
                        int[] a = i < actual.size() ? actual.get(i) : null;
                        boolean same = e != null && a != null && e[0] == a[0] && e[1] == a[1] && e[2] == a[2];
                        if (!same) {
                            if (bad++ < 6) {
                                System.out.println("seed " + seed + " " + name + " #" + i + ": vanilla " + str(e, set)
                                    + ", ours " + str(a, set));
                            }
                        }
                    }
                    startsChecked += expected.size();
                    differences += bad;
                    System.out.println("seed " + seed + " " + name + ": " + expected.size() + " starts, "
                        + (bad == 0 ? "identical" : bad + " differ") + ", " + ms + " ms");
                }
            }
        }
        System.out.println(differences == 0 ? "ALL IDENTICAL (" + startsChecked + " starts)" : differences + " differences");
    }

    private static String str(int[] s, int set) {
        return s == null ? "none" : s[0] + "," + s[1] + " " + Structures.STRUCTURE_NAMES[set][s[2]];
    }
}
