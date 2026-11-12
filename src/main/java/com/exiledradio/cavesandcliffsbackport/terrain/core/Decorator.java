package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;

/**
 * 1.18.2's decoration (ChunkGenerator.applyBiomeDecoration) for one chunk on its 3x3 Region, after every chunk of
 * the region has been filled, surfaced and carved (and decorated already where its turn came first: features
 * write into neighbors, so the order chunks are decorated in matters, in vanilla as here).
 *
 * <p>The loop is vanilla's: a WorldgenRandom over Xoroshiro (XoroRandom), setDecorationSeed(world seed, chunk
 * min x, min z); per step, the union over every biome stored in the 3x3 chunks of that biome's features for the
 * step, as indices into the step's global order (FeatureTables.STEPS, BiomeSource.featuresPerStep), sorted; each
 * placed with setFeatureSeed(decoration seed, index, step) through its placement modifiers, in the order vanilla's
 * lazy streams run them (each position goes all the way through the remaining modifiers and the feature before
 * the next one is drawn). Structure pieces, which vanilla places first in each step, are not here yet.
 *
 * <p>Features not ported yet are skipped. A subset decorates exactly as vanilla does with the rest disabled (each
 * feature's random is its own), which is how the checks test each stream; {@link #only} restricts to one.
 *
 * One instance per thread.
 */
public final class Decorator {
    private static final int MIN_Y = OverworldNoise.MIN_Y;
    private static final int HEIGHT = OverworldNoise.HEIGHT;
    private static final int STEPS = FeatureTables.STEPS.length;

    private final long seed;
    private final long zoomSeed;
    private final BiomeSource biomes;
    private final XoroRandom random = new XoroRandom(0L);
    /** Per step, per index: the ported feature, or null. */
    private final Placed[][] features = new Placed[STEPS][];
    /** Per biome, per step: the indices of its features. */
    private final int[][][] biomeSteps;
    /** Per biome: every feature it has, any step (BiomeGenerationSettings.hasFeature), by Placed.id. */
    private final boolean[][] biomeHas;
    private Set<String> only;
    private final byte[] stored = new byte[1536];
    private final boolean[] present = new boolean[256];
    private final boolean[] chosen = new boolean[512];

    public Decorator(OverworldNoise noise, BiomeSource biomes) {
        this.seed = noise.seed;
        this.zoomSeed = SurfaceBuilder.obfuscateSeed(noise.seed);
        this.biomes = biomes;
        Map<String, Placed> ported = new HashMap<>();
        for (Placed p : Ores.placed()) {
            ported.put(p.name, p);
        }
        for (Placed p : Geodes.placed(noise.seed)) {
            ported.put(p.name, p);
        }
        for (Placed p : Dripstone.placed()) {
            ported.put(p.name, p);
        }
        for (Placed p : Caves.placed()) {
            ported.put(p.name, p);
        }
        for (Placed p : Misc.placed()) {
            ported.put(p.name, p);
        }
        Map<String, Integer> ids = new HashMap<>();
        for (int k = 0; k < STEPS; k++) {
            String[] names = FeatureTables.STEPS[k];
            features[k] = new Placed[names.length];
            for (int i = 0; i < names.length; i++) {
                if (!ids.containsKey(names[i])) {
                    ids.put(names[i], ids.size());
                }
                Placed p = ported.get(names[i]);
                if (p != null) {
                    p.id = ids.get(names[i]);
                    features[k][i] = p;
                }
            }
        }
        int n = FeatureTables.BIOMES.length;
        biomeSteps = new int[n][][];
        biomeHas = new boolean[n][ids.size()];
        for (int b = 0; b < n; b++) {
            String[][] steps = FeatureTables.BIOMES[b];
            biomeSteps[b] = new int[steps.length][];
            for (int k = 0; k < steps.length; k++) {
                // Biomes outside the overworld's biome source (nether, end) list features no step order holds.
                int[] idx = new int[steps[k].length];
                int m = 0;
                for (int i = 0; i < idx.length; i++) {
                    int at = k < STEPS ? Arrays.asList(FeatureTables.STEPS[k]).indexOf(steps[k][i]) : -1;
                    if (at >= 0) {
                        idx[m++] = at;
                        biomeHas[b][ids.get(steps[k][i])] = true;
                    }
                }
                biomeSteps[b][k] = Arrays.copyOf(idx, m);
            }
        }
    }

    /** The ported placed features' names, in step and index order (for the oracle's -Dfeatures). */
    public java.util.List<String> ported() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Placed[] step : features) {
            for (Placed p : step) {
                if (p != null) {
                    names.add(p.name);
                }
            }
        }
        return names;
    }

    /** Runs only the named placed features (null: every ported one), as VanillaFeatureDump's -Dfeatures does. */
    public void only(Set<String> names) {
        only = names;
    }

    // ---- the loop ----

    private Region region;

    /** Decorates the region's center chunk. */
    public void decorate(Region region) {
        this.region = region;
        int cx = region.centerX;
        int cz = region.centerZ;
        int ox = cx << 4;
        int oz = cz << 4;
        random.newRandom();
        long decorationSeed = random.setDecorationSeed(seed, ox, oz);
        long mask = 0L;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                mask |= storedBiomes(cx + dx, cz + dz);
            }
        }
        for (int b = 0; b < 64; b++) {
            present[b] = (mask >>> b & 1L) != 0L;
        }
        for (int k = 0; k < STEPS; k++) {
            Placed[] list = features[k];
            Arrays.fill(chosen, 0, list.length, false);
            for (int b = 0; b < present.length; b++) {
                if (present[b] && k < biomeSteps[b].length) {
                    for (int i : biomeSteps[b][k]) {
                        chosen[i] = true;
                    }
                }
            }
            for (int i = 0; i < list.length; i++) {
                Placed p = list[i];
                if (chosen[i] && p != null && (only == null || only.contains(p.name))) {
                    random.setFeatureSeed(decorationSeed, i, k);
                    run(p, 0, ox, MIN_Y, oz);
                }
            }
        }
        this.region = null;
    }

    // Per chunk, the set of biome ids it stores (all below 64 in the overworld), direct-mapped: each chunk is in
    // nine decorations' 3x3 unions.
    private static final int CACHE = 64;
    private final long[] maskKeys = new long[CACHE * CACHE];
    private final long[] masks = new long[CACHE * CACHE];
    private final boolean[] maskPresent = new boolean[CACHE * CACHE];

    private long storedBiomes(int cx, int cz) {
        int slot = (cx & (CACHE - 1)) * CACHE + (cz & (CACHE - 1));
        long key = (long) cx << 32 | (cz & 0xFFFFFFFFL);
        if (!maskPresent[slot] || maskKeys[slot] != key) {
            biomes.fillChunk(cx, cz, stored);
            long m = 0L;
            for (byte b : stored) {
                m |= 1L << b;
            }
            masks[slot] = m;
            maskKeys[slot] = key;
            maskPresent[slot] = true;
        }
        return masks[slot];
    }

    // ---- placement (levelgen/placement) ----

    static final int COUNT = 0;
    static final int COUNT_UNIFORM = 1;
    static final int RARITY = 2;
    static final int IN_SQUARE = 3;
    static final int HEIGHT_UNIFORM = 4;
    static final int HEIGHT_TRAPEZOID = 5;
    static final int BIOME = 6;
    static final int HEIGHTMAP_OCEAN_FLOOR = 7;
    static final int SURFACE_THRESHOLD_OCEAN_FLOOR = 8;
    static final int HEIGHT_VERY_BIASED = 9;
    /** HeightmapPlacement, argument: Region's heightmap type, or WG_OCEAN_FLOOR / WG_WORLD_SURFACE. */
    static final int HEIGHTMAP = 10;
    /** A Modifier from Placed.mods, argument: its index. */
    static final int CUSTOM = 11;

    static final int WG_OCEAN_FLOOR = 4;
    static final int WG_WORLD_SURFACE = 5;

    /** A placed feature: its placement modifiers as ops (opcode, then its arguments) and its feature. */
    static final class Placed {
        final String name;
        final int[] ops;
        final Feature feature;
        Modifier[] mods = new Modifier[0];
        int id;

        Placed(String name, Feature feature, int... ops) {
            this.name = name;
            this.feature = feature;
            this.ops = ops;
        }

        /** The modifiers CUSTOM ops refer to, by index. */
        Placed mods(Modifier... mods) {
            this.mods = mods;
            return this;
        }
    }

    /** A placement modifier that is not an op: it calls next.at(x, y, z) for each position it gives. */
    interface Modifier {
        void apply(Region region, XoroRandom random, Next next, int x, int y, int z);
    }

    /** The rest of a placed feature's modifiers after a Modifier. */
    static final class Next {
        Decorator decorator;
        Placed placed;
        int at;

        void at(int x, int y, int z) {
            decorator.run(placed, at, x, y, z);
        }
    }

    interface Feature {
        /** ConfiguredFeature.place at x, y, z (Feature.place's ensureCanWrite of the origin included). */
        boolean place(Region region, XoroRandom random, int x, int y, int z);
    }


    /** PlacedFeature.placeWithContext: each modifier's positions flow lazily through the rest, depth first. */
    private void run(Placed p, int at, int x, int y, int z) {
        int[] ops = p.ops;
        if (at == ops.length) {
            if (region.canWrite(x, z)) {
                p.feature.place(region, random, x, y, z);
            }
            return;
        }
        switch (ops[at]) {
            case COUNT: {
                int n = ops[at + 1];
                for (int i = 0; i < n; i++) {
                    run(p, at + 2, x, y, z);
                }
                return;
            }
            case COUNT_UNIFORM: {
                int n = random.between(ops[at + 1], ops[at + 2]);
                for (int i = 0; i < n; i++) {
                    run(p, at + 3, x, y, z);
                }
                return;
            }
            case RARITY:
                if (random.nextFloat() < 1.0F / (float) ops[at + 1]) {
                    run(p, at + 2, x, y, z);
                }
                return;
            case IN_SQUARE: {
                int i = random.nextInt(16) + x;
                int j = random.nextInt(16) + z;
                run(p, at + 1, i, y, j);
                return;
            }
            case HEIGHT_UNIFORM:
                run(p, at + 3, x, uniformHeight(ops[at + 1], ops[at + 2]), z);
                return;
            case HEIGHT_TRAPEZOID:
                run(p, at + 4, x, trapezoidHeight(ops[at + 1], ops[at + 2], ops[at + 3]), z);
                return;
            case BIOME:
                if (biomeHas[zoomedBiome(x, y, z)][p.id]) {
                    run(p, at + 1, x, y, z);
                }
                return;
            case HEIGHTMAP_OCEAN_FLOOR: {
                // HeightmapPlacement(OCEAN_FLOOR_WG)
                int k = region.oceanFloor(x, z);
                if (k > MIN_Y) {
                    run(p, at + 1, x, k, z);
                }
                return;
            }
            case SURFACE_THRESHOLD_OCEAN_FLOOR: {
                // SurfaceRelativeThresholdFilter(OCEAN_FLOOR_WG, min, max), in longs as vanilla
                long i = (long) region.oceanFloor(x, z);
                long j = i + (long) ops[at + 1];
                long k = i + (long) ops[at + 2];
                if (j <= (long) y && (long) y <= k) {
                    run(p, at + 3, x, y, z);
                }
                return;
            }
            case HEIGHT_VERY_BIASED:
                run(p, at + 4, x, veryBiasedHeight(ops[at + 1], ops[at + 2], ops[at + 3]), z);
                return;
            case HEIGHTMAP: {
                int t = ops[at + 1];
                int k = t == WG_OCEAN_FLOOR ? region.oceanFloor(x, z) : t == WG_WORLD_SURFACE ? region.worldSurfaceWg(x, z)
                    : region.height(t, x, z);
                if (k > MIN_Y) {
                    run(p, at + 2, x, k, z);
                }
                return;
            }
            case CUSTOM: {
                Next n = new Next();
                n.decorator = this;
                n.placed = p;
                n.at = at + 2;
                p.mods[ops[at + 1]].apply(region, random, n, x, y, z);
                return;
            }
            default:
                throw new IllegalStateException("op " + ops[at]);
        }
    }

    /** UniformHeight.sample (anchors already resolved). */
    private int uniformHeight(int i, int j) {
        return i > j ? i : random.between(i, j);
    }

    /** VeryBiasedToBottomHeight.sample (Mth.nextInt: min when min >= max). */
    private int veryBiasedHeight(int i, int j, int inner) {
        if (j - i - inner + 1 <= 0) {
            return i;
        }
        int k = nextInt(i + inner, j);
        int l = nextInt(i, k - 1);
        return nextInt(i, l - 1 + inner);
    }

    private int nextInt(int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    /** TrapezoidHeight.sample. */
    private int trapezoidHeight(int i, int j, int plateau) {
        if (i > j) {
            return i;
        }
        int k = j - i;
        if (plateau >= k) {
            return random.between(i, j);
        }
        int l = (k - plateau) / 2;
        int i1 = k - l;
        return i + random.between(0, i1) + random.between(0, l);
    }

    // ---- biomes ----

    /** BiomeManager.getBiome over the region's stored biomes (quart y clamped to the world). */
    int zoomedBiome(int x, int y, int z) {
        int i = x - 2;
        int j = y - 2;
        int k = z - 2;
        int l = i >> 2;
        int i1 = j >> 2;
        int j1 = k >> 2;
        // The zoom picks one of the 8 quarts around the position; where they all hold one biome, that is it.
        BiomeSource s = biomes;
        int b = s.stored(l, i1, j1);
        if (s.stored(l + 1, i1, j1) == b && s.stored(l, i1 + 1, j1) == b && s.stored(l + 1, i1 + 1, j1) == b
                && s.stored(l, i1, j1 + 1) == b && s.stored(l + 1, i1, j1 + 1) == b && s.stored(l, i1 + 1, j1 + 1) == b
                && s.stored(l + 1, i1 + 1, j1 + 1) == b) {
            return b;
        }
        double d0 = (double) (i & 3) / 4.0D;
        double d1 = (double) (j & 3) / 4.0D;
        double d2 = (double) (k & 3) / 4.0D;
        int k1 = 0;
        double d3 = Double.POSITIVE_INFINITY;
        for (int l1 = 0; l1 < 8; ++l1) {
            boolean flag = (l1 & 4) == 0;
            boolean flag1 = (l1 & 2) == 0;
            boolean flag2 = (l1 & 1) == 0;
            double d7 = SurfaceBuilder.fiddledDistance(zoomSeed, flag ? l : l + 1, flag1 ? i1 : i1 + 1, flag2 ? j1 : j1 + 1,
                flag ? d0 : d0 - 1.0D, flag1 ? d1 : d1 - 1.0D, flag2 ? d2 : d2 - 1.0D);
            if (d3 > d7) {
                k1 = l1;
                d3 = d7;
            }
        }
        return biomes.stored((k1 & 4) == 0 ? l : l + 1, (k1 & 2) == 0 ? i1 : i1 + 1, (k1 & 1) == 0 ? j1 : j1 + 1);
    }

    // ---- OreFeature ----

    static final int RULE_NATURAL_STONE = 0;
    static final int RULE_STONE_ORE_REPLACEABLES = 1;
    static final int RULE_DEEPSLATE_ORE_REPLACEABLES = 2;

    /** TagMatchTest on base_stone_overworld, stone_ore_replaceables, deepslate_ore_replaceables (the jar's block tags). */
    static boolean matches(int rule, int code) {
        switch (rule) {
            case RULE_NATURAL_STONE:
                return code == STONE || code == GRANITE || code == DIORITE || code == ANDESITE || code == TUFF || code == DEEPSLATE;
            case RULE_STONE_ORE_REPLACEABLES:
                return code == STONE || code == GRANITE || code == DIORITE || code == ANDESITE;
            default:
                return code == DEEPSLATE || code == TUFF;
        }
    }

    /** Feature.ORE with an OreConfiguration: targets (rule, code pairs), size, discard chance on air exposure. */
    static final class Ore implements Feature {
        final int[] targets;
        final int size;
        final float discard;

        Ore(int size, float discard, int... targets) {
            this.size = size;
            this.discard = discard;
            this.targets = targets;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            float f = random.nextFloat() * (float) Math.PI;
            float f1 = (float) size / 8.0F;
            int i = McMath.ceil((double) (((float) size / 16.0F * 2.0F + 1.0F) / 2.0F));
            double d0 = (double) x + Math.sin((double) f) * (double) f1;
            double d1 = (double) x - Math.sin((double) f) * (double) f1;
            double d2 = (double) z + Math.cos((double) f) * (double) f1;
            double d3 = (double) z - Math.cos((double) f) * (double) f1;
            double d4 = (double) (y + random.nextInt(3) - 2);
            double d5 = (double) (y + random.nextInt(3) - 2);
            int k = x - McMath.ceil((double) f1) - i;
            int l = y - 2 - i;
            int i1 = z - McMath.ceil((double) f1) - i;
            int j1 = 2 * (McMath.ceil((double) f1) + i);
            int k1 = 2 * (2 + i);
            for (int l1 = k; l1 <= k + j1; ++l1) {
                for (int i2 = i1; i2 <= i1 + j1; ++i2) {
                    if (l <= level.oceanFloor(l1, i2)) {
                        return doPlace(level, random, d0, d1, d2, d3, d4, d5, k, l, i1, j1, k1);
                    }
                }
            }
            return false;
        }

        private boolean doPlace(Region level, XoroRandom random, double p0, double p1, double p2, double p3,
                                double p4, double p5, int minX, int minY, int minZ, int width, int height) {
            int placed = 0;
            java.util.BitSet bitset = new java.util.BitSet(width * height * width);
            int j = size;
            double[] adouble = new double[j * 4];
            for (int k = 0; k < j; ++k) {
                float f = (float) k / (float) j;
                double d0 = McMath.lerp((double) f, p0, p1);
                double d1 = McMath.lerp((double) f, p4, p5);
                double d2 = McMath.lerp((double) f, p2, p3);
                double d3 = random.nextDouble() * (double) j / 16.0D;
                double d4 = ((double) (McMath.sin((float) Math.PI * f) + 1.0F) * d3 + 1.0D) / 2.0D;
                adouble[k * 4] = d0;
                adouble[k * 4 + 1] = d1;
                adouble[k * 4 + 2] = d2;
                adouble[k * 4 + 3] = d4;
            }
            for (int l3 = 0; l3 < j - 1; ++l3) {
                if (!(adouble[l3 * 4 + 3] <= 0.0D)) {
                    for (int i4 = l3 + 1; i4 < j; ++i4) {
                        if (!(adouble[i4 * 4 + 3] <= 0.0D)) {
                            double d8 = adouble[l3 * 4] - adouble[i4 * 4];
                            double d10 = adouble[l3 * 4 + 1] - adouble[i4 * 4 + 1];
                            double d12 = adouble[l3 * 4 + 2] - adouble[i4 * 4 + 2];
                            double d14 = adouble[l3 * 4 + 3] - adouble[i4 * 4 + 3];
                            if (d14 * d14 > d8 * d8 + d10 * d10 + d12 * d12) {
                                if (d14 > 0.0D) {
                                    adouble[i4 * 4 + 3] = -1.0D;
                                } else {
                                    adouble[l3 * 4 + 3] = -1.0D;
                                }
                            }
                        }
                    }
                }
            }
            for (int j4 = 0; j4 < j; ++j4) {
                double d9 = adouble[j4 * 4 + 3];
                if (!(d9 < 0.0D)) {
                    double d11 = adouble[j4 * 4];
                    double d13 = adouble[j4 * 4 + 1];
                    double d15 = adouble[j4 * 4 + 2];
                    int k4 = Math.max(McMath.floor(d11 - d9), minX);
                    int l = Math.max(McMath.floor(d13 - d9), minY);
                    int i1 = Math.max(McMath.floor(d15 - d9), minZ);
                    int j1 = Math.max(McMath.floor(d11 + d9), k4);
                    int k1 = Math.max(McMath.floor(d13 + d9), l);
                    int l1 = Math.max(McMath.floor(d15 + d9), i1);
                    for (int i2 = k4; i2 <= j1; ++i2) {
                        double d5 = ((double) i2 + 0.5D - d11) / d9;
                        if (d5 * d5 < 1.0D) {
                            for (int j2 = l; j2 <= k1; ++j2) {
                                double d6 = ((double) j2 + 0.5D - d13) / d9;
                                if (d5 * d5 + d6 * d6 < 1.0D) {
                                    for (int k2 = i1; k2 <= l1; ++k2) {
                                        double d7 = ((double) k2 + 0.5D - d15) / d9;
                                        if (d5 * d5 + d6 * d6 + d7 * d7 < 1.0D && !Region.outsideBuildHeight(j2)) {
                                            int l2 = i2 - minX + (j2 - minY) * width + (k2 - minZ) * width * height;
                                            if (!bitset.get(l2)) {
                                                bitset.set(l2);
                                                if (level.canWrite(i2, k2)) {
                                                    int state = level.get(i2, j2, k2);
                                                    for (int t = 0; t < targets.length; t += 2) {
                                                        if (canPlace(level, random, state, targets[t], i2, j2, k2)) {
                                                            level.setRaw(i2, j2, k2, targets[t + 1]);
                                                            ++placed;
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return placed > 0;
        }

        /** OreFeature.canPlaceOre. */
        private boolean canPlace(Region level, XoroRandom random, int state, int rule, int x, int y, int z) {
            if (!matches(rule, state)) {
                return false;
            }
            if (discard <= 0.0F) {
                return true;
            }
            if (!(discard >= 1.0F) && random.nextFloat() >= discard) {
                return true;
            }
            return !adjacentToAir(level, x, y, z);
        }
    }

    /** Feature.isAdjacentToAir. */
    static boolean adjacentToAir(Region level, int x, int y, int z) {
        return isAir(level.get(x, y - 1, z)) || isAir(level.get(x, y + 1, z)) || isAir(level.get(x, y, z - 1))
            || isAir(level.get(x, y, z + 1)) || isAir(level.get(x - 1, y, z)) || isAir(level.get(x + 1, y, z));
    }
}
