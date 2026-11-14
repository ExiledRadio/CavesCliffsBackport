package com.exiledradio.cavesandcliffsbackport.height;

/**
 * Alfheim's lighting-queue position key and boundary flags, as plain arithmetic (no Minecraft classes, so it is
 * unit-tested on its own: {@code tools/height/run.sh AlfheimKeyTest}). {@link AlfheimHooks} calls this from the
 * patched Alfheim sites.
 *
 * <pre>
 *   narrow (Alfheim 1.6's own):  z 26 bits (0-25, + 2^25)  x 26 bits (26-51, + 2^25)  y 8 bits (52-59, raw)    light 60-63
 *   wide (extended height on):   z 25 bits (0-24, + 2^24)  x 25 bits (25-49, + 2^24)  y 9 bits (50-58, + 64)  bit 59 spare, light 60-63
 * </pre>
 *
 * The wide layout holds y -64..447 (an extended world uses -64..319, a vanilla one 0..255) and x, z within
 * ±16,777,216; further out positions alias in the queue (wrong light there, no crash). Neighbor keys are the key plus
 * a per-facing shift built the way Alfheim builds them; leaving the world is an explicit y compare, since a 384 range
 * has no carry bit.
 */
public final class AlfheimKey {

    /** Alfheim's {@code Y_CHECK}: the carry into the light bits that marks a narrow neighbor key outside 0..255. */
    public static final long NARROW_Y_CHECK = 1L << 60;
    /** Alfheim's {@code M_CHUNK}: a narrow key masked to its chunk. */
    public static final long NARROW_CHUNK_MASK = (0x3FFFFF0L << 26) | 0x3FFFFF0L;

    static final long M25 = (1L << 25) - 1;
    static final long M26 = (1L << 26) - 1;
    static final int WIDE_XZ_OFFSET = 1 << 24;
    static final int NARROW_XZ_OFFSET = 1 << 25;
    static final int WIDE_Y_OFFSET = 64;
    public static final int WIDE_Y_SHIFT = 50;
    public static final int WIDE_X_SHIFT = 25;
    public static final int NARROW_Y_SHIFT = 52;
    public static final int NARROW_X_SHIFT = 26;
    /** A wide key masked to its chunk: x bits 29-49, z bits 4-24. */
    public static final long WIDE_CHUNK_MASK = ((M25 & ~15L) << 25) | (M25 & ~15L);

    private AlfheimKey() {
    }

    public static long encode(int x, int y, int z, boolean wide) {
        if (!wide) {
            // Alfheim's encodeWorldCoord, exactly (y unmasked: a y outside 0..255 garbles into other bits).
            return (long) y << 52 | ((long) x + NARROW_XZ_OFFSET) << 26 | ((long) z + NARROW_XZ_OFFSET);
        }
        return ((long) ((y + WIDE_Y_OFFSET) & 511)) << WIDE_Y_SHIFT
                | (((long) x + WIDE_XZ_OFFSET) & M25) << WIDE_X_SHIFT
                | (((long) z + WIDE_XZ_OFFSET) & M25);
    }

    public static int x(long key, boolean wide) {
        return wide ? (int) ((key >> WIDE_X_SHIFT) & M25) - WIDE_XZ_OFFSET : (int) ((key >> 26) & M26) - NARROW_XZ_OFFSET;
    }

    public static int y(long key, boolean wide) {
        return wide ? (int) ((key >> WIDE_Y_SHIFT) & 511) - WIDE_Y_OFFSET : (int) ((key >> 52) & 255);
    }

    public static int z(long key, boolean wide) {
        return wide ? (int) (key & M25) - WIDE_XZ_OFFSET : (int) (key & M26) - NARROW_XZ_OFFSET;
    }

    /** The y shift of {@code <clinit>}'s neighbor shifts (Alfheim's 52). */
    public static int yShift(boolean wide) {
        return wide ? WIDE_Y_SHIFT : NARROW_Y_SHIFT;
    }

    /** The x shift of {@code <clinit>}'s neighbor shifts (Alfheim's 26). */
    public static int xShift(boolean wide) {
        return wide ? WIDE_X_SHIFT : NARROW_X_SHIFT;
    }

    public static long chunkMask(boolean wide) {
        return wide ? WIDE_CHUNK_MASK : NARROW_CHUNK_MASK;
    }

    /** The neighbor shift for a unit offset, built as Alfheim's {@code <clinit>} builds it. */
    public static long neighborShift(int dx, int dy, int dz, boolean wide) {
        return (long) dy << yShift(wide) | (long) dx << xShift(wide) | (long) dz;
    }

    /**
     * Whether a neighbor key (a position key plus a neighbor shift) is outside the world's height {@code minY} (incl.)
     * to {@code maxY} (excl.). Narrow: Alfheim's carry test, which only knows 0..255.
     */
    public static boolean outside(long key, boolean wide, int minY, int maxY) {
        if (!wide) {
            return (key & NARROW_Y_CHECK) != 0;
        }
        int y = y(key, true);
        return y < minY || y >= maxY;
    }

    // ---------------------------------------------------------------------------------------------
    // Boundary flags: Alfheim keeps one short per boundary, one bit per section. An extended world's 24 sections
    // share them two to a bit (bit b: sections minSection + 2b and + 2b + 1); a flagged pair rechecks both.
    // ---------------------------------------------------------------------------------------------

    /** The flag bit (as a mask) of section y {@code sectionY}. */
    public static int flagMask(int sectionY, boolean extended, int minSection) {
        return extended ? 1 << ((sectionY - minSection) >> 1) : 1 << sectionY;
    }

    /** The lowest block y a flag bit covers. */
    public static int flagBottom(int bit, boolean extended, int minY) {
        return extended ? minY + (bit << 5) : bit << 4;
    }

    /** The highest block y a flag bit covers (inclusive, as Alfheim's {@code (i << 4) + 15}). */
    public static int flagTop(int bit, boolean extended, int minY) {
        return extended ? minY + (bit << 5) + 31 : (bit << 4) + 15;
    }
}
