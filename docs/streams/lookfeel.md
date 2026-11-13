# Stream Lf: the look and feel of 1.17 and 1.18

Package `lookfeel` (client part in `lookfeel.client`). No config category: like the other backports' look and feel it
is always on, and Backport Core's own settings choose the panorama (`presentation.panorama`). No entity ids; packet ids
75-79 claimed, unused. Dev-server port 25854. Also changed outside the package: one line each in the mod class's
pre-init, `ClientSetup.init` and `SelfTestRunner.TESTS`; `height.HeightContent` now starts its client test only for
`-Pclienttest=height` or `startup` (so `-Pclienttest=lookfeel` runs this stream's instead).

Sources: `family-notes/PRESENTATION_PLAN.md`, the 1.17 and 1.18 sections of
`family-notes/RESEARCH_NON_CONTENT_CHANGES.md`, Mojang's 1.16.5, 1.17.1 and 1.18.2 client jars and asset indexes, and
1.18.2's decompiled code (`MC1182Reference`).

## Changelog section

*   **1.18's title screen**: the Caves & Cliffs panorama. 1.17's amethyst geode panorama is in too; Backport Core's
    panorama setting can pick it (`1.17`), or show a different one each launch.
*   **Splash texts** from 1.17 and 1.18: "Contains simulated goats!", "Get to the coppah!", "Honey, I waxed the
    copper!", "[this splash text is now available]" and more. "Woo, /v/!" is gone, as in 1.17.
*   **Monochrome Logo** (Accessibility Settings): the startup screen in black instead of red.
*   **Hide Lightning Flashes** (Accessibility Settings): lightning no longer lights up the sky. The bolts still show.
*   **Autosave Indicator** (Video Settings, on): "Saving world" fades in at the bottom right while a single-player world
    saves.
*   **Brightness** defaults to 50 instead of Moody. It is set once; after that the slider is yours.
*   **Clouds** float at y 192 in the overworld, as in 1.18.
*   **Fog** starts farther out, close to the edge of your render distance, as in 1.18.1.
*   **F3**: the Block row shows the position inside the chunk, and the Chunk row the region file
    (`[30 2 in r.-1.0.mca]`), as in 1.18.2.
*   Vanilla text worded as 1.18 words it: "VSync", "Block of Lapis Lazuli", and the build limit message no longer
    ends in "blocks".

## How it works

Backport Core owns every piece only one version can have at a time and shows the newest installed version's
(`docs/PRESENTATION.md` in Core). `LookAndFeel.preInit` declares four versions and hands Core their pieces:

| Version | Pieces |
|---|---|
| 1.17 | panorama (`textures/gui/title/background/1_17/`), splashes 1.16.5 → 1.17.1 (13 added, 3 removed) |
| 1.18 | panorama, overlay, splashes 1.17.1 → 1.18.2 (2 added, 2 removed), Hide Lightning Flashes, Autosave Indicator, brightness default, single slots and vanilla text (below); clouds and lightning on the client |
| 1.18.1 | fog distances (client) |
| 1.18.2 | F3 rows (client) |

*   **Panoramas**: the jars carry 1x1 placeholders; the six 1024x1024 faces of each are the asset index's
    (`tools/lookfeel/generate.py`). With Core's `LATEST`, 1.18's shows.
*   **Splashes**: Core unions every installed version's additions and removals. 1.17's "Now Java 16!", "Now on OpenGL
    3.2 core profile!" and 1.18's "Java 16 + 1 = 17!" name the runtime, which is false on 1.12.2, so Core ignores them
    (both ways).
*   **Monochrome Logo** is Core's own option (`Presentation.MONOCHROME_LOGO`, since 1.17), shown once 1.17 is
    declared; Core's startup screen draws black with it on.
*   **Single slots** from 1.18.2's jar, unchanged since 1.15/1.16 and registered at 1.18 so a game with this mod alone
    looks like 1.18 too: panorama overlay (one clear pixel: no gradient), title logo, Mojang Studios startup logo,
    `widgets.png`, `checkbox.png`, the title screen's Accessibility icon, the multiplayer warning.
*   **Vanilla text** (`vanilla_text/`, 19 languages, Mojang's 1.18.2 files): the 80 keys the earlier backports
    reword, with their 1.18.2 text, plus 1.17's rewordings of 1.12.2 text ("Use VSync" → "VSync", the build limit
    message, "Block of Lapis Lazuli", "Shulker Bullet", "Cover Me with Diamonds", the authentication message) and
    1.18's new strings this stream shows. "World Type:" keeps its colon: 1.17 moved the colon into the button's
    "%s: %s", which 1.12.2's button does not use.
*   **Options**: `HIDE_LIGHTNING_FLASHES` (Accessibility, off, tooltip) after Core's Monochrome Logo, and
    `AUTOSAVE_INDICATOR` (Video, on) at the end of Video Settings, both 1.18.2's order. Saved in Core's
    `config/backportcore/options.txt`.
*   **Brightness**: `gameOptionDefault("gamma", "0.5")` at 1.18 (21w39a). Core writes it once per instance when the
    title screen first shows, then never again.
*   **Clouds** (`FogPipeline` step at 1.18, Core patch P15/P16): 192 in dimension 0 when the provider kept 1.12.2's
    128. 1.18 has the clouds at 192 in every overworld (any world type, superflat too), so they are not limited to
    the Caves & Cliffs world type; other dimensions keep theirs.
*   **Fog** (`FogPipeline` distance step at 1.18.1): in clear air, terrain fog from `far - clamp(far / 10, 4, 64)` to
    `far` (1.12.2: `0.75 * far` to `far`), `far` at least 32, as 1.18.2's `FogRenderer.setupFog`. The sky's fog (0 to
    `far`) is already 1.18's; water, lava, blindness, powder snow, the nether's thick fog and a boss's are left to
    vanilla and the other steps. An earlier step's distances stand.
*   **Hide Lightning Flashes** (`FogPipeline` sky step at 1.18): 1.18 skips the flash in `ClientLevel.getSkyColor` only
    (its light map still brightens), so the step undoes 1.12.2's blend toward (0.8, 0.8, 1.0) in the sky color.
*   **Autosave indicator**: 1.18's `Gui.tickAutosaveIndicator` and `renderSavingIndicator`. Every world save on the
    integrated server (autosave every 900 ticks, pausing, `/save-all`) counts, as `isCurrentlySaving` does in 1.18;
    a save is shorter than a client tick, so one begun since the last client tick counts as in progress. Drawn after
    the HUD unless F1 hides it.
*   **F3** (`DebugText` step at 1.18.2): `Block: x y z [x&15 y&15 z&15]` and
    `Chunk: cx cy cz [cx&31 cz&31 in r.rx.rz.mca]`, from the same position 1.12.2 uses.
*   **Menu music** is stream Mu's: 1.18's five menu tracks on the same 1.18 contribution (`music.menuMusic`).
*   **Loading screen**: Core's table has no 1.17 or 1.18 revision; 1.16's stays.

`LookHooks` holds the numbers as plain functions so the server self-test checks them against 1.18.2's code.

## Out of scope

Each with why; Core's pieces are in `docs/CORE_REQUESTS.md`.

*   **Cylindrical fog** (1.18.1): fixed-function fog measures radially or by depth, never horizontally (Lf-1). The
    distances are 1.18.1's; the shape stays a sphere.
*   **Key Binds and Online Options screens; Sneak, Sprint and Auto-Jump on Controls** (1.18): Core's screens (Lf-2).
*   **Brightness slider label** "Default" at 50 and plain numbers (1.18): Core's Video Settings (Lf-3).
*   **Mouse wheel and Shift+click on cycling buttons** (1.17): Core's and vanilla's buttons (Lf-4).
*   **32-bit warning on the title screen** (1.18.2): Core's title screen (Lf-5).
*   **Inventory effect list on the right** (1.18): a screen every container shares (Lf-6).
*   **Simulation Distance, Chunk Builder, the sound Device option, cylindrical render distance**: no 1.12.2
    counterpart (1.12.2 ticks what it loads, builds chunks one way and opens OpenAL's default device).
*   **F3+L profiling** (1.17): 1.12.2's profiler has no recording format; `/debug` stays.
*   **F3 Multinoise, biome builder and terrain rows** (1.18, NoiseRouter in 1.18.2): they read the 1.18 generator. For
    `cc118-all`: one more `DebugText.add("1.18", ...)` step in this package, asking the generator for the climate
    values at the player's column, in a Caves & Cliffs world only.
*   **Sprinting into walls at low angles** (1.18): movement code (`EntityPlayerSP.onLivingUpdate`), a patch for little.
*   **Alt (not Shift) for the debug world type** (1.17), the narrator's position hints, credits, languages and fonts,
    the redstone creative tab order (Core's creative order), absorption hearts under Poison, spectators seeing in lava,
    the frosty powder snow overlay (stream Ps), death messages, subtitles for bundles and crops (no such things here),
    the world-selection warnings, enchantment display caps, the 1.17 shader renderer.
*   **World creation**: 1.18's world types are stream T's (`cavesandcliffs`); "Caves" and "Floating islands" never
    existed in 1.12.2.

## Tested

*   `./gradlew runServer -Pselftest=all` (fresh `eclipse/world`, port 25854), section `lookfeel`: the four versions
    registered by this mod, newest 1.18.2, loading screen 1.16's; Core serves the logo, startup logo, widgets and
    check box from this jar (sizes read from the PNGs); the Accessibility icon, multiplayer warning and vanilla text
    registered; both panoramas (12 faces at 1024x1024, 1.18's last), the clear-pixel overlay; the splash files' counts
    and the pool (additions in, removals out, runtime splashes out, exact size alone); both options (screen, since,
    label, tooltip, defaults, order on Accessibility and Video, set and reset); the brightness default pending at
    1.18; every vanilla_text language with en_us's keys and 1.18.2's wording; the cloud height, fog distances
    (32 to 1024 blocks), the lightning blend undone, F3 rows (negative coordinates, below y 0) and the indicator fade.
*   `python -I tools/check_assets.py`: 0 errors.
*   `./gradlew build --rerun-tasks`: the release jar's classes carry `func_` names.
*   Full suite 2026-11-13: `SELFTEST DONE pass=4609 fail=0`.

## Not tested

*   **Nothing visual has been looked at.** The client test is written but was not run (the user's own game was
    open, and a dev client would take its focus):

    ```
    ./gradlew runClient -Pclienttest=lookfeel
    ```

    It waits on the title screen, checks 1.18's panorama is loaded and brightness is 50, saves
    `eclipse/screenshots/cc_lookfeel_title.png`, then makes a superflat world, saves it and checks "Saving world" drew
    (`cc_lookfeel_saving.png`), checks the clouds at 192 and the F3 step (`cc_lookfeel_f3.png`), and quits by itself
    (report in `eclipse/selftest-client-report.txt`). No clicks or keys are sent.
*   The fog distance and the sky without the flash on screen (headless numbers only), and the options on Core's
    screens.
