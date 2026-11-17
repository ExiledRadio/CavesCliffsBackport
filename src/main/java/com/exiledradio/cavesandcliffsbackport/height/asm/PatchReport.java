package com.exiledradio.cavesandcliffsbackport.height.asm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per site, found or not: the pattern-drift report. Written by the transformer as each target class loads, read by
 * {@code height.HeightContent} (which turns extended height off for the session on any miss) and by the self-test
 * (which fails on any miss).
 */
public final class PatchReport {

    private static final Map<String, Boolean> SITES = new LinkedHashMap<String, Boolean>();
    private static final List<String> SKIPPED_CLASSES = new ArrayList<String>();

    private PatchReport() {
    }

    static synchronized void site(String id, boolean found) {
        SITES.put(id, found);
    }

    static synchronized void classSkipped(String target) {
        SKIPPED_CLASSES.add(target);
    }

    /**
     * The sites of the mod-compat patches (other mods' own code, {@code height.compat}), kept apart: a miss there
     * leaves that one class as its mod wrote it and never turns extended height off.
     */
    private static final Map<String, Boolean> COMPAT = new LinkedHashMap<String, Boolean>();

    static synchronized void compatSite(String id, boolean found) {
        COMPAT.put(id, found);
    }

    /** Every mod-compat site seen so far (its class loaded), as {@code "<class>.<site>: found"} or {@code ": NOT FOUND"}. */
    public static synchronized List<String> compatLines() {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Boolean> e : COMPAT.entrySet()) {
            out.add(e.getKey() + (e.getValue() ? ": found" : ": NOT FOUND"));
        }
        return out;
    }

    /** Whether every site of one mod-compat class (by its report label) was found, so the class was patched. */
    public static synchronized boolean compatPatched(String label) {
        boolean any = false;
        for (Map.Entry<String, Boolean> e : COMPAT.entrySet()) {
            if (e.getKey().startsWith(label + ".")) {
                any = true;
                if (!e.getValue()) {
                    return false;
                }
            }
        }
        return any;
    }

    /** Whether the class's patches were all found (and so applied). */
    public static synchronized boolean classPatched(String targetPrefix) {
        boolean any = false;
        for (Map.Entry<String, Boolean> e : SITES.entrySet()) {
            if (e.getKey().startsWith(targetPrefix + ".")) {
                any = true;
                if (!e.getValue()) {
                    return false;
                }
            }
        }
        return any;
    }

    /** Every site seen so far, as {@code "<class>.<site>: found"} or {@code ": NOT FOUND"}. */
    public static synchronized List<String> lines() {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Boolean> e : SITES.entrySet()) {
            out.add(e.getKey() + (e.getValue() ? ": found" : ": NOT FOUND"));
        }
        return out;
    }

    public static synchronized List<String> missed() {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Boolean> e : SITES.entrySet()) {
            if (!e.getValue()) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** Forgets every site (the offline drift check runs the patches once per side). */
    public static synchronized void clear() {
        SITES.clear();
        SKIPPED_CLASSES.clear();
        COMPAT.clear();
    }

    public static synchronized int size() {
        return SITES.size();
    }
}
