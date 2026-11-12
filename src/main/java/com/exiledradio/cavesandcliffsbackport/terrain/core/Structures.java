package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1.18.2's overworld structure placement: which chunks start which structure (ChunkGenerator.createStructures with
 * data/worldgen/StructureSets), for a seed, as vanilla decides it before any piece is placed.
 *
 * <p>A structure set's placement names the candidate chunks (RandomSpreadStructurePlacement: one per spacing x
 * spacing cell; ConcentricRingsStructurePlacement: strongholds' 128 ring positions, each moved to a nearby biome
 * that can hold one). At a candidate, createStructures tries the set's structures (more than one: a weighted draw,
 * dropping each that fails) and each structure's own start check decides: the biome at the start position (the
 * chunk middle at the surface, or the jigsaw start piece's center, the ruined portal's chosen height, ...), heights
 * at corners (pyramids, temples, mansions), a probability (mineshafts, buried treasure), the biomes around
 * (monuments), no village nearby (outposts). Every check here is the vanilla one with its own random draws; the
 * jigsaw start templates' and ruined portals' boxes come from vanilla's template files (StructureTables).
 *
 * <p>Heights are the noise fill's base column (getBaseHeight: WORLD_SURFACE_WG or OCEAN_FLOOR_WG), biomes the
 * biome source's answer at a quart (cold search, see Climate). Pieces themselves are not built here.
 *
 * One instance per thread.
 */
public final class Structures {
    public static final int VILLAGES = 0;
    public static final int DESERT_PYRAMIDS = 1;
    public static final int IGLOOS = 2;
    public static final int JUNGLE_TEMPLES = 3;
    public static final int SWAMP_HUTS = 4;
    public static final int PILLAGER_OUTPOSTS = 5;
    public static final int OCEAN_MONUMENTS = 6;
    public static final int WOODLAND_MANSIONS = 7;
    public static final int BURIED_TREASURES = 8;
    public static final int MINESHAFTS = 9;
    public static final int RUINED_PORTALS = 10;
    public static final int SHIPWRECKS = 11;
    public static final int OCEAN_RUINS = 12;
    public static final int STRONGHOLDS = 13;
    public static final int SETS = 14;

    public static final String[] SET_NAMES = {"villages", "desert_pyramids", "igloos", "jungle_temples", "swamp_huts",
        "pillager_outposts", "ocean_monuments", "woodland_mansions", "buried_treasures", "mineshafts",
        "ruined_portals", "shipwrecks", "ocean_ruins", "strongholds"};
    /** Per set, its structures in the set's order (the index a start reports). */
    public static final String[][] STRUCTURE_NAMES = {
        {"village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga"},
        {"desert_pyramid"}, {"igloo"}, {"jungle_pyramid"}, {"swamp_hut"}, {"pillager_outpost"}, {"monument"},
        {"mansion"}, {"buried_treasure"}, {"mineshaft", "mineshaft_mesa"},
        {"ruined_portal", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_swamp",
            "ruined_portal_mountain", "ruined_portal_ocean", "ruined_portal_nether"},
        {"shipwreck", "shipwreck_beached"}, {"ocean_ruin_cold", "ocean_ruin_warm"}, {"stronghold"}};

    // RandomSpreadStructurePlacement: spacing, separation, triangular spread, salt (strongholds: rings).
    private static final int[] SPACING = {34, 32, 32, 32, 32, 32, 32, 80, 1, 1, 40, 24, 20, 0};
    private static final int[] SEPARATION = {8, 8, 8, 8, 8, 8, 5, 20, 0, 0, 15, 4, 8, 0};
    private static final boolean[] TRIANGULAR = {false, false, false, false, false, false, true, true, false, false,
        false, false, false, false};
    private static final int[] SALT = {10387312, 14357617, 14357618, 14357619, 14357620, 165745296, 10387313, 10387319,
        0, 0, 34222645, 165745295, 14357621, 0};
    // ConcentricRingsStructurePlacement(32, 3, 128).
    private static final int RING_DISTANCE = 32;
    private static final int RING_SPREAD = 3;
    private static final int RING_COUNT = 128;

    private static final int SEA_LEVEL = OverworldNoise.SEA_LEVEL;
    private static final int MIN_Y = OverworldNoise.MIN_Y;
    private static final int HEIGHT = OverworldNoise.HEIGHT;

    private final long seed;
    private final BiomeSource biomes;
    private final ChunkFiller filler;
    private final LegacyRandom random = new LegacyRandom(0L);
    private final LegacyRandom pick = new LegacyRandom(0L);
    /** [set][structure][biome]: the structure's biome tag. */
    private final boolean[][][] allowed;
    private final boolean[] oceanOrRiver = new boolean[256];
    /** Start pools expanded: per pool, per entry, the offset into the run table, or -1 for the empty element. */
    private final int[][] poolEntries;
    private final char[] column = new char[HEIGHT];
    private int[] rings;

    public Structures(OverworldNoise noise, ColumnCache columns, BiomeSource biomes) {
        this.seed = noise.seed;
        this.biomes = biomes;
        this.filler = new ChunkFiller(noise, columns);
        allowed = new boolean[SETS][][];
        for (int s = 0; s < SETS; s++) {
            int[][] tags = StructureTables.BIOMES[s];
            allowed[s] = new boolean[tags.length][256];
            for (int i = 0; i < tags.length; i++) {
                for (int b : tags[i]) {
                    allowed[s][i][b] = true;
                }
            }
        }
        for (int b : StructureTables.OCEAN_OR_RIVER) {
            oceanOrRiver[b] = true;
        }
        poolEntries = new int[StructureTables.START_POOLS.length][];
        for (int p = 0; p < poolEntries.length; p++) {
            int[] runs = StructureTables.START_POOLS[p];
            int total = 0;
            for (int at = 0; at < runs.length; at += 19) {
                total += runs[at];
            }
            int[] entries = new int[total];
            int n = 0;
            for (int at = 0; at < runs.length; at += 19) {
                for (int c = 0; c < runs[at]; c++) {
                    entries[n++] = runs[at + 1] == 0 ? -1 : at + 2;
                }
            }
            poolEntries[p] = entries;
        }
    }

    // ---- placement ----

    /** RandomSpreadStructurePlacement.getPotentialFeatureChunk: the candidate of the cell holding chunk cx, cz, packed. */
    public long potentialChunk(int set, int cx, int cz) {
        int i = SPACING[set];
        int j = SEPARATION[set];
        int k = Math.floorDiv(cx, i);
        int l = Math.floorDiv(cz, i);
        LegacyRandom r = random;
        r.setLargeFeatureWithSalt(seed, k, l, SALT[set]);
        int i1 = i - j;
        int j1 = spread(r, i1, TRIANGULAR[set]);
        int k1 = spread(r, i1, TRIANGULAR[set]);
        return pack(k * i + j1, l * i + k1);
    }

    private static int spread(LegacyRandom r, int bound, boolean triangular) {
        return triangular ? (r.nextInt(bound) + r.nextInt(bound)) / 2 : r.nextInt(bound);
    }

    /** StructurePlacement.isFeatureChunk. */
    public boolean isFeatureChunk(int set, int cx, int cz) {
        if (set == STRONGHOLDS) {
            int[] r = ringPositions();
            for (int i = 0; i < r.length; i += 2) {
                if (r[i] == cx && r[i + 1] == cz) {
                    return true;
                }
            }
            return false;
        }
        return potentialChunk(set, cx, cz) == pack(cx, cz);
    }

    /**
     * The structure of set that starts at chunk cx, cz (its index in STRUCTURE_NAMES[set]), or -1 if none: the
     * placement, then createStructures' choice among the set's structures.
     */
    public int startAt(int set, int cx, int cz) {
        return isFeatureChunk(set, cx, cz) ? choose(set, cx, cz) : -1;
    }

    /**
     * Every start of set in chunks minCx..maxCx-1, minCz..maxCz-1: cx, cz, structure index per start, in the order
     * of the placement's cells (x outer, z inner). Strongholds: every ring position, whatever the area.
     */
    public List<int[]> starts(int set, int minCx, int minCz, int maxCx, int maxCz) {
        List<int[]> out = new ArrayList<>();
        if (set == STRONGHOLDS) {
            int[] r = ringPositions();
            for (int i = 0; i < r.length; i += 2) {
                out.add(new int[]{r[i], r[i + 1], 0});
            }
            return out;
        }
        int s = SPACING[set];
        for (int gx = Math.floorDiv(minCx, s); gx <= Math.floorDiv(maxCx - 1, s); gx++) {
            for (int gz = Math.floorDiv(minCz, s); gz <= Math.floorDiv(maxCz - 1, s); gz++) {
                long p = potentialChunk(set, gx * s, gz * s);
                int x = (int) (p >> 32);
                int z = (int) p;
                if (x >= minCx && x < maxCx && z >= minCz && z < maxCz) {
                    int chosen = choose(set, x, z);
                    if (chosen >= 0) {
                        out.add(new int[]{x, z, chosen});
                    }
                }
            }
        }
        return out;
    }

    private static long pack(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }

    /** createStructures at a feature chunk. */
    private int choose(int set, int cx, int cz) {
        int[] weights = StructureTables.WEIGHTS[set];
        if (weights.length == 1) {
            return tryStart(set, 0, cx, cz) ? 0 : -1;
        }
        int n = weights.length;
        int[] remaining = new int[n];
        for (int i = 0; i < n; i++) {
            remaining[i] = i;
        }
        LegacyRandom r = pick;
        r.setLargeFeatureSeed(seed, cx, cz);
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        while (n > 0) {
            int j = r.nextInt(total);
            int k = 0;
            for (int i = 0; i < n; i++) {
                j -= weights[remaining[i]];
                if (j < 0) {
                    break;
                }
                ++k;
            }
            int e = remaining[k];
            if (tryStart(set, e, cx, cz)) {
                return e;
            }
            System.arraycopy(remaining, k + 1, remaining, k, n - k - 1);
            n--;
            total -= weights[e];
        }
        return -1;
    }

    // ---- the structures' start checks (pieceGeneratorSupplier) ----

    private boolean tryStart(int set, int index, int cx, int cz) {
        boolean[] tag = allowed[set][index];
        int midX = (cx << 4) + 8;
        int midZ = (cz << 4) + 8;
        switch (set) {
            case VILLAGES:
                return jigsaw(index, cx, cz, tag);
            case PILLAGER_OUTPOSTS:
                return outpostLocation(cx, cz) && jigsaw(5, cx, cz, tag);
            case DESERT_PYRAMIDS:
                return biomeOnTop(midX, midZ, false, tag) && lowestY(cx, cz, 21, 21) >= SEA_LEVEL;
            case JUNGLE_TEMPLES:
                return biomeOnTop(midX, midZ, false, tag) && lowestY(cx, cz, 12, 15) >= SEA_LEVEL;
            case IGLOOS:
            case SWAMP_HUTS:
                return biomeOnTop(midX, midZ, false, tag);
            case OCEAN_MONUMENTS:
                return allOceanOrRiver((cx << 4) + 9, SEA_LEVEL, (cz << 4) + 9, 29) && biomeOnTop(midX, midZ, true, tag);
            case WOODLAND_MANSIONS:
                return mansion(cx, cz, tag);
            case BURIED_TREASURES: {
                LegacyRandom r = random;
                r.setLargeFeatureWithSalt(seed, cx, cz, 10387320);
                return r.nextFloat() < 0.01F && biomeOnTop(midX, midZ, true, tag);
            }
            case MINESHAFTS: {
                LegacyRandom r = random;
                r.setLargeFeatureSeed(seed, cx, cz);
                return !(r.nextDouble() >= (double) 0.004F) && tag[biomes.biome(midX >> 2, 50 >> 2, midZ >> 2)];
            }
            case RUINED_PORTALS:
                return ruinedPortal(index, cx, cz, tag);
            case SHIPWRECKS:
                // shipwreck_beached checks the world surface, shipwreck the ocean floor.
                return biomeOnTop(midX, midZ, index == 0, tag);
            case OCEAN_RUINS:
                return biomeOnTop(midX, midZ, true, tag);
            case STRONGHOLDS:
                return true;
            default:
                throw new IllegalArgumentException("set " + set);
        }
    }

    /** getFirstOccupiedHeight: getBaseHeight - 1. */
    private int firstOccupied(int x, int z, boolean oceanFloor) {
        return filler.baseHeight(x, z, oceanFloor) - 1;
    }

    /** PieceGeneratorSupplier.Context.validBiomeOnTop at block x, z. */
    private boolean biomeOnTop(int x, int z, boolean oceanFloor, boolean[] tag) {
        int k = firstOccupied(x, z, oceanFloor);
        return tag[biomes.biome(x >> 2, k >> 2, z >> 2)];
    }

    /** Context.getLowestY(dx, dz): the lowest of getCornerHeights(minX, dx, minZ, dz). */
    private int lowestY(int cx, int cz, int dx, int dz) {
        int i = cx << 4;
        int j = cz << 4;
        int a = firstOccupied(i, j, false);
        int b = firstOccupied(i, j + dz, false);
        int c = firstOccupied(i + dx, j, false);
        int d = firstOccupied(i + dx, j + dz, false);
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    /** BiomeSource.getBiomesWithin: every biome in the quart box around x, y, z is ocean or river. */
    private boolean allOceanOrRiver(int x, int y, int z, int r) {
        int i = (x - r) >> 2;
        int j = (y - r) >> 2;
        int k = (z - r) >> 2;
        int l = (x + r) >> 2;
        int i1 = (y + r) >> 2;
        int j1 = (z + r) >> 2;
        for (int qz = k; qz <= j1; ++qz) {
            for (int qx = i; qx <= l; ++qx) {
                for (int qy = j; qy <= i1; ++qy) {
                    if (!oceanOrRiver[biomes.biome(qx, qy, qz)]) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** PillagerOutpostFeature.checkLocation. */
    private boolean outpostLocation(int cx, int cz) {
        int i = cx >> 4;
        int j = cz >> 4;
        LegacyRandom r = random;
        r.setSeed((long) (i ^ j << 4) ^ seed);
        r.nextInt();
        if (r.nextInt(5) != 0) {
            return false;
        }
        // !hasFeatureChunkInRange(VILLAGES, seed, cx, cz, 10)
        for (int x = cx - 10; x <= cx + 10; ++x) {
            for (int z = cz - 10; z <= cz + 10; ++z) {
                if (isFeatureChunk(VILLAGES, x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * JigsawPlacement.addPieces' start check: the start pool's random template and rotation at the chunk's
     * corner (y 0, the height from the heightmap), the biome at its box center on the surface.
     */
    private boolean jigsaw(int pool, int cx, int cz, boolean[] tag) {
        LegacyRandom r = random;
        r.setLargeFeatureSeed(seed, cx, cz);
        int rotation = r.nextInt(4);
        int[] entries = poolEntries[pool];
        int entry = entries[r.nextInt(entries.length)];
        if (entry < 0) {
            return false;
        }
        int[] runs = StructureTables.START_POOLS[pool];
        int o = entry + 1 + rotation * 4;
        int minX = (cx << 4) + runs[o];
        int maxX = (cx << 4) + runs[o + 1];
        int minZ = (cz << 4) + runs[o + 2];
        int maxZ = (cz << 4) + runs[o + 3];
        int i = (maxX + minX) / 2;
        int j = (maxZ + minZ) / 2;
        int k = filler.baseHeight(i, j, false);
        return tag[biomes.biome(i >> 2, k >> 2, j >> 2)];
    }

    /** WoodlandMansionFeature.pieceGeneratorSupplier's checks. */
    private boolean mansion(int cx, int cz, boolean[] tag) {
        LegacyRandom r = random;
        r.setLargeFeatureSeed(seed, cx, cz);
        int rotation = r.nextInt(4);
        int i = 5;
        int j = 5;
        if (rotation == 1) {
            i = -5;
        } else if (rotation == 2) {
            i = -5;
            j = -5;
        } else if (rotation == 3) {
            j = -5;
        }
        int k = (cx << 4) + 7;
        int l = (cz << 4) + 7;
        int a = firstOccupied(k, l, false);
        int b = firstOccupied(k, l + j, false);
        int c = firstOccupied(k + i, l, false);
        int d = firstOccupied(k + i, l + j, false);
        int i1 = Math.min(Math.min(a, b), Math.min(c, d));
        return i1 >= 60 && tag[biomes.biome(k >> 2, a >> 2, l >> 2)];
    }

    // Ruined portal vertical placements.
    private static final int ON_LAND_SURFACE = 0;
    private static final int PARTLY_BURIED = 1;
    private static final int ON_OCEAN_FLOOR = 2;
    private static final int IN_MOUNTAIN = 3;
    private static final int UNDERGROUND = 4;
    private static final int IN_NETHER = 5;

    /** RuinedPortalFeature.pieceGeneratorSupplier's checks for one portal type (the set's order). */
    private boolean ruinedPortal(int type, int cx, int cz, boolean[] tag) {
        if (type == 6) {
            // The nether portal's biome tag holds only nether biomes: it never starts in the overworld.
            return false;
        }
        LegacyRandom r = random;
        r.setLargeFeatureSeed(seed, cx, cz);
        int placement;
        boolean airPocket;
        switch (type) {
            case 1: // desert
                placement = PARTLY_BURIED;
                airPocket = false;
                break;
            case 2: // jungle
                placement = ON_LAND_SURFACE;
                airPocket = r.nextFloat() < 0.5F;
                break;
            case 3: // swamp
                placement = ON_OCEAN_FLOOR;
                airPocket = false;
                break;
            case 4: { // mountain
                boolean flag = r.nextFloat() < 0.5F;
                placement = flag ? IN_MOUNTAIN : ON_LAND_SURFACE;
                airPocket = flag || r.nextFloat() < 0.5F;
                break;
            }
            case 5: // ocean
                placement = ON_OCEAN_FLOOR;
                airPocket = false;
                break;
            default: { // standard
                boolean flag1 = r.nextFloat() < 0.5F;
                placement = flag1 ? UNDERGROUND : ON_LAND_SURFACE;
                airPocket = flag1 || r.nextFloat() < 0.5F;
                break;
            }
        }
        int template;
        if (r.nextFloat() < 0.05F) {
            template = 10 + r.nextInt(3);
        } else {
            template = r.nextInt(10);
        }
        int rotation = r.nextInt(4);
        int mirror = r.nextFloat() < 0.5F ? 0 : 1;
        int[] box = StructureTables.PORTALS[template];
        int o = (rotation * 2 + mirror) * 5;
        int x0 = cx << 4;
        int z0 = cz << 4;
        int minX = x0 + box[o];
        int maxX = x0 + box[o + 1];
        int minZ = z0 + box[o + 2];
        int maxZ = z0 + box[o + 3];
        int ySpan = box[o + 4];
        int centerX = minX + (maxX - minX + 1) / 2;
        int centerZ = minZ + (maxZ - minZ + 1) / 2;
        boolean oceanFloor = placement == ON_OCEAN_FLOOR;
        int i = filler.baseHeight(centerX, centerZ, oceanFloor) - 1;
        int j = suitableY(r, placement, i, ySpan, minX, maxX, minZ, maxZ);
        return tag[biomes.biome(x0 >> 2, j >> 2, z0 >> 2)];
    }

    private final char[][] corners = new char[4][HEIGHT];

    /** RuinedPortalFeature.findSuitableY (not IN_NETHER). */
    private int suitableY(LegacyRandom r, int placement, int top, int ySpan, int minX, int maxX, int minZ, int maxZ) {
        int j = MIN_Y + 15;
        int i;
        if (placement == IN_MOUNTAIN) {
            int k = top - ySpan;
            i = 70 < k ? r.between(70, k) : k;
        } else if (placement == UNDERGROUND) {
            int j1 = top - ySpan;
            i = j < j1 ? r.between(j, j1) : j1;
        } else if (placement == PARTLY_BURIED) {
            i = top - ySpan + r.between(2, 8);
        } else {
            i = top;
        }
        filler.baseColumn(minX, minZ, corners[0]);
        filler.baseColumn(maxX, minZ, corners[1]);
        filler.baseColumn(minX, maxZ, corners[2]);
        filler.baseColumn(maxX, maxZ, corners[3]);
        boolean oceanFloor = placement == ON_OCEAN_FLOOR;
        int l;
        for (l = i; l > j; --l) {
            int count = 0;
            for (int c = 0; c < 4; c++) {
                int code = l < MIN_Y || l >= MIN_Y + HEIGHT ? BlockCodes.AIR : corners[c][l - MIN_Y];
                if (oceanFloor ? code != BlockCodes.AIR && code != BlockCodes.WATER && code != BlockCodes.LAVA
                        : code != BlockCodes.AIR) {
                    ++count;
                    if (count == 3) {
                        return l;
                    }
                }
            }
        }
        return l;
    }

    // ---- strongholds ----

    /** ChunkGenerator.generateRingPositions for the strongholds' rings: cx, cz pairs in ring order. */
    public int[] ringPositions() {
        if (rings != null) {
            return rings;
        }
        boolean[] tag = allowed[STRONGHOLDS][0];
        int[] out = new int[RING_COUNT * 2];
        int i = RING_DISTANCE;
        int j = RING_COUNT;
        int k = RING_SPREAD;
        Random random = new Random();
        random.setSeed(seed);
        double d0 = random.nextDouble() * Math.PI * 2.0D;
        int l = 0;
        int i1 = 0;
        for (int j1 = 0; j1 < j; ++j1) {
            double d1 = (double) (4 * i + i * i1 * 6) + (random.nextDouble() - 0.5D) * (double) i * 2.5D;
            int k1 = (int) Math.round(Math.cos(d0) * d1);
            int l1 = (int) Math.round(Math.sin(d0) * d1);
            long found = findBiomeHorizontal((k1 << 4) + 8, 0, (l1 << 4) + 8, 112, tag, random);
            if (found != Long.MIN_VALUE) {
                k1 = (int) (found >> 32) >> 4;
                l1 = (int) found >> 4;
            }
            out[j1 * 2] = k1;
            out[j1 * 2 + 1] = l1;
            d0 += (Math.PI * 2D) / (double) k;
            ++l;
            if (l == k) {
                ++i1;
                l = 0;
                k += 2 * k / (i1 + 1);
                k = Math.min(k, j - j1);
                d0 += random.nextDouble() * Math.PI * 2.0D;
            }
        }
        rings = out;
        return out;
    }

    /**
     * BiomeSource.findBiomeHorizontal(x, y, z, radius, 1, tag, random, false): a random quart (reservoir draw) of
     * the square ring at the radius whose biome is in the tag; its block x, z packed, or Long.MIN_VALUE.
     */
    private long findBiomeHorizontal(int x, int y, int z, int radius, boolean[] tag, Random random) {
        int i = x >> 2;
        int j = z >> 2;
        int k = radius >> 2;
        int l = y >> 2;
        long pair = Long.MIN_VALUE;
        int i1 = 0;
        for (int k1 = k; k1 <= k; ++k1) {
            for (int l1 = -k1; l1 <= k1; ++l1) {
                for (int i2 = -k1; i2 <= k1; ++i2) {
                    int k2 = i + i2;
                    int j2 = j + l1;
                    if (tag[biomes.biome(k2, l, j2)]) {
                        if (pair == Long.MIN_VALUE || random.nextInt(i1 + 1) == 0) {
                            pair = pack(k2 << 2, j2 << 2);
                        }
                        ++i1;
                    }
                }
            }
        }
        return pair;
    }
}
