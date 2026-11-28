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
| RLCraft 2.9.3 (CurseForge, Forge 2860) | `CavesAndCliffsBackport`, `BackportCore-1.2.0` | nothing: Phosphor 0.2.6 stays (branch `cc-phosphor`; before it, `phosphor-1.12.2-0.2.6`, client and server) | none |
| RLCraft Dregora, Java 8 (CurseForge) | the same (Core 1.2.0 is already there for V&P) | nothing: Phosphor 0.2.6 and PhosphorCrashFix stay (branch `cc-phosphor`; before it, both) | none |
| RLCraft Dregora, Cleanroom (Prism) | the same; Fluidlogged API 3.3.3 (the Fast instance has 3.3.2) | nothing: Alfheim 1.6 stays (branch `cc-alfheim`; before it, `Alfheim`) | none (StellarCore's `WorldBlockPosJudgement` is adapted, branch `cc-stellarcore`) |

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
    (some below y 0) generate in the new terrain. Traverse's biomes join it as other mods' biomes (item 7).
    RLCraft's client (Prism copy, Phosphor removed): the height client test 18/0 (blocks and built render chunks at
    -64 and 304) with RenderLib, Entity Culling, Better Foliage, ChunkAnimator, FBP and the rest.
*   **Dregora: works mechanically, Phosphor (Java 8) or Alfheim (Cleanroom) removed, but it is not Dregora.** The
    `cavesandcliffs` world type replaces OTG's terrain, so the DregoraRL preset, its biomes and OTG structures are
    not in that world. An 80x80-chunk pregen on the Java 8 server ran clean after the two fixes below. The Cleanroom
    clients (stream H's copies, Alfheim removed, this branch's jar and the current Core): Celeritas 22/0 (101 sites),
    Fast 15/0 (105 sites), and both now log "Music: 1.18's tracks join the game music."
    **With Alfheim kept** (branch `cc-alfheim`, 2026-11-14; the client test with two more light reads below 0):
    Fast 17/0 (131 sites), Celeritas 24/0 (127 sites). The Celeritas one ran with Demonica 0.6.0 moved aside: with
    the current Core 1.2.0 (P24, `RenderGlobal.renderSky`), Demonica's Iris mixin `demonica$beginSunset` loses its
    target and the instance does not start, with or without Alfheim. That is for Core (`height.md`, "Not tested").

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

7.  **Other mods' biomes did not generate in `cavesandcliffs` worlds** (branch `cc-modded-biomes`): Biomes O' Plenty,
    Defiled Lands and Traverse appeared only where a 1.18 biome had a modded biome's name, and wrongly so (Biomes O'
    Plenty's temperate grove stood in for 1.18's snowy grove, its meadow for 1.18's meadow), with no decoration.
    `terrain/ModdedBiomes` now places them as TerraBlender does in 1.18: each overworld biome in Forge's
    `BiomeManager` lists or in Biomes O' Plenty's own (its climate weights, capped at 10) takes the place of the 1.18
    biomes closest to it (same kind: land, mountain, river, beach, ocean, mushroom; then BiomeDictionary kind tags,
    temperature, rainfall). Where (since branch `claude/cc-modded-biome-sizes`, item 7a): a noise at 1.18's
    temperature scale decides per 1.18 biome whether a column is it or a modded one, the modded share being the
    weights against a vanilla biome's 10, at most 50%; Voronoi cells of 1024 blocks pick which modded one. Name matching now takes only the family's biomes. In a column a modded biome holds:
    *   the chunk's biome array, the biome provider, mob spawning, grass and water colors, weather: the modded biome;
    *   surface: the soil the 1.18 surface rule laid (grass, dirt, sand, gravel, snow, down to the stone) becomes its
        top and filler blocks; under water only a sea floor block other than dirt (Biomes O' Plenty's) replaces it;
    *   decoration: populate runs the modded biome's own `decorate` (its decorator and every decoration event, which
        Biomes O' Plenty's generators run in) instead of firing `ModDecoration`'s events; 1.12's ores, springs and
        sand, gravel and clay patches are denied there, and the 1.18 trees, cacti and surface plants step aside in
        chunks whose center it holds (`Decorator.TreeGate.PLANTS`, beside Dynamic Trees' gate).
    Settings (`height` category, read when a world loads): `moddedBiomes`, `moddedBiomesExcluded`,
    `moddedBiomeHosts` (`modid:name=1.18 name`), `moddedBiomesMaxShare`, `moddedBiomeAreaSize`. The log lists
    which modded biome takes whose place. Not in Cubic Chunks worlds' surface (the cubes take block codes).

    Rig `G:\VBPTest\cc-moddedbiomes` (Forge 2860 server, Java 8 on the E-cores, `run.sh`: fresh world, Chunk
    Pregenerator square, stop; `tools/packs/chunkdiff.py` compares two worlds' blocks and biomes chunk by chunk):
    *   **Vanilla-only (C&C and Core alone), seed 2026, 32x32 chunks:** this build against the 1.0.0 release jar it
        branches from is within the run-to-run spread (above, "Not fixed"): 53-70 of 835 inner chunks differ, against
        83 for the release jar against itself, in the same fluid, glow lichen and dripstone blocks; biomes identical
        in every chunk. Without a modded biome mod nothing new runs: no overlay, no gate, the old populate.
    *   **Biomes O' Plenty 7.0.1.2445 and Defiled Lands 1.4.3 (with Baubles), 80x80 chunks, seed 2026:** the log
        places 59 biomes (52 BoP, 7 Defiled) against 25 hosts; 42.5% of the columns hold one of 20 BoP biomes
        (boreal forest 15.8%, woodland 5.6%, cherry blossom grove, orchard, maple woods, highland, steppe, alps, ...).
        Per chunk: BoP grass 19.9 and dirt 50.9 (its surface), BoP leaves 56, logs 3.9, plants 9.4, gem ore 3.6 (its
        generators); vanilla coal 142, iron 57, diamond 0.42 against 127, 53 and 0.46 in the vanilla-only world
        (1.12's ores stay out). 74-81 chunks/s pregen.
    *   **Defiled Lands alone** (`moddedBiomesExcluded=biomesoplenty`, which the log confirms: 7 biomes), seed 42:
        20.8% of the columns are Defiled (vilespine forest, defiled swamp, plains, tenebra forest, hills). Per chunk:
        defiled grass 40.3 and dirt 186 (surface), corrupted stone 2611 and gravel 49.5 (its decoration's
        corruption), vilespine 5.9, tenebra logs 1.4 and leaves 0.8, scuronotte 0.75; 24 conjuring altars.
    *   Seed 171 has no modded biome within 640 blocks of spawn: its 14 regions there all drew a rank above 0.52, the
        only such 1280-block square of 1024 tried (`RankWin`, a probe over the compiled classes).
    *   No exception in any log.

7a. **Biome sizes next to BoP** (branch `claude/cc-modded-biome-sizes`, off `claude/xf-cc-118`; a play-test report:
    "a lot of my biomes are really small and the BoP ones very large"). Until here 512-block regions decided
    modded or 1.18 and cut 1.18's patches straight wherever a region border crossed them. Measured with
    `tools/packs/patches.py` (contiguous patches of the saved column biomes, those cut by the area's edge left out)
    on the family set (Core 1.5.0, C&C 1.2.0, Wild Update 1.2.0, Trails & Tales 1.0.5, Tricky Trials 1.0.4, Game
    Drops 1.1.0), 160x160 chunks, seed 2026, and a patch probe over the terrain core's biome source (8192x8192
    blocks, two seeds, area-weighted mean patch size in blocks, Σs²/Σs):
    *   **Family biomes are never replaced and never resized**: the cherry grove is identical with and without BoP
        (92 patches, 3.31% of the columns, the large ones 29.8k columns on average), as are mangrove swamps, pale
        gardens and dappled forests in the probe (they are not hosts). Its size is 1.20's: probe 42k/19k blocks
        against meadow 23k/36k and grove 49k/13k, a mountain-slope biome among mountain-slope biomes. A cherry grove
        a few trees wide is one of 1.20's own fragments.
    *   **BoP patches were not larger: they and the 1.18 biomes around them were cut smaller.** Probe, seeds 2026/42:

        | Placement | modded share | 1.18 land patches | modded patches |
        | --- | --- | --- | --- |
        | no modded biomes | 0 | 107k / 128k | - |
        | 512-block regions (before) | 23% / 23% | 65k / 99k | 55k / 69k |
        | 2048-block regions | 26% / 25% | 79k / 105k | 116k / 84k |
        | noise at 1.18's vegetation scale (-8) + 1024 cells | 24% / 24% | 79k / 98k | 73k / 59k |
        | **noise at 1.18's temperature scale (-10) + 1024 cells (now)** | 21% / 32% | **102k / 100k** | **105k / 97k** |
        | the same with 512 cells | 21% / 32% | 102k / 100k | 78k / 83k |

        Server census, seed 2026 (large patches, 256+ columns, mean in columns): no BoP 21.7k for 1.18 land; with
        BoP before, 15.5k for 1.18 land and 10.7k for BoP; after, the 2512-block square around spawn is all 1.18 (its
        biomes identical to the world without BoP in all 24,611 shared chunks). Seed 42: 15.0k without BoP, 10.8k
        before (BoP 9.1k, 22% of the columns), 15.9k after (BoP 3.3% of that square).
    *   **The cost: BoP comes in zones** of a few thousand blocks, as 1.18's temperature bands do, so one spawn area
        holds little or none and another a lot (21% and 32% over 8192 blocks square). A noise at -9 would be a
        middle way (probe: 1.18 land 95k/111k, modded 79k/85k, BoP zones about half as wide).
    *   The setting is now `moddedBiomeAreaSize` (default 1024, the area that keeps one modded biome): the old
        `moddedBiomeRegionSize=512` stays in installed configs, so it is a new key.

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
*   **Falling blocks after modded decoration**: Defiled Lands' corruption turns 1.18's underground gravel and sand
    blobs into its own falling blocks, and Biomes O' Plenty's waterside generators lay gravel on river floors; where
    a 1.18 cave is under them they fall on their first tick (0.1-0.5 per chunk with BoP, 0.5 with Defiled, 0 in
    vanilla-only worlds; they land and settle). Their handlers run whatever the event result. Making scheduled ticks
    immediate while they decorate would make water flow recursively mid-generation, so it is left.
*   **Modded biomes are read when a world loads**, not saved with it: adding or removing such a mod, or changing the
    settings, changes new chunks only, with a seam at the explored edge. Tricky Trials' 1.21.5 spring plants
    (`ModernFeatures`) still grow in modded biomes' chunks. A Cubic Chunks world gets the modded biomes and their
    decoration but keeps the 1.18 surface blocks.
*   **The 1.18 terrain is not fully deterministic run to run** (found while testing item 7): the same jar, seed and
    pregen twice gives 70-100 of 835 chunks that differ, in fluids (flowing water and lava, the stone and obsidian
    they make), glow lichen faces, pointed dripstone tips and, once, one tree. Biomes and everything else match.
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
*   **Phosphor** packs y into 8 bits of a 64-bit position (z 26, x 26, y 8, light 4; a y-overflow bit), keeps
    per-section flags in 16-bit masks, and `@Overwrite`s the chunk light methods this coremod patches. **Alfheim 1.6**,
    its descendant with the same layout, works (branch `cc-alfheim`, `height.md` "Alfheim 1.6"), and Phosphor itself
    now does too, with the same design (branch `cc-phosphor`, `height.md` "Phosphor"): 0.2.6, 0.2.7 and Hesperus
    0.2.9.1-0.2.9.3. The runs above were made with Phosphor removed; the packs no longer need to remove it.
*   Dregora's CurseForge mod set does not start as a dedicated server as is: `!mixinbooter-11.13.jar` makes FML load
    RenderLib's tweaker as a coremod and it fails (`AsyncKeyboardTransformer`); `RLCraftThreadFix` needs MixinBooter.
    Both are client-side, so the test server left them out. Not this mod's.
*   `Potentially Dangerous alternative prefix cavesandcliffsbackport ... expected placebo` (about 60 warnings at
    start): some recipes register while Placebo is the active mod. Harmless.
