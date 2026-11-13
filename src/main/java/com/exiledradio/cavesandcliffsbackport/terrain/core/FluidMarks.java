package com.exiledradio.cavesandcliffsbackport.terrain.core;

/**
 * The post-processing marks 1.18.2's noise fill and carvers leave on aquifer fluids, for a finished chunk.
 *
 * <p>NoiseBasedChunkGenerator.doFill and WorldCarver.carveBlock mark a fluid block for post-processing when the
 * aquifer's {@code shouldScheduleFluidUpdate()} is true after deciding it. For every answer that is a fluid that flag
 * is one thing: the similarity of the two nearest aquifer centers is at least FLOWING_UPDATE_SIMULARITY
 * (similarity(10 * 10, 12 * 12)), i.e. their squared distances differ by at most 44 (from d1 &gt; 0 on every path that
 * returns a fluid sets it true; d1 &lt;= 0 sets it to d1 &gt;= that threshold). Below y -54 the global lava answers
 * first, with the flag false. So the mark is a function of the position alone and is found after the fact.
 *
 * <p>A mark matters only where the fluid can move when the chunk starts ticking (FlowingFluid.tick of a source
 * spreads down and to the sides). A source whose side and lower neighbors are its own fluid or block motion does
 * nothing, so only exposed fluids are looked at: the flag is computed for those few (the marks vanilla leaves on
 * the rest change nothing).
 */
public final class FluidMarks {
    private static final int MIN_Y = OverworldNoise.MIN_Y;
    private static final int HEIGHT = OverworldNoise.HEIGHT;
    private static final int LAVA_LEVEL = -54;

    private final OverworldNoise noise;

    public FluidMarks(OverworldNoise noise) {
        this.noise = noise;
    }

    /**
     * The finished chunk's updates: decoration's marks where they can change something, then the aquifer's
     * ({@link #scan}). A fluid tick or a post-processed fluid that cannot move does nothing in 1.18 (FlowingFluid.tick
     * of an enclosed source), nor does the post-processing of a block whose shape no neighbor decides (rock, ore,
     * dirt: updateShape gives the same state), so those are left out: what is kept is a fluid that can flow, sand or
     * gravel over air or fluid, a block that is not a full obstacle (plants, glow lichen: their survival) and the
     * hasPostProcess blocks.
     */
    public int[] finish(char[] blocks, char[] north, char[] south, char[] west, char[] east, int cx, int cz, Marks decoration) {
        Marks out = new Marks();
        for (int k = 0; k < decoration.size(); k++) {
            int e = decoration.get(k);
            if (Marks.kind(e) >= Marks.REQUEST) {
                // A newer feature to place in the world: kept whatever the block is.
                out.add(e);
                continue;
            }
            int lx = Marks.x(e);
            int lz = Marks.z(e);
            int i = Marks.y(e) - MIN_Y;
            int code = blocks[(lx * 16 + lz) * HEIGHT + i];
            int f = BlockCodes.flags(code);
            boolean keep;
            if ((f & BlockCodes.F_FLUID) != 0) {
                keep = exposed(blocks, north, south, west, east, lx, lz, i, code == BlockCodes.LAVA);
            } else if (Marks.kind(e) == Marks.TICK) {
                keep = false;
            } else if ((f & BlockCodes.F_POST) != 0 || (f & BlockCodes.F_BLOCKS_MOTION) == 0) {
                keep = !BlockCodes.isAir(code);
            } else {
                keep = FALLING.contains(BlockCodes.name(code)) && i > 0
                    && (BlockCodes.flags(blocks[(lx * 16 + lz) * HEIGHT + i - 1]) & BlockCodes.F_BLOCKS_MOTION) == 0;
            }
            if (keep) {
                out.add(e);
            }
        }
        scan(blocks, north, south, west, east, cx, cz, out);
        return out.toArray();
    }

    private static final java.util.Set<String> FALLING = new java.util.HashSet<>(java.util.Arrays.asList(
        "sand", "red_sand", "gravel"));

    private boolean exposed(char[] blocks, char[] north, char[] south, char[] west, char[] east, int lx, int lz, int i,
            boolean lava) {
        int col = (lx * 16 + lz) * HEIGHT;
        return i > 0 && open(lava, blocks[col + i - 1], true)
            || side(lava, lx == 0 ? west : blocks, lx == 0 ? 15 : lx - 1, lz, i)
            || side(lava, lx == 15 ? east : blocks, lx == 15 ? 0 : lx + 1, lz, i)
            || side(lava, lz == 0 ? north : blocks, lx, lz == 0 ? 15 : lz - 1, i)
            || side(lava, lz == 15 ? south : blocks, lx, lz == 15 ? 0 : lz + 1, i);
    }

    /**
     * Adds a {@link Marks#POST} for every water or lava source of the chunk that the aquifer marked and that has a
     * neighbor (below or beside) it can flow into or meet. Neighbor chunks' blocks are read where given (north,
     * south, west, east: z - 1, z + 1, x - 1, x + 1; null counts as open).
     */
    public void scan(char[] blocks, char[] north, char[] south, char[] west, char[] east, int cx, int cz, Marks out) {
        int bx = cx << 4;
        int bz = cz << 4;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int col = (lx * 16 + lz) * HEIGHT;
                // Below -54 only the global lava fills (flag false); the carvers' lava there takes it too.
                for (int i = LAVA_LEVEL - MIN_Y; i < HEIGHT; i++) {
                    int code = blocks[col + i];
                    if (code != BlockCodes.WATER && code != BlockCodes.LAVA) {
                        continue;
                    }
                    if (exposed(blocks, north, south, west, east, lx, lz, i, code == BlockCodes.LAVA)
                            && flagged(bx + lx, MIN_Y + i, bz + lz)) {
                        out.add(Marks.pack(lx, MIN_Y + i, lz, Marks.POST));
                    }
                }
            }
        }
    }

    private static boolean side(boolean lava, char[] blocks, int lx, int lz, int i) {
        return blocks == null || open(lava, blocks[(lx * 16 + lz) * HEIGHT + i], false);
    }

    /** Whether a source can flow into (or meet) this neighbor: not its own fluid, not a block that stops motion. */
    private static boolean open(boolean lava, int n, boolean below) {
        int f = BlockCodes.flags(n);
        if ((f & BlockCodes.F_BLOCKS_MOTION) != 0) {
            return false;
        }
        if (lava) {
            // Lava beside water does nothing in 1.18 (water cannot be replaced from the side); over water it makes stone.
            return n != BlockCodes.LAVA && (below || (f & BlockCodes.F_WATER) == 0);
        }
        return (f & BlockCodes.F_WATER) == 0;
    }

    /** NoiseBasedAquifer's shouldScheduleFluidUpdate for a fluid it answers at x, y, z (y at least -54). */
    public boolean flagged(int x, int y, int z) {
        if (y < LAVA_LEVEL) {
            return false;
        }
        int gx0 = Math.floorDiv(x - 5, 16);
        int gy0 = Math.floorDiv(y + 1, 12);
        int gz0 = Math.floorDiv(z - 5, 16);
        int dist1 = Integer.MAX_VALUE;
        int dist2 = Integer.MAX_VALUE;
        for (int ox = 0; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                for (int oz = 0; oz <= 1; oz++) {
                    int gx = gx0 + ox;
                    int gy = gy0 + oy;
                    int gz = gz0 + oz;
                    Xoroshiro random = noise.aquiferRandom.at(gx, gy, gz);
                    int ddx = gx * 16 + random.nextInt(10) - x;
                    int ddy = gy * 12 + random.nextInt(9) - y;
                    int ddz = gz * 16 + random.nextInt(10) - z;
                    int dist = ddx * ddx + ddy * ddy + ddz * ddz;
                    if (dist1 >= dist) {
                        dist2 = dist1;
                        dist1 = dist;
                    } else if (dist2 >= dist) {
                        dist2 = dist;
                    }
                }
            }
        }
        // similarity(dist1, dist2) >= similarity(100, 144): 1 - |dist2 - dist1| / 25 >= 1 - 44 / 25.
        return 1.0D - (double) Math.abs(dist2 - dist1) / 25.0D >= 1.0D - 44.0D / 25.0D;
    }
}
