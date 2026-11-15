import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Carvers;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ChunkFiller;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Region;
import com.exiledradio.cavesandcliffsbackport.terrain.core.SurfaceBuilder;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * Decorates the chunks VanillaFeatureDump decorated, in its order (two side x side areas per seed: around 0,0 and
 * a far point), with the same placed features (-Dfeatures there, the features argument here), and compares every
 * block with features_SEED.bin.gz. Usage: FeatureCheck <dump-dir> <side> <features,comma,separated|all> [seeds...]
 * Prints ALL IDENTICAL or the first differences, the states seen and decoration ms/chunk.
 */
public final class FeatureCheck {
    static final class Chunk {
        final char[] blocks = new char[16 * 16 * 384];
        final int[] oceanFloor = new int[512];
        final int[] heights = new int[Region.HEIGHTMAPS * 256];
    }

    /**
     * -DlooseBees: a bee nest and air (or a plant) compare equal. BeehiveDecorator shuffles with the JVM's shared random, so
     * vanilla's hive position is not reproducible (every draw from the feature random still is).
     */
    static final boolean looseBees = System.getProperty("looseBees") != null;

    static boolean isBee(int code) {
        return BlockCodes.name(code).equals("bee_nest");
    }

    static boolean beeOrAir(int code) {
        // A plant patch placed after the tree fills the air where the other side's nest went.
        return code == BlockCodes.AIR || BlockCodes.name(code).equals("bee_nest")
            || (BlockCodes.flags(code) & BlockCodes.F_BLOCKS_MOTION) == 0 && !BlockCodes.liquid(code);
    }

    /** -Dtrace=file: after each chunk, MultifaceBlock.DIRECTIONS' order (glow lichen's process-global shuffle). */
    static final java.io.PrintStream TRACE = traceStream();

    private static java.io.PrintStream traceStream() {
        String f = System.getProperty("trace");
        try {
            return f == null ? null : new java.io.PrintStream(new java.io.FileOutputStream(f));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String[] DIRECTION_NAMES = {"down", "up", "north", "south", "west", "east"};

    static String traceHash(Region region, int cx, int cz) {
        StringBuilder b = new StringBuilder();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int h = 0;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = -64; y < 320; y++) {
                            h = h * 31 + BlockCodes.state(region.get(((cx + dx) << 4) + x, y, ((cz + dz) << 4) + z)).hashCode();
                        }
                    }
                }
                b.append(Integer.toHexString(h)).append(' ');
            }
        }
        return b.toString();
    }

    static String traceDirections() throws ReflectiveOperationException {
        java.lang.reflect.Field d = Class.forName("com.exiledradio.cavesandcliffsbackport.terrain.core.LushCaves").getDeclaredField("DIRECTIONS");
        d.setAccessible(true);
        int[] a = (int[]) d.get(null);
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            b.append(i == 0 ? "" : ", ").append(DIRECTION_NAMES[a[i]]);
        }
        return b.append("]").toString();
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        int side = Integer.parseInt(args[1]);
        Set<String> only = args[2].equals("all") ? null : new HashSet<>(Arrays.asList(args[2].split(",")));
        long[] seeds = {42L, 12345L, -7346534567L, 8675309L};
        if (args.length > 3) {
            seeds = new long[args.length - 3];
            for (int i = 3; i < args.length; i++) {
                seeds[i - 3] = Long.parseLong(args[i]);
            }
        }
        // -Deditions: the newer biomes (Editions flags); -Drules: the newest version whose feature rules apply (118..121).
        int editions = Integer.getInteger("editions", 0);
        int rules = Integer.getInteger("rules", 118);
        Climate climate = new Climate(editions);
        long differences = 0L;
        long checked = 0L;
        long decoratedTotal = 0L;
        long nanos = 0L;
        Map<String, Long> seen = new TreeMap<>();
        for (long seed : seeds) {
            OverworldNoise noise = new OverworldNoise(seed, editions);
            ColumnCache columns = new ColumnCache(noise);
            ChunkFiller filler = new ChunkFiller(noise, columns);
            BiomeSource biomes = new BiomeSource(noise, columns, climate);
            SurfaceBuilder surface = new SurfaceBuilder(noise, columns, biomes);
            Carvers carvers = new Carvers(noise, columns, surface);
            Decorator decorator = new Decorator(noise, biomes);
            decorator.only(only);
            decorator.rules(rules);
            String tc = System.getProperty("tracechunk");
            Map<Long, Chunk> chunks = new HashMap<>();
            Random rnd = new Random(seed * 31L + 5L);
            int[][] centers = {{0, 0}, {rnd.nextInt(20000) - 10000, rnd.nextInt(20000) - 10000}};
            // -Dcenters=x,z/x,z/... replaces the two areas (chunk coordinates of each area's center).
            String given = System.getProperty("centers");
            if (given != null) {
                String[] parts = given.split("/");
                centers = new int[parts.length][];
                for (int c = 0; c < parts.length; c++) {
                    String[] xz = parts[c].split(",");
                    centers[c] = new int[]{Integer.parseInt(xz[0].trim()), Integer.parseInt(xz[1].trim())};
                }
            }
            List<int[]> order = new ArrayList<>();
            for (int[] c : centers) {
                for (int i = 0; i < side * side; i++) {
                    order.add(new int[]{c[0] - side / 2 + i / side, c[1] - side / 2 + i % side});
                }
            }
            Region region = new Region();
            char[][] b9 = new char[Region.CHUNKS][];
            int[][] h9 = new int[Region.CHUNKS][];
            int[][] m9 = new int[Region.CHUNKS][];
            int r = Region.READ;
            for (int[] p : order) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        long key = (long) (p[0] + dx) << 32 | ((p[1] + dz) & 0xFFFFFFFFL);
                        Chunk c = chunks.get(key);
                        if (c == null) {
                            c = new Chunk();
                            filler.fill(p[0] + dx, p[1] + dz, c.blocks);
                            surface.build(p[0] + dx, p[1] + dz, c.blocks);
                            carvers.carve(p[0] + dx, p[1] + dz, c.blocks);
                            Region.worldgenHeights(c.blocks, c.oceanFloor);
                            Region.primeHeightmaps(c.blocks, c.heights);
                            chunks.put(key, c);
                        }
                        b9[(dx + r) * Region.SIDE + dz + r] = c.blocks;
                        h9[(dx + r) * Region.SIDE + dz + r] = c.oceanFloor;
                        m9[(dx + r) * Region.SIDE + dz + r] = c.heights;
                    }
                }
                region.set(p[0], p[1], b9, h9, m9);
                long t = System.nanoTime();
                decorator.decorate(region);
                nanos += System.nanoTime() - t;
                if (TRACE != null) {
                    TRACE.println(p[0] + "," + p[1] + " " + traceDirections() + " " + traceHash(region, p[0], p[1]));
                }
                decoratedTotal++;
            }
            long seedDifferences = 0L;
            // The live heightmaps must equal freshly primed ones (no section write may change a predicate).
            int[] primed = new int[Region.HEIGHTMAPS * 256];
            for (Chunk c : chunks.values()) {
                Region.primeHeightmaps(c.blocks, primed);
                if (!Arrays.equals(primed, c.heights)) {
                    if (seedDifferences++ < Integer.getInteger("shown", 12)) {
                        System.out.println("seed " + seed + ": a chunk's live heightmaps differ from its blocks");
                    }
                }
            }
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(
                    new FileInputStream(new File(dir, "features_" + seed + ".bin.gz")), 1 << 16), 1 << 16))) {
                int n = in.readInt();
                int[] codes = new int[n];
                String[] names = new String[n];
                for (int i = 0; i < n; i++) {
                    names[i] = in.readUTF();
                    codes[i] = BlockCodes.byState(names[i]);
                    if (codes[i] < 0 && names[i].contains("waterlogged=false")) {
                        // 1.19+ gave every leaves block a waterlogged property: dry ones are the core's 1.18 codes.
                        codes[i] = BlockCodes.byState(names[i].replace(",waterlogged=false", "").replace("[waterlogged=false]", ""));
                    }
                    if (codes[i] < 0) {
                        System.out.println("seed " + seed + ": no code for vanilla state " + names[i]);
                    }
                }
                int count = in.readInt();
                for (int c = 0; c < count; c++) {
                    int cx = in.readInt();
                    int cz = in.readInt();
                    Chunk ours = chunks.get((long) cx << 32 | (cz & 0xFFFFFFFFL));
                    // The dump reads through ProtoChunk.getBlockState: cave air in all-air sections shows as air.
                    Region.readAsVanilla(ours.blocks);
                    for (int i = 0; i < 16 * 16 * 384; i++) {
                        int v = in.readChar();
                        checked++;
                        seen.merge(names[v], 1L, Long::sum);
                        if (codes[v] != ours.blocks[i] && !(looseBees && (isBee(codes[v]) || isBee(ours.blocks[i])) && beeOrAir(codes[v]) && beeOrAir(ours.blocks[i]))) {
                            if (seedDifferences++ < Integer.getInteger("shown", 12)) {
                                System.out.println("seed " + seed + " chunk " + cx + "," + cz + " block " + ((cx << 4) + i / 384 / 16)
                                    + "," + (i % 384 - 64) + "," + ((cz << 4) + i / 384 % 16) + ": vanilla " + names[v]
                                    + ", ours " + BlockCodes.state(ours.blocks[i]));
                            }
                        }
                    }
                }
            }
            System.out.println("seed " + seed + ": " + seedDifferences + " differences");
            differences += seedDifferences;
        }
        System.out.println("states: " + seen);
        System.out.println(String.format(java.util.Locale.ROOT, "decoration %.3f ms/chunk (java %s)",
            nanos / 1.0E6D / decoratedTotal, System.getProperty("java.version")));
        System.out.println(differences == 0 ? "ALL IDENTICAL (" + decoratedTotal + " chunks decorated, " + checked + " blocks)"
            : differences + " differences in " + checked + " blocks");
    }
}
