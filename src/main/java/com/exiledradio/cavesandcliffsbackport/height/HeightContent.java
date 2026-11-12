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
            {"alfheim", "Alfheim"},
            {"phosphor-lighting", "Phosphor"},
            {"cubicchunks", "Cubic Chunks"},
            {"cavesnotcliffs", "Caves Not Cliffs"},
            {"cavebiomesapi", "Cave Biomes API (Caves Not Cliffs)"},
            {"farmers_future_delight", "Farmer's Future Delight"},
    };

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
        String why = decide(client);
        refusal = why;
        if (why != null) {
            allowed = false;
            CavesAndCliffsBackport.LOGGER.warn("Extended height (y -64 to 319) is OFF: {}. The Caves & Cliffs world type is "
                    + "not offered and every world keeps y 0-255.", why);
            return;
        }
        worldType = new WorldTypeCavesAndCliffs();
        allowed = true;
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
        if (!missed.isEmpty()) {
            return missed.size() + " patch site(s) not found (" + missed + "); another mod has probably changed that code";
        }
        return null;
    }
}
