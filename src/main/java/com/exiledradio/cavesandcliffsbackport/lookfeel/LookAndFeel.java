package com.exiledradio.cavesandcliffsbackport.lookfeel;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.ToggleOption;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import net.minecraft.util.ResourceLocation;

/**
 * Stream Lf: the look and feel of 1.17 and 1.18, the changes they made beyond their content. Backport Core owns
 * every piece only one version can have at a time and shows the newest installed version's; this class hands Core
 * the pieces of 1.17, 1.18, 1.18.1 and 1.18.2 (assets from {@code tools/lookfeel/generate.py}).
 *
 * <ul>
 *   <li>1.17 (21w15a): the amethyst geode panorama, and the splash changes between 1.16.5's and 1.17.1's
 *       {@code splashes.txt} (13 added, 3 removed: "Woo, /v/!" and two that name a Java or OpenGL version, which
 *       Core ignores). Monochrome Logo (21w13a) is Core's own option, shown from 1.17;</li>
 *   <li>1.18 (21w41a): the Caves &amp; Cliffs panorama, 1.18.2's splash changes ("[this splash text is now
 *       available]"), Hide Lightning Flashes (Accessibility, 21w37a), Autosave Indicator (Video, 21w42a), the
 *       brightness default of 50 (21w39a), and the clouds at 192 (client side). 1.18's menu music is stream Mu's
 *       ({@code music.MusicContent});</li>
 *   <li>1.18.1: world fog starting farther out (client side);</li>
 *   <li>1.18.2: the F3 Block and Chunk rows with the in-chunk position and the region file (client side);</li>
 *   <li>from 1.18.2's jar, as Core's single slots: the panorama overlay (one clear pixel), the title logo and the
 *       Mojang Studios startup logo, the button and check box textures, the title screen's Accessibility icon and
 *       the multiplayer warning, all unchanged since 1.16, so that 1.18 looks the same with or without the earlier
 *       backports; and vanilla's own text as 1.18.2 words it ({@code vanilla_text/}: "VSync", "Block of Lapis
 *       Lazuli", 1.18's new option names ...).</li>
 * </ul>
 * The world-loading screen stays 1.16's (Core's table has no revision at 1.17 or 1.18).
 */
public final class LookAndFeel {

    private static final String ID = CavesAndCliffsBackport.MOD_ID;

    /** 1.18's Hide Lightning Flashes (Accessibility Settings, 21w37a): lightning no longer flashes the sky. */
    public static final ToggleOption HIDE_LIGHTNING_FLASHES = new ToggleOption(ID + ":hide_lightning_flashes",
            "options.hideLightningFlashes", OptionScreen.ACCESSIBILITY, false);

    /** 1.18's Autosave Indicator (Video Settings, 21w42a): "Saving world" at the bottom right while a save runs. */
    public static final ToggleOption AUTOSAVE_INDICATOR = new ToggleOption(ID + ":autosave_indicator",
            "options.autosaveIndicator", OptionScreen.VIDEO, true);

    private static boolean registered;

    private LookAndFeel() {
    }

    private static ResourceLocation rl(String path) {
        return new ResourceLocation(ID, path);
    }

    /** Pre-init, both sides: hands Core the pieces of 1.17 and 1.18. */
    public static void preInit() {
        if (registered) {
            return;
        }
        registered = true;
        Presentation.version("1.17")
                .panorama(ID + ":textures/gui/title/background/1_17/panorama_")
                .addSplashes(rl("texts/splashes_added_1_17.txt"))
                .removeSplashes(rl("texts/splashes_removed_1_17.txt"));

        Contribution v118 = Presentation.version("1.18");
        v118.panorama(ID + ":textures/gui/title/background/panorama_")
                .panoramaOverlay(rl("textures/gui/title/background/panorama_overlay.png"))
                .addSplashes(rl("texts/splashes_added_1_18.txt"))
                .removeSplashes(rl("texts/splashes_removed_1_18.txt"))
                .titleLogo(rl("textures/gui/title/minecraft.png"))
                .startupLogo("textures/gui/title/mojangstudios.png", rl("textures/gui/title/mojangstudios.png"))
                .replaceAsset("textures/gui/widgets.png", rl("textures/gui/widgets.png"))
                .replaceAsset("textures/gui/checkbox.png", rl("textures/gui/checkbox.png"))
                .titleAccessibilityIcon(rl("textures/gui/accessibility.png"))
                .multiplayerWarning("multiplayerWarning.header", "multiplayerWarning.message", "multiplayerWarning.check")
                .vanillaText(rl("vanilla_text"))
                // 21w39a: "Moody" (0) is no longer the default brightness; 50 is (options.txt "gamma:0.5").
                .gameOptionDefault("gamma", "0.5");
        // 1.18.2's Accessibility Settings end: ... FOV Effects, Monochrome Logo, Hide Lightning Flashes.
        HIDE_LIGHTNING_FLASHES.tooltip("options.hideLightningFlashes.tooltip");
        v118.option(HIDE_LIGHTNING_FLASHES.after(Presentation.MONOCHROME_LOGO.id()));
        // 1.18.2's Video Settings end with it (after FOV Effects): no anchor, so it goes last.
        v118.option(AUTOSAVE_INDICATOR);

        // 1.18.1: the fog. 1.18.2: the F3 rows. Both are client steps (lookfeel.client.LookAndFeelClient).
        Presentation.version("1.18.1");
        Presentation.version("1.18.2");
    }
}
