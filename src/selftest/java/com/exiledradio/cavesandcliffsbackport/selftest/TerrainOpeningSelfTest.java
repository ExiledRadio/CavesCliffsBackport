package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.waterlog.Waterlogging;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.terrain.FluidUpdates;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain;
import com.exiledradio.cavesandcliffsbackport.terrain.core.FluidMarks;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Marks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockReed;
import net.minecraft.block.BlockSign;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Aquifer fluids that 1.12's population opens ({@code FluidUpdates.populationWrite}): 1.18 marks every flagged aquifer
 * fluid and places its structures before the chunk ticks, so a structure that cuts a marked fluid lets it flow.
 * <ul>
 *   <li>mineshafts: around 1.18 mineshaft starts, a 1.12 corridor that cuts an aquifer the generator saw enclosed;
 *       the water or lava flows into the corridor once the chunk's updates run;</li>
 *   <li>nothing else starts: every fluid block of the area that changes is within reach of a position that got an
 *       update (the generator's own or an opened one);</li>
 *   <li>by hand, in a chunk still waiting: an enclosed source opened through the hook flows if the aquifer flagged
 *       its position and stays still if not (1.18 keeps an unflagged fluid still), and water kept in a block below y 0
 *       (Backport Core's section store, with Fluidlogged API) flows like plain water.</li>
 * </ul>
 */
public final class TerrainOpeningSelfTest implements SelfTest {

    private static final EnumFacing[] FLOW = {EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST,
        EnumFacing.EAST};
    private static final EnumFacing[] SIDES = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    @Override
    public String name() {
        return "terrainopen";
    }

    @Override
    public void run(Check check) throws Exception {
        WorldServer world = check.world;
        if (!Heights.isExtended(world) || !ModernTerrain.owns(world)
                || !(world.getChunkProvider().chunkGenerator instanceof ModernGenerator)) {
            return;
        }
        ModernGenerator gen = (ModernGenerator) world.getChunkProvider().chunkGenerator;
        FluidMarks marks = gen.fluidMarks();
        FluidUpdates.timing = true;
        int found = 0;
        int flowed = 0;
        int areas = 0;
        Set<Long> seen = new HashSet<>();
        // Mineshaft starts on a spiral out from 3000, -3000, until three cuts are found (at most 24 mineshafts).
        for (int i = 0; i < 40 && areas < 24 && found < 3; i++) {
            double a = i * 2.4;
            int nx = 3000 + (int) (Math.cos(a) * (1200 + 260 * i));
            int nz = -3000 + (int) (Math.sin(a) * (1200 + 260 * i));
            BlockPos start = gen.getNearestStructurePos(world, "Mineshaft", new BlockPos(nx, 30, nz), false);
            if (start == null || !seen.add(ChunkPos.asLong(start.getX() >> 4, start.getZ() >> 4))) {
                continue;
            }
            areas++;
            int[] r = mineshaftArea(check, world, gen, marks, start.getX() >> 4, start.getZ() >> 4, areas == 1);
            found += r[0];
            flowed += r[1];
        }
        check.ok(found > 0, "a 1.12 structure piece (around " + areas + " mineshafts) cuts an aquifer the generator "
                + "saw enclosed (" + found + ")");
        check.eq(found, flowed, "the aquifer fluids 1.12 pieces open flow in once their chunk's updates run");
        int[][] centers = {{-7000, 7000}, {-7600, 7400}, {-8200, 6600}, {-6600, 8200}, {-9000, 9000}};
        boolean done = false;
        for (int i = 0; i < centers.length && !done; i++) {
            done = synthetic(check, world, marks, centers[i][0] >> 4, centers[i][1] >> 4, i == centers.length - 1);
        }
        FluidUpdates.timing = false;
    }

    // ---------------------------------------------------------------------------------------------
    // Mineshafts

    /** 9x9 chunks around a mineshaft start; returns {opened fluids next to a corridor, of them flowed}. */
    private static int[] mineshaftArea(Check check, WorldServer world, ModernGenerator gen, FluidMarks marks, int cx,
            int cz, boolean diff) {
        long writes = FluidUpdates.writes;
        long writeNanos = FluidUpdates.writeNanos;
        long t0 = System.nanoTime();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                world.getChunkProvider().provideChunk(cx + dx, cz + dz);
            }
        }
        double genMs = (System.nanoTime() - t0) / 1e6;
        List<BlockPos> sources = new ArrayList<>();
        List<BlockPos> openings = new ArrayList<>();
        List<BlockPos> updated = new ArrayList<>();
        List<String> pieces = new ArrayList<>();
        int opened = 0;
        int alreadyOwn = 0;
        int unflaggedNext = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                Chunk chunk = world.getChunkProvider().getLoadedChunk(cx + dx, cz + dz);
                int[] all = FluidUpdates.pending(chunk);
                if (all != null) {
                    for (int e : all) {
                        if (Marks.kind(e) < Marks.REQUEST) {
                            updated.add(at(chunk, e));
                        }
                    }
                }
                int[] list = FluidUpdates.opened(chunk);
                if (list == null) {
                    continue;
                }
                Set<Integer> own = new HashSet<>();
                for (int e : FluidUpdates.own(chunk)) {
                    own.add(e & 0xFFFFF);
                }
                for (int e : list) {
                    opened++;
                    if (own.contains(e & 0xFFFFF)) {
                        alreadyOwn++;
                        continue;
                    }
                    BlockPos s = at(chunk, e);
                    check.ok(marks.flagged(s.getX(), s.getY(), s.getZ()), "an opened fluid's position is flagged (" + s + ")");
                    for (EnumFacing f : FLOW) {
                        BlockPos o = s.offset(f);
                        String piece = piece(gen, world, o);
                        Block ob = world.getBlockState(o).getBlock();
                        if (piece == null && (ob == Blocks.WEB || ob == Blocks.RAIL || ob == Blocks.OAK_FENCE
                                || ob == Blocks.TORCH)) {
                            // Blocks only a 1.12 mineshaft corridor places underground here (isInsideStructure can
                            // miss a corridor whose start is held by a neighbor's population).
                            piece = "Mineshaft (" + ob.getRegistryName() + ")";
                        }
                        if (passable(world.getBlockState(o)) && piece != null) {
                            pieces.add(piece);
                            sources.add(s);
                            openings.add(o);
                            break;
                        }
                    }
                    if ((sources.isEmpty() || !sources.get(sources.size() - 1).equals(s)) && logged++ < 8) {
                        StringBuilder b = new StringBuilder();
                        for (EnumFacing f : EnumFacing.values()) {
                            BlockPos o = s.offset(f);
                            b.append(f).append('=').append(world.getBlockState(o)).append(piece(gen, world, o) == null ? "" : "*")
                                    .append(' ');
                        }
                        CavesAndCliffsBackport.LOGGER.info("SELFTEST INFO opened fluid {} ({}) not next to a passable piece: {}",
                                s, world.getBlockState(s), b);
                    }
                }
                // Unflagged sources next to a corridor: 1.18 keeps them still (the area diff below checks it).
                for (int i = 0; i < 64; i++) {
                    // (counted on a sample of columns only, for the log)
                    int lx = i * 7 & 15;
                    int lz = i * 11 & 15;
                    for (int y = -54; y < 64; y++) {
                        BlockPos p = new BlockPos((chunk.x << 4) + lx, y, (chunk.z << 4) + lz);
                        IBlockState st = world.getBlockState(p);
                        if (st.getBlock() instanceof BlockLiquid && st.getValue(BlockLiquid.LEVEL) == 0
                                && !marks.flagged(p.getX(), y, p.getZ())) {
                            for (EnumFacing f : FLOW) {
                                if (passable(world.getBlockState(p.offset(f)))
                                        && gen.isInsideStructure(world, "Mineshaft", p.offset(f))) {
                                    unflaggedNext++;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        int[][] snapshot = diff ? snapshot(world, cx, cz) : null;
        List<Material> fluids = new ArrayList<>();
        for (BlockPos s : sources) {
            fluids.add(world.getBlockState(s).getMaterial());
        }
        long opTicked = FluidUpdates.openedTicked;
        FluidUpdates.run(world);
        for (int t = 0; t < 40; t++) {
            world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime() + 1);
            world.tickUpdates(false);
        }
        int flowed = 0;
        BlockPos firstMissed = null;
        for (int i = 0; i < sources.size(); i++) {
            if (reached(world, openings.get(i), fluids.get(i))) {
                flowed++;
            } else if (firstMissed == null) {
                firstMissed = sources.get(i);
            }
        }
        if (firstMissed != null) {
            check.fail("an opened aquifer fluid at " + firstMissed + " did not flow into the corridor");
        }
        double perWrite = FluidUpdates.writes == writes ? 0 : (FluidUpdates.writeNanos - writeNanos) / 1e3
                / (FluidUpdates.writes - writes);
        CavesAndCliffsBackport.LOGGER.info("SELFTEST INFO openings near mineshaft chunk {},{}: {} fluids opened by the "
                + "population ({} already exposed at finish), {} next to a 1.12 piece {}, {} flowed (first {}); {} ticked as opened; "
                + "unflagged sources next to a corridor (sampled columns) {}; {} population writes seen, {} us each, "
                + "{} ms of hook time in {} ms of generation", cx, cz, opened, alreadyOwn, sources.size(), pieces, flowed,
                sources.isEmpty() ? "-" : sources.get(0), FluidUpdates.openedTicked - opTicked, unflaggedNext,
                FluidUpdates.writes - writes, String.format(Locale.ROOT, "%.3f", perWrite),
                String.format(Locale.ROOT, "%.1f", (FluidUpdates.writeNanos - writeNanos) / 1e6),
                String.format(Locale.ROOT, "%.0f", genMs));
        if (snapshot != null) {
            compare(check, world, cx, cz, snapshot, updated);
        }
        return new int[] {sources.size(), flowed};
    }

    /** Every block state of the inner 7x7 chunks, y -64 to 319. */
    private static int[][] snapshot(WorldServer world, int cx, int cz) {
        int[][] out = new int[49][];
        int k = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                Chunk c = world.getChunkProvider().getLoadedChunk(cx + dx, cz + dz);
                int[] ids = new int[256 * 384];
                int i = 0;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = -64; y < 320; y++) {
                            ids[i++] = Block.getStateId(c.getBlockState((c.x << 4) + lx, y, (c.z << 4) + lz));
                        }
                    }
                }
                out[k++] = ids;
            }
        }
        return out;
    }

    /**
     * Every fluid block that changed (a fluid before or after) is within reach of an updated position: 16 blocks
     * across and at most one above it (fluids flow down and out). An unflagged or untouched aquifer that started
     * flowing would change blocks with no update near.
     */
    private static void compare(Check check, WorldServer world, int cx, int cz, int[][] before, List<BlockPos> updated) {
        int changed = 0;
        int far = 0;
        BlockPos firstFar = null;
        int k = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                Chunk c = world.getChunkProvider().getLoadedChunk(cx + dx, cz + dz);
                int[] ids = before[k++];
                int i = 0;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = -64; y < 320; y++) {
                            int was = ids[i++];
                            int x = (c.x << 4) + lx;
                            int z = (c.z << 4) + lz;
                            IBlockState now = c.getBlockState(x, y, z);
                            if (Block.getStateId(now) == was) {
                                continue;
                            }
                            IBlockState old = Block.getStateById(was);
                            if (!(old.getMaterial().isLiquid() || now.getMaterial().isLiquid())) {
                                continue;
                            }
                            changed++;
                            if (!near(updated, x, y, z)) {
                                far++;
                                if (firstFar == null) {
                                    firstFar = new BlockPos(x, y, z);
                                }
                            }
                        }
                    }
                }
            }
        }
        CavesAndCliffsBackport.LOGGER.info("SELFTEST INFO openings area diff near {},{}: {} fluid blocks changed, {} updated "
                + "positions, {} changes with no update near (first {})", cx, cz, changed, updated.size(), far,
                firstFar == null ? "-" : firstFar);
        check.ok(changed > 0, "fluids flow in the area once its chunks' updates run (" + changed + " blocks)");
        check.eq(0, far, "no fluid flows away from the updated positions: untouched aquifers stay still (first "
                + (firstFar == null ? "-" : firstFar.toString()) + ")");
    }

    private static boolean near(List<BlockPos> updated, int x, int y, int z) {
        for (BlockPos u : updated) {
            if (Math.abs(u.getX() - x) <= 16 && Math.abs(u.getZ() - z) <= 16 && y <= u.getY() + 1) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // By hand

    /**
     * In the center chunk of a fresh 5x5 (ready, its updates not run yet): enclosed water sources with one solid
     * neighbor walled in on every other side. The solid is set to air through the population hook, as a structure
     * piece would.
     */
    private static boolean synthetic(Check check, WorldServer world, FluidMarks marks, int cx, int cz, boolean last) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                world.getChunkProvider().provideChunk(cx + dx, cz + dz);
            }
        }
        List<Chunk> inner = new ArrayList<>();
        Set<Long> updated = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk c = world.getChunkProvider().getLoadedChunk(cx + dx, cz + dz);
                inner.add(c);
                int[] own = FluidUpdates.own(c);
                for (int e : own == null ? new int[0] : own) {
                    updated.add(at(c, e).toLong());
                }
            }
        }
        BlockPos[] flagged = site(world, marks, inner, updated, true, -50, 63, null);
        BlockPos[] unflagged = site(world, marks, inner, updated, false, -50, 63, flagged);
        BlockPos[] kept = null;
        Block holder = null;
        boolean waterlog = Waterlogging.isAvailable();
        if (waterlog) {
            kept = site(world, marks, inner, updated, true, -54, -1, flagged);
            if (kept != null) {
                for (Block b : new Block[] {Blocks.GLASS_PANE, Blocks.IRON_BARS, Blocks.OAK_FENCE}) {
                    if (Waterlogging.canHoldWater(b.getDefaultState(), world, kept[0])) {
                        holder = b;
                        break;
                    }
                }
            }
        }
        if (flagged == null || unflagged == null || waterlog && (kept == null || holder == null)) {
            CavesAndCliffsBackport.LOGGER.info("SELFTEST INFO openings by hand: no full set of sites near chunk {},{} "
                    + "(flagged {}, unflagged {}, kept {}, holder {})", cx, cz, flagged, unflagged, kept, holder);
            if (!last) {
                FluidUpdates.run(world);
                return false;
            }
        }
        Chunk chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
        check.ok(FluidUpdates.waiting(chunk), "a chunk of 1.18 terrain waits for its updates");
        check.ok(flagged != null, "an enclosed flagged water source in a waiting chunk");
        check.ok(unflagged != null, "an enclosed unflagged water source in a waiting chunk");
        if (kept != null && holder != null) {
            // Water a feature kept in a block it placed in the aquifer's water (before the chunk is finished).
            Waterlogging.placeWithWater(world, kept[0], holder.getDefaultState(), 2 | 16);
            check.ok(Waterlogging.hasWaterSource(world, kept[0]), "water kept in a block below y 0 (" + kept[0] + ")");
        } else if (waterlog) {
            check.fail("no site for water kept in a block below y 0 (" + (kept == null ? "no site" : "no block holds water") + ")");
        }
        ChunkPos populating = new ChunkPos(cx - 1, cz - 1);
        for (BlockPos[] s : new BlockPos[][] {flagged, unflagged, kept != null && holder != null ? kept : null}) {
            if (s != null) {
                // Chunk.setBlockState's hook first, as the patched method runs it, then the write itself (flag 2, as
                // structure pieces place their blocks: no neighbor updates).
                FluidUpdates.populationWrite(populating, world.getChunk(s[1]), s[1], Blocks.AIR.getDefaultState());
                world.setBlockState(s[1], Blocks.AIR.getDefaultState(), 2);
            }
        }
        if (flagged != null) {
            check.ok(isOpened(world, flagged[0]), "the flagged source the write opened waits for its update (" + flagged[0] + ")");
        }
        if (unflagged != null) {
            check.ok(!isOpened(world, unflagged[0]), "an unflagged source the write opened gets no update (" + unflagged[0] + ")");
        }
        if (kept != null && holder != null) {
            check.ok(isOpened(world, kept[0]), "water kept in a block the write opened waits for its update (" + kept[0] + ")");
        }
        FluidUpdates.run(world);
        for (Chunk c : inner) {
            check.ok(!FluidUpdates.waiting(c), "the chunk's updates ran (" + c.x + "," + c.z + ")");
        }
        for (int t = 0; t < 40; t++) {
            world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime() + 1);
            world.tickUpdates(false);
        }
        if (flagged != null) {
            check.ok(reached(world, flagged[1], Material.WATER), "the opened flagged source flows into the opening ("
                    + flagged[1] + ": " + world.getBlockState(flagged[1]) + ")");
        }
        if (unflagged != null) {
            check.eq(Blocks.AIR.getDefaultState(), world.getBlockState(unflagged[1]),
                    "an opened unflagged source stays still, as 1.18.2 keeps it (" + unflagged[1] + ")");
        }
        if (kept != null && holder != null) {
            check.ok(reached(world, kept[1], Material.WATER), "water kept in a block below y 0 flows into the opening ("
                    + kept[1] + ": " + world.getBlockState(kept[1]) + ")");
            check.ok(world.getBlockState(kept[0]).getBlock() == holder && Waterlogging.hasWaterSource(world, kept[0]),
                    "the block keeps its water source after its tick");
        }
        CavesAndCliffsBackport.LOGGER.info("SELFTEST INFO openings by hand in chunk {},{}: flagged {}, unflagged {}, kept {} in {}",
                cx, cz, flagged == null ? "-" : flagged[0], unflagged == null ? "-" : unflagged[0],
                kept == null ? "-" : kept[0], holder == null ? "-" : holder.getRegistryName());
        return true;
    }

    private static boolean isOpened(WorldServer world, BlockPos s) {
        int[] opened = FluidUpdates.opened(world.getChunk(s));
        int key = Marks.pack(s.getX(), s.getY(), s.getZ(), 0);
        for (int e : opened == null ? new int[0] : opened) {
            if ((e & 0xFFFFF) == key) {
                return true;
            }
        }
        return false;
    }

    /**
     * {source, solid}: a still water source of the chunk (flagged or not), with no neighbor it can flow into, not
     * updated, none of its neighbors updated, and a side neighbor in the chunk that is a full opaque block walled in
     * by full opaque blocks on its five other sides.
     */
    private static BlockPos[] site(WorldServer world, FluidMarks marks, List<Chunk> chunks, Set<Long> updated,
            boolean flag, int minY, int maxY, BlockPos[] not) {
        for (Chunk chunk : chunks) {
            BlockPos[] s = site(world, marks, chunk, updated, flag, minY, maxY, not);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    private static BlockPos[] site(WorldServer world, FluidMarks marks, Chunk chunk, Set<Long> updated, boolean flag,
            int minY, int maxY, BlockPos[] not) {
        for (int y = minY; y <= maxY; y++) {
            for (int lx = 1; lx < 15; lx++) {
                for (int lz = 1; lz < 15; lz++) {
                    BlockPos s = new BlockPos((chunk.x << 4) + lx, y, (chunk.z << 4) + lz);
                    if (not != null && (s.equals(not[0]) || s.distanceSq(not[0]) < 64)) {
                        continue;
                    }
                    IBlockState st = world.getBlockState(s);
                    if (st.getBlock() != Blocks.WATER || st.getValue(BlockLiquid.LEVEL) != 0
                            || marks.flagged(s.getX(), y, s.getZ()) != flag || updated.contains(s.toLong())) {
                        continue;
                    }
                    boolean enclosed = true;
                    for (EnumFacing f : EnumFacing.values()) {
                        BlockPos n = s.offset(f);
                        IBlockState ns = world.getBlockState(n);
                        if (updated.contains(n.toLong()) || f != EnumFacing.UP && ns.getMaterial() != Material.WATER
                                && !ns.getMaterial().blocksMovement()) {
                            enclosed = false;
                            break;
                        }
                    }
                    if (!enclosed) {
                        continue;
                    }
                    for (EnumFacing f : SIDES) {
                        BlockPos o = s.offset(f);
                        if (!world.getBlockState(o).isOpaqueCube() || (o.getX() & 15) == 0 || (o.getX() & 15) == 15
                                || (o.getZ() & 15) == 0 || (o.getZ() & 15) == 15) {
                            continue;
                        }
                        boolean walled = true;
                        for (EnumFacing g : EnumFacing.values()) {
                            if (g != f.getOpposite() && !world.getBlockState(o.offset(g)).isOpaqueCube()) {
                                walled = false;
                                break;
                            }
                        }
                        if (walled) {
                            return new BlockPos[] {s, o};
                        }
                    }
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private static int logged;

    private static final String[] PIECES ={"Mineshaft", "Stronghold", "Village", "Temple", "Monument", "Mansion"};

    /** The 1.12 structure whose piece holds the position, or null. */
    private static String piece(ModernGenerator gen, WorldServer world, BlockPos o) {
        for (String name : PIECES) {
            if (gen.isInsideStructure(world, name, o)) {
                return name;
            }
        }
        return null;
    }

    private static BlockPos at(Chunk chunk, int e) {
        return new BlockPos((chunk.x << 4) + Marks.x(e), Marks.y(e), (chunk.z << 4) + Marks.z(e));
    }

    /** A block 1.12's flowing liquid can flow into (BlockDynamicLiquid.isBlocked, not a liquid). */
    private static boolean passable(IBlockState s) {
        Block b = s.getBlock();
        Material m = s.getMaterial();
        return !m.isLiquid() && !m.blocksMovement() && m != Material.PORTAL && m != Material.STRUCTURE_VOID
                && !(b instanceof BlockDoor) && !(b instanceof BlockSign) && !(b instanceof BlockLadder)
                && !(b instanceof BlockReed);
    }

    /** The fluid reached it: the same fluid, or what lava and water make when they meet. */
    private static boolean reached(WorldServer world, BlockPos at, Material fluid) {
        IBlockState s = world.getBlockState(at);
        return s.getMaterial() == fluid || s.getBlock() == Blocks.OBSIDIAN || s.getBlock() == Blocks.COBBLESTONE
                || s.getBlock() == Blocks.STONE
                || fluid == Material.WATER && Waterlogging.isAvailable() && Waterlogging.hasWater(world, at);
    }
}
