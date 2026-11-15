import com.exiledradio.cavesandcliffsbackport.terrain.core.Journal;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The decoration journal across a crash, without a game: side x side chunks finished in a spiral (as PipelineBench),
 * against the same run interrupted. The interrupted run saves the world (held chunks into a store kept in memory) a
 * third of the way, goes on to two thirds, and dies without saving; the game is taken to have written every chunk
 * but the last {@code lost} it finished. A second pipeline replays the journal onto the store, finishes the lost
 * chunks again and the rest, and every finished chunk must be the uninterrupted run's.
 *
 * <p>Arguments: seed side threads [cap] [lost]. With -Dnojournal the second pipeline gets no journal (the state before
 * the fix): some chunks differ. Prints the number of chunks that differ, and FAIL or OK.
 */
public final class JournalCheck {

    /** The world's held-chunk store, in memory; durable: the chunks the game has on disk. */
    static final class MemStore implements Pipeline.Store {
        final Map<Long, byte[]> entries = new HashMap<>();
        final Set<Long> durable = new HashSet<>();

        @Override
        public byte[] load(int cx, int cz) {
            return entries.get(key(cx, cz));
        }

        @Override
        public void save(int cx, int cz, byte[] data) {
            entries.put(key(cx, cz), data);
        }

        @Override
        public void released(int cx, int cz, boolean hasEntry, boolean unsaved) {
            // Kept: the game may not have the chunk yet.
        }

        @Override
        public boolean finished(int cx, int cz) {
            return durable.contains(key(cx, cz));
        }
    }

    static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    public static void main(String[] args) throws IOException {
        long seed = Long.parseLong(args[0]);
        int side = Integer.parseInt(args[1]);
        int threads = Integer.parseInt(args[2]);
        int cap = args.length > 3 ? Integer.parseInt(args[3]) : 4096;
        int lost = args.length > 4 ? Integer.parseInt(args[4]) : 6;
        boolean journal = !Boolean.getBoolean("nojournal");
        int[][] order = PipelineBench.spiral(side);
        int n = order.length;

        // Glow lichen's direction order is the JVM's (every spread shuffles it): each run starts from the same one.
        int[] lichen = Pipeline.lichenOrder();
        Map<Long, Integer> whole = new HashMap<>();
        Pipeline a = new Pipeline(seed, threads, cap);
        a.setStore(new MemStore());
        for (int[] c : order) {
            whole.put(key(c[0], c[1]), Arrays.hashCode(a.finish(c[0], c[1]).blocks));
            a.prefetch(c[0], c[1], 6);
        }
        a.shutdown();

        File dir = new File(System.getProperty("java.io.tmpdir"), "journalcheck-" + System.nanoTime());
        Pipeline.setLichenOrder(lichen);
        MemStore store = new MemStore();
        final Journal file = new Journal(dir, e -> {
            throw new IllegalStateException(e);
        });
        Pipeline b = new Pipeline(seed, threads, cap);
        b.setStore(store);
        if (journal) {
            b.setLog(file, 0);
        }
        Map<Long, Integer> split = new HashMap<>();
        int saveAt = n / 3;
        int crashAt = 2 * n / 3;
        int[] lichenAtSave = lichen;
        for (int i = 0; i < crashAt; i++) {
            int[] c = order[i];
            split.put(key(c[0], c[1]), Arrays.hashCode(b.finish(c[0], c[1]).blocks));
            b.prefetch(c[0], c[1], 6);
            if (i == saveAt) {
                // The world saves: the game's chunks so far, then the held chunks; a journal segment closes.
                for (int j = 0; j <= i; j++) {
                    store.durable.add(key(order[j][0], order[j][1]));
                }
                b.saveHeld();
                lichenAtSave = Pipeline.lichenOrder();
                int closed = file.rotate();
                file.deleteBelow(closed);
            }
        }
        for (int j = 0; j < crashAt - lost; j++) {
            store.durable.add(key(order[j][0], order[j][1]));
        }
        // The crash: the held chunks in memory are gone, the store and the journal stay.
        b.shutdown();
        file.close();

        // The new JVM's order: the one saved with the store at the world save (state.dat).
        Pipeline.setLichenOrder(lichenAtSave);
        Pipeline c = new Pipeline(seed, threads, cap);
        c.setStore(store);
        Journal again = new Journal(dir, e -> {
            throw new IllegalStateException(e);
        });
        List<byte[]> records = again.read();
        long bytes = 0;
        for (byte[] r : records) {
            bytes += r.length + 8;
        }
        int replayed = 0;
        long t = System.nanoTime();
        if (journal) {
            c.setLog(again, Journal.last(records));
            replayed = c.replay(records);
        }
        long replayNanos = System.nanoTime() - t;
        for (int i = crashAt - lost; i < n; i++) {
            int[] o = order[i];
            split.put(key(o[0], o[1]), Arrays.hashCode(c.finish(o[0], o[1]).blocks));
            c.prefetch(o[0], o[1], 6);
        }
        c.shutdown();
        again.close();
        PipelineBench.empty(dir);
        dir.delete();

        int differ = 0;
        for (Map.Entry<Long, Integer> e : whole.entrySet()) {
            if (!e.getValue().equals(split.get(e.getKey()))) {
                differ++;
            }
        }
        System.out.println(String.format(java.util.Locale.ROOT,
                "seed %d, %d chunks, %d threads, cap %d, journal %s: %d records on disk (%d KB) at the crash, %d replayed in %.0f ms;"
                        + " %d chunks differ from the uninterrupted run: %s",
                seed, n, threads, cap, journal, records.size(), bytes / 1024, replayed, replayNanos / 1e6, differ,
                differ == 0 ? "OK" : "FAIL"));
        if (differ != 0) {
            System.exit(1);
        }
    }
}
