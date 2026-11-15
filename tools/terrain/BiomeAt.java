import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;

/** The core's biome at a block (its quart), as Oracle1214 point prints vanilla's. Usage: BiomeAt editions seed x y z */
public final class BiomeAt {
    public static void main(String[] args) {
        int e = Integer.parseInt(args[0]);
        long seed = Long.parseLong(args[1]);
        int x = Integer.parseInt(args[2]);
        int y = Integer.parseInt(args[3]);
        int z = Integer.parseInt(args[4]);
        OverworldNoise noise = new OverworldNoise(seed, e);
        BiomeSource s = new BiomeSource(noise, new ColumnCache(noise), new Climate(e));
        System.out.println("point " + seed + " " + x + " " + y + " " + z + " " + Biomes.name(s.biome(x >> 2, y >> 2, z >> 2)));
    }
}
