package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;

/**
 * 1.18.2's overworld surface: SurfaceSystem.buildSurface with the rules of SurfaceRuleData.overworld() (bedrock
 * floor, the preliminary-surface rules, the deepslate gradient) written out as plain code, plus the eroded
 * badlands pillars and frozen ocean icebergs. Runs on a chunk ChunkFiller filled, in place.
 *
 * <p>Biomes come from BiomeSource through BiomeManager's zoom (the 1.15 fuzzy offset with the SHA-256 of the
 * seed), at block resolution, with ChunkAccess.getNoiseBiome's clamp of quart y to the world's height: in
 * vanilla the surface step reads stored biomes, its own chunk's and its neighbors' (at BIOMES or later).
 *
 * <p>Mojang's rule tree is a sequence of conditions that are pure functions of the position and the column scan
 * (lazy only for speed), so evaluating the same tests in the same order, skipping the ones whose answer cannot
 * change the result, gives the same block. Every noise and random draw is the same call on the same seed.
 *
 * One instance per thread.
 */
public final class SurfaceBuilder {
    private static final int MIN_Y = OverworldNoise.MIN_Y;
    private static final int HEIGHT = OverworldNoise.HEIGHT;
    private static final int MAX_Y = MIN_Y + HEIGHT - 1;
    private static final int SEA_LEVEL = OverworldNoise.SEA_LEVEL;
    private static final int NONE = -1;
    private static final int MIN_QUART_Y = MIN_Y >> 2;
    private static final int MAX_QUART_Y = MIN_QUART_Y + (HEIGHT >> 2) - 1;

    private final OverworldNoise noise;
    private final ColumnCache columns;
    private final BiomeSource biomes;
    private final Xoroshiro.Positional random;
    private final Xoroshiro.Positional bedrockFloor;
    private final Xoroshiro.Positional deepslate;
    private final long zoomSeed;
    private final byte[] bands;

    private final DoubleNoise surfaceNoise;
    private final DoubleNoise surfaceSecondaryNoise;
    private final DoubleNoise clayBandsOffsetNoise;
    private final DoubleNoise badlandsPillarNoise;
    private final DoubleNoise badlandsPillarRoofNoise;
    private final DoubleNoise badlandsSurfaceNoise;
    private final DoubleNoise icebergPillarNoise;
    private final DoubleNoise icebergPillarRoofNoise;
    private final DoubleNoise icebergSurfaceNoise;
    private final DoubleNoise swampNoise;
    private final DoubleNoise calciteNoise;
    private final DoubleNoise gravelNoise;
    private final DoubleNoise powderSnowNoise;
    private final DoubleNoise packedIceNoise;
    private final DoubleNoise iceNoise;

    // ---- the chunk ----
    private byte[] blocks;
    private int baseX;
    private int baseZ;
    /** WORLD_SURFACE_WG per column (x * 16 + z): one above the highest non-air block, or MIN_Y. */
    private final int[] heights = new int[256];

    // ---- the column (SurfaceRules.Context's XZ state) ----
    private int column;
    private int bx;
    private int bz;
    private int surfaceDepth;
    private int minSurfaceLevel;
    private double surfaceSecondary;
    private boolean surfaceSecondaryDone;
    private double surfaceValue;
    private boolean surfaceValueDone;
    private int steep;
    private double calcite;
    private boolean calciteDone;
    private double gravel;
    private boolean gravelDone;
    private double powderSnow;
    private boolean powderSnowDone;
    private double packedIce;
    private boolean packedIceDone;
    private double ice;
    private boolean iceDone;
    private double swamp;
    private boolean swampDone;
    private int preliminaryCellX = Integer.MIN_VALUE;
    private int preliminaryCellZ = Integer.MIN_VALUE;
    private final int[] preliminary = new int[4];

    // ---- the block (the Y state) ----
    private int y;
    private int stoneDepthAbove;
    private int waterHeight;
    private int biome;

    public SurfaceBuilder(OverworldNoise noise, ColumnCache columns, BiomeSource biomes) {
        this.noise = noise;
        this.columns = columns;
        this.biomes = biomes;
        Xoroshiro.Positional p = noise.positional;
        this.random = p;
        surfaceNoise = noise(p, "surface", -6, 1, 1, 1);
        surfaceSecondaryNoise = noise(p, "surface_secondary", -6, 1, 1, 0, 1);
        clayBandsOffsetNoise = noise(p, "clay_bands_offset", -8, 1);
        badlandsPillarNoise = noise(p, "badlands_pillar", -2, 1, 1, 1, 1);
        badlandsPillarRoofNoise = noise(p, "badlands_pillar_roof", -8, 1);
        badlandsSurfaceNoise = noise(p, "badlands_surface", -6, 1, 1, 1);
        icebergPillarNoise = noise(p, "iceberg_pillar", -6, 1, 1, 1, 1);
        icebergPillarRoofNoise = noise(p, "iceberg_pillar_roof", -3, 1);
        icebergSurfaceNoise = noise(p, "iceberg_surface", -6, 1, 1, 1);
        swampNoise = noise(p, "surface_swamp", -2, 1);
        calciteNoise = noise(p, "calcite", -9, 1, 1, 1, 1);
        gravelNoise = noise(p, "gravel", -8, 1, 1, 1, 1);
        powderSnowNoise = noise(p, "powder_snow", -6, 1, 1, 1, 1);
        packedIceNoise = noise(p, "packed_ice", -7, 1, 1, 1, 1);
        iceNoise = noise(p, "ice", -4, 1, 1, 1, 1);
        bands = generateBands(p.fromHashOf("minecraft:clay_bands"));
        bedrockFloor = p.fromHashOf("minecraft:bedrock_floor").forkPositional();
        deepslate = p.fromHashOf("minecraft:deepslate").forkPositional();
        zoomSeed = obfuscateSeed(noise.seed);
    }

    private static DoubleNoise noise(Xoroshiro.Positional p, String name, int firstOctave, double... amplitudes) {
        return new DoubleNoise(p.fromHashOf("minecraft:" + name), firstOctave, amplitudes);
    }

    // ---- block access ----

    private int get(int y) {
        return y < MIN_Y || y > MAX_Y ? AIR : blocks[column * HEIGHT + y - MIN_Y];
    }

    /** BlockColumn.setBlock: ProtoChunk.setBlockState and the WORLD_SURFACE_WG heightmap's update. */
    private void set(int y, int code) {
        if (y < MIN_Y || y > MAX_Y) {
            return;
        }
        blocks[column * HEIGHT + y - MIN_Y] = (byte) code;
        int h = heights[column];
        if (y <= h - 2) {
            return;
        }
        if (code != AIR) {
            if (y >= h) {
                heights[column] = y + 1;
            }
        } else if (h - 1 == y) {
            int at = column * HEIGHT - MIN_Y;
            for (int j = y - 1; j >= MIN_Y; --j) {
                if (blocks[at + j] != AIR) {
                    heights[column] = j + 1;
                    return;
                }
            }
            heights[column] = MIN_Y;
        }
    }

    private static boolean isFluid(int code) {
        return code == WATER || code == LAVA;
    }

    /**
     * Builds the surface of chunk cx, cz in blocks (ChunkFiller's layout, index (x * 16 + z) * 384 + y + 64).
     */
    public void build(int cx, int cz, byte[] blocks) {
        this.blocks = blocks;
        baseX = cx << 4;
        baseZ = cz << 4;
        for (int c = 0; c < 256; c++) {
            int at = c * HEIGHT;
            int h = MIN_Y;
            for (int i = HEIGHT - 1; i >= 0; i--) {
                if (blocks[at + i] != AIR) {
                    h = MIN_Y + i + 1;
                    break;
                }
            }
            heights[c] = h;
        }
        for (int k = 0; k < 16; ++k) {
            for (int l = 0; l < 16; ++l) {
                column = k * 16 + l;
                bx = baseX + k;
                bz = baseZ + l;
                beginZoomColumn();
                // ChunkAccess.getHeight is the heightmap's first available y minus one (the top block), plus one.
                int k1 = heights[column];
                int top = zoomedBiome(bx, k1, bz);
                if (top == Biomes.ERODED_BADLANDS) {
                    erodedBadlandsExtension(k1);
                }
                int l1 = heights[column];
                updateXZ();
                buildColumn(l1);
                if (top == Biomes.FROZEN_OCEAN || top == Biomes.DEEP_FROZEN_OCEAN) {
                    frozenOceanExtension(top, k1);
                }
            }
        }
        this.blocks = null;
    }

    private void updateXZ() {
        surfaceDepth = surfaceDepth(bx, bz);
        surfaceSecondaryDone = false;
        surfaceValueDone = false;
        calciteDone = false;
        gravelDone = false;
        powderSnowDone = false;
        packedIceDone = false;
        iceDone = false;
        swampDone = false;
        steep = -1;
        minSurfaceLevel = minSurfaceLevel();
    }

    /** SurfaceSystem.buildSurface's scan down one column. */
    private void buildColumn(int top) {
        int i2 = 0;
        int j2 = Integer.MIN_VALUE;
        int minSurface = minSurfaceLevel;
        int at = column * HEIGHT - MIN_Y;
        byte[] b = blocks;
        // Below the min surface level only the bedrock and deepslate gradients can apply, and they read nothing
        // of the scan: the scan's state is kept only above it.
        int scanTo = Math.max(minSurface, MIN_Y);
        // Above the world everything reads as air, which leaves the scan's state where it starts.
        for (int y = Math.min(top, MAX_Y); y >= scanTo; --y) {
            int state = b[at + y];
            if (state == AIR) {
                i2 = 0;
                j2 = Integer.MIN_VALUE;
            } else if (isFluid(state)) {
                if (j2 == Integer.MIN_VALUE) {
                    j2 = y + 1;
                }
            } else {
                ++i2;
                if (state == STONE) {
                    this.y = y;
                    this.stoneDepthAbove = i2;
                    this.waterHeight = j2;
                    this.biome = -1;
                    int r = rule();
                    if (r != NONE) {
                        if (r == AIR) {
                            set(y, r);
                        } else {
                            b[at + y] = (byte) r;
                        }
                    }
                }
            }
        }
        int deepTop = Math.min(scanTo - 1, Math.min(top, 7));
        // Stone turned into another non-air block leaves the heightmap as it is (Heightmap.update only moves for
        // a non-air block above the top or air at the top), so these are plain writes.
        for (int y = deepTop; y >= MIN_Y; --y) {
            if (b[at + y] == STONE) {
                if (y <= -64 || y < -59 && gradient(bedrockFloor, y, -64, -59)) {
                    b[at + y] = BEDROCK;
                } else if (y <= 0 || gradient(deepslate, y, 0, 8)) {
                    b[at + y] = DEEPSLATE;
                }
            }
        }
    }

    // ---- the rules (SurfaceRuleData.overworld) ----

    private int rule() {
        int y = this.y;
        if (y <= -64 || y < -59 && gradient(bedrockFloor, y, -64, -59)) {
            return BEDROCK;
        }
        // abovePreliminarySurface holds here: buildColumn only asks at or above the min surface level.
        int r = surface();
        if (r != NONE) {
            return r;
        }
        if (y <= 0 || y < 8 && gradient(deepslate, y, 0, 8)) {
            return DEEPSLATE;
        }
        return NONE;
    }

    /** VerticalGradientConditionSource between trueAtAndBelow and falseAtAndAbove (exclusive here). */
    private boolean gradient(Xoroshiro.Positional p, int y, int trueAtAndBelow, int falseAtAndAbove) {
        if (y <= trueAtAndBelow) {
            return true;
        }
        if (y >= falseAtAndAbove) {
            return false;
        }
        double d0 = map((double) y, (double) trueAtAndBelow, (double) falseAtAndAbove, 1.0D, 0.0D);
        return (double) p.at(bx, y, bz).nextFloat() < d0;
    }

    private int biome() {
        if (biome < 0) {
            biome = zoomedBiome(bx, y, bz);
        }
        return biome;
    }

    private boolean onFloor() {
        return stoneDepthAbove <= 1;
    }

    /**
     * ON_CEILING, stoneDepthBelow <= 1. Vanilla's stone depth below is y minus the bottom of the run of solid
     * (neither air nor fluid) blocks y is in, plus 1, found by scanning down from the run's top; it is 1 exactly
     * when the block under y is not solid. Nothing below y has been changed yet when y is ruled on, so asking that
     * block gives the same answer without the scan (no other rule reads the depth below).
     */
    private boolean onCeiling() {
        int s = get(y - 1);
        return s == AIR || isFluid(s);
    }

    private boolean underFloor() {
        return stoneDepthAbove <= 1 + surfaceDepth;
    }

    /** waterBlockCheck(offset, 0). */
    private boolean waterBlock(int offset) {
        return waterHeight == Integer.MIN_VALUE || y >= waterHeight + offset;
    }

    /** c8: waterStartCheck(-6, -1). */
    private boolean waterStart() {
        return waterHeight == Integer.MIN_VALUE || y + stoneDepthAbove >= waterHeight + -6 + surfaceDepth * -1;
    }

    private boolean hole() {
        return surfaceDepth <= 0;
    }

    private double surfaceValue() {
        if (!surfaceValueDone) {
            surfaceValue = surfaceNoise.getValue((double) bx, 0.0D, (double) bz);
            surfaceValueDone = true;
        }
        return surfaceValue;
    }

    /** SurfaceRuleData.surfaceNoiseAbove. */
    private boolean surfaceNoiseAbove(double v) {
        double d = surfaceValue();
        return d >= v / 8.25D && d <= Double.MAX_VALUE;
    }

    private boolean surfaceBetween(double min, double max) {
        double d = surfaceValue();
        return d >= min && d <= max;
    }

    /** c14, c15, c16: the three bands of the surface noise badlands use. */
    private boolean surfaceBands() {
        return surfaceBetween(-0.909D, -0.5454D) || surfaceBetween(-0.1818D, 0.1818D) || surfaceBetween(0.5454D, 0.909D);
    }

    private static boolean between(double d, double min, double max) {
        return d >= min && d <= max;
    }

    private double calcite() {
        if (!calciteDone) {
            calcite = calciteNoise.getValue((double) bx, 0.0D, (double) bz);
            calciteDone = true;
        }
        return calcite;
    }

    private double gravel() {
        if (!gravelDone) {
            gravel = gravelNoise.getValue((double) bx, 0.0D, (double) bz);
            gravelDone = true;
        }
        return gravel;
    }

    private double powderSnow() {
        if (!powderSnowDone) {
            powderSnow = powderSnowNoise.getValue((double) bx, 0.0D, (double) bz);
            powderSnowDone = true;
        }
        return powderSnow;
    }

    private double packedIce() {
        if (!packedIceDone) {
            packedIce = packedIceNoise.getValue((double) bx, 0.0D, (double) bz);
            packedIceDone = true;
        }
        return packedIce;
    }

    private double ice() {
        if (!iceDone) {
            ice = iceNoise.getValue((double) bx, 0.0D, (double) bz);
            iceDone = true;
        }
        return ice;
    }

    private double swamp() {
        if (!swampDone) {
            swamp = swampNoise.getValue((double) bx, 0.0D, (double) bz);
            swampDone = true;
        }
        return swamp;
    }

    private double surfaceSecondary() {
        if (!surfaceSecondaryDone) {
            surfaceSecondary = surfaceSecondaryNoise.getValue((double) bx, 0.0D, (double) bz);
            surfaceSecondaryDone = true;
        }
        return surfaceSecondary;
    }

    /** stoneDepthCheck(0, true, range, FLOOR). */
    private boolean deepUnderFloor(int range) {
        int k = (int) map(surfaceSecondary(), -1.0D, 1.0D, 0.0D, (double) range);
        return stoneDepthAbove <= 1 + surfaceDepth + k;
    }

    /** SurfaceRules.Context.SteepMaterialCondition. */
    private boolean steep() {
        if (steep < 0) {
            int i = bx & 15;
            int j = bz & 15;
            int k = Math.max(j - 1, 0);
            int l = Math.min(j + 1, 15);
            int i1 = heights[i * 16 + k];
            int j1 = heights[i * 16 + l];
            boolean s;
            if (j1 >= i1 + 4) {
                s = true;
            } else {
                int k1 = Math.max(i - 1, 0);
                int l1 = Math.min(i + 1, 15);
                int i2 = heights[k1 * 16 + j];
                int j2 = heights[l1 * 16 + j];
                s = i2 >= j2 + 4;
            }
            steep = s ? 1 : 0;
        }
        return steep == 1;
    }

    /** grass: c7 ? grass : dirt. */
    private int grass() {
        return waterBlock(0) ? GRASS_BLOCK : DIRT;
    }

    private int sand() {
        return onCeiling() ? SANDSTONE : SAND;
    }

    private int gravelRule() {
        return onCeiling() ? STONE : GRAVEL;
    }

    /** The part of SurfaceRuleData.overworldLike under abovePreliminarySurface (surfacerules$rulesource8). */
    private int surface() {
        boolean floor = onFloor();
        if (floor) {
            int b = biome();
            if (b == Biomes.WOODED_BADLANDS && y >= 97 + surfaceDepth * 2) {
                return surfaceBands() ? COARSE_DIRT : grass();
            }
            if (b == Biomes.SWAMP && y >= 62 && !(y >= 63) && swamp() >= 0.0D && swamp() <= Double.MAX_VALUE) {
                return WATER;
            }
        }
        int b = biome();
        if (b == Biomes.BADLANDS || b == Biomes.ERODED_BADLANDS || b == Biomes.WOODED_BADLANDS) {
            if (floor) {
                if (y >= 256) {
                    return ORANGE_TERRACOTTA;
                }
                if (y + stoneDepthAbove >= 74 + surfaceDepth) {
                    return surfaceBands() ? TERRACOTTA : band(y);
                }
                if (waterBlock(-1)) {
                    return onCeiling() ? RED_SANDSTONE : RED_SAND;
                }
                if (!hole()) {
                    return ORANGE_TERRACOTTA;
                }
                if (waterStart()) {
                    return WHITE_TERRACOTTA;
                }
                return gravelRule();
            }
            if (y + stoneDepthAbove >= 63 - surfaceDepth) {
                if (y >= 63 && !(y + stoneDepthAbove >= 74 + surfaceDepth)) {
                    return ORANGE_TERRACOTTA;
                }
                return band(y);
            }
            if (underFloor() && waterStart()) {
                return WHITE_TERRACOTTA;
            }
        }
        if (floor && waterBlock(-1)) {
            if ((b == Biomes.FROZEN_OCEAN || b == Biomes.DEEP_FROZEN_OCEAN) && hole()) {
                if (waterBlock(0)) {
                    return AIR;
                }
                if (Biomes.coldEnoughToSnow(b, bx, y, bz)) {
                    return ICE;
                }
                return WATER;
            }
            return floorRule(b);
        }
        if (waterStart()) {
            if (floor && (b == Biomes.FROZEN_OCEAN || b == Biomes.DEEP_FROZEN_OCEAN) && hole()) {
                return WATER;
            }
            if (underFloor()) {
                return underFloorRule(b);
            }
            if ((b == Biomes.WARM_OCEAN || b == Biomes.BEACH || b == Biomes.SNOWY_BEACH) && deepUnderFloor(6)) {
                return SANDSTONE;
            }
            if (b == Biomes.DESERT && deepUnderFloor(30)) {
                return SANDSTONE;
            }
        }
        if (floor) {
            if (b == Biomes.FROZEN_PEAKS || b == Biomes.JAGGED_PEAKS) {
                return STONE;
            }
            if (b == Biomes.WARM_OCEAN || b == Biomes.LUKEWARM_OCEAN || b == Biomes.DEEP_LUKEWARM_OCEAN) {
                return sand();
            }
            return gravelRule();
        }
        return NONE;
    }

    /** surfacerules$rulesource3: stony peaks and shore, windswept hills, sand by beaches and deserts. */
    private int stonyRule(int b) {
        if (b == Biomes.STONY_PEAKS) {
            return between(calcite(), -0.0125D, 0.0125D) ? CALCITE : STONE;
        }
        if (b == Biomes.STONY_SHORE) {
            return between(gravel(), -0.05D, 0.05D) ? gravelRule() : STONE;
        }
        if (b == Biomes.WINDSWEPT_HILLS) {
            return surfaceNoiseAbove(1.0D) ? STONE : NONE;
        }
        if (b == Biomes.WARM_OCEAN || b == Biomes.BEACH || b == Biomes.SNOWY_BEACH || b == Biomes.DESERT) {
            return sand();
        }
        if (b == Biomes.DRIPSTONE_CAVES) {
            return STONE;
        }
        return NONE;
    }

    /** surfacerules$rulesource7: the floor at or above water. Never null (it ends in grass). */
    private int floorRule(int b) {
        switch (b) {
            case Biomes.FROZEN_PEAKS:
                if (steep()) {
                    return PACKED_ICE;
                }
                if (between(packedIce(), 0.0D, 0.2D)) {
                    return PACKED_ICE;
                }
                if (between(ice(), 0.0D, 0.025D)) {
                    return ICE;
                }
                if (waterBlock(0)) {
                    return SNOW_BLOCK;
                }
                return grass();
            case Biomes.SNOWY_SLOPES:
                if (steep()) {
                    return STONE;
                }
                if (between(powderSnow(), 0.35D, 0.6D) && waterBlock(0)) {
                    return POWDER_SNOW;
                }
                if (waterBlock(0)) {
                    return SNOW_BLOCK;
                }
                return grass();
            case Biomes.JAGGED_PEAKS:
                if (steep()) {
                    return STONE;
                }
                if (waterBlock(0)) {
                    return SNOW_BLOCK;
                }
                return grass();
            case Biomes.GROVE:
                if (between(powderSnow(), 0.35D, 0.6D) && waterBlock(0)) {
                    return POWDER_SNOW;
                }
                if (waterBlock(0)) {
                    return SNOW_BLOCK;
                }
                return grass();
            case Biomes.WINDSWEPT_SAVANNA:
                if (surfaceNoiseAbove(1.75D)) {
                    return STONE;
                }
                if (surfaceNoiseAbove(-0.5D)) {
                    return COARSE_DIRT;
                }
                return grass();
            case Biomes.WINDSWEPT_GRAVELLY_HILLS:
                if (surfaceNoiseAbove(2.0D)) {
                    return gravelRule();
                }
                if (surfaceNoiseAbove(1.0D)) {
                    return STONE;
                }
                if (surfaceNoiseAbove(-1.0D)) {
                    return grass();
                }
                return gravelRule();
            case Biomes.OLD_GROWTH_PINE_TAIGA:
            case Biomes.OLD_GROWTH_SPRUCE_TAIGA:
                if (surfaceNoiseAbove(1.75D)) {
                    return COARSE_DIRT;
                }
                if (surfaceNoiseAbove(-0.95D)) {
                    return PODZOL;
                }
                return grass();
            case Biomes.ICE_SPIKES:
                if (waterBlock(0)) {
                    return SNOW_BLOCK;
                }
                return grass();
            case Biomes.MUSHROOM_FIELDS:
                return MYCELIUM;
            default: {
                int r = stonyRule(b);
                return r != NONE ? r : grass();
            }
        }
    }

    /** surfacerules$rulesource6: under the floor, above the water start. Never null (it ends in dirt). */
    private int underFloorRule(int b) {
        switch (b) {
            case Biomes.FROZEN_PEAKS:
                if (steep()) {
                    return PACKED_ICE;
                }
                if (between(packedIce(), -0.5D, 0.2D)) {
                    return PACKED_ICE;
                }
                if (between(ice(), -0.0625D, 0.025D)) {
                    return ICE;
                }
                if (waterBlock(0)) {
                    return SNOW_BLOCK;
                }
                return DIRT;
            case Biomes.SNOWY_SLOPES:
                if (steep()) {
                    return STONE;
                }
                if (between(powderSnow(), 0.45D, 0.58D) && waterBlock(0)) {
                    return POWDER_SNOW;
                }
                if (waterBlock(0)) {
                    return SNOW_BLOCK;
                }
                return DIRT;
            case Biomes.JAGGED_PEAKS:
                return STONE;
            case Biomes.GROVE:
                if (between(powderSnow(), 0.45D, 0.58D) && waterBlock(0)) {
                    return POWDER_SNOW;
                }
                return DIRT;
            case Biomes.WINDSWEPT_SAVANNA:
                return surfaceNoiseAbove(1.75D) ? STONE : DIRT;
            case Biomes.WINDSWEPT_GRAVELLY_HILLS:
                if (surfaceNoiseAbove(2.0D)) {
                    return gravelRule();
                }
                if (surfaceNoiseAbove(1.0D)) {
                    return STONE;
                }
                if (surfaceNoiseAbove(-1.0D)) {
                    return DIRT;
                }
                return gravelRule();
            default: {
                int r = stonyRule(b);
                return r != NONE ? r : DIRT;
            }
        }
    }

    // ---- SurfaceSystem's helpers ----

    /** SurfaceSystem.getSurfaceDepth. */
    private int surfaceDepth(int x, int z) {
        double d0 = surfaceNoise.getValue((double) x, 0.0D, (double) z);
        return (int) (d0 * 2.75D + 3.0D + random.at(x, 0, z).nextDouble() * 0.25D);
    }

    /** SurfaceRules.Context.getMinSurfaceLevel. */
    private int minSurfaceLevel() {
        int i = bx >> 4;
        int j = bz >> 4;
        if (i != preliminaryCellX || j != preliminaryCellZ) {
            preliminaryCellX = i;
            preliminaryCellZ = j;
            preliminary[0] = columns.preliminarySurfaceLevel(i << 4, j << 4);
            preliminary[1] = columns.preliminarySurfaceLevel((i + 1) << 4, j << 4);
            preliminary[2] = columns.preliminarySurfaceLevel(i << 4, (j + 1) << 4);
            preliminary[3] = columns.preliminarySurfaceLevel((i + 1) << 4, (j + 1) << 4);
        }
        double tx = (double) ((float) (bx & 15) / 16.0F);
        double tz = (double) ((float) (bz & 15) / 16.0F);
        double a = lerp(tx, (double) preliminary[0], (double) preliminary[1]);
        double b = lerp(tx, (double) preliminary[2], (double) preliminary[3]);
        int l = floor(lerp(tz, a, b));
        return l + surfaceDepth - 8;
    }

    private int band(int y) {
        int i = (int) Math.round(clayBandsOffsetNoise.getValue((double) bx, 0.0D, (double) bz) * 4.0D);
        return bands[(y + i + bands.length) % bands.length];
    }

    private void erodedBadlandsExtension(int k1) {
        double d1 = Math.min(Math.abs(badlandsSurfaceNoise.getValue((double) bx, 0.0D, (double) bz) * 8.25D),
            badlandsPillarNoise.getValue((double) bx * 0.2D, 0.0D, (double) bz * 0.2D) * 15.0D);
        if (!(d1 <= 0.0D)) {
            double d4 = Math.abs(badlandsPillarRoofNoise.getValue((double) bx * 0.75D, 0.0D, (double) bz * 0.75D) * 1.5D);
            double d5 = 64.0D + Math.min(d1 * d1 * 2.5D, Math.ceil(d4 * 50.0D) + 24.0D);
            int i = floor(d5);
            if (k1 <= i) {
                for (int j = i; j >= MIN_Y; --j) {
                    int s = get(j);
                    if (s == STONE) {
                        break;
                    }
                    if (s == WATER) {
                        return;
                    }
                }
                for (int k = i; k >= MIN_Y && get(k) == AIR; --k) {
                    set(k, STONE);
                }
            }
        }
    }

    private void frozenOceanExtension(int biome, int k1) {
        int minSurface = minSurfaceLevel;
        double d1 = Math.min(Math.abs(icebergSurfaceNoise.getValue((double) bx, 0.0D, (double) bz) * 8.25D),
            icebergPillarNoise.getValue((double) bx * 1.28D, 0.0D, (double) bz * 1.28D) * 15.0D);
        if (!(d1 <= 1.8D)) {
            double d5 = Math.abs(icebergPillarRoofNoise.getValue((double) bx * 1.17D, 0.0D, (double) bz * 1.17D) * 1.5D);
            double d6 = Math.min(d1 * d1 * 1.2D, Math.ceil(d5 * 40.0D) + 14.0D);
            if (Biomes.shouldMeltFrozenOceanIcebergSlightly(biome, bx, 63, bz)) {
                d6 -= 2.0D;
            }
            double d2;
            if (d6 > 2.0D) {
                d2 = (double) SEA_LEVEL - d6 - 7.0D;
                d6 += (double) SEA_LEVEL;
            } else {
                d6 = 0.0D;
                d2 = 0.0D;
            }
            double d7 = d6;
            Xoroshiro r = random.at(bx, 0, bz);
            int i = 2 + r.nextInt(4);
            int j = SEA_LEVEL + 18 + r.nextInt(10);
            int k = 0;
            for (int l = Math.max(k1, (int) d6 + 1); l >= minSurface; --l) {
                if (get(l) == AIR && l < (int) d7 && r.nextDouble() > 0.01D
                        || get(l) == WATER && l > (int) d2 && l < SEA_LEVEL && d2 != 0.0D && r.nextDouble() > 0.15D) {
                    if (k <= i && l > j) {
                        set(l, SNOW_BLOCK);
                        ++k;
                    } else {
                        set(l, PACKED_ICE);
                    }
                }
            }
        }
    }

    private static byte[] generateBands(Xoroshiro r) {
        byte[] b = new byte[192];
        java.util.Arrays.fill(b, (byte) TERRACOTTA);
        for (int k = 0; k < b.length; ++k) {
            k += r.nextInt(5) + 1;
            if (k < b.length) {
                b[k] = ORANGE_TERRACOTTA;
            }
        }
        makeBands(r, b, 1, YELLOW_TERRACOTTA);
        makeBands(r, b, 2, BROWN_TERRACOTTA);
        makeBands(r, b, 1, RED_TERRACOTTA);
        int l = r.nextInt(15 - 9 + 1) + 9;
        int i = 0;
        for (int j = 0; i < l && j < b.length; j += r.nextInt(16) + 4) {
            b[j] = WHITE_TERRACOTTA;
            if (j - 1 > 0 && (r.nextLong() & 1L) != 0L) {
                b[j - 1] = LIGHT_GRAY_TERRACOTTA;
            }
            if (j + 1 < b.length && (r.nextLong() & 1L) != 0L) {
                b[j + 1] = LIGHT_GRAY_TERRACOTTA;
            }
            ++i;
        }
        return b;
    }

    private static void makeBands(Xoroshiro r, byte[] b, int base, int code) {
        int i = r.nextInt(15 - 6 + 1) + 6;
        for (int j = 0; j < i; ++j) {
            int k = base + r.nextInt(3);
            int l = r.nextInt(b.length);
            for (int i1 = 0; l + i1 < b.length && i1 < k; ++i1) {
                b[l + i1] = (byte) code;
            }
        }
    }

    // ---- BiomeManager ----

    /** BiomeManager.obfuscateSeed: Hashing.sha256().hashLong(seed).asLong(). */
    static long obfuscateSeed(long seed) {
        byte[] in = new byte[8];
        for (int i = 0; i < 8; i++) {
            in[i] = (byte) (seed >>> (8 * i));
        }
        byte[] h;
        try {
            h = MessageDigest.getInstance("SHA-256").digest(in);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        long v = 0L;
        for (int i = 7; i >= 0; i--) {
            v = v << 8 | (h[i] & 0xFFL);
        }
        return v;
    }

    /** The 4 quart columns the zoom can pick from for the current block column: (l, j1) (l+1, j1) (l, j1+1) (l+1, j1+1). */
    private int slot00;
    private int slot10;
    private int slot01;
    private int slot11;

    private void beginZoomColumn() {
        int l = (bx - 2) >> 2;
        int j1 = (bz - 2) >> 2;
        slot00 = biomes.slotOf(l, j1);
        slot10 = biomes.slotOf(l + 1, j1);
        slot01 = biomes.slotOf(l, j1 + 1);
        slot11 = biomes.slotOf(l + 1, j1 + 1);
    }

    /** BiomeManager.getBiome at a block, over stored biomes (quart y clamped). */
    int zoomedBiome(int x, int y, int z) {
        int i = x - 2;
        int j = y - 2;
        int k = z - 2;
        int l = i >> 2;
        int i1 = j >> 2;
        int j1 = k >> 2;
        // The zoom picks one of the 8 quarts around the position; where they all hold the same biome, that is it.
        BiomeSource s = biomes;
        int b = s.storedInSlot(slot00, i1);
        if (s.storedInSlot(slot10, i1) == b && s.storedInSlot(slot00, i1 + 1) == b && s.storedInSlot(slot10, i1 + 1) == b
                && s.storedInSlot(slot01, i1) == b && s.storedInSlot(slot11, i1) == b
                && s.storedInSlot(slot01, i1 + 1) == b && s.storedInSlot(slot11, i1 + 1) == b) {
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
            int i2 = flag ? l : l + 1;
            int j2 = flag1 ? i1 : i1 + 1;
            int k2 = flag2 ? j1 : j1 + 1;
            double d4 = flag ? d0 : d0 - 1.0D;
            double d5 = flag1 ? d1 : d1 - 1.0D;
            double d6 = flag2 ? d2 : d2 - 1.0D;
            double d7 = fiddledDistance(zoomSeed, i2, j2, k2, d4, d5, d6);
            if (d3 > d7) {
                k1 = l1;
                d3 = d7;
            }
        }
        int i3 = (k1 & 2) == 0 ? i1 : i1 + 1;
        int slot = (k1 & 4) == 0 ? (k1 & 1) == 0 ? slot00 : slot01 : (k1 & 1) == 0 ? slot10 : slot11;
        return s.storedInSlot(slot, i3);
    }

    private static long lcg(long a, long b) {
        a *= a * 6364136223846793005L + 1442695040888963407L;
        return a + b;
    }

    private static double fiddledDistance(long seed, int x, int y, int z, double dx, double dy, double dz) {
        long v = lcg(seed, (long) x);
        v = lcg(v, (long) y);
        v = lcg(v, (long) z);
        v = lcg(v, (long) x);
        v = lcg(v, (long) y);
        v = lcg(v, (long) z);
        double d0 = fiddle(v);
        v = lcg(v, seed);
        double d1 = fiddle(v);
        v = lcg(v, seed);
        double d2 = fiddle(v);
        double a = dz + d2;
        double b = dy + d1;
        double c = dx + d0;
        return a * a + b * b + c * c;
    }

    private static double fiddle(long v) {
        double d0 = (double) Math.floorMod(v >> 24, 1024) / 1024.0D;
        return (d0 - 0.5D) * 0.9D;
    }

    // ---- Mth ----

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double map(double v, double fromA, double fromB, double toA, double toB) {
        return lerp((v - fromA) / (fromB - fromA), toA, toB);
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < (double) i ? i - 1 : i;
    }
}
