package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
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
import java.util.HashMap;
import java.util.Map;
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

    HeldStore(WorldServer world, File worldDir) {
        this.world = world;
        this.dir = new File(worldDir, CavesAndCliffsBackport.MOD_ID + "_terrain");
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
    public void forget(int cx, int cz) {
        put(cx, cz, FORGOTTEN);
    }

    private void put(final int cx, final int cz, final byte[] data) {
        final long k = key(cx, cz);
        synchronized (pending) {
            pending.put(k, data);
        }
        writer.execute(() -> {
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

    @Override
    public boolean finished(int cx, int cz) {
        return world.getChunkProvider().isChunkGeneratedAt(cx, cz);
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

    /** Writes what is waiting, then closes the files. */
    void close() {
        flush();
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

    void saveState() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setIntArray("LichenOrder", Pipeline.lichenOrder());
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
            Pipeline.setLichenOrder(CompressedStreamTools.readCompressed(in).getIntArray("LichenOrder"));
        } catch (IOException e) {
            CavesAndCliffsBackport.LOGGER.error("Could not read {}.", file, e);
        }
    }
}
