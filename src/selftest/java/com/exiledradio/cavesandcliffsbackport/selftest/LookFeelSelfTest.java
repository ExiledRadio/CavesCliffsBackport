package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.LoadingRevision;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.Splashes;
import com.exiledradio.backportcore.presentation.VersionedDefaults;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.PresentationOption;
import com.exiledradio.backportcore.presentation.versions.Look118;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stream Lf: 1.17's and 1.18's look and feel. Since 1.1.5 Backport Core owns every piece but the panoramas, their
 * overlay and the music (Core's {@code presentation.versions.Look118}): this checks that this mod still declares the
 * versions and that Core's pieces are there and right with it installed: assets and text, options and defaults, and
 * the numbers of the client steps (checked against 1.18.2's code).
 */
public final class LookFeelSelfTest implements SelfTest {

    private static final String ID = CavesAndCliffsBackport.MOD_ID;
    private static final String CORE = "backportcore";
    private static final String[] CODES = {"en_us", "de_de", "es_ar", "es_es", "es_mx", "es_uy", "es_ve", "fr_fr", "it_it",
            "ja_jp", "ko_kr", "nl_nl", "pl_pl", "pt_br", "ru_ru", "tr_tr", "uk_ua", "zh_cn", "zh_tw"};

    @Override
    public String name() {
        return "lookfeel";
    }

    @Override
    public void run(Check c) throws Exception {
        registration(c);
        panoramas(c);
        splashes(c);
        options(c);
        defaults(c);
        text(c);
        numbers(c);
        alone(c);
    }

    /**
     * Backport Core 1.4.7: with Caves &amp; Cliffs the only backport installed, every earlier version's look and feel is
     * still there, from Core (1.13's command suggestions, 1.14's scroll options, 1.15's toggles, 1.16's FOV Effects).
     */
    private static void alone(Check c) {
        String[] others = {"updateaquaticbackport", "villagerbackport", "buzzybeesbackport", "netherupdatebackport", "wildupdatebackport",
                "trailsandtalesbackport", "trickytrialsbackport", "gamedropsbackport"};
        for (String other : others) {
            if (net.minecraftforge.fml.common.Loader.isModLoaded(other)) {
                c.ok(true, "alone: skipped, " + other + " is installed");
                return;
            }
        }
        com.exiledradio.backportcore.presentation.option.ToggleOption suggest = Presentation.commandSuggestions();
        c.ok(suggest != null && "updateaquaticbackport:auto_suggestions".equals(suggest.id()) && suggest.isCoreOwned(),
                "alone: 1.13's command suggestions are on, from Core: " + suggest);
        c.ok(suggest != null && suggest.isShown() && Presentation.optionsOn(OptionScreen.CHAT).contains(suggest),
                "alone: Chat Settings shows Command Suggestions");
        for (String id : new String[] {"updateaquaticbackport:biome_blend_radius", "villagerbackport:mouse_wheel_sensitivity",
                "buzzybeesbackport:toggle_crouch", "netherupdatebackport:fov_effect_scale", "netherupdatebackport:entity_distance_scaling"}) {
            PresentationOption o = Presentation.option(id);
            c.ok(o != null && o.isCoreOwned() && o.isShown(), "alone: " + id + " is Core's and shows");
        }
        Contribution v116 = Presentation.coreContribution("1.16");
        c.ok(v116 != null && Presentation.installed().contains(v116), "alone: Core's 1.16 pieces apply below 1.18");
        c.ok(Presentation.multiplayerWarning() != null && Presentation.titleAccessibilityIcon() != null, "alone: the multiplayer warning and the Accessibility icon");
    }

    private static void registration(Check c) throws IOException {
        for (String v : new String[] {"1.17", "1.18", "1.18.1", "1.18.2"}) {
            Contribution con = Presentation.contribution(v);
            c.ok(con != null && con.owners().contains(ID), v + " is registered by " + ID + ": " + con);
        }
        // C&C is the newest installed backport in this dev server: 1.18.2 wins every single-slot piece.
        GameVersion newest = Presentation.newest();
        c.ok(newest.isAtLeast("1.18.2"), "the newest installed version is at least 1.18.2: " + newest);
        c.ok(Presentation.isAtLeast("1.17") && Presentation.isAtLeast("1.18"), "isAtLeast(1.17) and (1.18)");
        c.eq(LoadingRevision.DATA_1_16, Presentation.loadingRevision(), "the world-loading screen stays 1.16's");
        if (newest.isAtLeast("1.19")) {
            return; // a later backport owns the single slots
        }
        String[][] assets = {
                {"textures/gui/title/minecraft.png", "256x256"},
                {"textures/gui/title/mojangstudios.png", null},
                {"textures/gui/widgets.png", "256x256"},
                {"textures/gui/checkbox.png", "64x64"},
        };
        for (String[] a : assets) {
            ResourceLocation src = Presentation.asset(a[0]);
            // 1.18.2's files are 1.16.5's: Core's 1.18 serves its 1.16 copy.
            c.eq(new ResourceLocation(CORE, "look/1.16/" + a[0]), src, "Core serves vanilla's " + a[0] + " from its own copy");
            c.ok(pngSize(new ResourceLocation(ID, a[0])) == null, a[0] + " is no longer in this mod's jar");
            int[] size = src == null ? null : pngSize(src);
            c.ok(size != null && (a[1] == null || a[1].equals(size[0] + "x" + size[1])), a[0] + " is in the jar: " + Arrays.toString(size));
        }
        c.eq(new ResourceLocation(CORE, "look/1.16/textures/gui/accessibility.png"), Presentation.titleAccessibilityIcon(), "the title screen's Accessibility icon");
        c.ok(pngSize(new ResourceLocation(CORE, "look/1.16/textures/gui/accessibility.png")) != null, "accessibility.png is in Core's jar");
        String[] warn = Presentation.multiplayerWarning();
        c.ok(warn != null && "multiplayerWarning.header".equals(warn[0]) && "multiplayerWarning.check".equals(warn[2]),
                "the multiplayer warning uses vanilla's keys: " + Arrays.toString(warn));
        c.ok(Presentation.vanillaTexts().contains(new ResourceLocation(CORE, "look/1.18/vanilla_text")), "1.18's vanilla text is registered");
    }

    private static void panoramas(Check c) throws IOException {
        List<Presentation.Panorama> all = Presentation.panoramas();
        Presentation.Panorama p117 = null;
        Presentation.Panorama p118 = null;
        for (Presentation.Panorama p : all) {
            if (p.version.equals(GameVersion.parse("1.17"))) {
                p117 = p;
            } else if (p.version.equals(GameVersion.parse("1.18"))) {
                p118 = p;
            }
        }
        if (!c.ok(p117 != null && p118 != null, "1.17's and 1.18's panoramas are registered: " + all.size())) {
            return;
        }
        if (!Presentation.newest().isAtLeast("1.19")) {
            c.ok(all.get(all.size() - 1) == p118, "the newest panorama, LATEST's choice, is 1.18's");
        }
        for (int i = 0; i < 6; i++) {
            c.eq(new ResourceLocation(ID, "textures/gui/title/background/panorama_" + i + ".png"), p118.faces[i], "1.18 face " + i);
            c.eq(new ResourceLocation(ID, "textures/gui/title/background/1_17/panorama_" + i + ".png"), p117.faces[i], "1.17 face " + i);
            int[] s18 = pngSize(p118.faces[i]);
            int[] s17 = pngSize(p117.faces[i]);
            // Mojang's faces are 1024x1024 (1.18.2's top, face 4, is plain sky and compresses to 7 KB).
            c.ok(s18 != null && s18[0] == 1024 && s18[1] == 1024, "1.18 face " + i + " is a 1024x1024 image: " + Arrays.toString(s18));
            c.ok(s17 != null && s17[0] == 1024 && s17[1] == 1024, "1.17 face " + i + " is a 1024x1024 image: " + Arrays.toString(s17));
        }
        ResourceLocation overlay = Presentation.panoramaOverlay();
        if (!Presentation.newest().isAtLeast("1.19")) {
            c.eq(new ResourceLocation(ID, "textures/gui/title/background/panorama_overlay.png"), overlay, "the panorama overlay");
        }
        int[] size = pngSize(new ResourceLocation(ID, "textures/gui/title/background/panorama_overlay.png"));
        c.ok(size != null && size[0] == 1 && size[1] == 1, "1.18.2's overlay is one clear pixel: " + Arrays.toString(size));
    }

    private static void splashes(Check c) throws IOException {
        List<String> add17 = lines(new ResourceLocation(CORE, "look/1.18/texts/splashes_added_1_17.txt"));
        List<String> rem17 = lines(new ResourceLocation(CORE, "look/1.18/texts/splashes_removed_1_17.txt"));
        List<String> add18 = lines(new ResourceLocation(CORE, "look/1.18/texts/splashes_added_1_18.txt"));
        List<String> rem18 = lines(new ResourceLocation(CORE, "look/1.18/texts/splashes_removed_1_18.txt"));
        c.eq(13, add17.size(), "1.17.1 adds this many splashes to 1.16.5's");
        c.eq(3, rem17.size(), "1.17.1 removes this many");
        c.eq(2, add18.size(), "1.18.2 adds this many to 1.17.1's");
        c.eq(2, rem18.size(), "1.18.2 removes this many");
        List<String> base = lines("/assets/minecraft/texts/splashes.txt");
        List<String> pool = Splashes.pool(base, Presentation.installed(), f -> lines(f));
        for (String s : new String[] {"Contains simulated goats!", "Get to the coppah!", "Honey, I waxed the copper!",
                "Made by \"real\" people!", "[this splash text is now available]"}) {
            c.ok(pool.contains(s), "the splash pool has \"" + s + "\"");
        }
        c.ok(!pool.contains("[this splash text has been delayed until part 2]"), "1.18 took back 1.17's placeholder splash");
        c.ok(base.contains("Woo, /v/!") && !pool.contains("Woo, /v/!"), "1.17 removed \"Woo, /v/!\"");
        c.ok(!pool.contains("Now Java 16!") && !pool.contains("Java 16 + 1 = 17!") && !pool.contains("Now on OpenGL 3.2 core profile!"),
                "the splashes naming a Java or OpenGL version are not shown (false on 1.12.2)");
        // Every 1.17 and 1.18 addition is in, every removal out (unless the other version put it back), whatever else
        // is installed (Core's own 1.13 to 1.16 lists apply too since Core 1.4.7, so the pool's size is not checked).
        for (String s : add17) {
            c.ok(Splashes.isAboutTheRuntime(s) || rem18.contains(s) || pool.contains(s), "1.17's splash \"" + s + "\" is in the pool");
        }
        for (String s : add18) {
            c.ok(Splashes.isAboutTheRuntime(s) || pool.contains(s), "1.18's splash \"" + s + "\" is in the pool");
        }
        for (String s : rem18) {
            c.ok(!pool.contains(s), "1.18 removed \"" + s + "\"");
        }
        c.ok(CavesAndCliffsBackport.class.getResource("/assets/" + ID + "/texts/splashes_added_1_18.txt") == null,
                "the splash files are no longer this mod's");
    }

    private static void options(Check c) {
        PresentationOption hide = Presentation.option(Look118.HIDE_LIGHTNING_FLASHES.id());
        PresentationOption save = Presentation.option(Look118.AUTOSAVE_INDICATOR.id());
        c.ok(hide == Look118.HIDE_LIGHTNING_FLASHES && hide.isCoreOwned(), "Hide Lightning Flashes is registered, Core's");
        c.ok(save == Look118.AUTOSAVE_INDICATOR && save.isCoreOwned(), "Autosave Indicator is registered, Core's");
        c.eq(ID + ":hide_lightning_flashes", hide == null ? null : hide.id(), "it keeps this mod's id (saved values carry over)");
        c.eq(ID + ":autosave_indicator", save == null ? null : save.id(), "so does Autosave Indicator");
        c.eq(OptionScreen.ACCESSIBILITY, Look118.HIDE_LIGHTNING_FLASHES.screen(), "Hide Lightning Flashes is on Accessibility Settings");
        c.eq(OptionScreen.VIDEO, Look118.AUTOSAVE_INDICATOR.screen(), "Autosave Indicator is on Video Settings");
        c.eq(GameVersion.parse("1.18"), Look118.HIDE_LIGHTNING_FLASHES.since(), "Hide Lightning Flashes since 1.18");
        c.eq(GameVersion.parse("1.18"), Look118.AUTOSAVE_INDICATOR.since(), "Autosave Indicator since 1.18");
        c.eq("options.hideLightningFlashes", Look118.HIDE_LIGHTNING_FLASHES.labelKey(), "its label is vanilla's key");
        c.eq("options.hideLightningFlashes.tooltip", Look118.HIDE_LIGHTNING_FLASHES.tooltipKey(), "its tooltip");
        c.eq("options.autosaveIndicator", Look118.AUTOSAVE_INDICATOR.labelKey(), "Autosave Indicator's label");
        c.eq(false, Look118.HIDE_LIGHTNING_FLASHES.defaultValue(), "Hide Lightning Flashes is OFF by default");
        c.eq(true, Look118.AUTOSAVE_INDICATOR.defaultValue(), "Autosave Indicator is ON by default");
        c.ok(Look118.HIDE_LIGHTNING_FLASHES.isShown() && Look118.AUTOSAVE_INDICATOR.isShown(), "both show at the newest version");
        c.ok(!Look118.HIDE_LIGHTNING_FLASHES.isShownAt(GameVersion.parse("1.17.1")), "neither shows before 1.18");
        // 1.18.2's Accessibility Settings: ... FOV Effects, Monochrome Logo, Hide Lightning Flashes.
        c.eq(Presentation.MONOCHROME_LOGO.id(), Look118.HIDE_LIGHTNING_FLASHES.after(), "Hide Lightning Flashes follows Monochrome Logo");
        List<String> access = ids(Presentation.optionsOn(OptionScreen.ACCESSIBILITY));
        int mono = access.indexOf(Presentation.MONOCHROME_LOGO.id());
        int hideAt = access.indexOf(Look118.HIDE_LIGHTNING_FLASHES.id());
        c.ok(mono >= 0 && hideAt == mono + 1, "Accessibility Settings: Monochrome Logo (1.17, Core's), then Hide Lightning Flashes: " + access);
        List<String> video = ids(Presentation.optionsOn(OptionScreen.VIDEO));
        c.ok(!video.isEmpty() && video.get(video.size() - 1).equals(Look118.AUTOSAVE_INDICATOR.id()),
                "Video Settings ends with Autosave Indicator, as 1.18.2's does: " + video);
        Look118.HIDE_LIGHTNING_FLASHES.set(true);
        c.eq(true, Look118.HIDE_LIGHTNING_FLASHES.get(), "Hide Lightning Flashes can be set");
        Look118.HIDE_LIGHTNING_FLASHES.reset();
        c.eq(false, Look118.HIDE_LIGHTNING_FLASHES.get(), "and reset");
    }

    private static void defaults(Check c) {
        boolean found = false;
        for (VersionedDefaults.Entry e : VersionedDefaults.pending(Presentation.installed())) {
            if (e.kind == VersionedDefaults.Kind.GAME_OPTION && "gamma".equals(e.key)) {
                found = "0.5".equals(e.value) && e.version.equals(GameVersion.parse("1.18"));
            }
        }
        // A dedicated server never applies a vanilla setting (client only), so the entry stays pending here.
        c.ok(found, "1.18's brightness default (options.txt gamma:0.5, 21w39a) is a versioned default, applied once per instance");
    }

    private static void text(Check c) throws IOException {
        List<String> keys = null;
        for (String code : CODES) {
            List<String> lines = lines(new ResourceLocation(CORE, "look/1.18/vanilla_text/" + code + ".lang"));
            List<String> k = new ArrayList<String>();
            for (String l : lines) {
                k.add(l.substring(0, l.indexOf('=')));
            }
            if (keys == null) {
                keys = k;
            } else {
                c.ok(keys.equals(k), "vanilla_text/" + code + ".lang has en_us's keys");
            }
        }
        List<String> en = lines(new ResourceLocation(CORE, "look/1.18/vanilla_text/en_us.lang"));
        for (String want : new String[] {"options.vsync=VSync", "tile.blockLapis.name=Block of Lapis Lazuli",
                "build.tooHigh=Height limit for building is %s", "menu.savingLevel=Saving world",
                "options.hideLightningFlashes=Hide Lightning Flashes", "options.autosaveIndicator=Autosave Indicator",
                "entity.PigZombie.name=Zombified Piglin"}) {
            c.ok(en.contains(want), "1.18.2's wording: " + want);
        }
        c.ok(!String.join("\n", en).contains("selectWorld.mapType"), "World Type keeps its colon (1.12.2's button adds no \": \")");
    }

    private static void numbers(Check c) {
        c.eq(192.0F, Look118.cloudHeight(0, 128.0F), "the overworld's clouds are at 192 (1.18)");
        c.eq(128.0F, Look118.cloudHeight(7, 128.0F), "another dimension keeps its own");
        c.eq(150.0F, Look118.cloudHeight(0, 150.0F), "a provider's own cloud height stays");
        // FogRenderer.setupFog (1.18.2): start = far - clamp(far / 10, 4, 64), end = far, far at least 32.
        float[][] fog = {{32, 28, 32}, {128, 115.2F, 128}, {192, 172.8F, 192}, {512, 460.8F, 512}, {1024, 960, 1024}, {16, 28, 32}};
        for (float[] f : fog) {
            c.ok(Math.abs(Look118.terrainFogStart(f[0]) - f[1]) < 1e-3, "fog start at " + f[0] + " is " + f[1] + ": " + Look118.terrainFogStart(f[0]));
            c.eq(f[2], Look118.terrainFogEnd(f[0]), "fog end at " + f[0]);
        }
        // The flash: World.getSkyColorBody blends toward (0.8, 0.8, 1.0); undoing it gives the sky back.
        double[] sky = {0.47, 0.65, 1.0};
        int bolt = 2;
        float pt = 0.3F;
        float f = Math.min(bolt - pt, 1.0F) * 0.45F;
        double[] flashed = {(float) sky[0] * (1 - f) + 0.8F * f, (float) sky[1] * (1 - f) + 0.8F * f, (float) sky[2] * (1 - f) + 1.0F * f};
        Look118.undoLightningFlash(flashed, bolt, pt);
        c.ok(Math.abs(flashed[0] - sky[0]) < 1e-5 && Math.abs(flashed[1] - sky[1]) < 1e-5 && Math.abs(flashed[2] - sky[2]) < 1e-5,
                "Hide Lightning Flashes gives back the sky without the flash: " + Arrays.toString(flashed));
        double[] none = {0.1, 0.2, 0.3};
        Look118.undoLightningFlash(none, 0, 0.5F);
        c.ok(none[0] == 0.1 && none[1] == 0.2 && none[2] == 0.3, "no bolt, no change");
        c.eq("Block: -17 70 33 [15 6 1]", Look118.blockRow(-17, 70, 33), "1.18.2's F3 Block row");
        c.eq("Chunk: -2 4 2 [30 2 in r.-1.0.mca]", Look118.chunkRow(-17, 70, 33), "1.18.2's F3 Chunk row");
        c.eq("Chunk: 40 -4 -33 [8 31 in r.1.-2.mca]", Look118.chunkRow(640, -60, -520), "below y 0 and in a negative region");
        float v = 0.0F;
        for (int i = 0; i < 3; i++) {
            v = Look118.autosaveStep(v, true);
        }
        c.ok(Math.abs(v - 0.488F) < 1e-4, "the indicator fades in by 20% of the rest each tick: " + v);
        c.eq(108, Look118.autosaveAlpha(0.36F, 0.488F, 0.5F), "its alpha between ticks");
    }

    // ---- helpers ----

    private static List<String> ids(List<PresentationOption> options) {
        List<String> out = new ArrayList<String>();
        for (PresentationOption o : options) {
            out.add(o.id());
        }
        return out;
    }

    private static List<String> lines(ResourceLocation rl) {
        return lines("/assets/" + rl.getNamespace() + "/" + rl.getPath());
    }

    private static List<String> lines(String path) {
        List<String> out = new ArrayList<String>();
        InputStream in = LookFeelSelfTest.class.getResourceAsStream(path);
        if (in == null) {
            return out;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (!line.trim().isEmpty()) {
                    out.add(line);
                }
            }
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    /** @return a PNG's width and height from its IHDR chunk, or null when it is missing */
    private static int[] pngSize(ResourceLocation rl) throws IOException {
        InputStream in = LookFeelSelfTest.class.getResourceAsStream("/assets/" + rl.getNamespace() + "/" + rl.getPath());
        if (in == null) {
            return null;
        }
        try {
            byte[] h = new byte[24];
            int n = 0;
            while (n < 24) {
                int r = in.read(h, n, 24 - n);
                if (r < 0) {
                    return null;
                }
                n += r;
            }
            return new int[] {readInt(h, 16), readInt(h, 20)};
        } finally {
            in.close();
        }
    }

    private static int readInt(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | (b[at + 3] & 0xFF);
    }
}
