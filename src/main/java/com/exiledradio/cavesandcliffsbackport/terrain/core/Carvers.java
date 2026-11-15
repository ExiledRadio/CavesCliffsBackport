package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;

/**
 * 1.18.2's overworld carvers (NoiseBasedChunkGenerator.applyCarvers, GenerationStep.Carving.AIR): CAVE,
 * CAVE_EXTRA_UNDERGROUND and CANYON with data/worldgen/Carvers' configurations, started from every chunk within 8
 * of the one being carved. Runs in place on a chunk after SurfaceBuilder, and needs nothing of its neighbors'
 * blocks: carving only ever writes the chunk being carved.
 *
 * <p>Every overworld biome lists the same three carvers in the same order (BiomeDefaultFeatures
 * .addDefaultCarversAndLakes from OverworldBiomes.globalOverworldGeneration; the oracle checks it), so the start
 * chunk's carver biome never changes the result and is not looked up.
 *
 * <p>A carver's path (its tunnels, rooms, every radius and step) depends only on the start chunk and the world
 * seed; the chunk being carved only cuts tunnels short (canReach) and clips ellipsoids to itself. So each start
 * chunk's paths are recorded once and replayed for every chunk that can see them, in vanilla's order: start
 * chunk by start chunk, carver by carver, cave by cave, room before its tunnels, a branching tunnel's two branches
 * where it branches. canReach is tested where vanilla tests it, and ellipsoids are clipped as vanilla clips them;
 * a tunnel whose recorded ellipsoids (its branches' included) all miss the chunk's bounds is skipped, which
 * changes nothing because carveEllipsoid returns without effect for each of them.
 *
 * One instance per thread.
 */
public final class Carvers {
    private static final int MIN_Y = OverworldNoise.MIN_Y;
    private static final int HEIGHT = OverworldNoise.HEIGHT;
    /** WorldGenerationContext's min gen y + 1. */
    private static final int LOWEST_CARVED = MIN_Y + 1;
    /** min gen y + gen depth - 1 - 7 (not upgrading). */
    private static final int HIGHEST_CARVED = MIN_Y + HEIGHT - 1 - 7;
    /** lava_level aboveBottom(8) of all three. */
    private static final int LAVA_LEVEL = MIN_Y + 8;
    /** (getRange() * 2 - 1) * 16. */
    private static final int RANGE_BLOCKS = 112;

    private static final int CAVE = 0;
    private static final int CAVE_EXTRA_UNDERGROUND = 1;
    private static final int CANYON = 2;
    private static final float[] PROBABILITY = {0.15F, 0.07F, 0.01F};
    /** UniformHeight maxInclusive: absolute(180), absolute(47); both from aboveBottom(8). */
    private static final int[] CAVE_MAX_Y = {180, 47};

    /** 1.19's mud, in 1.19's #dirt and so carved (only 1.19's mangrove swamp has it). */
    private static final int MUD = BlockCodes.register("mud");
    private static final boolean[] REPLACEABLE = new boolean[Math.max(256, MUD + 1)];

    static {
        REPLACEABLE[MUD] = true;
        int[] codes = {WATER, STONE, GRANITE, DIRT, COARSE_DIRT, PODZOL, GRASS_BLOCK, TERRACOTTA, WHITE_TERRACOTTA,
            ORANGE_TERRACOTTA, YELLOW_TERRACOTTA, LIGHT_GRAY_TERRACOTTA, BROWN_TERRACOTTA, RED_TERRACOTTA, SANDSTONE,
            RED_SANDSTONE, MYCELIUM, PACKED_ICE, DEEPSLATE, CALCITE, SAND, RED_SAND, GRAVEL, TUFF, DEEPSLATE_IRON_ORE,
            RAW_IRON_BLOCK, COPPER_ORE, RAW_COPPER_BLOCK};
        for (int c : codes) {
            REPLACEABLE[c] = true;
        }
    }

    private final long seed;
    private final ChunkFiller.Aquifer aquifer;
    private final SurfaceBuilder surface;
    private final LegacyRandom random = new LegacyRandom(0L);

    // The recorded systems, direct-mapped by start chunk.
    private static final int CACHE_BITS = 6;
    private static final int CACHE_MASK = (1 << CACHE_BITS) - 1;
    private final long[] cacheKeys = new long[1 << CACHE_BITS * 2];
    private final boolean[] cachePresent = new boolean[1 << CACHE_BITS * 2];
    @SuppressWarnings("unchecked")
    private final Path[][] cached = new Path[1 << CACHE_BITS * 2][];
    private static final Path[] NONE = new Path[0];

    // ---- the chunk being carved ----
    private char[] blocks;
    private int cx;
    private int cz;
    private int minX;
    private int minZ;
    private double midX;
    private double midZ;
    private final long[] mask = new long[16 * 16 * HEIGHT / 64];

    public Carvers(OverworldNoise noise, ColumnCache columns, SurfaceBuilder surface) {
        this.seed = noise.seed;
        this.aquifer = new ChunkFiller.Aquifer(noise, columns);
        this.surface = surface;
    }

    /**
     * Carves chunk cx, cz in blocks (ChunkFiller's layout), which ChunkFiller.fill and SurfaceBuilder.build made.
     */
    public void carve(int cx, int cz, char[] blocks) {
        this.blocks = blocks;
        this.cx = cx;
        this.cz = cz;
        minX = cx << 4;
        minZ = cz << 4;
        midX = (double) (minX + 8);
        midZ = (double) (minZ + 8);
        Arrays.fill(mask, 0L);
        aquifer.begin(cx, cz);
        for (int j = -8; j <= 8; ++j) {
            for (int k = -8; k <= 8; ++k) {
                Path[] paths = system(cx + j, cz + k);
                for (Path p : paths) {
                    replay(p);
                }
            }
        }
        this.blocks = null;
    }

    // ---- recording ----

    /** One ellipsoid run: a room (one ellipsoid, no canReach) or a tunnel (steps, maybe two branches at its end). */
    static final class Path {
        final boolean canyon;
        final boolean room;
        /** CAVE's floor level (the skip checker), or the canyon's width factors. */
        final double floor;
        final float[] widths;
        final int end;
        final float thickness;
        int count;
        /** Per step: x, y, z, horizontal radius, vertical radius, step index. */
        double[] steps = new double[6 * 8];
        Path branchA;
        Path branchB;
        /** Over the steps and the branches': the x and z range in which carveEllipsoid's bounds test can pass. */
        double loX = Double.POSITIVE_INFINITY;
        double hiX = Double.NEGATIVE_INFINITY;
        double loZ = Double.POSITIVE_INFINITY;
        double hiZ = Double.NEGATIVE_INFINITY;

        Path(boolean canyon, boolean room, double floor, float[] widths, int end, float thickness) {
            this.canyon = canyon;
            this.room = room;
            this.floor = floor;
            this.widths = widths;
            this.end = end;
            this.thickness = thickness;
        }

        void add(double x, double y, double z, double hr, double vr, int j) {
            if (count * 6 == steps.length) {
                steps = Arrays.copyOf(steps, steps.length * 2);
            }
            int at = count * 6;
            steps[at] = x;
            steps[at + 1] = y;
            steps[at + 2] = z;
            steps[at + 3] = hr;
            steps[at + 4] = vr;
            steps[at + 5] = j;
            count++;
            // carveEllipsoid passes its bounds test when |x - midX| <= 16 + 2 hr; one block of margin either way.
            double reach = 16.0D + hr * 2.0D + 1.0D;
            loX = Math.min(loX, x - reach);
            hiX = Math.max(hiX, x + reach);
            loZ = Math.min(loZ, z - reach);
            hiZ = Math.max(hiZ, z + reach);
        }

        void include(Path p) {
            loX = Math.min(loX, p.loX);
            hiX = Math.max(hiX, p.hiX);
            loZ = Math.min(loZ, p.loZ);
            hiZ = Math.max(hiZ, p.hiZ);
        }
    }

    private Path[] system(int sx, int sz) {
        int slot = (sx & CACHE_MASK) << CACHE_BITS | (sz & CACHE_MASK);
        long key = (long) sx << 32 | (sz & 0xFFFFFFFFL);
        if (cachePresent[slot] && cacheKeys[slot] == key) {
            return cached[slot];
        }
        List<Path> out = null;
        LegacyRandom r = random;
        for (int l = 0; l < 3; ++l) {
            r.setLargeFeatureSeed(seed + (long) l, sx, sz);
            if (r.nextFloat() <= PROBABILITY[l]) {
                if (out == null) {
                    out = new ArrayList<>();
                }
                if (l == CANYON) {
                    canyon(out, r, sx, sz);
                } else {
                    cave(out, r, sx, sz, CAVE_MAX_Y[l]);
                }
            }
        }
        Path[] paths = out == null ? NONE : out.toArray(new Path[0]);
        cacheKeys[slot] = key;
        cachePresent[slot] = true;
        cached[slot] = paths;
        return paths;
    }

    /** CaveWorldCarver.carve. */
    private static void cave(List<Path> out, LegacyRandom r, int sx, int sz, int maxY) {
        int i = RANGE_BLOCKS;
        int j = r.nextInt(r.nextInt(r.nextInt(15) + 1) + 1);
        for (int k = 0; k < j; ++k) {
            double d0 = (double) ((sx << 4) + r.nextInt(16));
            double d1 = (double) r.between(LAVA_LEVEL, maxY);
            double d2 = (double) ((sz << 4) + r.nextInt(16));
            double d3 = (double) r.between(0.7F, 1.4F);
            double d4 = (double) r.between(0.8F, 1.3F);
            double d5 = (double) r.between(-1.0F, -0.4F);
            int l = 1;
            if (r.nextInt(4) == 0) {
                double d6 = (double) r.between(0.1F, 0.9F);
                float f1 = 1.0F + r.nextFloat() * 6.0F;
                // createRoom
                double rd0 = 1.5D + (double) (McMath.sin((float) Math.PI / 2F) * f1);
                double rd1 = rd0 * d6;
                Path room = new Path(false, true, d5, null, 0, 0.0F);
                room.add(d0 + 1.0D, d1, d2, rd0, rd1, 0);
                out.add(room);
                l += r.nextInt(4);
            }
            for (int k1 = 0; k1 < l; ++k1) {
                float f = r.nextFloat() * ((float) Math.PI * 2F);
                float f3 = (r.nextFloat() - 0.5F) / 4.0F;
                float f2 = thickness(r);
                int i1 = i - r.nextInt(i / 4);
                out.add(tunnel(r.nextLong(), d0, d1, d2, d3, d4, f2, f, f3, 0, i1, 1.0D, d5));
            }
        }
    }

    private static float thickness(LegacyRandom r) {
        float f = r.nextFloat() * 2.0F + r.nextFloat();
        if (r.nextInt(10) == 0) {
            f *= r.nextFloat() * r.nextFloat() * 3.0F + 1.0F;
        }
        return f;
    }

    /** CaveWorldCarver.createTunnel, recorded. */
    private static Path tunnel(long seed, double x, double y, double z, double hm, double vm, float thickness,
                               float yaw, float pitch, int start, int end, double yScale, double floor) {
        LegacyRandom random = new LegacyRandom(seed);
        int i = random.nextInt(end / 2) + end / 4;
        boolean flag = random.nextInt(6) == 0;
        float f = 0.0F;
        float f1 = 0.0F;
        Path p = new Path(false, false, floor, null, end, thickness);
        for (int j = start; j < end; ++j) {
            double d0 = 1.5D + (double) (McMath.sin((float) Math.PI * (float) j / (float) end) * thickness);
            double d1 = d0 * yScale;
            float f2 = McMath.cos(pitch);
            x += (double) (McMath.cos(yaw) * f2);
            y += (double) McMath.sin(pitch);
            z += (double) (McMath.sin(yaw) * f2);
            pitch *= flag ? 0.92F : 0.7F;
            pitch += f1 * 0.1F;
            yaw += f * 0.1F;
            f1 *= 0.9F;
            f *= 0.75F;
            f1 += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            f += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;
            if (j == i && thickness > 1.0F) {
                p.branchA = tunnel(random.nextLong(), x, y, z, hm, vm, random.nextFloat() * 0.5F + 0.5F,
                    yaw - ((float) Math.PI / 2F), pitch / 3.0F, j, end, 1.0D, floor);
                p.branchB = tunnel(random.nextLong(), x, y, z, hm, vm, random.nextFloat() * 0.5F + 0.5F,
                    yaw + ((float) Math.PI / 2F), pitch / 3.0F, j, end, 1.0D, floor);
                p.include(p.branchA);
                p.include(p.branchB);
                return p;
            }
            if (random.nextInt(4) != 0) {
                p.add(x, y, z, d0 * hm, d1 * vm, j);
            }
        }
        return p;
    }

    /** CanyonWorldCarver.carve and doCarve, recorded. */
    private static void canyon(List<Path> out, LegacyRandom r, int sx, int sz) {
        int i = RANGE_BLOCKS;
        double d0 = (double) ((sx << 4) + r.nextInt(16));
        int j = r.between(10, 67);
        double d1 = (double) ((sz << 4) + r.nextInt(16));
        float f = r.nextFloat() * ((float) Math.PI * 2F);
        float f1 = r.between(-0.125F, 0.125F);
        double d2 = (double) 3.0F;
        // TrapezoidFloat.of(0, 6, 2)
        float tf = 6.0F - 0.0F;
        float tf1 = (tf - 2.0F) / 2.0F;
        float tf2 = tf - tf1;
        float f2 = 0.0F + r.nextFloat() * tf2 + r.nextFloat() * tf1;
        int k = (int) ((float) i * r.between(0.75F, 1.0F));
        out.add(canyonPath(r.nextLong(), d0, (double) j, d1, f2, f, f1, 0, k, d2));
    }

    private static Path canyonPath(long seed, double x, double y, double z, float thickness, float yaw, float pitch,
                                   int start, int end, double yScale) {
        LegacyRandom random = new LegacyRandom(seed);
        float[] widths = new float[HEIGHT];
        float w = 1.0F;
        for (int j = 0; j < HEIGHT; ++j) {
            if (j == 0 || random.nextInt(3) == 0) {
                w = 1.0F + random.nextFloat() * random.nextFloat();
            }
            widths[j] = w * w;
        }
        float f = 0.0F;
        float f1 = 0.0F;
        Path p = new Path(true, false, 0.0D, widths, end, thickness);
        for (int i = start; i < end; ++i) {
            double d0 = 1.5D + (double) (McMath.sin((float) i * (float) Math.PI / (float) end) * thickness);
            double d1 = d0 * yScale;
            d0 *= (double) random.between(0.75F, 1.0F);
            // updateVerticalRadius(config, random, d1, (float) end, (float) i)
            float vf = 1.0F - Math.abs(0.5F - (float) i / (float) end) * 2.0F;
            float vf1 = 1.0F + 0.0F * vf;
            d1 = (double) vf1 * d1 * (double) random.between(0.75F, 1.0F);
            float f2 = McMath.cos(pitch);
            float f3 = McMath.sin(pitch);
            x += (double) (McMath.cos(yaw) * f2);
            y += (double) f3;
            z += (double) (McMath.sin(yaw) * f2);
            pitch *= 0.7F;
            pitch += f1 * 0.05F;
            yaw += f * 0.05F;
            f1 *= 0.8F;
            f *= 0.5F;
            f1 += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            f += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;
            if (random.nextInt(4) != 0) {
                p.add(x, y, z, d0, d1, i);
            }
        }
        return p;
    }

    // ---- replay on the chunk ----

    private void replay(Path p) {
        if (midX < p.loX || midX > p.hiX || midZ < p.loZ || midZ > p.hiZ) {
            return;
        }
        double[] s = p.steps;
        if (p.room) {
            carveEllipsoid(p, s[0], s[1], s[2], s[3], s[4]);
            return;
        }
        for (int n = 0, at = 0; n < p.count; n++, at += 6) {
            if (!canReach(s[at], s[at + 2], (int) s[at + 5], p.end, p.thickness)) {
                return;
            }
            carveEllipsoid(p, s[at], s[at + 1], s[at + 2], s[at + 3], s[at + 4]);
        }
        if (p.branchA != null) {
            replay(p.branchA);
            replay(p.branchB);
        }
    }

    /** WorldCarver.canReach for the chunk being carved. */
    private boolean canReach(double x, double z, int j, int end, float thickness) {
        double d2 = x - midX;
        double d3 = z - midZ;
        double d4 = (double) (end - j);
        double d5 = (double) (thickness + 2.0F + 16.0F);
        return d2 * d2 + d3 * d3 - d4 * d4 <= d5 * d5;
    }

    /** WorldCarver.carveEllipsoid. */
    private void carveEllipsoid(Path p, double x, double y, double z, double hr, double vr) {
        double d2 = 16.0D + hr * 2.0D;
        if (Math.abs(x - midX) > d2 || Math.abs(z - midZ) > d2) {
            return;
        }
        int i = minX;
        int j = minZ;
        int k = Math.max(McMath.floor(x - hr) - i - 1, 0);
        int l = Math.min(McMath.floor(x + hr) - i, 15);
        int i1 = Math.max(McMath.floor(y - vr) - 1, LOWEST_CARVED);
        int k1 = Math.min(McMath.floor(y + vr) + 1, HIGHEST_CARVED);
        int l1 = Math.max(McMath.floor(z - hr) - j - 1, 0);
        int i2 = Math.min(McMath.floor(z + hr) - j, 15);
        for (int j2 = k; j2 <= l; ++j2) {
            int k2 = i + j2;
            double d3 = ((double) k2 + 0.5D - x) / hr;
            for (int l2 = l1; l2 <= i2; ++l2) {
                int i3 = j + l2;
                double d4 = ((double) i3 + 0.5D - z) / hr;
                if (!(d3 * d3 + d4 * d4 >= 1.0D)) {
                    boolean grass = false;
                    int column = (j2 * 16 + l2) * HEIGHT - MIN_Y;
                    for (int j3 = k1; j3 > i1; --j3) {
                        double d5 = ((double) j3 - 0.5D - y) / vr;
                        if (!skip(p, d3, d5, d4, j3)) {
                            int bit = j2 | l2 << 4 | (j3 - MIN_Y) << 8;
                            long m = 1L << bit;
                            if ((mask[bit >>> 6] & m) == 0L) {
                                mask[bit >>> 6] |= m;
                                grass = carveBlock(column, k2, j3, i3, grass);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean skip(Path p, double dx, double dy, double dz, int y) {
        if (p.canyon) {
            return (dx * dx + dz * dz) * (double) p.widths[y - MIN_Y - 1] + dy * dy / 6.0D >= 1.0D;
        }
        if (dy <= p.floor) {
            return true;
        }
        return dx * dx + dy * dy + dz * dz >= 1.0D;
    }

    /** WorldCarver.carveBlock; returns the column's grass flag. */
    private boolean carveBlock(int column, int x, int y, int z, boolean grass) {
        char[] b = blocks;
        int at = column + y;
        int state = b[at];
        if (state == GRASS_BLOCK || state == MYCELIUM) {
            grass = true;
        }
        if (state >= REPLACEABLE.length || !REPLACEABLE[state]) {
            return grass;
        }
        int carved = y <= LAVA_LEVEL ? LAVA : aquifer.computeSubstance(x, y, z, 0.0D);
        if (carved < 0) {
            return grass;
        }
        b[at] = (char) carved;
        if (grass && b[at - 1] == DIRT) {
            int top = surface.topMaterial(cx, cz, b, x, y - 1, z, carved != AIR);
            if (top >= 0) {
                b[at - 1] = (char) top;
                surface.markFluid(x & 15, y - 1, z & 15, top);
            }
        }
        return grass;
    }
}
