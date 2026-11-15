package net.minecraft.world.level.biome;

import com.mojang.datafixers.util.Pair;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Vanilla 1.21.4's overworld biome table for Caves and Cliffs Backport's NewerBiomeCheck: OverworldBiomeBuilder's
 * parameter points in order, the climate of every overworld biome, each biome's placed features per step and
 * FeatureSorter's per-step order over the biome source's biomes (first appearance in the parameter list, as
 * MultiNoiseBiomeSource's possibleBiomes). Run against the Mojang-named client jar and its libraries (Java 21).
 *
 * Arguments: out.txt, or biomes outDir side scattered [seeds...]
 */
public final class Oracle1214 {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        HolderLookup.RegistryLookup<Biome> biomeLookup = lookup.lookupOrThrow(Registries.BIOME);
        java.util.Map<PlacedFeature, String> featureNames = new java.util.IdentityHashMap<>();
        lookup.lookupOrThrow(Registries.PLACED_FEATURE).listElements()
                .forEach(r -> featureNames.put(r.value(), r.key().location().getPath()));
        NAMES = featureNames;
        List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> points = new ArrayList<>();
        new OverworldBiomeBuilder().addBiomes(points::add);
        if (args[0].equals("biomes")) {
            SAMPLER = seed -> net.minecraft.world.level.levelgen.RandomState.create(lookup,
                    net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD, seed).sampler();
            biomes(points, new java.io.File(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), seeds(args));
            return;
        }
        try (PrintWriter out = new PrintWriter(args[0], "UTF-8")) {
            out.println("version " + VERSION);
            Set<ResourceKey<Biome>> order = new LinkedHashSet<>();
            for (Pair<Climate.ParameterPoint, ResourceKey<Biome>> p : points) {
                Climate.ParameterPoint q = p.getFirst();
                out.println("leaf " + p.getSecond().location().getPath() + " " + q.temperature().min() + " " + q.temperature().max()
                        + " " + q.humidity().min() + " " + q.humidity().max() + " " + q.continentalness().min() + " "
                        + q.continentalness().max() + " " + q.erosion().min() + " " + q.erosion().max() + " " + q.depth().min()
                        + " " + q.depth().max() + " " + q.weirdness().min() + " " + q.weirdness().max() + " " + q.offset());
                order.add(p.getSecond());
            }
            List<Holder<Biome>> biomes = new ArrayList<>();
            for (ResourceKey<Biome> k : order) {
                biomes.add(biomeLookup.getOrThrow(k));
            }
            write(out, biomes, "full");
            // Every edition combination: without the mangrove swamp and deep dark (WILD), the cherry grove (CHERRY),
            // the pale garden (PALE).
            String[][] groups = {{"mangrove_swamp", "deep_dark"}, {"cherry_grove"}, {"pale_garden"}};
            String[] tags = {"wild", "cherry", "pale"};
            for (int mask = 0; mask < 8; mask++) {
                if (mask == 7) {
                    continue;
                }
                StringBuilder tag = new StringBuilder();
                List<Holder<Biome>> subset = new ArrayList<>();
                for (Holder<Biome> b : biomes) {
                    String n = b.unwrapKey().get().location().getPath();
                    boolean keep = true;
                    for (int g = 0; g < 3; g++) {
                        if ((mask & 1 << g) == 0 && java.util.Arrays.asList(groups[g]).contains(n)) {
                            keep = false;
                        }
                    }
                    if (keep) {
                        subset.add(b);
                    }
                }
                for (int g = 0; g < 3; g++) {
                    if ((mask & 1 << g) != 0) {
                        tag.append(tag.length() == 0 ? "" : "+").append(tags[g]);
                    }
                }
                write(out, subset, tag.length() == 0 ? "none" : tag.toString());
            }
            Field climate = Biome.class.getDeclaredField("climateSettings");
            climate.setAccessible(true);
            for (Holder<Biome> h : biomes) {
                Biome b = h.value();
                Object c = climate.get(b);
                java.lang.reflect.Method mod = c.getClass().getDeclaredMethod("temperatureModifier");
                mod.setAccessible(true);
                out.println("climate " + h.unwrapKey().get().location().getPath() + " " + Float.floatToIntBits(b.getBaseTemperature())
                        + " " + Float.floatToIntBits((Float) call(c, "downfall")) + " " + (b.hasPrecipitation() ? "precipitation" : "none") + " "
                        + ((Biome.TemperatureModifier) call(c, "temperatureModifier")).getName());
                List<HolderSet<PlacedFeature>> steps = b.getGenerationSettings().features();
                for (int k = 0; k < steps.size(); k++) {
                    StringBuilder sb = new StringBuilder();
                    for (Holder<PlacedFeature> f : steps.get(k)) {
                        sb.append(' ').append(NAMES.get(f.value()));
                    }
                    out.println("biome " + h.unwrapKey().get().location().getPath() + " step " + k + " :" + sb);
                }
            }
        }
        System.out.println("wrote " + args[0]);
    }

    static final String VERSION = System.getProperty("mcversion", "1.21.4");

    static java.util.Map<PlacedFeature, String> NAMES;

    static Object call(Object o, String name) throws Exception {
        java.lang.reflect.Method m = o.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(o);
    }

    static void write(PrintWriter out, List<Holder<Biome>> biomes, String tag) {
        List<FeatureSorter.StepFeatureData> steps = FeatureSorter.buildFeaturesPerStep(biomes,
                b -> b.value().getGenerationSettings().features(), true);
        for (int k = 0; k < steps.size(); k++) {
            StringBuilder sb = new StringBuilder();
            for (PlacedFeature f : steps.get(k).features()) {
                sb.append(' ').append(NAMES.get(f));
            }
            out.println("step " + tag + " " + k + " :" + sb);
        }
    }

    /**
     * Biome maps for NewerBiomeCheck, gzipped, biomes_SEED.bin.gz: int leafCount, int nameCount, nameCount UTF biome
     * names (the bytes below are indexes into them); int chunkCount, then per chunk int cx, int cz and 1536 bytes,
     * index (qx * 4 + qz) * 96 + qy + 16 (quart y -16..79): side x side chunks around 0,0, side x side around a far
     * point, then scattered ones. Each quart is a cold search (RTree.lastResult cleared first), as VanillaBiomeDump.
     */
    static void biomes(List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> points, java.io.File out, int side,
            int scattered, long[] seeds) throws Exception {
        out.mkdirs();
        List<Pair<Climate.ParameterPoint, String>> named = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Pair<Climate.ParameterPoint, ResourceKey<Biome>> p : points) {
            String n = p.getSecond().location().getPath();
            named.add(Pair.of(p.getFirst(), n));
            if (!names.contains(n)) {
                names.add(n);
            }
        }
        Climate.ParameterList<String> list = new Climate.ParameterList<>(named);
        Field indexField = Climate.ParameterList.class.getDeclaredField("index");
        indexField.setAccessible(true);
        Object index = indexField.get(list);
        Field lastField = index.getClass().getDeclaredField("lastResult");
        lastField.setAccessible(true);
        ThreadLocal<?> last = (ThreadLocal<?>) lastField.get(index);
        byte[] chunk = new byte[1536];
        for (long seed : seeds) {
            Climate.Sampler sampler = SAMPLER.apply(seed);
            java.util.Random rnd = new java.util.Random(seed * 131L + 7L);
            int farX = rnd.nextInt(40000) - 20000;
            int farZ = rnd.nextInt(40000) - 20000;
            // -Dareas=seed:cx,cz;seed:cx,cz...: the second area centered on that chunk (a pale garden) instead.
            for (String a : System.getProperty("areas", "").split(";")) {
                String[] kv = a.split(":");
                if (kv.length == 2 && Long.parseLong(kv[0]) == seed) {
                    String[] xz = kv[1].split(",");
                    farX = Integer.parseInt(xz[0]) - side / 2;
                    farZ = Integer.parseInt(xz[1]) - side / 2;
                }
            }
            int count = side * side * 2 + scattered;
            try (java.io.DataOutputStream w = new java.io.DataOutputStream(new java.io.BufferedOutputStream(
                    new java.util.zip.GZIPOutputStream(new java.io.FileOutputStream(new java.io.File(out,
                    "biomes_" + seed + ".bin.gz")), 1 << 16), 1 << 16))) {
                w.writeInt(points.size());
                w.writeInt(names.size());
                for (String n : names) {
                    w.writeUTF(n);
                }
                w.writeInt(count);
                for (int i = 0; i < count; i++) {
                    int cx;
                    int cz;
                    if (i < side * side) {
                        cx = i / side - side / 2;
                        cz = i % side - side / 2;
                    } else if (i < side * side * 2) {
                        int j = i - side * side;
                        cx = farX + j / side;
                        cz = farZ + j % side;
                    } else {
                        int span = i % 3 == 0 ? 2000 : i % 3 == 1 ? 60000 : 1870000;
                        cx = rnd.nextInt(span * 2) - span;
                        cz = rnd.nextInt(span * 2) - span;
                    }
                    for (int qx = 0; qx < 4; qx++) {
                        for (int qz = 0; qz < 4; qz++) {
                            for (int qy = -16; qy < 80; qy++) {
                                last.remove();
                                String b = list.findValue(sampler.sample(cx * 4 + qx, qy, cz * 4 + qz));
                                chunk[(qx * 4 + qz) * 96 + qy + 16] = (byte) names.indexOf(b);
                            }
                        }
                    }
                    w.writeInt(cx);
                    w.writeInt(cz);
                    w.write(chunk);
                }
            }
            System.out.println("seed " + seed + ": " + count + " chunks");
        }
    }

    static java.util.function.LongFunction<Climate.Sampler> SAMPLER;

    static long[] seeds(String[] args) {
        if (args.length <= 4) {
            return new long[]{42L, 12345L, -7346534567L, 8675309L};
        }
        long[] s = new long[args.length - 4];
        for (int i = 4; i < args.length; i++) {
            s[i - 4] = Long.parseLong(args[i]);
        }
        return s;
    }
}
