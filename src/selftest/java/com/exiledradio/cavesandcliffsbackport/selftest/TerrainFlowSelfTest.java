package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.terrain.FluidUpdates;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Marks;
import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generated fluids keep flowing until they settle, as 1.18.2's do (a spring's fluid tick and an aquifer edge's
 * post-processing are only the first step: every block the flow makes ticks in turn).
 *
 * <p>Two parts, in a fresh 9x9 chunks of 1.18 terrain whose updates still wait:
 * <ul>
 *   <li>rooms: at a spring's position (a fluid tick) and at an aquifer edge's (post-processing), a closed flat room
 *       is built without updates and the fluid put back as a still source; once the chunk's updates ran and the world
 *       ticked long enough, the fluid covers exactly vanilla's spread on a flat floor: lava 3 blocks out (levels 2, 4,
 *       6), water 7 (levels 1 to 7), every block of it still;</li>
 *   <li>the area's own springs and edges: once settled, no flowing block and no liquid over air is left without a
 *       tick to come (a flow that stopped halfway).</li>
 * </ul>
 */
public final class TerrainFlowSelfTest implements SelfTest {

    /** Room half-size: water reaches 7, so 8 and 9 stay dry; the walls are at 10. */
    private static final int R = 10;
    private static final int TICKS = 1500;

    @Override
    public String name() {
        return "terrainflow";
    }

    @Override
    public void run(Check check) throws Exception {
        WorldServer world = check.world;
        if (!Heights.isExtended(world) || !ModernTerrain.owns(world)) {
            return;
        }
        int cx = 1500 >> 4;
        int cz = -1700 >> 4;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                world.getChunkProvider().provideChunk(cx + dx, cz + dz);
            }
        }
        // Room centers: [kind, fluid] -> a marked position of that kind in an inner chunk.
        int[][] wanted = {{Marks.TICK, 0}, {Marks.TICK, 1}, {Marks.POST, 0}, {Marks.POST, 1}};
        List<BlockPos> centers = new ArrayList<BlockPos>();
        List<int[]> rooms = new ArrayList<int[]>();
        List<BlockPos> natural = new ArrayList<BlockPos>();
        int minY = Heights.minY(world) + 3;
        int maxY = Heights.maxY(world) - 3;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Chunk chunk = world.getChunkProvider().getLoadedChunk(cx + dx, cz + dz);
                int[] updates = FluidUpdates.pending(chunk);
                if (updates == null) {
                    continue;
                }
                for (int e : updates) {
                    int kind = Marks.kind(e);
                    if (kind > Marks.POST) {
                        continue;
                    }
                    BlockPos pos = new BlockPos((chunk.x << 4) + Marks.x(e), Marks.y(e), (chunk.z << 4) + Marks.z(e));
                    if (world.getBlockState(pos).getBlock() instanceof BlockLiquid && natural.size() < 64) {
                        natural.add(pos);
                    }
                    if (pos.getY() < minY || pos.getY() > maxY) {
                        continue;
                    }
                    for (int[] w : wanted) {
                        if (w[0] != kind || taken(rooms, w) || !apart(centers, pos)) {
                            continue;
                        }
                        centers.add(pos);
                        rooms.add(w);
                        break;
                    }
                }
            }
        }
        check.eq(wanted.length, rooms.size(), "rooms at springs and aquifer edges of 1.18 terrain near " + (cx << 4) + ","
                + (cz << 4));
        // Natural fluids that touch a room are rebuilt into it.
        for (int i = 0; i < centers.size(); i++) {
            BlockPos c = centers.get(i);
            for (java.util.Iterator<BlockPos> it = natural.iterator(); it.hasNext(); ) {
                BlockPos n = it.next();
                if (Math.abs(n.getX() - c.getX()) <= R + 1 && Math.abs(n.getZ() - c.getZ()) <= R + 1
                        && Math.abs(n.getY() - c.getY()) <= 2) {
                    it.remove();
                }
            }
            IBlockState fluid = rooms.get(i)[1] == 0 ? Blocks.LAVA.getDefaultState() : Blocks.WATER.getDefaultState();
            build(check, c, fluid);
        }
        for (BlockPos c : centers) {
            check.eq(1, count(world, c, null), "the room at " + c + " holds only its source before the chunk's updates");
        }

        FluidUpdates.run(world);
        for (int t = 0; t < TICKS; t++) {
            world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime() + 1);
            world.tickUpdates(false);
        }

        for (int i = 0; i < centers.size(); i++) {
            BlockPos c = centers.get(i);
            boolean lava = rooms.get(i)[1] == 0;
            String what = (lava ? "lava " : "water ") + (rooms.get(i)[0] == Marks.TICK ? "spring" : "aquifer edge")
                    + " at " + c;
            int reach = lava ? 3 : 7;
            int step = lava ? 2 : 1;
            int wrong = 0;
            int moving = 0;
            String first = null;
            for (int dx = -R + 1; dx < R; dx++) {
                for (int dz = -R + 1; dz < R; dz++) {
                    BlockPos p = c.add(dx, 0, dz);
                    IBlockState s = world.getBlockState(p);
                    int d = Math.abs(dx) + Math.abs(dz);
                    String got = s.getBlock() instanceof BlockLiquid ? s.getMaterial() == Material.LAVA ? "lava "
                            + s.getValue(BlockLiquid.LEVEL) : "water " + s.getValue(BlockLiquid.LEVEL) : s.getBlock()
                            .getRegistryName().getPath();
                    String want = d > reach ? "air" : (lava ? "lava " : "water ") + d * step;
                    if (!want.equals(got)) {
                        wrong++;
                        if (first == null) {
                            first = dx + "," + dz + " is " + got + " (wants " + want + ")";
                        }
                    }
                    if (s.getBlock() instanceof BlockDynamicLiquid) {
                        moving++;
                    }
                }
            }
            check.eq(0, wrong, "the " + what + " spread " + reach + " blocks out over a flat floor (first wrong "
                    + first + ")");
            check.eq(0, moving, "the " + what + " settled: every block of it still");
            CavesAndCliffsBackport.LOGGER.info("SELFTEST INFO flow room {}: {} fluid blocks, {} wrong", what,
                    count(world, c, null), wrong);
        }

        // The area's own fluids: a flowing block with no tick to come is a flow that stopped.
        Set<BlockPos> ticking = new HashSet<BlockPos>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                List<NextTickListEntry> list = world.getPendingBlockUpdates(world.getChunk(cx + dx, cz + dz), false);
                if (list != null) {
                    for (NextTickListEntry e : list) {
                        ticking.add(e.position);
                    }
                }
            }
        }
        int stuck = 0;
        int flowing = 0;
        String firstStuck = null;
        for (BlockPos n : natural) {
            for (BlockPos p : BlockPos.getAllInBox(n.add(-8, -16, -8), n.add(8, 1, 8))) {
                IBlockState s = world.getBlockState(p);
                if (!(s.getBlock() instanceof BlockLiquid)) {
                    continue;
                }
                // Moving (a flowing block), or a liquid over air: vanilla's flow never settles like that.
                boolean open = world.getBlockState(p.down()).getMaterial() == Material.AIR;
                if (s.getBlock() instanceof BlockDynamicLiquid || open) {
                    flowing++;
                    if (!ticking.contains(p)) {
                        stuck++;
                        if (firstStuck == null) {
                            firstStuck = p + " " + s;
                        }
                    }
                }
            }
        }
        check.eq(0, stuck, "the area's " + natural.size() + " springs and edges left no flowing block, and no liquid over "
                + "air, without a tick (first " + firstStuck + ")");
        CavesAndCliffsBackport.LOGGER.info("SELFTEST INFO flow area: {} natural fluids, {} flowing blocks still moving, "
                + "{} stuck", natural.size(), flowing - stuck, stuck);
    }

    private static boolean taken(List<int[]> rooms, int[] w) {
        return rooms.contains(w);
    }

    private static boolean apart(List<BlockPos> centers, BlockPos pos) {
        for (BlockPos c : centers) {
            if (Math.abs(c.getX() - pos.getX()) <= 2 * R + 2 && Math.abs(c.getZ() - pos.getZ()) <= 2 * R + 2
                    && Math.abs(c.getY() - pos.getY()) <= 4) {
                return false;
            }
        }
        return true;
    }

    /** A closed room of stone, one block of air high, with the source in the middle of its floor. */
    private static void build(Check check, BlockPos c, IBlockState fluid) {
        check.fill(c.add(-R, -1, -R), c.add(R, 1, R), Blocks.STONE.getDefaultState());
        check.fill(c.add(-R + 1, 0, -R + 1), c.add(R - 1, 0, R - 1), Blocks.AIR.getDefaultState());
        check.world.setBlockState(c, fluid, 2 | 16);
    }

    /** Fluid blocks in the room's air layer. */
    private static int count(WorldServer world, BlockPos c, Material only) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBox(c.add(-R + 1, 0, -R + 1), c.add(R - 1, 0, R - 1))) {
            IBlockState s = world.getBlockState(p);
            if (s.getBlock() instanceof BlockLiquid && (only == null || s.getMaterial() == only)) {
                n++;
            }
        }
        return n;
    }
}
