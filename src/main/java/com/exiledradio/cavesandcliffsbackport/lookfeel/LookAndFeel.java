package com.exiledradio.cavesandcliffsbackport.lookfeel;

import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import net.minecraft.util.ResourceLocation;

/**
 * Stream Lf: the look and feel of 1.17 and 1.18, the changes they made beyond their content. Backport Core owns
 * every piece only one version can have at a time and shows the newest installed version's. Since Backport Core
 * 1.4.7 Core also owns 1.17's and 1.18's own pieces that are not tied to this mod's content (its
 * {@code presentation.versions.Look118}: Hide Lightning Flashes, the Autosave Indicator, the clouds at 192, 1.18.1's
 * fog, 1.18.2's F3 Block and Chunk rows, the brightness default, the splashes, the title and startup logos, the
 * button, check box and Accessibility textures, the multiplayer warning and vanilla's text as 1.18.2 words it), so a
 * later backport installed alone still has them. What stays here (assets from {@code tools/lookfeel/generate.py}):
 *
 * <ul>
 *   <li>the declarations that 1.17, 1.18, 1.18.1 and 1.18.2 are installed;</li>
 *   <li>1.17's amethyst geode panorama (21w15a) and 1.18's Caves &amp; Cliffs panorama (21w41a), with 1.18.2's
 *       panorama overlay (one clear pixel);</li>
 *   <li>1.18's menu music (stream Mu's {@code music.MusicContent}).</li>
 * </ul>
 * The world-loading screen stays 1.16's (Core's table has no revision at 1.17 or 1.18).
 */
public final class LookAndFeel {

    private static final String ID = CavesAndCliffsBackport.MOD_ID;

    private static boolean registered;

    private LookAndFeel() {
    }

    /** Pre-init, both sides: declares 1.17 and 1.18 and hands Core their panoramas. */
    public static void preInit() {
        if (registered) {
            return;
        }
        registered = true;
        Presentation.version("1.17")
                .panorama(ID + ":textures/gui/title/background/1_17/panorama_");
        Presentation.version("1.18")
                .panorama(ID + ":textures/gui/title/background/panorama_")
                .panoramaOverlay(new ResourceLocation(ID, "textures/gui/title/background/panorama_overlay.png"));
        // 1.18.1: the fog. 1.18.2: the F3 rows. Both are Core's client steps.
        Presentation.version("1.18.1");
        Presentation.version("1.18.2");
    }
}
