import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.XoroRandom;

/**
 * Lists chunks where a rare placed feature (RarityFilter first) passes its rarity draw and the chunk's center quart
 * at block y holds the biome: test areas for features like desert_well. Usage: FindRare seed step index rarity biomeId y radius [max]
 */
public final class FindRare {
    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        int step = Integer.parseInt(args[1]);
        int index = Integer.parseInt(args[2]);
        int rarity = Integer.parseInt(args[3]);
        int biome = Integer.parseInt(args[4]);
        int y = Integer.parseInt(args[5]);
        int radius = Integer.parseInt(args[6]);
        int max = args.length > 7 ? Integer.parseInt(args[7]) : 10;
        OverworldNoise noise = new OverworldNoise(seed);
        BiomeSource biomes = new BiomeSource(noise, new ColumnCache(noise), new Climate());
        XoroRandom random = new XoroRandom(0L);
        int found = 0;
        for (int r = 0; r <= radius && found < max; r++) {
            for (int cx = -r; cx <= r && found < max; cx++) {
                for (int cz = -r; cz <= r && found < max; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != r) {
                        continue;
                    }
                    long ds = random.setDecorationSeed(seed, cx << 4, cz << 4);
                    random.setFeatureSeed(ds, index, step);
                    if (random.nextFloat() < 1.0F / (float) rarity && biomes.biome(cx * 4 + 2, y >> 2, cz * 4 + 2) == biome) {
                        System.out.println(cx + "," + cz);
                        found++;
                    }
                }
            }
        }
    }
}
