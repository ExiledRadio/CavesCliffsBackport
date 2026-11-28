package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * 1.18.2's spawn point search on the climate ({@code Climate.SpawnFinder}, which {@code MinecraftServer.setInitialSpawn}
 * starts from): the block x, z nearest 0, 0 whose climate best fits {@code OverworldBiomeBuilder.spawnTarget}, inland
 * and away from the rivers' weirdness band. The world spawn is then the first spawnable block of the chunk holding
 * it, or of the chunks spiralling out from it (done on the real chunks, by the caller).
 *
 * <p>With {@link Editions#PALE} the fitness is 1.21.4's: the climate's fit times 2048 squared plus the squared
 * distance (1.18.2 adds a distance penalty that grows with the fourth power). With {@link Editions#DEEP_FREEZE} the
 * climate's fit takes 26.4's Climate.Parameter.distance (a value at a range's max is 1 away) over its five parameters.
 */
public final class SpawnSearch {

    private static final long[][] TARGETS;

    static {
        long fullMin = Climate.quantize(-1.0F);
        long fullMax = Climate.quantize(1.0F);
        long inland = Climate.quantize(-0.11F);
        // Per dimension min, max: temperature, humidity, continentalness, erosion, depth, weirdness.
        TARGETS = new long[][]{
            {fullMin, fullMax, fullMin, fullMax, inland, fullMax, fullMin, fullMax, 0L, 0L,
                Climate.quantize(-1.0F), Climate.quantize(-0.16F)},
            {fullMin, fullMax, fullMin, fullMax, inland, fullMax, fullMin, fullMax, 0L, 0L,
                Climate.quantize(0.16F), Climate.quantize(1.0F)}};
    }

    private final OverworldNoise noise;
    private final ColumnCache columns;
    private final double[] column = new double[OverworldNoise.COL_SIZE];
    private int bestX;
    private int bestZ;
    private long bestFitness;

    public SpawnSearch(OverworldNoise noise, ColumnCache columns) {
        this.noise = noise;
        this.columns = columns;
    }

    /** The climate spawn position: {x, z} in blocks (vanilla's y is 0). */
    public int[] find() {
        bestX = 0;
        bestZ = 0;
        bestFitness = fitness(0, 0);
        radialSearch(2048.0F, 512.0F);
        radialSearch(512.0F, 32.0F);
        return new int[]{bestX, bestZ};
    }

    private void radialSearch(float maxRadius, float step) {
        float angle = 0.0F;
        float radius = step;
        int cx = bestX;
        int cz = bestZ;
        while (radius <= maxRadius) {
            int x = cx + (int) (Math.sin((double) angle) * (double) radius);
            int z = cz + (int) (Math.cos((double) angle) * (double) radius);
            long f = fitness(x, z);
            if (f < bestFitness) {
                bestFitness = f;
                bestX = x;
                bestZ = z;
            }
            angle += step / radius;
            if ((double) angle > Math.PI * 2.0D) {
                angle = 0.0F;
                radius += step;
            }
        }
    }

    /** getSpawnPositionAndFitness: the distance penalty plus the best target's fitness, sampled at quart y 0. */
    long fitness(int x, int z) {
        long squared = (long) x * (long) x + (long) z * (long) z;
        long distance;
        if (Editions.from1214(noise.editions)) {
            distance = squared;
        } else {
            double d0 = 2500.0D * 2500.0D;
            distance = (long) ((double) (10000.0F * 10000.0F) * Math.pow((double) squared / d0, 2.0D));
        }
        // Climate.Sampler.sample(quart x, 0, quart z): the density functions at the quart's block corner.
        int bx = (x >> 2) << 2;
        int bz = (z >> 2) << 2;
        columns.get(bx, bz, column);
        double sx = column[OverworldNoise.COL_SHIFT_X];
        double sz = column[OverworldNoise.COL_SHIFT_Z];
        long[] p = {
            Climate.quantize((float) noise.shifted(noise.temperature, bx, bz, sx, sz)),
            Climate.quantize((float) noise.shifted(noise.vegetation, bx, bz, sx, sz)),
            Climate.quantize((float) column[OverworldNoise.COL_CONTINENTS]),
            Climate.quantize((float) column[OverworldNoise.COL_EROSION]),
            0L,
            Climate.quantize((float) column[OverworldNoise.COL_RIDGES])};
        long best = Long.MAX_VALUE;
        // 26.4 (DEEP_FREEZE): Climate.Parameter.distance's half-open ranges; its targets have no depth.
        boolean v264 = Editions.from264(noise.editions);
        for (long[] t : TARGETS) {
            long sum = 0L;
            for (int d = 0; d < 6; d++) {
                if (v264 && d == 4) {
                    continue;
                }
                long v = p[d];
                long above = v264 ? v - t[d * 2 + 1] + 1L : v - t[d * 2 + 1];
                long below = t[d * 2] - v;
                long dist = above > 0L ? above : Math.max(below, 0L);
                sum += dist * dist;
            }
            best = Math.min(best, sum);
        }
        return Editions.from1214(noise.editions) ? best * 2048L * 2048L + distance : distance + best;
    }
}
