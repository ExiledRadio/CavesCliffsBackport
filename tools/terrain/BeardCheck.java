import com.exiledradio.cavesandcliffsbackport.terrain.core.Beardifier;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ChunkFiller;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Compares ChunkFiller.fill with a Beardifier against the oracle's beard_SEED.bin.gz (VanillaBeardDump: vanilla's
 * own Beardifier over made-up structure pieces), block by block, and the beard kernel against vanilla's. Also
 * counts the blocks the beard changed against a fill without it. Usage: BeardCheck <oracle-out> [seeds...]
 */
public final class BeardCheck {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        long[] seeds = {42L, 12345L, -7346534567L, 8675309L};
        if (args.length > 1) {
            seeds = new long[args.length - 1];
            for (int i = 1; i < args.length; i++) {
                seeds[i - 1] = Long.parseLong(args[i]);
            }
        }
        long differences = 0L;
        long checked = 0L;
        long chunksChecked = 0L;
        long changedByBeard = 0L;
        long beardNanos = 0L;
        long plainNanos = 0L;
        byte[] expected = new byte[16 * 16 * 384];
        char[] actual = new char[16 * 16 * 384];
        char[] plain = new char[16 * 16 * 384];
        for (long seed : seeds) {
            OverworldNoise noise = new OverworldNoise(seed);
            ColumnCache columns = new ColumnCache(noise);
            ChunkFiller filler = new ChunkFiller(noise, columns);
            long seedDifferences = 0L;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(
                    new FileInputStream(new File(dir, "beard_" + seed + ".bin.gz")), 1 << 16)))) {
                if (in.readLong() != Beardifier.kernelHash()) {
                    System.out.println("seed " + seed + ": beard kernel differs from vanilla's");
                    differences++;
                }
                int areas = in.readInt();
                for (int a = 0; a < areas; a++) {
                    int count = in.readInt();
                    List<Beardifier.Piece> pieces = new ArrayList<>();
                    for (int p = 0; p < count; p++) {
                        int minX = in.readInt();
                        int minY = in.readInt();
                        int minZ = in.readInt();
                        int maxX = in.readInt();
                        int maxY = in.readInt();
                        int maxZ = in.readInt();
                        int effect = in.readInt();
                        boolean jigsaw = in.readBoolean();
                        int delta = in.readInt();
                        boolean rigid = in.readBoolean();
                        int nj = in.readInt();
                        int[] junctions = new int[nj * 3];
                        for (int j = 0; j < junctions.length; j++) {
                            junctions[j] = in.readInt();
                        }
                        pieces.add(new Beardifier.Piece(minX, minY, minZ, maxX, maxY, maxZ, effect, jigsaw, delta, rigid,
                            junctions));
                    }
                    int chunks = in.readInt();
                    for (int c = 0; c < chunks; c++) {
                        int cx = in.readInt();
                        int cz = in.readInt();
                        in.readFully(expected);
                        long t0 = System.nanoTime();
                        filler.fill(cx, cz, actual, Beardifier.forChunk(cx, cz, pieces));
                        long t1 = System.nanoTime();
                        filler.fill(cx, cz, plain);
                        long t2 = System.nanoTime();
                        beardNanos += t1 - t0;
                        plainNanos += t2 - t1;
                        chunksChecked++;
                        for (int i = 0; i < expected.length; i++) {
                            checked++;
                            if (plain[i] != expected[i]) {
                                changedByBeard++;
                            }
                            if (expected[i] != actual[i] && seedDifferences++ < 12) {
                                int x = (cx << 4) + i / 384 / 16;
                                int z = (cz << 4) + i / 384 % 16;
                                int y = i % 384 - 64;
                                System.out.println("seed " + seed + " chunk " + cx + "," + cz + " block " + x + "," + y + ","
                                    + z + ": vanilla " + BlockCodes.name(expected[i]) + ", ours " + BlockCodes.name(actual[i]));
                            }
                        }
                    }
                }
            }
            System.out.println("seed " + seed + ": " + seedDifferences + " differences");
            differences += seedDifferences;
        }
        System.out.println("blocks the beardifier changed: " + changedByBeard);
        System.out.println(String.format(java.util.Locale.ROOT, "fill with beard %.3f ms/chunk, without %.3f ms/chunk",
            beardNanos / 1.0E6D / chunksChecked, plainNanos / 1.0E6D / chunksChecked));
        System.out.println(differences == 0 ? "ALL IDENTICAL (" + chunksChecked + " chunks, " + checked + " blocks)"
            : differences + " differences in " + checked + " blocks");
    }
}
