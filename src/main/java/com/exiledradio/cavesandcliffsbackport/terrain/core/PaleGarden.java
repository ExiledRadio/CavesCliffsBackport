package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.*;

/**
 * 1.21.4's pale garden features ({@link Editions#PALE}), as vanilla 1.21.4 places them, draw for draw:
 * <ul>
 *   <li>pale_garden_vegetation: 16 tries of pale_oak_creaking_checked (0.1) or pale_oak_checked, both
 *       wouldSurvive(pale oak sapling). The tree is 1.21.4's TreeFeature with DarkOakTrunkPlacer(6, 2, 1),
 *       DarkOakFoliagePlacer(0, 0), ThreeLayersFeatureSize(1, 1, 0, 1, 2), ignoring vines, decorated by
 *       PaleMossDecorator(0.15, 0.4, 0.8) and, for the creaking one, CreakingHeartDecorator(1.0); 1.21.4's
 *       validTreePos (air or #replaceable_by_trees), waterlogged leaves and box-limited updateLeaves.</li>
 *   <li>pale_moss_patch: VegetationPatchFeature (pale moss over #moss_replaceable, radius 2-4, 0.3 vegetation:
 *       pale moss carpet 25, short grass 25, tall grass 10). A carpet goes in by MossyCarpetBlock.placeAt, whose
 *       topper draws from the WorldGenRegion's own random ({@link #begin}).</li>
 *   <li>pale_garden_flowers and flower_pale_garden: closed eyeblossom patches (96 tries over 7/3, and one).</li>
 * </ul>
 * Its own class rather than Trees' and Vegetation's 1.18.2 code, which these do not share: the 1.21.4 tree
 * differs from 1.18.2's TreeFeature in validTreePos, the leaves and updateLeaves.
 */
final class PaleGarden {
    // ---- block codes (state names as VanillaFeatureDump writes them; leaves without waterlogged=false) ----

    static final int[] LOG = new int[3];
    /** [distance 1..7][waterlogged 0, 1]. */
    static final int[][] LEAVES = new int[8][2];
    static final int MOSS_BLOCK = register("pale_moss_block");
    static final int HANGING_MOSS = register("pale_hanging_moss[tip=false]");
    static final int HANGING_MOSS_TIP = register("pale_hanging_moss[tip=true]");
    static final int CLOSED_EYEBLOSSOM = register("closed_eyeblossom");
    static final int CREAKING_HEART = register("creaking_heart[active=true,axis=y,natural=true]");
    /** Wall sides of MossyCarpetBlock. */
    static final int NONE = 0;
    static final int LOW = 1;
    static final int TALL = 2;
    private static final String[] SIDES = {"none", "low", "tall"};
    /** [bottom 0/1][north][east][south][west] (each NONE, LOW, TALL) as one index, see {@link #carpet}. */
    static final int[] CARPET = new int[2 * 81];

    private static int[] leafDistance;
    private static int[] leafWet;
    private static int[] carpetIndex;

    static {
        String[] axes = {"x", "y", "z"};
        for (int a = 0; a < 3; a++) {
            LOG[a] = register("pale_oak_log[axis=" + axes[a] + "]");
        }
        for (int d = 1; d <= 7; d++) {
            LEAVES[d][0] = register("pale_oak_leaves[distance=" + d + ",persistent=false]");
            LEAVES[d][1] = register("pale_oak_leaves[distance=" + d + ",persistent=false,waterlogged=true]");
        }
        for (int b = 0; b < 2; b++) {
            for (int n = 0; n < 3; n++) {
                for (int e = 0; e < 3; e++) {
                    for (int s = 0; s < 3; s++) {
                        for (int w = 0; w < 3; w++) {
                            CARPET[carpetIdx(b, n, e, s, w)] = register("pale_moss_carpet[bottom=" + (b == 1) + ",east="
                                + SIDES[e] + ",north=" + SIDES[n] + ",south=" + SIDES[s] + ",west=" + SIDES[w] + "]");
                        }
                    }
                }
            }
        }
        leafDistance = new int[count()];
        leafWet = new int[count()];
        carpetIndex = new int[count()];
        Arrays.fill(carpetIndex, -1);
        for (int d = 1; d <= 7; d++) {
            for (int w = 0; w < 2; w++) {
                leafDistance[LEAVES[d][w]] = d;
                leafWet[LEAVES[d][w]] = w;
            }
        }
        for (int i = 0; i < CARPET.length; i++) {
            carpetIndex[CARPET[i]] = i;
        }
    }

    private static int carpetIdx(int bottom, int n, int e, int s, int w) {
        return (((bottom * 3 + n) * 3 + e) * 3 + s) * 3 + w;
    }

    /** The default carpet: bottom, no sides. */
    static final int CARPET_BASE = CARPET[carpetIdx(1, 0, 0, 0, 0)];

    static boolean isCarpet(int code) {
        return code < carpetIndex.length && carpetIndex[code] >= 0;
    }

    private static boolean bottom(int carpet) {
        return carpetIndex[carpet] >= 81;
    }

    /** The carpet's side toward a horizontal Direction ordinal (north 2, south 3, west 4, east 5). */
    private static int side(int carpet, int dir) {
        int i = carpetIndex[carpet] % 81;
        int n = i / 27;
        int e = i / 9 % 3;
        int s = i / 3 % 3;
        int w = i % 3;
        return dir == NORTH ? n : dir == EAST ? e : dir == SOUTH ? s : w;
    }

    private static int withSide(int carpet, int dir, int v) {
        int i = carpetIndex[carpet];
        int b = i / 81;
        int r = i % 81;
        int[] s = {r / 27, r / 9 % 3, r / 3 % 3, r % 3};
        s[dir == NORTH ? 0 : dir == EAST ? 1 : dir == SOUTH ? 2 : 3] = v;
        return CARPET[carpetIdx(b, s[0], s[1], s[2], s[3])];
    }

    private static int withBottom(int carpet, boolean b) {
        int i = carpetIndex[carpet] % 81;
        return CARPET[(b ? 81 : 0) + i];
    }

    static boolean isLeaves(int code) {
        return code < leafDistance.length && leafDistance[code] != 0;
    }

    // ---- directions: Direction ordinals (down, up, north, south, west, east) ----

    private static final int NORTH = 2;
    private static final int SOUTH = 3;
    private static final int WEST = 4;
    private static final int EAST = 5;
    /** Direction.Plane.HORIZONTAL's order: north, east, south, west. */
    private static final int[] HORIZONTAL = {NORTH, EAST, SOUTH, WEST};
    private static final int[] DX = {0, 0, 0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, -1, 1, 0, 0};

    // ---- the region's own random (WorldGenRegion.getRandom) ----

    private final Xoroshiro.Positional regionRandoms;
    private Xoroshiro regionRandom;

    PaleGarden(OverworldNoise noise) {
        // RandomState.getOrCreateRandomFactory(minecraft:worldgen_region_random).
        regionRandoms = noise.positional.fromHashOf("minecraft:worldgen_region_random").forkPositional();
    }

    /** Before a chunk's decoration: WorldGenRegion's random, at the center chunk's world position. */
    void begin(int cx, int cz) {
        regionRandom = regionRandoms.at(cx << 4, 0, cz << 4);
    }

    /** XoroshiroRandomSource.nextBoolean. */
    private boolean regionBoolean() {
        return (regionRandom.nextLong() & 1L) != 0L;
    }

    // ---- placements ----

    List<Placed> placed() {
        Feature paleOak = new Trees.Checked(new Tree(false));
        Feature creaking = new Trees.Checked(new Tree(true));
        Feature eyeblossom = Vegetation.onlyInAir(this::placeEyeblossom);
        return Arrays.asList(
            new Placed("pale_garden_vegetation", new Trees.Selector(new float[]{0.1F, 0.9F}, new Feature[]{creaking, paleOak}, paleOak),
                COUNT, 16, IN_SQUARE, WATER_DEPTH, 0, HEIGHTMAP, Region.OCEAN_FLOOR, BIOME),
            new Placed("pale_moss_patch", this::mossPatch, COUNT, 1, IN_SQUARE, HEIGHTMAP, Region.MOTION_BLOCKING_NO_LEAVES, BIOME),
            new Placed("pale_garden_flowers", Vegetation.patch(96, 7, 3, eyeblossom),
                RARITY, 8, IN_SQUARE, HEIGHTMAP, Region.MOTION_BLOCKING_NO_LEAVES, BIOME),
            new Placed("flower_pale_garden", Vegetation.patch(1, 0, 0, eyeblossom),
                RARITY, 32, IN_SQUARE, HEIGHTMAP, Region.MOTION_BLOCKING, BIOME));
    }

    // ---- tags and block tests (1.21.4) ----

    /** TreeFeature.validTreePos: air or #replaceable_by_trees. */
    static boolean validTreePos(int code) {
        if (isAir(code) || (flags(code) & F_LEAVES) != 0) {
            return true;
        }
        switch (name(code)) {
            case "pale_moss_carpet": case "grass": case "fern": case "dead_bush": case "vine": case "glow_lichen":
            case "sunflower": case "lilac": case "rose_bush": case "peony": case "tall_grass": case "large_fern":
            case "hanging_roots": case "pitcher_plant": case "water": case "seagrass": case "tall_seagrass":
            case "warped_roots": case "nether_sprouts": case "crimson_roots":
            // #small_flowers
            case "dandelion": case "poppy": case "blue_orchid": case "allium": case "azure_bluet": case "red_tulip":
            case "orange_tulip": case "white_tulip": case "pink_tulip": case "oxeye_daisy": case "cornflower":
            case "lily_of_the_valley": case "wither_rose": case "torchflower": case "closed_eyeblossom":
            case "open_eyeblossom":
                return true;
            default:
                return false;
        }
    }

    /** BlockTags.LOGS. */
    static boolean log(int code) {
        String n = name(code);
        return n.endsWith("_log") || n.endsWith("_wood") || n.endsWith("_stem") && !n.equals("mushroom_stem")
            || n.endsWith("_hyphae");
    }

    /** FluidState.isSourceOfType(WATER): a water source, a waterlogged block or a water plant. */
    private static boolean waterSource(int code) {
        return code == WATER || (flags(code) & F_WATER) != 0 && !name(code).equals("water");
    }

    /** BushBlock.mayPlaceOn: #dirt (with the pale moss block) or farmland. */
    private static boolean bushSoil(int code) {
        return LushCaves.dirt(code) || name(code).equals("farmland");
    }

    /** MultifaceBlock.canAttachTo(level, pos, dir): the neighbor's support or collision face toward pos is full. */
    private static boolean canAttachTo(Region level, int x, int y, int z, int dir) {
        return faceFull(level.get(x + DX[dir], y + DY[dir], z + DZ[dir]), dir ^ 1);
    }

    // ---- the tree ----

    final class Tree implements Feature {
        final boolean creakingHeart;
        private HashSet<LushCaves.Pos> logs;
        private HashSet<LushCaves.Pos> foliage;
        private HashSet<LushCaves.Pos> decorations;
        private Region level;

        Tree(boolean creakingHeart) {
            this.creakingHeart = creakingHeart;
        }

        private void trunk(int x, int y, int z, int state) {
            logs.add(new LushCaves.Pos(x, y, z));
            level.set(x, y, z, state);
        }

        private void leaf(int x, int y, int z, int state) {
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
            logs = new HashSet<>();
            foliage = new HashSet<>();
            decorations = new HashSet<>();
            try {
                if (!doPlace(random, x, y, z) || logs.isEmpty() && foliage.isEmpty()) {
                    return false;
                }
                // TreeDecorator.Context: the sets as lists sorted by y (stable, ties in HashSet order).
                List<LushCaves.Pos> logList = new ArrayList<>(logs);
                List<LushCaves.Pos> leafList = new ArrayList<>(foliage);
                Comparator<LushCaves.Pos> byY = Comparator.comparingInt(p -> p.y);
                logList.sort(byY);
                leafList.sort(byY);
                paleMoss(random, logList, leafList);
                if (creakingHeart) {
                    creakingHeart(random, logList);
                }
                int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
                encapsulate(box, logs);
                encapsulate(box, foliage);
                encapsulate(box, decorations);
                Trees.Shape shape = updateLeaves(level, box, logs, decorations);
                shape.updateShapeAtEdge(level);
                return true;
            } finally {
                this.level = null;
            }
        }

        private boolean doPlace(XoroRandom random, int x, int y, int z) {
            // DarkOakTrunkPlacer(6, 2, 1).getTreeHeight; DarkOakFoliagePlacer: height 4, radius and offset constant 0.
            int h = 6 + random.nextInt(3) + random.nextInt(2);
            if (y < -64 + 1 || y + h + 1 > 319 + 1) {
                return false;
            }
            int free = maxFreeTreeHeight(h, x, y, z);
            if (free < h) {
                return false;
            }
            for (int[] a : trunk(random, free, x, y, z)) {
                foliage(random, a[0], a[1], a[2], a[3] != 0);
            }
            return true;
        }

        /** ThreeLayersFeatureSize(1, 1, 0, 1, 2).getSizeAtHeight. */
        private int sizeAt(int h, int i) {
            return i < 1 ? 0 : i >= h - 1 ? 2 : 1;
        }

        private int maxFreeTreeHeight(int h, int x, int y, int z) {
            for (int i = 0; i <= h + 1; ++i) {
                int j = sizeAt(h, i);
                for (int k = -j; k <= j; ++k) {
                    for (int l = -j; l <= j; ++l) {
                        int s = level.get(x + k, y + i, z + l);
                        if (!validTreePos(s) && !log(s)) {
                            return i - 2;
                        }
                    }
                }
            }
            return h;
        }

        /** TrunkPlacer.setDirtAt: dirt unless #dirt already, grass and mycelium excepted (the trunk setter). */
        private void setDirtAt(int x, int y, int z) {
            int s = level.get(x, y, z);
            if (!(LushCaves.dirt(s) && !sameBlock(s, GRASS_BLOCK) && !sameBlock(s, MYCELIUM))) {
                trunk(x, y, z, DIRT);
            }
        }

        private void placeLog(int x, int y, int z) {
            if (validTreePos(level.get(x, y, z))) {
                trunk(x, y, z, LOG[1]);
            }
        }

        /** DarkOakTrunkPlacer.placeTrunk: attachments as {x, y, z, double trunk}. */
        private List<int[]> trunk(XoroRandom random, int h, int x, int y, int z) {
            List<int[]> list = new ArrayList<>();
            setDirtAt(x, y - 1, z);
            setDirtAt(x + 1, y - 1, z);
            setDirtAt(x, y - 1, z + 1);
            setDirtAt(x + 1, y - 1, z + 1);
            int direction = HORIZONTAL[random.nextInt(4)];
            int i = h - random.nextInt(4);
            int j = 2 - random.nextInt(3);
            int tx = x;
            int tz = z;
            int top = y + h - 1;
            for (int k = 0; k < h; ++k) {
                if (k >= i && j > 0) {
                    tx += DX[direction];
                    tz += DZ[direction];
                    --j;
                }
                int ty = y + k;
                int s = level.get(tx, ty, tz);
                if (isAir(s) || (flags(s) & F_LEAVES) != 0) {
                    placeLog(tx, ty, tz);
                    placeLog(tx + 1, ty, tz);
                    placeLog(tx, ty, tz + 1);
                    placeLog(tx + 1, ty, tz + 1);
                }
            }
            list.add(new int[]{tx, top, tz, 1});
            for (int dx = -1; dx <= 2; ++dx) {
                for (int dz = -1; dz <= 2; ++dz) {
                    if ((dx < 0 || dx > 1 || dz < 0 || dz > 1) && random.nextInt(3) <= 0) {
                        int n = random.nextInt(3) + 2;
                        for (int k = 0; k < n; ++k) {
                            placeLog(x + dx, top - k - 1, z + dz);
                        }
                        list.add(new int[]{x + dx, top, z + dz, 0});
                    }
                }
            }
            return list;
        }

        /** DarkOakFoliagePlacer.createFoliage with radius 0 and offset 0. */
        private void foliage(XoroRandom random, int x, int y, int z, boolean doubleTrunk) {
            if (doubleTrunk) {
                leavesRow(x, y, z, 2, -1, true);
                leavesRow(x, y, z, 3, 0, true);
                leavesRow(x, y, z, 2, 1, true);
                if (random.nextBoolean()) {
                    leavesRow(x, y, z, 0, 2, true);
                }
            } else {
                leavesRow(x, y, z, 2, -1, false);
                leavesRow(x, y, z, 1, 0, false);
            }
        }

        private void leavesRow(int x, int y, int z, int r, int dy, boolean doubleTrunk) {
            int i = doubleTrunk ? 1 : 0;
            for (int dx = -r; dx <= r + i; ++dx) {
                for (int dz = -r; dz <= r + i; ++dz) {
                    if (!skipSigned(dx, dy, dz, r, doubleTrunk)) {
                        tryPlaceLeaf(x + dx, y + dy, z + dz);
                    }
                }
            }
        }

        /** DarkOakFoliagePlacer.shouldSkipLocationSigned (no draws). */
        private boolean skipSigned(int dx, int dy, int dz, int r, boolean doubleTrunk) {
            if (!(dy != 0 || !doubleTrunk || dx != -r && dx < r || dz != -r && dz < r)) {
                return true;
            }
            int ax;
            int az;
            if (doubleTrunk) {
                ax = Math.min(Math.abs(dx), Math.abs(dx - 1));
                az = Math.min(Math.abs(dz), Math.abs(dz - 1));
            } else {
                ax = Math.abs(dx);
                az = Math.abs(dz);
            }
            if (dy == -1 && !doubleTrunk) {
                return ax == r && az == r;
            }
            return dy == 1 && ax + az > r * 2 - 2;
        }

        /** FoliagePlacer.tryPlaceLeaf: not over persistent leaves, a valid tree position, waterlogged in a water source. */
        private void tryPlaceLeaf(int x, int y, int z) {
            int s = level.get(x, y, z);
            // No generated leaves are persistent (no code has persistent=true).
            if (!validTreePos(s)) {
                return;
            }
            leaf(x, y, z, LEAVES[7][waterSource(s) ? 1 : 0]);
        }

        /** PaleMossDecorator(0.15, 0.4, 0.8). */
        private void paleMoss(XoroRandom random, List<LushCaves.Pos> logList, List<LushCaves.Pos> leafList) {
            if (logList.isEmpty()) {
                return;
            }
            List<LushCaves.Pos> shuffled = new ArrayList<>(logList);
            shuffle(shuffled, random);
            LushCaves.Pos lowest = shuffled.get(0);
            for (LushCaves.Pos p : shuffled) {
                if (p.y < lowest.y) {
                    lowest = p;
                }
            }
            if (random.nextFloat() < 0.8F && level.canWrite(lowest.x, lowest.z)) {
                mossPatch(level, random, lowest.x, lowest.y + 1, lowest.z);
            }
            for (LushCaves.Pos p : logList) {
                if (random.nextFloat() < 0.4F && isAir(level.get(p.x, p.y - 1, p.z))) {
                    mossHanger(random, p.x, p.y - 1, p.z);
                }
            }
            for (LushCaves.Pos p : leafList) {
                if (random.nextFloat() < 0.15F && isAir(level.get(p.x, p.y - 1, p.z))) {
                    mossHanger(random, p.x, p.y - 1, p.z);
                }
            }
        }

        private void mossHanger(XoroRandom random, int x, int y, int z) {
            while (isAir(level.get(x, y - 1, z)) && !(random.nextFloat() < 0.5F)) {
                decoration(x, y, z, HANGING_MOSS);
                --y;
            }
            decoration(x, y, z, HANGING_MOSS_TIP);
        }

        /** CreakingHeartDecorator(1.0): a shuffled log with logs on all six sides becomes a natural, active heart. */
        private void creakingHeart(XoroRandom random, List<LushCaves.Pos> logList) {
            if (logList.isEmpty() || random.nextFloat() >= 1.0F) {
                return;
            }
            List<LushCaves.Pos> shuffled = new ArrayList<>(logList);
            shuffle(shuffled, random);
            for (LushCaves.Pos p : shuffled) {
                boolean all = true;
                for (int d = 0; d < 6 && all; d++) {
                    all = log(level.get(p.x + DX[d], p.y + DY[d], p.z + DZ[d]));
                }
                if (all) {
                    decoration(p.x, p.y, p.z, CREAKING_HEART);
                    return;
                }
            }
        }
    }

    /** Util.shuffle(list, random). */
    static <T> void shuffle(List<T> list, XoroRandom random) {
        for (int i = list.size(); i > 1; i--) {
            int j = random.nextInt(i);
            list.set(i - 1, list.set(j, list.get(i - 1)));
        }
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

    /** LeavesBlock.getOptionalDistanceAt: a log 0, leaves their distance, anything else -1. */
    private static int optionalDistance(int code) {
        if (log(code)) {
            return 0;
        }
        if (isLeaves(code)) {
            return leafDistance[code];
        }
        // Other trees' leaves (1.18.2's codes) have a distance too.
        String s = state(code);
        int i = s.indexOf("_leaves[distance=");
        return i < 0 ? -1 : s.charAt(i + 17) - '0';
    }

    /** The same leaves with another distance (pale oak's or 1.18.2's codes). */
    private static int withDistance(int code, int d) {
        if (isLeaves(code)) {
            return LEAVES[d][leafWet[code]];
        }
        String s = state(code);
        int i = s.indexOf("_leaves[distance=");
        return byState(s.substring(0, i + 17) + d + s.substring(i + 18));
    }

    /** 1.20's TreeFeature.updateLeaves: a BFS from the logs inside the box only, buckets by distance. */
    static Trees.Shape updateLeaves(Region level, int[] box, HashSet<LushCaves.Pos> logs, HashSet<LushCaves.Pos> decorations) {
        Trees.Shape shape = new Trees.Shape(box);
        List<HashSet<LushCaves.Pos>> buckets = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            buckets.add(new HashSet<>());
        }
        for (LushCaves.Pos p : new ArrayList<>(decorations)) {
            if (shape.inside(p.x, p.y, p.z)) {
                shape.fill(p.x, p.y, p.z);
            }
        }
        int l = 0;
        buckets.get(0).addAll(logs);
        while (true) {
            while (l >= 7 || !buckets.get(l).isEmpty()) {
                if (l >= 7) {
                    return shape;
                }
                Iterator<LushCaves.Pos> it = buckets.get(l).iterator();
                LushCaves.Pos p = it.next();
                it.remove();
                if (shape.inside(p.x, p.y, p.z)) {
                    if (l != 0) {
                        int s = level.get(p.x, p.y, p.z);
                        // BlockState.setValue(DISTANCE): only leaves get here at a distance above 0.
                        int t = withDistance(s, l);
                        if (t >= 0 && t != s) {
                            level.set(p.x, p.y, p.z, t);
                        }
                    }
                    shape.fill(p.x, p.y, p.z);
                    for (int d = 0; d < 6; d++) {
                        int nx = p.x + DX[d];
                        int ny = p.y + DY[d];
                        int nz = p.z + DZ[d];
                        if (shape.inside(nx, ny, nz) && !shape.isFull(nx - shape.minX, ny - shape.minY, nz - shape.minZ)) {
                            int od = optionalDistance(level.get(nx, ny, nz));
                            if (od >= 0) {
                                int k = Math.min(od, l + 1);
                                if (k < 7) {
                                    buckets.get(k).add(new LushCaves.Pos(nx, ny, nz));
                                    l = Math.min(l, k);
                                }
                            }
                        }
                    }
                }
            }
            l++;
        }
    }

    // ---- the pale moss patch (VegetationFeatures.PALE_MOSS_PATCH) ----

    /** VegetationPatchFeature with ConfiguredFeature.place's ensureCanWrite already done by the caller. */
    boolean mossPatch(Region level, XoroRandom random, int x, int y, int z) {
        int xr = random.between(2, 4) + 1;
        int zr = random.between(2, 4) + 1;
        HashSet<LushCaves.Pos> set = new HashSet<>();
        for (int i = -xr; i <= xr; ++i) {
            boolean edgeX = i == -xr || i == xr;
            for (int j = -zr; j <= zr; ++j) {
                boolean edgeZ = j == -zr || j == zr;
                boolean edge = edgeX || edgeZ;
                boolean corner = edgeX && edgeZ;
                boolean side = edge && !corner;
                if (!corner && (!side || !(random.nextFloat() > 0.75F))) {
                    int mx = x + i;
                    int my = y;
                    int mz = z + j;
                    for (int k = 0; isAir(level.get(mx, my, mz)) && k < 5; ++k) {
                        --my;
                    }
                    for (int k = 0; !isAir(level.get(mx, my, mz)) && k < 5; ++k) {
                        ++my;
                    }
                    int gy = my - 1;
                    if (isAir(level.get(mx, my, mz)) && sturdyFace(level.get(mx, gy, mz), 1)) {
                        // depth ConstantInt(1), no extra bottom block: one block of pale moss.
                        int current = level.get(mx, gy, mz);
                        boolean placed;
                        if (current == MOSS_BLOCK) {
                            placed = true;
                        } else if (LushCaves.mossReplaceable(current)) {
                            level.set(mx, gy, mz, MOSS_BLOCK);
                            placed = true;
                        } else {
                            placed = false;
                        }
                        if (placed) {
                            set.add(new LushCaves.Pos(mx, gy, mz));
                        }
                    }
                }
            }
        }
        for (LushCaves.Pos p : set) {
            if (random.nextFloat() < 0.3F) {
                LushCaves.placeInline(this::mossVegetation, level, random, p.x, p.y + 1, p.z);
            }
        }
        return !set.isEmpty();
    }

    /** PALE_MOSS_VEGETATION: SimpleBlockFeature over pale moss carpet 25, short grass 25, tall grass 10. */
    private boolean mossVegetation(Region level, XoroRandom random, int x, int y, int z) {
        int w = random.nextInt(60);
        int below = level.get(x, y - 1, z);
        if (w < 25) {
            // MossyCarpetBlock (bottom): anything but air below.
            if (isAir(below)) {
                return false;
            }
            placeCarpet(level, x, y, z);
            return true;
        }
        if (!bushSoil(below)) {
            return false;
        }
        if (w < 50) {
            level.set(x, y, z, LushCaves.GRASS_PLANT);
            return true;
        }
        if (!isAir(level.get(x, y + 1, z))) {
            return false;
        }
        LushCaves.placeDouble(level, x, y, z, LushCaves.TALL_GRASS_LOWER, LushCaves.TALL_GRASS_UPPER);
        return true;
    }

    /** MossyCarpetBlock.placeAt(level, pos, level.getRandom(), 2). */
    private void placeCarpet(Region level, int x, int y, int z) {
        level.set(x, y, z, updatedState(level, CARPET_BASE, x, y, z, true));
        int above = level.get(x, y + 1, z);
        boolean carpetAbove = isCarpet(above);
        if ((!carpetAbove || !bottom(above)) && (carpetAbove || replaceable(above))) {
            int t = updatedState(level, withBottom(CARPET_BASE, false), x, y + 1, z, true);
            for (int d : HORIZONTAL) {
                if (side(t, d) != NONE && !regionBoolean()) {
                    t = withSide(t, d, NONE);
                }
            }
            if (hasFaces(t) && t != above) {
                level.set(x, y + 1, z, t);
            }
        }
    }

    private static boolean hasFaces(int carpet) {
        if (bottom(carpet)) {
            return true;
        }
        for (int d : HORIZONTAL) {
            if (side(carpet, d) != NONE) {
                return true;
            }
        }
        return false;
    }

    /** MossyCarpetBlock.getUpdatedState. */
    static int updatedState(Region level, int carpet, int x, int y, int z, boolean placing) {
        placing |= bottom(carpet);
        int above = -1;
        int below = -1;
        for (int d : HORIZONTAL) {
            int v = canAttachTo(level, x, y, z, d) ? (placing ? LOW : side(carpet, d)) : NONE;
            if (v == LOW) {
                if (above < 0) {
                    above = level.get(x, y + 1, z);
                }
                if (isCarpet(above) && side(above, d) != NONE && !bottom(above)) {
                    v = TALL;
                }
                if (!bottom(carpet)) {
                    if (below < 0) {
                        below = level.get(x, y - 1, z);
                    }
                    if (isCarpet(below) && side(below, d) == NONE) {
                        v = NONE;
                    }
                }
            }
            carpet = withSide(carpet, d, v);
        }
        return carpet;
    }

    // ---- eyeblossoms ----

    /** SimpleBlockFeature(closed eyeblossom, schedule tick): the bush test, then the block (the tick is 1.12's). */
    private boolean placeEyeblossom(Region level, XoroRandom random, int x, int y, int z) {
        if (!bushSoil(level.get(x, y - 1, z))) {
            return false;
        }
        level.set(x, y, z, CLOSED_EYEBLOSSOM);
        return true;
    }

    // ---- Block.updateShape (StructureTemplate.updateShapeAtEdge after a tree) ----

    /**
     * The pale garden blocks' updateShape, or -1 for any other block: MossyCarpetBlock (gone without support or
     * faces, its sides updated), HangingMossBlock (tip unless more moss hangs below), the eyeblossom (a bush).
     */
    static int updateShape(Region level, int state, int dir, int neighbor, int x, int y, int z) {
        if (isCarpet(state)) {
            int below = level.get(x, y - 1, z);
            boolean survives = bottom(state) ? !isAir(below) : isCarpet(below) && bottom(below);
            if (!survives) {
                return AIR;
            }
            int s = updatedState(level, state, x, y, z, false);
            return hasFaces(s) ? s : AIR;
        }
        if (state == HANGING_MOSS || state == HANGING_MOSS_TIP) {
            int below = level.get(x, y - 1, z);
            return below == HANGING_MOSS || below == HANGING_MOSS_TIP ? HANGING_MOSS : HANGING_MOSS_TIP;
        }
        if (state == CLOSED_EYEBLOSSOM) {
            return bushSoil(level.get(x, y - 1, z)) ? state : AIR;
        }
        return -1;
    }
}
