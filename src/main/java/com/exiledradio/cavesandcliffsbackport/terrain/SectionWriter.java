package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BitArray;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.IBlockStatePalette;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * Fills a 1.12 section from a finished chunk's codes the fast way: each distinct state goes into the section's
 * palette once (through vanilla's own {@code set}, so palette growth and resizing are vanilla's), then every block's
 * palette index is written straight into the section's bit array, and the section's block and tick counts are set
 * once. The result is the same section {@code ExtendedBlockStorage.set} block by block would give, at a fraction of
 * the cost (that path looks each state up in the palette and recounts per block).
 *
 * <p>If the fields cannot be reached (another mod replaced the classes), it falls back to vanilla's {@code set}.
 * One per generator; server thread.
 */
final class SectionWriter {

    private static final Field STORAGE = field(BlockStateContainer.class, "field_186021_b");
    private static final Field PALETTE = field(BlockStateContainer.class, "field_186022_c");
    private static final Field BLOCKS = field(ExtendedBlockStorage.class, "field_76682_b");
    private static final Field TICKS = field(ExtendedBlockStorage.class, "field_76683_c");
    private static final boolean FAST = STORAGE != null && PALETTE != null && BLOCKS != null && TICKS != null;

    private static Field field(Class<?> c, String srg) {
        try {
            return ObfuscationReflectionHelper.findField(c, srg);
        } catch (RuntimeException e) {
            CavesAndCliffsBackport.LOGGER.warn("1.18 terrain: {}.{} not found; sections are written block by block.",
                    c.getSimpleName(), srg);
            return null;
        }
    }

    private final StateMap states;
    /** Per code: the stamp of the section it was last seen in, and its palette index there. */
    private int[] seen = new int[256];
    private int[] index = new int[256];
    private int stamp;
    private final int[] distinct = new int[4096];
    /** Where each distinct code first appears (palette resizes keep only the states some cell holds). */
    private final int[] first = new int[4096];
    private final short[] codes = new short[4096];

    SectionWriter(StateMap states) {
        this.states = states;
    }

    /**
     * Section base y (a multiple of 16, -64..304) of a finished chunk into a new section, or null if it holds only
     * air.
     */
    ExtendedBlockStorage write(char[] blocks, int base, boolean sky) {
        int at = base - Pipeline.MIN_Y;
        int n = 0;
        int s = ++stamp;
        boolean any = false;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int col = (x * 16 + z) * Pipeline.HEIGHT + at;
                for (int y = 0; y < 16; y++) {
                    int code = blocks[col + y];
                    codes[y << 8 | z << 4 | x] = (short) code;
                    if (code != 0) {
                        if (code >= seen.length) {
                            seen = java.util.Arrays.copyOf(seen, Math.max(code + 1, seen.length * 2));
                            index = java.util.Arrays.copyOf(index, seen.length);
                        }
                        if (seen[code] != s) {
                            seen[code] = s;
                            first[n] = y << 8 | z << 4 | x;
                            distinct[n++] = code;
                        }
                        any = true;
                    }
                }
            }
        }
        if (!any) {
            return null;
        }
        ExtendedBlockStorage section = new ExtendedBlockStorage(base, sky);
        BlockStateContainer data = section.getData();
        if (!FAST) {
            for (int i = 0; i < 4096; i++) {
                int code = codes[i] & 0xFFFF;
                if (code != 0) {
                    section.set(i & 15, i >> 8, (i >> 4) & 15, states.state(code));
                }
            }
            return section;
        }
        // Each distinct state into the palette through vanilla's set, in its own cell, so a resize keeps them all.
        boolean real = false;
        for (int k = 0; k < n; k++) {
            IBlockState state = states.state(distinct[k]);
            if (state.getBlock() != Blocks.AIR) {
                int i = first[k];
                data.set(i & 15, i >> 8, (i >> 4) & 15, state);
                real = true;
            }
        }
        if (!real) {
            return null;
        }
        try {
            IBlockStatePalette palette = (IBlockStatePalette) PALETTE.get(data);
            BitArray storage = (BitArray) STORAGE.get(data);
            int air = palette.idFor(Blocks.AIR.getDefaultState());
            int nonAir = 0;
            int ticking = 0;
            for (int k = 0; k < n; k++) {
                IBlockState state = states.state(distinct[k]);
                index[distinct[k]] = state.getBlock() == Blocks.AIR ? air : palette.idFor(state);
            }
            for (int i = 0; i < 4096; i++) {
                int code = codes[i] & 0xFFFF;
                if (code == 0) {
                    storage.setAt(i, air);
                } else {
                    IBlockState state = states.state(code);
                    storage.setAt(i, index[code]);
                    if (state.getBlock() != Blocks.AIR) {
                        nonAir++;
                        if (state.getBlock().getTickRandomly()) {
                            ticking++;
                        }
                    }
                }
            }
            BLOCKS.setInt(section, nonAir);
            TICKS.setInt(section, ticking);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return section;
    }
}
