package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * Which newer vanilla versions' overworld additions a world's 1.18 terrain has: flags, 0 for exactly 1.18.2.
 *
 * <p>The terrain shape is the same in 1.18.2, 1.19 and 1.20 (one exception below); what they add is biomes, and
 * the biomes' features take their places in FeatureSorter's per-step order. Each addition comes with the backport
 * that has its blocks, so a world gets it only when that backport is installed when the world is made:
 * <ul>
 *   <li>{@link #WILD}: 1.19's mangrove swamp and deep dark (Wild Update Backport): OverworldBiomeBuilder's 1.19
 *       lines, the 1.19 surface rules (mud, the mangrove swamp's water), and 1.19's aquifers (none in the deep dark's
 *       region; a level at or below y -10 that comes from the surface gets lava the way a deep one does).</li>
 *   <li>{@link #CHERRY}: 1.20's cherry grove (Trails &amp; Tales Backport), in PLATEAU_BIOMES_VARIANT. The aquifers
 *       are 1.20's (the same as 1.19's).</li>
 *   <li>{@link #PALE}: 1.21.4's pale garden (Tricky Trials Backport, with its pale garden biome registered), in
 *       PLATEAU_BIOMES_VARIANT where the dark forest was. Its feature tables are 1.21.4's for every biome (which
 *       drop seagrass_simple and list ore_diamond_medium), the aquifers mark fluids for updates by 1.21.4's rule
 *       (ChunkFiller.Aquifer#flagged1214), and the RTree and aquifer sampling are 1.20's.</li>
 * </ul>
 * With WILD and CHERRY, the tables are 1.20.1's; with WILD alone 1.19.2's; with CHERRY alone 1.20.1's without the
 * mangrove swamp's and the deep dark's features (and 1.18.2's swamp line in the biome builder). With PALE they are
 * 1.21.4's over the biomes the editions have. Oracles: tools/terrain/oracle-newer.
 */
public final class Editions {
    private Editions() {
    }

    public static final int NONE = 0;
    public static final int WILD = 1;
    public static final int CHERRY = 2;
    public static final int PALE = 4;

    /** 1.19's aquifers (and 1.20's): any newer edition. */
    public static boolean newerAquifers(int editions) {
        return editions != NONE;
    }

    /** 1.20's RTree (6 children per node) and aquifer sampling order: CHERRY or anything newer. */
    public static boolean from120(int editions) {
        return (editions & (CHERRY | PALE)) != 0;
    }

    /**
     * The world's saved name for the editions: "1.18", "1.19" (WILD), "1.20" (both), "1.20-cherry" (CHERRY); with the
     * pale garden "1.21.4" (all three), else "1.21.4-" and the others it has, "1.21.4-wild-pale", "1.21.4-cherry-pale",
     * "1.21.4-pale".
     */
    public static String name(int editions) {
        if ((editions & PALE) != 0) {
            if (editions == (WILD | CHERRY | PALE)) {
                return "1.21.4";
            }
            return "1.21.4" + ((editions & WILD) != 0 ? "-wild" : "") + ((editions & CHERRY) != 0 ? "-cherry" : "") + "-pale";
        }
        switch (editions) {
            case WILD:
                return "1.19";
            case WILD | CHERRY:
                return "1.20";
            case CHERRY:
                return "1.20-cherry";
            default:
                return "1.18";
        }
    }

    /** The editions of a saved name, or -1 for one this version does not know. */
    public static int byName(String name) {
        switch (name) {
            case "1.18":
                return NONE;
            case "1.19":
                return WILD;
            case "1.20":
                return WILD | CHERRY;
            case "1.20-cherry":
                return CHERRY;
            case "1.21.4":
                return WILD | CHERRY | PALE;
            case "1.21.4-wild-pale":
                return WILD | PALE;
            case "1.21.4-cherry-pale":
                return CHERRY | PALE;
            case "1.21.4-pale":
                return PALE;
            default:
                return -1;
        }
    }
}
