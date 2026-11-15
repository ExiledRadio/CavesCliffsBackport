package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Marks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * What 1.18.2 does to a generated chunk when it starts ticking, in a 1.12 world with 1.18 terrain: the fluid ticks
 * its features scheduled (springs, the fluids beside a geode's crack: delay 0) and its post-processing
 * ({@code LevelChunk.postProcessGeneration}: the aquifer fluids the noise and carvers marked tick, other marked blocks
 * take their shape from their neighbors). Without this a spring in a cave wall stayed a still source.
 *
 * <p>1.18 runs both once the 3x3 chunks around are complete (ChunkMap.prepareTickingChunk); here, at the end of the
 * world tick after the chunk and its eight neighbors are loaded and the four populations that write into it are
 * done. The positions ({@link Marks} entries, from the generator) wait with the chunk: in memory while it is loaded,
 * in its NBT when it is saved before that.
 *
 * <p>Per position, as 1.18:
 * <ul>
 *   <li>a still or flowing liquid gets its tick now: a still one is first made the flowing block, as 1.12's
 *       BlockStaticLiquid.updateLiquid does, then BlockDynamicLiquid.updateTick runs on the flowing state, which
 *       spreads a source down and to the sides as FlowingFluid.tick does (every block it places ticks in turn) and
 *       leaves the source still (no mixing check on the source itself, as 1.18's tick has none);</li>
 *   <li>post-processing of any other block: a plant that cannot stay goes without a drop (1.18's updateShape gives
 *       air), anything else gets a neighbor update (sand and gravel over a lake fall, glow lichen loses faces
 *       without support).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class FluidUpdates {

    private static final String KEY = "PendingUpdates";

    /** Chunks with updates still to run (weak: an unloaded chunk's entry goes with it, after its save wrote it). */
    private static final Map<Chunk, int[]> PENDING = Collections.synchronizedMap(new WeakHashMap<Chunk, int[]>());
    /** Chunks to look at after this tick: loaded, or next to one that loaded. */
    private static final Set<Chunk> CHECK = Collections.newSetFromMap(new WeakHashMap<Chunk, Boolean>());

    /** Counters (self-test, log). */
    public static long applied;
    public static long ticked;
    /** Chunks whose newer features (ModernFeatures) ran. */
    public static long modernRuns;
    /** Time spent running updates (not looking for ready chunks), in nanoseconds. */
    public static long nanos;

    private FluidUpdates() {
    }

    /** A chunk the generator just made: its updates wait for its neighbors. */
    static void put(Chunk chunk, int[] updates) {
        if (updates.length > 0) {
            PENDING.put(chunk, updates);
        }
    }

    /** For the self-test: the updates a loaded chunk still waits with (null for none). */
    public static int[] pending(Chunk chunk) {
        return PENDING.get(chunk);
    }

    @SubscribeEvent
    public static void onDataLoad(ChunkDataEvent.Load event) {
        if (event.getWorld().isRemote) {
            return;
        }
        NBTTagCompound tag = event.getData().getCompoundTag(CavesAndCliffsBackport.MOD_ID);
        if (tag.hasKey(KEY)) {
            int[] updates = tag.getIntArray(KEY);
            if (updates.length > 0) {
                PENDING.put(event.getChunk(), updates);
            }
        }
    }

    @SubscribeEvent
    public static void onDataSave(ChunkDataEvent.Save event) {
        int[] updates = PENDING.get(event.getChunk());
        if (updates == null) {
            return;
        }
        NBTTagCompound root = event.getData();
        NBTTagCompound tag = root.getCompoundTag(CavesAndCliffsBackport.MOD_ID);
        tag.setIntArray(KEY, updates);
        root.setTag(CavesAndCliffsBackport.MOD_ID, tag);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote || PENDING.isEmpty()) {
            return;
        }
        Chunk chunk = event.getChunk();
        ChunkProviderServer provider = ((WorldServer) world).getChunkProvider();
        synchronized (CHECK) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Chunk c = dx == 0 && dz == 0 ? chunk : provider.getLoadedChunk(chunk.x + dx, chunk.z + dz);
                    if (c != null && PENDING.containsKey(c)) {
                        CHECK.add(c);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }
        run((WorldServer) event.world);
    }

    /** Runs the updates of every chunk looked at that is ready now (the world tick's end; the self-test). */
    public static void run(WorldServer world) {
        List<Chunk> ready = new ArrayList<Chunk>();
        synchronized (CHECK) {
            if (CHECK.isEmpty()) {
                return;
            }
            for (java.util.Iterator<Chunk> it = CHECK.iterator(); it.hasNext(); ) {
                Chunk c = it.next();
                if (c.getWorld() != world) {
                    continue;
                }
                it.remove();
                if (c.isLoaded() && ready(world, c)) {
                    ready.add(c);
                }
            }
        }
        long t = System.nanoTime();
        for (Chunk c : ready) {
            int[] updates = PENDING.remove(c);
            if (updates != null) {
                apply(world, c, updates);
            }
        }
        nanos += System.nanoTime() - t;
    }

    /**
     * Whether a column's population ran, where vanilla's flag does not say (a Cubic Chunks world populates cubes:
     * {@code cubic.CubicWorlds}); null answers leave it to the flag. Null without Cubic Chunks.
     */
    public static volatile java.util.function.BiFunction<WorldServer, Chunk, Boolean> populated;

    private static boolean populated(WorldServer world, Chunk c) {
        java.util.function.BiFunction<WorldServer, Chunk, Boolean> f = populated;
        Boolean answer = f == null ? null : f.apply(world, c);
        return answer != null ? answer : c.isTerrainPopulated();
    }

    /** The 3x3 around is loaded and the populations that write into the chunk ran. */
    private static boolean ready(WorldServer world, Chunk c) {
        ChunkProviderServer provider = world.getChunkProvider();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk n = provider.getLoadedChunk(c.x + dx, c.z + dz);
                if (n == null || dx <= 0 && dz <= 0 && !populated(world, n)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void apply(World world, Chunk chunk, int[] updates) {
        int bx = chunk.x << 4;
        int bz = chunk.z << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int requests = 0;
        for (int e : updates) {
            if (Marks.kind(e) >= Marks.REQUEST) {
                // Another backport's newer feature (ModernFeatures), run after 1.18's own updates.
                requests++;
                continue;
            }
            pos.setPos(bx + Marks.x(e), Marks.y(e), bz + Marks.z(e));
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof BlockLiquid) {
                BlockDynamicLiquid flowing = BlockLiquid.getFlowingBlock(state.getMaterial());
                BlockPos at = pos.toImmutable();
                if (block != flowing) {
                    // As 1.12 wakes a still liquid (BlockStaticLiquid.updateLiquid): the flowing block goes in first,
                    // and its tick is given that state. The tick takes the fluid it spreads from the state it gets
                    // (Fluidlogged API's FluidFlowHandler.updateDynamic: FluidState.of(state)), so a still state
                    // spread still blocks with no tick of their own, and the flow stopped one block out.
                    state = flowing.getDefaultState()
                            .withProperty(BlockLiquid.LEVEL, state.getValue(BlockLiquid.LEVEL));
                    world.setBlockState(at, state, 2);
                }
                // The tick at once (1.18's delay 0); a source spreads and goes back to still (placeStaticBlock).
                flowing.updateTick(world, at, state, world.rand);
                ticked++;
                continue;
            }
            if (Marks.kind(e) != Marks.POST || state.getMaterial() == net.minecraft.block.material.Material.AIR) {
                // A fluid tick where no fluid is left does nothing.
                continue;
            }
            BlockPos at = pos.toImmutable();
            if (block instanceof BlockBush) {
                if (!((BlockBush) block).canBlockStay(world, at, state)) {
                    world.setBlockState(at, Blocks.AIR.getDefaultState(), 2);
                }
            } else {
                state.neighborChanged(world, at, Blocks.AIR, at);
            }
        }
        if (requests > 0 && world instanceof WorldServer
                && ((WorldServer) world).getChunkProvider().chunkGenerator instanceof ModernGenerator) {
            ModernFeatures modern = ((ModernGenerator) ((WorldServer) world).getChunkProvider().chunkGenerator).modern();
            if (modern != null) {
                int[] list = new int[requests];
                int n = 0;
                for (int e : updates) {
                    if (Marks.kind(e) >= Marks.REQUEST) {
                        list[n++] = e;
                    }
                }
                modern.run(world, chunk, list);
                modernRuns++;
            }
        }
        applied++;
    }
}
