import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;

/** Prints the ported placed features, comma separated, for VanillaFeatureDump's -Dfeatures. */
public final class PortedFeatures {
    public static void main(String[] args) {
        int editions = Integer.getInteger("editions", 0);
        OverworldNoise noise = new OverworldNoise(0L, editions);
        ColumnCache columns = new ColumnCache(noise);
        Decorator d = new Decorator(noise, new BiomeSource(noise, columns, new Climate(editions)));
        System.out.println(String.join(",", d.ported()));
    }
}
