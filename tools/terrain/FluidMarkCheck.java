import com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ChunkFiller;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.FluidMarks;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * The aquifer's post-processing marks on the noise fill's fluids against vanilla's (FillDump1214's marks_SEED.bin:
 * what doFill marked, aquifer.shouldScheduleFluidUpdate): every water and lava block the core's fill places, marked
 * by FluidMarks.flaggedAt or not, against the dump's set. Usage: FluidMarkCheck &lt;dir&gt; (JAVA_OPTS -Deditions=7 for
 * 1.21.4's rule).
 */
public final class FluidMarkCheck {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        int editions = Integer.getInteger("editions", 7);
        long total = 0;
        long marked = 0;
        long bad = 0;
        for (File f : dir.listFiles()) {
            if (!f.getName().startsWith("marks_")) {
                continue;
            }
            long seed = Long.parseLong(f.getName().substring(6, f.getName().indexOf(".bin")));
            OverworldNoise noise = new OverworldNoise(seed, editions);
            ColumnCache columns = new ColumnCache(noise);
            ChunkFiller filler = new ChunkFiller(noise, columns);
            FluidMarks marks = new FluidMarks(noise);
            char[] blocks = new char[16 * 16 * 384];
            long seedBad = 0;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), 1 << 16))) {
                int count = in.readInt();
                for (int c = 0; c < count; c++) {
                    int cx = in.readInt();
                    int cz = in.readInt();
                    int n = in.readInt();
                    Set<Long> vanilla = new HashSet<>();
                    for (int i = 0; i < n; i++) {
                        vanilla.add(key(in.readInt(), in.readInt(), in.readInt()));
                    }
                    java.util.Arrays.fill(blocks, (char) 0);
                    filler.fill(cx, cz, blocks);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int i = 0; i < 384; i++) {
                                int code = blocks[(x * 16 + z) * 384 + i];
                                if (code != BlockCodes.WATER && code != BlockCodes.LAVA) {
                                    continue;
                                }
                                int bx = (cx << 4) + x;
                                int by = i - 64;
                                int bz = (cz << 4) + z;
                                boolean ours = marks.flaggedAt(cx, cz, bx, by, bz);
                                boolean theirs = vanilla.contains(key(bx, by, bz));
                                total++;
                                marked += theirs ? 1 : 0;
                                if (ours != theirs && seedBad++ < 5) {
                                    System.out.println("seed " + seed + " " + bx + "," + by + "," + bz + " " + BlockCodes.state(code)
                                        + ": vanilla " + (theirs ? "marked" : "unmarked") + ", ours " + (ours ? "marked" : "unmarked"));
                                }
                            }
                        }
                    }
                }
            }
            System.out.println("seed " + seed + ": " + seedBad + " differ");
            bad += seedBad;
        }
        System.out.println(total + " fluid blocks, " + marked + " marked by vanilla; "
            + (bad == 0 ? "ALL IDENTICAL" : bad + " DIFFER"));
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }
}
