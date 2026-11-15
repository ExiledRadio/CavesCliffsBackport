import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Editions;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * The newer biome tables ({@link Editions}) against vanilla 1.19.2, 1.20.1 and 1.21.4 (tools/terrain/oracle-newer):
 * <ul>
 *   <li>OverworldBiomeBuilder's parameter points, every one in order, against the oracle's "leaf" lines: WILD
 *       against 1.19.2's, WILD | CHERRY against 1.20.1's; CHERRY alone against 1.20.1's with the deep dark left out
 *       and each swamp / mangrove swamp pair joined back into 1.18.2's one swamp line; WILD | CHERRY | PALE against
 *       1.21.4's, and PALE with either or neither of the others against 1.21.4's with theirs taken out the same way
 *       (the cherry grove's leaves back to meadow);</li>
 *   <li>the biome of every quart of the oracles' biome maps (Oracle1xx biomes, a cold search like vanilla's
 *       BiomeCheck dump), through BiomeSource.fillChunk.</li>
 * </ul>
 * Usage: NewerBiomeCheck &lt;oracle119.txt&gt; &lt;biomes119 dir&gt; &lt;oracle120.txt&gt; &lt;biomes120 dir&gt;
 * [&lt;oracle1214.txt&gt; &lt;biomes1214 dir&gt;]
 */
public final class NewerBiomeCheck {
    static long differences;

    public static void main(String[] args) throws Exception {
        List<String> l119 = leaves(args[0]);
        List<String> l120 = leaves(args[2]);
        leaves("1.19.2 (WILD)", new Climate(Editions.WILD), l119);
        leaves("1.20.1 (WILD | CHERRY)", new Climate(Editions.WILD | Editions.CHERRY), l120);
        leaves("1.20.1 without Wild (CHERRY)", new Climate(Editions.CHERRY), withoutWild(l120));
        maps("1.19.2", Editions.WILD, new File(args[1]));
        maps("1.20.1", Editions.WILD | Editions.CHERRY, new File(args[3]));
        if (args.length > 5) {
            List<String> l1214 = leaves(args[4]);
            int pale = Editions.PALE;
            leaves("1.21.4 (WILD | CHERRY | PALE)", new Climate(Editions.WILD | Editions.CHERRY | pale), l1214);
            leaves("1.21.4 without Trails (WILD | PALE)", new Climate(Editions.WILD | pale), cherryToMeadow(l1214));
            leaves("1.21.4 without Wild (CHERRY | PALE)", new Climate(Editions.CHERRY | pale), withoutWild(l1214));
            leaves("1.21.4 alone (PALE)", new Climate(pale), cherryToMeadow(withoutWild(l1214)));
            maps("1.21.4", Editions.WILD | Editions.CHERRY | pale, new File(args[5]));
        }
        System.out.println(differences == 0 ? "ALL IDENTICAL" : differences + " DIFFERENCES");
    }

    static List<String> leaves(String path) throws Exception {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(new File(path).toPath(), StandardCharsets.UTF_8)) {
            if (line.startsWith("leaf ")) {
                out.add(line.substring(5));
            }
        }
        return out;
    }

    /** 1.20.1's leaves as 1.20.1 without the mangrove swamp and the deep dark would have them. */
    static List<String> withoutWild(List<String> in) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            String[] a = in.get(i).split(" ");
            if (a[0].equals("deep_dark")) {
                continue;
            }
            if (a[0].equals("swamp") && i + 2 < in.size() && in.get(i + 2).startsWith("mangrove_swamp ")) {
                // addSurfaceBiome adds depth 0 then depth 1: swamp 0, swamp 1, mangrove 0, mangrove 1.
                for (int k = 0; k < 2; k++) {
                    String[] s = in.get(i + k).split(" ");
                    String[] m = in.get(i + 2 + k).split(" ");
                    s[2] = m[2];
                    out.add(String.join(" ", s));
                }
                i += 3;
                continue;
            }
            out.add(in.get(i));
        }
        return out;
    }

    /** The cherry grove's leaves as meadow (PLATEAU_BIOMES_VARIANT's slots before it: null, the plateau meadow). */
    static List<String> cherryToMeadow(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) {
            out.add(s.startsWith("cherry_grove ") ? "meadow" + s.substring("cherry_grove".length()) : s);
        }
        return out;
    }

    /** The cherry grove's leaves back to what PLATEAU_BIOMES_VARIANT had before it (null: the plateau biome). */
    static List<String> withoutCherry(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) {
            String[] a = s.split(" ");
            // 1.19 swapped pickBadlandsBiome's two answers; 1.18.2 has them the other way round.
            if (a[0].equals("badlands") && in.indexOf(s) >= 0 && isLowHumidityBadlands(a)) {
                a[0] = "eroded_badlands";
            } else if (a[0].equals("eroded_badlands")) {
                a[0] = "badlands";
            }
            if (a[0].equals("cherry_grove")) {
                // [1][0] and [2][0], [2][1] were null: PLATEAU_BIOMES, meadow for all three.
                a[0] = "meadow";
            }
            out.add(String.join(" ", a));
        }
        return out;
    }

    /** A badlands leaf from pickBadlandsBiome's humidity &lt; 2 branch: humidity max at most -0.1 (humidities[1]). */
    static boolean isLowHumidityBadlands(String[] a) {
        return Long.parseLong(a[4]) <= -1000L;
    }

    static void leaves(String what, Climate climate, List<String> expected) {
        int n = climate.leafCount();
        int bad = 0;
        if (n != expected.size()) {
            System.out.println(what + ": " + n + " leaves, vanilla " + expected.size());
            bad++;
        }
        for (int i = 0; i < Math.min(n, expected.size()); i++) {
            String ours = climate.describeLeaf(i);
            if (!ours.equals(expected.get(i))) {
                if (bad++ < 5) {
                    System.out.println(what + ": leaf " + i + " is " + ours + ", vanilla " + expected.get(i));
                }
            }
        }
        System.out.println(what + ": " + n + " leaves, " + (bad == 0 ? "identical" : bad + " differ"));
        differences += bad;
    }

    static void maps(String what, int editions, File dir) throws Exception {
        Climate climate = new Climate(editions);
        int[] counts = new int[Biomes.COUNT];
        for (File f : dir.listFiles()) {
            String name = f.getName();
            if (!name.startsWith("biomes_")) {
                continue;
            }
            long seed = Long.parseLong(name.substring(7, name.indexOf(".bin")));
            OverworldNoise noise = new OverworldNoise(seed, editions);
            BiomeSource source = new BiomeSource(noise, new ColumnCache(noise), climate);
            long bad = 0;
            long total = 0;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(
                    new FileInputStream(f), 1 << 16)))) {
                int leaves = in.readInt();
                if (leaves != climate.leafCount()) {
                    System.out.println(what + " seed " + seed + ": " + leaves + " leaves, ours " + climate.leafCount());
                    bad++;
                }
                int names = in.readInt();
                int[] map = new int[names];
                for (int i = 0; i < names; i++) {
                    map[i] = Biomes.byName(in.readUTF());
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
                        int e = map[expected[i]];
                        counts[e]++;
                        total++;
                        if (actual[i] != e) {
                            if (bad++ < 5) {
                                System.out.println(what + " seed " + seed + " chunk " + cx + "," + cz + " quart " + i
                                        + ": ours " + Biomes.name(actual[i]) + ", vanilla " + Biomes.name(e));
                            }
                        }
                    }
                }
            }
            System.out.println(what + " seed " + seed + ": " + total + " quarts, " + bad + " differ");
            differences += bad;
        }
        System.out.println(what + ": mangrove_swamp=" + counts[Biomes.MANGROVE_SWAMP] + " deep_dark="
                + counts[Biomes.DEEP_DARK] + " cherry_grove=" + counts[Biomes.CHERRY_GROVE] + " pale_garden=" + counts[Biomes.PALE_GARDEN] + " swamp="
                + counts[Biomes.SWAMP] + " meadow=" + counts[Biomes.MEADOW]);
    }
}
