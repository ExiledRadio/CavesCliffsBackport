package com.exiledradio.cavesandcliffsbackport.deepslate.client;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Which stone each modded ore texture was drawn on, which of its pixels are ore, and the deepslate textures that
 * follow. Plain images in, plain images out (no game classes), so the dev tool ({@code tools/oretextures}) runs the
 * very code the game does.
 *
 * <h2>Texture families</h2>
 * A mod's ore textures are usually one drawing recolored: Thermal Foundation's and Immersive Engineering's ores share
 * their specks' shapes. Pixel by pixel, a speck is where the family's textures differ from the stone (or from each
 * other); a pixel of stone is the same in all of them. That shared shape is the family's mask, and it cuts an ore whose
 * specks are nearly the stone's own grey (silver, platinum, aluminum) as cleanly as a colorful one. Textures of one mod
 * and size whose clear-cut specks mostly overlap form a family; a texture in none (or in a family of one) is cut by its
 * own difference from the stone as before.
 */
final class OreTextures {

    /** A difference above this from an exactly matched stone is drawn, not stone. */
    static final int LOW = 3;
    /** A texture is on an exact copy of its stone when at least this share of its pixels equal the stone's. */
    static final double EXACT = 0.40;
    /** Two pixels are the same color within this (per channel). */
    static final int AGREE = 2;
    /** Two textures were drawn on one copy of a stone when at least this share of their pixels agree... */
    static final double SAME_STONE = 0.40;
    /** ...and are not (nearly) one texture when less than this share do. */
    static final double SAME_TEXTURE = 0.98;
    /** A family member with less than this share of its pixels left as ore is cut by its own colors instead. */
    static final double MIN_DRAWN = 0.02;

    static final class Result {
        /** Texture name -> its deepslate texture. */
        final Map<String, BufferedImage> painted = new LinkedHashMap<String, BufferedImage>();
        /** Texture name -> the stones it was drawn on (one frame each). */
        final Map<String, List<int[]>> bases = new LinkedHashMap<String, List<int[]>>();
        /** Texture names with no stone found under them (kept as they are). */
        final List<String> kept = new ArrayList<String>();
        /** Texture names whose texture is the stone itself (a base layer). */
        final Set<String> plain = new HashSet<String>();
        /** Texture name -> the pixels drawn as ore, for textures cut by a family's mask. */
        final Map<String, boolean[]> familyMasks = new LinkedHashMap<String, boolean[]>();
        /** Texture name -> its family's number (textures of one family share it). */
        final Map<String, Integer> family = new LinkedHashMap<String, Integer>();
    }

    private OreTextures() {
    }

    /**
     * @param images    every texture by name ("namespace:path"); null images are skipped
     * @param stones    vanilla stone (the current pack's, the default one)
     * @param modStones a mod's own stone blocks' textures, by namespace
     * @param families  whether texture families may cut textures (off: the method before families, for comparisons)
     */
    static Result paint(Map<String, BufferedImage> images, List<BufferedImage> stones,
                        Function<String, List<BufferedImage>> modStones, BufferedImage deepslate, boolean families) {
        Result r = new Result();
        Map<String, List<String>> unmatched = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, BufferedImage> e : images.entrySet()) {
            BufferedImage img = e.getValue();
            if (img == null) {
                continue;
            }
            int[] px = OrePainter.firstFrame(img);
            if (OrePainter.clear(px) >= OrePainter.LAYER) {
                continue;
            }
            int w = img.getWidth(), fh = Math.min(OrePainter.frame(img), img.getHeight());
            List<int[]> mine = new ArrayList<int[]>();
            boolean matched = false;
            for (BufferedImage s : stones) {
                int[] b = OrePainter.scaled(s, w, fh);
                mine.add(b);
                matched |= OrePainter.match(px, b) >= OrePainter.MATCH;
            }
            if (matched) {
                r.bases.put(e.getKey(), mine);
            } else {
                String key = namespace(e.getKey()) + "@" + w + "x" + fh;
                List<String> l = unmatched.get(key);
                if (l == null) {
                    unmatched.put(key, l = new ArrayList<String>());
                }
                l.add(e.getKey());
            }
        }
        // The mod's own stone: the texture of one of its stone blocks the ore was drawn on (Defiled Lands' defiled
        // stone), else what most of its ore textures share. The block's texture comes first: an ore's own shading
        // around its bits keeps two textures out of three from agreeing there, and those pixels would stay.
        Map<String, List<BufferedImage>> own = new HashMap<String, List<BufferedImage>>();
        for (List<String> group : unmatched.values()) {
            String ns = namespace(group.get(0));
            List<BufferedImage> mine = own.get(ns);
            if (mine == null) {
                own.put(ns, mine = modStones.apply(ns));
            }
            List<int[]> frames = new ArrayList<int[]>();
            for (String t : group) {
                frames.add(OrePainter.firstFrame(images.get(t)));
            }
            int[] shared = OrePainter.consensus(frames);
            for (String t : group) {
                BufferedImage img = images.get(t);
                int[] px = OrePainter.firstFrame(img);
                int w = img.getWidth(), fh = Math.min(OrePainter.frame(img), img.getHeight());
                int[] best = null;
                double bestShare = OrePainter.MATCH;
                for (BufferedImage s : mine) {
                    int[] b = OrePainter.scaled(s, w, fh);
                    double share = OrePainter.match(px, b);
                    if (share >= bestShare) {
                        bestShare = share;
                        best = b;
                    }
                }
                if (best == null && shared != null && OrePainter.match(px, shared) >= OrePainter.MATCH) {
                    best = shared;
                }
                if (best != null) {
                    r.bases.put(t, Collections.singletonList(best));
                } else if (families && shared != null) {
                    // Perhaps drawn on another copy of the stone (IE's silver): a family may still cut it, below.
                    r.bases.put(t, Collections.singletonList(shared));
                } else {
                    r.kept.add(t);
                }
            }
        }

        if (families) {
            cutFamilies(images, r);
        }
        // Textures that only matched as a family's member, and no family took: as before, kept.
        for (String t : new ArrayList<String>(r.bases.keySet())) {
            if (!r.familyMasks.containsKey(t) && !matchesAny(images.get(t), r.bases.get(t))) {
                r.bases.remove(t);
                r.kept.add(t);
            }
        }
        for (Map.Entry<String, List<int[]>> e : r.bases.entrySet()) {
            BufferedImage img = images.get(e.getKey());
            r.painted.put(e.getKey(), OrePainter.paint(img, e.getValue(), deepslate, r.familyMasks.get(e.getKey())));
            if (OrePainter.match(OrePainter.firstFrame(img), e.getValue().get(0)) >= 0.9) {
                r.plain.add(e.getKey());
            }
        }
        return r;
    }

    private static boolean matchesAny(BufferedImage img, List<int[]> bases) {
        int[] px = OrePainter.firstFrame(img);
        for (int[] b : bases) {
            if (OrePainter.match(px, b) >= OrePainter.MATCH) {
                return true;
            }
        }
        return false;
    }

    static String namespace(String name) {
        int i = name.indexOf(':');
        return i < 0 ? "minecraft" : name.substring(0, i);
    }

    /** Per pixel of the first frame: how far the texture is from the nearest of its stones (255 where none is defined). */
    static int[] difference(int[] px, List<int[]> bases) {
        int[] d = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            if ((px[i] >>> 24) < 128) {
                continue;
            }
            int best = 255;
            for (int[] b : bases) {
                if (i < b.length && (b[i] >>> 24) >= 128) {
                    best = Math.min(best, OrePainter.distance(px[i], b[i]));
                }
            }
            d[i] = best;
        }
        return d;
    }

    /** Whether a texture was drawn on an exact copy of one of its stones (so any difference above {@link #LOW} is drawn). */
    static boolean exact(BufferedImage img, List<int[]> bases) {
        int[] px = OrePainter.firstFrame(img);
        for (int[] b : bases) {
            int n = 0;
            for (int i = 0; i < px.length && i < b.length; i++) {
                if ((b[i] >>> 24) >= 128 && (px[i] & 0xFFFFFF) == (b[i] & 0xFFFFFF)) {
                    n++;
                }
            }
            if (n >= EXACT * px.length) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sorts each mod's still (unanimated) ore textures of one size into families: textures drawn on one copy of a
     * stone (most of their pixels agree). Within a family a pixel is stone where another member has the very same
     * color there, and ore where this member differs from all of them: the pixels that vary between a mod's ores
     * are its specks, however grey.
     */
    private static void cutFamilies(Map<String, BufferedImage> images, Result r) {
        Map<String, List<String>> groups = new LinkedHashMap<String, List<String>>();
        for (String t : r.bases.keySet()) {
            BufferedImage img = images.get(t);
            if (OrePainter.frame(img) != img.getHeight()) {
                continue;
            }
            String key = namespace(t) + "@" + img.getWidth() + "x" + img.getHeight();
            List<String> l = groups.get(key);
            if (l == null) {
                groups.put(key, l = new ArrayList<String>());
            }
            l.add(t);
        }
        int number = 0;
        for (List<String> group : groups.values()) {
            if (group.size() < 3) {
                continue;
            }
            int n = group.size();
            int[][] px = new int[n][];
            for (int m = 0; m < n; m++) {
                px[m] = OrePainter.firstFrame(images.get(group.get(m)));
            }
            // Partners: drawn on one copy of a stone.
            boolean[][] partner = new boolean[n][n];
            int[] parent = new int[n];
            for (int m = 0; m < n; m++) {
                parent[m] = m;
            }
            for (int a = 0; a < n; a++) {
                for (int b = a + 1; b < n; b++) {
                    double share = agreement(px[a], px[b]);
                    if (share >= SAME_STONE && share < SAME_TEXTURE) {
                        partner[a][b] = partner[b][a] = true;
                        parent[root(parent, a)] = root(parent, b);
                    }
                }
            }
            // The stone's palette: every color two partners agree on somewhere. A pixel in it is stone even where all
            // of a member's partners have specks (a family whose ores are not all one shape).
            Set<Integer> palette = new HashSet<Integer>();
            for (int a = 0; a < n; a++) {
                for (int b = a + 1; b < n; b++) {
                    if (partner[a][b]) {
                        for (int i = 0; i < px[a].length; i++) {
                            if ((px[a][i] >>> 24) >= 128 && (px[b][i] >>> 24) >= 128 && OrePainter.distance(px[a][i], px[b][i]) <= AGREE) {
                                palette.add(px[a][i] & 0xFFFFFF);
                            }
                        }
                    }
                }
            }
            // The family's specks: where most members are clearly not stone. A pixel only faintly off its stone (a
            // grey ore's speck, a tinted rim of stone around one) is drawn only there.
            int[][] diff = new int[n][];
            for (int m = 0; m < n; m++) {
                diff[m] = difference(px[m], r.bases.get(group.get(m)));
            }
            boolean[] core = new boolean[px[0].length];
            int need = Math.max(2, (n + 1) / 2);
            for (int i = 0; i < core.length; i++) {
                int count = 0;
                for (int m = 0; m < n; m++) {
                    if (i < diff[m].length && diff[m][i] > OrePainter.TOLERANCE) {
                        count++;
                    }
                }
                core[i] = count >= need;
            }
            Map<Integer, Integer> numbers = new HashMap<Integer, Integer>();
            for (int m = 0; m < n; m++) {
                int len = px[m].length;
                // Only an exactly matched stone tells "faintly off" apart from another copy of the stone.
                boolean exact = exact(images.get(group.get(m)), r.bases.get(group.get(m)));
                boolean[] ore = new boolean[len];
                boolean any = false;
                int drawn = 0;
                for (int i = 0; i < len; i++) {
                    if ((px[m][i] >>> 24) < 128) {
                        continue;
                    }
                    boolean stone = false;
                    for (int o = 0; o < n && !stone; o++) {
                        if (partner[m][o]) {
                            any = true;
                            stone = (px[o][i] >>> 24) >= 128 && OrePainter.distance(px[m][i], px[o][i]) <= AGREE;
                        }
                    }
                    stone |= palette.contains(px[m][i] & 0xFFFFFF) || exact && diff[m][i] <= OrePainter.TOLERANCE && !core[i];
                    ore[i] = !stone;
                    if (!stone) {
                        drawn++;
                    }
                }
                // No partner, or (nearly) nothing left: a near copy of another texture. The texture's own colors decide.
                if (!any || drawn < MIN_DRAWN * len) {
                    continue;
                }
                String t = group.get(m);
                Integer f = numbers.get(root(parent, m));
                if (f == null) {
                    numbers.put(root(parent, m), f = ++number);
                }
                r.familyMasks.put(t, ore);
                r.family.put(t, f);
            }
        }
    }

    /** The share of two textures' pixels with the same color (within {@link #AGREE}). */
    static double agreement(int[] a, int[] b) {
        int n = 0;
        for (int i = 0; i < a.length && i < b.length; i++) {
            if ((a[i] >>> 24) >= 128 && (b[i] >>> 24) >= 128 && OrePainter.distance(a[i], b[i]) <= AGREE) {
                n++;
            }
        }
        return n / (double) Math.max(1, a.length);
    }

    private static int root(int[] parent, int m) {
        while (parent[m] != m) {
            m = parent[m] = parent[parent[m]];
        }
        return m;
    }

    /** Shared / either, over two pixel sets. */
    static double overlap(boolean[] a, boolean[] b) {
        int both = 0, either = 0;
        for (int i = 0; i < a.length && i < b.length; i++) {
            if (a[i] && b[i]) {
                both++;
            }
            if (a[i] || b[i]) {
                either++;
            }
        }
        return either == 0 ? 0 : both / (double) either;
    }
}
