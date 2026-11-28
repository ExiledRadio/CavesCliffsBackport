package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * Biomes O' Plenty 1.18.2's overworld regions (its "common" and "rare" TerraBlender regions): 1.18's
 * OverworldBiomeBuilder slots (the same temperature, humidity, continentalness, erosion and weirdness cells) with
 * Biomes O' Plenty's biome tables in vanilla's places. A Biomes O' Plenty biome that is not there falls back the
 * way Biomes O' Plenty's own BiomeUtil.biomeOrFallback does, to the next biome named for that slot and in the end to
 * vanilla's.
 *
 * <p>Biomes O' Plenty biomes are named by their 1.18 names and resolved through {@link Resolver}; vanilla ones are
 * 1.18 ids ({@link Biomes}). The rare region is the common one with its middle biomes replaced by the rare table's
 * (muskeg, ominous woods, mystic grove, fungal jungle) where it names one. Underground biomes (spider nest, glowing
 * grotto) are left out: 1.12 has none.
 */
public final class BopRegionBuilder {

    /** No biome for a name: {@link Resolver#resolve} answers this, and the slot falls back. */
    public static final int MISSING = Integer.MIN_VALUE;

    /** Resolves a Biomes O' Plenty 1.18 name to a region value (a modded biome), or {@link #MISSING}. */
    public interface Resolver {
        /**
         * @param name  the 1.18 name, without namespace
         * @param home  the 1.18 biome the slot falls back to in the end without it (where a stand-in should fit)
         */
        int resolve(String name, int home);
    }

    private final Resolver resolver;
    private final boolean rare;
    private final RegionTree.Builder out;

    private BopRegionBuilder(Resolver resolver, boolean rare, RegionTree.Builder out) {
        this.resolver = resolver;
        this.rare = rare;
        this.out = out;
    }

    /** Adds a region's leaves to out. */
    public static void build(Resolver resolver, boolean rare, RegionTree.Builder out) {
        new BopRegionBuilder(resolver, rare, out).addBiomes();
    }

    // ---- parameters (OverworldBiomeBuilder's) ----

    private static long q(float v) {
        return Climate.quantize(v);
    }

    private static final class P {
        final long min;
        final long max;

        P(long min, long max) {
            this.min = min;
            this.max = max;
        }
    }

    private static P span(float a, float b) {
        return new P(q(a), q(b));
    }

    private static P span(P a, P b) {
        return new P(a.min, b.max);
    }

    private final P full = span(-1.0F, 1.0F);
    private final P[] temperatures = {span(-1.0F, -0.45F), span(-0.45F, -0.15F), span(-0.15F, 0.2F), span(0.2F, 0.55F),
        span(0.55F, 1.0F)};
    private final P[] humidities = {span(-1.0F, -0.35F), span(-0.35F, -0.1F), span(-0.1F, 0.1F), span(0.1F, 0.3F),
        span(0.3F, 1.0F)};
    private final P[] erosions = {span(-1.0F, -0.78F), span(-0.78F, -0.375F), span(-0.375F, -0.2225F),
        span(-0.2225F, 0.05F), span(0.05F, 0.45F), span(0.45F, 0.55F), span(0.55F, 1.0F)};
    private final P frozen = temperatures[0];
    private final P unfrozen = span(temperatures[1], temperatures[4]);
    private final P mushroomC = span(-1.2F, -1.05F);
    private final P deepOceanC = span(-1.05F, -0.455F);
    private final P oceanC = span(-0.455F, -0.19F);
    private final P coastC = span(-0.19F, -0.11F);
    private final P inlandC = span(-0.11F, 0.55F);
    private final P nearC = span(-0.11F, 0.03F);
    private final P midC = span(0.03F, 0.3F);
    private final P farC = span(0.3F, 1.0F);

    // ---- vanilla's tables (OverworldBiomeBuilder) ----

    private static final int N = -1;
    private static final int[][] OCEANS = {
        {Biomes.DEEP_FROZEN_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.DEEP_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN, Biomes.WARM_OCEAN},
        {Biomes.FROZEN_OCEAN, Biomes.COLD_OCEAN, Biomes.OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.WARM_OCEAN}};
    private static final int[][] MIDDLE = {
        {Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.TAIGA},
        {Biomes.PLAINS, Biomes.PLAINS, Biomes.FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA},
        {Biomes.FLOWER_FOREST, Biomes.PLAINS, Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.DARK_FOREST},
        {Biomes.SAVANNA, Biomes.SAVANNA, Biomes.FOREST, Biomes.JUNGLE, Biomes.JUNGLE},
        {Biomes.DESERT, Biomes.DESERT, Biomes.DESERT, Biomes.DESERT, Biomes.DESERT}};
    private static final int[][] MIDDLE_VARIANT = {
        {Biomes.ICE_SPIKES, N, Biomes.SNOWY_TAIGA, N, N},
        {N, N, N, N, Biomes.OLD_GROWTH_PINE_TAIGA},
        {Biomes.SUNFLOWER_PLAINS, N, N, Biomes.OLD_GROWTH_BIRCH_FOREST, N},
        {N, N, Biomes.PLAINS, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE},
        {N, N, N, N, N}};
    private static final int[][] PLATEAU = {
        {Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.SNOWY_TAIGA},
        {Biomes.MEADOW, Biomes.MEADOW, Biomes.FOREST, Biomes.TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA},
        {Biomes.MEADOW, Biomes.MEADOW, Biomes.MEADOW, Biomes.MEADOW, Biomes.DARK_FOREST},
        {Biomes.SAVANNA_PLATEAU, Biomes.SAVANNA_PLATEAU, Biomes.FOREST, Biomes.FOREST, Biomes.JUNGLE},
        {Biomes.BADLANDS, Biomes.BADLANDS, Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.WOODED_BADLANDS}};
    private static final int[][] EXTREME_HILLS = {
        {Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_FOREST},
        {Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_FOREST},
        {Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_FOREST},
        {N, N, N, N, N},
        {N, N, N, N, N}};

    // ---- Biomes O' Plenty's tables (by 1.18 name) ----

    private static final String X = null;
    private static final String[][] ISLANDS = {
        {"rainbow_hills", "rainbow_hills", "rainbow_hills", "rainbow_hills", "rainbow_hills"},
        {X, X, X, X, X},
        {"origin_valley", "origin_valley", "origin_valley", "origin_valley", "origin_valley"},
        {"tropics", "tropics", "tropics", "tropics", "tropics"},
        {"tropics", "tropics", "tropics", "tropics", "tropics"}};
    private static final String[][] MIDDLE_BOP = {
        {"cold_desert", "tundra", "snowy_coniferous_forest", "maple_woods", "dead_forest"},
        {"seasonal_forest", "coniferous_forest", "coniferous_forest", "field", "field"},
        {"prairie", "shrubland", "shrubland", "grassland", "grassland"},
        {"scrubland", "mediterranean_forest", "woodland", "rainforest", "rainforest"},
        {"wasteland", "wasteland", "dryland", "lush_desert", "lush_desert"}};
    private static final String[][] MIDDLE_VARIANT_BOP = {
        {X, X, "snowy_fir_clearing", "snowy_maple_woods", "old_growth_dead_forest"},
        {"pumpkin_patch", "fir_clearing", X, X, "forested_field"},
        {"pasture", "lavender_field", "redwood_forest", "clover_patch", "cherry_blossom_grove"},
        {X, X, "old_growth_woodland", X, "rocky_rainforest"},
        {X, "wooded_wasteland", X, "lush_savanna", "volcanic_plains"}};
    private static final String[][] SWAMP_BOP = {
        {X, X, X, X, X},
        {"bog", "bog", "wetland", "wetland", "wetland"},
        {"marsh", "marsh", "marsh", "marsh", "marsh"},
        {"bayou", "bayou", "bayou", "floodplain", "floodplain"},
        {X, X, X, X, X}};
    private static final String[][] PLATEAU_BOP = {
        {"cold_desert", "tundra", "snowy_fir_clearing", "maple_woods", "dead_forest"},
        {"seasonal_forest", "coniferous_forest", "highland", "highland", "field"},
        {"orchard", "rocky_shrubland", "rocky_shrubland", "highland", "highland"},
        {"wooded_scrubland", "mediterranean_forest", "woodland", "rainforest", "rocky_rainforest"},
        {"wasteland", "wasteland", "dryland", "lush_desert", "lush_desert"}};
    private static final String[][] PLATEAU_VARIANT_BOP = {
        {X, X, X, "snowy_maple_woods", X},
        {"boreal_forest", X, X, X, X},
        {X, "lavender_forest", X, "highland_moor", "bamboo_grove"},
        {X, X, X, "rocky_rainforest", X},
        {X, X, X, "lush_savanna", "volcano"}};
    private static final String[][] EXTREME_HILLS_BOP = {
        {X, X, X, X, X},
        {X, X, X, X, X},
        {X, X, X, X, X},
        {X, X, X, X, "rocky_rainforest"},
        {X, X, X, X, "volcano"}};
    private static final String[][] RARE_BOP = {
        {X, X, X, "muskeg", X},
        {X, X, "ominous_woods", X, X},
        {X, X, X, X, "mystic_grove"},
        {X, X, X, "fungal_jungle", X},
        {X, X, X, X, X}};

    /**
     * The first of the names there is, else the fallback (BiomeUtil.biomeOrFallback); home is the 1.18 biome the
     * chain ends at, which a stand-in for a missing name should fit.
     */
    private int or(String a, String b, int fallback, int home) {
        int v = a == null ? MISSING : resolver.resolve(a, home);
        if (v == MISSING && b != null) {
            v = resolver.resolve(b, home);
        }
        return v == MISSING ? fallback : v;
    }

    private int or(String a, int fallback, int home) {
        return or(a, null, fallback, home);
    }

    /** A chain ending at a 1.18 biome, its own home. */
    private int or(String a, int vanilla) {
        return or(a, null, vanilla, vanilla);
    }

    // ---- the builder ----

    private void addBiomes() {
        addOffCoast();
        addInland();
    }

    private void surface(P t, P h, P c, P e, P w, int biome) {
        long[] min = {t.min, h.min, c.min, e.min, 0L, w.min, 0L};
        long[] max = {t.max, h.max, c.max, e.max, 0L, w.max, 0L};
        out.add(min, max, biome);
        min = min.clone();
        max = max.clone();
        min[Climate.DEPTH] = q(1.0F);
        max[Climate.DEPTH] = q(1.0F);
        out.add(min, max, biome);
    }

    private void addOffCoast() {
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                surface(temperatures[i], humidities[j], mushroomC, full, full, or(ISLANDS[i][j], Biomes.MUSHROOM_FIELDS));
            }
            surface(temperatures[i], full, deepOceanC, full, full, OCEANS[0][i]);
            surface(temperatures[i], full, oceanC, full, full, OCEANS[1][i]);
        }
    }

    private void addInland() {
        mid(span(-1.0F, -0.93333334F));
        high(span(-0.93333334F, -0.7666667F));
        peaks(span(-0.7666667F, -0.56666666F));
        high(span(-0.56666666F, -0.4F));
        mid(span(-0.4F, -0.26666668F));
        low(span(-0.26666668F, -0.05F));
        valleys(span(-0.05F, 0.05F));
        low(span(0.05F, 0.26666668F));
        mid(span(0.26666668F, 0.4F));
        high(span(0.4F, 0.56666666F));
        peaks(span(0.56666666F, 0.7666667F));
        high(span(0.7666667F, 0.93333334F));
        mid(span(0.93333334F, 1.0F));
    }

    private static boolean negative(P w) {
        return w.max < 0L;
    }

    private void peaks(P w) {
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                P t = temperatures[i];
                P h = humidities[j];
                int middle = rareOr(i, j, w, middleBop(i, j, w));
                int middleOrSlope = rareOr(i, j, w, middleOrSlopeBop(i, j, w));
                int plateau = plateauBop(i, j, w);
                int hills = extremeHillsVanilla(i, j, w);
                int hillsBop = extremeHillsBop(i, j, w);
                int shattered = maybeShattered(i, j, w, hills);
                int peak = peakBop(i, j, w);
                surface(t, h, span(coastC, farC), erosions[0], w, peak);
                surface(t, h, span(coastC, nearC), erosions[1], w, middleOrSlope);
                surface(t, h, span(midC, farC), erosions[1], w, peak);
                surface(t, h, span(coastC, nearC), span(erosions[2], erosions[3]), w, middle);
                surface(t, h, span(midC, farC), erosions[2], w, plateau);
                surface(t, h, midC, erosions[3], w, middle);
                surface(t, h, farC, erosions[3], w, plateau);
                surface(t, h, span(coastC, farC), erosions[4], w, middle);
                surface(t, h, span(coastC, nearC), erosions[5], w, shattered);
                surface(t, h, span(midC, farC), erosions[5], w, hillsBop);
                surface(t, h, span(coastC, farC), erosions[6], w, middle);
            }
        }
    }

    private void high(P w) {
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                P t = temperatures[i];
                P h = humidities[j];
                int middleVanilla = middleVanilla(i, j, w);
                int middle = rareOr(i, j, w, middleBop(i, j, w));
                int middleOrSlope = rareOr(i, j, w, middleOrSlopeBop(i, j, w));
                int plateau = plateauBop(i, j, w);
                int hillsBop = extremeHillsBop(i, j, w);
                int shattered = maybeShattered(i, j, w, middleVanilla);
                int slope = slopeBop(i, j, w);
                int peak = peakBop(i, j, w);
                surface(t, h, coastC, span(erosions[0], erosions[1]), w, middle);
                surface(t, h, nearC, erosions[0], w, slope);
                surface(t, h, span(midC, farC), erosions[0], w, peak);
                surface(t, h, nearC, erosions[1], w, middleOrSlope);
                surface(t, h, span(midC, farC), erosions[1], w, slope);
                surface(t, h, span(coastC, nearC), span(erosions[2], erosions[3]), w, middle);
                surface(t, h, span(midC, farC), erosions[2], w, plateau);
                surface(t, h, midC, erosions[3], w, middle);
                surface(t, h, farC, erosions[3], w, plateau);
                surface(t, h, span(coastC, farC), erosions[4], w, middle);
                surface(t, h, span(coastC, nearC), erosions[5], w, shattered);
                surface(t, h, span(midC, farC), erosions[5], w, hillsBop);
                surface(t, h, span(coastC, farC), erosions[6], w, middle);
            }
        }
    }

    private void mid(P w) {
        surface(full, full, coastC, span(erosions[0], erosions[2]), w, Biomes.STONY_SHORE);
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                P t = temperatures[i];
                P h = humidities[j];
                int middleVanilla = middleVanilla(i, j, w);
                int middle = rareOr(i, j, w, middleBop(i, j, w));
                int middleOrSlope = rareOr(i, j, w, middleOrSlopeBop(i, j, w));
                int hillsBop = extremeHillsBop(i, j, w);
                int plateau = plateauBop(i, j, w);
                int beach = beach(i, j);
                int shattered = maybeShattered(i, j, w, middleVanilla);
                int shatteredCoast = shatteredCoast(i, j, w);
                int slope = slopeBop(i, j, w);
                int swamp = swampBop(i, j, w);
                surface(t, h, span(nearC, farC), erosions[0], w, slope);
                surface(t, h, span(nearC, midC), erosions[1], w, middleOrSlope);
                surface(t, h, farC, erosions[1], w, i == 0 ? slope : plateau);
                surface(t, h, nearC, erosions[2], w, middle);
                surface(t, h, midC, erosions[2], w, middle);
                surface(t, h, farC, erosions[2], w, plateau);
                surface(t, h, span(coastC, nearC), erosions[3], w, middle);
                surface(t, h, span(midC, farC), erosions[3], w, middle);
                if (negative(w)) {
                    surface(t, h, coastC, erosions[4], w, beach);
                    surface(t, h, span(nearC, farC), erosions[4], w, middle);
                } else {
                    surface(t, h, span(coastC, farC), erosions[4], w, middle);
                }
                surface(t, h, coastC, erosions[5], w, shatteredCoast);
                surface(t, h, nearC, erosions[5], w, shattered);
                surface(t, h, span(midC, farC), erosions[5], w, hillsBop);
                surface(t, h, coastC, erosions[6], w, negative(w) ? beach : middle);
                surface(t, h, span(nearC, farC), erosions[6], w, i == 0 ? coldWet(middle, j) : swamp);
            }
        }
    }

    private void low(P w) {
        surface(full, full, coastC, span(erosions[0], erosions[2]), w, Biomes.STONY_SHORE);
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                P t = temperatures[i];
                P h = humidities[j];
                int middleVanilla = middleVanilla(i, j, w);
                int middle = rareOr(i, j, w, middleBop(i, j, w));
                int middleOrSlope = rareOr(i, j, w, middleOrSlopeBop(i, j, w));
                int beach = beach(i, j);
                int shattered = maybeShattered(i, j, w, middleVanilla);
                int shatteredCoast = shatteredCoast(i, j, w);
                int swamp = swampBop(i, j, w);
                surface(t, h, nearC, span(erosions[0], erosions[1]), w, middle);
                surface(t, h, span(midC, farC), span(erosions[0], erosions[1]), w, middleOrSlope);
                surface(t, h, nearC, span(erosions[2], erosions[3]), w, middle);
                surface(t, h, span(midC, farC), span(erosions[2], erosions[3]), w, middle);
                surface(t, h, coastC, span(erosions[3], erosions[4]), w, beach);
                surface(t, h, span(nearC, farC), erosions[4], w, middle);
                surface(t, h, coastC, erosions[5], w, shatteredCoast);
                surface(t, h, nearC, erosions[5], w, shattered);
                surface(t, h, span(midC, farC), erosions[5], w, middle);
                surface(t, h, coastC, erosions[6], w, beach);
                surface(t, h, span(nearC, farC), erosions[6], w, i == 0 ? coldWet(middle, j) : swamp);
            }
        }
    }

    private void valleys(P w) {
        surface(frozen, full, coastC, span(erosions[0], erosions[1]), w, negative(w) ? Biomes.STONY_SHORE : Biomes.FROZEN_RIVER);
        surface(unfrozen, full, coastC, span(erosions[0], erosions[1]), w, negative(w) ? Biomes.STONY_SHORE : Biomes.RIVER);
        surface(frozen, full, nearC, span(erosions[0], erosions[1]), w, Biomes.FROZEN_RIVER);
        surface(unfrozen, full, nearC, span(erosions[0], erosions[1]), w, Biomes.RIVER);
        surface(frozen, full, span(coastC, farC), span(erosions[2], erosions[5]), w, Biomes.FROZEN_RIVER);
        surface(unfrozen, full, span(coastC, farC), span(erosions[2], erosions[5]), w, Biomes.RIVER);
        surface(frozen, full, coastC, erosions[6], w, Biomes.FROZEN_RIVER);
        surface(unfrozen, full, coastC, erosions[6], w, Biomes.RIVER);
        surface(frozen, full, span(inlandC, farC), erosions[6], w, Biomes.FROZEN_RIVER);
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                P t = temperatures[i];
                P h = humidities[j];
                int middle = rareOr(i, j, w, middleBop(i, j, w));
                int swamp = swampBop(i, j, w);
                surface(t, h, span(midC, farC), span(erosions[0], erosions[1]), w, middle);
                if (i != 0) {
                    surface(t, h, span(inlandC, farC), erosions[6], w, swamp);
                }
            }
        }
    }

    // ---- picks ----

    /** The rare region's pick in place of a middle pick: its rare table, else that middle pick. */
    private int rareOr(int i, int j, P w, int common) {
        if (!rare) {
            return common;
        }
        // pickRareBiomeBOP: the rare biome, else the middle Biomes O' Plenty biome (not the slope one).
        return or(RARE_BOP[i][j], middleBop(i, j, w), middleVanilla(i, j, w));
    }

    /** The cold wet slot (temperature 0, highest erosion): the middle biome, or in the rare region muskeg first. */
    private int coldWet(int middle, int j) {
        return rare ? or("muskeg", middle, MIDDLE[0][j]) : middle;
    }

    private int middleVanilla(int i, int j, P w) {
        if (negative(w)) {
            return MIDDLE[i][j];
        }
        int v = MIDDLE_VARIANT[i][j];
        return v == N ? MIDDLE[i][j] : v;
    }

    private int middleBop(int i, int j, P w) {
        int middle = or(MIDDLE_BOP[i][j], MIDDLE[i][j]);
        if (negative(w)) {
            return middle;
        }
        return or(MIDDLE_VARIANT_BOP[i][j], middle, middleVanilla(i, j, w));
    }

    private int middleOrSlopeBop(int i, int j, P w) {
        return i == 0 ? slopeBop(i, j, w) : middleBop(i, j, w);
    }

    private int swampBop(int i, int j, P w) {
        int middle = middleBop(i, j, w);
        String s = SWAMP_BOP[i][j];
        if (s != null) {
            int v = resolver.resolve(s, Biomes.SWAMP);
            if (v != MISSING) {
                return v;
            }
        }
        // biomeOrFallback(swamp, middle BOP, SWAMP): the middle pick is always registered.
        return middle;
    }

    private int maybeShattered(int i, int j, P w, int hills) {
        return i > 1 && j < 4 && !negative(w) ? Biomes.WINDSWEPT_SAVANNA : hills;
    }

    private int shatteredCoast(int i, int j, P w) {
        int b = !negative(w) ? middleVanilla(i, j, w) : beach(i, j);
        return maybeShattered(i, j, w, b);
    }

    private int beach(int i, int j) {
        if (i == 0) {
            return Biomes.SNOWY_BEACH;
        } else if (i == 2 && j < 2) {
            return or("dune_beach", Biomes.BEACH);
        }
        return i == 4 ? Biomes.DESERT : Biomes.BEACH;
    }

    private int badlands(int j, P w) {
        if (j < 2) {
            return negative(w) ? Biomes.ERODED_BADLANDS : Biomes.BADLANDS;
        }
        return j < 3 ? Biomes.BADLANDS : Biomes.WOODED_BADLANDS;
    }

    private int plateauBop(int i, int j, P w) {
        if (negative(w)) {
            return or(PLATEAU_BOP[i][j], PLATEAU[i][j]);
        }
        return or(PLATEAU_VARIANT_BOP[i][j], PLATEAU_BOP[i][j], PLATEAU[i][j], PLATEAU[i][j]);
    }

    private int peakVanilla(int i, int j, P w) {
        if (i <= 2) {
            return negative(w) ? Biomes.JAGGED_PEAKS : Biomes.FROZEN_PEAKS;
        }
        return i == 3 ? Biomes.STONY_PEAKS : badlands(j, w);
    }

    private int peakBop(int i, int j, P w) {
        return steep(i, j, peakVanilla(i, j, w), peakVanilla(i, j, w));
    }

    private int slopeBop(int i, int j, P w) {
        return steep(i, j, plateauBop(i, j, w), PLATEAU[i][j]);
    }

    /** pickPeakBiomeBOP's and pickSlopeBiomeBOP's shared part. */
    private int steep(int i, int j, int fallback, int home) {
        if (i == 1) {
            return or("crag", fallback, home);
        } else if (i == 2) {
            return or("jade_cliffs", fallback, home);
        } else if (i == 3 && j >= 3) {
            return or("rocky_rainforest", fallback, home);
        } else if (i == 4 && j == 4) {
            return or("volcano", fallback, home);
        }
        return fallback;
    }

    private int extremeHillsVanilla(int i, int j, P w) {
        int v = EXTREME_HILLS[i][j];
        return v == N ? middleVanilla(i, j, w) : v;
    }

    private int extremeHillsBop(int i, int j, P w) {
        return or(EXTREME_HILLS_BOP[i][j], extremeHillsVanilla(i, j, w));
    }
}
