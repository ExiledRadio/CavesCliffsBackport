package net.minecraft.world.level.levelgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

/**
 * Every biome's climate settings (base temperature, temperature modifier, downfall, precipitation) as
 * biome_info.txt, and biome_temperatures.bin.gz: int count, then per sample int biome, x, y, z, float
 * Biome.getTemperature, boolean coldEnoughToSnow, for the Caves and Cliffs Backport's BiomeTemperatureCheck.
 * Arguments: outDir samplesPerBiome
 */
public final class VanillaBiomeInfoDump {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        int per = Integer.parseInt(args[1]);
        VanillaWorld world = new VanillaWorld(0L);
        Field climateField = Biome.class.getDeclaredField("climateSettings");
        climateField.setAccessible(true);
        try (PrintWriter p = new PrintWriter(new File(out, "biome_info.txt"), "UTF-8")) {
            p.println("# id name baseTemperatureBits modifier downfallBits precipitation");
            for (int id = 0; id < world.holders.length; id++) {
                Biome b = world.holders[id].value();
                Object c = climateField.get(b);
                Field mod = c.getClass().getDeclaredField("temperatureModifier");
                mod.setAccessible(true);
                p.println(id + " " + VanillaWorld.BIOME_NAMES.get(id) + " " + Float.floatToIntBits(b.getBaseTemperature()) + " "
                    + mod.get(c) + " " + Float.floatToIntBits(b.getDownfall()) + " " + b.getPrecipitation());
            }
        }
        java.lang.reflect.Method temp = Biome.class.getDeclaredMethod("getTemperature", BlockPos.class);
        temp.setAccessible(true);
        Random r = new Random(77L);
        int n = world.holders.length * per;
        try (DataOutputStream w = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(
                new FileOutputStream(new File(out, "biome_temperatures.bin.gz")))))) {
            w.writeInt(n);
            for (int id = 0; id < world.holders.length; id++) {
                Biome b = world.holders[id].value();
                for (int i = 0; i < per; i++) {
                    int span = i % 3 == 0 ? 300 : i % 3 == 1 ? 30000 : 3000000;
                    int x = r.nextInt(span * 2) - span;
                    int z = r.nextInt(span * 2) - span;
                    int y = i % 4 == 0 ? 60 + r.nextInt(30) : -64 + r.nextInt(384);
                    BlockPos pos = new BlockPos(x, y, z);
                    w.writeInt(id);
                    w.writeInt(x);
                    w.writeInt(y);
                    w.writeInt(z);
                    w.writeFloat((Float) temp.invoke(b, pos));
                    w.writeBoolean(b.coldEnoughToSnow(pos));
                }
            }
        }
        System.out.println(n + " samples");
    }
}
