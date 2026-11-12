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
 * placements of VegetationPlacements / TreePlacements). So far: straight trunks with blob, spruce and pine foliage,
 * two-layer feature sizes and beehives (trees_snowy, trees_badlands, trees_birch, trees_taiga, trees_grove,
 * birch_tall).
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
    /** leaves[wood][distance 1..7] (persistent=false). */
    static final int[][] LEAVES = new int[WOODS.length][8];
    static final int BEE_NEST = register("bee_nest[facing=south,honey_level=0]");
    /** Per code: the leaf distance (1..7) or 0; and the code with another distance. */
    private static int[] leafDistance;
    private static int[] leafWood;

    static {
        String[] axes = {"x", "y", "z"};
        for (int w = 0; w < WOODS.length; w++) {
            for (int a = 0; a < 3; a++) {
                LOG[w][a] = register(WOODS[w] + "_log[axis=" + axes[a] + "]");
            }
            for (int d = 1; d <= 7; d++) {
                LEAVES[w][d] = register(WOODS[w] + "_leaves[distance=" + d + ",persistent=false]");
            }
        }
        leafDistance = new int[count()];
        leafWood = new int[count()];
        for (int w = 0; w < WOODS.length; w++) {
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
        Modifier sapling = (level, random, next, x, y, z) -> {
            if (saplingSurvives(level, x, y, z)) {
                next.at(x, y, z);
            }
        };
        int of = Region.OCEAN_FLOOR;
        return Arrays.asList(
            new Placed("trees_snowy", spruce, COUNT_EXTRA, 0, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME, CUSTOM, 0).mods(sapling),
            new Placed("trees_badlands", oak, COUNT_EXTRA, 5, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME, CUSTOM, 0).mods(sapling),
            new Placed("trees_birch", birchBees0002, COUNT_EXTRA, 10, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME, CUSTOM, 0).mods(sapling),
            new Placed("trees_taiga", new Selector(new float[]{0.33333334F}, new Feature[]{pineChecked}, spruceChecked),
                COUNT_EXTRA, 10, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("birch_tall", new Selector(new float[]{0.5F}, new Feature[]{superBirchBees0002Checked}, birchBees0002Checked),
                COUNT_EXTRA, 10, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME),
            new Placed("trees_grove", new Selector(new float[]{0.33333334F}, new Feature[]{new OnSnow(pine)}, new OnSnow(spruce)),
                COUNT_EXTRA, 10, 10, 1, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, of, BIOME));
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
                if (beehive >= 0.0F) {
                    List<LushCaves.Pos> logs = new ArrayList<>(trunk);
                    List<LushCaves.Pos> leafList = new ArrayList<>(foliage);
                    Comparator<LushCaves.Pos> byY = Comparator.comparingInt(p -> p.y);
                    logs.sort(byY);
                    leafList.sort(byY);
                    beehive(random, logs, leafList);
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
                if (i1 >= i) {
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
                int j = i < sizeLimit ? sizeLower : sizeUpper;
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
            if (!dirtNotGrass(level.get(x, y, z))) {
                trunk(x, y, z, DIRT);
            }
        }

        private void placeLog(int x, int y, int z) {
            if (validTreePos(level.get(x, y, z))) {
                trunk(x, y, z, log);
            }
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
            // spruce, pine
            return dx == r && dz == r && r > 0;
        }

        private void leavesRow(XoroRandom random, int ax, int ay, int az, int r, int dy, boolean doubleTrunk) {
            int i = doubleTrunk ? 1 : 0;
            for (int j = -r; j <= r + i; ++j) {
                for (int k = -r; k <= r + i; ++k) {
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
                            foliage(x, y, z, leaves);
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
        return state;
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
