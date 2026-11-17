package com.exiledradio.cavesandcliffsbackport.deepslate.client;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The image work behind modded deepslate ore textures: an ore texture minus the stone it was drawn on is the ore's
 * overlay, and the overlay drawn over deepslate is the deepslate ore. Plain {@code java.awt} images; every image is
 * read into ARGB first, so palette and keyed-transparency PNGs come out right.
 */
final class OrePainter {

    /** How far (per channel) a pixel may be from the stone under it and still count as stone. */
    static final int TOLERANCE = 14;
    /** An ore texture matches a base when at least this share of its pixels are the base's. */
    static final double MATCH = 0.25;
    /** A texture with at least this share of see-through pixels is an overlay layer, used as it is. */
    static final double LAYER = 0.10;

    /** "Undefined" in a base: matches nothing. */
    static final int NONE = 0;

    private OrePainter() {
    }

    static BufferedImage argb(BufferedImage in) {
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(in, 0, 0, null);
        return out;
    }

    /** The side of one animation frame: frames stack downward and are square. */
    static int frame(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        return h % w == 0 ? w : h;
    }

    /** The first frame's pixels (w x f, row by row). */
    static int[] firstFrame(BufferedImage img) {
        int w = img.getWidth(), f = Math.min(frame(img), img.getHeight());
        return img.getRGB(0, 0, w, f, null, 0, w);
    }

    /** An image scaled to w x h by nearest pixel, as Minecraft scales a 16x texture against a 32x one. */
    static int[] scaled(BufferedImage img, int w, int h) {
        int[] out = new int[w * h];
        int sw = img.getWidth(), sh = Math.min(img.getHeight(), img.getWidth());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y * w + x] = img.getRGB(x * sw / w, y * sh / h);
            }
        }
        return out;
    }

    static boolean close(int a, int b) {
        if ((b >>> 24) < 128) {
            return false;
        }
        for (int shift = 0; shift <= 16; shift += 8) {
            if (Math.abs(((a >> shift) & 255) - ((b >> shift) & 255)) > TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    /** @return the share of see-through pixels */
    static double clear(int[] px) {
        int n = 0;
        for (int p : px) {
            if ((p >>> 24) < 128) {
                n++;
            }
        }
        return n / (double) px.length;
    }

    /** @return the share of the texture's pixels that are the base's */
    static double match(int[] px, int[] base) {
        int n = 0;
        for (int i = 0; i < px.length && i < base.length; i++) {
            if (close(px[i], base[i])) {
                n++;
            }
        }
        return n / (double) px.length;
    }

    /**
     * A mod's own stone: per pixel, the color most of its ore textures agree on (at least half of them, and two).
     *
     * @return the base, {@link #NONE} where they don't agree; or null when fewer than three textures are given
     */
    static int[] consensus(List<int[]> textures) {
        if (textures.size() < 3) {
            return null;
        }
        int len = textures.get(0).length;
        int need = Math.max(2, (textures.size() + 1) / 2);
        int[] base = new int[len];
        for (int i = 0; i < len; i++) {
            int best = NONE, bestCount = 0;
            for (int[] a : textures) {
                int count = 0;
                for (int[] b : textures) {
                    if (close(a[i], b[i])) {
                        count++;
                    }
                }
                if (count > bestCount) {
                    bestCount = count;
                    best = a[i];
                }
            }
            base[i] = bestCount >= need ? best | 0xFF000000 : NONE;
        }
        return base;
    }

    /**
     * Paints the deepslate ore: every pixel of the ore that is none of the bases (and not see-through) over deepslate.
     *
     * @param bases the stones the ore may have been drawn on, each the size of one frame
     */
    static BufferedImage paint(BufferedImage ore, List<int[]> bases, BufferedImage deepslate) {
        int w = ore.getWidth(), h = ore.getHeight(), f = frame(ore);
        int[] ds = scaled(deepslate, w, f);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int local = (y % f) * w + x;
                int p = ore.getRGB(x, y);
                boolean stone = (p >>> 24) < 128;
                for (int[] b : bases) {
                    stone |= close(p, b[local]);
                }
                out.setRGB(x, y, stone ? ds[local] | 0xFF000000 : p | 0xFF000000);
            }
        }
        return out;
    }
}
