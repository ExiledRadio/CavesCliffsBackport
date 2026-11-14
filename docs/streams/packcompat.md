# Pack compatibility: RLCraft and RLCraft Dregora

Branch `cc-packcompat`. Tested 2026-11-12 on copies under `G:\VBPTest\cc-packs` (the user's instances untouched): a
dedicated Forge 2860 server per pack (Java 8), built from the CurseForge instances' mods and configs, and Prism client
copies. The release jar plus Backport Core 1.2.0 (built from Core `main`, d152653). Tools in `tools/packs`: `scan.py`
(counts every block, block entity, entity and biome in a world's region files, both section lists), `compare.py`,
`pregen.sh` (a timed Chunk Pregenerator square over RCON, password `cctest`), `rcon.py`, `launch-prism.sh` (a Prism
copy's client test, stopped by its own PID); `tools/terrain/LandSeed` (land and cave-biome share near 0, 0 per seed).
The spawn probe is `selftest/PackProbe` (a test build with `-Dcavesandcliffsbackport.selftest=packprobe`).

## What each pack needs

| Pack | Add | Remove for extended height | Config |
| --- | --- | --- | --- |
| RLCraft 2.9.3 (CurseForge, Forge 2860) | `CavesAndCliffsBackport`, `BackportCore-1.2.0` | `phosphor-1.12.2-0.2.6` (client and server) | none |
| RLCraft Dregora, Java 8 (CurseForge) | the same (Core 1.2.0 is already there for V&P) | `phosphor`, `PhosphorCrashFix` | none |
| RLCraft Dregora, Cleanroom (Prism) | the same; Fluidlogged API 3.3.3 (the Fast instance has 3.3.2) | `Alfheim` | StellarCore `B:WorldBlockPosJudgement=false` |

Without removing anything, every 1.17 part works in the packs' own worlds and extended height turns itself off with a
log line naming the mod.

**Backport Core must be the current 1.2.0 build.** The `BackportCore-1.2.0.jar` already in the user's Dregora
instances (CurseForge and Cleanroom Celeritas) is an early build without `terrain.TerrainSource`; with it, creating a
Caves & Cliffs world crashed (`NoClassDefFoundError: com/exiledradio/backportcore/terrain/TerrainSource`, seen in the
Fast test copy). Extended height now refuses with a message instead, and the dependency is `backportcore@[1.2.0,)`.

## Default worlds (the packs' own terrain)

Seed 1718, a 48x48-chunk pregen around 0, 0 with Chunk Pregenerator, then every chunk counted, with and without this
mod (Dregora without it still has Core, which V&P needs).

*   **RLCraft** (Default world type, RLCraft's structure mods): every 1.17 generator places (per chunk: deepslate 1773,
    copper ore 40.7, deepslate ores, geodes (1.21 budding amethyst per chunk), dripstone 15.5 blocks, lush caves with moss
    30.9 and azalea, glow lichen, powder snow 2.4, tuff, calcite). Ores below y 8 turn into their deepslate forms
    with the totals kept (diamond 3.31 against 3.32 per chunk, gold 9.35 against 9.54). Chests (0.16 per chunk),
    spawners (0.11), Charm crates, Quark monster boxes, Ice and Fire roosts and ores, Lycanites blocks, Dynamic Trees,
    Quark's stone revamp, underground biomes, speleothems and crystals: within the run-to-run spread of the baseline.
*   **Dregora** (OTG, the DregoraRL preset): every 1.17 generator places the same way. The OTG structures are
    identical (banners, droppers, crates, beds: ratio 1.01), and the ore totals are kept.
*   No exception from this mod in either log; no coremod or mixin clash; no ID conflict.

## Extended height with the 1.18 terrain (`level-type=cavesandcliffs`)

*   **RLCraft: works, Phosphor removed.** Seed 2026 (land near 0, 0; 1718 is 87% ocean there). Pregen without errors,
    sections -4..19. A six-minute night with three probe players (`PackProbe`) spawned 500 mobs: zombies, skeletons
    and creepers below y 0 as well as above, 20 Lycanites species, Infernal Mobs' modifiers (11 tagged), Ice and Fire
    trolls, hippogryphs and a fire dragon, Quark stonelings below 0, axolotls and glow squid, a village with
    villagers. Ice and Fire roosts, Recurrent Complex/Ruins/Battle Towers/Roguelike structures, chests and spawners
    (some below y 0) generate in the new terrain. Biomes map to Traverse's meadow where Traverse is installed.
    RLCraft's client (Prism copy, Phosphor removed): the height client test 18/0 (blocks and built render chunks at
    -64 and 304) with RenderLib, Entity Culling, Better Foliage, ChunkAnimator, FBP and the rest.
*   **Dregora: works mechanically, Phosphor (Java 8) or Alfheim (Cleanroom) removed, but it is not Dregora.** The
    `cavesandcliffs` world type replaces OTG's terrain, so the DregoraRL preset, its biomes and OTG structures are
    not in that world. An 80x80-chunk pregen on the Java 8 server ran clean after the two fixes below. The Cleanroom
    clients (stream H's copies, Alfheim removed, this branch's jar and the current Core): Celeritas 22/0 (101 sites),
    Fast 15/0 (105 sites), and both now log "Music: 1.18's tracks join the game music."

## Fixed here

1.  **Dynamic Trees and the 1.18 trees doubled up** (RLCraft and Dregora, `cavesandcliffs` worlds): Dynamic Trees
    cancels 1.12's tree decoration event, which the 1.18 generator never fires, so both grew. `terrain/DynamicTreesGate`
    asks Dynamic Trees directly with its own handler's logic (biome database, dimension blacklist, the cactus
    option) and the decorator leaves the 1.18 trees (and cacti) out of those chunks: vanilla leaves 190 to 19 per
    chunk, Dynamic Trees' 199 to 320. The gate is `Decorator.TreeGate`; without Dynamic Trees nothing changes
    (`tools/terrain/TreeGateCheck`: identical chunks with a gate that allows all, no leaves above y 40 with one that
    allows none, identical blocks below y 0). Firing the real event was tried first and overflowed the stack in
    Dregora: Biomes O' Plenty generates its trees in that event, which reads the chunk being generated.
2.  **Future MC crashed chunk generation** (Dregora, `cavesandcliffs`): its flowers draw `nextInt(getHeight + 32)`,
    and a 1.18 pit open to the sky down to y -44 gave a height of -44 (`IllegalArgumentException: bound must be
    positive`, a server crash outside Chunk Pregenerator). The coremod's `World.getHeight(x, z)` now never answers
    below 0, 1.12's "nothing here". Height self-test 130/0 with three new checks.
3.  **RLCraft's client refused extended height**: RenderLib replaces the entity loop of `RenderGlobal.renderEntities`
    (site `renderEntities.list` not found). Its loop walks the world's entity list, so the site is not needed with it.
    Entity Culling 6.3.1's ray tracer read `getBlockStorageArray()[sectionY]` unchecked (would throw below y 0): it
    reads our slot now. Both in `height/asm/ClientPatches`.
4.  **1.18 music never installed in any game** (every pack and dev client logged `Music: could not add 1.18's tracks`):
    `ReflectionHelper.getPrivateValue(class, obj, name, obfName)` takes the dev name first; the call had them swapped.
5.  The client test waited forever under Custom Main Menu (RLCraft) and threw when extended height was refused; the
    height coremod can dump the classes it patches (`-Dcavesandcliffsbackport.height.dump=<dir>`).

6.  **Quark's stone revamp (marble, limestone, jasper, slate and its granite, diorite and andesite clusters),
    underground biomes and varied dungeons did not generate in `cavesandcliffs` worlds** (branch `cc118-fluids-quark`):
    Quark runs them in the DIRT ore event of 1.12's biome decorator and the DUNGEON population event, which the 1.18
    terrain never fired. The generator's populate now fires DUNGEON, `DecorateBiomeEvent.Pre`/`Post`,
    `OreGenEvent.Pre`/`Post` and the DIRT `GenerateMinable` once per chunk (`terrain/ModDecoration`,
    `docs/streams/generator.md`), and the 1.18 granite, diorite and andesite blobs step aside where Quark places its own
    (Quark denies 1.12's there too). Family rig (`G:\VBPTest\cc118fq-rig`, a copy of `family-all`: the family's
    release jars, `FamilyProbe` census of 41x41 chunks at spawn, seed 171, plus a region scan by stone variant), per
    chunk, Quark r1.6-179 and QuarkRotN r1.6-192 giving the same:

    | Per chunk | C&C world, before | C&C world, now | Default world |
    | --- | --- | --- | --- |
    | marble / limestone / slate | 0 / 0 / 0 | 303.6 / 60.8 / 0 | 79.5 / 90.7 / 179.1 |
    | granite / diorite / andesite (stone variants) | 969 / 1023 / 1060 (1.18's) | 33.7 / 5.9 / 348.8 | 238.8 / 0 / 476.0 |
    | underground biomes: elder prismarine / biome cobblestone / glowcelium | 0 / 0 / 0 | 4.15 / 3.05 / 0 | 3.05 / 273.5 / 1.88 |
    | cobwebs / mossy cobblestone (spider, overgrown biomes) | 0.84 / 1.15 | 2.13 / 5.39 | 0.43 / 4.25 |
    | spawners (varied dungeons) | 0.03 | 0.05 | 0.01 |

    The C&C area is plains and forest (73%), the Default one taiga, extreme hills and swamp: Quark's stones and
    underground biomes follow their biome types (marble plains, slate cold, the icy and lava biomes' cobblestone cold
    and mesa), so the mix differs and the totals are of one order. Speleothems, crystals and roots as before. Default
    worlds: the 21x21-chunk census before and after this change is identical but for 1-9 blocks of dirt, leaves and logs (run to
    run noise), Quark's blocks exactly. No exception in any log.

## OptiFine (branch `cc-optifine`)

**Extended height works with OptiFine HD U G5** (Dregora's Shaders instances), shaders and their shadow pass
included. Until this branch it refused: OptiFine rewrites `RenderGlobal.getRenderChunkOffset`, `setupTerrain` and
`ViewFrustum.getRenderChunk`, and six client sites were not found. The patches now carry OptiFine's variant of each
site beside vanilla's, and two small patches for OptiFine's own `ChunkVisibility` and `RenderChunkUtils`
(`docs/streams/height.md`, "OptiFine"; the plan is `docs/streams/optifine-alfheim.md`, section 1). Alfheim is still
removed for extended height (section 2 of that plan).

| Pack copy (on G:, Alfheim removed) | Client test | Notes |
| --- | --- | --- |
| `ccheight-Shaders` (Cleanroom 0.5.17, OptiFine G5, Complementary v4.6) | **42/0** (101 sites) | |
| `ccheight-ShadersNothirium` (plus Nothirium 0.4.9) | **32/0** (106 sites) | Fluidlogged API 3.3.2 swapped for 3.3.3 (Backport Core needs it); one of three runs crashed, below |

Both copies are junctioned into Prism; `tools/packs/launch-prism.sh` stops each by its own PID. The client test makes
a Caves & Cliffs world and checks, besides stream H's checks: the drift report names the seven OptiFine variants;
`ChunkVisibility.getMaxChunkY` answers 320; the render chunks at y -64 and 304 are in OptiFine's `renderInfos` and in
the shadow pass (`renderInfosShadow`) with Complementary on, again with Render Regions on, and with shaders off. With
Nothirium, OptiFine's `setupTerrain` does not run (Nothirium draws the chunks), so its own render chunks are checked
instead. It saves screenshots (`screenshots/cc-height-*.png`: the world bottom, a cave at y -50, the view from
y 280, the bottom with shaders off) and logs the frame rate at spawn.

**Default worlds with OptiFine** (the Shaders copy, `selftest-client-world-default` in the game directory): 13/0,
the frustum 16 render chunks high from y 0, every OptiFine variant in the report, `ChunkVisibility.getMaxChunkY`
running its own body (128). Frame rate at spawn over 20 s: 145 with the coremod on, 104 with `height.enabled=false`
(both swing 75-275 while chunks load; no slowdown).

**Nothirium with OptiFine shaders ran out of direct memory once in three runs** (`ccheight-ShadersNothirium`,
`OutOfMemoryError: Cannot reserve 524288 bytes of direct buffer memory`, 8 GB in use, in Nothirium's
`RenderChunkTaskCompile.compileSection`). Nothirium allocates a `RegionRenderCacheBuilder` whenever its pool is empty
and keeps every one it ever made (an unbounded queue); each holds a compiled section until it is uploaded. The test
teleports across the world, so whole render distances recompile at once, with shaders' wider vertex format and 24
sections per column instead of 16. Not ours to fix (Nothirium's pool); a player who flies fast in that instance may
see it. A larger `-XX:MaxDirectMemorySize` than the heap would be the workaround to try.

## Not fixed (findings)

*   Overlaps, not clashes: Quark's candles and ours, crystal caves and geodes, speleothems and dripstone,
    underground biomes and lush caves.
*   **Biomes O' Plenty's decoration** (trees and plants in its biomes) does not run in `cavesandcliffs` worlds for the
    same reason; its biomes appear only where the 1.18 map names one (meadow).
*   **Backport Core's 1.13+ overworld rules apply to Default worlds once this mod is installed** (it registers 1.18,
    so Core's hills seeding, 1.15 biome zoom and 1.16 layer rules switch on). New chunks of an existing RLCraft world
    get a slightly different biome layout (grass, sand and snow within 1-4% at spawn, but different), so a seam can
    show at the edge of explored land. Dregora (OTG) is not affected. A choice for the family: keep 1.12's layers in
    worlds created before the backport was added, as extended height does.
*   **What removing Phosphor costs** (RLCraft server, Default world, seed 1718, a fresh 48x48-chunk pregen at
    2000, 2000, alternating, twice each): 61 and 58 s without Phosphor, 39 and 37 s with it (38-40 against 59-62
    chunks/s). In `cavesandcliffs` worlds without Phosphor the same pregen took 30-40 s (58-77 chunks/s): the 1.18
    generator places its features before a chunk is in the world, so they cause no light updates; the extended world
    is no slower than RLCraft's Default world with Phosphor.
*   **Alfheim and Phosphor** stay incompatible: both pack y into 8 bits of a 64-bit position (Phosphor: z 26, x 26,
    y 8, light 4; a y-overflow bit), keep per-section flags in 16-bit masks, and `@Overwrite` the chunk light
    methods this coremod patches. A compatible path means rewriting their lighting engines' encoding and section
    bookkeeping, per mod.
*   Dregora's CurseForge mod set does not start as a dedicated server as is: `!mixinbooter-11.13.jar` makes FML load
    RenderLib's tweaker as a coremod and it fails (`AsyncKeyboardTransformer`); `RLCraftThreadFix` needs MixinBooter.
    Both are client-side, so the test server left them out. Not this mod's.
*   `Potentially Dangerous alternative prefix cavesandcliffsbackport ... expected placebo` (about 60 warnings at
    start): some recipes register while Placebo is the active mod. Harmless.
