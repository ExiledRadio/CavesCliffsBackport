import com.exiledradio.cavesandcliffsbackport.terrain.core.Beardifier;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Compares the terrain core's Beardifier, for beard_box pieces and their junctions, with vanilla 1.19.2's own
 * (net.minecraft.world.level.levelgen.Beardifier, run from the Mojang-named client jar): the kernel, then the
 * density at every block around made-up pieces of 1 to 6 rigid boxes with ground level deltas and junctions, the
 * sizes of ancient city pieces. Usage: tools/terrain/beardbox119.sh
 */
public final class BeardBoxCheck119 {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Field f = net.minecraft.world.level.levelgen.Beardifier.class.getDeclaredField("BEARD_KERNEL");
        f.setAccessible(true);
        float[] vanillaKernel = (float[]) f.get(null);
        long h = 1L;
        for (float v : vanillaKernel) {
            h = h * 31L + Float.floatToIntBits(v);
        }
        long differences = h == Beardifier.kernelHash119() ? 0 : 1;
        System.out.println("kernel: " + (differences == 0 ? "same" : "differs"));

        Random random = new Random(1019L);
        long checked = 0L;
        long nonZero = 0L;
        double worst = 0.0;
        for (int area = 0; area < 200; area++) {
            int cx = random.nextInt(64) - 32;
            int cz = random.nextInt(64) - 32;
            ObjectArrayList<net.minecraft.world.level.levelgen.Beardifier.Rigid> rigids = new ObjectArrayList<>();
            ObjectArrayList<JigsawJunction> junctions = new ObjectArrayList<>();
            List<Beardifier.Piece> ours = new ArrayList<>();
            int count = 1 + random.nextInt(6);
            for (int p = 0; p < count; p++) {
                int minX = (cx << 4) - 20 + random.nextInt(56);
                int minZ = (cz << 4) - 20 + random.nextInt(56);
                int minY = -60 + random.nextInt(40);
                int maxX = minX + random.nextInt(40);
                int maxY = minY + random.nextInt(30);
                int maxZ = minZ + random.nextInt(40);
                int delta = random.nextInt(4) == 0 ? random.nextInt(5) - 2 : 1;
                rigids.add(new net.minecraft.world.level.levelgen.Beardifier.Rigid(
                        new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ), TerrainAdjustment.BEARD_BOX, delta));
                int[] j = new int[3 * random.nextInt(3)];
                for (int n = 0; n < j.length; n += 3) {
                    j[n] = minX + random.nextInt(maxX - minX + 1);
                    j[n + 1] = minY + random.nextInt(maxY - minY + 1);
                    j[n + 2] = minZ + random.nextInt(maxZ - minZ + 1);
                }
                ours.add(new Beardifier.Piece(minX, minY, minZ, maxX, maxY, maxZ, Beardifier.BEARD_BOX, true, delta, true, j));
            }
            // Vanilla's list: rigid pieces in order, then the junctions of every piece in order (within the chunk's reach).
            Beardifier mine = Beardifier.forChunk(cx, cz, ours);
            for (Beardifier.Piece p : ours) {
                for (int n = 0; n < p.junctions.length; n += 3) {
                    int x = p.junctions[n];
                    int z = p.junctions[n + 2];
                    if (x > (cx << 4) - 12 && z > (cz << 4) - 12 && x < (cx << 4) + 15 + 12 && z < (cz << 4) + 15 + 12) {
                        junctions.add(new JigsawJunction(x, p.junctions[n + 1], z, 0, StructureTemplatePool.Projection.RIGID));
                    }
                }
            }
            // Only pieces close to the chunk, as forStructuresInChunk keeps.
            ObjectArrayList<net.minecraft.world.level.levelgen.Beardifier.Rigid> close = new ObjectArrayList<>();
            for (net.minecraft.world.level.levelgen.Beardifier.Rigid r : rigids) {
                BoundingBox b = r.box();
                int x = cx << 4;
                int z = cz << 4;
                if (b.maxX() >= x - 12 && b.minX() <= x + 15 + 12 && b.maxZ() >= z - 12 && b.minZ() <= z + 15 + 12) {
                    close.add(r);
                }
            }
            net.minecraft.world.level.levelgen.Beardifier vanilla =
                    new net.minecraft.world.level.levelgen.Beardifier(close.listIterator(), junctions.listIterator());
            for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                    for (int y = -64; y < 0; y++) {
                        double v = vanilla.compute(new DensityFunction.SinglePointContext(x, y, z));
                        double o = mine == null ? 0.0 : mine.compute(x, y, z);
                        boolean touched = mine != null && mine.touches(x, y, z, x, y, z);
                        if (v != o || (v != 0.0 && !touched)) {
                            if (differences < 10) {
                                System.out.println("differs at " + x + " " + y + " " + z + ": vanilla " + v + ", ours " + o
                                        + (touched ? "" : " (outside our reach)"));
                            }
                            differences++;
                            worst = Math.max(worst, Math.abs(v - o));
                        }
                        if (v != 0.0) {
                            nonZero++;
                        }
                        checked++;
                    }
                }
            }
        }
        System.out.println(checked + " blocks (" + nonZero + " in a beard), " + differences + " differences"
                + (differences > 0 ? ", worst " + worst : ""));
        System.exit(differences == 0 ? 0 : 1);
    }
}
