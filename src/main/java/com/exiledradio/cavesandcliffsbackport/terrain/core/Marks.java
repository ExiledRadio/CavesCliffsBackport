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
 * Each entry is an int: kind &lt;&lt; 20 | (y + 64) &lt;&lt; 8 | x &lt;&lt; 4 | z (chunk-local x and z).
 */
public final class Marks {
    public static final int TICK = 0;
    public static final int POST = 1;

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
