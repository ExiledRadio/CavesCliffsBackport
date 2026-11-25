# Extended height (y -64 to 319): design

Decision (the user, relayed 2026-11-07): stream T is option A, full 1.18 world height. Bytecode patching is
approved **for this part only**. This document came before any of its code.

**Status (2026-11-11):** the height coremod (part 1) is built on branch `cc-height`, as designed here, with what
building it added: per-section entity lists (so mobs below 0 render and area queries find them), the falling-block
limits, the build limit, spawn heights from the bottom, the fog near the bottom, chunk receive/unload re-render
ranges and the entity lists `RenderGlobal` reads. How it works, every site and what was tested:
`docs/streams/height.md`. The speed numbers are in section 4. Alfheim was first refused (the user, 2026-11-11: it packs y in 8
bits in its `LightingEngine` and replaces `Chunk.relightBlock`); Alfheim 1.6 is now patched in its own classes (section
3, branch `cc-alfheim`).

The work splits in two:

1. **The height coremod** (`com.exiledradio.cavesandcliffsbackport.height`, its own small package): makes a
   1.12.2 world hold, save, send, light and draw blocks from y -64 to 319. No terrain in it.
2. **The 1.18 terrain** (`terrain`, plain Forge code, no patches): density terrain, noise caves, aquifers, ore
   veins, mountain biomes, cave regions as real 3D areas. Written on top of the coremod's small API.

Everything below was read from Forge 14.23.5.2847's sources (the line numbers are from its `forgeSrc`
sources jar) and from the installed Fluidlogged API 3.3.3 and Celeritas 2.5.0 jars.

## 1. Where vanilla assumes 0-255

### 1.1 The section layout we keep

A 1.12 chunk is `ExtendedBlockStorage[16]`, slot `y >> 4`. Ours becomes 24 slots, laid out so **every slot a
1.12 mod can compute is still right**:

| Slots | Heights | Why there |
| --- | --- | --- |
| 0-15 | y 0-255 | unchanged: `storageArrays[y >> 4]` in any mod keeps working |
| 16-19 | y 256-319 | the natural continuation: a mod computing `y >> 4` for y up to 319 lands on the right slot |
| 20-23 | y -64 to -1 | out of the way: vanilla and well-behaved mods guard `y >= 0` before indexing |

(The existing extended-height mods put -64..-1 at 16-19, so a mod indexing `y >> 4` for y 256-319 reads the
wrong blocks. Ours does not; its only cost is that "slot index x 16" is not the height for slots 20-23, and
`ExtendedBlockStorage.getYLocation()` stays correct for all of them.)

The layout only applies to worlds that have extended height (1.6). Every other world keeps 16 slots.

### 1.2 What gets patched

Our own ASM transformer, one small class per target class, each change matched against the instruction
pattern it expects (section 2). "Site" means one place in one method.

| Class | Methods / sites | Change |
| --- | --- | --- |
| `World` | `isOutsideBuildHeight` (282); `isAreaLoaded` y check (336); `getLight`, `getLightFromNeighbors`, `getLightFromNeighborsFor`, `getLightFor` (744-939: `y < 0` gives 0, `y >= 256` gives 15); `canSnowAt`/`canBlockFreeze` light checks (2962, 3015); `getHeight` / `getActualHeight` stay, made per world | `0` and `256` become this world's min and max |
| `WorldProvider` | `getHeight`, `getActualHeight` (603, 608) | 320 for an extended overworld |
| `Chunk` | constructor: array size; `getBlockState` (540), `setBlockState` (593), `getLightFor`/`setLightFor`/`getLightSubtracted` (692-742), `getBlockStorageArray` users inside it, `isEmptyBetween` (1191), `getTopFilledSegment`, `generateSkylightMap`, `generateHeightMap`, `relightBlock` (the `& 255` at 396), `checkSkylightNeighborHeight`/`updateSkylightNeighborHeight`, `enqueueRelightChecks`, `getPrecipitationHeight`, the client `read` (1240-1276) | `storageArrays[y >> 4]` becomes `Heights.slot(world, y)`; `y >= 0 && y >> 4 < length` becomes `Heights.inside(world, y)`; top-down loops start at the world's top and stop at its bottom; `getTopFilledSegment` walks slots in height order; the heightmap keeps full ints |
| `ChunkCache` | `getBlockState`, `getLightFor`, `getCombinedLight`, `isSideSolid` (97-211) | world min/max instead of 0/256 |
| `AnvilChunkLoader` | `writeChunkToNBT` (354: `Y & 255`), `readChunkFromNBT` | slots 0-15 stay in `Sections` exactly as vanilla writes them; slots 16-23 go to a second list, `cavesandcliffsbackport:Sections`, with signed `Y` (section 1.5) |
| `SPacketChunkData` | constructor (`65535` means a full chunk), `calculateChunkSize`, `extractChunkData` | "full chunk" becomes all 24 sections; the section mask is already an int, so 24 bits fit |
| `PlayerChunkMapEntry` | `blockChanged` (209-213: `x << 12 \| z << 8 \| y` in a short), `update` (161, 183, 250, 274) | changes outside 0-255 are kept as positions in a side list and sent as single-block packets; the packet format itself does not change |
| `Entity` | `onEntityUpdate`/`outOfWorld` (585: `posY < -64`) | the void starts 64 below the world's bottom |
| `ViewFrustum` (client) | `createRenderChunks`, `updateChunkPositions`, `getRenderChunk` (62: `countChunksY = 16`, 154) | 24 render chunks high, starting at the world's bottom |
| `RenderGlobal` (client) | `getRenderChunkOffset` (1053: `0 <= y < 256`), `setupTerrain`'s start chunk | world min/max |
| `EntityRenderer` / `WorldProvider` (client) | void fog and the sky going dark below the horizon | 1.18 has no void fog; off for extended worlds |

Not patched, on purpose:

* `ChunkPrimer` (a fixed 16x256x16 array): our terrain writes straight into the chunk's sections; other
  generators keep their 0-255 primer.
* `SPacketMultiBlockChange`'s format: handled on the sending side (above), so packet-reading mods see
  vanilla packets.
* `BlockPos`: its long form already holds y from -2048 to 2047.
* Random ticks (`WorldServer.updateBlocks`): they walk the section array and use `getYLocation()`, which is
  right for every slot.

Estimated size: about 15 classes, 60 sites. Each site is a few instructions; nothing replaces a whole method.

### 1.3 `y >> 4` in other mods

Mods that index sections for y 0-319 work (1.1). Mods that loop `0 .. world.getHeight()` see 0-319 and miss
the deep part, which is harmless. Mods that loop over the section array and use `index << 4` as the height
read slots 20-23 as y 320-383: wrong, but only for blocks below y 0. Known users get listed as they are found
(the pack's structure mods first: Ruins, Battle Towers, Lycanites, Roguelike Dungeons, Recurrent Complex).

### 1.4 Lighting

Sky light enters at the top of the world (320) and goes down to its bottom; the heightmap holds real heights
(negative ones too). Block light needs only the range checks above. **Alfheim and Phosphor replace vanilla's
lighting engine**, and RLCraft Revamped uses Alfheim: their engines carry their own 0-255 assumptions. Alfheim 1.6 and
Phosphor (0.2.6, 0.2.7, Hesperus 0.2.9.1-0.2.9.3) have a compat path each (section 3: a wider position key, sections by
slot, boundary flags two sections to a bit).

### 1.5 The save format, and removing the mod

* Slots 0-15 are written to `Level.Sections` exactly as vanilla writes them. Slots 16-23 go to
  `Level.cavesandcliffsbackport:Sections`, a list vanilla never reads.
* **Removing the mod**: the world still loads, with no crash and no regenerated chunks. Everything from 0 to
  255 is there; blocks below 0 and above 255 are gone. Bedrock was at -64, so the bottom of the world is open
  at y 0 and players can fall out. That is said plainly in the config comment and the mod description.
* The world's height is saved in its own data (`cavesandcliffsbackport_height` in `data/`), never guessed.

### 1.6 Which worlds, and existing worlds

* Extended height is a property of a world, chosen when it is created: a world type "Caves & Cliffs"
  (`cavesandcliffs`), and a config switch that makes it the default for new worlds. Only the overworld;
  the Nether and End keep 0-255.
* **Existing worlds keep 0-255** unless an operator upgrades them with `/cavesandcliffs upgradeworld` (section 9),
  the way 1.18 upgraded old worlds. Adding the mod to an old world changes nothing by itself.
* The 1.17 streams already built (deepslate layer, ores, geodes) keep working in old worlds within 0-255. In an
  extended world, the deepslate layer pass steps aside (it already skips worlds whose minimum is below 0), and
  the terrain places deepslate itself.

## 2. ASM, not mixins

Our own ASM coremod (`IFMLLoadingPlugin` plus one `IClassTransformer`), with no MixinBooter, which is the
user's standing preference. Mixins are not much safer here:

* Almost every site is a constant (`0`, `256`, `16`, `255`, `65535`) or an array index inside a vanilla
  method. In mixin terms that is `@ModifyConstant`/`@Redirect` on the same instructions, with the same
  conflicts when another mod touches the same method.
* Fluidlogged API, which Backport Core requires, is ASM too, and patches the same classes (`Chunk`,
  `ChunkCache`, `World`, `WorldServer`, `RenderChunk`). Two ASM transformers that each change a few
  instructions in place can run in either order; a mixin `@Overwrite` would not.
* Not depending on MixinBooter means no version clash with Cleanroom's built-in mixin or with packs that
  ship their own.

Rules for the transformer:

* Each site has an exact pattern (opcode sequence plus owner/name/descriptor, SRG names in production, MCP in
  the dev environment through FML's deobfuscation flag). If a pattern is not found, the transformer logs which
  site failed and turns extended height off for the session, rather than half-patching a class.
* Every patched site calls into one class, `height.Heights`, with plain static methods (`minY(World)`,
  `maxY(World)`, `slot(World, int)`, `inside(World, int)`, `sections(World)`), so the patches stay tiny and
  their logic is ordinary Java that can be unit-tested.
* A world without extended height takes the same code paths as vanilla, and `Heights` answers 0/256/16 for it.
* Packaging: the mod jar carries `FMLCorePlugin` and `FMLCorePluginContainsFMLMod` in its manifest, as
  Fluidlogged API does, so it stays one jar. The plugin does nothing unless extended height is possible
  (config on, no conflicting mod found).
* Mojang's frames: a class Forge does not patch (here `ViewFrustum`, `SPacketChunkData`) keeps ProGuard's
  stack-map frames, which mark a local TOP once vanilla stops reading it. Inserted code that loads such a local
  is a `VerifyError` in the real game only (dev runs recompile Minecraft with javac), so inserted loads read only
  `this` or values vanilla still reads after that point. The start-up check above loads each target without
  linking it, which does not verify it. Before a release, `tools/release/boot-server.sh <server dir> <log>
  <Backport Core jar> build/libs/<this mod>.jar` boots the release jars on a plain dedicated server with
  `-Dcavesandcliffsbackport.loadPatched=true`: `PatchCheck` then loads, links and verifies every class this mod's
  two transformers patch (`ChangesTransformer`'s too) and logs `PATCHCHECK ok`. The family's frame verifier
  (`family-notes/tools/verify/ScanAll.java`) checks both sides against the real client jar; `smallchanges` has
  the JVM verify each patched SRG class of `ChangesTransformer`.

## 3. Compatibility

| | Status | What to do |
| --- | --- | --- |
| **Fluidlogged API** (Core requires it) | Its transformers touch the same `Chunk`/`World` methods (`setBlockState`, `getPrecipitationHeight`, `enqueueRelightChecks`, light lookups); ours change other instructions in them, so both apply in either order. **But** its per-chunk fluid storage packs a position into 16 bits with y as 8 of them (`FluidStateCapabilityVanilla.serializeY` is `y << 8`): fluidlogged blocks below 0 or above 255 would land on the wrong heights. | Needs a fix outside our patches: (a) ask its author for a wider key in extended worlds (it already abstracts the serializer and has a Cubic Chunks path); or (b) Core's `Waterloggable` adapter keeps fluid states outside 0-255 itself. Until then, waterlogging works only within 0-255 in extended worlds. To settle with the Core maintainer. |
| **Cleanroom** | Runs Forge coremods and plain `IClassTransformer`s. | Test on the Dregora Cleanroom instances. |
| **Celeritas** | Replaces vanilla's chunk rendering and sizes its own section range (it hard-coded 0-16). | **Done (stream H):** its section range, and the three places it reads sections by index, are patched; tested in the Dregora Celeritas instance (blocks and built sections at -64 and 304). |
| **OptiFine** | Rewrites much of `RenderGlobal` and chunk rendering. A strict pattern miss in a client class turns extended height off with a message, rather than crashing. | Test with the Shaders instance. |
| **VintageFix** | Its `chunk_access` mixin overwrites `Chunk.getBlockState` with a 0-255 body. | **Done (stream H):** that overwrite gets the same patch as vanilla's method (Mixin reads it through our transformer); tested in the Dregora Fast instance. |
| **Nothirium** | Its own chunk renderer, camera-centred in y, with three 0-16 section limits. | **Done (stream H):** the limits and its section lookup are patched; tested in the Fast instance. |
| **Valkyrie** (Red Studio) | Without OptiFine, `@Overwrite`s `ViewFrustum.createRenderChunks`, `updateChunkPositions`, `markBlocksForUpdate`, `getRenderChunk` and `RenderGlobal.getRenderChunkOffset` with y from 0 and a 0-256 walk; Mixin applies them after our transformer, so the frustum ran y 0-383 (nothing below 0 drawn; `renderEntities` crashed above y 320). | **Done (1.1.6):** `asm/ValkyriePatches` patches its two mixin classes as Mixin reads them (as Alfheim's), with vanilla's changes; made for 0.3-Dev-5, a miss refuses with Valkyrie named (`height/ValkyrieCheck`). With Celeritas its RenderGlobal mixin does not apply (Celeritas owns `setupTerrain`). Tested on Cleanroom 0.7.14 with and without Celeritas. |
| **Roughly Enough IDs / Just Enough IDs** (mod id `jeid`) | Their `BlockStateContainer.getDataForNBT` mixin writes palette indices and keeps the palette in the container; their `AnvilChunkLoader` mixin saves it as `Palette` for vanilla's `Sections` only. Our list's sections were saved without it and read back as block ids: below 0 and above 255, chunks came back as air, stone and nulls after a reload. Biomes become an int array: `getBiomeArray()` is a throwaway array of their error biome, and after `generateChunk` they refill the ints from the biome provider. | **Done (1.1.6):** `height/IdsCompat` saves and reads `Palette` (and REID's `Add2`) in our list through their `INewBlockStateContainer`, puts our generator's surface biomes in their int array once the chunk is in the world (`ChunkEvent.Load`, after their refill), and our biome readers read the ints. Resolved by name (no dependency); same interfaces in JEID 1.0.3-1.0.4 and REID 2.0-2.3. At start-up a 4096-block section of every saveable vanilla state is round-tripped through our save code; a miss refuses with the version named. A section an older C&C saved without its palette is left empty (never decoded) and its tag kept in `<world>/cavesandcliffsbackport_lost_sections/`. `tools/height/ids-selftest.sh` (`IdsSelfTest`). |
| **StellarCore** | `WorldBlockPosJudgement` overwrites `World.isOutsideBuildHeight` with 0-255. | Handled: with StellarCore installed, our mixin config `mixins.cavesandcliffsbackport.stellarcore.json` (plugin `height.asm.StellarCoreMixinPlugin`) rewrites the overwritten body's `y < 0` / `y >= 256` to the world's height after Mixin applies it (exact match, 1.5.22 and 1.6.0). If the body is anything else, the probe at world load keeps the world 0-255 and names the StellarCore version; `B:WorldBlockPosJudgement=false` is the fallback. |
| **Alfheim 1.6** | Replaces the lighting engine (1.4) with mixins: packs y in 8 bits of its queue key, reads sections by `y >> 4`, keeps one boundary flag bit per section in a `short`, and `@Overwrite`s `Chunk.relightBlock` and `getLightFor`. | **Done (branch `cc-alfheim`):** its `LightingEngine` and mixin classes are patched as Mixin reads them (26 sites): with extended height on, a key with 9 bits of y (x and z within 16,777,216), sections by slot, flags two sections to a bit (same save format), our `relightBlock` sites back in its overwrite. Exactly 1.6: any other version refuses, with the reason. Tested on a dedicated server (light across chunk borders and down shafts below 0, across a restart; Default-world light unchanged) and in the Dregora Cleanroom copies (`height.md`). |
| **Phosphor** (and its fork Hesperus) | Replaces the lighting engine (1.4), with Alfheim's 8-bit y (Alfheim descends from it). | **Done (branch `cc-phosphor`):** the Alfheim design, in Phosphor's own classes (`LightingEngine`, `LightingHooks`, `LightingEngineHelpers`, `MixinChunk`; 28 sites). Phosphor 0.2.6 and 0.2.7 and Hesperus 0.2.9.1-0.2.9.3 (the same bytecode where it matters); any other version refuses, with the reason. Tested on the dev server (`height.md`). |
| **Cubic Chunks** | Stores any height in 16x16x16 cubes. | The coremod refuses to turn on; the Caves & Cliffs world type is a Cubic Chunks one instead (y -64 to 319, the same 1.18 terrain in cubes; `docs/streams/cubicchunks.md`). |
| **The other extended-height mods** (Caves Not Cliffs, Farmer's Future Delight) | Same job, different layouts. | Detected at startup; extended height refuses to turn on, with a message. |

## 4. Speed

Hard requirement (RLCraft Revamped). The measurement already on file: vanilla 68 chunks/s, the existing
extended-height terrain mods about 15 (4.5x slower), on the 14900K, same seed.

* **Height alone** (vanilla terrain in an extended world, the patches active): **at most 1.2x vanilla's time
  per chunk.** The extra 128 blocks of height are mostly empty sections above (never allocated) and solid
  stone below, which our generator writes section by section.
  **Measured 2026-11-11** (`python tools/height/bench.py 2`: the `heightbench` self-test on a dev server pinned to
  the E-cores, logical processors 16-31; seed 1718; a 24x24 square of chunks at chunk (1000, 1000), generated and
  populated, after an 8x8 warm-up; then each chunk written to NBT as a save does, four times; a fresh world per run;
  best of two rounds; every other stream's world generators on in all three modes):

  | Mode | Generate + populate | Write to NBT | Chunks/s | vs vanilla |
  | --- | --- | --- | --- | --- |
  | vanilla (`height.enabled=false`: nothing patched) | 11.96 ms/chunk | 3.14 ms | 83.6 | 1 |
  | coremod on, Default world | 12.47 ms | 3.48 ms | 80.2 | gen x1.04, gen+save x1.06 |
  | coremod on, Caves & Cliffs world (y -64..319) | 13.30 ms | 4.10 ms | 75.2 | **gen x1.11, gen+save x1.15** |

  Within the 1.2x target. The extended world's extra cost is the four deep sections each chunk now has (filling
  them, and writing them to the save); the run-to-run spread of vanilla itself was about 7%.
* **1.18 terrain**: **at most 2x vanilla's time per chunk, aim 1.5x**, and at least 34 chunks/s on the same
  machine and seed. How: 1.18's own cell interpolation (density sampled at 4x8x4 cell corners only), column
  noise cached per chunk column, and noise for chunks around the one being generated computed ahead on a
  small worker pool (the main thread then only fills blocks). No noise sampled per block.
  **Measured 2026-11-12** (stream G, `docs/streams/generator.md`): the 1.18 generator takes **0.61x** vanilla
  1.12's time per chunk on the 16 E-cores (5.99 vs 9.76 ms/chunk, 167 chunks/s; `tools/terrain/genbench.py`, this
  section's harness) and 0.69x on four E-cores (7.20 vs 10.43 ms). Head-to-head with Chunk Pregenerator on plain
  servers (`tools/terrain/pregenbench.py`): 57.2 chunks/s, vanilla 46.9, Caves Not Cliffs 9.7, Farmer's Future
  Delight 8.8. With trees and vegetation merged (same day): **0.44x** (5.13 vs 11.63 ms/chunk, 195 chunks/s) and
  57.1 chunks/s against vanilla's 49.6 head-to-head.
* **Measured** before each part merges: a pregeneration benchmark on a dedicated dev server, fixed seed, a
  fixed square of chunks, chunks/s and ms/chunk against plain vanilla on the same build, run pinned to the
  E-cores (memory note on P-core instability).

## 5. Test plan

Automated, headless, no hand play.

1. **`Heights` logic**: plain unit tests of the slot layout (every y from -64 to 319 to its slot and back),
   with and without extended height. No patches involved.
2. **Patched paths** (`-Pselftest=height`, dev server with the coremod loaded through
   `-Dfml.coreMods.load`): set and read a block at every height; light it, and check sky light at -64 under
   open sky and block light at 319; save a chunk to NBT and read it back (both section lists, signed `Y`);
   write `SPacketChunkData` to a buffer and check the section mask and size; change 20 blocks below 0 in one
   tick and check single-block packets go out; an entity falls into the void below -128; a non-extended world
   behaves as vanilla (the whole existing self-test suite passes unchanged).
3. **Removal**: copy an extended test world, strip our section list from its region files with a script, load
   it on a server without the mod: it loads, 0-255 intact, no chunk regenerated.
4. **Client**: a client self-test that creates a single-player world through the integrated server, waits for
   the chunks around the player, and checks the render frustum is 24 high and that the client chunks hold
   blocks at -64 and 319. Logged, not looked at. With OptiFine/Celeritas: start-up and that same log.
5. **Pattern drift**: the transformer reports, per site, found or not; the test fails on any "not found".
6. **Speed**: section 4's benchmark, recorded in this document with its numbers.

## 6. For other mods (Backport Core)

`WorldHeights` in Core already answers min/max height for the other extended-height mods. It gets a branch for
ours (CORE_REQUESTS 11), so Wild Update's deep dark and Tricky Trials' trial chambers use 1.18's real heights
in our worlds and 0-255 elsewhere.

## 7. Order

1. This document (now). The user reviews it.
2. Read Alfheim's lighting and Celeritas's section manager (section 3); settle Fluidlogged API's y packing with
   Core's maintainer.
3. The coremod with `Heights`, tests 1, 2, 5; then save, packets, removal (test 3); then the client (test 4).
4. The terrain on top, part by part, each benchmarked (test 6).

## 8. Open points for the user

* **Alfheim**: works (1.6; section 3). A new Alfheim version refuses extended height until its patterns are checked.
* **Fluidlogged blocks outside 0-255** wait for (a) or (b) in section 3.
* **Old worlds stay 0-255** unless upgraded (section 9).

## 9. Upgrading an old world

`/cavesandcliffs upgradeworld` (operator level 4: the server console, or a single player with cheats on) upgrades a
0-255 Default world to y -64..319 the way 1.18 upgraded worlds from 1.17. Package `upgrade`; the terrain side is
`terrain.UpgradeTerrain`, `terrain.core.Upgrade` and `terrain.core.Blending`.

**The command.** `upgradeworld` explains what happens and asks for `upgradeworld confirm` within 60 seconds. Confirm
saves the world, copies `level.dat` to `level.dat.before-extended-height` and writes
`data/cavesandcliffsbackport_upgrade.dat` (state pending); it tells the player to back up the world folder and
restart. `upgradeworld cancel` clears a pending request; `upgradeworld status` reports progress. It refuses when:
extended height is off (Cubic Chunks, Caves Not Cliffs, Farmer's Future Delight, an Alfheim other than 1.6, a
Phosphor other than the supported ones, the config switch, a missed patch site: `HeightContent.refusal()`); StellarCore
keeps `World.isOutsideBuildHeight` at 0-255; the structure guard patch was not applied; the world type is not
Default (1.18 upgraded Default, Large Biomes and Amplified; this mod has only Default's terrain); the world is
extended or upgraded already, or the request is pending.

**The switch, at load** (`WorldUpgrade.worldConstructed`, from `HeightHooks.worldConstructed`, before the world's
height is read and before its chunk generator exists). The checks run again; then every chunk in `region/*.mca` is
recorded as old (one bit per chunk, from the region headers), `cavesandcliffsbackport_terrain.dat` says 1.18 (with the
newer biomes the installed backports have), `cavesandcliffsbackport_height.dat` says -64..319, the state file says
upgraded (written last, atomically), and the world type becomes Caves & Cliffs (level.dat gets it at the next save;
an upgraded state file puts it back if level.dat lags). Stopping or crashing at any point leaves the old world or the
upgraded one.

**Old chunks** (`ModernGenerator.upgradeChunk`, on `ChunkDataEvent.Load`, before the chunk joins the world). A chunk
whose save has no `cavesandcliffsbackport:Sections` list was written by the 0-255 world. As in 1.18
(`ChunkHeightAndBiomeFix`, `BelowZeroRetrogen`, `ChunkStatus.NOISE`):

| 1.18 | Here |
| --- | --- |
| Retrogen only for chunks with bedrock somewhere at y 0 that are not air all over y 0 | Same; others stay empty below 0 |
| `missing_bedrock`: columns with air at y 0 stay air below 0 | Same |
| Noise, surface rules and carvers only in y -64..-1 (the generation height accessor); carvers up to y -1 | Same (`Region.writeTop`, `Carvers.highest`) |
| Bedrock at y 0..4 becomes deepslate (`replaceOldBedrock`); the surface rules put the new floor at -64 | Same |
| Features write only below 0 (`WorldGenRegion.ensureCanWrite`), in any of the 3x3 chunks | Same, for features of the upgraded chunk |
| Chunks the old version never populated: retrogen to their status, then features at every height | Same (`Upgrade.decorateAll`, 1.12's `TerrainPopulated` false) |
| Biomes below 0: the old column biome, but lush and dripstone caves stay | Same; 1.12 biomes become 1.18's by Mojang's renames (`BiomeMap.upgraded`) |
| Carvers skip what lies within 4 blocks of the old 0..255 box (`addAroundOldChunksCarvingMaskFilter`) | Same (`Blending.CarveMask`) |
| Old blocks the features read from y 0 up | The old chunk's blocks; a block the 1.18 terrain has no code for reads as stone (air, water and lava as themselves) |

The chunk's light is checked again once its neighbors load (`setLightPopulated(false)`), so lava and lichen below 0
light up. Its 3D biomes are saved with it (`cavesandcliffsbackport:Biomes3D`), since they are not the noise's.
Nothing from y 0 up changes but the bedrock rule, in a chunk 1.12 populated.

**New chunks** blend toward old ones within 7 chunks, as `Blender` does: the offset, factor and jaggedness splines
lerp toward the old terrain's heights (`blendOffsetAndFactor`), the density at noise corners right at the border toward
the old blocks' (`blendDensity`, read as `BlendingData.getDensityColumn` reads them), and the biomes toward the old
ones (`blendBiome`, with the same shift noise). The old chunks' data is read from loaded chunks or straight from the
region files (`upgrade.OldChunks`, no chunk is loaded). Differences: the inverse-distance sums visit old chunks in a
fixed order, not fastutil's hash order (last-bit differences); `Blender.generateBorderTicks` is not ported (the
generator's own fluid updates cover the border); a feature of an old chunk being upgraded that crosses into a neighbor
written already is cut there, as any feature crossing into an existing chunk in this generator is.

**Structures** (`upgrade.StructureGuard`). Every 1.12 structure generator makes its starts in
`MapGenStructure.recursiveGenerate`; the coremod hands each new start to the guard there (a mod-compat site: a miss
leaves the class alone and only blocks the upgrade). A start whose chunk is old, or whose bounding box reaches into an
old chunk, is emptied (no pieces; kept so the chunk is not asked again). That covers 1.12's own structures, Backport
Core's `ScatteredStructure`, `ModernJigsaw` and data-driven structures, ancient cities and trial chambers, and other
mods' `MapGenStructure`s; structures that other mods build from an `IWorldGenerator` of their own are not covered.
Starts saved before the upgrade are read from the save and go on as before.

**Tests.** `sh tools/height/upgrade.sh -Pcore_version=1.4.5` (`UpgradeSelfTest`, three servers on one world):
`upgrade1` makes a Default world, a 12 x 12 chunk area around a village with known blocks, and runs the command;
`upgrade2` checks the old chunks byte for byte from y 0 up, deepslate, caves, ores and bedrock below, the bedrock
hole, new chunks at full height, the border, the structures and the command's refusal; `upgrade3` restarts and
checks every chunk loads as it was saved and none is upgraded twice. Results (2026-11-23): 144 old chunks upgraded as
they loaded in 3.2 s; 9,326,498 blocks from y 0 up the same, 110,686 bedrock blocks now deepslate, none else changed;
below 0, 1.45 million deepslate, 12,660 deepslate ores, bedrock at -64 in every column but the hole's. At the border
the new terrain's ground is 0.3 blocks from the old on average (the unblended 1.18 terrain 1.9). 16 structure starts
since the upgrade: 12 built, all clear of the old chunks; 4 emptied; the village saved before the upgrade keeps its
pieces.
