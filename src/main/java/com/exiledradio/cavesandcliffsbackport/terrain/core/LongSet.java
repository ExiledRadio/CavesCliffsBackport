package com.exiledradio.cavesandcliffsbackport.terrain.core;

/** A set of longs, open addressing, no boxing (chunk keys the pipeline has settled). Not thread safe. */
final class LongSet {
    private static final long EMPTY = Long.MIN_VALUE;
    private static final long REMOVED = Long.MIN_VALUE + 1;
    private long[] table = new long[1024];
    private int size;
    private int used;

    LongSet() {
        java.util.Arrays.fill(table, EMPTY);
    }

    private static int mix(long k) {
        long h = k * 0x9E3779B97F4A7C15L;
        return (int) (h ^ h >>> 32);
    }

    boolean contains(long k) {
        int mask = table.length - 1;
        for (int i = mix(k) & mask; ; i = (i + 1) & mask) {
            long v = table[i];
            if (v == EMPTY) {
                return false;
            }
            if (v == k) {
                return true;
            }
        }
    }

    void add(long k) {
        if (contains(k)) {
            return;
        }
        if ((used + 1) * 2 > table.length) {
            rehash(size * 4 > table.length ? table.length * 2 : table.length);
        }
        int mask = table.length - 1;
        int i = mix(k) & mask;
        while (table[i] != EMPTY && table[i] != REMOVED) {
            i = (i + 1) & mask;
        }
        if (table[i] == EMPTY) {
            used++;
        }
        table[i] = k;
        size++;
    }

    void remove(long k) {
        int mask = table.length - 1;
        for (int i = mix(k) & mask; ; i = (i + 1) & mask) {
            long v = table[i];
            if (v == EMPTY) {
                return;
            }
            if (v == k) {
                table[i] = REMOVED;
                size--;
                return;
            }
        }
    }

    private void rehash(int length) {
        long[] old = table;
        table = new long[length];
        java.util.Arrays.fill(table, EMPTY);
        size = 0;
        used = 0;
        for (long v : old) {
            if (v != EMPTY && v != REMOVED) {
                add(v);
            }
        }
    }

    int size() {
        return size;
    }
}
