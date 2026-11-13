import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;

/** LandSeed seed...: land share at y 64 and lush/dripstone share at y 0 in the 768x768 blocks around 0,0. */
public final class LandSeed {
    public static void main(String[] args) {
        for (String a : args) {
            long seed = Long.parseLong(a);
            OverworldNoise noise = new OverworldNoise(seed);
            BiomeSource b = new BiomeSource(noise, new ColumnCache(noise), new Climate());
            int land = 0, lush = 0, drip = 0, n = 0;
            for (int x = -384; x < 384; x += 16) for (int z = -384; z < 384; z += 16) {
                int s = b.biome(x >> 2, 64 >> 2, z >> 2);
                if (s < 39 || s > 47) land++;
                int d = b.biome(x >> 2, 0, z >> 2);
                if (d == 50) lush++;
                if (d == 49) drip++;
                n++;
            }
            System.out.println(seed + " land " + 100 * land / n + "% lush " + 100 * lush / n + "% dripstone " + 100 * drip / n + "%");
        }
    }
}
