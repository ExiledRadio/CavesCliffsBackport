package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.*;

/**
 * Lush caves' vegetation (CavePlacements / CaveFeatures: lush_caves_ceiling_vegetation, cave_vines, lush_caves_clay,
 * lush_caves_vegetation, spore_blossom, classic_vines_cave_feature) and glow lichen (every overworld biome) as 1.18.2
 * places them. rooted_azalea_tree waits for the tree features.
 */
final class LushCaves {
    private LushCaves() {
    }

    static final int MOSS_BLOCK = register("moss_block");
    static final int MOSS_CARPET = register("moss_carpet");
    static final int AZALEA = register("azalea");
    static final int FLOWERING_AZALEA = register("flowering_azalea");
    static final int GRASS_PLANT = register("grass");
    static final int TALL_GRASS_LOWER = register("tall_grass[half=lower]");
    static final int TALL_GRASS_UPPER = register("tall_grass[half=upper]");
    static final int SPORE_BLOSSOM = register("spore_blossom");
    /** cave_vines[age][berries], age 23..25. */
    static final int[][] CAVE_VINES = new int[26][2];
    /** cave_vines_plant[berries]. */
    static final int[] CAVE_VINES_PLANT = {register("cave_vines_plant[berries=false]"), register("cave_vines_plant[berries=true]")};
    /** Horizontal facings in Direction 2D data order: south, west, north, east. */
    private static final String[] FACING = {"south", "west", "north", "east"};
    /** small_dripleaf[facing][upper][waterlogged]. */
    static final int[][][] SMALL_DRIPLEAF = new int[4][2][2];
    /** big_dripleaf[facing][waterlogged] (tilt none), big_dripleaf_stem[facing][waterlogged]. */
    static final int[][] BIG_DRIPLEAF = new int[4][2];
    static final int[][] BIG_DRIPLEAF_STEM = new int[4][2];
    /** glow_lichen by face mask (bit = Direction ordinal: down, up, north, south, west, east) and waterlogged. */
    static final int[][] LICHEN = new int[64][2];
    /** vine by face mask over up, north, south, west, east (bit = Direction ordinal). */
    static final int[] VINE = new int[64];
    private static final int[] LICHEN_MASK;
    private static final int[] LICHEN_WET;

    static {
        for (int a = 23; a <= 25; a++) {
            for (int b = 0; b < 2; b++) {
                CAVE_VINES[a][b] = register("cave_vines[age=" + a + ",berries=" + (b == 1) + "]");
            }
        }
        for (int f = 0; f < 4; f++) {
            for (int w = 0; w < 2; w++) {
                for (int u = 0; u < 2; u++) {
                    SMALL_DRIPLEAF[f][u][w] = register("small_dripleaf[facing=" + FACING[f] + ",half=" + (u == 1 ? "upper" : "lower")
                        + ",waterlogged=" + (w == 1) + "]");
                }
                BIG_DRIPLEAF[f][w] = register("big_dripleaf[facing=" + FACING[f] + ",tilt=none,waterlogged=" + (w == 1) + "]");
                BIG_DRIPLEAF_STEM[f][w] = register("big_dripleaf_stem[facing=" + FACING[f] + ",waterlogged=" + (w == 1) + "]");
            }
        }
        for (int m = 0; m < 64; m++) {
            for (int w = 0; w < 2; w++) {
                LICHEN[m][w] = register("glow_lichen[down=" + bit(m, 0) + ",east=" + bit(m, 5) + ",north=" + bit(m, 2)
                    + ",south=" + bit(m, 3) + ",up=" + bit(m, 1) + ",waterlogged=" + (w == 1) + ",west=" + bit(m, 4) + "]");
            }
            if ((m & 1) == 0) {
                VINE[m] = register("vine[east=" + bit(m, 5) + ",north=" + bit(m, 2) + ",south=" + bit(m, 3) + ",up=" + bit(m, 1)
                    + ",west=" + bit(m, 4) + "]");
            }
        }
        LICHEN_MASK = new int[count()];
        LICHEN_WET = new int[count()];
        Arrays.fill(LICHEN_MASK, -1);
        for (int m = 0; m < 64; m++) {
            for (int w = 0; w < 2; w++) {
                LICHEN_MASK[LICHEN[m][w]] = m;
                LICHEN_WET[LICHEN[m][w]] = w;
            }
        }
    }

    private static boolean bit(int m, int b) {
        return (m >> b & 1) != 0;
    }

    static final int[] DX = Geodes.DX;
    static final int[] DY = Geodes.DY;
    static final int[] DZ = Geodes.DZ;

    static List<Placed> placed() {
        int[] scanUp = {IN_SQUARE, HEIGHT_UNIFORM, -64, 256, CUSTOM, 0, BIOME};
        List<Placed> list = new ArrayList<>();
        list.add(new Placed("lush_caves_ceiling_vegetation", new Patch(MOSS_BLOCK, true, false, 0, 1, 2, 0.0F, 5, 0.08F, 0.3F,
            LushCaves::mossReplaceable, new Column(true, DOWN_DIR, LushCaves::onlyAir, 6, new int[]{5, 0, 3, 1, 1, 7})),
            ops(Decorator.COUNT, 125, scanUp)).mods(scan(1, false, -1)));
        list.add(new Placed("cave_vines", new Column(true, DOWN_DIR, LushCaves::onlyAir, 15, new int[]{2, 0, 19, 3, 0, 2, 10, 0, 6}),
            ops(Decorator.COUNT, 188, scanUp)).mods(scan(1, true, -1)));
        Feature dripleaf = new DripleafSelector();
        list.add(new Placed("lush_caves_clay", new RandomBoolean(
            new Patch(CLAY, false, false, 3, 3, 3, 0.8F, 2, 0.05F, 0.7F, LushCaves::lushGroundReplaceable, dripleaf),
            new Patch(CLAY, false, true, 3, 3, 3, 0.8F, 5, 0.1F, 0.7F, LushCaves::lushGroundReplaceable, dripleaf)),
            ops(Decorator.COUNT, 62, scanUp)).mods(scan(-1, false, 1)));
        list.add(new Placed("lush_caves_vegetation", new Patch(MOSS_BLOCK, false, false, 1, 1, 1, 0.0F, 5, 0.8F, 0.3F,
            LushCaves::mossReplaceable, new MossVegetation()),
            ops(Decorator.COUNT, 125, scanUp)).mods(scan(-1, false, 1)));
        list.add(new Placed("spore_blossom", new SporeBlossom(), ops(Decorator.COUNT, 25, scanUp)).mods(scan(1, false, -1)));
        list.add(new Placed("classic_vines_cave_feature", new Vines(),
            Decorator.COUNT, 256, IN_SQUARE, HEIGHT_UNIFORM, -64, 256, BIOME));
        list.add(new Placed("glow_lichen", new GlowLichen(),
            COUNT_UNIFORM, 104, 157, HEIGHT_UNIFORM, -64, 256, IN_SQUARE, SURFACE_THRESHOLD_OCEAN_FLOOR, Integer.MIN_VALUE, -13, BIOME));
        return list;
    }

    private static int[] ops(int op, int n, int[] rest) {
        int[] a = new int[rest.length + 2];
        a[0] = op;
        a[1] = n;
        System.arraycopy(rest, 0, a, 2, rest.length);
        return a;
    }

    // ---- placement: EnvironmentScanPlacement(dir, target, ONLY_IN_AIR, 12), RandomOffsetPlacement.vertical(offset) ----

    /** dy 1 scans up, -1 down; target solid, or hasSturdyFace(DOWN) when sturdyDown. */
    private static Modifier scan(int dy, boolean sturdyDown, int offset) {
        return (level, random, next, x, y, z) -> {
            if (level.get(x, y, z) != AIR) {
                return;
            }
            for (int i = 0; i < 12; ) {
                if (target(level.get(x, y, z), sturdyDown)) {
                    next.at(x, y + offset, z);
                    return;
                }
                y += dy;
                if (Region.outsideBuildHeight(y)) {
                    return;
                }
                if (level.get(x, y, z) == AIR) {
                    ++i;
                    continue;
                }
                break;
            }
            if (target(level.get(x, y, z), sturdyDown)) {
                next.at(x, y + offset, z);
            }
        };
    }

    private static boolean target(int code, boolean sturdyDown) {
        return sturdyDown ? faceFull(code, 0) : solid(code);
    }

    // ---- tags ----

    static boolean dirt(int code) {
        return code == DIRT || code == GRASS_BLOCK || code == PODZOL || code == COARSE_DIRT || code == MYCELIUM
            || code == MOSS_BLOCK || name(code).equals("rooted_dirt");
    }

    static boolean caveVines(int code) {
        String n = name(code);
        return n.equals("cave_vines") || n.equals("cave_vines_plant");
    }

    static boolean mossReplaceable(int code) {
        return Dripstone.baseStoneOverworld(code) || caveVines(code) || dirt(code);
    }

    static boolean lushGroundReplaceable(int code) {
        return mossReplaceable(code) || code == CLAY || code == GRAVEL || code == SAND;
    }

    private static boolean onlyAir(int code) {
        return code == AIR;
    }

    private static boolean airOrWater(int code) {
        return code == AIR || code == WATER;
    }

    private static boolean isWater(int code) {
        return (flags(code) & F_WATER) != 0;
    }

    interface Test {
        boolean test(int code);
    }

    /** An inline PlacedFeature without modifiers: ConfiguredFeature.place's ensureCanWrite, then the feature. */
    static boolean placeInline(Feature f, Region level, XoroRandom random, int x, int y, int z) {
        return level.canWrite(x, z) && f.place(level, random, x, y, z);
    }

    // ---- VegetationPatchFeature / WaterloggedVegetationPatchFeature ----

    /** BlockPos as a HashSet key: Vec3i's hashCode, so the set iterates in vanilla's order. */
    static final class Pos {
        final int x;
        final int y;
        final int z;

        Pos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int hashCode() {
            return (y + z * 31) * 31 + x;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pos)) {
                return false;
            }
            Pos p = (Pos) o;
            return p.x == x && p.y == y && p.z == z;
        }
    }

    static final int DOWN_DIR = 0;
    static final int UP_DIR = 1;

    static final class Patch implements Feature {
        final int ground;
        final boolean ceiling;
        final boolean waterlogged;
        final int depthConstant;
        final int depthMin;
        final int depthMax;
        final float extraBottomBlockChance;
        final int verticalRange;
        final float vegetationChance;
        final float extraEdgeColumnChance;
        final Test replaceable;
        final Feature vegetation;

        /** depthConstant > 0: ConstantInt; else UniformInt(depthMin, depthMax). xzRadius is UniformInt(4, 7). */
        Patch(int ground, boolean ceiling, boolean waterlogged, int depthConstant, int depthMin, int depthMax,
              float extraBottomBlockChance, int verticalRange, float vegetationChance, float extraEdgeColumnChance,
              Test replaceable, Feature vegetation) {
            this.ground = ground;
            this.ceiling = ceiling;
            this.waterlogged = waterlogged;
            this.depthConstant = depthConstant;
            this.depthMin = depthMin;
            this.depthMax = depthMax;
            this.extraBottomBlockChance = extraBottomBlockChance;
            this.verticalRange = verticalRange;
            this.vegetationChance = vegetationChance;
            this.extraEdgeColumnChance = extraEdgeColumnChance;
            this.replaceable = replaceable;
            this.vegetation = vegetation;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int i = random.between(4, 7) + 1;
            int j = random.between(4, 7) + 1;
            HashSet<Pos> set = placeGroundPatch(level, random, x, y, z, i, j);
            if (waterlogged) {
                HashSet<Pos> set1 = new HashSet<>();
                for (Pos p : set) {
                    if (!exposed(level, p)) {
                        set1.add(p);
                    }
                }
                for (Pos p : set1) {
                    level.set(p.x, p.y, p.z, WATER);
                }
                set = set1;
            }
            int dy = ceiling ? -1 : 1;
            for (Pos p : set) {
                if (vegetationChance > 0.0F && random.nextFloat() < vegetationChance) {
                    if (waterlogged) {
                        // super.placeVegetation at pos.below(): the feature at pos, then the bottom block waterlogged.
                        if (placeInline(vegetation, level, random, p.x, p.y, p.z)) {
                            int s = level.get(p.x, p.y, p.z);
                            int wet = waterloggedVariant(s);
                            if (wet >= 0) {
                                level.set(p.x, p.y, p.z, wet);
                            }
                        }
                    } else {
                        placeInline(vegetation, level, random, p.x, p.y + dy, p.z);
                    }
                }
            }
            return !set.isEmpty();
        }

        private static boolean exposed(Region level, Pos p) {
            return !sturdy(level.get(p.x, p.y, p.z - 1)) || !sturdy(level.get(p.x + 1, p.y, p.z))
                || !sturdy(level.get(p.x, p.y, p.z + 1)) || !sturdy(level.get(p.x - 1, p.y, p.z))
                || !faceFull(level.get(p.x, p.y - 1, p.z), 1);
        }

        private HashSet<Pos> placeGroundPatch(Region level, XoroRandom random, int x, int y, int z, int xr, int zr) {
            int d = ceiling ? 1 : -1;
            HashSet<Pos> set = new HashSet<>();
            for (int i = -xr; i <= xr; ++i) {
                boolean flag = i == -xr || i == xr;
                for (int j = -zr; j <= zr; ++j) {
                    boolean flag1 = j == -zr || j == zr;
                    boolean flag2 = flag || flag1;
                    boolean flag3 = flag && flag1;
                    boolean flag4 = flag2 && !flag3;
                    if (!flag3 && (!flag4 || extraEdgeColumnChance != 0.0F && !(random.nextFloat() > extraEdgeColumnChance))) {
                        int mx = x + i;
                        int my = y;
                        int mz = z + j;
                        for (int k = 0; isAir(level.get(mx, my, mz)) && k < verticalRange; ++k) {
                            my += d;
                        }
                        for (int i1 = 0; !isAir(level.get(mx, my, mz)) && i1 < verticalRange; ++i1) {
                            my -= d;
                        }
                        int gy = my + d;
                        if (isAir(level.get(mx, my, mz)) && faceFull(level.get(mx, gy, mz), ceiling ? 0 : 1)) {
                            int l = (depthConstant > 0 ? depthConstant : random.between(depthMin, depthMax))
                                + (extraBottomBlockChance > 0.0F && random.nextFloat() < extraBottomBlockChance ? 1 : 0);
                            if (placeGround(level, mx, gy, mz, l, d)) {
                                set.add(new Pos(mx, gy, mz));
                            }
                        }
                    }
                }
            }
            return set;
        }

        private boolean placeGround(Region level, int x, int y, int z, int n, int d) {
            for (int i = 0; i < n; ++i) {
                int current = level.get(x, y, z);
                if (current != ground) {
                    if (!replaceable.test(current)) {
                        return i != 0;
                    }
                    level.set(x, y, z, ground);
                    y += d;
                }
            }
            return true;
        }
    }

    /** The same state with waterlogged=true, if it has the property and it is false; else -1. */
    static int waterloggedVariant(int code) {
        String s = state(code);
        if (!s.contains("waterlogged=false")) {
            return -1;
        }
        return byState(s.replace("waterlogged=false", "waterlogged=true"));
    }

    // ---- selectors ----

    static final class RandomBoolean implements Feature {
        final Feature whenTrue;
        final Feature whenFalse;

        RandomBoolean(Feature whenTrue, Feature whenFalse) {
            this.whenTrue = whenTrue;
            this.whenFalse = whenFalse;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            boolean flag = random.nextBoolean();
            return placeInline(flag ? whenTrue : whenFalse, level, random, x, y, z);
        }
    }

    /** DRIPLEAF: small dripleaf, then big dripleaf columns facing east, west, south, north. */
    static final class DripleafSelector implements Feature {
        // Direction 2D data values: south 0, west 1, north 2, east 3.
        private final Feature[] options = {new SmallDripleaf(), new BigDripleaf(3), new BigDripleaf(1), new BigDripleaf(0),
            new BigDripleaf(2)};

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            return placeInline(options[random.nextInt(5)], level, random, x, y, z);
        }
    }

    // ---- BlockColumnFeature ----

    /**
     * Cave vines: a body layer (WeightedListInt over UniformInts: weight, min, max triples) of cave_vines_plant
     * (4:1 without and with berries), then one cave_vines head (4:1, age 23..25).
     */
    static final class Column implements Feature {
        final boolean prioritizeTip;
        final int direction;
        final Test allowed;
        final int totalWeight;
        final int[] bodyHeights;

        Column(boolean prioritizeTip, int direction, Test allowed, int totalWeight, int[] bodyHeights) {
            this.prioritizeTip = prioritizeTip;
            this.direction = direction;
            this.allowed = allowed;
            this.totalWeight = totalWeight;
            this.bodyHeights = bodyHeights;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int[] aint = {weightedUniform(random, totalWeight, bodyHeights), 1};
            return placeColumn(level, random, x, y, z, aint, DY[direction], prioritizeTip, allowed,
                (r, l) -> l == 0 ? CAVE_VINES_PLANT[r.nextInt(5) < 4 ? 0 : 1] : vinesHead(r));
        }

        private static int vinesHead(XoroRandom random) {
            int berries = random.nextInt(5) < 4 ? 0 : 1;
            return CAVE_VINES[random.between(23, 25)][berries];
        }
    }

    /** WeightedListInt over UniformInts (weight, min, max triples): the entry by nextInt(total), then its draw. */
    static int weightedUniform(XoroRandom random, int total, int[] entries) {
        int i = random.nextInt(total);
        for (int e = 0; e < entries.length; e += 3) {
            i -= entries[e];
            if (i < 0) {
                return random.between(entries[e + 1], entries[e + 2]);
            }
        }
        throw new IllegalStateException();
    }

    interface LayerState {
        int state(XoroRandom random, int layer);
    }

    /** BlockColumnFeature.place after the layer heights were drawn. */
    static boolean placeColumn(Region level, XoroRandom random, int x, int y, int z, int[] aint, int dy,
                               boolean prioritizeTip, Test allowed, LayerState states) {
        int j = 0;
        for (int h : aint) {
            j += h;
        }
        if (j == 0) {
            return false;
        }
        // The allowed test starts one past the origin; placing starts at the origin.
        int ty = y + dy;
        for (int l = 0; l < j; ++l) {
            if (!allowed.test(level.get(x, ty, z))) {
                truncate(aint, j, l, prioritizeTip);
                break;
            }
            ty += dy;
        }
        int py = y;
        for (int k1 = 0; k1 < aint.length; ++k1) {
            for (int j1 = 0; j1 < aint[k1]; ++j1) {
                level.set(x, py, z, states.state(random, k1));
                py += dy;
            }
        }
        return true;
    }

    private static void truncate(int[] a, int total, int at, boolean prioritizeTip) {
        int i = total - at;
        int j = prioritizeTip ? 1 : -1;
        int k = prioritizeTip ? 0 : a.length - 1;
        int l = prioritizeTip ? a.length : -1;
        for (int i1 = k; i1 != l && i > 0; i1 += j) {
            int k1 = Math.min(a[i1], i);
            i -= k1;
            a[i1] -= k1;
        }
    }

    /** makeDripleaf(facing): stem WeightedListInt(UniformInt(0, 4) weight 2, 0 weight 1), one leaf, up through air or water. */
    static final class BigDripleaf implements Feature {
        final int facing;

        BigDripleaf(int facing) {
            this.facing = facing;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int stem = random.nextInt(3) < 2 ? random.between(0, 4) : 0;
            int[] aint = {stem, 1};
            return placeColumn(level, random, x, y, z, aint, 1, true, LushCaves::airOrWater,
                (r, l) -> l == 0 ? BIG_DRIPLEAF_STEM[facing][0] : BIG_DRIPLEAF[facing][0]);
        }
    }

    // ---- SimpleBlockFeature ----

    /** makeSmallDripleaf: facing east, west, north, south (weight 1 each), placed as a double plant. */
    static final class SmallDripleaf implements Feature {
        private static final int[] FACINGS = {3, 1, 2, 0};

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int f = FACINGS[random.nextInt(4)];
            // SmallDripleafBlock.canSurvive (lower half): mayPlaceOn the block below.
            int below = level.get(x, y - 1, z);
            boolean ok = below == CLAY || below == MOSS_BLOCK
                || isWaterSource(level.get(x, y, z)) && (dirt(below) || name(below).equals("farmland"));
            if (!ok) {
                return false;
            }
            if (!isAir(level.get(x, y + 1, z))) {
                return false;
            }
            placeDouble(level, x, y, z, SMALL_DRIPLEAF[f][0][0], SMALL_DRIPLEAF[f][1][0]);
            return true;
        }
    }

    private static boolean isWaterSource(int code) {
        return isWater(code);
    }

    /** DoublePlantBlock.placeAt: lower, then upper, each waterlogged from the water there. */
    static void placeDouble(Region level, int x, int y, int z, int lower, int upper) {
        level.set(x, y, z, copyWaterlogged(level, x, y, z, lower));
        level.set(x, y + 1, z, copyWaterlogged(level, x, y + 1, z, upper));
    }

    static int copyWaterlogged(Region level, int x, int y, int z, int code) {
        if (!state(code).contains("waterlogged=")) {
            return code;
        }
        boolean water = isWater(level.get(x, y, z));
        String s = state(code);
        return byState(water ? s.replace("waterlogged=false", "waterlogged=true") : s.replace("waterlogged=true", "waterlogged=false"));
    }

    /** MOSS_VEGETATION: flowering azalea 4, azalea 7, moss carpet 25, grass 50, tall grass 10. */
    static final class MossVegetation implements Feature {
        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int i = random.nextInt(96);
            int below = level.get(x, y - 1, z);
            if (i < 4 || i < 11) {
                // AzaleaBlock.mayPlaceOn: clay, or BushBlock's dirt / farmland.
                if (!(below == CLAY || dirt(below) || name(below).equals("farmland"))) {
                    return false;
                }
                level.set(x, y, z, i < 4 ? FLOWERING_AZALEA : AZALEA);
                return true;
            }
            if (i < 36) {
                if (isAir(below)) {
                    return false;
                }
                level.set(x, y, z, MOSS_CARPET);
                return true;
            }
            if (!(dirt(below) || name(below).equals("farmland"))) {
                return false;
            }
            if (i < 86) {
                level.set(x, y, z, GRASS_PLANT);
                return true;
            }
            if (!isAir(level.get(x, y + 1, z))) {
                return false;
            }
            placeDouble(level, x, y, z, TALL_GRASS_LOWER, TALL_GRASS_UPPER);
            return true;
        }
    }

    static final class SporeBlossom implements Feature {
        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            if (!faceFull(level.get(x, y + 1, z), 0) || isWater(level.get(x, y, z))) {
                return false;
            }
            level.set(x, y, z, SPORE_BLOSSOM);
            return true;
        }
    }

    /** VinesFeature: the first of up, north, south, west, east with a full face toward it. */
    static final class Vines implements Feature {
        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            if (!isAir(level.get(x, y, z))) {
                return false;
            }
            for (int d = 1; d < 6; d++) {
                if (faceFull(level.get(x + DX[d], y + DY[d], z + DZ[d]), d ^ 1)) {
                    level.set(x, y, z, VINE[1 << d]);
                    return true;
                }
            }
            return false;
        }
    }

    // ---- GlowLichenFeature, MultifaceBlock ----

    /**
     * MultifaceBlock.DIRECTIONS: a static array that spreadFromFaceTowardRandomDirection shuffles in place
     * (Collections.shuffle over Arrays.asList), so every spread starts from the order the previous one left, for
     * the life of the JVM. Kept the same way here: process-global, mutated by every lichen spread.
     */
    static final int[] DIRECTIONS = {0, 1, 2, 3, 4, 5};

    /** GLOW_LICHEN's valid directions: ceiling (up), then the walls (north, east, south, west). */
    private static final int[] VALID = {1, 2, 5, 3, 4};

    static void shuffle(int[] list, int size, XoroRandom random) {
        for (int i = size; i > 1; i--) {
            int j = random.nextInt(i);
            int t = list[i - 1];
            list[i - 1] = list[j];
            list[j] = t;
        }
    }

    static boolean canBePlacedOn(int code) {
        return code == STONE || code == ANDESITE || code == DIORITE || code == GRANITE || code == Dripstone.DRIPSTONE_BLOCK
            || code == CALCITE || code == TUFF || code == DEEPSLATE;
    }

    static int lichenMask(int code) {
        return code < LICHEN_MASK.length ? LICHEN_MASK[code] : -1;
    }

    /** MultifaceBlock.getStateForPlacement for glow lichen; -1 for null. */
    static int stateForPlacement(Region level, int state, int x, int y, int z, int dir) {
        int mask;
        int wet;
        int m = lichenMask(state);
        if (m >= 0) {
            if ((m >> dir & 1) != 0) {
                return -1;
            }
            mask = m;
            wet = LICHEN_WET[state];
        } else {
            mask = 0;
            wet = isWater(state) ? 1 : 0;
        }
        if (!faceFull(level.get(x + DX[dir], y + DY[dir], z + DZ[dir]), dir ^ 1)) {
            return -1;
        }
        return LICHEN[mask | 1 << dir][wet];
    }

    static final class GlowLichen implements Feature {
        private final int[] list = new int[5];
        private final int[] list1 = new int[5];

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int origin = level.get(x, y, z);
            if (!(isAir(origin) || origin == WATER)) {
                return false;
            }
            System.arraycopy(VALID, 0, list, 0, 5);
            shuffle(list, 5, random);
            if (placeIfPossible(level, x, y, z, origin, random, list, 5)) {
                return true;
            }
            for (int n = 0; n < 5; n++) {
                int d = list[n];
                int m = 0;
                for (int v : VALID) {
                    if (v != (d ^ 1)) {
                        list1[m++] = v;
                    }
                }
                shuffle(list1, m, random);
                // The search loop sets the same neighbor (origin + d) each of its 20 rounds, and a failed attempt
                // changes nothing and draws nothing, so one round decides.
                int bx = x + DX[d];
                int by = y + DY[d];
                int bz = z + DZ[d];
                int s = level.get(bx, by, bz);
                if (!(isAir(s) || s == WATER) && lichenMask(s) < 0) {
                    continue;
                }
                if (placeIfPossible(level, bx, by, bz, s, random, list1, m)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean placeIfPossible(Region level, int x, int y, int z, int state, XoroRandom random, int[] dirs, int n) {
            for (int i = 0; i < n; i++) {
                int d = dirs[i];
                if (canBePlacedOn(level.get(x + DX[d], y + DY[d], z + DZ[d]))) {
                    int s1 = stateForPlacement(level, state, x, y, z, d);
                    if (s1 < 0) {
                        return false;
                    }
                    level.set(x, y, z, s1);
                    if (random.nextFloat() < 0.5F) {
                        spreadFromFaceTowardRandomDirection(level, s1, x, y, z, d, random);
                    }
                    return true;
                }
            }
            return false;
        }
    }

    static boolean spreadFromFaceTowardRandomDirection(Region level, int state, int x, int y, int z, int face, XoroRandom random) {
        shuffle(DIRECTIONS, 6, random);
        for (int i = 0; i < 6; i++) {
            if (spreadFromFaceTowardDirection(level, state, x, y, z, face, DIRECTIONS[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean spreadFromFaceTowardDirection(Region level, int state, int x, int y, int z, int face, int d) {
        int m = lichenMask(state);
        if ((d >> 1) == (face >> 1) || (m >> face & 1) == 0 || (m >> d & 1) != 0) {
            return false;
        }
        if (canSpreadToFace(level, x, y, z, d)) {
            return spreadToFace(level, x, y, z, d);
        }
        int bx = x + DX[d];
        int by = y + DY[d];
        int bz = z + DZ[d];
        if (canSpreadToFace(level, bx, by, bz, face)) {
            return spreadToFace(level, bx, by, bz, face);
        }
        int cx = bx + DX[face];
        int cy = by + DY[face];
        int cz = bz + DZ[face];
        int o = d ^ 1;
        return canSpreadToFace(level, cx, cy, cz, o) && spreadToFace(level, cx, cy, cz, o);
    }

    private static boolean canSpreadToFace(Region level, int x, int y, int z, int d) {
        int s = level.get(x, y, z);
        if (!(isAir(s) || lichenMask(s) >= 0 || s == WATER)) {
            return false;
        }
        return stateForPlacement(level, s, x, y, z, d) >= 0;
    }

    private static boolean spreadToFace(Region level, int x, int y, int z, int d) {
        int s = stateForPlacement(level, level.get(x, y, z), x, y, z, d);
        return s >= 0 && level.set(x, y, z, s);
    }
}
