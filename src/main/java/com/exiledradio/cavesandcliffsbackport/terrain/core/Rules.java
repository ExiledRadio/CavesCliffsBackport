package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * Which version's code the 1.18.2 features run (Decorator.rules): the newest one a family backport installed brings.
 * The features and their order stay the world's (Editions: 1.18.2's, or 1.19.2's and 1.20.1's with the newer
 * biomes); what changes is how the newer versions place the same features. The changes, by version (Mojang's code
 * compared release to release; checked against vanilla dumps, tools/terrain/oracle-newer):
 * <ul>
 *   <li>{@link #V119} (1.19.2, Wild Update Backport): the disks test for source water in their placement and only
 *       disk_sand turns to sandstone, over the air block (1.18's DiskReplaceFeature did it for any falling block, over
 *       cave air too); ice_patch looks one block below the heightmap, filtering on the snow block and then the biome
 *       there; trees grow through seagrass; leaves grown into source water are waterlogged; glow lichen spreads in a
 *       fresh shuffle of the directions (1.18.2 shuffled one process-global array).</li>
 *   <li>{@link #V120} (1.20.1, Trails &amp; Tales Backport): TreeFeature.updateLeaves keeps to the tree's box and can
 *       leave a leaf one further than its distance (Trees.updateLeaves120); geodes take an exact inverse square
 *       root.</li>
 *   <li>{@link #V121} (1.21.11, Tricky Trials Backport): bee nests shuffle with the tree's random, huge mushrooms
 *       replace only #replaceable_by_mushrooms, dark oak branches put their leaves on the branch, Mth.sin and cos of
 *       a double, trees grow through small flowers, trees_mangrove without its survival filter, underwater magma
 *       only between fully occluding faces, disks mark only the top of a run for post-processing. Not checked against
 *       vanilla (docs/streams/generator.md, Not done).</li>
 * </ul>
 * Everything else between the versions (codecs, RandomSource, Material's removal, the shuffles' helpers) places the
 * same blocks for the blocks the core has.
 */
public final class Rules {
    private Rules() {
    }

    public static final int V118 = 118;
    public static final int V119 = 119;
    public static final int V120 = 120;
    public static final int V121 = 121;

    /** The Minecraft version whose code the rules are: "1.18.2", "1.19.2", "1.20.1" or "1.21.11". */
    public static String name(int rules) {
        switch (rules) {
            case V119:
                return "1.19.2";
            case V120:
                return "1.20.1";
            case V121:
                return "1.21.11";
            default:
                return "1.18.2";
        }
    }
}
