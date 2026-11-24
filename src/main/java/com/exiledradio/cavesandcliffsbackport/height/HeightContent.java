package com.exiledradio.cavesandcliffsbackport.height;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.height.asm.HeightCorePlugin;
import com.exiledradio.cavesandcliffsbackport.height.asm.HeightTransformer;
import com.exiledradio.cavesandcliffsbackport.height.asm.PatchReport;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Stream H's start-up: decides once, in pre-init, whether extended height is on for this session, and registers the
 * Caves & Cliffs world type when it is.
 *
 * <p>It is on only when the config switch is on, the coremod ran, no mod that changes the same parts of the game is
 * installed, and every patch site was found. To know the last before any world exists, every patched class is
 * loaded here (which is when the transformer patches it) and the drift report is read.
 */
public final class HeightContent {

    /** Mod ids of the mods extended height refuses to run with (see {@code HeightCorePlugin.CONFLICTS}). */
    private static final String[][] CONFLICT_MODS = {
            {"cubicchunks", "Cubic Chunks"},
            {"cavesnotcliffs", "Caves Not Cliffs"},
            {"cavebiomesapi", "Cave Biomes API (Caves Not Cliffs)"},
            {"farmers_future_delight", "Farmer's Future Delight"},
    };

    /** Phosphor's and its fork Hesperus' mod id. */
    private static final String PHOSPHOR = "phosphor-lighting";

    private static volatile boolean allowed;
    @Nullable
    private static volatile String refusal = "not started";
    @Nullable
    private static WorldType worldType;

    private HeightContent() {
    }

    /** Whether extended height is on this session. */
    public static boolean allowed() {
        return allowed;
    }

    /** Why extended height is off, or null when it is on. */
    @Nullable
    public static String refusal() {
        return refusal;
    }

    /** The Caves & Cliffs world type, or null when extended height is off. */
    @Nullable
    public static WorldType worldType() {
        return worldType;
    }

    public static void preInit(boolean client) {
        String clientTest = System.getProperty(CavesAndCliffsBackport.MOD_ID + ".clienttest");
        if (client && ("height".equals(clientTest) || "startup".equals(clientTest))) {
            // A test build's client test (runClient -Pclienttest=height), reached by name: a release has no test classes.
            try {
                Class.forName("com.exiledradio.cavesandcliffsbackport.selftest.HeightClientSelfTest")
                        .getMethod("start", String.class).invoke(null, clientTest);
            } catch (ReflectiveOperationException e) {
                CavesAndCliffsBackport.LOGGER.error("CLIENT SELFTEST DONE pass=0 fail=1: the test classes are not in this build.", e);
            }
        }
        String why = decide(client);
        refusal = why;
        if (why != null) {
            allowed = false;
            if (ModConfig.height.enabled && com.exiledradio.cavesandcliffsbackport.cubic.CubicSupport.PRESENT) {
                // Cubic Chunks holds any height itself: the world type is its cubic one, and the coremod stays off.
                worldType = com.exiledradio.cavesandcliffsbackport.cubic.CubicSupport.preInit();
                if (worldType != null) {
                    return;
                }
            }
            CavesAndCliffsBackport.LOGGER.warn("Extended height (y -64 to 319) is OFF: {}. The Caves & Cliffs world type is "
                    + "not offered and every world keeps y 0-255.", why);
            return;
        }
        allowed = true;
        if (Loader.isModLoaded("alfheim") && !AlfheimHooks.wide()) {
            // Alfheim's engine fixed its key layout before this decision (it reads allowed() once); a narrow key
            // cannot hold y below 0.
            allowed = false;
            refusal = "Alfheim's lighting engine started before pre-init, with its own position key";
            CavesAndCliffsBackport.LOGGER.warn("Extended height (y -64 to 319) is OFF: {}.", refusal);
            return;
        }
        if (Loader.isModLoaded(PHOSPHOR) && !PhosphorHooks.wide()) {
            // The same for Phosphor's engine.
            allowed = false;
            refusal = "Phosphor's lighting engine started before pre-init, with its own position key";
            CavesAndCliffsBackport.LOGGER.warn("Extended height (y -64 to 319) is OFF: {}.", refusal);
            return;
        }
        if (Loader.isModLoaded(PHOSPHOR)) {
            CavesAndCliffsBackport.LOGGER.info("Extended height with Phosphor {}: its lighting covers x and z within "
                    + "16,777,216 of 0 (Phosphor alone: 33,554,432).", HeightCorePlugin.phosphorVersion());
        }
        if (Loader.isModLoaded("alfheim")) {
            CavesAndCliffsBackport.LOGGER.info("Extended height with Alfheim {}: its lighting covers x and z within "
                    + "16,777,216 of 0 (Alfheim alone: 33,554,432).", HeightCorePlugin.ALFHEIM_VERSION);
        }
        worldType = new WorldTypeCavesAndCliffs();
        CavesAndCliffsBackport.LOGGER.info("Extended height is on: {} patch sites found; world type '{}' registered.",
                PatchReport.size(), worldType.getName());
    }

    @Nullable
    private static String decide(boolean client) {
        if (!ModConfig.height.enabled) {
            return "the config switch height.enabled is off";
        }
        if (!HeightCorePlugin.active()) {
            String plugin = HeightCorePlugin.refusal();
            return plugin != null ? plugin : "the coremod did not run";
        }
        for (String[] mod : CONFLICT_MODS) {
            if (Loader.isModLoaded(mod[0])) {
                return mod[1] + " is installed (it changes the same height and lighting code)";
            }
        }
        boolean alfheim = Loader.isModLoaded("alfheim");
        if (alfheim) {
            String version = Loader.instance().getIndexedModList().get("alfheim").getVersion();
            if (!HeightCorePlugin.ALFHEIM_VERSION.equals(version)) {
                return "Alfheim " + version + " is installed (extended height is made for Alfheim "
                        + HeightCorePlugin.ALFHEIM_VERSION + " exactly)";
            }
        }
        boolean phosphor = Loader.isModLoaded(PHOSPHOR);
        if (phosphor) {
            String version = HeightCorePlugin.phosphorVersion();
            if (!HeightCorePlugin.PHOSPHOR_VERSIONS.contains(version)) {
                return "Phosphor " + version + " is installed (extended height is made for "
                        + HeightCorePlugin.PHOSPHOR_NAMES + ")";
            }
        }
        // A Backport Core without its terrain API (before 1.3.0; Forge's version check normally stops that first)
        // would crash with NoClassDefFoundError when a Caves & Cliffs world is made.
        if (HeightContent.class.getClassLoader().getResource("com/exiledradio/backportcore/terrain/TerrainSource.class") == null) {
            return "the installed Backport Core has no terrain API; install Backport Core 1.3.0 or newer";
        }
        ClassLoader loader = HeightContent.class.getClassLoader();
        for (Map.Entry<String, Boolean> target : HeightTransformer.targets(client).entrySet()) {
            String name = target.getKey();
            if (!target.getValue() && loader.getResource(name.replace('.', '/') + ".class") == null) {
                continue;
            }
            try {
                Class.forName(name, false, loader);
            } catch (Throwable t) {
                return "loading " + name + " failed: " + t;
            }
        }
        List<String> missed = PatchReport.missed();
        for (String site : missed) {
            if (site.startsWith(ValkyrieCheck.LABEL_PREFIX)) {
                return "Valkyrie " + ValkyrieCheck.version() + " is installed and its render chunk code could not be "
                        + "adjusted for y -64 to 319 (made for Valkyrie " + ValkyrieCheck.MADE_FOR + "; missed: " + missed
                        + "). Remove Valkyrie or install that version";
            }
        }
        if (!missed.isEmpty()) {
            return missed.size() + " patch site(s) not found (" + missed + "); another mod has probably changed that code";
        }
        if (alfheim) {
            // Its mixins are read when Chunk and ChunkCache load (above, at the latest); every one must have been
            // patched, or its overwrites would undo ours.
            for (String label : new String[] {"AlfheimLightingEngine", "AlfheimChunkMixin", "AlfheimChunkCacheMixin"}) {
                if (!PatchReport.classPatched(label)) {
                    return "Alfheim is installed but its " + label.substring("Alfheim".length())
                            + " was not patched (not read through the transformers)";
                }
            }
        }
        String valkyrie = ValkyrieCheck.unpatched();
        if (valkyrie != null) {
            return valkyrie;
        }
        if (phosphor) {
            // Its engine classes load above; MixinChunk is read when Chunk loads (above, at the latest). Every one
            // must have been patched, or its code would undo ours. (With Phosphor's own config switch off, Mixin never
            // reads MixinChunk, and this refuses too.)
            for (String label : new String[] {"PhosphorLightingEngine", "PhosphorLightingHooks",
                    "PhosphorLightingEngineHelpers", "PhosphorMixinChunk"}) {
                if (!PatchReport.classPatched(label)) {
                    return "Phosphor is installed but its " + label.substring("Phosphor".length())
                            + " was not patched (not read through the transformers, or Phosphor is switched off in its config)";
                }
            }
        }
        return null;
    }
}
