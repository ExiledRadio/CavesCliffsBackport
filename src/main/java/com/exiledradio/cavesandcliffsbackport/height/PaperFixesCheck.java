package com.exiledradio.cavesandcliffsbackport.height;

import com.exiledradio.cavesandcliffsbackport.height.asm.PatchReport;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import javax.annotation.Nullable;

/**
 * PaperFixes (Elephant1214): its {@code pathingChunkCache} and {@code pathNodeCache} options overwrite ChunkCache and
 * WalkNodeProcessor code with y 0-255 copies ({@code height.asm.PaperFixesPatches}). Mixin reads those mixin classes
 * through the transformers when ChunkCache and WalkNodeProcessor load; with an option on, its class must have been
 * patched, or nothing below y 0 is drawn and mobs see air there.
 */
final class PaperFixesCheck {

    static final String LABEL_PREFIX = "PaperFixes";
    static final String MADE_FOR = "2.0.1";
    static final String WORKAROUND = "or set B:pathingChunkCache=false and B:pathNodeCache=false in config/paperfixes.cfg";

    private PaperFixesCheck() {
    }

    static String version() {
        ModContainer mod = Loader.instance().getIndexedModList().get("paperfixes");
        return mod == null ? "?" : mod.getVersion();
    }

    /** The refusal for a missed PaperFixes site. */
    static String missed(java.util.List<String> missed) {
        return "PaperFixes " + version() + " is installed and its pathfinding caches could not be adjusted for y -64 to 319 "
                + "(made for PaperFixes " + MADE_FOR + "; missed: " + missed + "). Install that version, " + WORKAROUND;
    }

    /** Why extended height cannot run with the installed PaperFixes, or null (not installed, options off, or patched). */
    @Nullable
    static String unpatched(ClassLoader loader) {
        if (!Loader.isModLoaded("paperfixes")) {
            return null;
        }
        if (option("pathingChunkCache") && !PatchReport.classPatched("PaperFixesPathingChunkCache")) {
            return unread("pathing_chunk_cache.ChunkCacheMixin");
        }
        if (option("pathNodeCache")) {
            try {
                // Its WalkNodeProcessor mixin is read when the class loads; load it now to check.
                Class.forName("net.minecraft.pathfinding.WalkNodeProcessor", false, loader);
            } catch (Throwable t) {
                return "loading WalkNodeProcessor failed: " + t;
            }
            if (!PatchReport.classPatched("PaperFixesWalkNodeProcessor")) {
                java.util.List<String> missed = PatchReport.missed();
                return missed.isEmpty() ? unread("path_node_cache.WalkNodeProcessorMixin") : missed(missed);
            }
        }
        return null;
    }

    private static String unread(String mixin) {
        return "PaperFixes " + version() + " is installed but its " + mixin + " was not patched (not read through the "
                + "transformers); nothing below y 0 would be drawn or walked on. Remove PaperFixes, " + WORKAROUND;
    }

    /** One of PaperFixes' performance options (true if it cannot be read: then its mixin applies by default). */
    private static boolean option(String name) {
        try {
            Class<?> config = Class.forName("me.elephant1214.paperfixes.configuration.PaperFixesConfig");
            Object performance = config.getField("performance").get(null);
            return performance.getClass().getField(name).getBoolean(performance);
        } catch (Throwable t) {
            return true;
        }
    }
}
