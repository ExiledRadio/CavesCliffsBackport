package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 1.18.2's overworld biome parameters (OverworldBiomeBuilder) and the search Climate.RTree does over them, plus
 * an exact shortcut for the search. With {@link Editions}, the newer versions' parameters: 1.19's mangrove swamp and
 * deep dark, 1.20's cherry grove (checked against the oracles by tools/terrain/NewerBiomeCheck).
 *
 * <p>Parameters are quantized longs (Climate.quantizeCoord: (long) (value * 10000.0F)) in Mojang's order:
 * temperature, humidity, continentalness, erosion, depth, weirdness, offset.
 *
 * <p><b>Ties.</b> Vanilla's search starts from the thread's previous answer (RTree.lastResult, a ThreadLocal) and
 * keeps it when another leaf is exactly as close, so where two biomes tie (a climate value exactly on a shared
 * border, about one quart in a few thousand) vanilla's answer depends on what that worker thread looked up before.
 * That is not reproducible, not even by vanilla itself, so this class always answers as vanilla does from a cold
 * start (no previous result): the closest leaf, ties going to the leaf the tree's depth-first order reaches
 * first. The oracle dumps vanilla the same way (it clears lastResult before every query).
 *
 * <p>Immutable after construction and safe to share between threads.
 */
public final class Climate {
    public static final int TEMPERATURE = 0;
    public static final int HUMIDITY = 1;
    public static final int CONTINENTALNESS = 2;
    public static final int EROSION = 3;
    public static final int DEPTH = 4;
    public static final int WEIRDNESS = 5;
    public static final int OFFSET = 6;
    private static final int DIMS = 7;

    public static long quantize(float v) {
        return (long) (v * 10000.0F);
    }

    static final class Node {
        final long[] min = new long[DIMS];
        final long[] max = new long[DIMS];
        Node[] children;
        int biome = -1;
        int rank = -1;

        long distance(long[] t, boolean halfOpen) {
            long s = 0L;
            for (int d = 0; d < DIMS; d++) {
                long v = dist(min[d], max[d], t[d], halfOpen);
                s += v * v;
            }
            return s;
        }
    }

    /**
     * Climate.Parameter.distance. 26.4 ({@link Editions#from264}, halfOpen) counts a value at the max as 1 away (target
     * - max + 1), so a range holds min to max - 1 and a point is never at distance 0.
     */
    static long dist(long min, long max, long v, boolean halfOpen) {
        long i = halfOpen ? v - max + 1L : v - max;
        long j = min - v;
        return i > 0L ? i : Math.max(j, 0L);
    }

    /** Leaves in OverworldBiomeBuilder's order. */
    private final Node[] leaves;
    /** Leaves in the tree's depth-first order: rank r is byRank[r]. */
    private final Node[] byRank;
    private final Node root;

    /** The newer versions' additions this table has ({@link Editions} flags). */
    public final int editions;
    /** 26.4's Climate.Parameter.distance ({@link Editions#from264}). */
    private final boolean halfOpen;

    public Climate() {
        this(Editions.NONE);
    }

    public Climate(int editions) {
        this.editions = editions;
        this.halfOpen = Editions.from264(editions);
        List<Node> list = new ArrayList<>();
        new Builder(list, editions).addBiomes();
        leaves = list.toArray(new Node[0]);
        // Climate.RTree.CHILDREN_PER_NODE: 10 in 1.18.2 and 1.19, 6 from 1.20 (which moves ties), 19 in 26.4.
        root = build(new ArrayList<>(list), Editions.from264(editions) ? 19 : Editions.from120(editions) ? 6 : 10);
        List<Node> order = new ArrayList<>();
        rank(root, order);
        byRank = order.toArray(new Node[0]);
        if (byRank.length != leaves.length) {
            throw new IllegalStateException("tree lost leaves");
        }
        fast = new Fast();
    }

    public int leafCount() {
        return leaves.length;
    }

    /** Leaf i's biome, in OverworldBiomeBuilder's order. */
    public int leafBiome(int i) {
        return leaves[i].biome;
    }

    /** Leaf i's quantized bounds (Mojang's parameter order, offset last), copied. */
    public long[][] leafBounds(int i) {
        return new long[][]{leaves[i].min.clone(), leaves[i].max.clone()};
    }

    /** Leaf i in the oracles' format: biome name, then min and max of the six parameters, then the offset. */
    public String describeLeaf(int i) {
        Node n = leaves[i];
        StringBuilder sb = new StringBuilder(Biomes.name(n.biome));
        for (int d = 0; d < OFFSET; d++) {
            sb.append(' ').append(n.min[d]).append(' ').append(n.max[d]);
        }
        return sb.append(' ').append(n.min[OFFSET]).toString();
    }

    // ---- the tree, built exactly as Climate.RTree.create builds it ----

    private static long sumAbsCenters(Node n) {
        long s = 0L;
        for (int d = 0; d < DIMS; d++) {
            s += Math.abs((n.min[d] + n.max[d]) / 2L);
        }
        return s;
    }

    static Node build(List<Node> nodes, int perNode) {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("Need at least one child to build a node");
        } else if (nodes.size() == 1) {
            return nodes.get(0);
        } else if (nodes.size() <= perNode) {
            nodes.sort(Comparator.comparingLong(Climate::sumAbsCenters));
            return subTree(nodes);
        }
        long best = Long.MAX_VALUE;
        int bestDim = -1;
        List<Node> bestBuckets = null;
        for (int k = 0; k < DIMS; k++) {
            sort(nodes, k, false);
            List<Node> buckets = bucketize(nodes, perNode);
            long cost = 0L;
            for (Node b : buckets) {
                cost += cost(b);
            }
            if (best > cost) {
                best = cost;
                bestDim = k;
                bestBuckets = buckets;
            }
        }
        sort(bestBuckets, bestDim, true);
        List<Node> children = new ArrayList<>();
        for (Node b : bestBuckets) {
            children.add(build(Arrays.asList(b.children), perNode));
        }
        return subTree(children);
    }

    private static void sort(List<Node> nodes, int dim, boolean abs) {
        Comparator<Node> c = comparator(dim, abs);
        for (int i = 1; i < DIMS; i++) {
            c = c.thenComparing(comparator((dim + i) % DIMS, abs));
        }
        nodes.sort(c);
    }

    private static Comparator<Node> comparator(int dim, boolean abs) {
        return Comparator.comparingLong(n -> {
            long i = (n.min[dim] + n.max[dim]) / 2L;
            return abs ? Math.abs(i) : i;
        });
    }

    private static List<Node> bucketize(List<Node> nodes, int perNode) {
        List<Node> out = new ArrayList<>();
        List<Node> current = new ArrayList<>();
        int size = (int) Math.pow((double) perNode, Math.floor(Math.log((double) nodes.size() - 0.01D) / Math.log((double) perNode)));
        for (Node n : nodes) {
            current.add(n);
            if (current.size() >= size) {
                out.add(subTree(current));
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            out.add(subTree(current));
        }
        return out;
    }

    private static long cost(Node n) {
        long s = 0L;
        for (int d = 0; d < DIMS; d++) {
            s += Math.abs(n.max[d] - n.min[d]);
        }
        return s;
    }

    private static Node subTree(List<Node> children) {
        Node n = new Node();
        n.children = children.toArray(new Node[0]);
        for (int d = 0; d < DIMS; d++) {
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            boolean first = true;
            for (Node c : n.children) {
                // Parameter.span(null) is the parameter itself; then min of mins, max of maxes.
                if (first) {
                    min = c.min[d];
                    max = c.max[d];
                    first = false;
                } else {
                    min = Math.min(c.min[d], min);
                    max = Math.max(c.max[d], max);
                }
            }
            n.min[d] = min;
            n.max[d] = max;
        }
        return n;
    }

    private static void rank(Node n, List<Node> order) {
        if (n.children == null) {
            n.rank = order.size();
            order.add(n);
            return;
        }
        for (Node c : n.children) {
            rank(c, order);
        }
    }

    // ---- searches ----

    /** RTree.SubTree.search, Mojang's code: the closest leaf, keeping last when nothing is strictly closer. */
    static Node search(Node node, long[] t, Node last, boolean halfOpen) {
        if (node.children == null) {
            return node;
        }
        long i = last == null ? Long.MAX_VALUE : last.distance(t, halfOpen);
        Node leaf = last;
        for (Node child : node.children) {
            long j = child.distance(t, halfOpen);
            if (i > j) {
                Node found = search(child, t, leaf, halfOpen);
                long k = child == found ? j : found.distance(t, halfOpen);
                if (i > k) {
                    i = k;
                    leaf = found;
                }
            }
        }
        return leaf;
    }

    /** The biome vanilla's tree search finds from a cold start. target: 7 quantized values (offset 0). */
    public int searchTree(long[] target) {
        return search(root, target, null, halfOpen).biome;
    }

    /** The same by trying every leaf: the closest, ties to the lowest depth-first rank. */
    public int searchBruteForce(long[] target) {
        Node best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Node n : leaves) {
            long d = n.distance(target, halfOpen);
            if (d < bestDistance || d == bestDistance && n.rank < best.rank) {
                best = n;
                bestDistance = d;
            }
        }
        return best.biome;
    }

    // ---- the shortcut ----

    /*
     * Every overworld leaf is a "surface" leaf whose depth is the point 0 or the point 1 (addSurfaceBiome adds
     * both), or one of a few underground leaves (depth 0.2 to 0.9). A leaf's squared distance is the sum of one
     * term per parameter, so for a column (fixed temperature, humidity, continentalness, erosion, weirdness) the
     * closest depth-0 leaf at any depth d is the depth-0 leaf closest in the other five parameters (ties to the
     * lowest rank), at distance that + d^2; the same for depth 1 with (d - 1)^2. Per column the search is then
     * that pair plus the underground leaves; per y it is a comparison of four numbers.
     *
     * The depth-0 (and depth-1) leaves' five other parameters are boxes on a grid whose lines are every leaf's
     * bounds. A column strictly inside a grid cell is inside exactly the leaves that cover the cell, so the
     * closest of them (distance 0, lowest rank) is precomputed per cell. A value beyond the outermost line of a
     * parameter counts as inside the outermost cell: the leaves reaching that line are exactly the ones covering
     * it, and they are all equally (and least) far. A value exactly on a grid line, or a cell no leaf covers,
     * falls back to trying every leaf (rare: a few columns per thousand).
     */

    private final Fast fast;

    /** One column's precomputed search: the closest depth-0 and depth-1 leaves, and the others' distances. */
    public static final class Column {
        long distance0;
        int rank0;
        long distance1;
        int rank1;
        final long[] otherDistance = new long[5];
        long[] target5 = new long[DIMS];
    }

    public Column newColumn() {
        return new Column();
    }

    /** Fills c for a column's five climate values (quantized). */
    public void column(Column c, long temperature, long humidity, long continentalness, long erosion, long weirdness) {
        fast.column(c, temperature, humidity, continentalness, erosion, weirdness);
    }

    /** The biome at quantized depth in a column filled by column(). */
    public int biome(Column c, long depth) {
        return fast.biome(c, depth);
    }

    private static final int[] GRID_DIMS = {TEMPERATURE, HUMIDITY, CONTINENTALNESS, EROSION, WEIRDNESS};

    private final class Fast {
        private final Node[][] classes = new Node[2][];
        private final Node[] others;
        private final long[] othersDepthMin;
        private final long[] othersDepthMax;
        private final long[][] lines = new long[5][];
        private final int[] cells = new int[5];
        private final int[][] cellRank = new int[2][];
        /** The squared offset distance of the surface leaves (all at offset 0) from a target's 0. */
        private final long offsetTerm;

        Fast() {
            List<Node> c0 = new ArrayList<>();
            List<Node> c1 = new ArrayList<>();
            List<Node> other = new ArrayList<>();
            for (Node n : leaves) {
                if (n.min[OFFSET] != 0L || n.max[OFFSET] != 0L) {
                    other.add(n);
                } else if (n.min[DEPTH] == 0L && n.max[DEPTH] == 0L) {
                    c0.add(n);
                } else if (n.min[DEPTH] == 10000L && n.max[DEPTH] == 10000L) {
                    c1.add(n);
                } else {
                    other.add(n);
                }
            }
            long o = dist(0L, 0L, 0L, halfOpen);
            offsetTerm = o * o;
            classes[0] = c0.toArray(new Node[0]);
            classes[1] = c1.toArray(new Node[0]);
            others = other.toArray(new Node[0]);
            if (others.length > 5) {
                throw new IllegalStateException("too many underground leaves: " + others.length);
            }
            othersDepthMin = new long[others.length];
            othersDepthMax = new long[others.length];
            for (int i = 0; i < others.length; i++) {
                othersDepthMin[i] = others[i].min[DEPTH];
                othersDepthMax[i] = others[i].max[DEPTH];
            }
            for (int g = 0; g < 5; g++) {
                int d = GRID_DIMS[g];
                java.util.TreeSet<Long> set = new java.util.TreeSet<>();
                for (int k = 0; k < 2; k++) {
                    for (Node n : classes[k]) {
                        set.add(n.min[d]);
                        set.add(n.max[d]);
                    }
                }
                long[] l = new long[set.size()];
                int i = 0;
                for (long v : set) {
                    l[i++] = v;
                }
                lines[g] = l;
                cells[g] = l.length - 1;
            }
            int total = 1;
            for (int g = 0; g < 5; g++) {
                total *= cells[g];
            }
            for (int k = 0; k < 2; k++) {
                int[] ranks = new int[total];
                Arrays.fill(ranks, Integer.MAX_VALUE);
                for (Node n : classes[k]) {
                    int[] lo = new int[5];
                    int[] hi = new int[5];
                    for (int g = 0; g < 5; g++) {
                        int d = GRID_DIMS[g];
                        lo[g] = Arrays.binarySearch(lines[g], n.min[d]);
                        hi[g] = Arrays.binarySearch(lines[g], n.max[d]);
                    }
                    for (int a = lo[0]; a < hi[0]; a++) {
                        for (int b = lo[1]; b < hi[1]; b++) {
                            for (int c = lo[2]; c < hi[2]; c++) {
                                for (int e = lo[3]; e < hi[3]; e++) {
                                    for (int w = lo[4]; w < hi[4]; w++) {
                                        int at = index(a, b, c, e, w);
                                        if (n.rank < ranks[at]) {
                                            ranks[at] = n.rank;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                cellRank[k] = ranks;
            }
        }

        private int index(int a, int b, int c, int e, int w) {
            return (((a * cells[1] + b) * cells[2] + c) * cells[3] + e) * cells[4] + w;
        }

        /** The cell along grid parameter g holding v, or -1 when v is exactly on a line. */
        private int cell(int g, long v) {
            long[] l = lines[g];
            if (v < l[0]) {
                return 0;
            }
            int last = l.length - 1;
            if (v > l[last]) {
                return last - 1;
            }
            int i = Arrays.binarySearch(l, v);
            return i >= 0 ? -1 : -i - 2;
        }

        void column(Column col, long t, long h, long c, long e, long w) {
            long[] target = col.target5;
            target[TEMPERATURE] = t;
            target[HUMIDITY] = h;
            target[CONTINENTALNESS] = c;
            target[EROSION] = e;
            target[DEPTH] = 0L;
            target[WEIRDNESS] = w;
            target[OFFSET] = 0L;
            int a = cell(0, t);
            int b = cell(1, h);
            int cc = cell(2, c);
            int ee = cell(3, e);
            int ww = cell(4, w);
            boolean onGrid = a >= 0 && b >= 0 && cc >= 0 && ee >= 0 && ww >= 0;
            int at = onGrid ? index(a, b, cc, ee, ww) : -1;
            for (int k = 0; k < 2; k++) {
                int rank = onGrid ? cellRank[k][at] : Integer.MAX_VALUE;
                long distance;
                if (rank != Integer.MAX_VALUE) {
                    distance = distance5(byRank[rank], target);
                } else {
                    // Brute force over the class: closest in the five parameters, ties to the lowest rank.
                    distance = Long.MAX_VALUE;
                    for (Node n : classes[k]) {
                        long d = distance5(n, target);
                        if (d < distance || d == distance && n.rank < rank) {
                            distance = d;
                            rank = n.rank;
                        }
                    }
                }
                // The offset (0 for every leaf, so the same term for all; 1 with 26.4's distance), as the others'.
                distance += offsetTerm;
                if (k == 0) {
                    col.distance0 = distance;
                    col.rank0 = rank;
                } else {
                    col.distance1 = distance;
                    col.rank1 = rank;
                }
            }
            for (int i = 0; i < others.length; i++) {
                Node n = others[i];
                long s = 0L;
                for (int d = 0; d < DIMS; d++) {
                    if (d != DEPTH) {
                        long v = dist(n.min[d], n.max[d], target[d], halfOpen);
                        s += v * v;
                    }
                }
                col.otherDistance[i] = s;
            }
        }

        private long distance5(Node n, long[] target) {
            long s = 0L;
            for (int g = 0; g < 5; g++) {
                int d = GRID_DIMS[g];
                long v = dist(n.min[d], n.max[d], target[d], halfOpen);
                s += v * v;
            }
            return s;
        }

        int biome(Column col, long depth) {
            long d0 = dist(0L, 0L, depth, halfOpen);
            long d1 = dist(10000L, 10000L, depth, halfOpen);
            long best = col.distance0 + d0 * d0;
            int rank = col.rank0;
            long c1 = col.distance1 + d1 * d1;
            if (c1 < best || c1 == best && col.rank1 < rank) {
                best = c1;
                rank = col.rank1;
            }
            for (int i = 0; i < others.length; i++) {
                long v = dist(othersDepthMin[i], othersDepthMax[i], depth, halfOpen);
                long c = col.otherDistance[i] + v * v;
                if (c < best || c == best && others[i].rank < rank) {
                    best = c;
                    rank = others[i].rank;
                }
            }
            return byRank[rank].biome;
        }
    }

    // ---- OverworldBiomeBuilder ----

    private static final class P {
        final long min;
        final long max;

        P(long min, long max) {
            this.min = min;
            this.max = max;
        }

        static P span(float a, float b) {
            if (a > b) {
                throw new IllegalArgumentException("min > max");
            }
            return new P(quantize(a), quantize(b));
        }

        static P span(P a, P b) {
            if (a.min > b.max) {
                throw new IllegalArgumentException("min > max");
            }
            return new P(a.min, b.max);
        }

        static P point(float v) {
            return span(v, v);
        }
    }

    /** OverworldBiomeBuilder.addBiomes, line for line. */
    private static final class Builder {
        private final List<Node> out;
        private final P FULL_RANGE = P.span(-1.0F, 1.0F);
        private final P[] temperatures = {P.span(-1.0F, -0.45F), P.span(-0.45F, -0.15F), P.span(-0.15F, 0.2F),
            P.span(0.2F, 0.55F), P.span(0.55F, 1.0F)};
        private final P[] humidities = {P.span(-1.0F, -0.35F), P.span(-0.35F, -0.1F), P.span(-0.1F, 0.1F),
            P.span(0.1F, 0.3F), P.span(0.3F, 1.0F)};
        private final P[] erosions = {P.span(-1.0F, -0.78F), P.span(-0.78F, -0.375F), P.span(-0.375F, -0.2225F),
            P.span(-0.2225F, 0.05F), P.span(0.05F, 0.45F), P.span(0.45F, 0.55F), P.span(0.55F, 1.0F)};
        private final P FROZEN_RANGE = temperatures[0];
        private final P UNFROZEN_RANGE = P.span(temperatures[1], temperatures[4]);
        private final P mushroomFieldsContinentalness = P.span(-1.2F, -1.05F);
        private final P deepOceanContinentalness = P.span(-1.05F, -0.455F);
        private final P oceanContinentalness = P.span(-0.455F, -0.19F);
        private final P coastContinentalness = P.span(-0.19F, -0.11F);
        private final P inlandContinentalness = P.span(-0.11F, 0.55F);
        private final P nearInlandContinentalness = P.span(-0.11F, 0.03F);
        private final P midInlandContinentalness = P.span(0.03F, 0.3F);
        private final P farInlandContinentalness = P.span(0.3F, 1.0F);
        private final int[][] OCEANS = {
            {Biomes.DEEP_FROZEN_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.DEEP_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN, Biomes.WARM_OCEAN},
            {Biomes.FROZEN_OCEAN, Biomes.COLD_OCEAN, Biomes.OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.WARM_OCEAN}};
        private static final int N = -1;
        private final int[][] MIDDLE_BIOMES = {
            {Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.TAIGA},
            {Biomes.PLAINS, Biomes.PLAINS, Biomes.FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA},
            {Biomes.FLOWER_FOREST, Biomes.PLAINS, Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.DARK_FOREST},
            {Biomes.SAVANNA, Biomes.SAVANNA, Biomes.FOREST, Biomes.JUNGLE, Biomes.JUNGLE},
            {Biomes.DESERT, Biomes.DESERT, Biomes.DESERT, Biomes.DESERT, Biomes.DESERT}};
        private final int[][] MIDDLE_BIOMES_VARIANT = {
            {Biomes.ICE_SPIKES, N, Biomes.SNOWY_TAIGA, N, N},
            {N, N, N, N, Biomes.OLD_GROWTH_PINE_TAIGA},
            {Biomes.SUNFLOWER_PLAINS, N, N, Biomes.OLD_GROWTH_BIRCH_FOREST, N},
            {N, N, Biomes.PLAINS, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE},
            {N, N, N, N, N}};
        private final int[][] PLATEAU_BIOMES = {
            {Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.SNOWY_TAIGA},
            {Biomes.MEADOW, Biomes.MEADOW, Biomes.FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA},
            {Biomes.MEADOW, Biomes.MEADOW, Biomes.MEADOW, Biomes.MEADOW, Biomes.DARK_FOREST},
            {Biomes.SAVANNA_PLATEAU, Biomes.SAVANNA_PLATEAU, Biomes.FOREST, Biomes.FOREST, Biomes.JUNGLE},
            {Biomes.BADLANDS, Biomes.BADLANDS, Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.WOODED_BADLANDS}};
        private final int[][] PLATEAU_BIOMES_VARIANT = {
            {Biomes.ICE_SPIKES, N, N, N, N},
            {N, N, Biomes.MEADOW, Biomes.MEADOW, Biomes.OLD_GROWTH_PINE_TAIGA},
            {N, N, Biomes.FOREST, Biomes.BIRCH_FOREST, N},
            {N, N, N, N, N},
            {Biomes.ERODED_BADLANDS, Biomes.ERODED_BADLANDS, N, N, N}};
        private final int[][] SHATTERED_BIOMES = {
            {Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_FOREST},
            {Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_FOREST},
            {Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_FOREST},
            {N, N, N, N, N},
            {N, N, N, N, N}};

        /** Editions flags: which newer lines to add (see the methods below). */
        private final int editions;

        Builder(List<Node> out, int editions) {
            this.editions = editions;
            if ((editions & Editions.CHERRY) != 0) {
                // 1.20: PLATEAU_BIOMES_VARIANT gains the cherry grove.
                PLATEAU_BIOMES_VARIANT[1][0] = Biomes.CHERRY_GROVE;
                PLATEAU_BIOMES_VARIANT[2][0] = Biomes.CHERRY_GROVE;
                PLATEAU_BIOMES_VARIANT[2][1] = Biomes.CHERRY_GROVE;
            }
            if ((editions & Editions.SPRING) != 0) {
                // 1.21.5 to 1.21.11: the temperate, most humid plateau biome itself, at both weirdness signs.
                PLATEAU_BIOMES[2][4] = Biomes.PALE_GARDEN;
            } else if ((editions & Editions.PALE) != 0) {
                // 1.21.4: the temperate, most humid plateau variant (null before: the dark forest of PLATEAU_BIOMES).
                PLATEAU_BIOMES_VARIANT[2][4] = Biomes.PALE_GARDEN;
            }
            if ((editions & Editions.GAME_DROPS) != 0) {
                // 26.3: the cool, driest middle variant (weirdness above 0), plains before.
                MIDDLE_BIOMES_VARIANT[1][0] = Biomes.DAPPLED_FOREST;
            }
            this.out = out;
        }

        void addBiomes() {
            addOffCoastBiomes();
            addInlandBiomes();
            addUndergroundBiomes();
        }

        private void addOffCoastBiomes() {
            addSurfaceBiome(FULL_RANGE, FULL_RANGE, mushroomFieldsContinentalness, FULL_RANGE, FULL_RANGE, 0.0F, Biomes.MUSHROOM_FIELDS);
            for (int i = 0; i < temperatures.length; ++i) {
                P t = temperatures[i];
                addSurfaceBiome(t, FULL_RANGE, deepOceanContinentalness, FULL_RANGE, FULL_RANGE, 0.0F, OCEANS[0][i]);
                addSurfaceBiome(t, FULL_RANGE, oceanContinentalness, FULL_RANGE, FULL_RANGE, 0.0F, OCEANS[1][i]);
            }
        }

        private void addInlandBiomes() {
            addMidSlice(P.span(-1.0F, -0.93333334F));
            addHighSlice(P.span(-0.93333334F, -0.7666667F));
            addPeaks(P.span(-0.7666667F, -0.56666666F));
            addHighSlice(P.span(-0.56666666F, -0.4F));
            addMidSlice(P.span(-0.4F, -0.26666668F));
            addLowSlice(P.span(-0.26666668F, -0.05F));
            addValleys(P.span(-0.05F, 0.05F));
            addLowSlice(P.span(0.05F, 0.26666668F));
            addMidSlice(P.span(0.26666668F, 0.4F));
            addHighSlice(P.span(0.4F, 0.56666666F));
            addPeaks(P.span(0.56666666F, 0.7666667F));
            addHighSlice(P.span(0.7666667F, 0.93333334F));
            addMidSlice(P.span(0.93333334F, 1.0F));
        }

        private void addPeaks(P w) {
            for (int i = 0; i < temperatures.length; ++i) {
                P t = temperatures[i];
                for (int j = 0; j < humidities.length; ++j) {
                    P h = humidities[j];
                    int b = pickMiddleBiome(i, j, w);
                    int b1 = pickMiddleBiomeOrBadlandsIfHot(i, j, w);
                    int b2 = pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(i, j, w);
                    int b3 = pickPlateauBiome(i, j, w);
                    int b4 = pickShatteredBiome(i, j, w);
                    int b5 = maybePickWindsweptSavannaBiome(i, j, w, b4);
                    int b6 = pickPeakBiome(i, j, w);
                    addSurfaceBiome(t, h, P.span(coastContinentalness, farInlandContinentalness), erosions[0], w, 0.0F, b6);
                    addSurfaceBiome(t, h, P.span(coastContinentalness, nearInlandContinentalness), erosions[1], w, 0.0F, b2);
                    addSurfaceBiome(t, h, P.span(midInlandContinentalness, farInlandContinentalness), erosions[1], w, 0.0F, b6);
                    addSurfaceBiome(t, h, P.span(coastContinentalness, nearInlandContinentalness), P.span(erosions[2], erosions[3]), w, 0.0F, b);
                    addSurfaceBiome(t, h, P.span(midInlandContinentalness, farInlandContinentalness), erosions[2], w, 0.0F, b3);
                    addSurfaceBiome(t, h, midInlandContinentalness, erosions[3], w, 0.0F, b1);
                    addSurfaceBiome(t, h, farInlandContinentalness, erosions[3], w, 0.0F, b3);
                    addSurfaceBiome(t, h, P.span(coastContinentalness, farInlandContinentalness), erosions[4], w, 0.0F, b);
                    addSurfaceBiome(t, h, P.span(coastContinentalness, nearInlandContinentalness), erosions[5], w, 0.0F, b5);
                    addSurfaceBiome(t, h, P.span(midInlandContinentalness, farInlandContinentalness), erosions[5], w, 0.0F, b4);
                    addSurfaceBiome(t, h, P.span(coastContinentalness, farInlandContinentalness), erosions[6], w, 0.0F, b);
                }
            }
        }

        private void addHighSlice(P w) {
            for (int i = 0; i < temperatures.length; ++i) {
                P t = temperatures[i];
                for (int j = 0; j < humidities.length; ++j) {
                    P h = humidities[j];
                    int b = pickMiddleBiome(i, j, w);
                    int b1 = pickMiddleBiomeOrBadlandsIfHot(i, j, w);
                    int b2 = pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(i, j, w);
                    int b3 = pickPlateauBiome(i, j, w);
                    int b4 = pickShatteredBiome(i, j, w);
                    int b5 = maybePickWindsweptSavannaBiome(i, j, w, b);
                    int b6 = pickSlopeBiome(i, j, w);
                    int b7 = pickPeakBiome(i, j, w);
                    addSurfaceBiome(t, h, coastContinentalness, P.span(erosions[0], erosions[1]), w, 0.0F, b);
                    addSurfaceBiome(t, h, nearInlandContinentalness, erosions[0], w, 0.0F, b6);
                    addSurfaceBiome(t, h, P.span(midInlandContinentalness, farInlandContinentalness), erosions[0], w, 0.0F, b7);
                    addSurfaceBiome(t, h, nearInlandContinentalness, erosions[1], w, 0.0F, b2);
                    addSurfaceBiome(t, h, P.span(midInlandContinentalness, farInlandContinentalness), erosions[1], w, 0.0F, b6);
                    addSurfaceBiome(t, h, P.span(coastContinentalness, nearInlandContinentalness), P.span(erosions[2], erosions[3]), w, 0.0F, b);
                    addSurfaceBiome(t, h, P.span(midInlandContinentalness, farInlandContinentalness), erosions[2], w, 0.0F, b3);
                    addSurfaceBiome(t, h, midInlandContinentalness, erosions[3], w, 0.0F, b1);
                    addSurfaceBiome(t, h, farInlandContinentalness, erosions[3], w, 0.0F, b3);
                    addSurfaceBiome(t, h, P.span(coastContinentalness, farInlandContinentalness), erosions[4], w, 0.0F, b);
                    addSurfaceBiome(t, h, P.span(coastContinentalness, nearInlandContinentalness), erosions[5], w, 0.0F, b5);
                    addSurfaceBiome(t, h, P.span(midInlandContinentalness, farInlandContinentalness), erosions[5], w, 0.0F, b4);
                    addSurfaceBiome(t, h, P.span(coastContinentalness, farInlandContinentalness), erosions[6], w, 0.0F, b);
                }
            }
        }

        private void addMidSlice(P w) {
            addSurfaceBiome(FULL_RANGE, FULL_RANGE, coastContinentalness, P.span(erosions[0], erosions[2]), w, 0.0F, Biomes.STONY_SHORE);
            addSwamp(w, P.span(nearInlandContinentalness, farInlandContinentalness));
            for (int i = 0; i < temperatures.length; ++i) {
                P t = temperatures[i];
                for (int j = 0; j < humidities.length; ++j) {
                    P h = humidities[j];
                    int b = pickMiddleBiome(i, j, w);
                    int b1 = pickMiddleBiomeOrBadlandsIfHot(i, j, w);
                    int b2 = pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(i, j, w);
                    int b3 = pickShatteredBiome(i, j, w);
                    int b4 = pickPlateauBiome(i, j, w);
                    int b5 = pickBeachBiome(i, j);
                    int b6 = maybePickWindsweptSavannaBiome(i, j, w, b);
                    int b7 = pickShatteredCoastBiome(i, j, w);
                    int b8 = pickSlopeBiome(i, j, w);
                    addSurfaceBiome(t, h, P.span(nearInlandContinentalness, farInlandContinentalness), erosions[0], w, 0.0F, b8);
                    addSurfaceBiome(t, h, P.span(nearInlandContinentalness, midInlandContinentalness), erosions[1], w, 0.0F, b2);
                    addSurfaceBiome(t, h, farInlandContinentalness, erosions[1], w, 0.0F, i == 0 ? b8 : b4);
                    addSurfaceBiome(t, h, nearInlandContinentalness, erosions[2], w, 0.0F, b);
                    addSurfaceBiome(t, h, midInlandContinentalness, erosions[2], w, 0.0F, b1);
                    addSurfaceBiome(t, h, farInlandContinentalness, erosions[2], w, 0.0F, b4);
                    addSurfaceBiome(t, h, P.span(coastContinentalness, nearInlandContinentalness), erosions[3], w, 0.0F, b);
                    addSurfaceBiome(t, h, P.span(midInlandContinentalness, farInlandContinentalness), erosions[3], w, 0.0F, b1);
                    if (w.max < 0L) {
                        addSurfaceBiome(t, h, coastContinentalness, erosions[4], w, 0.0F, b5);
                        addSurfaceBiome(t, h, P.span(nearInlandContinentalness, farInlandContinentalness), erosions[4], w, 0.0F, b);
                    } else {
                        addSurfaceBiome(t, h, P.span(coastContinentalness, farInlandContinentalness), erosions[4], w, 0.0F, b);
                    }
                    addSurfaceBiome(t, h, coastContinentalness, erosions[5], w, 0.0F, b7);
                    addSurfaceBiome(t, h, nearInlandContinentalness, erosions[5], w, 0.0F, b6);
                    addSurfaceBiome(t, h, P.span(midInlandContinentalness, farInlandContinentalness), erosions[5], w, 0.0F, b3);
                    if (w.max < 0L) {
                        addSurfaceBiome(t, h, coastContinentalness, erosions[6], w, 0.0F, b5);
                    } else {
                        addSurfaceBiome(t, h, coastContinentalness, erosions[6], w, 0.0F, b);
                    }
                    if (i == 0) {
                        addSurfaceBiome(t, h, P.span(nearInlandContinentalness, farInlandContinentalness), erosions[6], w, 0.0F, b);
                    }
                }
            }
        }

        private void addLowSlice(P w) {
            addSurfaceBiome(FULL_RANGE, FULL_RANGE, coastContinentalness, P.span(erosions[0], erosions[2]), w, 0.0F, Biomes.STONY_SHORE);
            addSwamp(w, P.span(nearInlandContinentalness, farInlandContinentalness));
            for (int i = 0; i < temperatures.length; ++i) {
                P t = temperatures[i];
                for (int j = 0; j < humidities.length; ++j) {
                    P h = humidities[j];
                    int b = pickMiddleBiome(i, j, w);
                    int b1 = pickMiddleBiomeOrBadlandsIfHot(i, j, w);
                    int b2 = pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(i, j, w);
                    int b3 = pickBeachBiome(i, j);
                    int b4 = maybePickWindsweptSavannaBiome(i, j, w, b);
                    int b5 = pickShatteredCoastBiome(i, j, w);
                    addSurfaceBiome(t, h, nearInlandContinentalness, P.span(erosions[0], erosions[1]), w, 0.0F, b1);
                    addSurfaceBiome(t, h, P.span(midInlandContinentalness, farInlandContinentalness), P.span(erosions[0], erosions[1]), w, 0.0F, b2);
                    addSurfaceBiome(t, h, nearInlandContinentalness, P.span(erosions[2], erosions[3]), w, 0.0F, b);
                    addSurfaceBiome(t, h, P.span(midInlandContinentalness, farInlandContinentalness), P.span(erosions[2], erosions[3]), w, 0.0F, b1);
                    addSurfaceBiome(t, h, coastContinentalness, P.span(erosions[3], erosions[4]), w, 0.0F, b3);
                    addSurfaceBiome(t, h, P.span(nearInlandContinentalness, farInlandContinentalness), erosions[4], w, 0.0F, b);
                    addSurfaceBiome(t, h, coastContinentalness, erosions[5], w, 0.0F, b5);
                    addSurfaceBiome(t, h, nearInlandContinentalness, erosions[5], w, 0.0F, b4);
                    addSurfaceBiome(t, h, P.span(midInlandContinentalness, farInlandContinentalness), erosions[5], w, 0.0F, b);
                    addSurfaceBiome(t, h, coastContinentalness, erosions[6], w, 0.0F, b3);
                    if (i == 0) {
                        addSurfaceBiome(t, h, P.span(nearInlandContinentalness, farInlandContinentalness), erosions[6], w, 0.0F, b);
                    }
                }
            }
        }

        private void addValleys(P w) {
            addSurfaceBiome(FROZEN_RANGE, FULL_RANGE, coastContinentalness, P.span(erosions[0], erosions[1]), w, 0.0F, w.max < 0L ? Biomes.STONY_SHORE : Biomes.FROZEN_RIVER);
            addSurfaceBiome(UNFROZEN_RANGE, FULL_RANGE, coastContinentalness, P.span(erosions[0], erosions[1]), w, 0.0F, w.max < 0L ? Biomes.STONY_SHORE : Biomes.RIVER);
            addSurfaceBiome(FROZEN_RANGE, FULL_RANGE, nearInlandContinentalness, P.span(erosions[0], erosions[1]), w, 0.0F, Biomes.FROZEN_RIVER);
            addSurfaceBiome(UNFROZEN_RANGE, FULL_RANGE, nearInlandContinentalness, P.span(erosions[0], erosions[1]), w, 0.0F, Biomes.RIVER);
            addSurfaceBiome(FROZEN_RANGE, FULL_RANGE, P.span(coastContinentalness, farInlandContinentalness), P.span(erosions[2], erosions[5]), w, 0.0F, Biomes.FROZEN_RIVER);
            addSurfaceBiome(UNFROZEN_RANGE, FULL_RANGE, P.span(coastContinentalness, farInlandContinentalness), P.span(erosions[2], erosions[5]), w, 0.0F, Biomes.RIVER);
            addSurfaceBiome(FROZEN_RANGE, FULL_RANGE, coastContinentalness, erosions[6], w, 0.0F, Biomes.FROZEN_RIVER);
            addSurfaceBiome(UNFROZEN_RANGE, FULL_RANGE, coastContinentalness, erosions[6], w, 0.0F, Biomes.RIVER);
            addSwamp(w, P.span(inlandContinentalness, farInlandContinentalness));
            addSurfaceBiome(FROZEN_RANGE, FULL_RANGE, P.span(inlandContinentalness, farInlandContinentalness), erosions[6], w, 0.0F, Biomes.FROZEN_RIVER);
            for (int i = 0; i < temperatures.length; ++i) {
                P t = temperatures[i];
                for (int j = 0; j < humidities.length; ++j) {
                    P h = humidities[j];
                    int b = pickMiddleBiomeOrBadlandsIfHot(i, j, w);
                    addSurfaceBiome(t, h, P.span(midInlandContinentalness, farInlandContinentalness), P.span(erosions[0], erosions[1]), w, 0.0F, b);
                }
            }
        }

        /**
         * The swamp line of the mid slice, the low slice and the valleys: 1.18.2's over every unfrozen temperature;
         * 1.19's (WILD) swamp in the two cool ones and the mangrove swamp in the two warm ones.
         */
        private void addSwamp(P w, P continentalness) {
            if ((editions & Editions.WILD) == 0) {
                addSurfaceBiome(UNFROZEN_RANGE, FULL_RANGE, continentalness, erosions[6], w, 0.0F, Biomes.SWAMP);
                return;
            }
            addSurfaceBiome(P.span(temperatures[1], temperatures[2]), FULL_RANGE, continentalness, erosions[6], w, 0.0F, Biomes.SWAMP);
            addSurfaceBiome(P.span(temperatures[3], temperatures[4]), FULL_RANGE, continentalness, erosions[6], w, 0.0F, Biomes.MANGROVE_SWAMP);
        }

        private void addUndergroundBiomes() {
            // 26.4 (DEEP_FREEZE): the dripstone and sulfur caves' humidity -1 to 0.7 (the lush caves' range above it).
            boolean v264 = Editions.from264(editions);
            P caveHumidity = v264 ? P.span(-1.0F, 0.7F) : FULL_RANGE;
            addUndergroundBiome(FULL_RANGE, caveHumidity, P.span(0.8F, 1.0F), FULL_RANGE, FULL_RANGE, 0.0F, Biomes.DRIPSTONE_CAVES);
            addUndergroundBiome(FULL_RANGE, P.span(0.7F, 1.0F), FULL_RANGE, FULL_RANGE, FULL_RANGE, 0.0F, Biomes.LUSH_CAVES);
            if ((editions & Editions.GAME_DROPS) != 0) {
                // 26.2: coast to inland continentalness, the two highest erosions, weirdness -1.1 to -0.85.
                addUndergroundBiome(FULL_RANGE, caveHumidity, P.span(coastContinentalness, inlandContinentalness),
                    P.span(erosions[5], erosions[6]), P.span(-1.1F, -0.85F), 0.0F, Biomes.SULFUR_CAVES);
            }
            if ((editions & Editions.WILD) != 0) {
                // 1.19: addBottomBiome, depth the point 1.1.
                add(FULL_RANGE, FULL_RANGE, FULL_RANGE, P.span(erosions[0], erosions[1]), P.point(1.1F), FULL_RANGE, 0.0F, Biomes.DEEP_DARK);
            }
            if (v264) {
                // 26.4: after the deep dark, temperature -1 to -0.8, humidity -1 to 0.7, deep ocean to ocean
                // continentalness, any erosion, weirdness 0.5 to 1.1.
                addUndergroundBiome(P.span(-1.0F, -0.8F), P.span(-1.0F, 0.7F),
                    P.span(deepOceanContinentalness, oceanContinentalness), FULL_RANGE, P.span(0.5F, 1.1F), 0.0F,
                    Biomes.ICE_CAVES);
            }
        }

        private int pickMiddleBiome(int t, int h, P w) {
            if (w.max < 0L) {
                return MIDDLE_BIOMES[t][h];
            }
            int b = MIDDLE_BIOMES_VARIANT[t][h];
            return b == N ? MIDDLE_BIOMES[t][h] : b;
        }

        private int pickMiddleBiomeOrBadlandsIfHot(int t, int h, P w) {
            return t == 4 ? pickBadlandsBiome(h, w) : pickMiddleBiome(t, h, w);
        }

        private int pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(int t, int h, P w) {
            return t == 0 ? pickSlopeBiome(t, h, w) : pickMiddleBiomeOrBadlandsIfHot(t, h, w);
        }

        private int maybePickWindsweptSavannaBiome(int t, int h, P w, int b) {
            return t > 1 && h < 4 && w.max >= 0L ? Biomes.WINDSWEPT_SAVANNA : b;
        }

        private int pickShatteredCoastBiome(int t, int h, P w) {
            int b = w.max >= 0L ? pickMiddleBiome(t, h, w) : pickBeachBiome(t, h);
            return maybePickWindsweptSavannaBiome(t, h, w, b);
        }

        private int pickBeachBiome(int t, int h) {
            if (t == 0) {
                return Biomes.SNOWY_BEACH;
            }
            return t == 4 ? Biomes.DESERT : Biomes.BEACH;
        }

        private int pickBadlandsBiome(int h, P w) {
            if (h < 2) {
                if (editions != Editions.NONE) {
                    // 1.19 (and 1.20) swapped the two.
                    return w.max < 0L ? Biomes.BADLANDS : Biomes.ERODED_BADLANDS;
                }
                return w.max < 0L ? Biomes.ERODED_BADLANDS : Biomes.BADLANDS;
            }
            return h < 3 ? Biomes.BADLANDS : Biomes.WOODED_BADLANDS;
        }

        private int pickPlateauBiome(int t, int h, P w) {
            if (w.max < 0L) {
                return PLATEAU_BIOMES[t][h];
            }
            int b = PLATEAU_BIOMES_VARIANT[t][h];
            return b == N ? PLATEAU_BIOMES[t][h] : b;
        }

        private int pickPeakBiome(int t, int h, P w) {
            if (t <= 2) {
                return w.max < 0L ? Biomes.JAGGED_PEAKS : Biomes.FROZEN_PEAKS;
            }
            return t == 3 ? Biomes.STONY_PEAKS : pickBadlandsBiome(h, w);
        }

        private int pickSlopeBiome(int t, int h, P w) {
            if (t >= 3) {
                return pickPlateauBiome(t, h, w);
            }
            return h <= 1 ? Biomes.SNOWY_SLOPES : Biomes.GROVE;
        }

        private int pickShatteredBiome(int t, int h, P w) {
            int b = SHATTERED_BIOMES[t][h];
            return b == N ? pickMiddleBiome(t, h, w) : b;
        }

        private void addSurfaceBiome(P t, P h, P c, P e, P w, float offset, int biome) {
            add(t, h, c, e, P.point(0.0F), w, offset, biome);
            add(t, h, c, e, P.point(1.0F), w, offset, biome);
        }

        private void addUndergroundBiome(P t, P h, P c, P e, P w, float offset, int biome) {
            add(t, h, c, e, P.span(0.2F, 0.9F), w, offset, biome);
        }

        private void add(P t, P h, P c, P e, P d, P w, float offset, int biome) {
            Node n = new Node();
            P[] ps = {t, h, c, e, d, w};
            for (int i = 0; i < 6; i++) {
                n.min[i] = ps[i].min;
                n.max[i] = ps[i].max;
            }
            long o = quantize(offset);
            n.min[OFFSET] = o;
            n.max[OFFSET] = o;
            n.biome = biome;
            out.add(n);
        }
    }
}
