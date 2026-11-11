package com.exiledradio.cavesandcliffsbackport.terrain.core;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.Spline.CONTINENTS;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Spline.EROSION;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Spline.RIDGES;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Spline.WEIRDNESS;

/** 1.18.2's TerrainShaper.overworld(false): the offset, factor and jaggedness splines. */
public final class TerrainShaper {
    private static final float GLOBAL_OFFSET = -0.50375F;

    public final Spline offsetSampler;
    public final Spline factorSampler;
    public final Spline jaggednessSampler;

    public TerrainShaper() {
        Spline s0 = buildErosionOffsetSpline(-0.15F, 0.0F, 0.0F, 0.1F, 0.0F, -0.03F, false, false);
        Spline s1 = buildErosionOffsetSpline(-0.1F, 0.03F, 0.1F, 0.1F, 0.01F, -0.03F, false, false);
        Spline s2 = buildErosionOffsetSpline(-0.1F, 0.03F, 0.1F, 0.7F, 0.01F, -0.03F, true, true);
        Spline s3 = buildErosionOffsetSpline(-0.05F, 0.03F, 0.1F, 1.0F, 0.01F, 0.01F, true, true);
        offsetSampler = Spline.builder(CONTINENTS).add(-1.1F, 0.044F, 0.0F).add(-1.02F, -0.2222F, 0.0F)
            .add(-0.51F, -0.2222F, 0.0F).add(-0.44F, -0.12F, 0.0F).add(-0.18F, -0.12F, 0.0F).add(-0.16F, s0, 0.0F)
            .add(-0.15F, s0, 0.0F).add(-0.1F, s1, 0.0F).add(0.25F, s2, 0.0F).add(1.0F, s3, 0.0F).build();
        factorSampler = Spline.builder(CONTINENTS).add(-0.19F, 3.95F, 0.0F).add(-0.15F, erosionFactor(6.25F, true), 0.0F)
            .add(-0.1F, erosionFactor(5.47F, true), 0.0F).add(0.03F, erosionFactor(5.08F, true), 0.0F)
            .add(0.06F, erosionFactor(4.69F, false), 0.0F).build();
        jaggednessSampler = Spline.builder(CONTINENTS).add(-0.11F, 0.0F, 0.0F)
            .add(0.03F, buildErosionJaggednessSpline(1.0F, 0.5F, 0.0F, 0.0F), 0.0F)
            .add(0.65F, buildErosionJaggednessSpline(1.0F, 1.0F, 1.0F, 0.0F), 0.0F).build();
    }

    public float offset(float[] point) {
        return offsetSampler.apply(point) + GLOBAL_OFFSET;
    }

    public float factor(float[] point) {
        return factorSampler.apply(point);
    }

    public float jaggedness(float[] point) {
        return jaggednessSampler.apply(point);
    }

    public static float peaksAndValleys(float weirdness) {
        return -(Math.abs(Math.abs(weirdness) - 0.6666667F) - 0.33333334F) * 3.0F;
    }

    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    private static Spline buildErosionJaggednessSpline(float a, float b, float c, float d) {
        Spline s0 = buildRidgeJaggednessSpline(a, c);
        Spline s1 = buildRidgeJaggednessSpline(b, d);
        return Spline.builder(EROSION).add(-1.0F, s0, 0.0F).add(-0.78F, s1, 0.0F).add(-0.5775F, s1, 0.0F)
            .add(-0.375F, 0.0F, 0.0F).build();
    }

    private static Spline buildRidgeJaggednessSpline(float high, float mid) {
        float f = peaksAndValleys(0.4F);
        float f1 = peaksAndValleys(0.56666666F);
        float f2 = (f + f1) / 2.0F;
        Spline.Builder builder = Spline.builder(RIDGES);
        builder.add(f, 0.0F, 0.0F);
        if (mid > 0.0F) {
            builder.add(f2, buildWeirdnessJaggednessSpline(mid), 0.0F);
        } else {
            builder.add(f2, 0.0F, 0.0F);
        }
        if (high > 0.0F) {
            builder.add(1.0F, buildWeirdnessJaggednessSpline(high), 0.0F);
        } else {
            builder.add(1.0F, 0.0F, 0.0F);
        }
        return builder.build();
    }

    private static Spline buildWeirdnessJaggednessSpline(float v) {
        float f = 0.63F * v;
        float f1 = 0.3F * v;
        return Spline.builder(WEIRDNESS).add(-0.01F, f, 0.0F).add(0.01F, f1, 0.0F).build();
    }

    private static Spline erosionFactor(float value, boolean flag) {
        Spline base = Spline.builder(WEIRDNESS).add(-0.2F, 6.3F, 0.0F).add(0.2F, value, 0.0F).build();
        Spline.Builder builder = Spline.builder(EROSION).add(-0.6F, base, 0.0F)
            .add(-0.5F, Spline.builder(WEIRDNESS).add(-0.05F, 6.3F, 0.0F).add(0.05F, 2.67F, 0.0F).build(), 0.0F)
            .add(-0.35F, base, 0.0F).add(-0.25F, base, 0.0F)
            .add(-0.1F, Spline.builder(WEIRDNESS).add(-0.05F, 2.67F, 0.0F).add(0.05F, 6.3F, 0.0F).build(), 0.0F)
            .add(0.03F, base, 0.0F);
        if (flag) {
            Spline s1 = Spline.builder(WEIRDNESS).add(0.0F, value, 0.0F).add(0.1F, 0.625F, 0.0F).build();
            Spline s2 = Spline.builder(RIDGES).add(-0.9F, value, 0.0F).add(-0.69F, s1, 0.0F).build();
            builder.add(0.35F, value, 0.0F).add(0.45F, s2, 0.0F).add(0.55F, s2, 0.0F).add(0.62F, value, 0.0F);
        } else {
            Spline s3 = Spline.builder(RIDGES).add(-0.7F, base, 0.0F).add(-0.15F, 1.37F, 0.0F).build();
            Spline s4 = Spline.builder(RIDGES).add(0.45F, base, 0.0F).add(0.7F, 1.56F, 0.0F).build();
            builder.add(0.05F, s4, 0.0F).add(0.4F, s4, 0.0F).add(0.45F, s3, 0.0F).add(0.55F, s3, 0.0F)
                .add(0.58F, value, 0.0F);
        }
        return builder.build();
    }

    private static float calculateSlope(float a, float b, float c, float d) {
        return (b - a) / (d - c);
    }

    private static Spline buildMountainRidgeSplineWithPoints(float magnitude, boolean flag) {
        Spline.Builder builder = Spline.builder(RIDGES);
        float f2 = mountainContinentalness(-1.0F, magnitude, -0.7F);
        float f4 = mountainContinentalness(1.0F, magnitude, -0.7F);
        float f5 = calculateMountainRidgeZeroContinentalnessPoint(magnitude);
        if (-0.65F < f5 && f5 < 1.0F) {
            float f14 = mountainContinentalness(-0.65F, magnitude, -0.7F);
            float f9 = mountainContinentalness(-0.75F, magnitude, -0.7F);
            float f10 = calculateSlope(f2, f9, -1.0F, -0.75F);
            builder.add(-1.0F, f2, f10);
            builder.add(-0.75F, f9, 0.0F);
            builder.add(-0.65F, f14, 0.0F);
            float f11 = mountainContinentalness(f5, magnitude, -0.7F);
            float f12 = calculateSlope(f11, f4, f5, 1.0F);
            builder.add(f5 - 0.01F, f11, 0.0F);
            builder.add(f5, f11, f12);
            builder.add(1.0F, f4, f12);
        } else {
            float f7 = calculateSlope(f2, f4, -1.0F, 1.0F);
            if (flag) {
                builder.add(-1.0F, Math.max(0.2F, f2), 0.0F);
                builder.add(0.0F, lerp(0.5F, f2, f4), f7);
            } else {
                builder.add(-1.0F, f2, f7);
            }
            builder.add(1.0F, f4, f7);
        }
        return builder.build();
    }

    private static float mountainContinentalness(float ridge, float magnitude, float cutoff) {
        float f2 = 1.0F - (1.0F - magnitude) * 0.5F;
        float f3 = 0.5F * (1.0F - magnitude);
        float f4 = (ridge + 1.17F) * 0.46082947F;
        float f5 = f4 * f2 - f3;
        return ridge < cutoff ? Math.max(f5, -0.2222F) : Math.max(f5, 0.0F);
    }

    private static float calculateMountainRidgeZeroContinentalnessPoint(float magnitude) {
        float f2 = 1.0F - (1.0F - magnitude) * 0.5F;
        float f3 = 0.5F * (1.0F - magnitude);
        return f3 / (0.46082947F * f2) - 1.17F;
    }

    private static Spline buildErosionOffsetSpline(float a, float b, float c, float magnitude, float e, float f,
                                                   boolean extended, boolean flag) {
        Spline s0 = buildMountainRidgeSplineWithPoints(lerp(magnitude, 0.6F, 1.5F), flag);
        Spline s1 = buildMountainRidgeSplineWithPoints(lerp(magnitude, 0.6F, 1.0F), flag);
        Spline s2 = buildMountainRidgeSplineWithPoints(magnitude, flag);
        Spline s3 = ridgeSpline(a - 0.15F, 0.5F * magnitude, lerp(0.5F, 0.5F, 0.5F) * magnitude, 0.5F * magnitude,
            0.6F * magnitude, 0.5F);
        Spline s4 = ridgeSpline(a, e * magnitude, b * magnitude, 0.5F * magnitude, 0.6F * magnitude, 0.5F);
        Spline s5 = ridgeSpline(a, e, e, b, c, 0.5F);
        Spline s6 = ridgeSpline(a, e, e, b, c, 0.5F);
        Spline s7 = Spline.builder(RIDGES).add(-1.0F, a, 0.0F).add(-0.4F, s5, 0.0F).add(0.0F, c + 0.07F, 0.0F).build();
        Spline s8 = ridgeSpline(-0.02F, f, f, b, c, 0.0F);
        Spline.Builder builder = Spline.builder(EROSION).add(-0.85F, s0, 0.0F).add(-0.7F, s1, 0.0F).add(-0.4F, s2, 0.0F)
            .add(-0.35F, s3, 0.0F).add(-0.1F, s4, 0.0F).add(0.2F, s5, 0.0F);
        if (extended) {
            builder.add(0.4F, s6, 0.0F).add(0.45F, s7, 0.0F).add(0.55F, s7, 0.0F).add(0.58F, s6, 0.0F);
        }
        builder.add(0.7F, s8, 0.0F);
        return builder.build();
    }

    private static Spline ridgeSpline(float a, float b, float c, float d, float e, float minSlope) {
        float f = Math.max(0.5F * (b - a), minSlope);
        float f1 = 5.0F * (c - b);
        return Spline.builder(RIDGES).add(-1.0F, a, f).add(-0.4F, b, Math.min(f, f1)).add(0.0F, c, f1)
            .add(0.4F, d, 2.0F * (d - c)).add(1.0F, e, 0.7F * (e - d)).build();
    }
}
