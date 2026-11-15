import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;

/** Prints the first chunk (on a coarse spiral from 0,0) whose quart at block y holds the given biome id. Usage: FindBiome seed biomeId y */
public final class FindBiome {
    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        int biome = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int editions = Integer.getInteger("editions", 0);
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
                        System.out.println(cx + "," + cz);
                        return;
                    }
                }
            }
        }
        System.out.println("none");
    }
}
