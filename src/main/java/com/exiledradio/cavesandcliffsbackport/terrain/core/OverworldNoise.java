package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * 1.18.2's overworld noise router (NoiseRouterData.overworldWithNewCaves, normal biomes), written out as plain
 * code instead of a tree of density function objects. Every method computes what Mojang's function computes at
 * one position, with the same floating-point operations in the same order.
 *
 * Immutable after construction and safe to share between threads.
 */
public final class OverworldNoise {
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;
    public static final int SEA_LEVEL = 63;

    public final long seed;
    public final Xoroshiro.Positional aquiferRandom;
    public final Xoroshiro.Positional oreRandom;

    final DoubleNoise shift;
    final DoubleNoise temperature;
    final DoubleNoise vegetation;
    final DoubleNoise continentalness;
    final DoubleNoise erosion;
    final DoubleNoise ridge;
    final DoubleNoise jagged;
    final DoubleNoise aquiferBarrier;
    final DoubleNoise aquiferFloodedness;
    final DoubleNoise aquiferSpread;
    final DoubleNoise aquiferLava;
    final DoubleNoise pillar;
    final DoubleNoise pillarRareness;
    final DoubleNoise pillarThickness;
    final DoubleNoise spaghetti2d;
    final DoubleNoise spaghetti2dElevation;
    final DoubleNoise spaghetti2dModulator;
    final DoubleNoise spaghetti2dThickness;
    final DoubleNoise spaghetti3d1;
    final DoubleNoise spaghetti3d2;
    final DoubleNoise spaghetti3dRarity;
    final DoubleNoise spaghetti3dThickness;
    final DoubleNoise spaghettiRoughness;
    final DoubleNoise spaghettiRoughnessModulator;
    final DoubleNoise caveEntrance;
    final DoubleNoise caveLayer;
    final DoubleNoise caveCheese;
    final DoubleNoise oreVeininess;
    final DoubleNoise oreVeinA;
    final DoubleNoise oreVeinB;
    final DoubleNoise oreGap;
    final DoubleNoise noodle;
    final DoubleNoise noodleThickness;
    final DoubleNoise noodleRidgeA;
    final DoubleNoise noodleRidgeB;
    final BlendedNoise base3d;
    final TerrainShaper shaper = new TerrainShaper();

    // mapFromUnitTo(noise, from, to) = noise * scale + offset, with Mojang's own arithmetic for the two constants.
    private static final double SPAGHETTI_3D_THICKNESS_OFFSET = (-0.065D + -0.088D) * 0.5D;
    private static final double SPAGHETTI_3D_THICKNESS_SCALE = (-0.088D - -0.065D) * 0.5D;
    private static final double ROUGHNESS_MODULATOR_OFFSET = (0.0D + -0.1D) * 0.5D;
    private static final double ROUGHNESS_MODULATOR_SCALE = (-0.1D - 0.0D) * 0.5D;
    private static final double ELEVATION_OFFSET = (-8.0D + 8.0D) * 0.5D;
    private static final double ELEVATION_SCALE = (8.0D - -8.0D) * 0.5D;
    private static final double THICKNESS_2D_OFFSET = (-0.6D + -1.3D) * 0.5D;
    private static final double THICKNESS_2D_SCALE = (-1.3D - -0.6D) * 0.5D;
    private static final double PILLAR_RARENESS_OFFSET = (0.0D + -2.0D) * 0.5D;
    private static final double PILLAR_RARENESS_SCALE = (-2.0D - 0.0D) * 0.5D;
    private static final double PILLAR_THICKNESS_OFFSET = (0.0D + 1.1D) * 0.5D;
    private static final double PILLAR_THICKNESS_SCALE = (1.1D - 0.0D) * 0.5D;
    private static final double NOODLE_THICKNESS_OFFSET = (-0.05D + -0.1D) * 0.5D;
    private static final double NOODLE_THICKNESS_SCALE = (-0.1D - -0.05D) * 0.5D;
    private static final double ORE_THICKNESS = (double) -0.08F;

    /** DimensionType.MIN_Y * 2 and MAX_Y * 2: the range of the "y" density function. */
    private static final double Y_FROM = -4064.0D;
    private static final double Y_TO = 4062.0D;

    public OverworldNoise(long seed) {
        this.seed = seed;
        Xoroshiro.Positional positional = Xoroshiro.ofSeed(seed).forkPositional();
        shift = noise(positional, "offset", -3, 1, 1, 1, 0);
        temperature = noise(positional, "temperature", -10, 1.5, 0, 1, 0, 0, 0);
        vegetation = noise(positional, "vegetation", -8, 1, 1, 0, 0, 0, 0);
        continentalness = noise(positional, "continentalness", -9, 1, 1, 2, 2, 2, 1, 1, 1, 1);
        erosion = noise(positional, "erosion", -9, 1, 1, 0, 1, 1);
        ridge = noise(positional, "ridge", -7, 1, 2, 1, 0, 0, 0);
        jagged = noise(positional, "jagged", -16, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
        aquiferBarrier = noise(positional, "aquifer_barrier", -3, 1);
        aquiferFloodedness = noise(positional, "aquifer_fluid_level_floodedness", -7, 1);
        aquiferSpread = noise(positional, "aquifer_fluid_level_spread", -5, 1);
        aquiferLava = noise(positional, "aquifer_lava", -1, 1);
        pillar = noise(positional, "pillar", -7, 1, 1);
        pillarRareness = noise(positional, "pillar_rareness", -8, 1);
        pillarThickness = noise(positional, "pillar_thickness", -8, 1);
        spaghetti2d = noise(positional, "spaghetti_2d", -7, 1);
        spaghetti2dElevation = noise(positional, "spaghetti_2d_elevation", -8, 1);
        spaghetti2dModulator = noise(positional, "spaghetti_2d_modulator", -11, 1);
        spaghetti2dThickness = noise(positional, "spaghetti_2d_thickness", -11, 1);
        spaghetti3d1 = noise(positional, "spaghetti_3d_1", -7, 1);
        spaghetti3d2 = noise(positional, "spaghetti_3d_2", -7, 1);
        spaghetti3dRarity = noise(positional, "spaghetti_3d_rarity", -11, 1);
        spaghetti3dThickness = noise(positional, "spaghetti_3d_thickness", -8, 1);
        spaghettiRoughness = noise(positional, "spaghetti_roughness", -5, 1);
        spaghettiRoughnessModulator = noise(positional, "spaghetti_roughness_modulator", -8, 1);
        caveEntrance = noise(positional, "cave_entrance", -7, 0.4, 0.5, 1);
        caveLayer = noise(positional, "cave_layer", -8, 1);
        caveCheese = noise(positional, "cave_cheese", -8, 0.5, 1, 2, 1, 2, 1, 0, 2, 0);
        oreVeininess = noise(positional, "ore_veininess", -8, 1);
        oreVeinA = noise(positional, "ore_vein_a", -7, 1);
        oreVeinB = noise(positional, "ore_vein_b", -7, 1);
        oreGap = noise(positional, "ore_gap", -5, 1);
        noodle = noise(positional, "noodle", -8, 1);
        noodleThickness = noise(positional, "noodle_thickness", -8, 1);
        noodleRidgeA = noise(positional, "noodle_ridge_a", -7, 1);
        noodleRidgeB = noise(positional, "noodle_ridge_b", -7, 1);
        base3d = new BlendedNoise(positional.fromHashOf("minecraft:terrain"));
        aquiferRandom = positional.fromHashOf("minecraft:aquifer").forkPositional();
        oreRandom = positional.fromHashOf("minecraft:ore").forkPositional();
    }

    private static DoubleNoise noise(Xoroshiro.Positional positional, String name, int firstOctave, double... amplitudes) {
        return new DoubleNoise(positional.fromHashOf("minecraft:" + name), firstOctave, amplitudes);
    }

    public DoubleNoise temperatureNoise() {
        return temperature;
    }

    public DoubleNoise vegetationNoise() {
        return vegetation;
    }

    // ---- small helpers, Mth's arithmetic ----

    static double clamp(double v, double min, double max) {
        return v < min ? min : v > max ? max : v;
    }

    static double clampedLerp(double a, double b, double t) {
        return t < 0.0D ? a : t > 1.0D ? b : a + t * (b - a);
    }

    static double clampedMap(double v, double fromA, double fromB, double toA, double toB) {
        return clampedLerp(toA, toB, (v - fromA) / (fromB - fromA));
    }

    /** The "y" density function: nominally y itself, through a clamped lerp. */
    static double yFunction(int y) {
        return clampedMap((double) y, Y_FROM, Y_TO, Y_FROM, Y_TO);
    }

    // ---- per column (2D) ----

    public double shiftX(int x, int z) {
        return shift.getValue((double) x * 0.25D, 0.0D, (double) z * 0.25D) * 4.0D;
    }

    public double shiftZ(int x, int z) {
        return shift.getValue((double) z * 0.25D, (double) x * 0.25D, 0.0D) * 4.0D;
    }

    public double shifted(DoubleNoise noise, int x, int z, double shiftX, double shiftZ) {
        return noise.getValue((double) x * 0.25D + shiftX, 0.0D, (double) z * 0.25D + shiftZ);
    }

    /**
     * Fills out with the column values at block x, z: shiftX, shiftZ, continents, erosion, ridges, offset, factor,
     * jaggedness (COL_* indexes). point is a scratch float[4].
     */
    public void column(int x, int z, double[] out, int at, float[] point) {
        double sx = shiftX(x, z);
        double sz = shiftZ(x, z);
        double c = shifted(continentalness, x, z, sx, sz);
        double e = shifted(erosion, x, z, sx, sz);
        double r = shifted(ridge, x, z, sx, sz);
        point[Spline.CONTINENTS] = (float) c;
        point[Spline.EROSION] = (float) e;
        point[Spline.WEIRDNESS] = (float) r;
        point[Spline.RIDGES] = TerrainShaper.peaksAndValleys((float) r);
        // lerp(blendAlpha = 1, blendValue, spline) leaves the spline value, plus a positive zero.
        double offset = clamp((double) shaper.offset(point), -0.81D, 2.5D) + 0.0D;
        double factor = clamp((double) shaper.factor(point), 0.0D, 8.0D) + 0.0D;
        double jaggedness = clamp((double) shaper.jaggedness(point), 0.0D, 1.28D) + 0.0D;
        out[at + COL_SHIFT_X] = sx;
        out[at + COL_SHIFT_Z] = sz;
        out[at + COL_CONTINENTS] = c;
        out[at + COL_EROSION] = e;
        out[at + COL_RIDGES] = r;
        out[at + COL_OFFSET] = offset;
        out[at + COL_FACTOR] = factor;
        out[at + COL_JAGGEDNESS] = jaggedness;
    }

    public static final int COL_SHIFT_X = 0;
    public static final int COL_SHIFT_Z = 1;
    public static final int COL_CONTINENTS = 2;
    public static final int COL_EROSION = 3;
    public static final int COL_RIDGES = 4;
    public static final int COL_OFFSET = 5;
    public static final int COL_FACTOR = 6;
    public static final int COL_JAGGEDNESS = 7;
    public static final int COL_SIZE = 8;

    /** overworld/depth: the height gradient plus the offset spline. */
    public static double depth(int y, double offset) {
        return clampedMap((double) y, -64.0D, 320.0D, 1.5D, -1.5D) + offset;
    }

    /** initial_density_without_jaggedness. */
    public static double initialDensity(int y, double offset, double factor) {
        double d = depth(y, offset);
        double m = d == 0.0D ? 0.0D : d * factor;
        return (m > 0.0D ? m : m * 0.25D) * 4.0D;
    }

    /** NoiseRouterData.computePreliminarySurfaceLevelScanning for a column's offset and factor. */
    public static int preliminarySurfaceLevel(double offset, double factor) {
        for (int cell = 40; cell >= -8; cell--) {
            int y = cell * 8;
            double d1 = initialDensity(y, offset, factor) + -0.703125D;
            double d2 = clamp(d1, -64.0D, 64.0D);
            d2 = slide(d2, y);
            if (d2 > 0.390625D) {
                return y;
            }
        }
        return Integer.MAX_VALUE;
    }

    /** NoiseRouterData.applySlide with the overworld's sliders. */
    public static double slide(double v, int y) {
        double d0 = (double) (y / 8 - -8);
        v = clampedLerp(-0.078125D, v, ((48.0D - d0) - 8.0D) / 2.0D);
        return clampedLerp(0.1171875D, v, (d0 - 0.0D) / 3.0D);
    }

    // ---- 3D ----

    /** overworld/sloped_cheese. */
    public double slopedCheese(int x, int y, int z, double offset, double factor, double jaggedness) {
        double jag;
        if (jaggedness == 0.0D) {
            jag = 0.0D;
        } else {
            double j = jagged.getValue((double) x * 1500.0D, (double) y * 0.0D, (double) z * 1500.0D);
            jag = jaggedness * (j > 0.0D ? j : j * 0.5D);
        }
        double d = depth(y, offset) + jag;
        double m = d == 0.0D ? 0.0D : d * factor;
        return (m > 0.0D ? m : m * 0.25D) * 4.0D + base3d.compute(x, y, z);
    }

    static double spaghettiRarity3d(double v) {
        if (v < -0.5D) {
            return 0.75D;
        } else if (v < 0.0D) {
            return 1.0D;
        }
        return v < 0.5D ? 1.5D : 2.0D;
    }

    static double spaghettiRarity2d(double v) {
        if (v < -0.75D) {
            return 0.5D;
        } else if (v < -0.5D) {
            return 0.75D;
        } else if (v < 0.5D) {
            return 1.0D;
        }
        return v < 0.75D ? 2.0D : 3.0D;
    }

    static double weird(DoubleNoise noise, double rarity, int x, int y, int z) {
        return rarity * Math.abs(noise.getValue((double) x / rarity, (double) y / rarity, (double) z / rarity));
    }

    /** overworld/caves/spaghetti_roughness_function. */
    public double roughness(int x, int y, int z) {
        double mod = spaghettiRoughnessModulator.getValue((double) x, (double) y, (double) z) * ROUGHNESS_MODULATOR_SCALE
            + ROUGHNESS_MODULATOR_OFFSET;
        if (mod == 0.0D) {
            return 0.0D;
        }
        return mod * (Math.abs(spaghettiRoughness.getValue((double) x, (double) y, (double) z)) + -0.4D);
    }

    /** overworld/caves/entrances, given the roughness there. */
    public double entrances(int x, int y, int z, double roughness) {
        double d6 = caveEntrance.getValue((double) x * 0.75D, (double) y * 0.5D, (double) z * 0.75D);
        double d7 = d6 + 0.37D + clampedMap((double) y, -10.0D, 30.0D, 0.3D, 0.0D);
        double rarity = spaghettiRarity3d(spaghetti3dRarity.getValue((double) x * 2.0D, (double) y, (double) z * 2.0D));
        double thickness = spaghetti3dThickness.getValue((double) x, (double) y, (double) z) * SPAGHETTI_3D_THICKNESS_SCALE
            + SPAGHETTI_3D_THICKNESS_OFFSET;
        double s1 = weird(spaghetti3d1, rarity, x, y, z);
        double s2 = weird(spaghetti3d2, rarity, x, y, z);
        double d4 = clamp(Math.max(s1, s2) + thickness, -1.0D, 1.0D);
        return Math.min(d7, roughness + d4);
    }

    /** overworld/caves/spaghetti_2d. */
    public double spaghetti2d(int x, int y, int z) {
        double rarity = spaghettiRarity2d(spaghetti2dModulator.getValue((double) x * 2.0D, (double) y, (double) z * 2.0D));
        double w = weird(spaghetti2d, rarity, x, y, z);
        double elevation = spaghetti2dElevation.getValue((double) x, (double) y * 0.0D, (double) z) * ELEVATION_SCALE
            + ELEVATION_OFFSET;
        double thickness = spaghetti2dThickness.getValue((double) x * 2.0D, (double) y, (double) z * 2.0D) * THICKNESS_2D_SCALE
            + THICKNESS_2D_OFFSET;
        double d4 = Math.abs(elevation + clampedMap((double) y, -64.0D, 320.0D, 8.0D, -40.0D));
        double t = d4 + thickness;
        double d5 = t * t * t;
        double d6 = w + thickness * 0.083D;
        return clamp(Math.max(d6, d5), -1.0D, 1.0D);
    }

    /** overworld/caves/pillars. */
    public double pillars(int x, int y, int z) {
        double p = pillar.getValue((double) x * 25.0D, (double) y * 0.3D, (double) z * 25.0D);
        double rareness = pillarRareness.getValue((double) x, (double) y, (double) z) * PILLAR_RARENESS_SCALE
            + PILLAR_RARENESS_OFFSET;
        double d3 = p * 2.0D + rareness;
        if (d3 == 0.0D) {
            return 0.0D;
        }
        double thickness = pillarThickness.getValue((double) x, (double) y, (double) z) * PILLAR_THICKNESS_SCALE
            + PILLAR_THICKNESS_OFFSET;
        return d3 * (thickness * thickness * thickness);
    }

    /** The function under the main interpolator: terrain and caves, slid at the world's top and bottom. */
    public double mainDensity(int x, int y, int z, double offset, double factor, double jaggedness) {
        double sloped = slopedCheese(x, y, z, offset, factor, jaggedness);
        double roughness = roughness(x, y, z);
        double entrances = entrances(x, y, z, roughness);
        double v;
        if (sloped >= -1000000.0D && sloped < 1.5625D) {
            v = Math.min(sloped, entrances * 5.0D);
        } else {
            double layer = caveLayer.getValue((double) x, (double) y * 8.0D, (double) z);
            double d3 = layer * layer * 4.0D;
            double cheese = caveCheese.getValue((double) x, (double) y * 0.6666666666666666D, (double) z);
            double d5 = clamp(0.27D + cheese, -1.0D, 1.0D) + clamp(1.5D + sloped * -0.64D, 0.0D, 0.5D);
            double d6 = d3 + d5;
            double d7 = Math.min(Math.min(d6, entrances), spaghetti2d(x, y, z) + roughness);
            double pillars = pillars(x, y, z);
            double d9 = pillars >= -1000000.0D && pillars < 0.03D ? -1000000.0D : pillars;
            v = Math.max(d7, d9);
        }
        return slide(v, y);
    }

    private static boolean yInside(int y, double min, double maxExclusive) {
        double v = yFunction(y);
        return v >= min && v < maxExclusive;
    }

    public double noodleToggle(int x, int y, int z) {
        return yInside(y, -60.0D, 321.0D) ? noodle.getValue((double) x, (double) y, (double) z) : -1.0D;
    }

    public double noodleThickness(int x, int y, int z) {
        return yInside(y, -60.0D, 321.0D)
            ? noodleThickness.getValue((double) x, (double) y, (double) z) * NOODLE_THICKNESS_SCALE + NOODLE_THICKNESS_OFFSET
            : 0.0D;
    }

    public double noodleRidgeA(int x, int y, int z) {
        return yInside(y, -60.0D, 321.0D) ? noodleRidgeA.getValue((double) x * 2.6666666666666665D,
            (double) y * 2.6666666666666665D, (double) z * 2.6666666666666665D) : 0.0D;
    }

    public double noodleRidgeB(int x, int y, int z) {
        return yInside(y, -60.0D, 321.0D) ? noodleRidgeB.getValue((double) x * 2.6666666666666665D,
            (double) y * 2.6666666666666665D, (double) z * 2.6666666666666665D) : 0.0D;
    }

    public double veinToggle(int x, int y, int z) {
        return yInside(y, -60.0D, 51.0D)
            ? oreVeininess.getValue((double) x * 1.5D, (double) y * 1.5D, (double) z * 1.5D) : 0.0D;
    }

    public double veinA(int x, int y, int z) {
        return yInside(y, -60.0D, 51.0D)
            ? oreVeinA.getValue((double) x * 4.0D, (double) y * 4.0D, (double) z * 4.0D) : 0.0D;
    }

    public double veinB(int x, int y, int z) {
        return yInside(y, -60.0D, 51.0D)
            ? oreVeinB.getValue((double) x * 4.0D, (double) y * 4.0D, (double) z * 4.0D) : 0.0D;
    }

    /** The vein "ridged" function from the two interpolated vein noises. */
    public static double veinRidged(double a, double b) {
        return Math.max(Math.abs(a), Math.abs(b)) + ORE_THICKNESS;
    }

    public double veinGap(int x, int y, int z) {
        return oreGap.getValue((double) x, (double) y, (double) z);
    }

    /** The noodle cave density from its four interpolated inputs. */
    public static double noodleDensity(double toggle, double thickness, double ridgeA, double ridgeB) {
        if (toggle >= -1000000.0D && toggle < 0.0D) {
            return 64.0D;
        }
        return thickness + Math.max(Math.abs(ridgeA), Math.abs(ridgeB)) * 1.5D;
    }

    /** final_density from the interpolated main density and the noodle density. */
    public static double finalDensity(double main, double noodle) {
        double c = clamp(main * 0.64D, -1.0D, 1.0D);
        return Math.min(c / 2.0D - c * c * c / 24.0D, noodle);
    }

    public double barrier(int x, int y, int z) {
        return aquiferBarrier.getValue((double) x, (double) y * 0.5D, (double) z);
    }

    public double floodedness(int x, int y, int z) {
        return aquiferFloodedness.getValue((double) x, (double) y * 0.67D, (double) z);
    }

    public double spread(int x, int y, int z) {
        return aquiferSpread.getValue((double) x, (double) y * 0.7142857142857143D, (double) z);
    }

    public double lava(int x, int y, int z) {
        return aquiferLava.getValue((double) x, (double) y, (double) z);
    }
}
