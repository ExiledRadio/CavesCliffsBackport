package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.*;

/**
 * Trees as 1.18.2 places them (TreeFeature, its trunk and foliage placers, feature sizes and decorators; the tree
 * placements of VegetationPlacements / TreePlacements): every overworld tree. Straight, fancy, forking, dark oak,
 * giant, mega jungle and bending trunks; blob, spruce, pine, fancy, acacia, dark oak, mega jungle, mega pine, bush
 * and random spread foliage; two- and three-layer feature sizes; the beehive, cocoa, trunk vine, leave vine and
 * alter ground decorators; huge red and brown mushrooms (dark forests, mushroom fields) and the azalea tree's root
 * system (rooted_azalea_tree).
 *
 * <p>TreeFeature keeps three HashSet&lt;BlockPos&gt; (trunk, foliage, decorations) and the decorators read the trunk
 * and foliage sets as lists sorted by y, ties in HashSet order: LushCaves.Pos hashes as Vec3i does, so a
 * java.util.HashSet filled in vanilla's order iterates in vanilla's order. After the decorators, updateLeaves gives
 * leaves near the trunk their distance (BFS 1..6, the rest stay 7) and StructureTemplate.updateShapeAtEdge lets the
 * blocks on both sides of the changed shape's faces update their shape (snowy grass under new leaves loses its snow).
 *
 * <p>BeehiveDecorator shuffles its candidate positions with Collections.shuffle(list) without the feature's random,
 * so vanilla's hive position is not reproducible; this picks with a java.util.Random seeded from the position. Every
 * draw from the feature's random (the chance, the bees) is vanilla's.
 */
final class Trees {
    private Trees() {
    }

    static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
    static final int OAK = 0;
    static final int SPRUCE = 1;
    static final int BIRCH = 2;
    static final int JUNGLE = 3;
    static final int ACACIA = 4;
    static final int DARK_OAK = 5;
    /** log[wood][axis x, y, z]. */
    static final int[][] LOG = new int[WOODS.length][3];
    /** The leaves: the woods, then azalea and flowering azalea. */
    static final String[] LEAF_TYPES = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "azalea", "flowering_azalea"};
    static final int AZALEA_LEAVES = 6;
    static final int FLOWERING_AZALEA_LEAVES = 7;
    /** leaves[type][distance 1..7] (persistent=false). */
    static final int[][] LEAVES = new int[LEAF_TYPES.length][8];
    static final int BEE_NEST = register("bee_nest[facing=south,honey_level=0]");
    static final int ROOTED_DIRT = register("rooted_dirt");
    static final int HANGING_ROOTS = register("hanging_roots[waterlogged=false]");
    /** cocoa[age][facing as Direction ordinal 2..5]. */
    static final int[][] COCOA = new int[3][6];
    /** Huge mushroom blocks by face mask (bit = Direction ordinal: down, up, north, south, west, east). */
    static final int[] BROWN_MUSHROOM_BLOCK = new int[64];
    static final int[] RED_MUSHROOM_BLOCK = new int[64];
    static final int MUSHROOM_STEM;
    /** Per code: the leaf distance (1..7) or 0; and the code with another distance. */
    private static int[] leafDistance;
    private static int[] leafWood;

    private static String faces(int m) {
        return "down=" + (m & 1) + ",east=" + (m >> 5 & 1) + ",north=" + (m >> 2 & 1) + ",south=" + (m >> 3 & 1) + ",up="
            + (m >> 1 & 1) + ",west=" + (m >> 4 & 1);
    }

    static {
        String[] axes = {"x", "y", "z"};
        for (int w = 0; w < WOODS.length; w++) {
            for (int a = 0; a < 3; a++) {
                LOG[w][a] = register(WOODS[w] + "_log[axis=" + axes[a] + "]");
            }
        }
        for (int w = 0; w < LEAF_TYPES.length; w++) {
            for (int d = 1; d <= 7; d++) {
                LEAVES[w][d] = register(LEAF_TYPES[w] + "_leaves[distance=" + d + ",persistent=false]");
            }
        }
        String[] facings = {null, null, "north", "south", "west", "east"};
        for (int a = 0; a < 3; a++) {
            for (int f = 2; f < 6; f++) {
                COCOA[a][f] = register("cocoa[age=" + a + ",facing=" + facings[f] + "]");
            }
        }
        // Caps never have down; brown caps always up.
        for (int m = 0; m < 64; m++) {
            if ((m & 1) == 0) {
                String s = faces(m).replace("=1", "=true").replace("=0", "=false");
                if ((m & 2) != 0) {
                    BROWN_MUSHROOM_BLOCK[m] = register("brown_mushroom_block[" + s + "]");
                }
                RED_MUSHROOM_BLOCK[m] = register("red_mushroom_block[" + s + "]");
            }
        }
        MUSHROOM_STEM = register("mushroom_stem[down=false,east=true,north=true,south=true,up=false,west=true]");
        leafDistance = new int[count()];
        leafWood = new int[count()];
        for (int w = 0; w < LEAF_TYPES.length; w++) {
            for (int d = 1; d <= 7; d++) {
                leafDistance[LEAVES[w][d]] = d;
                leafWood[LEAVES[w][d]] = w;
            }
        }
    }

    // ---- placements ----

    static List<Placed> placed() {
        Tree oak = new Tree(LOG[OAK][1], LEAVES[OAK][7], true, -1.0F, STRAIGHT, 4, 2, 0, BLOB, c(2), c(0), 3, null, 1, 0, 1);
        Tree birchBees0002 = new Tree(LOG[BIRCH][1], LEAVES[BIRCH][7], true, 0.002F, STRAIGHT, 5, 2, 0, BLOB, c(2), c(0), 3, null, 1, 0, 1);
        Tree superBirchBees0002 = new Tree(LOG[BIRCH][1], LEAVES[BIRCH][7], true, 0.002F, STRAIGHT, 5, 2, 6, BLOB, c(2), c(0), 3, null, 1, 0, 1);
        Tree spruce = new Tree(LOG[SPRUCE][1], LEAVES[SPRUCE][7], true, -1.0F, STRAIGHT, 5, 2, 1, SPRUCE_FOLIAGE, u(2, 3), u(0, 2), 0, u(1, 2), 2, 0, 2);
        Tree pine = new Tree(LOG[SPRUCE][1], LEAVES[SPRUCE][7], true, -1.0F, STRAIGHT, 6, 4, 0, PINE_FOLIAGE, c(1), c(1), 0, u(3, 4), 2, 0, 2);
        Feature spruceChecked = new Checked(spruce);
        Feature pineChecked = new Checked(pine);
        Feature birchBees0002Checked = new Checked(birchBees0002);
        Feature superBirchBees0002Checked = new Checked(superBirchBees0002);
        Tree oakBees005 = oakBees(0.05F);
        Tree oakBees002 = oakBees(0.02F);
        Tree birchBees002 = new Tree(LOG[BIRCH][1], LEAVES[BIRCH][7], true, 0.02F, STRAIGHT, 5, 2, 0, BLOB, c(2), c(0), 3, null, 1, 0, 1);
        Tree superBirchBees = new Tree(LOG[BIRCH][1], LEAVES[BIRCH][7], true, 1.0F, STRAIGHT, 5, 2, 6, BLOB, c(2), c(0), 3, null, 1, 0, 1);
        Tree fancyOak = fancyOak(-1.0F);
        Feature oakChecked = new Checked(oak);
        Feature fancyOakChecked = new Checked(fancyOak);
        Tree acacia = new Tree(LOG[ACACIA][1], LEAVES[ACACIA][7], true, -1.0F, FORKING, 5, 2, 2, ACACIA_FOLIAGE, c(2), c(0), 0, null, 1, 0, 2);
        Feature savanna = new Selector(new float[]{0.8F}, new Feature[]{new Checked(acacia)}, oakChecked);
        Modifier sapling = (level, random, next, x, y, z) -> {
            if (saplingSurvives(level, x, y, z)) {
                next.at(x, y, z);
            }
        };
        int of = Region.OCEAN_FLOOR;
        Tree darkOak = new Tree(LOG[DARK_OAK][1], LEAVES[DARK_OAK][7], true, -1.0F, DARK_OAK_TRUNK, 6, 2, 1, DARK_OAK_FOLIAGE, c(0), c(0), 0, null, 0, 0, 0)
            .three(1, 1, 0, 1, 2);
        Tree birch = new Tree(LOG[BIRCH][1], LEAVES[BIRCH][7], true, -1.0F, STRAIGHT, 5, 2, 0, BLOB, c(2), c(0), 3, null, 1, 0, 1);
        Tree jungle = new Tree(LOG[JUNGLE][1], LEAVES[JUNGLE][7], true, -1.0F, STRAIGHT, 4, 8, 0, BLOB, c(2), c(0), 3, null, 1, 0, 1)
            .cocoa(0.2F).decorate(D_COCOA, D_TRUNK_VINE, D_LEAVE_VINE);
        Tree megaJungle = new Tree(LOG[JUNGLE][1], LEAVES[JUNGLE][7], false, -1.0F, MEGA_JUNGLE_TRUNK, 10, 2, 19, MEGA_JUNGLE_FOLIAGE, c(2), c(0), 2, null, 1, 1, 2)
            .decorate(D_TRUNK_VINE, D_LEAVE_VINE);
        Tree megaSpruce = new Tree(LOG[SPRUCE][1], LEAVES[SPRUCE][7], false, -1.0F, GIANT, 13, 2, 14, MEGA_PINE_FOLIAGE, c(0), c(0), 0, u(13, 17), 1, 1, 2)
            .decorate(D_ALTER_GROUND);
        Tree megaPine = new Tree(LOG[SPRUCE][1], LEAVES[SPRUCE][7], false, -1.0F, GIANT, 13, 2, 14, MEGA_PINE_FOLIAGE, c(0), c(0), 0, u(3, 7), 1, 1, 2)
            .decorate(D_ALTER_GROUND);
        Tree swampOak = new Tree(LOG[OAK][1], LEAVES[OAK][7], false, -1.0F, STRAIGHT, 5, 3, 0, BLOB, c(3), c(0), 3, null, 1, 0, 1)
            .decorate(D_LEAVE_VINE);
        Tree jungleBush = new Tree(LOG[JUNGLE][1], LEAVES[OAK][7], false, -1.0F, STRAIGHT, 1, 0, 0, BUSH_FOLIAGE, c(2), c(1), 2, null, 0, 0, 0);
        Tree azalea = new Tree(LOG[OAK][1], LEAVES[AZALEA_LEAVES][7], false, -1.0F, BENDING, 4, 2, 0, RANDOM_SPREAD_FOLIAGE, c(3), c(0), 0, c(2), 1, 0, 1)
            .azalea();
        Feature jungleChecked = new Checked(jungle);
        Feature jungleBushChecked = new Checked(jungleBush);
        Feature megaJungleChecked = new Checked(megaJungle);
        Feature megaSpruceChecked = new Checked(megaSpruce);
        Feature hugeBrown = inline(new HugeMushroom(false));
        Feature hugeRed = inline(new HugeMushroom(true));
        return Arrays.asList(
            new Placed("dark_forest_vegetation", new Selector(new float[]{0.025F, 0.05F, 0.6666667F, 0.2F, 0.1F},
                new Feature[]{hugeBrown, hugeRed, new Checked(darkOak), new Checked(birch), fancyOakChecked}, oakChecked),
                COUNT, 16, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_sparse_jungle", new Selector(new float[]{0.1F, 0.5F}, new Feature[]{fancyOakChecked, jungleBushChecked}, jungleChecked),
                COUNT_EXTRA, 2, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_jungle", new Selector(new float[]{0.1F, 0.5F, 0.33333334F},
                new Feature[]{fancyOakChecked, jungleBushChecked, megaJungleChecked}, jungleChecked),
                COUNT_EXTRA, 50, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("bamboo_vegetation", new Selector(new float[]{0.05F, 0.15F, 0.7F},
                new Feature[]{fancyOakChecked, jungleBushChecked, megaJungleChecked}, inline(Vegetation.grassJungle())),
                COUNT_EXTRA, 30, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_old_growth_spruce_taiga", new Selector(new float[]{0.33333334F, 0.33333334F},
                new Feature[]{megaSpruceChecked, pineChecked}, spruceChecked),
                COUNT_EXTRA, 10, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_old_growth_pine_taiga", new Selector(new float[]{0.025641026F, 0.30769232F, 0.33333334F},
                new Feature[]{megaSpruceChecked, new Checked(megaPine), pineChecked}, spruceChecked),
                COUNT_EXTRA, 10, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_swamp", swampOak, COUNT_EXTRA, 2, 10, 1, IN_SQUARE, WATER_DEPTH, 2, HEIGHTMAP, of, BIOME, CUSTOM, 0).mods(sapling),
            new Placed("mushroom_island_vegetation", new LushCaves.RandomBoolean(hugeRed, hugeBrown),
                IN_SQUARE, HEIGHTMAP, Region.MOTION_BLOCKING, BIOME),
            new Placed("rooted_azalea_tree", new RootSystem(azalea),
                COUNT_UNIFORM, 1, 2, IN_SQUARE, HEIGHT_UNIFORM, -64, 256, CUSTOM, 0, BIOME).mods(LushCaves.scan(1, false, -1)),
            new Placed("trees_snowy", spruce, COUNT_EXTRA, 0, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME, CUSTOM, 0).mods(sapling),
            new Placed("trees_badlands", oak, COUNT_EXTRA, 5, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME, CUSTOM, 0).mods(sapling),
            new Placed("trees_birch", birchBees0002, COUNT_EXTRA, 10, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME, CUSTOM, 0).mods(sapling),
            new Placed("trees_taiga", new Selector(new float[]{0.33333334F}, new Feature[]{pineChecked}, spruceChecked),
                COUNT_EXTRA, 10, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("birch_tall", new Selector(new float[]{0.5F}, new Feature[]{superBirchBees0002Checked}, birchBees0002Checked),
                COUNT_EXTRA, 10, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_plains", new Selector(new float[]{0.33333334F}, new Feature[]{inline(fancyOak(0.05F))}, inline(oakBees005)),
                COUNT_EXTRA, 0, 20, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, CUSTOM, 0, BIOME).mods(sapling),
            new Placed("trees_water", new Selector(new float[]{0.1F}, new Feature[]{fancyOakChecked}, oakChecked),
                COUNT_EXTRA, 0, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_windswept_hills", new Selector(new float[]{0.666F, 0.1F}, new Feature[]{spruceChecked, fancyOakChecked}, oakChecked),
                COUNT_EXTRA, 0, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_windswept_forest", new Selector(new float[]{0.666F, 0.1F}, new Feature[]{spruceChecked, fancyOakChecked}, oakChecked),
                COUNT_EXTRA, 3, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_flower_forest", new Selector(new float[]{0.2F, 0.1F},
                new Feature[]{new Checked(birchBees002), new Checked(fancyOak(0.02F))}, new Checked(oakBees002)),
                COUNT_EXTRA, 6, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_meadow", new Selector(new float[]{0.5F}, new Feature[]{new Checked(fancyOak(1.0F))}, new Checked(superBirchBees)),
                RARITY, 100, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_savanna", savanna, COUNT_EXTRA, 1, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_windswept_savanna", savanna, COUNT_EXTRA, 2, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_birch_and_oak", new Selector(new float[]{0.2F, 0.1F},
                new Feature[]{birchBees0002Checked, new Checked(fancyOak(0.002F))}, new Checked(oakBees(0.002F))),
                COUNT_EXTRA, 10, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_grove",new Selector(new float[]{0.33333334F}, new Feature[]{new OnSnow(pine)}, new OnSnow(spruce)),
                COUNT_EXTRA, 10, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME));
    }

    private static Tree oakBees(float bees) {
        return new Tree(LOG[OAK][1], LEAVES[OAK][7], true, bees, STRAIGHT, 4, 2, 0, BLOB, c(2), c(0), 3, null, 1, 0, 1);
    }

    /** createFancyOak: FancyTrunkPlacer(3, 11, 0), FancyFoliagePlacer(2, 4, 4), TwoLayers(0, 0, 0, min clipped 4). */
    private static Tree fancyOak(float bees) {
        return new Tree(LOG[OAK][1], LEAVES[OAK][7], true, bees, FANCY, 3, 11, 0, FANCY_FOLIAGE, c(2), c(4), 4, null, 0, 0, 0)
            .minClipped(4);
    }

    /** PlacementUtils.inlinePlaced: no modifiers, then the feature (ensureCanWrite first). */
    private static Feature inline(Feature f) {
        return (level, random, x, y, z) -> LushCaves.placeInline(f, level, random, x, y, z);
    }

    /** BlockPredicate.wouldSurvive(sapling): BushBlock.mayPlaceOn the block below (dirt tag or farmland). */
    static boolean saplingSurvives(Region level, int x, int y, int z) {
        int below = level.get(x, y - 1, z);
        return LushCaves.dirt(below) || name(below).equals("farmland");
    }

    /** A *_checked placed tree: BlockPredicateFilter(wouldSurvive(sapling)), then the tree (ensureCanWrite first). */
    static final class Checked implements Feature {
        final Feature tree;

        Checked(Feature tree) {
            this.tree = tree;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            return saplingSurvives(level, x, y, z) && LushCaves.placeInline(tree, level, random, x, y, z);
        }
    }

    /**
     * TreePlacements' SNOW_TREE_FILTER_DECORATOR: EnvironmentScanPlacement up (at most 8) to a block that is not powder
     * snow, then snow block or powder snow below.
     */
    static final class OnSnow implements Feature {
        final Feature tree;

        OnSnow(Feature tree) {
            this.tree = tree;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int found = Integer.MIN_VALUE;
            for (int i = 0; ; ) {
                if (i < 8) {
                    if (level.get(x, y, z) != POWDER_SNOW) {
                        found = y;
                        break;
                    }
                    ++y;
                    if (Region.outsideBuildHeight(y)) {
                        return false;
                    }
                    ++i;
                    continue;
                }
                if (level.get(x, y, z) != POWDER_SNOW) {
                    found = y;
                }
                break;
            }
            if (found == Integer.MIN_VALUE) {
                return false;
            }
            int below = level.get(x, found - 1, z);
            if (below != SNOW_BLOCK && below != POWDER_SNOW) {
                return false;
            }
            return LushCaves.placeInline(tree, level, random, x, found, z);
        }
    }

    /** RandomFeatureConfiguration (Feature.RANDOM_SELECTOR): the first entry whose chance passes, else the default. */
    static final class Selector implements Feature {
        final float[] chances;
        final Feature[] features;
        final Feature fallback;

        Selector(float[] chances, Feature[] features, Feature fallback) {
            this.chances = chances;
            this.features = features;
            this.fallback = fallback;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            for (int i = 0; i < features.length; i++) {
                if (random.nextFloat() < chances[i]) {
                    return features[i].place(level, random, x, y, z);
                }
            }
            return fallback.place(level, random, x, y, z);
        }
    }

    // ---- int providers: ConstantInt draws nothing, UniformInt always draws ----

    static final class IntP {
        final int min;
        final int max;
        final boolean uniform;

        IntP(int min, int max, boolean uniform) {
            this.min = min;
            this.max = max;
            this.uniform = uniform;
        }

        int sample(XoroRandom random) {
            return uniform ? random.nextInt(max - min + 1) + min : min;
        }
    }

    static IntP c(int v) {
        return new IntP(v, v, false);
    }

    static IntP u(int min, int max) {
        return new IntP(min, max, true);
    }

    // ---- block tests ----

    /** TreeFeature.validTreePos: air, leaves, Material.REPLACEABLE_PLANT, or water. */
    static boolean validTreePos(int code) {
        int f = flags(code);
        return (f & F_NOT_AIR) == 0 || (f & (F_LEAVES | F_REPLACEABLE_PLANT)) != 0 || sameBlock(code, WATER);
    }

    /** TreeFeature.isFree: validTreePos or BlockTags.LOGS. */
    static boolean isFree(int code) {
        if (validTreePos(code)) {
            return true;
        }
        String n = name(code);
        return n.endsWith("_log") || n.endsWith("_wood") || n.endsWith("_stem") || n.endsWith("_hyphae");
    }

    /** TrunkPlacer.isDirt: the dirt tag but not grass or mycelium. */
    static boolean dirtNotGrass(int code) {
        return LushCaves.dirt(code) && !sameBlock(code, GRASS_BLOCK) && !sameBlock(code, MYCELIUM);
    }

    // ---- TreeFeature ----

    static final int STRAIGHT = 0;
    static final int BLOB = 0;
    static final int SPRUCE_FOLIAGE = 1;
    static final int PINE_FOLIAGE = 2;
    static final int FANCY_FOLIAGE = 3;
    static final int FANCY = 1;
    static final int FORKING = 2;
    static final int ACACIA_FOLIAGE = 4;
    static final int DARK_OAK_FOLIAGE = 5;
    static final int MEGA_JUNGLE_FOLIAGE = 6;
    static final int MEGA_PINE_FOLIAGE = 7;
    static final int BUSH_FOLIAGE = 8;
    static final int RANDOM_SPREAD_FOLIAGE = 9;
    static final int DARK_OAK_TRUNK = 3;
    static final int GIANT = 4;
    static final int MEGA_JUNGLE_TRUNK = 5;
    static final int BENDING = 6;

    // Tree decorators (the beehive is its own field).
    static final int D_COCOA = 1;
    static final int D_TRUNK_VINE = 2;
    static final int D_LEAVE_VINE = 3;
    static final int D_ALTER_GROUND = 4;

    /** VineBlock.EAST, WEST, SOUTH, NORTH (Direction ordinals). */
    private static final int EAST = 5;
    private static final int WEST = 4;
    private static final int SOUTH = 3;
    private static final int NORTH = 2;

    /** TreeFeature.isAirOrLeaves. */
    static boolean airOrLeaves(int code) {
        return isAir(code) || (flags(code) & F_LEAVES) != 0;
    }

    /** Per y-axis log code: the wood's x, y, z axis codes. */
    static final java.util.Map<Integer, int[]> LOG_BY_STATE = new java.util.HashMap<>();

    static {
        for (int[] axes : LOG) {
            LOG_BY_STATE.put(axes[1], axes);
        }
    }

    static final class Tree implements Feature {
        final int log;
        final int leaves;
        final boolean ignoreVines;
        /** BeehiveDecorator probability, or negative for none. */
        final float beehive;
        final int trunkType;
        final int baseHeight;
        final int randA;
        final int randB;
        final int foliageType;
        final IntP radius;
        final IntP offset;
        /** BlobFoliagePlacer height. */
        final int blobHeight;
        /** Spruce: trunk height; pine: height. */
        final IntP foliageInt;
        final int sizeLimit;
        final int sizeLower;
        final int sizeUpper;
        /** FeatureSize.minClippedHeight, or -1. */
        int minClipped = -1;

        Tree minClipped(int h) {
            minClipped = h;
            return this;
        }

        /** ThreeLayersFeatureSize(limit, upperLimit, lowerSize, middleSize, upperSize), or null for two layers. */
        int[] three;
        int[] decorators = new int[0];
        float cocoa;
        int dirt = DIRT;
        boolean forceDirt;
        /** azalea_tree's leaves: WeightedStateProvider(azalea leaves 3, flowering azalea leaves 1). */
        boolean azaleaLeaves;

        Tree three(int limit, int upperLimit, int lower, int middle, int upper) {
            three = new int[]{limit, upperLimit, lower, middle, upper};
            return this;
        }

        Tree decorate(int... d) {
            decorators = d;
            return this;
        }

        Tree cocoa(float p) {
            cocoa = p;
            return this;
        }

        /** azalea_tree: rooted dirt, forced, and the weighted azalea leaves. */
        Tree azalea() {
            dirt = ROOTED_DIRT;
            forceDirt = true;
            azaleaLeaves = true;
            return this;
        }

        /** FeatureSize.getSizeAtHeight. */
        private int sizeAt(int h, int y) {
            if (three != null) {
                if (y < three[0]) {
                    return three[2];
                }
                return y >= h - three[1] ? three[4] : three[3];
            }
            return y < sizeLimit ? sizeLower : sizeUpper;
        }

        /** The foliage provider's state (draws only for the weighted azalea leaves). */
        private int leafState(XoroRandom random) {
            if (azaleaLeaves) {
                return random.nextInt(4) < 3 ? LEAVES[AZALEA_LEAVES][7] : LEAVES[FLOWERING_AZALEA_LEAVES][7];
            }
            return leaves;
        }

        private HashSet<LushCaves.Pos> trunk;
        private HashSet<LushCaves.Pos> foliage;
        private HashSet<LushCaves.Pos> decorations;
        private Region level;

        Tree(int log, int leaves, boolean ignoreVines, float beehive, int trunkType, int baseHeight, int randA, int randB,
             int foliageType, IntP radius, IntP offset, int blobHeight, IntP foliageInt, int sizeLimit, int sizeLower, int sizeUpper) {
            this.log = log;
            this.leaves = leaves;
            this.ignoreVines = ignoreVines;
            this.beehive = beehive;
            this.trunkType = trunkType;
            this.baseHeight = baseHeight;
            this.randA = randA;
            this.randB = randB;
            this.foliageType = foliageType;
            this.radius = radius;
            this.offset = offset;
            this.blobHeight = blobHeight;
            this.foliageInt = foliageInt;
            this.sizeLimit = sizeLimit;
            this.sizeLower = sizeLower;
            this.sizeUpper = sizeUpper;
        }

        private void trunk(int x, int y, int z, int state) {
            trunk.add(new LushCaves.Pos(x, y, z));
            level.set(x, y, z, state);
        }

        private void foliage(int x, int y, int z, int state) {
            foliage.add(new LushCaves.Pos(x, y, z));
            level.set(x, y, z, state);
        }

        private void decoration(int x, int y, int z, int state) {
            decorations.add(new LushCaves.Pos(x, y, z));
            level.set(x, y, z, state);
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            this.level = level;
            trunk = new HashSet<>();
            foliage = new HashSet<>();
            decorations = new HashSet<>();
            boolean flag = doPlace(random, x, y, z);
            if (flag && (!trunk.isEmpty() || !foliage.isEmpty())) {
                if (beehive >= 0.0F || decorators.length > 0) {
                    List<LushCaves.Pos> logs = new ArrayList<>(trunk);
                    List<LushCaves.Pos> leafList = new ArrayList<>(foliage);
                    Comparator<LushCaves.Pos> byY = Comparator.comparingInt(p -> p.y);
                    logs.sort(byY);
                    leafList.sort(byY);
                    if (beehive >= 0.0F) {
                        beehive(random, logs, leafList);
                    }
                    for (int d : decorators) {
                        switch (d) {
                            case D_COCOA:
                                cocoa(random, logs);
                                break;
                            case D_TRUNK_VINE:
                                trunkVines(random, logs);
                                break;
                            case D_LEAVE_VINE:
                                leaveVines(random, leafList);
                                break;
                            default:
                                alterGround(random, logs);
                        }
                    }
                }
                int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
                encapsulate(box, trunk);
                encapsulate(box, foliage);
                encapsulate(box, decorations);
                Shape shape = updateLeaves(level, box, trunk, decorations);
                shape.updateShapeAtEdge(level);
                this.level = null;
                return true;
            }
            this.level = null;
            return false;
        }

        private static void encapsulate(int[] box, HashSet<LushCaves.Pos> set) {
            for (LushCaves.Pos p : set) {
                box[0] = Math.min(box[0], p.x);
                box[1] = Math.min(box[1], p.y);
                box[2] = Math.min(box[2], p.z);
                box[3] = Math.max(box[3], p.x);
                box[4] = Math.max(box[4], p.y);
                box[5] = Math.max(box[5], p.z);
            }
        }

        private boolean doPlace(XoroRandom random, int x, int y, int z) {
            int i = baseHeight + random.nextInt(randA + 1) + random.nextInt(randB + 1);
            int j = foliageHeight(random, i);
            int k = i - j;
            int l = foliageRadius(random, k);
            if (y >= -64 + 1 && y + i + 1 <= 320) {
                int i1 = maxFreeTreeHeight(i, x, y, z);
                if (i1 >= i || minClipped >= 0 && i1 >= minClipped) {
                    if (trunkType >= DARK_OAK_TRUNK) {
                        List<int[]> list;
                        switch (trunkType) {
                            case DARK_OAK_TRUNK:
                                list = darkOakTrunk(random, i1, x, y, z);
                                break;
                            case GIANT:
                                list = giantTrunk(random, i1, x, y, z);
                                break;
                            case MEGA_JUNGLE_TRUNK:
                                list = megaJungleTrunk(random, i1, x, y, z);
                                break;
                            default:
                                list = bendingTrunk(random, i1, x, y, z);
                        }
                        for (int[] a : list) {
                            createFoliage(random, a[0], a[1], a[2], a[3], a[4] != 0, i1, j, l);
                        }
                        return true;
                    }
                    if (trunkType == FORKING) {
                        for (int[] a : forkingTrunk(random, i1, x, y, z)) {
                            createFoliage(random, a[0], a[1], a[2], a[3], false, i1, j, l);
                        }
                        return true;
                    }
                    if (trunkType == FANCY) {
                        List<int[]> attachments = fancyTrunk(random, i1, x, y, z);
                        for (int[] a : attachments) {
                            createFoliage(random, a[0], a[1], a[2], 0, false, i1, j, l);
                        }
                        return true;
                    }
                    // StraightTrunkPlacer
                    setDirtAt(x, y - 1, z);
                    for (int q = 0; q < i1; ++q) {
                        placeLog(x, y + q, z);
                    }
                    createFoliage(random, x, y + i1, z, 0, false, i1, j, l);
                    return true;
                }
            }
            return false;
        }

        private int foliageHeight(XoroRandom random, int treeHeight) {
            switch (foliageType) {
                case ACACIA_FOLIAGE:
                    return 0;
                case DARK_OAK_FOLIAGE:
                    return 4;
                case MEGA_PINE_FOLIAGE:
                case RANDOM_SPREAD_FOLIAGE:
                    return foliageInt.sample(random);
                case SPRUCE_FOLIAGE:
                    return Math.max(4, treeHeight - foliageInt.sample(random));
                case PINE_FOLIAGE:
                    return foliageInt.sample(random);
                default:
                    return blobHeight;
            }
        }

        private int foliageRadius(XoroRandom random, int k) {
            int r = radius.sample(random);
            if (foliageType == PINE_FOLIAGE) {
                r += random.nextInt(Math.max(k + 1, 1));
            }
            return r;
        }

        private int maxFreeTreeHeight(int h, int x, int y, int z) {
            for (int i = 0; i <= h + 1; ++i) {
                int j = sizeAt(h, i);
                for (int k = -j; k <= j; ++k) {
                    for (int l = -j; l <= j; ++l) {
                        int s = level.get(x + k, y + i, z + l);
                        if (!isFree(s) || !ignoreVines && name(s).equals("vine")) {
                            return i - 2;
                        }
                    }
                }
            }
            return h;
        }

        private void setDirtAt(int x, int y, int z) {
            if (forceDirt || !dirtNotGrass(level.get(x, y, z))) {
                trunk(x, y, z, dirt);
            }
        }

        // ---- DarkOakTrunkPlacer, GiantTrunkPlacer, MegaJungleTrunkPlacer, BendingTrunkPlacer ----
        // Attachments as {x, y, z, radius offset, double trunk}.

        private List<int[]> darkOakTrunk(XoroRandom random, int h, int x, int y, int z) {
            List<int[]> list = new ArrayList<>();
            setDirtAt(x, y - 1, z);
            setDirtAt(x + 1, y - 1, z);
            setDirtAt(x, y - 1, z + 1);
            setDirtAt(x + 1, y - 1, z + 1);
            int direction = random.nextInt(4);
            int i = h - random.nextInt(4);
            int j = 2 - random.nextInt(3);
            int j1 = x;
            int k1 = z;
            int l1 = y + h - 1;
            for (int i2 = 0; i2 < h; ++i2) {
                if (i2 >= i && j > 0) {
                    j1 += HX[direction];
                    k1 += HZ[direction];
                    --j;
                }
                int j2 = y + i2;
                if (airOrLeaves(level.get(j1, j2, k1))) {
                    placeLogAt(j1, j2, k1);
                    placeLogAt(j1 + 1, j2, k1);
                    placeLogAt(j1, j2, k1 + 1);
                    placeLogAt(j1 + 1, j2, k1 + 1);
                }
            }
            list.add(new int[]{j1, l1, k1, 0, 1});
            for (int l2 = -1; l2 <= 2; ++l2) {
                for (int i3 = -1; i3 <= 2; ++i3) {
                    if ((l2 < 0 || l2 > 1 || i3 < 0 || i3 > 1) && random.nextInt(3) <= 0) {
                        int j3 = random.nextInt(3) + 2;
                        for (int k2 = 0; k2 < j3; ++k2) {
                            placeLogAt(x + l2, l1 - k2 - 1, z + i3);
                        }
                        list.add(new int[]{j1 + l2, l1, k1 + i3, 0, 0});
                    }
                }
            }
            return list;
        }

        private List<int[]> giantTrunk(XoroRandom random, int h, int x, int y, int z) {
            setDirtAt(x, y - 1, z);
            setDirtAt(x + 1, y - 1, z);
            setDirtAt(x, y - 1, z + 1);
            setDirtAt(x + 1, y - 1, z + 1);
            for (int i = 0; i < h; ++i) {
                placeLogIfFree(x, y + i, z);
                if (i < h - 1) {
                    placeLogIfFree(x + 1, y + i, z);
                    placeLogIfFree(x + 1, y + i, z + 1);
                    placeLogIfFree(x, y + i, z + 1);
                }
            }
            List<int[]> list = new ArrayList<>();
            list.add(new int[]{x, y + h, z, 0, 1});
            return list;
        }

        private void placeLogIfFree(int x, int y, int z) {
            if (isFree(level.get(x, y, z))) {
                placeLogAt(x, y, z);
            }
        }

        private List<int[]> megaJungleTrunk(XoroRandom random, int h, int x, int y, int z) {
            List<int[]> list = giantTrunk(random, h, x, y, z);
            for (int i = h - 2 - random.nextInt(4); i > h / 2; i -= 2 + random.nextInt(4)) {
                float f = random.nextFloat() * ((float) Math.PI * 2.0F);
                int j = 0;
                int k = 0;
                for (int l = 0; l < 5; ++l) {
                    j = (int) (1.5F + McMath.cos(f) * (float) l);
                    k = (int) (1.5F + McMath.sin(f) * (float) l);
                    placeLogAt(x + j, y + i - 3 + l / 2, z + k);
                }
                list.add(new int[]{x + j, y + i, z + k, -2, 0});
            }
            return list;
        }

        private List<int[]> bendingTrunk(XoroRandom random, int h, int x, int y, int z) {
            int direction = random.nextInt(4);
            int i = h - 1;
            int mx = x;
            int my = y;
            int mz = z;
            setDirtAt(x, y - 1, z);
            List<int[]> list = new ArrayList<>();
            for (int j = 0; j <= i; ++j) {
                if (j + 1 >= i + random.nextInt(2)) {
                    mx += HX[direction];
                    mz += HZ[direction];
                }
                if (validTreePos(level.get(mx, my, mz))) {
                    placeLogAt(mx, my, mz);
                }
                if (j >= 3) {
                    list.add(new int[]{mx, my, mz, 0, 0});
                }
                ++my;
            }
            int l = random.nextInt(2) + 1;
            for (int k = 0; k <= l; ++k) {
                if (validTreePos(level.get(mx, my, mz))) {
                    placeLogAt(mx, my, mz);
                }
                list.add(new int[]{mx, my, mz, 0, 0});
                mx += HX[direction];
                mz += HZ[direction];
            }
            return list;
        }

        private void placeLog(int x, int y, int z) {
            if (validTreePos(level.get(x, y, z))) {
                trunk(x, y, z, log);
            }
        }

        // ---- ForkingTrunkPlacer ----

        /** Direction.Plane.HORIZONTAL (north, east, south, west) steps. */
        private static final int[] HX = {0, 1, 0, -1};
        private static final int[] HZ = {-1, 0, 1, 0};

        /** Attachments as {x, y, z, radius offset}. */
        private List<int[]> forkingTrunk(XoroRandom random, int h, int ox, int oy, int oz) {
            setDirtAt(ox, oy - 1, oz);
            List<int[]> list = new ArrayList<>();
            int direction = random.nextInt(4);
            int i = h - random.nextInt(4) - 1;
            int j = 3 - random.nextInt(3);
            int k = ox;
            int l = oz;
            int top = Integer.MIN_VALUE;
            for (int i1 = 0; i1 < h; ++i1) {
                int j1 = oy + i1;
                if (i1 >= i && j > 0) {
                    k += HX[direction];
                    l += HZ[direction];
                    --j;
                }
                if (placeLogAt(k, j1, l)) {
                    top = j1 + 1;
                }
            }
            if (top != Integer.MIN_VALUE) {
                list.add(new int[]{k, top, l, 1});
            }
            k = ox;
            l = oz;
            int direction1 = random.nextInt(4);
            if (direction1 != direction) {
                int j2 = i - random.nextInt(2) - 1;
                int k1 = 1 + random.nextInt(3);
                top = Integer.MIN_VALUE;
                for (int l1 = j2; l1 < h && k1 > 0; --k1) {
                    if (l1 >= 1) {
                        int i2 = oy + l1;
                        k += HX[direction1];
                        l += HZ[direction1];
                        if (placeLogAt(k, i2, l)) {
                            top = i2 + 1;
                        }
                    }
                    ++l1;
                }
                if (top != Integer.MIN_VALUE) {
                    list.add(new int[]{k, top, l, 0});
                }
            }
            return list;
        }

        private boolean placeLogAt(int x, int y, int z) {
            if (validTreePos(level.get(x, y, z))) {
                trunk(x, y, z, log);
                return true;
            }
            return false;
        }

        // ---- FancyTrunkPlacer ----

        /** Attachments as {x, y, z, branch base}. */
        private List<int[]> fancyTrunk(XoroRandom random, int h, int ox, int oy, int oz) {
            int j = h + 2;
            int k = McMath.floor((double) j * 0.618D);
            setDirtAt(ox, oy - 1, oz);
            int l = Math.min(1, McMath.floor(1.382D + Math.pow(1.0D * (double) j / 13.0D, 2.0D)));
            int i1 = oy + k;
            int j1 = j - 5;
            List<int[]> list = new ArrayList<>();
            list.add(new int[]{ox, oy + j1, oz, i1});
            for (; j1 >= 0; --j1) {
                float f = treeShape(j, j1);
                if (!(f < 0.0F)) {
                    for (int k1 = 0; k1 < l; ++k1) {
                        double d2 = 1.0D * (double) f * ((double) random.nextFloat() + 0.328D);
                        double d3 = (double) (random.nextFloat() * 2.0F) * Math.PI;
                        double d4 = d2 * Math.sin(d3) + 0.5D;
                        double d5 = d2 * Math.cos(d3) + 0.5D;
                        int bx = McMath.floor((double) ox + d4);
                        int by = oy + (j1 - 1);
                        int bz = McMath.floor((double) oz + d5);
                        if (makeLimb(random, bx, by, bz, bx, by + 5, bz, false)) {
                            int l1 = ox - bx;
                            int i2 = oz - bz;
                            double d6 = (double) by - Math.sqrt((double) (l1 * l1 + i2 * i2)) * 0.381D;
                            int j2 = d6 > (double) i1 ? i1 : (int) d6;
                            if (makeLimb(random, ox, j2, oz, bx, by, bz, false)) {
                                list.add(new int[]{bx, by, bz, j2});
                            }
                        }
                    }
                }
            }
            makeLimb(random, ox, oy, oz, ox, oy + k, oz, true);
            for (int[] c : list) {
                int i = c[3];
                if (!(ox == c[0] && i == c[1] && oz == c[2]) && trimBranches(j, i - oy)) {
                    makeLimb(random, ox, i, oz, c[0], c[1], c[2], true);
                }
            }
            List<int[]> out = new ArrayList<>();
            for (int[] c : list) {
                if (trimBranches(j, c[3] - oy)) {
                    out.add(c);
                }
            }
            return out;
        }

        private boolean makeLimb(XoroRandom random, int sx, int sy, int sz, int ex, int ey, int ez, boolean place) {
            if (!place && sx == ex && sy == ey && sz == ez) {
                return true;
            }
            int dx = ex - sx;
            int dy = ey - sy;
            int dz = ez - sz;
            int i = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
            float f = (float) dx / (float) i;
            float f1 = (float) dy / (float) i;
            float f2 = (float) dz / (float) i;
            for (int j = 0; j <= i; ++j) {
                int x = McMath.floor((double) sx + (double) (0.5F + (float) j * f));
                int y = McMath.floor((double) sy + (double) (0.5F + (float) j * f1));
                int z = McMath.floor((double) sz + (double) (0.5F + (float) j * f2));
                if (place) {
                    if (validTreePos(level.get(x, y, z))) {
                        // getLogAxis(start, this block)
                        int ax = Math.abs(x - sx);
                        int az = Math.abs(z - sz);
                        int m = Math.max(ax, az);
                        int axis = m > 0 ? (ax == m ? 0 : 2) : 1;
                        trunk(x, y, z, LOG_BY_STATE.get(log)[axis]);
                    }
                } else if (!isFree(level.get(x, y, z))) {
                    return false;
                }
            }
            return true;
        }

        private static boolean trimBranches(int h, int y) {
            return (double) y >= (double) h * 0.2D;
        }

        private static float treeShape(int h, int y) {
            if ((float) y < (float) h * 0.3F) {
                return -1.0F;
            }
            float f = (float) h / 2.0F;
            float f1 = f - (float) y;
            float f2 = (float) Math.sqrt((double) (f * f - f1 * f1));
            if (f1 == 0.0F) {
                f2 = f;
            } else if (Math.abs(f1) >= f) {
                return 0.0F;
            }
            return f2 * 0.5F;
        }

        // ---- foliage placers ----

        private void createFoliage(XoroRandom random, int ax, int ay, int az, int radiusOffset, boolean doubleTrunk,
                                   int treeHeight, int foliageHeight, int foliageRadius) {
            int off = offset.sample(random);
            switch (foliageType) {
                case SPRUCE_FOLIAGE: {
                    int i = random.nextInt(2);
                    int j = 1;
                    int k = 0;
                    for (int l = off; l >= -foliageHeight; --l) {
                        leavesRow(random, ax, ay, az, i, l, doubleTrunk);
                        if (i >= j) {
                            i = k;
                            k = 1;
                            j = Math.min(j + 1, foliageRadius + radiusOffset);
                        } else {
                            ++i;
                        }
                    }
                    return;
                }
                case PINE_FOLIAGE: {
                    int i = 0;
                    for (int j = off; j >= off - foliageHeight; --j) {
                        leavesRow(random, ax, ay, az, i, j, doubleTrunk);
                        if (i >= 1 && j == off - foliageHeight + 1) {
                            --i;
                        } else if (i < foliageRadius + radiusOffset) {
                            ++i;
                        }
                    }
                    return;
                }
                case ACACIA_FOLIAGE: {
                    int by = ay + off;
                    leavesRow(random, ax, by, az, foliageRadius + radiusOffset, -1 - foliageHeight, doubleTrunk);
                    leavesRow(random, ax, by, az, foliageRadius - 1, -foliageHeight, doubleTrunk);
                    leavesRow(random, ax, by, az, foliageRadius + radiusOffset - 1, 0, doubleTrunk);
                    return;
                }
                case DARK_OAK_FOLIAGE: {
                    int by = ay + off;
                    if (doubleTrunk) {
                        leavesRow(random, ax, by, az, foliageRadius + 2, -1, true);
                        leavesRow(random, ax, by, az, foliageRadius + 3, 0, true);
                        leavesRow(random, ax, by, az, foliageRadius + 2, 1, true);
                        if (random.nextBoolean()) {
                            leavesRow(random, ax, by, az, foliageRadius, 2, true);
                        }
                    } else {
                        leavesRow(random, ax, by, az, foliageRadius + 2, -1, false);
                        leavesRow(random, ax, by, az, foliageRadius + 1, 0, false);
                    }
                    return;
                }
                case MEGA_JUNGLE_FOLIAGE: {
                    int i = doubleTrunk ? foliageHeight : 1 + random.nextInt(2);
                    for (int j = off; j >= off - i; --j) {
                        int k = foliageRadius + radiusOffset + 1 - j;
                        leavesRow(random, ax, ay, az, k, j, doubleTrunk);
                    }
                    return;
                }
                case MEGA_PINE_FOLIAGE: {
                    int i = 0;
                    for (int j = ay - foliageHeight + off; j <= ay + off; ++j) {
                        int k = ay - j;
                        int l = foliageRadius + radiusOffset + McMath.floor((float) k / (float) foliageHeight * 3.5F);
                        int i1;
                        if (k > 0 && l == i && (j & 1) == 0) {
                            i1 = l + 1;
                        } else {
                            i1 = l;
                        }
                        leavesRow(random, ax, j, az, i1, 0, doubleTrunk);
                        i = l;
                    }
                    return;
                }
                case BUSH_FOLIAGE:
                    for (int i = off; i >= off - foliageHeight; --i) {
                        int j = foliageRadius + radiusOffset - 1 - i;
                        leavesRow(random, ax, ay, az, j, i, doubleTrunk);
                    }
                    return;
                case RANDOM_SPREAD_FOLIAGE:
                    for (int i = 0; i < 50; ++i) {
                        int x = ax + random.nextInt(foliageRadius) - random.nextInt(foliageRadius);
                        int y = ay + random.nextInt(foliageHeight) - random.nextInt(foliageHeight);
                        int z = az + random.nextInt(foliageRadius) - random.nextInt(foliageRadius);
                        if (validTreePos(level.get(x, y, z))) {
                            foliage(x, y, z, leafState(random));
                        }
                    }
                    return;
                case FANCY_FOLIAGE:
                    for (int i = off; i >= off - foliageHeight; --i) {
                        int j = foliageRadius + (i != off && i != off - foliageHeight ? 1 : 0);
                        leavesRow(random, ax, ay, az, j, i, doubleTrunk);
                    }
                    return;
                default:
                    for (int i = off; i >= off - foliageHeight; --i) {
                        int j = Math.max(foliageRadius + radiusOffset - 1 - i / 2, 0);
                        leavesRow(random, ax, ay, az, j, i, doubleTrunk);
                    }
            }
        }

        private boolean skip(XoroRandom random, int dx, int dy, int dz, int r, boolean doubleTrunk) {
            if (foliageType == BLOB) {
                return dx == r && dz == r && (random.nextInt(2) == 0 || dy == 0);
            }
            if (foliageType == ACACIA_FOLIAGE) {
                if (dy == 0) {
                    return (dx > 1 || dz > 1) && dx != 0 && dz != 0;
                }
                return dx == r && dz == r && r > 0;
            }
            if (foliageType == FANCY_FOLIAGE) {
                return ((float) dx + 0.5F) * ((float) dx + 0.5F) + ((float) dz + 0.5F) * ((float) dz + 0.5F) > (float) (r * r);
            }
            if (foliageType == DARK_OAK_FOLIAGE) {
                if (dy == -1 && !doubleTrunk) {
                    return dx == r && dz == r;
                }
                if (dy == 1) {
                    return dx + dz > r * 2 - 2;
                }
                return false;
            }
            if (foliageType == MEGA_JUNGLE_FOLIAGE || foliageType == MEGA_PINE_FOLIAGE) {
                if (dx + dz >= 7) {
                    return true;
                }
                return dx * dx + dz * dz > r * r;
            }
            if (foliageType == BUSH_FOLIAGE) {
                return dx == r && dz == r && random.nextInt(2) == 0;
            }
            // spruce, pine
            return dx == r && dz == r && r > 0;
        }

        private void leavesRow(XoroRandom random, int ax, int ay, int az, int r, int dy, boolean doubleTrunk) {
            int i = doubleTrunk ? 1 : 0;
            for (int j = -r; j <= r + i; ++j) {
                for (int k = -r; k <= r + i; ++k) {
                    // DarkOakFoliagePlacer.shouldSkipLocationSigned: the double trunk's level row skips its corners.
                    if (foliageType == DARK_OAK_FOLIAGE && !(dy != 0 || !doubleTrunk || j != -r && j < r || k != -r && k < r)) {
                        continue;
                    }
                    int sx;
                    int sz;
                    if (doubleTrunk) {
                        sx = Math.min(Math.abs(j), Math.abs(j - 1));
                        sz = Math.min(Math.abs(k), Math.abs(k - 1));
                    } else {
                        sx = Math.abs(j);
                        sz = Math.abs(k);
                    }
                    if (!skip(random, sx, dy, sz, r, doubleTrunk)) {
                        int x = ax + j;
                        int y = ay + dy;
                        int z = az + k;
                        if (validTreePos(level.get(x, y, z))) {
                            foliage(x, y, z, leafState(random));
                        }
                    }
                }
            }
        }

        // ---- decorators ----

        /** BeehiveDecorator: worldgen facing south, spawn directions east, south, west. */
        private void beehive(XoroRandom random, List<LushCaves.Pos> logs, List<LushCaves.Pos> leafList) {
            if (random.nextFloat() >= beehive) {
                return;
            }
            int i = !leafList.isEmpty() ? Math.max(leafList.get(0).y - 1, logs.get(0).y + 1)
                : Math.min(logs.get(0).y + 1 + random.nextInt(3), logs.get(logs.size() - 1).y);
            List<LushCaves.Pos> list = new ArrayList<>();
            for (LushCaves.Pos p : logs) {
                if (p.y == i) {
                    list.add(new LushCaves.Pos(p.x + 1, p.y, p.z));
                    list.add(new LushCaves.Pos(p.x, p.y, p.z + 1));
                    list.add(new LushCaves.Pos(p.x - 1, p.y, p.z));
                }
            }
            if (list.isEmpty()) {
                return;
            }
            // Vanilla: Collections.shuffle(list) on the JVM's shared random (not reproducible).
            LushCaves.Pos first = list.get(0);
            Collections.shuffle(list, new java.util.Random((long) first.x * 3129871L ^ (long) first.z * 116129781L ^ (long) first.y));
            for (LushCaves.Pos p : list) {
                if (isAir(level.get(p.x, p.y, p.z)) && isAir(level.get(p.x, p.y, p.z + 1))) {
                    decoration(p.x, p.y, p.z, BEE_NEST);
                    if (level.get(p.x, p.y, p.z) == BEE_NEST) {
                        int j = 2 + random.nextInt(2);
                        for (int k = 0; k < j; ++k) {
                            random.nextInt(599);
                        }
                    }
                    return;
                }
            }
        }

        /** CocoaDecorator: the lowest three log layers, each side by 1/4, facing the log. */
        private void cocoa(XoroRandom random, List<LushCaves.Pos> logs) {
            if (random.nextFloat() >= cocoa) {
                return;
            }
            int i = logs.get(0).y;
            for (LushCaves.Pos p : logs) {
                if (p.y - i <= 2) {
                    for (int d : HORIZONTAL) {
                        if (random.nextFloat() <= 0.25F) {
                            int o = d ^ 1;
                            int x = p.x + LushCaves.DX[o];
                            int z = p.z + LushCaves.DZ[o];
                            if (isAir(level.get(x, p.y, z))) {
                                decoration(x, p.y, z, COCOA[random.nextInt(3)][d]);
                            }
                        }
                    }
                }
            }
        }

        /** TrunkVineDecorator: per log, west, east, north, south by 2/3 each. */
        private void trunkVines(XoroRandom random, List<LushCaves.Pos> logs) {
            for (LushCaves.Pos p : logs) {
                if (random.nextInt(3) > 0 && isAir(level.get(p.x - 1, p.y, p.z))) {
                    decoration(p.x - 1, p.y, p.z, LushCaves.VINE[1 << EAST]);
                }
                if (random.nextInt(3) > 0 && isAir(level.get(p.x + 1, p.y, p.z))) {
                    decoration(p.x + 1, p.y, p.z, LushCaves.VINE[1 << WEST]);
                }
                if (random.nextInt(3) > 0 && isAir(level.get(p.x, p.y, p.z - 1))) {
                    decoration(p.x, p.y, p.z - 1, LushCaves.VINE[1 << SOUTH]);
                }
                if (random.nextInt(3) > 0 && isAir(level.get(p.x, p.y, p.z + 1))) {
                    decoration(p.x, p.y, p.z + 1, LushCaves.VINE[1 << NORTH]);
                }
            }
        }

        /** LeaveVineDecorator: per leaf, west, east, north, south by 1/4 each, hanging down up to 4 more. */
        private void leaveVines(XoroRandom random, List<LushCaves.Pos> leafList) {
            for (LushCaves.Pos p : leafList) {
                if (random.nextInt(4) == 0 && isAir(level.get(p.x - 1, p.y, p.z))) {
                    hangingVine(p.x - 1, p.y, p.z, EAST);
                }
                if (random.nextInt(4) == 0 && isAir(level.get(p.x + 1, p.y, p.z))) {
                    hangingVine(p.x + 1, p.y, p.z, WEST);
                }
                if (random.nextInt(4) == 0 && isAir(level.get(p.x, p.y, p.z - 1))) {
                    hangingVine(p.x, p.y, p.z - 1, SOUTH);
                }
                if (random.nextInt(4) == 0 && isAir(level.get(p.x, p.y, p.z + 1))) {
                    hangingVine(p.x, p.y, p.z + 1, NORTH);
                }
            }
        }

        private void hangingVine(int x, int y, int z, int face) {
            int vine = LushCaves.VINE[1 << face];
            decoration(x, y, z, vine);
            int i = 4;
            for (int by = y - 1; isAir(level.get(x, by, z)) && i > 0; --i) {
                decoration(x, by, z, vine);
                --by;
            }
        }

        /** AlterGroundDecorator(podzol) around the lowest log layer. */
        private void alterGround(XoroRandom random, List<LushCaves.Pos> logs) {
            if (logs.isEmpty()) {
                return;
            }
            int i = logs.get(0).y;
            for (LushCaves.Pos p : logs) {
                if (p.y != i) {
                    continue;
                }
                placeCircle(p.x - 1, p.y, p.z - 1);
                placeCircle(p.x + 2, p.y, p.z - 1);
                placeCircle(p.x - 1, p.y, p.z + 2);
                placeCircle(p.x + 2, p.y, p.z + 2);
                for (int j = 0; j < 5; ++j) {
                    int k = random.nextInt(64);
                    int l = k % 8;
                    int i1 = k / 8;
                    if (l == 0 || l == 7 || i1 == 0 || i1 == 7) {
                        placeCircle(p.x - 3 + l, p.y, p.z - 3 + i1);
                    }
                }
            }
        }

        private void placeCircle(int x, int y, int z) {
            for (int i = -2; i <= 2; ++i) {
                for (int j = -2; j <= 2; ++j) {
                    if (Math.abs(i) != 2 || Math.abs(j) != 2) {
                        placeGroundAt(x + i, y, z + j);
                    }
                }
            }
        }

        private void placeGroundAt(int x, int y, int z) {
            for (int i = 2; i >= -3; --i) {
                int s = level.get(x, y + i, z);
                if (LushCaves.dirt(s)) {
                    decoration(x, y + i, z, PODZOL);
                    break;
                }
                if (!isAir(s) && i < 0) {
                    break;
                }
            }
        }
    }

    /** Direction.Plane.HORIZONTAL: north, east, south, west, as Direction ordinals. */
    private static final int[] HORIZONTAL = {2, 5, 3, 4};

    // ---- HugeRedMushroomFeature, HugeBrownMushroomFeature (cap radius 2 / 3) ----

    static final class HugeMushroom implements Feature {
        final boolean red;
        final int radius;

        HugeMushroom(boolean red) {
            this.red = red;
            this.radius = red ? 2 : 3;
        }

        private int radiusForHeight(int j) {
            // getTreeRadiusForHeight(-1, -1, radius, j): red never widens its test, brown above 3.
            return red ? 0 : j <= 3 ? 0 : radius;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int h = random.nextInt(3) + 4;
            if (random.nextInt(12) == 0) {
                h *= 2;
            }
            if (!(y >= -64 + 1 && y + h + 1 < 320)) {
                return false;
            }
            int below = level.get(x, y - 1, z);
            if (!LushCaves.dirt(below) && !sameBlock(below, MYCELIUM) && !sameBlock(below, PODZOL)) {
                return false;
            }
            for (int j = 0; j <= h; ++j) {
                int k = radiusForHeight(j);
                for (int l = -k; l <= k; ++l) {
                    for (int i1 = -k; i1 <= k; ++i1) {
                        int s = level.get(x + l, y + j, z + i1);
                        if (!isAir(s) && (flags(s) & F_LEAVES) == 0) {
                            return false;
                        }
                    }
                }
            }
            if (red) {
                redCap(level, x, y, z, h);
            } else {
                brownCap(level, x, y, z, h);
            }
            for (int i = 0; i < h; ++i) {
                if (!solidRender(level.get(x, y + i, z))) {
                    level.set(x, y + i, z, MUSHROOM_STEM);
                }
            }
            return true;
        }

        private static int mask(boolean up, boolean west, boolean east, boolean north, boolean south) {
            return (up ? 2 : 0) | (north ? 4 : 0) | (south ? 8 : 0) | (west ? 16 : 0) | (east ? 32 : 0);
        }

        private void redCap(Region level, int x, int y, int z, int h) {
            for (int i = h - 3; i <= h; ++i) {
                int j = i < h ? radius : radius - 1;
                int k = radius - 2;
                for (int l = -j; l <= j; ++l) {
                    for (int i1 = -j; i1 <= j; ++i1) {
                        boolean flag4 = l == -j || l == j;
                        boolean flag5 = i1 == -j || i1 == j;
                        if (i >= h || flag4 != flag5) {
                            if (!solidRender(level.get(x + l, y + i, z + i1))) {
                                level.set(x + l, y + i, z + i1,
                                    RED_MUSHROOM_BLOCK[mask(i >= h - 1, l < -k, l > k, i1 < -k, i1 > k)]);
                            }
                        }
                    }
                }
            }
        }

        private void brownCap(Region level, int x, int y, int z, int h) {
            int i = radius;
            for (int j = -i; j <= i; ++j) {
                for (int k = -i; k <= i; ++k) {
                    boolean flag = j == -i;
                    boolean flag1 = j == i;
                    boolean flag2 = k == -i;
                    boolean flag3 = k == i;
                    boolean flag4 = flag || flag1;
                    boolean flag5 = flag2 || flag3;
                    if (!flag4 || !flag5) {
                        if (!solidRender(level.get(x + j, y + h, z + k))) {
                            boolean flag6 = flag || flag5 && j == 1 - i;
                            boolean flag7 = flag1 || flag5 && j == i - 1;
                            boolean flag8 = flag2 || flag4 && k == 1 - i;
                            boolean flag9 = flag3 || flag4 && k == i - 1;
                            level.set(x + j, y + h, z + k, BROWN_MUSHROOM_BLOCK[mask(true, flag6, flag7, flag8, flag9)]);
                        }
                    }
                }
            }
        }
    }

    // ---- RootSystemFeature (rooted_azalea_tree) ----

    static final class RootSystem implements Feature {
        final Feature tree;

        RootSystem(Feature tree) {
            this.tree = tree;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            if (!isAir(level.get(x, y, z))) {
                return false;
            }
            int my = y;
            for (int i = 0; i < 100; ++i) {
                ++my;
                if (allowedTreePosition(level, x, my, z) && spaceForTree(level, x, my, z)) {
                    int below = level.get(x, my - 1, z);
                    if ((flags(below) & F_FLUID) != 0 && !Vegetation.isWaterFluid(below) || !solid(below)) {
                        return true;
                    }
                    if (LushCaves.placeInline(tree, level, random, x, my, z)) {
                        for (int k = y; k < y + i; ++k) {
                            placeRootedDirt(level, random, x, k, z);
                        }
                        placeRoots(level, random, x, y, z);
                        return true;
                    }
                }
            }
            return true;
        }

        private static boolean allowedTreePosition(Region level, int x, int y, int z) {
            int s = level.get(x, y, z);
            String n = name(s);
            boolean here = isAir(s) || n.equals("water") || (flags(s) & (F_LEAVES | F_REPLACEABLE_PLANT)) != 0;
            if (!here) {
                return false;
            }
            int b = level.get(x, y - 1, z);
            return LushCaves.dirt(b) || b == SAND || b == RED_SAND || Vegetation.terracotta(b) || b == SNOW_BLOCK || b == POWDER_SNOW;
        }

        private static boolean spaceForTree(Region level, int x, int y, int z) {
            for (int i = 1; i <= 3; ++i) {
                int s = level.get(x, y + i, z);
                if (!isAir(s) && !(i + 1 <= 2 && Vegetation.isWaterFluid(s))) {
                    return false;
                }
            }
            return true;
        }

        private static boolean rootReplaceable(int s) {
            return Dripstone.baseStoneOverworld(s) || LushCaves.dirt(s) || Vegetation.terracotta(s) || s == RED_SAND || s == CLAY
                || s == GRAVEL || s == SAND || s == SNOW_BLOCK || s == POWDER_SNOW;
        }

        private static void placeRootedDirt(Region level, XoroRandom random, int x, int y, int z) {
            for (int j = 0; j < 20; ++j) {
                int px = x + random.nextInt(3) - random.nextInt(3);
                int pz = z + random.nextInt(3) - random.nextInt(3);
                if (rootReplaceable(level.get(px, y, pz))) {
                    level.set(px, y, pz, ROOTED_DIRT);
                }
            }
        }

        private static void placeRoots(Region level, XoroRandom random, int x, int y, int z) {
            for (int k = 0; k < 20; ++k) {
                int px = x + random.nextInt(3) - random.nextInt(3);
                int py = y + random.nextInt(2) - random.nextInt(2);
                int pz = z + random.nextInt(3) - random.nextInt(3);
                if (isAir(level.get(px, py, pz)) && sturdyFace(level.get(px, py + 1, pz), 0)) {
                    level.set(px, py, pz, HANGING_ROOTS);
                }
            }
        }
    }

    // ---- TreeFeature.updateLeaves and StructureTemplate.updateShapeAtEdge ----

    /** A DiscreteVoxelShape over a bounding box. */
    static final class Shape {
        final int minX;
        final int minY;
        final int minZ;
        final int sx;
        final int sy;
        final int sz;
        final boolean[] full;

        Shape(int[] box) {
            minX = box[0];
            minY = box[1];
            minZ = box[2];
            sx = box[3] - box[0] + 1;
            sy = box[4] - box[1] + 1;
            sz = box[5] - box[2] + 1;
            full = new boolean[sx * sy * sz];
        }

        boolean inside(int x, int y, int z) {
            return x >= minX && x < minX + sx && y >= minY && y < minY + sy && z >= minZ && z < minZ + sz;
        }

        void fill(int x, int y, int z) {
            full[((x - minX) * sy + (y - minY)) * sz + (z - minZ)] = true;
        }

        boolean isFull(int x, int y, int z) {
            return full[(x * sy + y) * sz + z];
        }

        /** DiscreteVoxelShape.forAllFaces with updateShapeAtEdge's consumer: z faces, then y, then x. */
        void updateShapeAtEdge(Region level) {
            for (int x = 0; x < sx; ++x) {
                for (int y = 0; y < sy; ++y) {
                    boolean flag = false;
                    for (int z = 0; z <= sz; ++z) {
                        boolean flag1 = z != sz && isFull(x, y, z);
                        if (!flag && flag1) {
                            face(level, 2, x, y, z);
                        }
                        if (flag && !flag1) {
                            face(level, 3, x, y, z - 1);
                        }
                        flag = flag1;
                    }
                }
            }
            for (int z = 0; z < sz; ++z) {
                for (int x = 0; x < sx; ++x) {
                    boolean flag = false;
                    for (int y = 0; y <= sy; ++y) {
                        boolean flag1 = y != sy && isFull(x, y, z);
                        if (!flag && flag1) {
                            face(level, 0, x, y, z);
                        }
                        if (flag && !flag1) {
                            face(level, 1, x, y - 1, z);
                        }
                        flag = flag1;
                    }
                }
            }
            for (int y = 0; y < sy; ++y) {
                for (int z = 0; z < sz; ++z) {
                    boolean flag = false;
                    for (int x = 0; x <= sx; ++x) {
                        boolean flag1 = x != sx && isFull(x, y, z);
                        if (!flag && flag1) {
                            face(level, 4, x, y, z);
                        }
                        if (flag && !flag1) {
                            face(level, 5, x - 1, y, z);
                        }
                        flag = flag1;
                    }
                }
            }
        }

        private void face(Region level, int dir, int lx, int ly, int lz) {
            int x = minX + lx;
            int y = minY + ly;
            int z = minZ + lz;
            int nx = x + LushCaves.DX[dir];
            int ny = y + LushCaves.DY[dir];
            int nz = z + LushCaves.DZ[dir];
            int state = level.get(x, y, z);
            int neighbor = level.get(nx, ny, nz);
            int state2 = updateShape(level, state, dir, neighbor, x, y, z);
            if (state2 != state) {
                level.set(x, y, z, state2);
            }
            int state3 = updateShape(level, neighbor, dir ^ 1, state2, nx, ny, nz);
            if (state3 != neighbor) {
                level.set(nx, ny, nz, state3);
            }
        }
    }

    /** Block.updateShape(state, direction, neighbor) for the blocks trees can border (others keep their state). */
    static int updateShape(Region level, int state, int dir, int neighbor, int x, int y, int z) {
        if (dir == 1) {
            // SnowyDirtBlock: snowy follows the block above.
            boolean snow = sameBlock(neighbor, SurfaceFeatures.SNOW) || neighbor == SNOW_BLOCK || neighbor == POWDER_SNOW;
            if (sameBlock(state, GRASS_BLOCK)) {
                return snow ? SurfaceFeatures.GRASS_BLOCK_SNOWY : GRASS_BLOCK;
            }
            if (sameBlock(state, PODZOL)) {
                return snow ? SurfaceFeatures.PODZOL_SNOWY : PODZOL;
            }
            if (sameBlock(state, MYCELIUM)) {
                return snow ? SurfaceFeatures.MYCELIUM_SNOWY : MYCELIUM;
            }
        }
        if (sameBlock(state, SurfaceFeatures.SNOW)) {
            return snowSurvives(level.get(x, y - 1, z)) ? state : AIR;
        }
        int vm = vineMask(state);
        if (vm >= 0) {
            if (dir == 0) {
                return state;
            }
            int m = vineUpdated(level, vm, x, y, z);
            return m == 0 ? AIR : LushCaves.VINE[m];
        }
        int cf = cocoaFacing(state);
        if (cf >= 0) {
            if (dir == cf && !jungleLog(level.get(x + LushCaves.DX[cf], y, z + LushCaves.DZ[cf]))) {
                return AIR;
            }
            return state;
        }
        if (state == HANGING_ROOTS) {
            return dir == 1 && !sturdyFace(level.get(x, y + 1, z), 0) ? AIR : state;
        }
        return Vegetation.updateShape(level, state, dir, neighbor, x, y, z);
    }

    private static int[] vineMasks;
    private static int[] cocoaFacings;

    private static synchronized void buildShapeTables() {
        int[] v = new int[count()];
        int[] c = new int[count()];
        Arrays.fill(v, -1);
        Arrays.fill(c, -1);
        for (int m = 0; m < 64; m += 2) {
            v[LushCaves.VINE[m]] = m;
        }
        for (int[] ages : COCOA) {
            for (int f = 2; f < 6; f++) {
                c[ages[f]] = f;
            }
        }
        cocoaFacings = c;
        vineMasks = v;
    }

    private static int vineMask(int code) {
        if (vineMasks == null) {
            buildShapeTables();
        }
        return code < vineMasks.length ? vineMasks[code] : -1;
    }

    private static int cocoaFacing(int code) {
        if (cocoaFacings == null) {
            buildShapeTables();
        }
        return code < cocoaFacings.length ? cocoaFacings[code] : -1;
    }

    private static boolean jungleLog(int code) {
        String n = name(code);
        return n.equals("jungle_log") || n.equals("jungle_wood") || n.equals("stripped_jungle_log") || n.equals("stripped_jungle_wood");
    }

    /** VineBlock.getUpdatedState as a face mask. */
    private static int vineUpdated(Region level, int mask, int x, int y, int z) {
        int above = level.get(x, y + 1, z);
        if ((mask & 2) != 0 && !faceFull(above, 1)) {
            mask &= ~2;
        }
        for (int d : HORIZONTAL) {
            if ((mask >> d & 1) != 0) {
                boolean flag = faceFull(level.get(x + LushCaves.DX[d], y, z + LushCaves.DZ[d]), d ^ 1);
                if (!flag) {
                    // canSupportAtFace's fallback and getUpdatedState's: the vine above has this face.
                    int am = vineMask(above);
                    flag = am >= 0 && (am >> d & 1) != 0;
                }
                if (!flag) {
                    mask &= ~(1 << d);
                }
            }
        }
        return mask;
    }

    /** SnowLayerBlock.canSurvive (snow below must be 8 layers; freeze_top_layer makes single layers only). */
    private static boolean snowSurvives(int below) {
        String n = name(below);
        if (n.equals("ice") || n.equals("packed_ice") || n.equals("barrier")) {
            return false;
        }
        if (n.equals("honey_block") || n.equals("soul_sand")) {
            return true;
        }
        return faceFull(below, 1) || state(below).equals("snow[layers=8]");
    }

    private static boolean hasDistance(int code) {
        return code < leafDistance.length && leafDistance[code] != 0;
    }

    private static int withDistance(int code, int d) {
        return LEAVES[leafWood[code]][d];
    }

    /** TreeFeature.updateLeaves. */
    static Shape updateLeaves(Region level, int[] box, HashSet<LushCaves.Pos> logs, HashSet<LushCaves.Pos> decorations) {
        List<HashSet<LushCaves.Pos>> list = new ArrayList<>();
        Shape shape = new Shape(box);
        for (int j = 0; j < 6; ++j) {
            list.add(new HashSet<>());
        }
        for (LushCaves.Pos p : new ArrayList<>(decorations)) {
            if (shape.inside(p.x, p.y, p.z)) {
                shape.fill(p.x, p.y, p.z);
            }
        }
        for (LushCaves.Pos p : new ArrayList<>(logs)) {
            if (shape.inside(p.x, p.y, p.z)) {
                shape.fill(p.x, p.y, p.z);
            }
            for (int d = 0; d < 6; d++) {
                LushCaves.Pos n = new LushCaves.Pos(p.x + LushCaves.DX[d], p.y + LushCaves.DY[d], p.z + LushCaves.DZ[d]);
                if (!logs.contains(n)) {
                    int s = level.get(n.x, n.y, n.z);
                    if (hasDistance(s)) {
                        list.get(0).add(n);
                        level.set(n.x, n.y, n.z, withDistance(s, 1));
                        if (shape.inside(n.x, n.y, n.z)) {
                            shape.fill(n.x, n.y, n.z);
                        }
                    }
                }
            }
        }
        for (int l = 1; l < 6; ++l) {
            HashSet<LushCaves.Pos> set = list.get(l - 1);
            HashSet<LushCaves.Pos> set1 = list.get(l);
            for (LushCaves.Pos p : set) {
                if (shape.inside(p.x, p.y, p.z)) {
                    shape.fill(p.x, p.y, p.z);
                }
                for (int d = 0; d < 6; d++) {
                    LushCaves.Pos n = new LushCaves.Pos(p.x + LushCaves.DX[d], p.y + LushCaves.DY[d], p.z + LushCaves.DZ[d]);
                    if (!set.contains(n) && !set1.contains(n)) {
                        int s = level.get(n.x, n.y, n.z);
                        if (hasDistance(s)) {
                            int k = leafDistance[s];
                            if (k > l + 1) {
                                level.set(n.x, n.y, n.z, withDistance(s, l + 1));
                                if (shape.inside(n.x, n.y, n.z)) {
                                    shape.fill(n.x, n.y, n.z);
                                }
                                set1.add(n);
                            }
                        }
                    }
                }
            }
        }
        return shape;
    }
}
