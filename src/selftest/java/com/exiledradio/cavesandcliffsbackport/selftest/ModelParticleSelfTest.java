package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Every block and item model of the mod has a particle texture that 1.12 can resolve - the sprite of break and
 * hit particles (FancyBlockParticles' too). Two ways to lose it, both drawn as the missing texture:
 * <ul>
 *   <li>a {@code #name} reference in a model's own texture map to a name the same file defines (1.12's
 *       {@code ModelBlock} calls that an upward reference and gives {@code missingno}; vanilla only refers to
 *       names a child defines), and</li>
 *   <li>a model whose chain ends in a vanilla parent that sets no particle ({@code block/block},
 *       {@code block/cube}, {@code block/thin_block}, {@code block/carpet}, ...) without a particle of its own.</li>
 * </ul>
 * The full check, of every state's baked particle sprite, is the client test {@code -Pclienttest=particles}.
 *
 * <pre>    ./gradlew runServer -Pselftest=models</pre>
 */
public final class ModelParticleSelfTest implements SelfTest {

    private static final String PREFIX = "assets/" + CavesAndCliffsBackport.MOD_ID + "/models/";

    /** Vanilla 1.12 parents that never set "particle" (each vanilla child model sets it). */
    private static final String[] NO_PARTICLE = {"block/anvil", "block/block", "block/carpet", "block/cube",
            "block/cube_directional", "block/cube_mirrored", "block/farmland", "block/glazed_terracotta", "block/grass",
            "block/thin_block"};

    @Override
    public String name() {
        return "models";
    }

    @Override
    public void run(Check c) throws Exception {
        Map<String, JsonObject> models = new HashMap<String, JsonObject>();
        File source = Loader.instance().getIndexedModList().get(CavesAndCliffsBackport.MOD_ID).getSource();
        if (source.isDirectory()) {
            Path root = source.toPath();
            try (Stream<Path> walk = Files.walk(root.resolve(PREFIX))) {
                Iterator<Path> it = walk.iterator();
                while (it.hasNext()) {
                    Path p = it.next();
                    String rel = root.relativize(p).toString().replace(File.separatorChar, '/');
                    if (rel.endsWith(".json")) {
                        try (InputStream in = Files.newInputStream(p)) {
                            models.put(rel.substring(PREFIX.length(), rel.length() - 5), parse(in));
                        }
                    }
                }
            }
        } else {
            try (ZipFile zip = new ZipFile(source)) {
                for (ZipEntry e : java.util.Collections.list(zip.entries())) {
                    String n = e.getName();
                    if (n.startsWith(PREFIX) && n.endsWith(".json")) {
                        try (InputStream in = zip.getInputStream(e)) {
                            models.put(n.substring(PREFIX.length(), n.length() - 5), parse(in));
                        }
                    }
                }
            }
        }
        c.ok(models.size() > 300, "model files read: " + models.size());
        // Templates (parents of our other models) need no particle: their children set it.
        java.util.Set<String> parents = new java.util.HashSet<String>();
        for (JsonObject m : models.values()) {
            if (m.has("parent") && m.get("parent").getAsString().startsWith(CavesAndCliffsBackport.MOD_ID + ":")) {
                parents.add(m.get("parent").getAsString().substring(CavesAndCliffsBackport.MOD_ID.length() + 1));
            }
        }
        int bad = 0;
        for (Entry<String, JsonObject> e : models.entrySet()) {
            JsonObject textures = e.getValue().has("textures") ? e.getValue().getAsJsonObject("textures") : new JsonObject();
            for (Entry<String, JsonElement> t : textures.entrySet()) {
                String v = t.getValue().getAsString();
                if (v.startsWith("#") && textures.has(v.substring(1))) {
                    bad++;
                    c.fail(e.getKey() + ": \"" + t.getKey() + "\": \"" + v + "\" refers to its own file (1.12 resolves it to missingno)");
                }
            }
            String vanillaEnd = null;
            boolean particle = false;
            JsonObject m = e.getValue();
            for (int depth = 0; m != null && depth < 16; depth++) {
                if (m.has("textures") && m.getAsJsonObject("textures").has("particle")) {
                    particle = true;
                    break;
                }
                if (!m.has("parent")) {
                    break;
                }
                String parent = m.get("parent").getAsString();
                String ours = parent.startsWith(CavesAndCliffsBackport.MOD_ID + ":") ? parent.substring(parent.indexOf(':') + 1) : null;
                if (ours == null) {
                    vanillaEnd = parent.startsWith("minecraft:") ? parent.substring(10) : parent;
                    break;
                }
                m = models.get(ours);
            }
            if (!particle && vanillaEnd != null && !parents.contains(e.getKey()) && java.util.Arrays.asList(NO_PARTICLE).contains(vanillaEnd)) {
                bad++;
                c.fail(e.getKey() + ": no particle texture (its parent " + vanillaEnd + " sets none), so break particles are the missing texture");
            }
        }
        c.ok(bad == 0, "every model's particle texture resolves: " + bad + " bad");
    }

    private static JsonObject parse(InputStream in) {
        return new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
