import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Carvers;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ChunkFiller;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.McMath;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.SurfaceBuilder;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.zip.GZIPInputStream;

/**
 * Compares ChunkFiller.fill + SurfaceBuilder.build + Carvers.carve against the oracle's carved_SEED.bin.gz
 * (VanillaCarverDump), block by block, and McMath's sine table against Mth's. Prints ALL IDENTICAL or the first
 * differences. Usage: CarverCheck <oracle-out> [seeds...]
 */
public final class CarverCheck {
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
        long chunksChecked = 0L;
        long carvedBlocks = 0L;
        long[] counts = new long[BlockCodes.COUNT];
        byte[] expected = new byte[16 * 16 * 384];
        byte[] actual = new byte[16 * 16 * 384];
        byte[] before = new byte[16 * 16 * 384];
        for (long seed : seeds) {
            OverworldNoise noise = new OverworldNoise(seed);
            ColumnCache columns = new ColumnCache(noise);
            ChunkFiller filler = new ChunkFiller(noise, columns);
            BiomeSource biomes = new BiomeSource(noise, columns, climate);
            SurfaceBuilder surface = new SurfaceBuilder(noise, columns, biomes);
            Carvers carvers = new Carvers(noise, columns, surface);
            long seedDifferences = 0L;
            int badChunks = 0;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(
                    new FileInputStream(new File(dir, "carved_" + seed + ".bin.gz")), 1 << 16)))) {
                long sinHash = in.readLong();
                if (sinHash != McMath.sinTableHash()) {
                    System.out.println("seed " + seed + ": sine table differs from Mth's");
                    differences++;
                }
                int count = in.readInt();
                for (int c = 0; c < count; c++) {
                    int cx = in.readInt();
                    int cz = in.readInt();
                    in.readFully(expected);
                    filler.fill(cx, cz, actual);
                    surface.build(cx, cz, actual);
                    System.arraycopy(actual, 0, before, 0, actual.length);
                    carvers.carve(cx, cz, actual);
                    chunksChecked++;
                    int chunkDifferences = 0;
                    for (int i = 0; i < expected.length; i++) {
                        checked++;
                        counts[expected[i]]++;
                        if (expected[i] != before[i]) {
                            carvedBlocks++;
                        }
                        if (expected[i] != actual[i]) {
                            chunkDifferences++;
                            if (seedDifferences++ < 12) {
                                int x = (cx << 4) + i / 384 / 16;
                                int z = (cz << 4) + i / 384 % 16;
                                int y = i % 384 - 64;
                                System.out.println("seed " + seed + " chunk " + cx + "," + cz + " block " + x + "," + y + ","
                                    + z + ": vanilla " + BlockCodes.name(expected[i]) + ", ours " + BlockCodes.name(actual[i])
                                    + " (before carving " + BlockCodes.name(before[i]) + ")");
                            }
                        }
                    }
                    if (chunkDifferences > 0) {
                        badChunks++;
                    }
                }
            }
            System.out.println("seed " + seed + ": " + seedDifferences + " differences in " + badChunks + " chunks");
            differences += seedDifferences;
        }
        StringBuilder seen = new StringBuilder();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                seen.append(' ').append(BlockCodes.name(i)).append('=').append(counts[i]);
            }
        }
        System.out.println("blocks:" + seen);
        System.out.println("blocks the carvers changed: " + carvedBlocks);
        System.out.println(differences == 0 ? "ALL IDENTICAL (" + chunksChecked + " chunks, " + checked + " blocks)"
            : differences + " differences in " + checked + " blocks");
    }
}
