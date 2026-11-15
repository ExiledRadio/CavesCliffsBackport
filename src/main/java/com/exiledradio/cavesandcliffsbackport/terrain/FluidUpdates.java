package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.backportcore.waterlog.Waterlogging;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.terrain.core.FluidMarks;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Marks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
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
 * <p><b>Openings made by population.</b> 1.18 marks every aquifer fluid with the flag, exposed or not, and its
 * structures are placed during decoration, before the chunk ticks, so a structure that opens a marked fluid lets it
 * flow. The generator keeps only the marked fluids that are exposed when the chunk is finished, before 1.12's
 * population. So every block 1.12's population sets ({@code Chunk.populate}: the structures' pieces, other mods'
 * {@code IWorldGenerator}s, the family's structures placed through Backport Core) is seen here
 * ({@link #populationWrite}, called by {@code Chunk.setBlockState} while Forge's {@code Chunk.populating} is set): a
 * block that does not stop motion is an opening, and the fluid source above it or beside it, if the generator has not
 * placed its updates yet, the population has not set it, and the aquifer flagged its position, gets the same update
 * as a marked one. The flag is a function of the position ({@link FluidMarks#flagged}), so nothing else is kept.
 * Every population that writes next to a chunk's fluids runs before that chunk is ready (the 1.12 population of a
 * chunk writes 8 to 23 blocks from its corner, and a chunk waits for the four whose window covers it, which reach 8
 * blocks past each side), so the openings are all known when the updates run.
 *
 * <p>Per position, as 1.18:
 * <ul>
 *   <li>a still or flowing liquid gets its tick now: a still one is first made the flowing block, as 1.12's
 *       BlockStaticLiquid.updateLiquid does, then BlockDynamicLiquid.updateTick runs on the flowing state, which
 *       spreads a source down and to the sides as FlowingFluid.tick does (every block it places ticks in turn) and
 *       leaves the source still (no mixing check on the source itself, as 1.18's tick has none);</li>
 *   <li>water kept in a block (Backport Core's waterlogging, at every height: Fluidlogged API with Core's section
 *       store keeps y below 0 and above 255) gets the same tick, which Fluidlogged API runs on the kept water;</li>
 *   <li>post-processing of any other block: a plant that cannot stay goes without a drop (1.18's updateShape gives
 *       air), anything else gets a neighbor update (sand and gravel over a lake fall, glow lichen loses faces
 *       without support).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class FluidUpdates {

    private static final String KEY = "PendingUpdates";
    private static final String WRITTEN_KEY = "PendingWritten";
    /** Below this only the global lava fills, unflagged (NoiseBasedAquifer). */
    private static final int LAVA_LEVEL = -54;

    /** A chunk the generator made whose updates have not run yet. */
    private static final class Pending {
        int[] updates;
        /** Fluid sources (POST entries) a population opened, flagged by the aquifer. */
        IntSet opened;
        /** Positions the population set (entries of kind 0): no longer the terrain's fluid. */
        IntSet written;

        Pending(int[] updates) {
            this.updates = updates;
        }
    }

    /**
     * Chunks with updates still to run (weak: an unloaded chunk's entry goes with it, after its save wrote it). Every
     * chunk the generator makes is here until its updates run, with no updates of its own too: a population may open
     * its fluids. Its monitor guards the entries' sets.
     */
    private static final Map<Chunk, Pending> PENDING = Collections.synchronizedMap(new WeakHashMap<Chunk, Pending>());
    /** Chunks to look at after this tick: loaded, or next to one that loaded. */
    private static final Set<Chunk> CHECK = Collections.newSetFromMap(new WeakHashMap<Chunk, Boolean>());

    /** Counters (self-test, log). */
    public static long applied;
    public static long ticked;
    /** Fluid sources a population opened that got their update. */
    public static long openedTicked;
    /** Chunks whose newer features (ModernFeatures) ran. */
    public static long modernRuns;
    /** Time spent running updates (not looking for ready chunks), in nanoseconds. */
    public static long nanos;
    /** Time spent in {@link #populationWrite}, in nanoseconds, while {@link #timing} is set (self-test). */
    public static long writeNanos;
    public static long writes;
    public static boolean timing;
    /** The last chunk {@link #populationWrite} looked up and its entry (populations write one chunk at a time). */
    private static Chunk lastChunk;
    private static Pending lastPending;

    private FluidUpdates() {
    }

    /** A chunk the generator just made: its updates wait for its neighbors. */
    static void put(Chunk chunk, int[] updates) {
        synchronized (PENDING) {
            PENDING.put(chunk, new Pending(updates));
            lastChunk = null;
        }
    }

    /** For the self-test: the updates a loaded chunk still waits with (null for none). */
    public static int[] pending(Chunk chunk) {
        synchronized (PENDING) {
            Pending p = PENDING.get(chunk);
            if (p == null) {
                return null;
            }
            int[] all = withOpened(p);
            return all.length == 0 ? null : all;
        }
    }

    /** For the self-test: the fluid sources a population opened in a chunk still waiting (null for none). */
    public static int[] opened(Chunk chunk) {
        synchronized (PENDING) {
            Pending p = PENDING.get(chunk);
            return p == null || p.opened == null || p.opened.size() == 0 ? null : p.opened.toArray();
        }
    }

    /** For the self-test: the generator's own updates of a chunk still waiting, without the opened fluids. */
    public static int[] own(Chunk chunk) {
        synchronized (PENDING) {
            Pending p = PENDING.get(chunk);
            return p == null ? null : p.updates.clone();
        }
    }

    /** For the self-test: whether the chunk still waits (its updates have not run). */
    public static boolean waiting(Chunk chunk) {
        return PENDING.containsKey(chunk);
    }

    @SubscribeEvent
    public static void onDataLoad(ChunkDataEvent.Load event) {
        if (event.getWorld().isRemote) {
            return;
        }
        NBTTagCompound tag = event.getData().getCompoundTag(CavesAndCliffsBackport.MOD_ID);
        if (tag.hasKey(KEY)) {
            Pending p = new Pending(tag.getIntArray(KEY));
            if (tag.hasKey(WRITTEN_KEY)) {
                int[] written = tag.getIntArray(WRITTEN_KEY);
                if (written.length > 0) {
                    p.written = new IntSet();
                    for (int e : written) {
                        p.written.add(e);
                    }
                }
            }
            synchronized (PENDING) {
                PENDING.put(event.getChunk(), p);
                lastChunk = null;
            }
        }
    }

    @SubscribeEvent
    public static void onDataSave(ChunkDataEvent.Save event) {
        int[] updates;
        int[] written;
        synchronized (PENDING) {
            Pending p = PENDING.get(event.getChunk());
            if (p == null) {
                return;
            }
            updates = withOpened(p);
            written = p.written == null ? null : p.written.toArray();
        }
        NBTTagCompound root = event.getData();
        NBTTagCompound tag = root.getCompoundTag(CavesAndCliffsBackport.MOD_ID);
        tag.setIntArray(KEY, updates);
        if (written != null && written.length > 0) {
            tag.setIntArray(WRITTEN_KEY, written);
        }
        root.setTag(CavesAndCliffsBackport.MOD_ID, tag);
    }

    /** The chunk's updates, then the opened fluids not already among them. Holding PENDING's monitor. */
    private static int[] withOpened(Pending p) {
        if (p.opened == null || p.opened.size() == 0) {
            return p.updates;
        }
        IntSet have = new IntSet();
        for (int e : p.updates) {
            if (Marks.kind(e) < Marks.REQUEST) {
                have.add(e & 0xFFFFF);
            }
        }
        int[] extra = p.opened.toArray();
        int[] all = java.util.Arrays.copyOf(p.updates, p.updates.length + extra.length);
        int n = p.updates.length;
        for (int e : extra) {
            if (!have.contains(e & 0xFFFFF)) {
                all[n++] = e;
            }
        }
        return n == all.length ? all : java.util.Arrays.copyOf(all, n);
    }

    // ---------------------------------------------------------------------------------------------
    // Population

    /**
     * {@code Chunk.setBlockState}, at its start (patched in by {@code height.asm.ChunkPatch}): {@code populating} is
     * Forge's {@code Chunk.populating}, the chunk being populated, null outside {@code Chunk.populate}. Records the
     * block as set by the population, and, if it does not stop motion, the flagged fluid sources it opens.
     */
    public static void populationWrite(ChunkPos populating, Chunk chunk, BlockPos pos, IBlockState state) {
        if (populating == null || PENDING.isEmpty()) {
            return;
        }
        int y = pos.getY();
        if (y < LAVA_LEVEL - 1) {
            return;
        }
        World world = chunk.getWorld();
        if (world.isRemote) {
            return;
        }
        long t = timing ? System.nanoTime() : 0L;
        synchronized (PENDING) {
            Pending p = chunk == lastChunk ? lastPending : PENDING.get(chunk);
            lastChunk = chunk;
            lastPending = p;
            if (p != null && y >= LAVA_LEVEL) {
                int key = Marks.pack(pos.getX(), y, pos.getZ(), 0);
                if (p.written == null) {
                    p.written = new IntSet();
                }
                p.written.add(key);
                if (p.opened != null) {
                    p.opened.remove(key | Marks.POST << 20);
                }
            }
            // A liquid the population places is not taken as an opening: water next to the aquifer's water changes
            // nothing (a monument's water beside the ocean's), and the one case that would (aquifer lava over placed
            // water) is left as 1.12 places it.
            if (!state.getMaterial().blocksMovement() && !state.getMaterial().isLiquid() && world instanceof WorldServer) {
                ChunkProviderServer provider = ((WorldServer) world).getChunkProvider();
                if (provider.chunkGenerator instanceof ModernGenerator) {
                    FluidMarks marks = ((ModernGenerator) provider.chunkGenerator).fluidMarks();
                    int x = pos.getX();
                    int z = pos.getZ();
                    // The fluid flows down and to the sides: the source above or beside the opening.
                    opening(world, provider, marks, chunk, x, y + 1, z);
                    opening(world, provider, marks, chunk, x - 1, y, z);
                    opening(world, provider, marks, chunk, x + 1, y, z);
                    opening(world, provider, marks, chunk, x, y, z - 1);
                    opening(world, provider, marks, chunk, x, y, z + 1);
                }
            }
        }
        writes++;
        if (timing) {
            writeNanos += System.nanoTime() - t;
        }
    }

    /** A fluid next to an opening: kept if it is a flagged source of a chunk still waiting that the population left. */
    private static void opening(World world, ChunkProviderServer provider, FluidMarks marks, Chunk from, int x, int y, int z) {
        if (y < LAVA_LEVEL) {
            return;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        // Never loads a chunk: a population only writes next to loaded ones.
        Chunk c = cx == from.x && cz == from.z ? from : provider.getLoadedChunk(cx, cz);
        if (c == null) {
            return;
        }
        Pending p = PENDING.get(c);
        if (p == null) {
            return;
        }
        int key = Marks.pack(x, y, z, 0);
        if (p.written != null && p.written.contains(key)) {
            return;
        }
        BlockPos at = new BlockPos(x, y, z);
        IBlockState state = c.getBlockState(at);
        if (!source(world, at, state) || !marks.flagged(x, y, z)) {
            return;
        }
        if (p.opened == null) {
            p.opened = new IntSet();
        }
        p.opened.add(key | Marks.POST << 20);
    }

    /** A water or lava source, or water kept in a block (only with waterlogging installed). */
    private static boolean source(World world, BlockPos at, IBlockState state) {
        if (state.getBlock() instanceof BlockLiquid) {
            return state.getValue(BlockLiquid.LEVEL) == 0;
        }
        return keptWater(world, at, state);
    }

    private static boolean keptWater(World world, BlockPos at, IBlockState state) {
        return state.getMaterial() != Material.AIR && !state.isOpaqueCube() && Waterlogging.isAvailable()
                && Waterlogging.hasWaterSource(world, at);
    }

    // ---------------------------------------------------------------------------------------------
    // Running the updates

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
            int[] updates;
            int own;
            synchronized (PENDING) {
                Pending p = PENDING.remove(c);
                lastChunk = null;
                if (p == null) {
                    continue;
                }
                own = p.updates.length;
                updates = withOpened(p);
            }
            if (updates.length > 0) {
                apply(world, c, updates, own);
            }
        }
        nanos += System.nanoTime() - t;
    }

    /** The 3x3 around is loaded and the populations that write into the chunk ran. */
    private static boolean ready(WorldServer world, Chunk c) {
        ChunkProviderServer provider = world.getChunkProvider();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk n = provider.getLoadedChunk(c.x + dx, c.z + dz);
                if (n == null || dx <= 0 && dz <= 0 && !n.isTerrainPopulated()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The updates; from index {@code own} on, the fluid sources the population opened. */
    private static void apply(World world, Chunk chunk, int[] updates, int own) {
        int bx = chunk.x << 4;
        int bz = chunk.z << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int requests = 0;
        for (int k = 0; k < updates.length; k++) {
            int e = updates[k];
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
                if (k >= own) {
                    openedTicked++;
                }
                continue;
            }
            if (state.getMaterial() == Material.AIR) {
                // A fluid tick where no fluid is left does nothing.
                continue;
            }
            BlockPos at = pos.toImmutable();
            if (keptWater(world, at, state)) {
                // postProcessGeneration ticks the block's fluid state, then gives the block its shape. Fluidlogged
                // API runs the flowing water's tick on the water kept at the position (FluidState.of(state)).
                Blocks.FLOWING_WATER.updateTick(world, at, Blocks.FLOWING_WATER.getDefaultState(), world.rand);
                ticked++;
                if (k >= own) {
                    openedTicked++;
                }
                state = world.getBlockState(at);
                block = state.getBlock();
            }
            if (Marks.kind(e) != Marks.POST || state.getMaterial() == Material.AIR) {
                continue;
            }
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

    /** A small set of ints (open addressing; 0 is a valid key, kept apart). */
    static final class IntSet {
        private int[] keys = new int[16];
        private boolean[] used = new boolean[16];
        private int size;

        int size() {
            return size;
        }

        private int slot(int key) {
            int h = key * 0x9E3779B9;
            return (h ^ h >>> 16) & (keys.length - 1);
        }

        boolean contains(int key) {
            for (int i = slot(key); used[i]; i = (i + 1) & (keys.length - 1)) {
                if (keys[i] == key) {
                    return true;
                }
            }
            return false;
        }

        void add(int key) {
            int i = slot(key);
            for (; used[i]; i = (i + 1) & (keys.length - 1)) {
                if (keys[i] == key) {
                    return;
                }
            }
            keys[i] = key;
            used[i] = true;
            if (++size * 2 > keys.length) {
                int[] old = keys;
                boolean[] oldUsed = used;
                keys = new int[old.length * 2];
                used = new boolean[old.length * 2];
                size = 0;
                for (int j = 0; j < old.length; j++) {
                    if (oldUsed[j]) {
                        add(old[j]);
                    }
                }
            }
        }

        void remove(int key) {
            int mask = keys.length - 1;
            int i = slot(key);
            for (; used[i]; i = (i + 1) & mask) {
                if (keys[i] == key) {
                    break;
                }
            }
            if (!used[i]) {
                return;
            }
            used[i] = false;
            size--;
            // Re-place the rest of the run so lookups that probed past this slot still find their keys.
            for (int j = (i + 1) & mask; used[j]; j = (j + 1) & mask) {
                int k = keys[j];
                used[j] = false;
                size--;
                add(k);
            }
        }

        int[] toArray() {
            int[] out = new int[size];
            int n = 0;
            for (int i = 0; i < keys.length; i++) {
                if (used[i]) {
                    out[n++] = keys[i];
                }
            }
            return out;
        }
    }
}
