import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;

/**
 * Prints the first chunk (on a coarse spiral from 0,0) whose quart at block y holds the given biome id. With editions
 * (Editions flags) and a count, the first coarse point whose 7 x 7 chunks around hold the biome at that many chunk
 * centers or more (a dense area). Usage: FindBiome seed biomeId y [editions [count]]
 */
public final class FindBiome {
    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        int biome = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int editions = args.length > 3 ? Integer.parseInt(args[3]) : Integer.getInteger("editions", 0);
        int need = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        OverworldNoise noise = new OverworldNoise(seed, editions);
        BiomeSource biomes = new BiomeSource(noise, new ColumnCache(noise), new Climate(editions));
        for (int r = 0; r < 600; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int cx = dx * 8;
                    int cz = dz * 8;
                    if (biomes.biome(cx * 4 + 2, y >> 2, cz * 4 + 2) == biome) {
                        int n = 0;
                        for (int i = -3; i <= 3 && need > 0; i++) {
                            for (int j = -3; j <= 3; j++) {
                                n += biomes.biome((cx + i) * 4 + 2, y >> 2, (cz + j) * 4 + 2) == biome ? 1 : 0;
                            }
                        }
                        if (n >= need) {
                            System.out.println(cx + "," + cz + (need > 0 ? " (" + n + " of 49)" : ""));
                            return;
                        }
                    }
                }
            }
        }
        System.out.println("none");
    }
}
