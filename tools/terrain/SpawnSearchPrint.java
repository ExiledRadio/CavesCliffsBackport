import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.SpawnSearch;

/** The core's climate spawn search per seed, as Oracle1214 spawn prints vanilla's. Usage: SpawnSearchPrint editions seeds... */
public final class SpawnSearchPrint {
    public static void main(String[] args) {
        int editions = Integer.parseInt(args[0]);
        for (int i = 1; i < args.length; i++) {
            long seed = Long.parseLong(args[i]);
            OverworldNoise noise = new OverworldNoise(seed, editions);
            int[] p = new SpawnSearch(noise, new ColumnCache(noise)).find();
            System.out.println("spawn " + seed + " " + p[0] + " " + p[1]);
        }
    }
}
