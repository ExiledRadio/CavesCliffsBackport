import com.exiledradio.cavesandcliffsbackport.height.AlfheimKey;

/**
 * Unit test of Alfheim's position key and boundary flags in both layouts ({@code AlfheimKey}; plan
 * {@code docs/streams/optifine-alfheim.md}, section 2, test 1): round trips, neighbor shifts, the chunk mask, the
 * outside test, and that the narrow layout is exactly Alfheim 1.6's.
 *
 * <pre>    tools/height/run.sh AlfheimKeyTest</pre>
 */
public final class AlfheimKeyTest {

    private static int failures;
    private static int checks;

    /** EnumFacing order (DOWN, UP, NORTH, SOUTH, WEST, EAST) and offsets. */
    private static final int[][] FACINGS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    public static void main(String[] args) {
        int edge = (1 << 24) - 1;
        int[] wideXz = {0, 1, -1, 15, 16, -16, -17, 12345, -98765, 29999984 >> 1, edge, -edge, -edge - 1};
        // 1. Wide: every y of an extended world, at the x/z above.
        for (int x : wideXz) {
            for (int z : wideXz) {
                for (int y = -64; y < 320; y++) {
                    long key = AlfheimKey.encode(x, y, z, true);
                    if (!same(x, y, z, key, true, "wide " + x + "," + y + "," + z)) {
                        continue;
                    }
                    ok((key >>> 59) == 0, "wide key " + x + "," + y + "," + z + " leaves bits 59-63 clear");
                    // The light bits on top (initial brightenings carry them) change nothing of the position.
                    same(x, y, z, key | 15L << 60, true, "wide with light bits " + x + "," + y + "," + z);
                    neighbors(x, y, z, key, true, -64, 320);
                }
            }
        }
        // 2. Wide in a vanilla world: neighbors leave at -1 and 256.
        for (int y = 0; y < 256; y++) {
            neighbors(5, y, -7, AlfheimKey.encode(5, y, -7, true), true, 0, 256);
        }
        // 3. Narrow is Alfheim 1.6's encodeWorldCoord / decodeWorldCoord / <clinit> / Y_CHECK / M_CHUNK, literally.
        int nedge = (1 << 25) - 1;
        int[] narrowXz = {0, 1, -1, 15, 16, -17, 29999984, -29999984, nedge, -nedge};
        for (int x : narrowXz) {
            for (int z : narrowXz) {
                for (int y = 0; y < 256; y++) {
                    long alfheim = (long) y << 52 | ((long) x + 33554432L) << 26 | ((long) z + 33554432L) << 0;
                    long key = AlfheimKey.encode(x, y, z, false);
                    eq(alfheim, key, "narrow encode is Alfheim's at " + x + "," + y + "," + z);
                    eq((int) ((key >> 26) & 67108863L) - 33554432, AlfheimKey.x(key, false), "narrow x is Alfheim's");
                    eq((int) ((key >> 52) & 255L), AlfheimKey.y(key, false), "narrow y is Alfheim's");
                    eq((int) ((key >> 0) & 67108863L) - 33554432, AlfheimKey.z(key, false), "narrow z is Alfheim's");
                    same(x, y, z, key, false, "narrow " + x + "," + y + "," + z);
                    neighbors(x, y, z, key, false, 0, 256);
                }
            }
        }
        eq(4503598620737520L, AlfheimKey.NARROW_CHUNK_MASK, "Alfheim's M_CHUNK");
        eq(1152921504606846976L, AlfheimKey.NARROW_Y_CHECK, "Alfheim's Y_CHECK");
        eq(52, AlfheimKey.yShift(false), "narrow y shift");
        eq(26, AlfheimKey.xShift(false), "narrow x shift");
        for (int[] f : FACINGS) {
            eq((long) f[1] << 52 | (long) f[0] << 26 | (long) f[2] << 0, AlfheimKey.neighborShift(f[0], f[1], f[2], false),
                    "narrow neighbor shift is Alfheim's <clinit>");
        }
        // 4. outside, directly: exactly -65 and 320 in an extended world; -1 and 256 in a vanilla one.
        for (int y = -100; y < 400; y++) {
            long key = AlfheimKey.encode(3, y, 3, true);
            if (y >= -64 && y < 448) {
                eq(y < -64 || y >= 320, AlfheimKey.outside(key, true, -64, 320), "extended outside(" + y + ")");
            }
            if (y >= -64 && y < 448) {
                eq(y < 0 || y >= 256, AlfheimKey.outside(key, true, 0, 256), "vanilla-world outside(" + y + ")");
            }
        }
        ok(AlfheimKey.outside(AlfheimKey.encode(0, -65, 0, true), true, -64, 320), "y -65 encoded directly is outside");
        // 5. Flags: every section once, each bit's range covers exactly its sections, every block in one bit.
        flags(true, -64, 320);
        flags(false, 0, 256);
        System.out.println((failures == 0 ? "ALFHEIMKEY OK " : "ALFHEIMKEY FAILED ") + (checks - failures) + "/" + checks);
        System.exit(failures == 0 ? 0 : 1);
    }

    private static boolean same(int x, int y, int z, long key, boolean wide, String what) {
        boolean good = AlfheimKey.x(key, wide) == x && AlfheimKey.y(key, wide) == y && AlfheimKey.z(key, wide) == z;
        ok(good, what + " round trip: got " + AlfheimKey.x(key, wide) + "," + AlfheimKey.y(key, wide) + ","
                + AlfheimKey.z(key, wide));
        return good;
    }

    private static void neighbors(int x, int y, int z, long key, boolean wide, int minY, int maxY) {
        long chunk = key & AlfheimKey.chunkMask(wide);
        for (int[] f : FACINGS) {
            long n = key + AlfheimKey.neighborShift(f[0], f[1], f[2], wide);
            int ny = y + f[1];
            int nx = x + f[0];
            int nz = z + f[2];
            // The key range's own edge (x or z one past it) is the documented alias, not a neighbor.
            int lim = wide ? 1 << 24 : 1 << 25;
            if (nx >= lim || nx < -lim || nz >= lim || nz < -lim) {
                continue;
            }
            boolean out = ny < minY || ny >= maxY;
            if (!eq(out, AlfheimKey.outside(n, wide, minY, maxY), "outside for " + x + "," + ny + "," + z)) {
                continue;
            }
            if (out) {
                continue;
            }
            same(nx, ny, nz, n, wide, (wide ? "wide" : "narrow") + " neighbor of " + x + "," + y + "," + z);
            boolean sameChunk = (nx >> 4) == (x >> 4) && (nz >> 4) == (z >> 4);
            eq(sameChunk, (n & AlfheimKey.chunkMask(wide)) == chunk, "chunk mask for neighbor " + nx + "," + ny + "," + nz);
        }
    }

    private static void flags(boolean extended, int minY, int maxY) {
        int minSection = minY >> 4;
        int[] blocks = new int[maxY - minY];
        int used = 0;
        for (int s = minSection; s < maxY >> 4; s++) {
            int mask = AlfheimKey.flagMask(s, extended, minSection);
            ok(Integer.bitCount(mask) == 1, "one bit for section " + s);
            int bit = Integer.numberOfTrailingZeros(mask);
            ok(bit < 16, "section " + s + "'s bit " + bit + " fits a short");
            int bottom = AlfheimKey.flagBottom(bit, extended, minY);
            int top = AlfheimKey.flagTop(bit, extended, minY);
            ok(bottom <= s << 4 && (s << 4) + 15 <= top, "bit " + bit + " (" + bottom + ".." + top + ") covers section " + s);
            eq(extended ? 31 : 15, top - bottom, "bit " + bit + " spans " + (extended ? 2 : 1) + " section(s)");
            used |= mask;
        }
        eq(extended ? 12 : 16, Integer.bitCount(used), "bits used");
        for (int bit = 0; bit < 16; bit++) {
            if ((used & 1 << bit) == 0) {
                continue;
            }
            for (int y = AlfheimKey.flagBottom(bit, extended, minY); y <= AlfheimKey.flagTop(bit, extended, minY); y++) {
                blocks[y - minY]++;
            }
        }
        for (int y = minY; y < maxY; y++) {
            eq(1, blocks[y - minY], "block y " + y + " in exactly one bit's range (" + (extended ? "extended" : "vanilla") + ")");
        }
    }

    private static void ok(boolean condition, String what) {
        checks++;
        if (!condition) {
            failures++;
            if (failures < 50) {
                System.out.println("FAIL " + what);
            }
        }
    }

    private static boolean eq(Object expected, Object actual, String what) {
        boolean good = expected.equals(actual);
        ok(good, what + ": expected " + expected + ", got " + actual);
        return good;
    }
}
