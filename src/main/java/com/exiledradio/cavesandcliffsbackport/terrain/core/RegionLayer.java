package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * Which biome region a quart column is in, as TerraBlender (1.18.2, the version Biomes O' Plenty 1.18.2 places its
 * biomes through) decides it: 1.17's layered noise (LCG contexts, cached areas). A region drawn by weight at 1:256
 * quart scale, then one fuzzy zoom (context 2000), three normal zooms (2001-2003) and {@code regionSize} more
 * (1001...; TerraBlender's overworld_region_size, 3 by default). Region 0 is vanilla's.
 *
 * <p>Not thread safe: the contexts and caches are mutable, as 1.17's are. One per thread.
 */
public final class RegionLayer {

    /** LinearCongruentialGenerator.next. */
    static long next(long a, long b) {
        a *= a * 6364136223846793005L + 1442695040888963407L;
        return a + b;
    }

    private static final class Context {
        final long seed;
        long rval;

        Context(long worldSeed, long modifier) {
            long i = next(modifier, modifier);
            i = next(i, modifier);
            i = next(i, modifier);
            long j = next(worldSeed, i);
            j = next(j, i);
            this.seed = next(j, i);
        }

        void initRandom(long x, long y) {
            long i = seed;
            i = next(i, x);
            i = next(i, y);
            i = next(i, x);
            i = next(i, y);
            rval = i;
        }

        int nextRandom(int bound) {
            int i = (int) Math.floorMod(rval >> 24, (long) bound);
            rval = next(rval, seed);
            return i;
        }

        int random(int a, int b) {
            return nextRandom(2) == 0 ? a : b;
        }

        int random(int a, int b, int c, int d) {
            int i = nextRandom(4);
            return i == 0 ? a : i == 1 ? b : i == 2 ? c : d;
        }
    }

    /** A layer: its own context and a direct-mapped cache, as Area. */
    private abstract static class Layer {
        final Context context;
        final long[] keys;
        final int[] values;
        final int mask;

        Layer(Context context, int cache) {
            this.context = context;
            int size = Integer.highestOneBit(Math.max(1, cache - 1)) << 1;
            this.mask = size - 1;
            this.keys = new long[size];
            java.util.Arrays.fill(keys, Long.MIN_VALUE);
            this.values = new int[size];
        }

        final int get(int x, int z) {
            long key = (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
            int idx = (int) mix(key) & mask;
            if (keys[idx] == key) {
                return values[idx];
            }
            context.initRandom(x, z);
            int v = apply(x, z);
            keys[idx] = key;
            values[idx] = v;
            return v;
        }

        abstract int apply(int x, int z);

        /** HashCommon.mix. */
        private static long mix(long x) {
            long h = x * -7046029254386353131L;
            h ^= h >>> 32;
            return h ^ h >>> 16;
        }
    }

    private static final class Initial extends Layer {
        final int[] regions;
        final int[] weights;
        final int total;

        Initial(Context c, int[] regions, int[] weights) {
            super(c, 25);
            this.regions = regions;
            this.weights = weights;
            int t = 0;
            for (int w : weights) {
                t += w;
            }
            this.total = t;
        }

        @Override
        int apply(int x, int z) {
            if (total == 0) {
                return 0;
            }
            int i = context.nextRandom(total);
            for (int k = 0; k < weights.length; k++) {
                i -= weights[k];
                if (i < 0) {
                    return regions[k];
                }
            }
            return 0;
        }
    }

    private static final class Zoom extends Layer {
        final Layer parent;
        final boolean fuzzy;

        Zoom(Context c, Layer parent, boolean fuzzy) {
            super(c, Math.min(1024, parent.mask + 1 << 2));
            this.parent = parent;
            this.fuzzy = fuzzy;
        }

        @Override
        int apply(int x, int y) {
            int i = parent.get(x >> 1, y >> 1);
            context.initRandom((long) (x >> 1 << 1), (long) (y >> 1 << 1));
            int j = x & 1;
            int k = y & 1;
            if (j == 0 && k == 0) {
                return i;
            }
            int l = parent.get(x >> 1, y + 1 >> 1);
            int i1 = context.random(i, l);
            if (j == 0 && k == 1) {
                return i1;
            }
            int j1 = parent.get(x + 1 >> 1, y >> 1);
            int k1 = context.random(i, j1);
            if (j == 1 && k == 0) {
                return k1;
            }
            int l1 = parent.get(x + 1 >> 1, y + 1 >> 1);
            return fuzzy ? context.random(i, j1, l, l1) : modeOrRandom(i, j1, l, l1);
        }

        private int modeOrRandom(int a, int b, int c, int d) {
            if (b == c && c == d) {
                return b;
            } else if (a == b && a == c) {
                return a;
            } else if (a == b && a == d) {
                return a;
            } else if (a == c && a == d) {
                return a;
            } else if (a == b && c != d) {
                return a;
            } else if (a == c && b != d) {
                return a;
            } else if (a == d && b != c) {
                return a;
            } else if (b == c && a != d) {
                return b;
            } else if (b == d && a != c) {
                return b;
            } else {
                return c == d && a != b ? c : context.random(a, b, c, d);
            }
        }
    }

    private final Layer top;

    /**
     * @param regions the region indices in TerraBlender's registration order (0 vanilla first), with their weights;
     *                regions without biomes are left out, as InitialLayer leaves them out
     */
    public RegionLayer(long worldSeed, int[] regions, int[] weights, int regionSize) {
        Layer l = new Initial(new Context(worldSeed, 1L), regions, weights);
        l = new Zoom(new Context(worldSeed, 2000L), l, true);
        for (int i = 0; i < 3; i++) {
            l = new Zoom(new Context(worldSeed, 2001L + i), l, false);
        }
        for (int i = 0; i < regionSize; i++) {
            l = new Zoom(new Context(worldSeed, 1001L + i), l, false);
        }
        this.top = l;
    }

    /** The region at quart qx, qz. */
    public int region(int qx, int qz) {
        return top.get(qx, qz);
    }
}
