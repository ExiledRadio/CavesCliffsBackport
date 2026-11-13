package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.terrain.FluidUpdates;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Marks;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.18's fluid ticks and post-processing in a world with 1.18 terrain ({@code FluidUpdates}): a generated spring
 * (a scheduled fluid tick) and an aquifer edge (a fluid the noise marked for post-processing) wait with their chunk
 * until its neighbors are in the world, then flow; the waiting updates are saved with the chunk.
 */
public final class TerrainFluidSelfTest implements SelfTest {

    private static final EnumFacing[] OPEN = {EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST,
        EnumFacing.EAST};

    @Override
    public String name() {
        return "terrain";
    }

    @Override
    public void run(Check check) throws Exception {
        WorldServer world = check.world;
        if (!Heights.isExtended(world) || !ModernTerrain.owns(world)) {
            return;
        }
        int[][] centers = {{-250, 260}, {400, 400}, {-600, -150}, {700, -500}};
        boolean spring = false;
        boolean aquifer = false;
        int areas = 0;
        for (int[] c : centers) {
            if (spring && aquifer) {
                break;
            }
            areas++;
            int[] found = area(check, world, c[0], c[1], !spring, !aquifer);
            spring |= found[0] > 0;
            aquifer |= found[1] > 0;
        }
        check.ok(spring, "a generated spring flows once its chunk's neighbors are in the world (" + areas + " areas)");
        check.ok(aquifer, "an aquifer edge the noise marked flows once its chunk's neighbors are in the world");
    }

    /** Generates 9x9 chunks at cx, cz and checks the springs and aquifer edges of the inner 7x7. */
    private static int[] area(Check check, WorldServer world, int cx, int cz, boolean wantSpring, boolean wantAquifer) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                world.getChunkProvider().provideChunk(cx + dx, cz + dz);
            }
        }
        // A ring chunk's own neighbors are not all loaded: it keeps waiting, and its updates go to disk with it.
        Chunk ring = world.getChunkProvider().getLoadedChunk(cx + 5 - 1, cz);
        int[] ringUpdates = FluidUpdates.pending(ring);
        if (ringUpdates != null) {
            NBTTagCompound tag = new NBTTagCompound();
            MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(ring, tag));
            int[] saved = tag.getCompoundTag("cavesandcliffsbackport").getIntArray("PendingUpdates");
            check.ok(java.util.Arrays.equals(ringUpdates, saved), "a waiting chunk's updates are saved with it ("
                    + ringUpdates.length + ")");
        }
        List<BlockPos> springs = new ArrayList<>();
        List<BlockPos> holes = new ArrayList<>();
        List<BlockPos> edges = new ArrayList<>();
        List<BlockPos> edgeOpen = new ArrayList<>();
        int waiting = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                Chunk chunk = world.getChunkProvider().getLoadedChunk(cx + dx, cz + dz);
                int[] updates = FluidUpdates.pending(chunk);
                if (updates == null) {
                    continue;
                }
                waiting++;
                // A spring the aquifer also marked is a spring: the edges are the marks without a tick.
                java.util.Set<Integer> ticks = new java.util.HashSet<>();
                for (int e : updates) {
                    if (Marks.kind(e) == Marks.TICK) {
                        ticks.add(e & 0xFFFFF);
                    }
                }
                for (int e : updates) {
                    BlockPos pos = new BlockPos((chunk.x << 4) + Marks.x(e), Marks.y(e), (chunk.z << 4) + Marks.z(e));
                    IBlockState state = world.getBlockState(pos);
                    if (!(state.getBlock() instanceof BlockLiquid) || state.getValue(BlockLiquid.LEVEL) != 0) {
                        continue;
                    }
                    BlockPos open = openNeighbor(world, pos, state.getMaterial());
                    if (open == null) {
                        continue;
                    }
                    if (Marks.kind(e) == Marks.TICK && springs.size() < 8) {
                        springs.add(pos);
                        holes.add(open);
                    } else if (Marks.kind(e) == Marks.POST && !ticks.contains(e & 0xFFFFF) && edges.size() < 8) {
                        edges.add(pos);
                        edgeOpen.add(open);
                    }
                }
            }
        }
        check.ok(waiting > 0, "chunks of 1.18 terrain wait with updates until their neighbors load (" + waiting + " of 49)");
        long before = FluidUpdates.ticked;
        long nanos = FluidUpdates.nanos;
        long applied = FluidUpdates.applied;
        // What the end of the world tick does, then a few ticks of the scheduled updates the flow made.
        FluidUpdates.run(world);
        double ms = (FluidUpdates.nanos - nanos) / 1e6;
        long chunks = FluidUpdates.applied - applied;
        for (int t = 0; t < 40; t++) {
            world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime() + 1);
            world.tickUpdates(false);
        }
        int left = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                left += FluidUpdates.pending(world.getChunkProvider().getLoadedChunk(cx + dx, cz + dz)) == null ? 0 : 1;
            }
        }
        check.eq(0, left, "every inner chunk ran its updates (" + (FluidUpdates.ticked - before) + " fluid ticks)");
        int springsFlowed = 0;
        for (int i = 0; i < springs.size(); i++) {
            springsFlowed += flowed(world, holes.get(i), world.getBlockState(springs.get(i)).getMaterial()) ? 1 : 0;
        }
        int edgesFlowed = 0;
        for (int i = 0; i < edges.size(); i++) {
            edgesFlowed += flowed(world, edgeOpen.get(i), world.getBlockState(edges.get(i)).getMaterial()) ? 1 : 0;
        }
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info("SELFTEST INFO fluids near {},{}: {} of "
                + "49 chunks waited with updates, {} fluid ticks; springs {} of {} flowed (first {}), aquifer edges {} of {} "
                + "(first {}); ring chunk waiting with {}; {} chunks' updates ran in {} ms", cx, cz, waiting, FluidUpdates.ticked - before, springsFlowed,
                springs.size(), springs.isEmpty() ? "-" : springs.get(0), edgesFlowed, edges.size(),
                edges.isEmpty() ? "-" : edges.get(0), ringUpdates == null ? 0 : ringUpdates.length, chunks,
                String.format(java.util.Locale.ROOT, "%.2f", ms));
        if (wantSpring && !springs.isEmpty()) {
            check.eq(springs.size(), springsFlowed, "springs near " + cx + "," + cz + " flow into their hole (first "
                    + springs.get(0) + ")");
        }
        if (wantAquifer && !edges.isEmpty()) {
            check.eq(edges.size(), edgesFlowed, "aquifer edges near " + cx + "," + cz + " flow (first " + edges.get(0) + ")");
        }
        return new int[] {springs.isEmpty() ? 0 : springsFlowed, edges.isEmpty() ? 0 : edgesFlowed};
    }

    /** A neighbor (below or beside) a still source can flow into, or null. */
    private static BlockPos openNeighbor(WorldServer world, BlockPos pos, Material fluid) {
        for (EnumFacing f : OPEN) {
            BlockPos n = pos.offset(f);
            IBlockState s = world.getBlockState(n);
            if (s.getBlock() == Blocks.AIR || s.getMaterial() == Material.AIR) {
                return n;
            }
        }
        return null;
    }

    /** The fluid reached it: flowing fluid of the same kind, or what lava and water make when they meet. */
    private static boolean flowed(WorldServer world, BlockPos at, Material fluid) {
        IBlockState s = world.getBlockState(at);
        return s.getMaterial() == fluid || s.getBlock() == Blocks.OBSIDIAN || s.getBlock() == Blocks.COBBLESTONE
                || s.getBlock() == Blocks.STONE;
    }
}
