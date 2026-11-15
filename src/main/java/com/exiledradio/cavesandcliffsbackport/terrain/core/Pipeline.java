package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The whole 1.18 overworld for a chunk generator that asks for one finished chunk at a time (1.12's), with no
 * Minecraft classes: fill, biomes, surface and carvers on a pool of worker threads, ahead of need; decoration on the
 * asking thread, in the order chunks are asked for.
 *
 * <p>Decoration of a chunk writes into the 3x3 chunks around it and reads the 5x5 ({@link Region}). So a chunk is
 * finished (no later decoration can change it) once the 3x3 around it is decorated, which needs the 7x7 around it
 * filled. {@link #finish} does exactly that and hands back the chunk; each chunk is filled once and decorated once.
 * Vanilla 1.18 decorates in whatever order its chunk system reaches chunks, so features that cross a chunk border
 * depend on that order there too; here it is the order 1.12 asks for chunks.
 *
 * <p>Held chunks ({@link Proto}) are dropped once nothing can read them again (finished, and every chunk within two
 * decorated: "settled"); a settled chunk is never decorated again. Beyond a memory cap the least recently used ones
 * go first: with a {@link Store} a dropped chunk that decoration has written into is kept there and comes back as it
 * was, so the cap changes nothing; without one it is filled again when asked for (what decoration had written into
 * it is lost). {@link #saveHeld} writes every such chunk still held to the store, so a world stopped and started
 * again decorates the chunks beyond its explored area exactly as one that never stopped.
 *
 * <p>A crash between two world saves: with a {@link Log}, every {@link #finish} that decorated or finished a chunk
 * hands a record of it (each decoration's writes and marks, in order) to the journal before handing the chunk back,
 * and the game writes no chunk before the journal has every record so far ({@link Journal#await}), so the game can
 * never have a chunk on disk whose neighbors' share of its features is not on disk too. Each held chunk
 * keeps the number of the last record that changed it ({@code lsn}, saved with it); {@link #replay} applies, after a
 * crash, every record newer than a chunk's to the chunk as the store has it (or as the noise gives it), which puts
 * back every held chunk as it was at the last record. The store forgets a chunk that settled only once the game has
 * it on disk ({@link Store#released}).
 *
 * <p>Structure terrain adaptation: a {@link BeardSource} gives each chunk's beardifier (the structure pieces near
 * it) when the chunk is first asked for, on the asking thread, before its fill is queued.
 *
 * <p>{@link #finish}, {@link #prefetch} and {@link #saveHeld} are for one thread (1.12's server thread); the pool is
 * internal.
 */
public final class Pipeline {
    public static final int MIN_Y = OverworldNoise.MIN_Y;
    public static final int HEIGHT = OverworldNoise.HEIGHT;
    public static final int VOLUME = 16 * 16 * HEIGHT;
    /** BiomeSource.fillChunk's layout: 4 x 4 quart columns of 96 quarts. */
    public static final int QUARTS = 16 * BiomeSource.QUARTS_Y;

    /** One chunk on its way: blocks (ChunkFiller's layout), the worldgen and live heightmaps, the column biomes. */
    public static final class Proto {
        public final int cx;
        public final int cz;
        public final char[] blocks;
        /** Region.worldgenHeights: OCEAN_FLOOR_WG, then WORLD_SURFACE_WG (512). */
        public final int[] worldgen;
        /** Region.primeHeightmaps, kept live by decoration (Region.HEIGHTMAPS * 256). */
        public final int[] heights;
        /** The biome id at each column's top block after carving (x * 16 + z). */
        public final byte[] surfaceBiomes;
        /** The chunk's stored (3D, quart) biomes, BiomeSource.fillChunk's layout. */
        public final byte[] quarts;
        /** Ticks and post-processing marks decoration left in this chunk ({@link Marks}). */
        final Marks marks = new Marks();
        /**
         * Once finished: every position 1.18.2 updates when the chunk starts ticking ({@link Marks} entries), the
         * decoration's marks and the aquifer fluids' ({@link FluidMarks}).
         */
        public int[] updates = new int[0];
        boolean decorated;
        boolean finished;
        /** Bumped by every decoration that writes into this chunk; {@code saved} is its value at the last store write. */
        int changes;
        int saved;
        /** The number of the last journal record that changed this chunk ({@link Log}); 0 for none. */
        long lsn;
        /** The structure pieces' terrain adaptation for the fill, or null. */
        Beardifier beard;
        final Task task;

        Proto(int cx, int cz, Pipeline owner) {
            this.cx = cx;
            this.cz = cz;
            this.blocks = new char[VOLUME];
            this.worldgen = new int[512];
            this.heights = new int[Region.HEIGHTMAPS * 256];
            this.surfaceBiomes = new byte[256];
            this.quarts = new byte[QUARTS];
            this.task = new Task(this, owner);
        }

        /** The code at chunk-local x, z and block y. */
        public int get(int x, int y, int z) {
            return blocks[(x * 16 + z) * HEIGHT + y - MIN_Y];
        }
    }

    /** The structure pieces near a chunk, as a beardifier for its fill. */
    public interface BeardSource {
        /** Beardifier.forChunk for chunk cx, cz, or null for none. Called on the asking thread. */
        Beardifier forChunk(int cx, int cz);
    }

    /**
     * Where held chunks go when they are dropped or the world is saved, so that nothing decoration wrote is lost.
     * Called on the asking thread; {@link #save} may hand the bytes to a writer thread, as long as a later
     * {@link #load} of the same chunk sees them.
     */
    public interface Store {
        /** What {@link #save} kept for this chunk ({@link Pipeline#decode} reads it), or null. */
        byte[] load(int cx, int cz);

        void save(int cx, int cz, byte[] data);

        /**
         * The chunk settled and is dropped: nothing reads it again once the game has it on disk. hasEntry: this store
         * holds an entry for it (to forget once the game has the chunk); unsaved: decoration changed it since that
         * entry (or it has none), so until the game has the chunk on disk only the journal records since the last
         * world save can give it back.
         */
        void released(int cx, int cz, boolean hasEntry, boolean unsaved);

        /**
         * Whether the world already holds this chunk as finished (a chunk the generator made in an earlier run): it
         * was decorated then, and is never decorated again.
         */
        boolean finished(int cx, int cz);
    }

    /**
     * Where the journal records go ({@link Journal}). Called on the asking thread, inside {@link #finish}, before the
     * chunk is handed back; the record may be written later, as long as nothing that depends on it (the game's
     * write of the chunk, a store entry carrying its number) reaches the disk first.
     */
    public interface Log {
        /** The record's first size ints; they may be changed once this returns. */
        void append(int[] record, int size);
    }

    /** The fill of one Proto, queued on the pool or run by the asking thread if no worker has started it. */
    static final class Task implements Runnable, Comparable<Task> {
        static final int QUEUED = 0;
        static final int RUNNING = 1;
        static final int DONE = 2;
        final Proto proto;
        final Pipeline owner;
        final AtomicInteger state = new AtomicInteger(QUEUED);
        volatile long priority;
        volatile Throwable failure;

        Task(Proto proto, Pipeline owner) {
            this.proto = proto;
            this.owner = owner;
        }

        @Override
        public void run() {
            if (state.compareAndSet(QUEUED, RUNNING)) {
                execute(owner.kits.get());
            }
        }

        void execute(Kit kit) {
            try {
                kit.produce(proto);
            } catch (Throwable t) {
                failure = t;
            } finally {
                synchronized (this) {
                    state.set(DONE);
                    notifyAll();
                }
            }
        }

        @Override
        public int compareTo(Task o) {
            return Long.compare(priority, o.priority);
        }
    }

    /** One thread's terrain objects (every core class but OverworldNoise and Climate keeps per-thread caches). */
    final class Kit {
        final ColumnCache columns = new ColumnCache(noise);
        final BiomeSource biomes = new BiomeSource(noise, columns, climate);
        final ChunkFiller filler = new ChunkFiller(noise, columns);
        final SurfaceBuilder surface = new SurfaceBuilder(noise, columns, biomes);
        final Carvers carvers = new Carvers(noise, columns, surface);
        final BiomeZoom zoom = new BiomeZoom(noise, biomes);

        void produce(Proto p) {
            long t = System.nanoTime();
            filler.fill(p.cx, p.cz, p.blocks, p.beard);
            p.beard = null;
            // The surface's fluids are marked here; the noise's and carvers' aquifer fluids when the chunk is finished.
            surface.marks = p.marks;
            try {
                surface.build(p.cx, p.cz, p.blocks);
                carvers.carve(p.cx, p.cz, p.blocks);
            } finally {
                surface.marks = null;
            }
            Region.worldgenHeights(p.blocks, p.worldgen);
            Region.primeHeightmaps(p.blocks, p.heights);
            int bx = p.cx << 4;
            int bz = p.cz << 4;
            for (int c = 0; c < 256; c++) {
                int top = p.worldgen[256 + c] - 1;
                p.surfaceBiomes[c] = (byte) zoom.biome(bx + (c >> 4), Math.max(top, MIN_Y), bz + (c & 15));
            }
            biomes.fillChunk(p.cx, p.cz, p.quarts);
            fillNanos.addAndGet(System.nanoTime() - t);
            filled.incrementAndGet();
        }
    }

    final OverworldNoise noise;
    final Climate climate;
    private final ThreadLocal<Kit> kits;
    private final ThreadPoolExecutor pool;
    private final Kit own;
    private final Decorator decorator;
    private final Region region = new Region();
    private final char[][] b25 = new char[Region.CHUNKS][];
    private final int[][] w25 = new int[Region.CHUNKS][];
    private final int[][] h25 = new int[Region.CHUNKS][];
    private final Marks[] m25 = new Marks[Region.CHUNKS];
    private final FluidMarks fluidMarks;
    /** Held chunks, least recently used first. */
    private final LinkedHashMap<Long, Proto> held = new LinkedHashMap<>(1024, 0.75F, true);
    private final int cap;
    /** Chunks dropped once settled: finished and decorated, as is everything within two of them. */
    private final LongSet gone = new LongSet();
    private long sequence;
    private BeardSource beards;
    private Store store;
    private Log log;
    /** The number of the last journal record written. */
    private long lsn;
    /** The record being built by {@link #finish}: ints, see {@link #journal}; the header first. */
    private int[] record = new int[4096];
    private int recordSize;
    private int recordDecorations;
    /** The chunks the record being built changes. */
    private final List<Proto> touched = new ArrayList<>();

    /** Counters for the benchmark and the log. */
    public final AtomicLong fillNanos = new AtomicLong();
    public final AtomicLong filled = new AtomicLong();
    public long decorateNanos;
    public long decorated;
    public long waitNanos;
    public long stolen;
    public long beardNanos;
    public long restored;
    public long stored;
    /** Time spent handing journal records over ({@link Log}) on the asking thread, and how many. */
    public long journalNanos;
    public long journaled;

    /**
     * @param seed the world seed
     * @param threads worker threads (0: fill on the asking thread only)
     * @param cap how many chunks to hold at most (each about 200 KB)
     */
    public Pipeline(long seed, int threads, int cap) {
        this(new OverworldNoise(seed), new Climate(), threads, cap);
    }

    /** Which chunks get their surface trees ({@link Decorator.TreeGate}); asked on the thread that calls {@link #finish}. */
    /** The feature rules' version ({@link Decorator#rules}); before the first chunk is asked for. */
    public void rules(int version) {
        decorator.rules(version);
    }

    public void treeGate(Decorator.TreeGate gate) {
        decorator.treeGate(gate);
    }

    /** A newer version's tree selectors ({@link Decorator#modernTrees}); before the first chunk is asked for. */
    public List<String> modernTrees(Map<String, Decorator.ModernTrees> selectors) {
        return decorator.modernTrees(selectors);
    }

    /** Over a world's noise and climate, shared with its biome provider (both are read only). */
    public Pipeline(OverworldNoise noise, Climate climate, int threads, int cap) {
        this.noise = noise;
        this.climate = climate;
        this.kits = new ThreadLocal<Kit>() {
            @Override
            protected Kit initialValue() {
                return new Kit();
            }
        };
        this.own = new Kit();
        this.decorator = new Decorator(noise, new BiomeSource(noise, new ColumnCache(noise), climate));
        this.fluidMarks = new FluidMarks(noise);
        this.cap = Math.max(cap, 7 * 7 + 64);
        if (threads > 0) {
            final AtomicInteger n = new AtomicInteger();
            ThreadFactory factory = r -> {
                Thread t = new Thread(r, "Caves & Cliffs terrain " + n.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            };
            pool = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                new PriorityBlockingQueue<Runnable>(), factory);
            pool.allowCoreThreadTimeOut(true);
        } else {
            pool = null;
        }
    }

    public OverworldNoise noise() {
        return noise;
    }

    public Climate climate() {
        return climate;
    }

    /** The structure pieces' terrain adaptation for every chunk asked for from now on. */
    public synchronized void setBeards(BeardSource beards) {
        this.beards = beards;
    }

    /** Where dropped and saved chunks go (see the class comment). */
    public synchronized void setStore(Store store) {
        this.store = store;
    }

    /**
     * Journal every finish from now on ({@link Log}), numbering records after {@code last} (the highest record
     * number a held chunk in the store or a journal record may carry).
     */
    public synchronized void setLog(Log log, long last) {
        this.log = log;
        this.lsn = Math.max(this.lsn, last);
    }

    /** The number of the last journal record written or replayed. */
    public synchronized long lsn() {
        return lsn;
    }

    /** Stops the workers; queued fills are dropped. Call {@link #saveHeld} first to keep what decoration wrote. */
    public void shutdown() {
        if (pool != null) {
            pool.shutdownNow();
        }
        synchronized (this) {
            held.clear();
        }
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    /** The held chunk, queuing its fill (at this priority: lower first) if it is new. */
    private Proto proto(int cx, int cz, long priority) {
        long k = key(cx, cz);
        Proto p = held.get(k);
        if (p == null) {
            gone.remove(k);
            if (store != null) {
                byte[] data = store.load(cx, cz);
                if (data != null) {
                    p = decode(data);
                    if (p != null && p.cx == cx && p.cz == cz) {
                        restored++;
                        held.put(k, p);
                        return p;
                    }
                }
            }
            p = new Proto(cx, cz, this);
            if (beards != null) {
                long t = System.nanoTime();
                p.beard = beards.forChunk(cx, cz);
                beardNanos += System.nanoTime() - t;
            }
            held.put(k, p);
            p.task.priority = priority;
            if (pool != null) {
                pool.execute(p.task);
            }
        }
        return p;
    }

    /** Waits for a chunk's fill, running it here if no worker has started it. */
    private Proto filled(Proto p) {
        Task t = p.task;
        if (t.state.get() != Task.DONE) {
            if (t.state.compareAndSet(Task.QUEUED, Task.RUNNING)) {
                stolen++;
                t.execute(own);
                if (pool != null) {
                    pool.remove(t);
                }
            } else {
                long w = System.nanoTime();
                synchronized (t) {
                    while (t.state.get() != Task.DONE) {
                        try {
                            t.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("interrupted while terrain was generated", e);
                        }
                    }
                }
                waitNanos += System.nanoTime() - w;
            }
        }
        if (t.failure != null) {
            held.remove(key(p.cx, p.cz));
            throw new IllegalStateException("terrain for chunk " + p.cx + "," + p.cz + " failed", t.failure);
        }
        return p;
    }

    /**
     * Queues the fills of the chunks within radius of cx, cz that are not held yet, nearest first, behind every
     * fill already asked for by {@link #finish}.
     */
    public synchronized void prefetch(int cx, int cz, int radius) {
        if (pool == null) {
            return;
        }
        long base = (1L << 62) - ((++sequence) << 16);
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r && !settledAway(cx + dx, cz + dz)) {
                        proto(cx + dx, cz + dz, base + r);
                    }
                }
            }
        }
        trim();
    }

    /**
     * The finished chunk cx, cz: the 3x3 around it decorated. The returned blocks stay valid until the next call.
     */
    public synchronized Proto finish(int cx, int cz) {
        long base = (++sequence) << 16;
        recordSize = Journal.HEADER;
        recordDecorations = 0;
        touched.clear();
        // Ask for the whole 7x7 first, nearest first, so the pool works on it while this thread decorates.
        for (int r = 0; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                        if (r > 0 && settledAway(cx + dx, cz + dz)) {
                            continue;
                        }
                        Proto p = proto(cx + dx, cz + dz, base + r);
                        if (p.task.priority > base + r && p.task.state.get() == Task.QUEUED && pool != null) {
                            // A prefetched fill still queued: move it up.
                            if (pool.remove(p.task)) {
                                p.task.priority = base + r;
                                pool.execute(p.task);
                            }
                        }
                    }
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                decorate(cx + dx, cz + dz, base);
            }
        }
        Proto c = held.get(key(cx, cz));
        if (c == null) {
            // Asked again for a chunk that settled (a world that lost the chunk it had): fill it as it would be.
            c = filled(proto(cx, cz, base));
        }
        boolean newly = !c.finished;
        if (newly) {
            c.finished = true;
            c.changes++;
            touched.add(c);
        }
        if (log != null && (newly || recordDecorations > 0)) {
            journal(c, newly);
        }
        c.updates = updates(c);
        release(cx, cz);
        trim();
        return c;
    }

    /** The finished chunk's updates: decoration's marks, then the aquifer fluids' that can move. */
    private int[] updates(Proto c) {
        return fluidMarks.finish(c.blocks, blocksOf(c.cx, c.cz - 1), blocksOf(c.cx, c.cz + 1), blocksOf(c.cx - 1, c.cz),
                blocksOf(c.cx + 1, c.cz), c.cx, c.cz, c.marks);
    }

    private char[] blocksOf(int cx, int cz) {
        Proto p = held.get(key(cx, cz));
        return p != null && p.task.state.get() == Task.DONE && p.task.failure == null ? p.blocks : null;
    }

    /**
     * A chunk that is not held and settled already (dropped this run, or finished by an earlier run): it is never
     * decorated or read again, so it need not be filled.
     */
    private boolean settledAway(int cx, int cz) {
        long k = key(cx, cz);
        if (held.containsKey(k)) {
            return false;
        }
        if (gone.contains(k)) {
            return true;
        }
        if (store != null && store.finished(cx, cz)) {
            gone.add(k);
            return true;
        }
        return false;
    }

    private void decorate(int cx, int cz, long base) {
        if (settledAway(cx, cz)) {
            return;
        }
        Proto center = filled(proto(cx, cz, base));
        if (center.decorated) {
            return;
        }
        int r = Region.READ;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                Proto p = filled(proto(cx + dx, cz + dz, base + 3));
                int i = (dx + r) * Region.SIDE + dz + r;
                b25[i] = p.blocks;
                w25[i] = p.worldgen;
                h25[i] = p.heights;
                m25[i] = p.marks;
                if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1) {
                    p.changes++;
                }
            }
        }
        long t = System.nanoTime();
        region.set(cx, cz, b25, w25, h25, m25);
        region.recording = log != null;
        region.opCount = 0;
        decorator.decorate(region);
        decorateNanos += System.nanoTime() - t;
        decorated++;
        center.decorated = true;
        if (log != null) {
            region.recording = false;
            int n = region.opCount;
            ensureRecord(3 + n);
            record[recordSize++] = cx;
            record[recordSize++] = cz;
            record[recordSize++] = n;
            System.arraycopy(region.ops, 0, record, recordSize, n);
            recordSize += n;
            recordDecorations++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    touched.add(held.get(key(cx + dx, cz + dz)));
                }
            }
        }
    }

    private void ensureRecord(int more) {
        if (recordSize + more > record.length) {
            record = java.util.Arrays.copyOf(record, Math.max(recordSize + more, record.length * 2));
        }
    }

    /**
     * Hands the record of this finish to the journal ({@link Log}; {@link Journal} packs and writes it): its number
     * (two ints), the chunk finished, 1 if this finish finished it, glow lichen's direction order after it (six 3-bit
     * directions), the number of decorations, then each decoration's center x, z, op count and ops
     * ({@link Region#ops}). The chunks it changed carry its number.
     */
    private void journal(Proto c, boolean newly) {
        long t = System.nanoTime();
        long n = ++lsn;
        int[] order = lichenOrder();
        int lichen = 0;
        for (int i = 0; i < 6; i++) {
            lichen |= order[i] << (3 * i);
        }
        record[0] = (int) (n >>> 32);
        record[1] = (int) n;
        record[2] = c.cx;
        record[3] = c.cz;
        record[4] = newly ? 1 : 0;
        record[5] = lichen;
        record[6] = recordDecorations;
        log.append(record, recordSize);
        for (Proto p : touched) {
            p.lsn = n;
        }
        touched.clear();
        journalNanos += System.nanoTime() - t;
        journaled++;
    }

    /**
     * After a crash: puts back the held chunks as the journal's records (oldest first, as {@link Journal#read}
     * gives them) left them. Each record is applied to every chunk whose {@code lsn} (the store's entry, or 0 for a
     * chunk the noise gives) is older, as {@link #finish} made it: its writes and marks in order through the same
     * code, then the decorated and finished flags. Call before the first {@link #finish}; the chunks stay held (and
     * changed) for the next {@link #saveHeld}.
     *
     * @return how many records changed at least one chunk
     */
    public synchronized int replay(List<byte[]> records) {
        int applied = 0;
        int ahead = 0;
        java.util.ArrayDeque<int[]> unpacked = new java.util.ArrayDeque<>();
        for (int r = 0; r < records.size(); r++) {
            // Queue the fills of the next records' chunks, so the workers make them while this thread applies.
            for (; ahead < records.size() && ahead < r + 16; ahead++) {
                int[] q = Journal.unpack(records.get(ahead));
                unpacked.add(q);
                int at = 7;
                for (int d = q[6]; d > 0; d--) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            proto(q[at] + dx, q[at + 1] + dz, (++sequence) << 16);
                        }
                    }
                    at += 3 + q[at + 2];
                }
            }
            int[] q = unpacked.poll();
            long n = (long) q[0] << 32 | (q[1] & 0xFFFFFFFFL);
            long base = (++sequence) << 16;
            touched.clear();
            int at = 7;
            for (int d = q[6]; d > 0; d--) {
                int cx = q[at];
                int cz = q[at + 1];
                int ops = q[at + 2];
                at += 3;
                Proto[] chunks = new Proto[Region.CHUNKS];
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        chunks[(dx + Region.READ) * Region.SIDE + dz + Region.READ] = replayed(cx + dx, cz + dz, n, base);
                    }
                }
                for (int i = 0; i < ops; i += 2) {
                    int op = q[at + i];
                    int k = Region.opChunk(op);
                    Proto p = chunks[k];
                    if (p == null) {
                        // Only the 3x3 is written; should a write land further out, it goes where it went.
                        p = chunks[k] = replayed(cx + k / Region.SIDE - Region.READ, cz + k % Region.SIDE - Region.READ,
                                n, base);
                    }
                    if (p.lsn < n) {
                        Region.apply(op, q[at + i + 1], p.blocks, p.heights, p.marks);
                    }
                }
                at += ops;
                Proto center = chunks[Region.READ * Region.SIDE + Region.READ];
                if (center.lsn < n) {
                    center.decorated = true;
                }
            }
            if (q[4] != 0) {
                Proto c = replayed(q[2], q[3], n, base);
                if (c.lsn < n) {
                    c.finished = true;
                }
            }
            if (!touched.isEmpty()) {
                applied++;
            }
            for (Proto p : touched) {
                p.lsn = n;
            }
            touched.clear();
            lsn = Math.max(lsn, n);
            int lichen = q[5];
            int[] order = new int[6];
            for (int i = 0; i < 6; i++) {
                order[i] = lichen >> (3 * i) & 7;
            }
            setLichenOrder(order);
            trim();
        }
        return applied;
    }

    /** A chunk record n changes, filled; marked changed (and touched) if the record is newer than it. */
    private Proto replayed(int cx, int cz, long n, long base) {
        Proto p = filled(proto(cx, cz, base));
        if (p.lsn < n && !touched.contains(p)) {
            p.changes++;
            touched.add(p);
        }
        return p;
    }

    /**
     * Drops the held chunks that settled and that the game has on disk ({@code durable}): after a {@link #replay},
     * the chunks the records brought back that nothing reads again.
     *
     * @return how many were dropped
     */
    public synchronized int dropSettled(java.util.function.BiPredicate<Integer, Integer> durable) {
        List<Proto> drop = new ArrayList<>();
        // A copy: settled() reads the held map, which is in access order.
        for (Proto p : new ArrayList<>(held.values())) {
            if (p.finished && p.task.state.get() == Task.DONE && settled(p.cx, p.cz) && durable.test(p.cx, p.cz)) {
                drop.add(p);
            }
        }
        for (Proto p : drop) {
            held.remove(key(p.cx, p.cz));
            gone.add(key(p.cx, p.cz));
            if (store != null) {
                store.released(p.cx, p.cz, p.saved != 0, false);
            }
        }
        return drop.size();
    }

    /** Drops the held chunks around cx, cz that nothing can read again. */
    private void release(int cx, int cz) {
        int r = Region.READ + 1;
        List<Long> drop = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                Proto p = held.get(key(cx + dx, cz + dz));
                if (p != null && p.finished && settled(p.cx, p.cz)) {
                    drop.add(key(p.cx, p.cz));
                }
            }
        }
        for (Long k : drop) {
            Proto p = held.remove(k);
            gone.add(k);
            if (store != null) {
                store.released(p.cx, p.cz, p.saved != 0, p.changes != p.saved);
            }
        }
    }

    /** Whether every chunk within decoration's read distance of cx, cz is decorated (so none reads it again). */
    private boolean settled(int cx, int cz) {
        int r = Region.READ;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                long k = key(cx + dx, cz + dz);
                Proto p = held.get(k);
                if (p == null ? !settledAway(cx + dx, cz + dz) : !p.decorated) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Above the cap, drops the least recently used chunks that no fill is running for (into the store, if any). */
    private void trim() {
        if (held.size() <= cap) {
            return;
        }
        Iterator<Map.Entry<Long, Proto>> it = held.entrySet().iterator();
        while (held.size() > cap * 7 / 8 && it.hasNext()) {
            Proto p = it.next().getValue();
            int s = p.task.state.get();
            if (s == Task.RUNNING) {
                continue;
            }
            if (s == Task.QUEUED && pool != null) {
                pool.remove(p.task);
            }
            if (s == Task.DONE && store != null && p.changes != p.saved && p.task.failure == null) {
                store.save(p.cx, p.cz, encode(p));
                stored++;
            }
            it.remove();
        }
    }

    /**
     * Writes every held chunk that decoration wrote into since it was last written to the store (chunks only
     * filled come back the same from the noise). Call when the world is saved.
     *
     * @return how many chunks were written
     */
    public synchronized int saveHeld() {
        if (store == null) {
            return 0;
        }
        int n = 0;
        for (Proto p : held.values()) {
            if (p.changes != p.saved && p.task.state.get() == Task.DONE && p.task.failure == null) {
                store.save(p.cx, p.cz, encode(p));
                p.saved = p.changes;
                n++;
            }
        }
        stored += n;
        return n;
    }

    /** How many chunks are held. */
    public synchronized int held() {
        return held.size();
    }

    // ---------------------------------------------------------------------------------------------
    // Saved chunks

    /**
     * 3: the number of the last journal record that changed the chunk (a long) follows the flags; 2: the decoration
     * marks follow (a count, then the entries); 1: without them. All three are read.
     */
    private static final int FORMAT = 3;
    private static final int FIXED = 4 * 4 + 1 + VOLUME * 2 + (512 + Region.HEIGHTMAPS * 256) * 4 + 256 + QUARTS;

    /** A held chunk as bytes, for a {@link Store}. */
    public static byte[] encode(Proto p) {
        ByteBuffer b = ByteBuffer.allocate(FIXED + 8 + 4 + p.marks.size() * 4);
        b.putInt(FORMAT).putInt(p.cx).putInt(p.cz).putInt(Math.max(1, p.changes));
        b.put((byte) ((p.decorated ? 1 : 0) | (p.finished ? 2 : 0)));
        b.putLong(p.lsn);
        b.asCharBuffer().put(p.blocks);
        b.position(b.position() + VOLUME * 2);
        b.asIntBuffer().put(p.worldgen).put(p.heights);
        b.position(b.position() + (512 + Region.HEIGHTMAPS * 256) * 4);
        b.put(p.surfaceBiomes).put(p.quarts);
        b.putInt(p.marks.size());
        for (int i = 0; i < p.marks.size(); i++) {
            b.putInt(p.marks.get(i));
        }
        return b.array();
    }

    /** A chunk {@link #encode} wrote, filled and ready (null for bytes of another format). */
    public Proto decode(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data);
        if (data.length < FIXED) {
            return null;
        }
        int format = b.getInt();
        int extra = format == 3 ? 8 : 0;
        if (!(format == 1 && data.length == FIXED || (format == 2 || format == 3) && data.length >= FIXED + extra + 4)) {
            return null;
        }
        Proto p = new Proto(b.getInt(), b.getInt(), this);
        p.changes = b.getInt();
        p.saved = p.changes;
        int flags = b.get();
        p.decorated = (flags & 1) != 0;
        p.finished = (flags & 2) != 0;
        if (format == 3) {
            p.lsn = b.getLong();
        }
        b.asCharBuffer().get(p.blocks);
        b.position(b.position() + VOLUME * 2);
        b.asIntBuffer().get(p.worldgen).get(p.heights);
        b.position(b.position() + (512 + Region.HEIGHTMAPS * 256) * 4);
        b.get(p.surfaceBiomes).get(p.quarts);
        if (format >= 2) {
            int n = b.getInt();
            if (n < 0 || data.length != FIXED + extra + 4 + n * 4) {
                return null;
            }
            for (int i = 0; i < n; i++) {
                p.marks.add(b.getInt());
            }
        }
        p.task.state.set(Task.DONE);
        return p;
    }

    /**
     * The order MultifaceBlock.DIRECTIONS is in: the one piece of decoration state that is not a chunk's (every
     * glow lichen spread in the JVM shuffles it). Saved beside the held chunks.
     */
    public static int[] lichenOrder() {
        synchronized (LushCaves.DIRECTIONS) {
            return LushCaves.DIRECTIONS.clone();
        }
    }

    /** Puts back an order {@link #lichenOrder} gave (a permutation of 0..5; anything else is ignored). */
    public static void setLichenOrder(int[] order) {
        if (order == null || order.length != 6) {
            return;
        }
        int seen = 0;
        for (int d : order) {
            if (d < 0 || d > 5) {
                return;
            }
            seen |= 1 << d;
        }
        if (seen != 63) {
            return;
        }
        synchronized (LushCaves.DIRECTIONS) {
            System.arraycopy(order, 0, LushCaves.DIRECTIONS, 0, 6);
        }
    }
}
