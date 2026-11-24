package com.exiledradio.cavesandcliffsbackport.deepslate.client;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Dev tool for texture families: runs {@link OreTextures} (the game's own code) on the ore textures in mod jars, with
 * families off (the method before families) and on, and writes per mod a sheet of original / before / after / mask,
 * and each family's mask.
 *
 * <pre>tools/oretextures/run.sh &lt;out dir&gt; &lt;minecraft 1.12.2 jar&gt; &lt;deepslate.png&gt; &lt;mod jar&gt;...</pre>
 *
 * Textures: every block texture whose file name is an ore's ({@code ore_*}, {@code ore/*}, {@code *_ore}), as the
 * game would find them through the ores' models. The game also tries a mod's own stone blocks; this tool does not.
 */
public final class OreFamilyTool {

    private static final Pattern ORE = Pattern.compile("^assets/([^/]+)/textures/blocks?/((?:.*/)?(?:ore_[^/]*|[^/]*_ore)\\.png)$");
    private static final int SCALE = 8;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: OreFamilyTool <out dir> <minecraft jar> <deepslate.png> <mod jar>...");
            System.exit(2);
        }
        File out = new File(args[0]);
        out.mkdirs();
        BufferedImage stone;
        try (ZipFile mc = new ZipFile(args[1])) {
            stone = read(mc, mc.getEntry("assets/minecraft/textures/blocks/stone.png"));
        }
        BufferedImage deepslate = OrePainter.argb(ImageIO.read(new File(args[2])));
        Map<String, BufferedImage> images = new LinkedHashMap<String, BufferedImage>();
        for (int a = 3; a < args.length; a++) {
            try (ZipFile jar = new ZipFile(args[a])) {
                Map<String, BufferedImage> sorted = new TreeMap<String, BufferedImage>();
                for (Enumeration<? extends ZipEntry> e = jar.entries(); e.hasMoreElements(); ) {
                    ZipEntry z = e.nextElement();
                    Matcher m = ORE.matcher(z.getName());
                    if (m.matches() && !"minecraft".equals(m.group(1))) {
                        String path = z.getName().substring(("assets/" + m.group(1) + "/textures/").length());
                        sorted.put(m.group(1) + ":" + path.substring(0, path.length() - 4), read(jar, z));
                    }
                }
                images.putAll(sorted);
            }
        }
        List<BufferedImage> stones = Collections.singletonList(stone);
        OreTextures.Result before = OreTextures.paint(images, stones, ns -> new ArrayList<BufferedImage>(), deepslate, false);
        OreTextures.Result after = OreTextures.paint(images, stones, ns -> new ArrayList<BufferedImage>(), deepslate, true);

        Map<String, List<String>> byMod = new LinkedHashMap<String, List<String>>();
        for (String t : images.keySet()) {
            String ns = OreTextures.namespace(t);
            if (!byMod.containsKey(ns)) {
                byMod.put(ns, new ArrayList<String>());
            }
            byMod.get(ns).add(t);
        }
        StringBuilder report = new StringBuilder();
        for (Map.Entry<String, List<String>> mod : byMod.entrySet()) {
            List<String> list = mod.getValue();
            int cell = 16 * SCALE, gap = 6, cols = 4;
            BufferedImage sheet = new BufferedImage(cols * (cell + gap) + gap, list.size() * (cell + gap) + gap + 20, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = sheet.createGraphics();
            g.setColor(new Color(40, 40, 48));
            g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            g.setColor(Color.WHITE);
            g.drawString("original      before (no families)      after (families)      drawn as ore", gap, 14);
            int row = 0;
            for (String t : list) {
                BufferedImage img = images.get(t);
                int y = 20 + gap + row * (cell + gap);
                draw(g, firstFrameImage(img, null), gap, y, cell);
                draw(g, firstFrameImage(before.painted.get(t), deepslate), gap + (cell + gap), y, cell);
                draw(g, firstFrameImage(after.painted.get(t), deepslate), gap + 2 * (cell + gap), y, cell);
                boolean[] mask = after.familyMasks.get(t);
                if (mask != null) {
                    draw(g, maskImage(mask, img.getWidth()), gap + 3 * (cell + gap), y, cell);
                }
                String how = after.familyMasks.containsKey(t) ? "family " + after.family.get(t)
                        : after.kept.contains(t) ? "kept (no stone found)"
                        : after.painted.containsKey(t) ? "own colors (no family)"
                        : "layer (used as it is)";
                report.append(t).append(": ").append(how).append('\n');
                row++;
            }
            g.dispose();
            ImageIO.write(sheet, "png", new File(out, mod.getKey() + "-sheet.png"));
        }
        // Each family's mask: where most of its members are not stone.
        Map<Integer, boolean[]> union = new TreeMap<Integer, boolean[]>();
        Map<Integer, Integer> widths = new TreeMap<Integer, Integer>();
        for (Map.Entry<String, Integer> e : after.family.entrySet()) {
            boolean[] m = after.familyMasks.get(e.getKey());
            boolean[] u = union.get(e.getValue());
            if (u == null) {
                union.put(e.getValue(), u = new boolean[m.length]);
                widths.put(e.getValue(), images.get(e.getKey()).getWidth());
            }
            for (int i = 0; i < m.length; i++) {
                u[i] |= m[i];
            }
        }
        for (Map.Entry<Integer, boolean[]> e : union.entrySet()) {
            BufferedImage m = maskImage(e.getValue(), widths.get(e.getKey()));
            BufferedImage big = new BufferedImage(m.getWidth() * SCALE, m.getHeight() * SCALE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = big.createGraphics();
            draw(g, m, 0, 0, big.getWidth());
            g.dispose();
            ImageIO.write(big, "png", new File(out, "family-" + e.getKey() + "-mask.png"));
        }
        java.nio.file.Files.write(new File(out, "report.txt").toPath(), report.toString().getBytes("UTF-8"));
        System.out.print(report);
    }

    private static BufferedImage read(ZipFile zip, ZipEntry e) throws Exception {
        try (InputStream in = zip.getInputStream(e)) {
            return OrePainter.argb(ImageIO.read(in));
        }
    }

    /** One frame; a see-through texture (a layer) shown over deepslate when one is given. */
    private static BufferedImage firstFrameImage(BufferedImage img, BufferedImage under) {
        if (img == null) {
            return null;
        }
        int w = img.getWidth(), f = Math.min(OrePainter.frame(img), img.getHeight());
        BufferedImage out = new BufferedImage(w, f, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        if (under != null) {
            g.drawImage(under, 0, 0, w, f, 0, 0, under.getWidth(), Math.min(under.getWidth(), under.getHeight()), null);
        }
        g.drawImage(img, 0, 0, w, f, 0, 0, w, f, null);
        g.dispose();
        return out;
    }

    private static BufferedImage maskImage(boolean[] mask, int w) {
        int h = mask.length / w;
        BufferedImage m = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < mask.length; i++) {
            m.setRGB(i % w, i / w, mask[i] ? 0xFFFFFFFF : 0xFF000000);
        }
        return m;
    }

    private static void draw(Graphics2D g, BufferedImage img, int x, int y, int size) {
        if (img != null) {
            g.drawImage(img, x, y, size, size, null);
        }
    }
}
