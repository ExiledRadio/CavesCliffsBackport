import com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;

import java.util.Arrays;

/**
 * The tree gate (Decorator.TreeGate, which the game asks 1.12's tree decoration event, so Dynamic Trees can grow its
 * own trees instead): side x side chunks around 0,0 of one seed, three ways. A gate that always says yes must give
 * exactly the chunks no gate gives (same checksum); a gate that always says no must leave no leaves above y 40 (the
 * surface trees gone) while the azalea trees under the lush caves stay; and the blocks below y 0 must not change
 * either way. Arguments: seed side. Prints the counts and "TREE GATE OK", or the failure and exit status 1.
 */
public final class TreeGateCheck {
    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        int side = Integer.parseInt(args[1]);
        long[] none = run(seed, side, 0);
        long[] yes = run(seed, side, 1);
        long[] no = run(seed, side, 2);
        System.out.println("no gate: checksum " + Long.toHexString(none[0]) + ", leaves above 40: " + none[1]
                + ", leaves below 40: " + none[2] + ", deep checksum " + Long.toHexString(none[3]));
        System.out.println("gate yes: checksum " + Long.toHexString(yes[0]) + ", leaves above 40: " + yes[1]);
        System.out.println("gate no: leaves above 40: " + no[1] + ", leaves below 40: " + no[2] + ", deep checksum "
                + Long.toHexString(no[3]));
        boolean ok = true;
        if (yes[0] != none[0]) {
            System.out.println("FAIL: a gate that allows every tree changed the chunks");
            ok = false;
        }
        if (none[1] == 0) {
            System.out.println("FAIL: the area has no surface trees to test with; pick another seed or a bigger side");
            ok = false;
        }
        if (no[1] != 0) {
            System.out.println("FAIL: " + no[1] + " leaves above y 40 with every tree denied");
            ok = false;
        }
        if (no[3] != none[3]) {
            System.out.println("FAIL: denying trees changed the blocks below y 0");
            ok = false;
        }
        System.out.println(ok ? "TREE GATE OK" : "TREE GATE FAILED");
        if (!ok) {
            System.exit(1);
        }
    }

    /**
     * Glow lichen's spread shuffles one static direction array in place, as vanilla 1.18.2 does (MultifaceBlock shuffles
     * Arrays.asList(DIRECTIONS)), so its order carries from one generator to the next in a JVM. Each run starts from
     * vanilla's initial order, as a fresh game does, or the runs could not be compared.
     */
    private static void resetLichenDirections() {
        try {
            java.lang.reflect.Field f = Class.forName("com.exiledradio.cavesandcliffsbackport.terrain.core.LushCaves")
                    .getDeclaredField("DIRECTIONS");
            f.setAccessible(true);
            int[] d = (int[]) f.get(null);
            for (int i = 0; i < d.length; i++) {
                d[i] = i;
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** mode 0: no gate; 1: every tree; 2: no tree. Returns checksum, leaves above 40, leaves at or below 40, deep checksum. */
    private static long[] run(long seed, int side, int mode) {
        resetLichenDirections();
        Pipeline p = new Pipeline(seed, 0, 4096);
        if (mode == 1) {
            p.treeGate((cx, cz, kind) -> true);
        } else if (mode == 2) {
            p.treeGate((cx, cz, kind) -> false);
        }
        long sum = 0L;
        long deep = 0L;
        long high = 0L;
        long low = 0L;
        int h = Pipeline.HEIGHT;
        for (int x = -side / 2; x < side / 2; x++) {
            for (int z = -side / 2; z < side / 2; z++) {
                char[] b = p.finish(x, z).blocks;
                sum = sum * 31L + Arrays.hashCode(b);
                for (int c = 0; c < 256; c++) {
                    for (int y = 0; y < h; y++) {
                        int code = b[c * h + y];
                        int wy = Pipeline.MIN_Y + y;
                        if (wy < 0) {
                            deep = deep * 31L + code;
                        }
                        if (BlockCodes.name(code).contains("leaves")) {
                            if (wy > 40) {
                                high++;
                            } else {
                                low++;
                            }
                        }
                    }
                }
            }
        }
        p.shutdown();
        return new long[] {sum, high, low, deep};
    }
}
