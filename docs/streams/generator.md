# Stream G: the 1.12 chunk generator (1.18 terrain in Caves & Cliffs worlds)

Package `terrain` (Forge side) on top of `terrain/core` (plain Java, `docs/TERRAIN_CORE.md`) and the height coremod
(`docs/EXTENDED_HEIGHT.md`, `docs/streams/height.md`). Branch `cc-generator`.

A Caves & Cliffs world (`level-type=cavesandcliffs`, or the world type on the creation screen) created from now on
gets 1.18.2's overworld from y -64 to 319: terrain, caves, aquifers, ore veins, biomes, surface, carvers and every placed
feature the core has ported, block for block what the core computes, which is vanilla 1.18.2's.

## Changelog section

*   **1.18 terrain in Caves & Cliffs worlds.** New Caves & Cliffs worlds generate 1.18's overworld: mountains up to
    y 319, deepslate and the deep caves down to bedrock at y -64, cheese, spaghetti and noodle caves, aquifers, ore
    veins, 1.18's biomes and their surfaces, geodes, dripstone and lush caves where 1.18 puts them. Seeds give 1.18's
    terrain and biomes, and villages, temples, monuments, mansions, mineshafts and strongholds start where 1.18 starts
    them (their buildings are 1.12's). With the family's backports installed, shipwrecks, ocean ruins, buried
    treasure, ruined portals and pillager outposts start where 1.18 starts them too.
*   **The terrain meets the structures** as 1.18's does: the ground is shaped under and around village houses and
    pillager outposts, and strongholds are buried in rock.
*   **Lush caves and dripstone caves spawn their own mobs**, under any biome: bats, glow squid and cave monsters, no
    animals; axolotls in 1.18's lush caves.
*   **Stopping the server changes nothing.** The chunks the generator holds ahead of the explored area are saved with
    the world, so features crossing its edge are not cut after a restart.
*   Generation is fast: the terrain is built on worker threads ahead of need (`height.terrainThreads`, 0 picks a
    number from the computer's cores).
*   **Quark's world generation works in Caves & Cliffs worlds**: marble, limestone, jasper, slate and its granite,
    diorite and andesite clusters, underground biomes and varied dungeons (they hang on 1.12's ore and dungeon
    events, which now fire there). Where Quark places its own granite, diorite and andesite, the 1.18 blobs of them step
    aside, as 1.12's do in a Default world.
*   **Springs, waterfalls and lava falls flow.** Water and lava springs in cave walls and the edges of underground
    lakes and lava pools start flowing when you come near, as in 1.18, instead of staying still.
*   Caves & Cliffs worlds created before this version keep the terrain they were made with (1.12's, with stone below
    y 0), so no seam runs through them.
*   **Newer biomes with the family's backports.** With Wild Update Backport installed, new worlds get 1.19's mangrove
    swamps (mud, mangrove trees with their roots and hanging propagules) and the deep dark, where 1.19 puts them; with
    Trails & Tales Backport, 1.20's cherry groves (cherry trees, pink petals). Seeds match 1.19 and 1.20. A world keeps
    the biomes it was made with (`height.newerBiomes` turns this off).
*   **Bee nests on 1.18's trees have their bees** with Buzzy Bees Backport, and the backport no longer adds a second
    nest in these worlds.
*   **1.21.5's spring plants with Tricky Trials Backport**: bushes, firefly bushes, dry grass, wildflowers (meadows
    too), leaf litter under forest and dark forest trees, fallen trees and flowered cacti grow in these worlds, where
    1.21.5 puts them. Without Tricky Trials the world is 1.18.2's exactly.

## How it works

**The generator** (`ModernGenerator`, an `IChunkGenerator`). `generateChunk(x, z)`:

1. `Pipeline.finish(x, z)` (core): the chunk with every placed feature of the 3x3 chunks around it applied (a chunk's
   features write into its 3x3 and read its 5x5, so a chunk is final once its 3x3 is decorated). The fill (with the
   beardifier of the structures near it, see Structure terrain), biomes, surface and carvers of each chunk run on a pool
   of worker threads; `finish` asks for the whole 7x7 at once, nearest first, and runs any fill no worker has started
   yet itself. Decoration runs on the server thread in 1.18's step order (`Decorator`), each chunk once: a chunk that
   settled (or that the world already holds, from an earlier run) is never decorated again.
2. The finished codes go straight into the chunk's sections (`SectionWriter`: each distinct state into the section's
   palette through vanilla's `set`, then every block's palette index into the bit array, block and tick counts set
   once), at the height coremod's slots (`HeightLayout.slotBase`). Codes become 1.12 states through `StateMap`.
3. The column biomes: 1.18's biome at each column's top block (the core's zoom, `BiomeZoom`), as a 1.12 biome
   (`BiomeMap`). The chunk's 3D biomes (1.18's stored quarts) are kept while it is loaded (`ChunkBiomes`).
4. 1.12's structure generators record their starts (`ModernStructures`), then `generateSkylightMap`.
5. The workers are told to fill the chunks within 4 of this one (`Pipeline.prefetch`).

`populate(x, z)` does what 1.12 still owns: the structures' pieces and world-generation animals. No 1.12 lakes,
dungeons, ores, trees or ice: the 1.18 features placed all of that. Forge's populate events fire as usual and other
mods' `IWorldGenerator`s run after it as in any world.

**Order.** Vanilla 1.18 decorates chunks in whatever order its chunk system reaches them, and a feature that crosses a
chunk border sees what neighbours decorated before it; so does this generator, in the order 1.12 asks for chunks.
`PipelineBench` checks that the result does not depend on the number of worker threads.

**Memory and restarts.** A held chunk is about 225 KB (384 x 256 codes, heightmaps, 3D biomes). Chunks are dropped
once nothing can read them again (finished, and every chunk within two decorated: settled); beyond a cap (a sixteenth
of the heap, 256 to 2048 chunks) the least recently used go first. A held chunk that decoration wrote into goes to the
world's held-chunk store when it is dropped above the cap and whenever the world saves (`HeldStore`:
`<world>/cavesandcliffsbackport_terrain/r.x.z.mca`, region files deflated as 1.12's, written by one thread of their
own); asked for again, it comes back as it was. A chunk that settles gets a one-byte entry in place of its data, so
the files stay about the size of the area held at once. Chunks only filled are not stored (the noise gives them
again). The one piece of decoration state that is not a chunk's, the order glow lichen's direction list is in
(vanilla's `MultifaceBlock.DIRECTIONS`, shuffled by every spread in the JVM), is saved beside them (`state.dat`).
So a world stopped and started again decorates the chunks beyond its explored area exactly as one that never
stopped (`RestartSelfTest`), and the cap no longer cuts features.

**Fluid ticks and post-processing** (`core/Marks`, `core/FluidMarks`, `FluidUpdates`). What vanilla 1.18.2 does to a
generated chunk after its blocks are placed, none of it a block change during generation:
- *Scheduled fluid ticks, delay 0* (`WorldGenLevel.scheduleTick(pos, fluid, 0)`): `SpringFeature` after placing its
  fluid; `GeodeFeature` at every fluid next to a crack it opens. (`LakeFeature` also schedules a tick of the air block
  in a lake's top half, which does nothing; `StructurePiece` and the Nether bridge schedule their placed fluids: 1.12's
  structures place their own.)
- *Post-processing marks* (`ChunkAccess.markPosForPostprocessing`), answered by `LevelChunk.postProcessGeneration` when
  the chunk starts ticking: the fluid there ticks (`FluidState.tick`), any other block gets
  `Block.updateFromNeighbourShapes`. Marked by `NoiseBasedChunkGenerator.doFill` and `WorldCarver.carveBlock` (a fluid
  the aquifer answered with `shouldScheduleFluidUpdate`), `SurfaceSystem`'s block column (every fluid the surface rules
  set: the swamps' water, water over holes in oceans), `WorldGenRegion.setBlock` of a block with `hasPostProcess`
  (brown and red mushrooms, magma: `BlockCodes.F_POST`), `Feature.markAboveForPostProcessing` (the two blocks over a
  lake's air and barrier and over a disk), glow lichen (`GlowLichenFeature` and its spread). `Blender`'s marks are for
  chunks from older versions; there are none.
The decoration's marks are recorded by `Region.mark` / `markAbove` into each chunk's `Marks` (kept in the held-chunk
store, format 2), the surface's on the worker. The aquifer's are found after the fact: for every fluid the aquifer
answers, the flag is the same function of the position (the two nearest aquifer centers' squared distances differ by
at most 44, i.e. similarity at least FLOWING_UPDATE_SIMULARITY; false below y -54, where the global lava answers first),
so `FluidMarks.scan` computes it only for the water and lava sources of the finished chunk that have a neighbor below
or beside they can flow into (anything not their own fluid that does not block motion; for lava, water only below).
Marks that cannot change anything are left out the same way (an enclosed fluid's tick does nothing; rock, ore and dirt
keep their shape): what is kept is a fluid that can flow, sand or gravel over air or fluid, blocks that are not full
obstacles (plants, glow lichen) and the `hasPostProcess` blocks. In seed 42's 12x12 at 0, 0 that is 497 positions, 3.5
per chunk (17 lava and 7 water springs, 58 exposed aquifer and surface fluids, 243 glow lichen, 169 magma); 30 or
fewer in any chunk of four test areas.

1.18 runs both once the 3x3 chunks around are complete (`ChunkMap.prepareTickingChunk`). Here the generator hands the
chunk's list to `FluidUpdates`, which runs it at the end of the world tick after the chunk and its eight neighbors are
loaded and the four populations that write into it are done; until then the list waits with the chunk (in its NBT,
`cavesandcliffsbackport.PendingUpdates`, if it is saved first). Per position: a still or flowing liquid gets
`BlockDynamicLiquid.updateTick` at once, on the still state (it spreads the source down and to the sides as
`FlowingFluid.tick` does and leaves the source still and unmixed, as 1.18's tick does); a plant that cannot stay is
removed without a drop (1.18's `updateShape` gives air); anything else gets a neighbor update (sand and gravel over a
lake fall after 2 ticks, as `FallingBlock.updateShape` schedules them; glow lichen drops faces without support).

**Other mods' decoration events** (`ModDecoration`). 1.12's population and biome decorator fire Forge events that
other mods generate in; the 1.18 features replace both, so none fired. `populate` now fires, once per chunk after the
structures and before the animals, the ones the 1.18 terrain has a counterpart for, in 1.12's order and at 1.12's
positions (the chunk corner; the biome's decorator holds that `chunkPos` meanwhile): `PopulateChunkEvent.Populate`
DUNGEON, `DecorateBiomeEvent.Pre`, `OreGenEvent.Pre`, `OreGenEvent.GenerateMinable` DIRT, `OreGenEvent.Post`,
`DecorateBiomeEvent.Post`. The answers are not used (1.18 placed its own monster rooms and dirt). They get a random of
their own, seeded from the chunk, so the animals after them are the same whatever draws from it. Not fired: the other
ore types (a mod that denies one to place its own would place it beside 1.18's ores) and `DecorateBiomeEvent.Decorate`
(Biomes O' Plenty grows its trees in TREE; see Dynamic Trees under pack compatibility). Each Quark world feature, in
r1.6-179 and QuarkRotN r1.6-192 alike:
- the stone revamp (`RevampStoneGen`: marble, limestone, jasper, slate, and granite, diorite and andesite clusters) and
  the underground biomes (`UndergroundBiomes`) run in the DIRT `GenerateMinable` on the ore bus, with the event's world
  and chunk; they write the 1.12 decoration window (+8) and keep their configured y ranges (stones 20-80, or to 128 for
  granite, diorite and andesite in the packs; underground biomes 10-40; nothing below 0). The stones replace only stone,
  so they sit in the stone above y 0 and never in deepslate. The underground biomes decorate with `world.rand`, so a
  second DIRT event for a chunk would decorate it twice: it fires once;
- varied dungeons (`VariedDungeons`) run in DUNGEON with the event's random;
- speleothems, crystal caves, cave roots, monster boxes, the archaeologist, fairy rings, pirate ships and clay are
  `IWorldGenerator`s and already ran; big swamp mushrooms (`DECORATE` BIG_SHROOM, which current Forge fires without a
  position) and bigger caves (experimental, off) are not used.
`RevampStoneGen` denies 1.12's GRANITE, DIORITE and ANDESITE ore events for each of those stones it generates itself;
`ModDecoration.quarkStones` asks it the same (`ModuleLoader.isFeatureEnabled`, `StoneInfo.enabled`) and the decoration
gate (`Decorator.TreeGate` kinds GRANITE, DIORITE, ANDESITE) leaves out 1.18's `ore_granite_*`, `ore_diorite_*` and
`ore_andesite_*` in those worlds, every other feature keeping its own seed. Backport Core's duplicate rules (Quark's
basalt off beside the Nether backport's) are untouched: they are Core's.

**Biomes for 1.12** (`BiomeMap`). A biome some installed mod registers under the 1.18 name (any namespace but
`minecraft`) is used first; otherwise the nearest 1.12 biome: the peaks and slopes are 1.12's ice mountains (goats spawn
there), stony peaks extreme hills, meadow plains, grove cold taiga, the warm and cold oceans ocean, the deep ones deep
ocean. `ModernBiomeProvider` answers for columns no chunk has yet (1.12 structures' biome checks, the spawn search,
other mods): 1.18's biome at the column's preliminary surface level, as 1.18's structures read it.

**Newer biomes** (`terrain/core/Editions`). The terrain is 1.18.2's; a world made while a backport with the blocks is
installed gets that version's additions, saved with the world (`Biomes` in `data/cavesandcliffsbackport_terrain.dat`:
"1.18", "1.19", "1.20", "1.20-cherry"), so adding or removing a backport later puts no seam through it:
- **WILD** (Wild Update Backport's mud and mangrove wood registered): 1.19.2's mangrove swamp and deep dark. The biome
  builder's 1.19 lines (the swamp line split at the warm temperatures, the deep dark as a bottom biome at depth 1.1),
  1.19's swap of badlands and eroded badlands in `pickBadlandsBiome`, 1.19's terrain numerics (ridges_folded and the
  offset's -0.50375 in double, `lerp(alpha, 10, factor)` with its rounding: the terrain differs from 1.18.2's by a
  rounding here and there), 1.19's aquifers (none in the deep dark's region; the lava test on the chosen level), 1.19's
  surface rules (mud floor and under-floor in the mangrove swamp, its water from y 60), FeatureSorter's 1.19.2 order,
  1.19's structure biome tags (mangrove swamps get mineshafts and swamp ruined portals; the stronghold tag's change
  does not matter here). The deep dark spawns nothing.
- **CHERRY** (Trails & Tales Backport's cherry wood and pink petals): 1.20.1's cherry grove in `PLATEAU_BIOMES_VARIANT`,
  1.20's RTree (6 children per node, which moves ties), 1.20's aquifer sampling order, the cherry grove's tags
  (mineshafts, outposts, mountain ruined portals). Alone, the swamp keeps 1.18.2's line and FeatureSorter's order is
  1.20.1's over its biomes without the two 1.19 ones.
- Features ported for them, from Mojang's code: `trees_mangrove` (UpwardsBranchingTrunkPlacer, MangroveRootPlacer with
  muddy roots and moss carpets, 70-try random spread leaves, 1.19's leave vines, AttachedToLeavesDecorator's hanging
  propagules, the beehive last), `disk_grass` (1.19's DiskFeature on mud), `trees_cherry` (CherryTrunkPlacer,
  CherryFoliagePlacer with hanging leaves, bees 0.05), `flower_cherry`. 1.19's dirt tag holds mud and muddy roots.
  The deep dark's sculk features are not placed: no backport has sculk yet.
- 1.12 biomes: a mangrove swamp is 1.12's swamp, the cherry grove plains (as the meadow), the deep dark plains for 1.12
  code that asks (it is always underground), unless a mod registers a biome under the modern name.

**Decoration the generator does itself** (Backport Core 1.2.0 `DecorationSource`). `placesFeature(world, name)` is true
for every placed feature the world's biomes list and every block the core's features put down; a backport's 1.12 hook
for the same thing steps aside (Buzzy Bees' populate nest scan). `DecorationSource.places(world, "bee_nest")`.

**Block states** (`StateMap`). Each code is a 1.18.2 state; it is resolved once: a table of the vanilla blocks whose
1.12 name or variant differs (`grass_block` is `grass`, `granite` is `stone[variant=granite]`, slabs' `type` is `half`
or a double slab block ...), else a block registered under the 1.18 name (this mod's first, then vanilla's, then any
other mod's: the family's backports register 1.13+ blocks under modern names), properties copied by name. Glow lichen
and small dripleaf have their own mapping; huge mushroom blocks turn 1.18's face mask into 1.12's compass variant
(`north_west`, `center`, ...; a mask 1.12 cannot name is `all_outside`, a stem with top and bottom `all_stem`). `waterlogged=true` (and water plants) becomes a flag: the water is placed
through Backport Core's waterlogging when the chunk is loaded into the world (`ModernTerrain.onChunkLoad`). Chests get
the simple dungeon loot table and spawners a dungeon mob; a bee nest (1.18's beehive decorator, Buzzy Bees' block) gets
2 or 3 bees in 1.15's `Bees` format (`Nests`: an entity id each, 0-598 ticks into the stay, 600 minimum; the counts
from a random of the nest's position, as the core keeps vanilla's draws but not their values). The newer biomes'
blocks stand in as dirt (mud, muddy roots), jungle and birch wood and leaves, air (roots, propagules, petals) only if
their backport is removed from a world that has them. Without a backport that adds them: blue ice is packed ice, tuff andesite, calcite diorite;
cornflower a blue orchid, lily of the valley an oxeye daisy, a sweet berry bush a fern; bamboo and bee nests nothing
(no 1.12 block stands in for them); kelp, seagrass, sea pickles, coral and coral fans the water they stand in; coral
blocks terracotta of their color (tube blue, brain pink, bubble magenta, fire red, horn yellow).

**Structures** (`ModernStructures`). Each 1.12 structure generator's start check asks the core's `Structures`: 1.18.2's
placement, the structure chosen and its own start checks, so starts are where Chunkbase shows them. Villages
(`MapGenVillage`; houses set on the ground at layout, see Structure terrain), desert pyramids, jungle temples, swamp huts and igloos (`MapGenScatteredFeature`, the piece by set;
the pyramid is moved from 1.12's fixed y 64 to the lowest corner of its footprint), ocean monuments, mineshafts (mesa
ones in badlands), strongholds (the 128 ring positions) and woodland mansions (`MansionStart`, 1.12's pieces on our
corner heights). `/locate` and explorer maps search the same starts (growing squares; strongholds the rings). Pillager
outposts, buried treasure, ruined portals, shipwrecks and ocean ruins have no 1.12 pieces: the family's backports
build them, where this generator says (below).

**Village & Pillage's villages** (branch `cc118-villages`; Core's `StructureGenerators`). The generator offers its
village generator to Backport Core before using it; with Village & Pillage installed (villages.style MODERN) its 1.14
jigsaw villages take 1.12's place: they start where the start rule says (1.18.2's village set and its type, plains,
desert, savanna, snowy or taiga), are laid out on this generator's WORLD_SURFACE_WG, register with `TerrainAdaptation`
as beard_thin (rigid houses beard, streets follow the ground; no junctions), and answer `/locate` and `/locate
Village`. 1.12's villages are then neither laid out nor adapted to. The generator is also Core's `FeatureSource`
("1.18.2"), so Village & Pillage's sweet berry, flower and jungle bamboo generators step aside: the 1.18 features place
its blocks (`StateMap` by modern name, preferring the copy Core's duplicate hiding keeps).

**The family's structures** (Backport Core's `StructureSource`, `docs/CORE_REQUESTS.md` 12). The generator gives Core a
start rule for shipwrecks, ocean ruins, buried treasure, ruined portals and pillager outposts: 1.18's grid for the
set, and at a chunk the core's `Structures.startAt` (placement, the biome at the start's height, the weighted choice
of the set's structures). Every `ScatteredStructure` (Update Aquatic's shipwrecks, ruins and treasure, the Nether
backport's ruined portals) follows it in its start check, nearest search and `/locate` grid, and picks the variant
the rule names (beached or sunken, warm or cold, the portal's type); Village & Pillage's outposts ask the same
(`StartRules`). Their pieces stay their own.

**Structure terrain** (`StructureBeards`, a `Pipeline.BeardSource`). 1.18.2 adds the beardifier to the fill around
the pieces of structures with `adapt_noise`: villages and outposts (beard, `beard_thin` in today's terms) and
strongholds (bury). 1.12 lays a start out when the chunks around it are generated and builds it when they are
populated, after the terrain. So when the pipeline first asks for a chunk (on the server thread, before its fill is
queued), the village and stronghold starts within eight chunks are laid out (`ModernStructures.laidOut`: the
generator's own `generate`, each start from its own seed, so nothing changes but when), and every start whose box
grown by 12 blocks reaches the chunk gives its pieces to `Beardifier.forChunk`, in 1.18's order: villages, then
strongholds, then the kinds other backports register with Core's `TerrainAdaptation` (Village & Pillage's outposts:
their jigsaw pieces with projection and ground level delta); within a structure its starts in the order 1.18 lists a
chunk's references (fastutil's `LongOpenHashSet` over the start chunks in `createReferences`' scan order,
`ModernStructures.referenceOrder`); each start's pieces in order. (Between structures vanilla's order is a hash
map's over identity hashes, not reproducible; one start almost always reaches a chunk alone.)
- 1.12's village houses measure the ground as they build, so their height depended on which chunks existed. Here
  each house, lamp and well is set on the ground when the village is laid out: 1.12's own rule (the average over its
  footprint of the first free block, at least the world's average ground level less one, the box moved by the
  class's own offset), read from the bare noise (WORLD_SURFACE_WG). It is then a rigid jigsaw piece to the beard,
  its ground at that level, as 1.18's houses are. Roads follow the ground block by block (1.18's terrain-matching
  pieces have no beard). Other mods' village pieces keep measuring as they build and get no beard.
- Stronghold pieces are buried (1.18's `StrongholdPiece` noise effect).
- 1.19's `beard_box` and `encapsulate` (ancient cities, trial chambers) have no 1.18.2 formula; a kind registered with
  them gets none (logged once). There are no ancient cities in 1.18.2.

**Mob spawning** (`ChunkBiomes`). `getPossibleCreatures` reads 1.18's biome at the spawn position in three dimensions
(the zoom over the chunk's stored quarts, as 1.18's spawner reads `getBiome`): under a desert, a lush cave spawns
what 1.18's lush caves do. The two cave biomes get 1.18.2's lists (`commonSpawns`: bats, glow squid in their own
group, spider, zombie, zombie villager, skeleton, creeper, slime, enderman, witch; drowned in dripstone caves;
tropical fish in lush caves where a mod adds a water ambient group; no animals), resolved against the mobs
installed, unless a mod registers a 1.12 biome under the cave biome's name (then its lists). Other positions get
their 1.18 biome's 1.12 lists. The axolotls' spawner and every other server-side `CaveRegions.lush` / `dripstone`
question in these worlds answer from the same 3D biome.

**Backport Core** (1.2.0 from its `cc-structures` branch, on `cc-heights`; `deps/BackportCore-1.2.0-dev.jar`). The generator is a
`TerrainSource`: `TerrainHeights.forWorld` gets a `ColumnTerrain` that answers from the core's noise (1.18's
WORLD_SURFACE_WG and OCEAN_FLOOR_WG of the bare terrain, `ChunkFiller.baseHeight`), so 1.14-style villages and other
planned structures stand on our ground.

**Other backports' generators** (Backport Core's `ModernGeneration`, 1.2.0): the generator says it runs 1.18.2's
generation in full (`generationVersion()`), and gives 1.18's biome at a block in three dimensions (`biomeId`, the
spawner's zoom). Update Aquatic's coral, kelp and seagrass generators step aside in these worlds (the core places
1.18.2's own with its blocks), and its ocean temperature, fish and drowned rules read `biomeId`. Kelp: a backport's
kelp with fewer ages than 1.18's 0-25 (Update Aquatic's 0-15) gets the age that leaves it as many growth steps
(`StateMap`: 1.18's generated 20-23 are 10-13).

**The 1.17 generators step aside** in these worlds (`ModernTerrain.owns`): geodes, copper ore, dripstone and lush cave
regions, powder snow patches and the deepslate layer.

**The spawn point** (`WorldEvent.CreateSpawnPosition`): the nearest chunk centre to 0, 0 whose bare terrain is dry
land above the sea, found from the noise alone (1.12's search would generate chunks until it finds grass). 1.12's
bonus chest is not placed then.

**Other backports' newer placed features** (`ModernFeatures`; Tricky Trials' `docs/CORE_REQUESTS.md` #11). The
family's rule is that the newest installed backport's version wins, so with Tricky Trials (1.21.x) installed its 1.21.5
spring plants belong in these worlds; without it nothing below runs and decoration is 1.18.2's exactly (FeatureCheck,
TerrainCheck: ALL IDENTICAL). Tricky Trials' code takes a `World`, and the core decorates held chunks before they are
in the world, so the core leaves requests in the chunk's `Marks` (kinds from `Marks.REQUEST`: `FALLEN` + the fallen
tree, `LITTER`, `LITTER_2X2`, `VEGETATION`), which wait with the chunk like the fluid ticks (held store, chunk NBT)
and run in `FluidUpdates` once the chunk's 3x3 is loaded, after 1.18's own updates. All calls go by name through
reflection, null-safe without Tricky Trials (or with its `flora.enabled` off):
*   **Tree selectors** (`Decorator.modernTrees`, `Trees.modern`): each of 1.18.2's tree features named in Tricky
    Trials' `FallenTrees.SELECTORS` gets 1.21.5's entries at its chances, 1.18.2's own entries kept in their order
    (checked: same chances; a selector that does not match is left as it was and named in the log). The forest's
    `trees_birch_and_oak` takes 1.21.5's `trees_birch_and_oak_leaf_litter`; `trees_birch`, `trees_snowy` and
    `trees_badlands`, single trees in 1.18.2, become selectors with their fallen tree first. A fallen tree that is
    picked and whose sapling would survive is a `FALLEN` request (`FallenTrees.place`); a forest or dark forest tree
    (not a huge mushroom) that stands is a `LITTER` request at its foot (a dark oak's 2x2: `LITTER_2X2`;
    `FallenTrees.leafLitter`). The placements are 1.18.2's; the selector draws one float per new entry, so with Tricky
    Trials the trees after it differ, as 1.21.5's do from 1.18.2's.
*   **Cactus patches**: the core's tree gate leaves out `patch_cactus_desert` and `patch_cactus_decorated` (kind
    CACTUS) and Tricky Trials places its own (`FloraFeatures.place`), the column with its 1-in-4 flower; Dynamic
    Trees' cactus answer is asked again first.
*   **Vegetation** (`VEGETATION`, one per chunk, last): every patch of every biome on the chunk's surface (Tricky
    Trials' `FloraBiomes` lists by its 1.21.5 biome names, merged into one order that keeps each list's), once each,
    from the chunk corner, through `FloraFeatures.place`; each patch filters by the biome at its position, as
    vanilla's union of the 3x3's biome features does. For a chunk of one biome this is `FloraFeatures.vegetation`.
    The random is the world seed's and the chunk's.
*   Tricky Trials' own 1.12.2 path (`FloraGen`, on `DecorateBiomeEvent.Post`, which `ModDecoration` fires for other
    mods) is left out of that event in these chunks (its listeners, read from the event bus, are skipped), so nothing
    grows twice. Before this, that path did run here and grew the 1.12.2 version of the plants (corner + 8 squares,
    fallen trees as extra attempts, flowers on the 1.18 cacti).
*   Not 1.21.5's random for random: the patches and fallen trees take their own random after the chunk is in the
    world (1.21.5 draws them inside the step, so its later features shift), and a fallen tree or litter is placed
    after the whole chunk is decorated rather than in its place in the step.

**Which worlds** (`ModernTerrain`): the overworld of a world with extended height, decided once and saved in
`data/cavesandcliffsbackport_terrain.dat` ("1.18", or "1.12" for a Caves & Cliffs world whose `region` folder already
had chunks when this generator first saw it).

## Tested

*   `sh tools/height/selftest.sh height,terrain` (fresh `cavesandcliffs` world, seed 42): the height test's checks
    (with 1.18 terrain below 0 instead of solid stone) and `TerrainSelfTest`: every state the core makes has a 1.12
    block; a chunk far from anything generated holds exactly the core's blocks, -64 to 319, against a second pipeline
    on one thread; bedrock at -64, deepslate below 0, every column a 1.12 biome; `/locate` for villages, temples,
    monuments, mansions, strongholds and mineshafts answers starts the core confirms; Backport Core's `TerrainHeights`
    answers the generator's surface; the spawn is on dry land.
*   `sh tools/terrain/run.sh PipelineBench 42 16 <threads>`: the same checksum over 256 finished chunks with 0 and 8
    workers.
*   `TerrainGapsSelfTest` (in `height,terrain`): a village's houses are on their footprint's noise ground and the
    fill with the generator's beard puts solid ground under a house and air over it (15 of 15 columns; 2 without the
    beard); every chunk a stronghold touches gets a bury beard (at least 1.0 at all 184 piece floors); the reference
    order is fastutil's (2,000 random sets against a real `LongOpenHashSet`); Core's start rules name exactly the
    core's 1.18 starts (61 shipwrecks, 96 ocean ruins, 6 buried treasures, 64 ruined portals, 3 outposts) on 1.18's
    grids, and a `ScatteredStructure` follows them (starts, variant, nearest); the 3D biome is the core's zoom;
    lush and dripstone caves under other biomes spawn bats, zombies and glow squid and no animals, and the surface
    above its column biome's animals; `CaveRegions` answers 1.18's caves; a held chunk survives its bytes exactly.
    With Update Aquatic, the Nether backport and Village & Pillage from their `cc-structures` branches in
    `eclipse/mods`: their ruins and portals have 1.18's variants (96 and 64, 0 wrong), an outpost's chunk gets a
    beard, and Core's `/locate` for shipwreck, both ocean ruins, buried treasure, ruined portal (and desert) and
    pillager outpost each names a 1.18 start of it.
*   `sh tools/height/selftest.sh terrainocean` (`TerrainOceanSelfTest`; seed -5767635369185270459 in
    `eclipse/server.properties` adds the plain ocean at -700, -869): five areas of 5x5 chunks (that ocean, and a warm,
    lukewarm, cold and frozen ocean) generated and populated by the world, every mod's world generators included,
    against a second pipeline finishing the same chunks in the same order, every block from -64 to 319. Allowed:
    blocks inside structure starts' boxes, glow lichen (its shuffled direction order), bubble columns on magma.
    This mod alone: 16 pass, 0 fail (0 other blocks wrong). With Update Aquatic `main` in `eclipse/mods`: 5 fail
    (coral 8762 against 1.18's 3415, kelp 8600 against 5315, seagrass 10049 against 5705, 116 coral blocks in the
    plain ocean's chunk, 2447 far from any warm ocean, `OceanKind` wrong at 292 samples). With Update Aquatic
    `cc118-terrain`: 20 pass, 0 fail (coral 3415, kelp 5315, seagrass 5705, exactly 1.18's; 415 bubble columns, all
    on magma).
*   Fluid ticks and post-processing (`TerrainFluidSelfTest`, in `height,terrain`): 9x9 chunks generated at chunk
    -250, 260; 39 of the inner 49 wait with updates until their neighbors load, and a ring chunk's list is written into
    its chunk NBT; after the world tick's run every inner chunk's list is gone, 8 of 8 lava and water springs flowed
    into their hole and 8 of 8 aquifer edges (marks without a spring) flowed. `MarkStats <seed> <cx> <cz> 12` over four 12x12
    areas (seeds 42 twice, 12345, 8675309): 3.5 to 7.1 positions per chunk, at most 34 in one chunk. TerrainCheck: ALL
    IDENTICAL; `PipelineBench 42 16 0`: checksum d70ea2ca54f75550, unchanged.
*   Other mods' decoration events: the family rig (`docs/streams/packcompat.md`, fixed item 6), Quark r1.6-179 and
    QuarkRotN r1.6-192 in Caves & Cliffs and Default worlds. `TreeGateCheck 42 8`: with the stone kinds denied no
    diorite or andesite is left (114,434 without) and the trees are unchanged. (Its tree check fails at seed 42, side 8
    on `main` as well: 226 leaves above y 40 with every tree denied.)
*   `sh tools/terrain/restart.sh` (`RestartSelfTest`, three servers in a Caves & Cliffs world): 12x12 chunks at
    2000, 2000 and then the 12x12 east of them in one run, against the first area, a stop, and the second area after
    the restart: every finished chunk (305, with the few populating generates) identical. Without the store (the
    same test with `setStore` left out): 110 differ.
*   The whole suite, `./gradlew runServer -Pselftest=all` in a fresh Default world (seed 1718): 4481 pass, 0 fail; with the gaps closed 4482 pass, 0 fail (one earlier run of the same code failed the
    goat test's jump cooldown by one tick, 299 for at least 300; the next passed).
    (On seed 42 the lush test's moss check fails 3 times in a Default world: 1.12's population snows on its test
    floor. Not this stream's; the suite's seed is 1718.)
*   `sh tools/height/selftest.sh`: 127 pass, 0 fail; with `height,terrain`: 150 pass, 0 fail; with the gaps closed 204 pass, 0 fail (88 for `terrain` alone with the
    three family backports installed).
*   `./gradlew runClient -Pclienttest=height`: a single-player Caves & Cliffs world, now with the 1.18 generator:
    18 pass, 0 fail.
*   The release jar in a plain Forge 2860 server (the head-to-head below) generates and saves without errors.
*   `TerrainCheck`: ALL IDENTICAL.

*   **Newer biomes** (`tools/terrain/oracle-newer`, vanilla 1.19.2 and 1.20.1 from Mojang's client jars and mappings,
    decompiled with ForgeFlower, run on their libraries): `NewerBiomeCheck` compares every OverworldBiomeBuilder
    parameter point (7,593 leaves for 1.19.2 and 1.20.1, 7,578 for 1.20 without the Wild biomes: identical) and the
    biome of every quart of 1,452 chunks per seed on four seeds, cold searches (8.9 million quarts per version: ALL
    IDENTICAL). `TerrainCheck -Deditions=1` and `=3` against 1.19.2's and 1.20.1's noise fill (`FillDump119`,
    `FillDump120`, 912 chunks: ALL IDENTICAL). `compare_newer.py`: 1.18.2's biomes keep every feature in 1.19.2 and
    1.20.1, FeatureSorter's 1.19.2 order without the two new biomes is 1.18.2's exactly. Without the backports:
    TerrainCheck and BiomeCheck ALL IDENTICAL, FeatureCheck (`fdef`, 2,048 chunks) ALL IDENTICAL.
*   `NewerBiomesSelfTest` (in `terrain`): with Buzzy Bees, Wild Update and Trails & Tales in `eclipse/mods` the world
    gets 1.20's biomes; a mangrove swamp near 0, 0 has mud (1,999 in 3 x 3 chunks), mangrove logs, leaves, roots and
    muddy roots and hanging propagules, the deep dark spawns nothing, a cherry grove has cherry logs, leaves and pink
    petals, meadow nests have 2-3 bees and the world holds exactly the core's nests (no second one from Buzzy Bees);
    the generator says it places bee nests, and mangrove and cherry trees only with their biomes. `height,terrain`
    without them: 212 pass, 0 fail. The whole suite (`runServer -Pselftest=all`, fresh Default world): 4482 pass, 0 fail.

## Speed

**Newer biomes (2026-11-12, cc118-biomes).** One E-core, `PipelineBench 42 16 0` (`-Deditions`): 1.18.2 16.1 ms/chunk
on main, 16.3 on this branch; with 1.20's biomes (editions 3) 15.6, with 1.19's (1) 15.7. `TerrainBench 42 12 5` 2.17
(main) and 2.26 (branch) ms/chunk, checksum f4cb385d0674f0f7 on both. PipelineBench's checksum hashes block codes, and
the new trees register codes, so its 1.18.2 value moved (d70ea2ca54f75550 to 628033d72d65df32) with the same states
(FeatureCheck identical); with editions 3 it is bb743f03407c7f38 with 0 and with 8 workers.

Measured 2026-11-12 on the 14900K, every run a fresh world.

**Dev server, `python tools/terrain/genbench.py 3`** (EXTENDED_HEIGHT section 4's harness: the `heightbench` self-test,
a 24x24 square of chunks at chunk 1000, 1000 generated and populated after an 8x8 warm-up, then written to NBT; seed
1718; pinned with `start /affinity`):

| Cores | Vanilla 1.12 (coremod off) | 1.18 terrain | vs vanilla's time per chunk |
| --- | --- | --- | --- |
| 16 E-cores, with fluid updates and the decoration events (2026-11-12, cc118-fluids-quark) | 11.94 ms/chunk, 84 chunks/s | **5.38 ms/chunk, 186 chunks/s** | **x0.45** |
| 16 E-cores, with the structure beard, held-chunk store and 3D biomes (2026-11-12, cc-gaps) | 11.95 ms/chunk, 84 chunks/s | **5.24 ms/chunk, 191 chunks/s** | **x0.44** |
| 16 E-cores (FFFF0000; 6 workers), with trees and vegetation | 11.63 ms/chunk, 86 chunks/s | **5.13 ms/chunk, 195 chunks/s** | **x0.44** |
| 16 E-cores, before trees and vegetation | 9.76 ms/chunk, 102 chunks/s | 5.99 ms/chunk, 167 chunks/s | x0.61 |
| 4 E-cores (F0000000; 2 workers, a small machine), before trees and vegetation | 10.43 ms/chunk, 96 chunks/s | **7.20 ms/chunk, 139 chunks/s** | **x0.69** |

Vanilla's run was slower with the trees run than before (11.6 against 9.8 ms/chunk: the machine, not the code, which
is the same for it); ours too varies by about 10% between sessions. Where the server thread's time goes (16 E-cores,
`TERRAINSTATS`, with trees and vegetation): decoration 1.4-1.6 ms per decorated chunk; before them, 1.6-1.9 ms (1.25
decorations per generated chunk in a square), waiting for workers 0.5 ms, writing the 1.12 chunk (sections, structure
starts, sky light) 2.3 ms; the workers' fill + surface + carvers 5.2 ms per chunk on a busy E-core. Saving: 0.5 ms/chunk
against vanilla's 1.1 (vanilla's population leaves scheduled block ticks to write; ours none).

With fluid updates and the decoration events (cc118-fluids-quark): the same within the sessions' spread (5.35-5.52
ms/chunk; decoration 1.40-1.59, write 1.08-1.22; saving 0.47 ms/chunk with the waiting lists in the chunks' NBT). The
marks cost nothing measurable in the pipeline (`PipelineBench 42 16 0` 7.0 ms/chunk on a busy machine, same checksum).
Running the updates happens on world ticks, not in the bench: in the fluid self-test 265 chunks' updates (198 fluid
ticks) took 19.2 ms, 0.07 ms per chunk.

With the gaps closed (cc-gaps; the same session's run of the code before them gave 5.76 ms/chunk median against
vanilla's 11.90, x0.48, on a busier machine): `beard_ms` 1.07-1.16 per generated chunk on the server thread, almost
all of it the stronghold rings' one-time biome search (about 1.4 s once per world, which used to land in the 1.12
write: `write_ms` fell from 2.3-2.6 to 1.0-1.2); laying out and summing the villages and strongholds near each new
chunk is about 0.07 ms. A candidate's structure start check is kept (1,024 per set), so 1.12's six generators and
the beard no longer recompute it for every chunk near it. Settled chunks are no longer filled and decorated again:
2,467 fills and 1,579 decorations for the 1,331 chunks against 2,715 and 1,648 before, 1,360 chunks held at the end
against 2,026. No chunk was stored or restored in the bench (below the cap).

**Head-to-head, `python tools/terrain/pregenbench.py G:/VBPTest/genbench 3`** (plain Forge 14.23.5.2860 servers, release
jars, Chunk Pregenerator's `pregen gen startradius square 300 300 16` (33x33 chunks), seed -3892104814965596348, -Xmx4G,
16 E-cores, wall clock from the command to "Pregenerated"):

| Terrain | Chunks/s (3 runs) | Time per chunk vs vanilla |
| --- | --- | --- |
| Vanilla 1.12 | 49.6 (48.8, 49.6, 49.8); before: 46.9 | 1 |
| **This mod, Caves & Cliffs world, with trees and vegetation** | **57.1** (57.4, 57.1, 56.3) | **x0.87** |
| This mod, before trees and vegetation | 57.2 (56.6, 57.2, 58.3) | x0.82 |
| Caves Not Cliffs 2.0.4 | 9.7 (9.7, 9.7, 9.7) | x4.83 |
| Farmer's Future Delight 1.4.6 (ffd_cac) | 8.8 (8.8, 8.8, 8.7) | x5.33 |

(Chunk Pregenerator paces itself by the tick, so its absolute rates are lower than the dev harness's.)

Targets (EXTENDED_HEIGHT section 4): at least 34 chunks/s and at most 2x vanilla's time per chunk: met on every
measurement, by 4 to 5 times the rate the competing 1.18 terrain mods reach.

**The core alone, one E-core** (`tools\terrain\bench.cmd 8 PipelineBench 42 16 0`: 16x16 chunks finished in a spiral,
fill on the same thread, no Minecraft): 15.0 ms/chunk (67 chunks/s); with the gaps closed 15.5-15.7 against
15.9-16.0 for the code before, in the same session, the same checksum (d70ea2ca54f75550, and with 8 workers);
`TerrainBench 42 12 5` 2.21-2.27 against 2.28 ms/chunk, fill 3.6 ms x 1.9 fills per chunk, decoration
6.2 ms (every placed feature, trees and vegetation included; 0, 0 at seed 42 is dark forest, the heaviest trees). The
same with 8 workers (unpinned): 2.5 ms/chunk, same checksum. (Before trees and vegetation: 11.7 ms/chunk, decoration
3.6 ms.)

## Not done

*   **The newer versions' changes to 1.18.2's own features** are not ported: in a 1.19 or 1.20 world they decorate
    as 1.18.2's (in 1.19's feature order). 1.19 changed DiskFeature (disk_sand gets sandstone over air, the water test
    moved to the placement), LeaveVineDecorator (nextFloat against 0.25 for nextInt(4)), and TreeFeature.validTreePos
    (seagrass is replaceable); a 1.19 decoration oracle would be the next step. The mangroves and cherries are ported
    from 1.19.2 and 1.20.1 line by line but not yet checked against a vanilla decoration dump.
*   **The deep dark has no sculk** (no backport has sculk blocks yet) and no ancient cities; trail ruins (1.20) and
    trial chambers (1.21) have no builder in the family yet, so no start rule is offered for them.
*   **Leaves in water**: 1.19 waterlogs mangrove leaves that grow into water; here they are dry.

*   **Plants and corals without a 1.12 block** stand in as listed under Block states when no family backport adds
    them: bamboo jungles have no bamboo, warm oceans terracotta reefs.
*   **Village pieces are 1.12's** without Village & Pillage, so the beard is 1.12's houses' (no jigsaw junctions:
    1.12's roads do not record where they meet a house). Village & Pillage's villages and outposts are its 1.14
    jigsaw pieces (not 1.18.2's pools) and keep no junctions either.
*   **The family's structures need their `cc-structures` branches** (Core 1.2.0 with `StructureSource`; Update
    Aquatic, Nether Update and Village & Pillage asking it). Their released jars keep their own placement.
*   **Spawn point**: dry land nearest 0, 0 by the noise, not 1.18's climate-based `findSpawnPosition`; no bonus chest.
*   **Clients still hear cave music by the 1.17 regions** (`CaveRegions` on the client has the noise key, not the 3D
    biomes); the server side (spawning, axolotls) uses 1.18's biomes.
*   **A crash** (not a stop) loses what decoration wrote since the last world save into chunks not yet generated.
*   **Decoration runs on the server thread** (about 1.5 ms of the 5 in the dev bench, more in forests). It could run on one more thread ahead of need,
    in the same order; not needed for the targets.
*   **Waterlogged blocks below 0 or above 255** need Backport Core's own store (stream C); until then they are dry.
*   OptiFine and the Dregora packs were not run with the 1.18 terrain (stream H tested the height there).
*   **Fluid updates are decided when the chunk is finished**, before 1.12's population: a marked aquifer fluid that a
    1.12 structure piece (a mineshaft corridor) or another mod's generator opens later stays still, where 1.18's
    structures, placed during decoration, would let it flow. A fluid in a waterlogged block (Backport Core's
    waterlogging) gets no tick.
*   **Quark's big swamp mushrooms** (`DECORATE` BIG_SHROOM) are not fired: current Forge fires that event without a
    position, so they never generate in a Default world either.
