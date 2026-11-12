package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.18's CubicSpline over the terrain shaper's point: continents, erosion, ridges (peaks and valleys), weirdness.
 * Float arithmetic in Mojang's order.
 */
public abstract class Spline {
    public static final int CONTINENTS = 0;
    public static final int EROSION = 1;
    public static final int RIDGES = 2;
    public static final int WEIRDNESS = 3;

    /** point = {continents, erosion, ridges, weirdness}. */
    public abstract float apply(float[] point);

    public static Spline constant(float value) {
        return new Constant(value);
    }

    public static Builder builder(int coordinate) {
        return new Builder(coordinate);
    }

    private static final class Constant extends Spline {
        private final float value;

        Constant(float value) {
            this.value = value;
        }

        @Override
        public float apply(float[] point) {
            return value;
        }
    }

    private static final class Multipoint extends Spline {
        private final int coordinate;
        private final float[] locations;
        private final Spline[] values;
        private final float[] derivatives;

        Multipoint(int coordinate, float[] locations, Spline[] values, float[] derivatives) {
            this.coordinate = coordinate;
            this.locations = locations;
            this.values = values;
            this.derivatives = derivatives;
        }

        @Override
        public float apply(float[] point) {
            float f = point[coordinate];
            float[] locations = this.locations;
            // Mth.binarySearch(0, length, i -> f < locations[i]) - 1
            int from = 0;
            int span = locations.length;
            while (span > 0) {
                int half = span / 2;
                int mid = from + half;
                if (f < locations[mid]) {
                    span = half;
                } else {
                    from = mid + 1;
                    span -= half + 1;
                }
            }
            int i = from - 1;
            int last = locations.length - 1;
            if (i < 0) {
                return values[0].apply(point) + derivatives[0] * (f - locations[0]);
            } else if (i == last) {
                return values[last].apply(point) + derivatives[last] * (f - locations[last]);
            }
            float f1 = locations[i];
            float f2 = locations[i + 1];
            float f3 = (f - f1) / (f2 - f1);
            float f4 = derivatives[i];
            float f5 = derivatives[i + 1];
            float f6 = values[i].apply(point);
            float f7 = values[i + 1].apply(point);
            float f8 = f4 * (f2 - f1) - (f7 - f6);
            float f9 = -f5 * (f2 - f1) + (f7 - f6);
            return (f6 + f3 * (f7 - f6)) + f3 * (1.0F - f3) * (f8 + f3 * (f9 - f8));
        }
    }

    public static final class Builder {
        private final int coordinate;
        private final List<Float> locations = new ArrayList<Float>();
        private final List<Spline> values = new ArrayList<Spline>();
        private final List<Float> derivatives = new ArrayList<Float>();

        Builder(int coordinate) {
            this.coordinate = coordinate;
        }

        public Builder add(float location, float value, float derivative) {
            return add(location, new Constant(value), derivative);
        }

        public Builder add(float location, Spline value, float derivative) {
            if (!locations.isEmpty() && location <= locations.get(locations.size() - 1)) {
                throw new IllegalArgumentException("Please register points in ascending order");
            }
            locations.add(location);
            values.add(value);
            derivatives.add(derivative);
            return this;
        }

        public Spline build() {
            int n = locations.size();
            float[] l = new float[n];
            float[] d = new float[n];
            for (int i = 0; i < n; i++) {
                l[i] = locations.get(i);
                d[i] = derivatives.get(i);
            }
            return new Multipoint(coordinate, l, values.toArray(new Spline[0]), d);
        }
    }
}
