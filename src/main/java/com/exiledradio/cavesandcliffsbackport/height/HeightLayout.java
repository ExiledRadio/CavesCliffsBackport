package com.exiledradio.cavesandcliffsbackport.height;

/**
 * The section layout of an extended chunk, as plain integer arithmetic (no Minecraft classes, so it is unit-tested
 * on its own: {@code tools/height/run.sh HeightLayoutTest}).
 *
 * <pre>
 *   slots  0-15   y   0 .. 255   (vanilla's: storageArrays[y >> 4] in any mod keeps working)
 *   slots 16-19   y 256 .. 319   (the natural continuation of y >> 4)
 *   slots 20-23   y -64 ..  -1   (out of the way of mods that guard y >= 0)
 * </pre>
 *
 * The layout is the same in every world: what differs is the length of a chunk's section array (16 in a vanilla
 * world, 24 in an extended one). {@link #slot(int)} answers a slot for every y from -64 to 319, and a vanilla chunk
 * treats slots 16 and up as outside, exactly as vanilla treats y outside 0-255. Every patched site compares the slot
 * against the array's length, so one mapping serves both kinds of world with no per-world branch.
 */
public final class HeightLayout {

    /** Sections of a vanilla chunk. */
    public static final int VANILLA_SECTIONS = 16;
    /** Sections of an extended chunk. */
    public static final int EXTENDED_SECTIONS = 24;
    /** Lowest block y of an extended world. */
    public static final int EXTENDED_MIN_Y = -64;
    /** One above the highest block y of an extended world. */
    public static final int EXTENDED_MAX_Y = 320;
    /** First slot of the sections below y 0. */
    public static final int FIRST_LOW_SLOT = 20;

    /** The slot of a y outside -64..319: larger than any section array, so a bounds check rejects it. */
    public static final int OUTSIDE = 1 << 20;

    private HeightLayout() {
    }

    /** The storage slot holding block y, or {@link #OUTSIDE}. */
    public static int slot(int y) {
        if (y >= 0) {
            return y < EXTENDED_MAX_Y ? y >> 4 : OUTSIDE;
        }
        return y >= EXTENDED_MIN_Y ? EXTENDED_SECTIONS + (y >> 4) : OUTSIDE;
    }

    /** The storage slot of section y {@code sectionY} (block y >> 4, -4..19), or {@link #OUTSIDE}. */
    public static int sectionSlot(int sectionY) {
        if (sectionY >= 0) {
            return sectionY < EXTENDED_MAX_Y >> 4 ? sectionY : OUTSIDE;
        }
        return sectionY >= EXTENDED_MIN_Y >> 4 ? EXTENDED_SECTIONS + sectionY : OUTSIDE;
    }

    /** The lowest block y of a slot (what {@code ExtendedBlockStorage.getYLocation()} answers for it). */
    public static int slotBase(int slot) {
        return slot < FIRST_LOW_SLOT ? slot << 4 : (slot - EXTENDED_SECTIONS) << 4;
    }

    /**
     * The slot at height position {@code fromBottom} (0 is the lowest section) of a section array of the given length:
     * lets a vanilla loop that walks the array by index walk it by height instead.
     */
    public static int byHeight(int fromBottom, int length) {
        if (length != EXTENDED_SECTIONS) {
            return fromBottom;
        }
        int low = EXTENDED_SECTIONS - FIRST_LOW_SLOT;
        return fromBottom < low ? FIRST_LOW_SLOT + fromBottom : fromBottom - low;
    }

    /**
     * Patched in front of vanilla's {@code y >= 0} section guards: 0 (passes) for every y an extended world can hold,
     * so the guard that follows ({@code slot(y) < length}) decides; -1 below that.
     */
    public static int notBelow(int y) {
        return y >= EXTENDED_MIN_Y ? 0 : -1;
    }

    /** Whether block y has a slot in a section array of the given length. */
    public static boolean inside(int y, int length) {
        return slot(y) < length;
    }
}
