import com.exiledradio.cavesandcliffsbackport.terrain.core.ChunkFiller;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;

/**
 * Compares the terrain core with the vanilla 1.18.2 oracle output (MC1182Reference, Oracle1182Terrain).
 * Arguments: oracleDir [seeds...]. Exit code 1 on any difference.
 *
 * <p>-Deditions=N (Editions flags, e.g. 1 for 1.19's WILD) checks that edition's fill against a newer oracle's
 * bigchunks files only (oracle-newer's FillDump119): the router points and the small chunk set are 1.18.2's.
 */
public final class TerrainCheck {
    static final String[] ROUTER = {"barrier", "floodedness", "spread", "lava", "temperature", "vegetation",
        "continents", "erosion", "depth", "ridges", "initialDensity", "finalDensity", "veinToggle", "veinRidged",
        "veinGap"};

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        long[] seeds = {42L, 12345L, -7346534567L, 8675309L};
        if (args.length > 1) {
            seeds = new long[args.length - 1];
            for (int i = 1; i < args.length; i++) {
                seeds[i - 1] = Long.parseLong(args[i]);
            }
        }
        long bad = 0;
        for (long seed : seeds) {
            if (EDITIONS != 0) {
                bad += chunks(dir, seed, "bigchunks_");
                continue;
            }
            bad += points(dir, seed);
            bad += chunks(dir, seed, "chunks_");
            if (new File(dir, "bigchunks_" + seed + ".bin").isFile()) {
                bad += chunks(dir, seed, "bigchunks_");
            }
        }
        System.out.println(bad == 0 ? "ALL IDENTICAL" : "DIFFERENCES: " + bad);
        System.exit(bad == 0 ? 0 : 1);
    }

    static long points(File dir, long seed) throws Exception {
        OverworldNoise n = new OverworldNoise(seed);
        double[] col = new double[OverworldNoise.COL_SIZE];
        float[] point = new float[4];
        long[] bad = new long[ROUTER.length];
        int count;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new FileInputStream(new File(dir, "points_" + seed + ".bin"))))) {
            count = in.readInt();
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                n.column(x, z, col, 0, point);
                double offset = col[OverworldNoise.COL_OFFSET];
                double factor = col[OverworldNoise.COL_FACTOR];
                double jag = col[OverworldNoise.COL_JAGGEDNESS];
                double sx = col[OverworldNoise.COL_SHIFT_X];
                double sz = col[OverworldNoise.COL_SHIFT_Z];
                double[] ours = {n.barrier(x, y, z), n.floodedness(x, y, z), n.spread(x, y, z), n.lava(x, y, z),
                    n.shifted(n.temperatureNoise(), x, z, sx, sz), n.shifted(n.vegetationNoise(), x, z, sx, sz),
                    col[OverworldNoise.COL_CONTINENTS], col[OverworldNoise.COL_EROSION],
                    OverworldNoise.depth(y, offset), col[OverworldNoise.COL_RIDGES],
                    OverworldNoise.initialDensity(y, offset, factor),
                    OverworldNoise.finalDensity(n.mainDensity(x, y, z, offset, factor, jag),
                        OverworldNoise.noodleDensity(n.noodleToggle(x, y, z), n.noodleThickness(x, y, z),
                            n.noodleRidgeA(x, y, z), n.noodleRidgeB(x, y, z))),
                    n.veinToggle(x, y, z), OverworldNoise.veinRidged(n.veinA(x, y, z), n.veinB(x, y, z)),
                    n.veinGap(x, y, z)};
                for (int f = 0; f < ROUTER.length; f++) {
                    double theirs = in.readDouble();
                    if (Double.doubleToLongBits(theirs + 0.0D) != Double.doubleToLongBits(ours[f] + 0.0D)) {
                        if (bad[f]++ < 2) {
                            System.out.println("  seed " + seed + " " + ROUTER[f] + " at " + x + "," + y + "," + z
                                + ": vanilla " + theirs + " ours " + ours[f]);
                        }
                    }
                }
            }
        }
        long total = 0;
        StringBuilder line = new StringBuilder("seed " + seed + " points " + count + ":");
        for (int f = 0; f < ROUTER.length; f++) {
            total += bad[f];
            if (bad[f] != 0) {
                line.append(' ').append(ROUTER[f]).append('=').append(bad[f]);
            }
        }
        System.out.println(line + (total == 0 ? " identical" : ""));
        return total;
    }

    static final int EDITIONS = Integer.getInteger("editions", 0);

    static long chunks(File dir, long seed, String prefix) throws Exception {
        OverworldNoise n = new OverworldNoise(seed, EDITIONS);
        ChunkFiller filler = new ChunkFiller(n);
        char[] ours = new char[16 * 16 * 384];
        byte[] theirs = new byte[16 * 16 * 384];
        long bad = 0;
        long[] kinds = new long[256];
        int count;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new FileInputStream(new File(dir, prefix + seed + ".bin")), 1 << 20))) {
            count = in.readInt();
            for (int i = 0; i < count; i++) {
                int cx = in.readInt();
                int cz = in.readInt();
                in.readFully(theirs);
                filler.fill(cx, cz, ours);
                long here = 0;
                for (int j = 0; j < ours.length; j++) {
                    kinds[theirs[j] & 255]++;
                    if (ours[j] != theirs[j]) {
                        if (bad + here < 5) {
                            System.out.println("  seed " + seed + " chunk " + cx + "," + cz + " x " + (j / 384 / 16)
                                + " z " + (j / 384 % 16) + " y " + (j % 384 - 64) + ": vanilla " + theirs[j]
                                + " ours " + ours[j]);
                        }
                        here++;
                    }
                }
                bad += here;
            }
        }
        System.out.println("seed " + seed + " " + prefix + " " + count + ": " + (bad == 0 ? "identical" : bad + " blocks differ")
            + "  (air " + kinds[0] + " stone " + kinds[1] + " water " + kinds[2] + " lava " + kinds[3] + " vein blocks "
            + (kinds[4] + kinds[5] + kinds[6] + kinds[7] + kinds[8] + kinds[9]) + " other " + kinds[255] + ")");
        return bad;
    }
}
