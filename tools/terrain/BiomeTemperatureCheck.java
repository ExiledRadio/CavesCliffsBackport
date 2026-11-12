import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.zip.GZIPInputStream;

/**
 * Compares Biomes.temperature and coldEnoughToSnow with vanilla's Biome.getTemperature / coldEnoughToSnow at the
 * oracle's samples (biome_temperatures.bin.gz, VanillaBiomeInfoDump): every biome, any height, out to 3 million
 * blocks. Usage: BiomeTemperatureCheck <oracle-out>
 */
public final class BiomeTemperatureCheck {
    public static void main(String[] args) throws Exception {
        int bad = 0;
        int n;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(
                new FileInputStream(new File(args[0], "biome_temperatures.bin.gz")))))) {
            n = in.readInt();
            for (int i = 0; i < n; i++) {
                int b = in.readInt();
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                float t = in.readFloat();
                boolean cold = in.readBoolean();
                float ours = Biomes.temperature(b, x, y, z);
                if (Float.floatToIntBits(ours) != Float.floatToIntBits(t) || Biomes.coldEnoughToSnow(b, x, y, z) != cold) {
                    if (bad++ < 10) {
                        System.out.println("biome " + b + " at " + x + "," + y + "," + z + ": vanilla " + t + ", ours " + ours);
                    }
                }
            }
        }
        System.out.println(bad == 0 ? "ALL IDENTICAL (" + n + " samples)" : bad + " differences of " + n);
    }
}
