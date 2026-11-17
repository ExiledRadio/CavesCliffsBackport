import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Glow lichen statistics over a VanillaFeatureDump directory, measured as LichenCensusSelfTest measures a 1.12 world:
 * lichen blocks a chunk, underground air (air or water at most 13 below the column's ocean floor) and eligible air
 * (underground air with lichen-able stone above it or beside it; a neighbor outside the dumped area counts as not).
 *   sh tools/terrain/run.sh LichenStats E:/MincraftModding/Workspaces/MC1182Reference/oracle-out/fdef 42 12345 -7346534567 8675309
 */
public class LichenStats {
    /** 0 other solid, 1 air or water, 2 lichen-able stone, 3 lichen, 4 not ground (fluids, plants, leaves, logs). */
    private static int[] kind;
    private static int[] faceCount;
    private static boolean[] floorFace;

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        long chunks = 0, lichen = 0, faces = 0, ugAir = 0, eligible = 0, lichenFloor = 0;
        List<Integer> perChunk = new ArrayList<>();
        for (int a = 1; a < args.length; a++) {
            Map<Long, char[]> all = new HashMap<>();
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(
                    new FileInputStream(new File(dir, "features_" + args[a] + ".bin.gz")), 1 << 16), 1 << 16))) {
                readStates(in);
                int count = in.readInt();
                for (int c = 0; c < count; c++) {
                    int cx = in.readInt();
                    int cz = in.readInt();
                    char[] buf = new char[16 * 16 * 384];
                    for (int i = 0; i < buf.length; i++) {
                        buf[i] = in.readChar();
                    }
                    all.put(key(cx, cz), buf);
                }
            }
            for (Map.Entry<Long, char[]> e : all.entrySet()) {
                int cx = (int) (e.getKey() >> 32);
                int cz = (int) (long) e.getKey();
                char[] buf = e.getValue();
                int cl = 0;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int base = (x * 16 + z) * 384;
                        int top = -65;
                        for (int y = 383; y >= 0; y--) {
                            int k = kind[buf[base + y]];
                            if (k == 0 || k == 2) {
                                top = y - 64;
                                break;
                            }
                        }
                        int limit = top + 1 - 13; // OCEAN_FLOOR_WG height - 13
                        for (int y = 0; y < 384; y++) {
                            int v = buf[base + y];
                            if (kind[v] == 3) {
                                lichen++;
                                cl++;
                                faces += faceCount[v];
                                if (floorFace[v]) {
                                    lichenFloor++;
                                }
                            }
                            if (y - 64 > limit || kind[v] != 1 && kind[v] != 3) {
                                continue;
                            }
                            ugAir++;
                            int wx = (cx << 4) + x, wz = (cz << 4) + z;
                            if (y + 1 < 384 && kind[buf[base + y + 1]] == 2 || stone(all, wx - 1, y, wz) || stone(all, wx + 1, y, wz)
                                    || stone(all, wx, y, wz - 1) || stone(all, wx, y, wz + 1)) {
                                eligible++;
                            }
                        }
                    }
                }
                perChunk.add(cl);
                chunks++;
            }
        }
        Collections.sort(perChunk);
        System.out.printf(Locale.ROOT, "chunks %d, lichen blocks %d (%.2f/chunk, median %d, p90 %d, max %d), faces %d, with floor face %d%n",
            chunks, lichen, lichen / (double) chunks, perChunk.get(perChunk.size() / 2), perChunk.get(perChunk.size() * 9 / 10),
            perChunk.get(perChunk.size() - 1), faces, lichenFloor);
        System.out.printf(Locale.ROOT, "underground air %d (%.0f/chunk): lichen per 1000 air %.3f; eligible air %d (%.0f/chunk): lichen per 1000 eligible %.3f%n",
            ugAir, ugAir / (double) chunks, 1000.0 * lichen / ugAir, eligible, eligible / (double) chunks, 1000.0 * lichen / eligible);
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    private static boolean stone(Map<Long, char[]> all, int wx, int y, int wz) {
        char[] buf = all.get(key(wx >> 4, wz >> 4));
        return buf != null && kind[buf[((wx & 15) * 16 + (wz & 15)) * 384 + y]] == 2;
    }

    private static void readStates(DataInputStream in) throws java.io.IOException {
        int n = in.readInt();
        kind = new int[n];
        faceCount = new int[n];
        floorFace = new boolean[n];
        for (int i = 0; i < n; i++) {
            String s = in.readUTF();
            String b = s.contains("[") ? s.substring(0, s.indexOf('[')) : s;
            if (b.equals("air") || b.equals("cave_air") || b.equals("water")) {
                kind[i] = 1;
            } else if (b.equals("stone") || b.equals("andesite") || b.equals("diorite") || b.equals("granite")
                    || b.equals("dripstone_block") || b.equals("calcite") || b.equals("tuff") || b.equals("deepslate")) {
                kind[i] = 2;
            } else if (b.equals("glow_lichen")) {
                kind[i] = 3;
                for (String p : new String[] {"down", "up", "north", "south", "west", "east"}) {
                    if (s.contains(p + "=true")) {
                        faceCount[i]++;
                    }
                }
                floorFace[i] = s.contains("down=true");
            } else if (b.equals("lava") || b.contains("leaves") || b.contains("log") || b.contains("grass") || b.contains("fern")
                    || b.contains("flower") || b.contains("vine") || b.contains("snow") || b.contains("kelp") || b.contains("seagrass")
                    || b.contains("mushroom") || b.contains("bush") || b.contains("tulip") || b.contains("poppy") || b.contains("dandelion")) {
                kind[i] = 4;
            }
        }
    }
}
