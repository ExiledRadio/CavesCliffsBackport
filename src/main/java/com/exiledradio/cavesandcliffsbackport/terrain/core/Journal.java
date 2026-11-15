package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.zip.CRC32;

/**
 * The decoration journal ({@link Pipeline#setLog}): one record per {@link Pipeline#finish} that decorated or
 * finished a chunk. A crash loses the held chunks' state in memory; the records since the last world save, replayed
 * onto the held chunks as they were then ({@link Pipeline#replay}), give it back exactly.
 *
 * <p>Records are packed ({@link #pack}) and written by a thread of the journal's own, in the order handed over; a
 * record counts as written once the operating system has it (unbuffered writes: a killed JVM loses nothing written).
 * What must not reach the disk before a record waits for it ({@link #await}): the game's save of the chunk the record
 * finished, and the held-chunk store's entries carrying its number. So whatever is on disk after a kill, the journal
 * holds every record it depends on.
 *
 * <p>Files {@code j.<n>.log} in a directory of their own, one segment per world save ({@link #rotate}); a record is
 * its length, its CRC-32 and its bytes, in one write (a kill leaves at most the last record cut short, which
 * {@link #read} stops at). Plain Java; {@link #append}, {@link #rotate} and {@link #deleteBelow} from one thread.
 */
public final class Journal implements Pipeline.Log {
    /** Ints before a record's decorations (see {@link Pipeline}'s journal). */
    public static final int HEADER = 7;

    private final File dir;
    private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    private final Consumer<IOException> onError;
    private volatile Thread thread;
    /** The segment new records go to (the submitting thread's view). */
    private int segment = -1;
    /** The number of the last record handed over, and of the last one written (or given up on). */
    private volatile long submitted;
    private volatile long written;
    private final Object progress = new Object();

    // The writer thread's.
    private final CRC32 crc = new CRC32();
    private FileOutputStream out;
    private int open = -1;
    private byte[] frame = new byte[1 << 16];
    private boolean failed;

    /** A rotation or deletion, run on the writer thread in order with the records. */
    private static final class Task {
        final int rotateTo;
        final int deleteBelow;
        final Runnable then;

        Task(int rotateTo, int deleteBelow, Runnable then) {
            this.rotateTo = rotateTo;
            this.deleteBelow = deleteBelow;
            this.then = then;
        }
    }

    /** onError: told once, on the writer thread, if a write fails (records are dropped from then on). */
    public Journal(File dir, Consumer<IOException> onError) {
        this.dir = dir;
        this.onError = onError;
    }

    /** The segments on disk, by number. */
    private TreeMap<Integer, File> segments() {
        TreeMap<Integer, File> m = new TreeMap<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.startsWith("j.") && n.endsWith(".log")) {
                    try {
                        m.put(Integer.parseInt(n.substring(2, n.length() - 4)), f);
                    } catch (NumberFormatException ignored) {
                        // Not a segment.
                    }
                }
            }
        }
        return m;
    }

    /**
     * Every whole record on disk ({@link #unpack} reads one), oldest first, up to the first one cut short or
     * damaged: the journal is a prefix of what was decorated. Call before the first {@link #append}.
     */
    public List<byte[]> read() throws IOException {
        List<byte[]> records = new ArrayList<>();
        CRC32 sum = new CRC32();
        for (File f : segments().values()) {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), 1 << 16))) {
                while (true) {
                    int length;
                    try {
                        length = in.readInt();
                    } catch (EOFException e) {
                        break;
                    }
                    int expected = in.readInt();
                    if (length < HEADER * 4 || length > (1 << 28)) {
                        return records;
                    }
                    byte[] b = new byte[length];
                    in.readFully(b);
                    sum.reset();
                    sum.update(b, 0, length);
                    if ((int) sum.getValue() != expected) {
                        return records;
                    }
                    records.add(b);
                }
            } catch (EOFException e) {
                return records;
            }
        }
        return records;
    }

    /** The highest record number in records ({@link #read}), or 0. */
    public static long last(List<byte[]> records) {
        long n = 0;
        for (byte[] r : records) {
            n = Math.max(n, (long) getInt(r, 0) << 32 | (getInt(r, 4) & 0xFFFFFFFFL));
        }
        return n;
    }

    /** The segment records handed over now go to; the first is one past every segment on disk. */
    public int segment() {
        if (segment < 0) {
            TreeMap<Integer, File> m = segments();
            segment = m.isEmpty() ? 0 : m.lastKey() + 1;
        }
        return segment;
    }

    private synchronized void start() {
        if (thread == null) {
            segment();
            thread = new Thread(this::run, "Caves & Cliffs terrain journal");
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * Hands over a record (Pipeline's ints, the header first; copied). It is written soon after; {@link #await} its
     * number before anything that depends on it goes to disk.
     */
    @Override
    public void append(int[] record, int size) {
        start();
        submitted = (long) record[0] << 32 | (record[1] & 0xFFFFFFFFL);
        queue.add(java.util.Arrays.copyOf(record, size));
    }

    /** The number of the last record handed over. */
    public long submitted() {
        return submitted;
    }

    /** Waits until every record up to number n is written (at once if it is; never forever if writing failed). */
    public void await(long n) {
        if (written >= n) {
            return;
        }
        synchronized (progress) {
            boolean interrupted = false;
            while (written < n && thread != null && thread.isAlive()) {
                try {
                    progress.wait(100);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Closes the segment being written once the records handed over so far are in it, and starts the next; returns
     * the number of the one closed.
     */
    public int rotate() {
        start();
        int closed = segment();
        segment = closed + 1;
        queue.add(new Task(segment, -1, null));
        return closed;
    }

    /** Deletes every segment numbered below first (on the writer thread, after what was handed over before). */
    public void deleteBelow(int first) {
        start();
        queue.add(new Task(-1, first, null));
    }

    /** Waits until everything handed over so far is written or done. */
    public void flush() {
        start();
        final Object done = new Object();
        final boolean[] ran = {false};
        queue.add(new Task(-1, -1, () -> {
            synchronized (done) {
                ran[0] = true;
                done.notifyAll();
            }
        }));
        synchronized (done) {
            while (!ran[0] && thread.isAlive()) {
                try {
                    done.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Writes what is waiting, then closes the file and stops the thread. */
    public void close() {
        if (thread == null) {
            return;
        }
        flush();
        thread.interrupt();
        try {
            thread.join(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        thread = null;
    }

    private void run() {
        open = segment;
        try {
            while (true) {
                Object o = queue.take();
                if (o instanceof int[]) {
                    write((int[]) o);
                } else {
                    Task t = (Task) o;
                    if (t.rotateTo >= 0) {
                        closeFile();
                        open = t.rotateTo;
                    }
                    if (t.deleteBelow >= 0) {
                        for (Map.Entry<Integer, File> e : segments().headMap(t.deleteBelow).entrySet()) {
                            if (e.getKey() != open || out == null) {
                                e.getValue().delete();
                            }
                        }
                    }
                    if (t.then != null) {
                        t.then.run();
                    }
                }
            }
        } catch (InterruptedException e) {
            // Closed: nothing is waiting (close flushed first).
        } finally {
            closeFile();
            synchronized (progress) {
                progress.notifyAll();
            }
        }
    }

    private void write(int[] r) {
        long n = (long) r[0] << 32 | (r[1] & 0xFFFFFFFFL);
        if (!failed) {
            try {
                if (out == null) {
                    dir.mkdirs();
                    out = new FileOutputStream(new File(dir, "j." + open + ".log"), true);
                }
                // At most 5 bytes for a header and 5 for a value: 5 per int.
                int most = 8 + r.length * 5;
                if (frame.length < most) {
                    frame = new byte[Math.max(most, frame.length * 2)];
                }
                int length = pack(r, frame, 8) - 8;
                crc.reset();
                crc.update(frame, 8, length);
                putInt(frame, 0, length);
                putInt(frame, 4, (int) crc.getValue());
                out.write(frame, 0, length + 8);
            } catch (IOException e) {
                failed = true;
                onError.accept(e);
            }
        }
        written = n;
        synchronized (progress) {
            progress.notifyAll();
        }
    }

    private void closeFile() {
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignored) {
                // Everything was written as it was appended.
            }
            out = null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Records as bytes

    private static int putInt(byte[] b, int at, int v) {
        b[at] = (byte) (v >>> 24);
        b[at + 1] = (byte) (v >>> 16);
        b[at + 2] = (byte) (v >>> 8);
        b[at + 3] = (byte) v;
        return at + 4;
    }

    private static int getInt(byte[] b, int at) {
        return (b[at] & 255) << 24 | (b[at + 1] & 255) << 16 | (b[at + 2] & 255) << 8 | b[at + 3] & 255;
    }

    /**
     * A record as bytes at b[at...], returning the end: the header ints, then per decoration its three ints and its
     * ops (pairs from {@link Region#ops}), each op header rearranged so neighboring blocks are close numbers (kind,
     * chunk, x, z, then y lowest) and written as the zigzag varint of its difference from the previous header,
     * shifted left one with the low bit set when the value differs from the previous op's; then that value as a
     * varint. About a third of the ints' size: section writes come in blobs of one block.
     */
    static int pack(int[] r, byte[] b, int at) {
        for (int i = 0; i < HEADER; i++) {
            at = putInt(b, at, r[i]);
        }
        int i = HEADER;
        for (int d = r[6]; d > 0; d--) {
            at = putInt(b, at, r[i]);
            at = putInt(b, at, r[i + 1]);
            int count = r[i + 2];
            at = putInt(b, at, count);
            i += 3;
            int prevHeader = 0;
            int prevValue = -1;
            for (int end = i + count; i < end; i += 2) {
                int op = r[i];
                int value = r[i + 1];
                int col = op >> 17 & 255;
                int h = (op >>> 30) << 27 | (op >> 25 & 31) << 22 | (col >> 4) << 13 | (col & 15) << 9 | (op & 511);
                int delta = h - prevHeader;
                prevHeader = h;
                long v = ((long) ((delta << 1) ^ (delta >> 31)) & 0xFFFFFFFFL) << 1 | (value == prevValue ? 0 : 1);
                while (v >= 0x80) {
                    b[at++] = (byte) (v | 0x80);
                    v >>>= 7;
                }
                b[at++] = (byte) v;
                if (value != prevValue) {
                    int x = value;
                    while ((x & ~0x7F) != 0) {
                        b[at++] = (byte) (x | 0x80);
                        x >>>= 7;
                    }
                    b[at++] = (byte) x;
                    prevValue = value;
                }
            }
        }
        return at;
    }

    /** A record's bytes ({@link #pack}, as {@link #read} gives them) back as Pipeline's ints. */
    public static int[] unpack(byte[] b) {
        int decorations = getInt(b, 24);
        int size = HEADER;
        int at = HEADER * 4;
        // First pass: the size.
        for (int d = 0; d < decorations; d++) {
            int ops = getInt(b, at + 8);
            size += 3 + ops;
            at += 12;
            for (int i = 0; i < ops; i += 2) {
                boolean differs = (b[at] & 1) != 0;
                while (b[at++] < 0) {
                    // Rest of the header's varint.
                }
                if (differs) {
                    while (b[at++] < 0) {
                        // Rest of the value's varint.
                    }
                }
            }
        }
        int[] out = new int[size];
        for (int i = 0; i < HEADER; i++) {
            out[i] = getInt(b, i * 4);
        }
        at = HEADER * 4;
        int o = HEADER;
        for (int d = 0; d < decorations; d++) {
            out[o] = getInt(b, at);
            out[o + 1] = getInt(b, at + 4);
            int ops = out[o + 2] = getInt(b, at + 8);
            o += 3;
            at += 12;
            int prevHeader = 0;
            int prevValue = -1;
            for (int i = 0; i < ops; i += 2) {
                long v = 0;
                int shift = 0;
                int x;
                do {
                    x = b[at++];
                    v |= (long) (x & 0x7F) << shift;
                    shift += 7;
                } while (x < 0);
                int zz = (int) (v >>> 1);
                prevHeader += (zz >>> 1) ^ -(zz & 1);
                if ((v & 1) != 0) {
                    int value = 0;
                    shift = 0;
                    do {
                        x = b[at++];
                        value |= (x & 0x7F) << shift;
                        shift += 7;
                    } while (x < 0);
                    prevValue = value;
                }
                int h = prevHeader;
                out[o++] = (h >>> 27 & 3) << 30 | (h >> 22 & 31) << 25 | ((h >> 13 & 15) << 4 | (h >> 9 & 15)) << 17
                        | (h & 511);
                out[o++] = prevValue;
            }
        }
        return out;
    }
}
