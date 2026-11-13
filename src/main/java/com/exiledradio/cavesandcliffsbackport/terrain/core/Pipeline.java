package com.exiledradio.cavesandcliffsbackport.terrain.core;

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
 * filled. {@link #finish} does exactly that and hands back the chunk; each chunk is filled once and decorated once
 * while it is held. Vanilla 1.18 decorates in whatever order its chunk system reaches chunks, so features that cross
 * a chunk border depend on that order there too; here it is the order 1.12 asks for chunks.
 *
 * <p>Held chunks ({@link Proto}) are dropped once nothing can read them again (finished, and every chunk within two
 * decorated), and beyond a memory cap the least recently used ones go first. A dropped chunk that is asked for again
 * is filled again; if it had been decorated it is decorated again (its own features come back; what neighbors had
 * written into it before is lost).
 *
 * <p>{@link #finish} and {@link #prefetch} are for one thread (1.12's server thread); the pool is internal.
 */
public final class Pipeline {
    public static final int MIN_Y = OverworldNoise.MIN_Y;
    public static final int HEIGHT = OverworldNoise.HEIGHT;
    public static final int VOLUME = 16 * 16 * HEIGHT;

    /** One chunk on its way: blocks (ChunkFiller's layout), the worldgen and live heightmaps, the column biomes. */
    public static final class Proto {
        public final int cx;
        public final int cz;
        public final char[] blocks = new char[VOLUME];
        /** Region.worldgenHeights: OCEAN_FLOOR_WG, then WORLD_SURFACE_WG (512). */
        public final int[] worldgen = new int[512];
        /** Region.primeHeightmaps, kept live by decoration (Region.HEIGHTMAPS * 256). */
        public final int[] heights = new int[Region.HEIGHTMAPS * 256];
        /** The biome id at each column's top block after carving (x * 16 + z). */
        public final byte[] surfaceBiomes = new byte[256];
        boolean decorated;
        boolean finished;
        final Task task;

        Proto(int cx, int cz, Pipeline owner) {
            this.cx = cx;
            this.cz = cz;
            this.task = new Task(this, owner);
        }

        /** The code at chunk-local x, z and block y. */
        public int get(int x, int y, int z) {
            return blocks[(x * 16 + z) * HEIGHT + y - MIN_Y];
        }
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
            filler.fill(p.cx, p.cz, p.blocks);
            surface.build(p.cx, p.cz, p.blocks);
            carvers.carve(p.cx, p.cz, p.blocks);
            Region.worldgenHeights(p.blocks, p.worldgen);
            Region.primeHeightmaps(p.blocks, p.heights);
            int bx = p.cx << 4;
            int bz = p.cz << 4;
            for (int c = 0; c < 256; c++) {
                int top = p.worldgen[256 + c] - 1;
                p.surfaceBiomes[c] = (byte) zoom.biome(bx + (c >> 4), Math.max(top, MIN_Y), bz + (c & 15));
            }
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
    /** Held chunks, least recently used first. */
    private final LinkedHashMap<Long, Proto> held = new LinkedHashMap<>(1024, 0.75F, true);
    private final int cap;
    /** Chunks dropped once settled: finished and decorated, as is everything within two of them. */
    private final LongSet gone = new LongSet();
    private long sequence;

    /** Counters for the benchmark and the log. */
    public final AtomicLong fillNanos = new AtomicLong();
    public final AtomicLong filled = new AtomicLong();
    public long decorateNanos;
    public long decorated;
    public long waitNanos;
    public long stolen;

    /**
     * @param seed the world seed
     * @param threads worker threads (0: fill on the asking thread only)
     * @param cap how many chunks to hold at most (each about 200 KB)
     */
    public Pipeline(long seed, int threads, int cap) {
        this(new OverworldNoise(seed), new Climate(), threads, cap);
    }

    /** Which chunks get their surface trees ({@link Decorator.TreeGate}); asked on the thread that calls {@link #finish}. */
    public void treeGate(Decorator.TreeGate gate) {
        decorator.treeGate(gate);
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

    /** Stops the workers; queued fills are dropped. */
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
            p = new Proto(cx, cz, this);
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
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
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
        // Ask for the whole 7x7 first, nearest first, so the pool works on it while this thread decorates.
        for (int r = 0; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
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
        c.finished = true;
        release(cx, cz);
        trim();
        return c;
    }

    private void decorate(int cx, int cz, long base) {
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
            }
        }
        long t = System.nanoTime();
        region.set(cx, cz, b25, w25, h25);
        decorator.decorate(region);
        decorateNanos += System.nanoTime() - t;
        decorated++;
        center.decorated = true;
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
            held.remove(k);
            gone.add(k);
        }
    }

    /** Whether every chunk within decoration's read distance of cx, cz is decorated (so none reads it again). */
    private boolean settled(int cx, int cz) {
        int r = Region.READ;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                long k = key(cx + dx, cz + dz);
                Proto p = held.get(k);
                if (p == null ? !gone.contains(k) : !p.decorated) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Above the cap, drops the least recently used chunks that no fill is running for. */
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
            it.remove();
        }
    }

    /** How many chunks are held. */
    public synchronized int held() {
        return held.size();
    }
}
