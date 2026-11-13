package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.Arrays;

/**
 * One chunk's positions that 1.18.2 updates once the chunk is in the world, recorded while it is generated (no block
 * changes):
 * <ul>
 *   <li>{@link #TICK}: a fluid tick scheduled with delay 0 ({@code WorldGenLevel.scheduleTick(pos, fluid, 0)}:
 *       SpringFeature after placing its fluid, GeodeFeature at the fluids next to a crack it opens);</li>
 *   <li>{@link #POST}: {@code ChunkAccess.markPosForPostprocessing}, which {@code LevelChunk.postProcessGeneration}
 *       answers when the chunk starts ticking: the fluid there ticks, any other block takes its shape from its
 *       neighbors. Marked by the noise fill and the carvers (aquifer fluids with {@code shouldScheduleFluidUpdate},
 *       see {@link FluidMarks}), {@code WorldGenRegion.setBlock} of a block with {@code hasPostProcess} (small
 *       mushrooms, magma: {@link BlockCodes#F_POST}), {@code Feature.markAboveForPostProcessing} (lakes, disks) and
 *       glow lichen.</li>
 * </ul>
 * Kinds from {@link #REQUEST} on are not 1.18.2's: they ask the game to place a newer version's feature at the
 * position once the chunk is in the world (another backport's code, which needs a World: the generator's
 * {@code terrain.ModernFeatures}). Only a generator given those features ({@link Decorator#modernTrees}) makes them,
 * so a world without them has none.
 *
 * <p>Each entry is an int: kind &lt;&lt; 20 | (y + 64) &lt;&lt; 8 | x &lt;&lt; 4 | z (chunk-local x and z).
 */
public final class Marks {
    public static final int TICK = 0;
    public static final int POST = 1;
    /** The first request kind. */
    public static final int REQUEST = 2;
    /** A fallen tree at the position: FALLEN + the fallen tree's index ({@link Decorator.ModernTrees}), 0 to 7. */
    public static final int FALLEN = 2;
    /** Leaf litter around a tree whose lowest log is at the position. */
    public static final int LITTER = 10;
    /** Leaf litter around a tree whose lowest logs are the 2x2 from the position (a dark oak). */
    public static final int LITTER_2X2 = 11;
    /** The chunk's newer vegetation patches (the generator adds it once per chunk; the position is unused). */
    public static final int VEGETATION = 12;

    private int[] entries = new int[0];
    private int size;

    public static int pack(int localX, int y, int localZ, int kind) {
        return kind << 20 | (y - OverworldNoise.MIN_Y) << 8 | (localX & 15) << 4 | (localZ & 15);
    }

    public static int kind(int entry) {
        return entry >>> 20;
    }

    public static int x(int entry) {
        return entry >> 4 & 15;
    }

    public static int z(int entry) {
        return entry & 15;
    }

    public static int y(int entry) {
        return (entry >> 8 & 0xFFF) + OverworldNoise.MIN_Y;
    }

    public void add(int entry) {
        if (size == entries.length) {
            entries = Arrays.copyOf(entries, Math.max(16, size * 2));
        }
        entries[size++] = entry;
    }

    public int size() {
        return size;
    }

    public int get(int i) {
        return entries[i];
    }

    public int[] toArray() {
        return Arrays.copyOf(entries, size);
    }

    void clear() {
        size = 0;
    }
}
