package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Journal;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.storage.RegionFile;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The pipeline's {@link Pipeline.Store} for a world: the chunks held ahead of the explored area that decoration has
 * written into, in region files of their own ({@code <world>/cavesandcliffsbackport_terrain/r.x.z.mca}, deflated as
 * 1.12's are), plus the one piece of decoration state that is not a chunk's ({@code state.dat}: glow lichen's
 * direction order). With them a world stopped and started again decorates the chunks beyond its explored area as one
 * that never stopped.
 *
 * <p>Writes go to one writer thread (deflating 230 KB takes a few milliseconds); a load of a chunk still waiting to
 * be written gets the waiting bytes. A chunk that settles (nothing reads it again) gets a one-byte entry in place of
 * its data, so the sectors are used again and the files stay about the size of the area held at once.
 *
 * <p>Crashes: the pipeline's decoration journal ({@link Journal}, in {@code journal/}) is written ahead of everything
 * that depends on it: an entry waits for the record its chunk carries, and the game's save of a chunk waits for the
 * journal ({@link #beforeChunkSave}). A world save closes a journal segment ({@link #checkpoint}); a segment is
 * deleted once the entries of that save are written and every chunk that settled during it is in the game's region
 * files (until then only the records give such a chunk back). After a crash {@link #recover} replays what is left.
 * A settled chunk's entry is forgotten only once the game has the chunk.
 */
final class HeldStore implements Pipeline.Store {

    private static final byte[] FORGOTTEN = {0};

    private final WorldServer world;
    private final File dir;
    private final Map<Long, RegionFile> regions = new HashMap<>();
    /** Bytes handed to the writer and not yet written, by chunk. */
    private final Map<Long, byte[]> pending = new HashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Caves & Cliffs held chunks");
        t.setDaemon(true);
        return t;
    });
    private boolean failed;

    /** The decoration journal. */
    final Journal journal;
    /** Records found on disk when the world loaded, replayed by {@link #recover} (then null). */
    private List<byte[]> pendingRecords;
    /** The game's region files of this world's chunks. */
    private final File chunkRegions;
    /**
     * Per journal segment, the chunks that settled while it was written and that it (or this store's entry) must
     * give back until the game has them on disk: the key, and 1 if this store holds an entry to forget then.
     */
    private final TreeMap<Integer, List<long[]>> released = new TreeMap<>();
    /** The oldest segment still on disk. */
    private int oldest;
    /** The last segment whose world save's entries are all written (set by the writer thread). */
    private volatile int checkpointWritten = -1;
    /** The number of the last journal record when the world was last saved (state.dat). */
    private long savedLsn;
    /** Region headers read while pruning, by region key (1024 offsets each, null for no file). */
    private final Map<Long, int[]> headers = new HashMap<>();

    HeldStore(WorldServer world, File worldDir) {
        this.world = world;
        this.dir = new File(worldDir, CavesAndCliffsBackport.MOD_ID + "_terrain");
        this.journal = new Journal(new File(dir, "journal"), e -> CavesAndCliffsBackport.LOGGER.error("Could not "
                + "write the terrain journal: a crash from now on can cut features at the edge of the explored area.",
                e));
        String folder = world.provider.getSaveFolder();
        this.chunkRegions = new File(folder == null ? worldDir : new File(worldDir, folder), "region");
    }

    /**
     * Reads the journal left on disk and has the pipeline journal its finishes, numbered after every record and
     * entry there may be. Once, after {@link #loadState} and before the first chunk is finished.
     */
    void open(Pipeline pipeline) {
        try {
            pendingRecords = journal.read();
        } catch (IOException e) {
            CavesAndCliffsBackport.LOGGER.error("Could not read the terrain journal in {}.", dir, e);
            pendingRecords = new ArrayList<>();
        }
        // Every segment on disk is replayed, then deleted, by recover().
        oldest = journal.segment();
        pipeline.setLog(journal, Math.max(savedLsn, Journal.last(pendingRecords)));
        if (pendingRecords.isEmpty()) {
            pendingRecords = null;
            journal.deleteBelow(oldest);
        }
    }

    /**
     * After a crash (records on disk): puts the held chunks back as the last record left them, writes them, and
     * deletes the old segments. Once, before the first chunk is finished or the world saved; nothing without records.
     */
    void recover(Pipeline pipeline) {
        List<byte[]> records = pendingRecords;
        if (records == null) {
            return;
        }
        pendingRecords = null;
        long t = System.nanoTime();
        int applied = pipeline.replay(records);
        headers.clear();
        int dropped = pipeline.dropSettled(this::durable);
        int saved = pipeline.saveHeld();
        flush();
        saveState(pipeline.lsn());
        journal.deleteBelow(oldest);
        CavesAndCliffsBackport.LOGGER.info("1.18 terrain of {}: the last session did not stop cleanly; {} of {} "
                + "decoration journal records replayed onto the chunks held ahead of the explored area ({} written, {} "
                + "settled) in {} ms.", world.getWorldInfo().getWorldName(), applied, records.size(), saved, dropped,
                (System.nanoTime() - t) / 1000000L);
    }

    /** The game is about to write a chunk: every journal record handed over so far goes first. */
    void beforeChunkSave() {
        journal.await(journal.submitted());
    }

    /**
     * The world saved (the held chunks were just handed over): closes the journal segment, and deletes the
     * segments nothing needs any more.
     */
    void checkpoint() {
        final int closed = journal.rotate();
        writer.execute(() -> checkpointWritten = closed);
        prune(closed);
    }

    private void prune(int closed) {
        headers.clear();
        int done = checkpointWritten;
        while (oldest <= done) {
            List<long[]> list = released.get(oldest);
            if (list != null) {
                boolean all = true;
                for (long[] e : list) {
                    if (!durable((int) (e[0] >> 32), (int) e[0])) {
                        all = false;
                        break;
                    }
                }
                if (!all && closed - oldest < 8) {
                    return;
                }
                if (!all) {
                    CavesAndCliffsBackport.LOGGER.warn("1.18 terrain: chunks that settled {} world saves ago are still "
                            + "not on disk; their journal segment is deleted anyway.", closed - oldest);
                }
                for (long[] e : list) {
                    if (e[1] != 0) {
                        forget((int) (e[0] >> 32), (int) e[0]);
                    }
                }
                released.remove(oldest);
            }
            oldest++;
            journal.deleteBelow(oldest);
        }
    }

    /** Whether the game has written chunk cx, cz to its region file (pending writes do not count). */
    boolean durable(int cx, int cz) {
        long k = key(cx >> 5, cz >> 5);
        int[] offsets = headers.get(k);
        if (!headers.containsKey(k)) {
            File f = new File(chunkRegions, "r." + (cx >> 5) + "." + (cz >> 5) + ".mca");
            offsets = null;
            if (f.isFile()) {
                try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
                    if (r.length() >= 4096) {
                        offsets = new int[1024];
                        byte[] b = new byte[4096];
                        r.readFully(b);
                        for (int i = 0; i < 1024; i++) {
                            offsets[i] = (b[i * 4] & 255) << 24 | (b[i * 4 + 1] & 255) << 16 | (b[i * 4 + 2] & 255) << 8
                                    | b[i * 4 + 3] & 255;
                        }
                    }
                } catch (IOException e) {
                    offsets = null;
                }
            }
            headers.put(k, offsets);
        }
        return offsets != null && offsets[(cx & 31) + (cz & 31) * 32] != 0;
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    /** The region file holding chunk cx, cz, or null if it does not exist and create is false. */
    private RegionFile region(int cx, int cz, boolean create) {
        long k = key(cx >> 5, cz >> 5);
        synchronized (regions) {
            RegionFile r = regions.get(k);
            if (r == null) {
                File f = new File(dir, "r." + (cx >> 5) + "." + (cz >> 5) + ".mca");
                if (!create && !f.isFile()) {
                    return null;
                }
                dir.mkdirs();
                r = new RegionFile(f);
                regions.put(k, r);
            }
            return r;
        }
    }

    @Override
    public byte[] load(int cx, int cz) {
        byte[] waiting;
        synchronized (pending) {
            waiting = pending.get(key(cx, cz));
        }
        if (waiting != null) {
            return waiting.length > 1 ? waiting : null;
        }
        RegionFile r = region(cx, cz, false);
        if (r == null || !r.isChunkSaved(cx & 31, cz & 31)) {
            return null;
        }
        try (DataInputStream in = r.getChunkDataInputStream(cx & 31, cz & 31)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 1024);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            byte[] data = out.toByteArray();
            return data.length > 1 ? data : null;
        } catch (IOException e) {
            CavesAndCliffsBackport.LOGGER.error("Could not read held chunk {}, {}; it is generated again.", cx, cz, e);
            return null;
        }
    }

    @Override
    public void save(int cx, int cz, byte[] data) {
        put(cx, cz, data);
    }

    @Override
    public void released(int cx, int cz, boolean hasEntry, boolean unsaved) {
        if (hasEntry || unsaved) {
            released.computeIfAbsent(journal.segment(), s -> new ArrayList<>()).add(new long[]{key(cx, cz),
                hasEntry ? 1 : 0});
        }
    }

    private void forget(int cx, int cz) {
        put(cx, cz, FORGOTTEN);
    }

    private void put(final int cx, final int cz, final byte[] data) {
        final long k = key(cx, cz);
        // The entry carries the number of the journal record that last changed it: that record goes first.
        final long after = journal.submitted();
        synchronized (pending) {
            pending.put(k, data);
        }
        writer.execute(() -> {
            journal.await(after);
            try {
                RegionFile r = region(cx, cz, true);
                try (DataOutputStream out = r.getChunkDataOutputStream(cx & 31, cz & 31)) {
                    out.write(data);
                }
            } catch (IOException | RuntimeException e) {
                if (!failed) {
                    failed = true;
                    CavesAndCliffsBackport.LOGGER.error("Could not save held chunk {}, {}: after a restart, features "
                            + "crossing into it from the explored area may be cut.", cx, cz, e);
                }
            } finally {
                synchronized (pending) {
                    if (pending.get(k) == data) {
                        pending.remove(k);
                    }
                }
            }
        });
    }

    /** An upgraded world's old chunks still to get their part below y 0 (they go through the pipeline), or null. */
    java.util.function.BiPredicate<Integer, Integer> upgrading;

    @Override
    public boolean finished(int cx, int cz) {
        return world.getChunkProvider().isChunkGeneratedAt(cx, cz) && (upgrading == null || !upgrading.test(cx, cz));
    }

    /** Waits until every write handed over so far is done. */
    void flush() {
        try {
            Future<?> f = writer.submit(() -> { });
            f.get(10, TimeUnit.MINUTES);
        } catch (Exception e) {
            CavesAndCliffsBackport.LOGGER.error("Held chunks were not all written.", e);
        }
    }

    /** Writes what is waiting, then closes the files (after the world's last save: its chunks are on disk). */
    void close() {
        flush();
        // Forge posts WorldEvent.Unload before the world flushes its chunk writes: wait for them here, so the chunks
        // count as on disk and the journal is deleted.
        try {
            net.minecraft.world.storage.ThreadedFileIOBase.getThreadedIOInstance().waitForFinish();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        prune(journal.segment());
        journal.close();
        writer.shutdown();
        synchronized (regions) {
            for (RegionFile r : regions.values()) {
                try {
                    r.close();
                } catch (IOException ignored) {
                    // Nothing more to write.
                }
            }
            regions.clear();
        }
    }

    // ---- decoration state

    void saveState(long lsn) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setIntArray("LichenOrder", Pipeline.lichenOrder());
        tag.setLong("Journal", lsn);
        dir.mkdirs();
        File file = new File(dir, "state.dat");
        try (FileOutputStream out = new FileOutputStream(file)) {
            CompressedStreamTools.writeCompressed(tag, out);
        } catch (IOException e) {
            CavesAndCliffsBackport.LOGGER.error("Could not save {}.", file, e);
        }
    }

    void loadState() {
        File file = new File(dir, "state.dat");
        if (!file.isFile()) {
            return;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            NBTTagCompound tag = CompressedStreamTools.readCompressed(in);
            Pipeline.setLichenOrder(tag.getIntArray("LichenOrder"));
            savedLsn = tag.getLong("Journal");
        } catch (IOException e) {
            CavesAndCliffsBackport.LOGGER.error("Could not read {}.", file, e);
        }
    }
}
