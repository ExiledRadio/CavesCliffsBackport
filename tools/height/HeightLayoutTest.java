import com.exiledradio.cavesandcliffsbackport.height.HeightLayout;

/**
 * Unit test of the section layout (EXTENDED_HEIGHT section 5.1): every y from -64 to 319 to its slot and back, with
 * and without extended height, and the helpers the patches use.
 *
 * <pre>    tools/height/run.sh HeightLayoutTest</pre>
 */
public final class HeightLayoutTest {

    private static int failures;
    private static int checks;

    public static void main(String[] args) {
        int[] seen = new int[HeightLayout.EXTENDED_SECTIONS];
        for (int y = -64; y < 320; y++) {
            int slot = HeightLayout.slot(y);
            eq(y < 0 ? 20 + ((y + 64) >> 4) : y >> 4, slot, "slot(" + y + ")");
            eq(slot, HeightLayout.sectionSlot(y >> 4), "sectionSlot(" + (y >> 4) + ")");
            int base = HeightLayout.slotBase(slot);
            ok(base <= y && y < base + 16, "slotBase(slot(" + y + ")) = " + base + " holds y");
            eq(y & ~15, base, "slotBase(slot(" + y + ")) is y's section base");
            ok(HeightLayout.inside(y, 24), "y " + y + " inside an extended chunk");
            eq(y >= 0 && y < 256, HeightLayout.inside(y, 16), "y " + y + " inside a vanilla chunk only in 0-255");
            eq(0, HeightLayout.notBelow(y), "notBelow(" + y + ")");
            seen[slot]++;
        }
        for (int s = 0; s < seen.length; s++) {
            eq(16, seen[s], "slot " + s + " holds 16 heights");
        }
        // Vanilla's indexing is unchanged for y 0-319: y >> 4 is the slot.
        for (int y = 0; y < 320; y++) {
            eq(y >> 4, HeightLayout.slot(y), "y >> 4 for y " + y);
        }
        for (int y : new int[] {-65, -100, -2048, 320, 400, 2047, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            eq(HeightLayout.OUTSIDE, HeightLayout.slot(y), "slot(" + y + ") outside");
            ok(!HeightLayout.inside(y, 24) && !HeightLayout.inside(y, 16), "y " + y + " inside no chunk");
        }
        eq(-1, HeightLayout.notBelow(-65), "notBelow(-65)");
        for (int sy : new int[] {-5, 20, 100, -100}) {
            eq(HeightLayout.OUTSIDE, HeightLayout.sectionSlot(sy), "sectionSlot(" + sy + ")");
        }
        // byHeight walks the slots bottom to top.
        int last = Integer.MIN_VALUE;
        boolean[] used = new boolean[24];
        for (int p = 0; p < 24; p++) {
            int slot = HeightLayout.byHeight(p, 24);
            ok(HeightLayout.slotBase(slot) > last, "byHeight(" + p + ", 24) climbs");
            last = HeightLayout.slotBase(slot);
            ok(!used[slot], "byHeight(" + p + ", 24) slot " + slot + " used once");
            used[slot] = true;
        }
        eq(-64, HeightLayout.slotBase(HeightLayout.byHeight(0, 24)), "the lowest section by height is y -64");
        eq(304, HeightLayout.slotBase(HeightLayout.byHeight(23, 24)), "the highest section by height is y 304");
        for (int p = 0; p < 16; p++) {
            eq(p, HeightLayout.byHeight(p, 16), "byHeight in a vanilla chunk is the index");
        }
        // listSection: entity list (from the bottom) to the clamped vanilla section of y >> 4.
        for (int y = HeightLayout.EXTENDED_MIN_Y; y < HeightLayout.EXTENDED_MAX_Y; y++) {
            int list = (y - HeightLayout.EXTENDED_MIN_Y) >> 4;
            eq(Math.max(0, Math.min(15, y >> 4)), HeightLayout.listSection(list, 24), "listSection(" + list + ", 24)");
        }
        for (int p = 0; p < 16; p++) {
            eq(p, HeightLayout.listSection(p, 16), "listSection in a vanilla chunk is the index");
        }
        System.out.println((failures == 0 ? "HEIGHTLAYOUT OK " : "HEIGHTLAYOUT FAILED ") + (checks - failures) + "/" + checks);
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void ok(boolean condition, String what) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("FAIL " + what);
        }
    }

    private static void eq(Object expected, Object actual, String what) {
        ok(expected.equals(actual), what + ": expected " + expected + ", got " + actual);
    }
}
