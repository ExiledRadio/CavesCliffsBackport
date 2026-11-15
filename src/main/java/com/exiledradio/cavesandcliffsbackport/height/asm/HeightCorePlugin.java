package com.exiledradio.cavesandcliffsbackport.height.asm;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * The extended-height coremod (stream H, {@code docs/EXTENDED_HEIGHT.md}): our own ASM, one transformer, one small
 * patch class per target class, no mixins. Named in the jar's manifest ({@code FMLCorePlugin} with
 * {@code FMLCorePluginContainsFMLMod}, so the mod stays one jar) and, in the dev environment, by
 * {@code -Dfml.coreMods.load} ({@code build.gradle}).
 *
 * <p>It patches nothing when the config switch {@code height.enabled} is off or a mod that changes the same parts of
 * the game is installed (checked again, with a log line, in {@code height.HeightContent}). Sorting index 1500: after
 * Fluidlogged API's transformers (1401), so they see vanilla's code and ours sees theirs.
 */
@IFMLLoadingPlugin.Name("Caves & Cliffs Backport Height")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions("com.exiledradio.cavesandcliffsbackport.height.asm.")
@IFMLLoadingPlugin.SortingIndex(1500)
public final class HeightCorePlugin implements IFMLLoadingPlugin {

    public static final Logger LOG = LogManager.getLogger("Caves & Cliffs Backport Height");

    /**
     * Mods whose own height or lighting code covers what ours patches. Each is named by a class only it has, so the
     * check works before any mod is loaded.
     */
    public static final String[][] CONFLICTS = {
            {"Cubic Chunks", "io/github/opencubicchunks/cubicchunks/core/asm/coremod/CubicChunksCoreMod.class"},
            {"Caves Not Cliffs", "net/celestiald/cavesnotcliffs/core/CavesNotCliffsCorePlugin.class"},
            {"Cave Biomes API (Caves Not Cliffs)", "net/celestiald/cavebiomes/core/CaveBiomesLoadingPlugin.class"},
            {"Farmer's Future Delight", "xy177/farmersfuturedelight/core/FFDLoadingPlugin.class"},
    };

    /** Whether the transformer patches anything this session. */
    static volatile boolean active;
    /** Why not, when it does not: the config switch, or the conflicting mod's name. */
    private static volatile String refusal;
    static volatile boolean client;

    /** Null when the coremod is on; else why it is off. */
    @Nullable
    public static String refusal() {
        return refusal;
    }

    public static boolean active() {
        return active;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {HeightTransformer.class.getName()};
    }

    @Nullable
    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        Object deobf = data.get("runtimeDeobfuscationEnabled");
        Asm.srg = !(deobf instanceof Boolean) || (Boolean) deobf;
        client = FMLLaunchHandler.side().isClient();

        String why = null;
        Object home = data.get("mcLocation");
        if (home instanceof File && !configEnabled(new File((File) home, "config/cavesandcliffsbackport.cfg"))) {
            why = "the config switch height.enabled is off";
        }
        if (why == null) {
            for (String[] conflict : CONFLICTS) {
                if (Launch.classLoader != null && Launch.classLoader.getResource(conflict[1]) != null) {
                    why = conflict[0] + " is installed";
                    break;
                }
            }
        }
        if (why == null && Launch.classLoader != null) {
            String alfheim = alfheimVersion();
            if (alfheim != null && !ALFHEIM_VERSION.equals(alfheim)) {
                why = "Alfheim " + alfheim + " is installed (extended height is made for Alfheim " + ALFHEIM_VERSION
                        + " exactly; its lighting code is patched site by site)";
            }
        }
        if (why == null && Launch.classLoader != null) {
            String phosphor = phosphorVersion();
            if (phosphor != null && !PHOSPHOR_VERSIONS.contains(phosphor)) {
                why = "Phosphor " + phosphor + " is installed (extended height is made for " + PHOSPHOR_NAMES
                        + "; their lighting code is patched site by site)";
            }
        }
        refusal = why;
        active = why == null;
        if (active) {
            LOG.info("Extended height: coremod on ({} names, {} side).", Asm.srg ? "SRG" : "MCP", client ? "client" : "server");
        } else {
            LOG.warn("Extended height is OFF for this session: {}. Every world keeps y 0-255.", why);
        }
    }

    /** The one Alfheim version the {@code AlfheimPatches} are written for. */
    public static final String ALFHEIM_VERSION = "1.6";

    /**
     * The installed Alfheim's version (its {@code ProjectConstants.VERSION}, read from the class file: no mod is
     * loaded yet), "unknown" when that cannot be read, or null when Alfheim is not installed.
     */
    @Nullable
    static String alfheimVersion() {
        if (Launch.classLoader.getResource("dev/redstudio/alfheim/asm/AlfheimPlugin.class") == null) {
            return null;
        }
        try (java.io.InputStream in = Launch.classLoader.getResourceAsStream("dev/redstudio/alfheim/ProjectConstants.class")) {
            if (in != null) {
                org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
                new org.objectweb.asm.ClassReader(in).accept(node, org.objectweb.asm.ClassReader.SKIP_CODE);
                for (org.objectweb.asm.tree.FieldNode f : node.fields) {
                    if ("VERSION".equals(f.name) && f.value instanceof String) {
                        return (String) f.value;
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Extended height: could not read Alfheim's version.", e);
        }
        return "unknown";
    }

    /**
     * The Phosphor builds the {@code PhosphorPatches} are written for, by {@code PhosphorConstants.MOD_VERSION}:
     * jellysquid's Phosphor 0.2.6 and 0.2.7, and its fork Hesperus 0.2.9.1-0.2.9.3 (all mod id
     * {@code phosphor-lighting}; their height and lighting code is the same bytecode).
     */
    public static final java.util.Set<String> PHOSPHOR_VERSIONS = java.util.Collections.unmodifiableSet(
            new java.util.HashSet<String>(java.util.Arrays.asList("1.12.2-0.2.6", "1.12.2-0.2.7", "1.12.2-0.2.9.1",
                    "1.12.2-0.2.9.2", "1.12.2-0.2.9.3")));
    public static final String PHOSPHOR_NAMES = "Phosphor 0.2.6 and 0.2.7 and Hesperus 0.2.9.1 to 0.2.9.3";

    /**
     * The installed Phosphor's (or Hesperus') version (its {@code PhosphorConstants.MOD_VERSION}, read from the class
     * file), "unknown" when that cannot be read, or null when no Phosphor is installed.
     */
    @Nullable
    public static String phosphorVersion() {
        if (Launch.classLoader.getResource("me/jellysquid/mods/phosphor/mod/world/lighting/LightingEngine.class") == null) {
            return null;
        }
        String v = constant("me/jellysquid/mods/phosphor/mod/PhosphorConstants.class", "MOD_VERSION");
        return v != null ? v : "unknown";
    }

    /** A {@code String} constant of a class file, read without loading the class; null when there is none. */
    @Nullable
    private static String constant(String resource, String field) {
        try (java.io.InputStream in = Launch.classLoader.getResourceAsStream(resource)) {
            if (in != null) {
                org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
                new org.objectweb.asm.ClassReader(in).accept(node, org.objectweb.asm.ClassReader.SKIP_CODE);
                for (org.objectweb.asm.tree.FieldNode f : node.fields) {
                    if (field.equals(f.name) && f.value instanceof String) {
                        return (String) f.value;
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Extended height: could not read {}.", resource, e);
        }
        return null;
    }

    /** The config file's {@code height { B:enabled=... }}, read as text (Forge's config is not loaded yet). */
    static boolean configEnabled(File file) {
        if (!file.isFile()) {
            return true;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            boolean inHeight = false;
            for (String raw : lines) {
                String line = raw.trim();
                if (line.startsWith("height {")) {
                    inHeight = true;
                } else if (inHeight && line.startsWith("}")) {
                    inHeight = false;
                } else if (inHeight && line.startsWith("B:enabled=")) {
                    return !"false".equalsIgnoreCase(line.substring("B:enabled=".length()).trim());
                }
            }
        } catch (Exception e) {
            LOG.warn("Extended height: could not read {}; taking height.enabled as on.", file, e);
        }
        return true;
    }

    @Nullable
    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
