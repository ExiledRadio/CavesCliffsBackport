import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.SpawnSearch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Compares SpawnSearch (the climate spawn position) with the oracle's spawns.txt (VanillaSpawnDump). Usage:
 * SpawnCheck <oracle-out>. Exit code 1 on any difference.
 */
public final class SpawnCheck {
    public static void main(String[] args) throws Exception {
        int seeds = 0;
        int bad = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(new File(args[0], "spawns.txt")))) {
            for (String line; (line = in.readLine()) != null; ) {
                String[] f = line.trim().split(" ");
                long seed = Long.parseLong(f[0]);
                int x = Integer.parseInt(f[1]);
                int z = Integer.parseInt(f[2]);
                OverworldNoise noise = new OverworldNoise(seed);
                int[] ours = new SpawnSearch(noise, new ColumnCache(noise)).find();
                seeds++;
                if (ours[0] != x || ours[1] != z) {
                    if (bad++ < 10) {
                        System.out.println("seed " + seed + ": vanilla " + x + "," + z + ", ours " + ours[0] + "," + ours[1]);
                    }
                }
            }
        }
        System.out.println(bad == 0 ? "ALL IDENTICAL (" + seeds + " seeds)" : bad + " of " + seeds + " seeds differ");
        System.exit(bad == 0 ? 0 : 1);
    }
}
