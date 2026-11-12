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
    them (their buildings are 1.12's).
*   Generation is fast: the terrain is built on worker threads ahead of need (`height.terrainThreads`, 0 picks a
    number from the computer's cores).
*   Caves & Cliffs worlds created before this version keep the terrain they were made with (1.12's, with stone below
    y 0), so no seam runs through them.

## How it works

**The generator** (`ModernGenerator`, an `IChunkGenerator`). `generateChunk(x, z)`:

1. `Pipeline.finish(x, z)` (core): the chunk with every placed feature of the 3x3 chunks around it applied (a chunk's
   features write into its 3x3 and read its 5x5, so a chunk is final once its 3x3 is decorated). The fill, biomes,
   surface and carvers of each chunk run on a pool of worker threads; `finish` asks for the whole 7x7 at once, nearest
   first, and runs any fill no worker has started yet itself. Decoration runs on the server thread in 1.18's step order
   (`Decorator`), each chunk once.
2. The finished codes go straight into the chunk's sections (`SectionWriter`: each distinct state into the section's
   palette through vanilla's `set`, then every block's palette index into the bit array, block and tick counts set
   once), at the height coremod's slots (`HeightLayout.slotBase`). Codes become 1.12 states through `StateMap`.
3. The column biomes: 1.18's biome at each column's top block (the core's zoom, `BiomeZoom`), as a 1.12 biome
   (`BiomeMap`).
4. 1.12's structure generators record their starts (`ModernStructures`), then `generateSkylightMap`.
5. The workers are told to fill the chunks within 4 of this one (`Pipeline.prefetch`).

`populate(x, z)` does what 1.12 still owns: the structures' pieces and world-generation animals. No 1.12 lakes,
dungeons, ores, trees or ice: the 1.18 features placed all of that. Forge's populate events fire as usual and other
mods' `IWorldGenerator`s run after it as in any world.

**Order.** Vanilla 1.18 decorates chunks in whatever order its chunk system reaches them, and a feature that crosses a
chunk border sees what neighbours decorated before it; so does this generator, in the order 1.12 asks for chunks.
`PipelineBench` checks that the result does not depend on the number of worker threads.

**Memory.** A held chunk is about 200 KB (384 x 256 codes plus heightmaps). Chunks are dropped once nothing can read
them again (finished, and every chunk within two decorated); beyond a cap (a sixteenth of the heap, 256 to 2048
chunks) the least recently used go first. A dropped chunk asked for again is filled and decorated again; what
neighbours had written into it is then lost (features crossing that border are cut). Held chunks are not saved: after
a restart the chunks next to the explored area are filled again, with the same result except for those crossings.

**Biomes for 1.12** (`BiomeMap`). A biome some installed mod registers under the 1.18 name (any namespace but
`minecraft`) is used first; otherwise the nearest 1.12 biome: the peaks and slopes are 1.12's ice mountains (goats spawn
there), stony peaks extreme hills, meadow plains, grove cold taiga, the warm and cold oceans ocean, the deep ones deep
ocean. `ModernBiomeProvider` answers for columns no chunk has yet (1.12 structures' biome checks, the spawn search,
other mods): 1.18's biome at the column's preliminary surface level, as 1.18's structures read it.

**Block states** (`StateMap`). Each code is a 1.18.2 state; it is resolved once: a table of the vanilla blocks whose
1.12 name or variant differs (`grass_block` is `grass`, `granite` is `stone[variant=granite]`, slabs' `type` is `half`
or a double slab block ...), else a block registered under the 1.18 name (this mod's first, then vanilla's, then any
other mod's: the family's backports register 1.13+ blocks under modern names), properties copied by name. Glow lichen
and small dripleaf have their own mapping. `waterlogged=true` (and water plants) becomes a flag: the water is placed
through Backport Core's waterlogging when the chunk is loaded into the world (`ModernTerrain.onChunkLoad`). Chests get
the simple dungeon loot table and spawners a dungeon mob. Blue ice without a backport that adds it is packed ice.

**Structures** (`ModernStructures`). Each 1.12 structure generator's start check asks the core's `Structures`: 1.18.2's
placement, the structure chosen and its own start checks, so starts are where Chunkbase shows them. Villages
(`MapGenVillage`), desert pyramids, jungle temples, swamp huts and igloos (`MapGenScatteredFeature`, the piece by set;
the pyramid is moved from 1.12's fixed y 64 to the lowest corner of its footprint), ocean monuments, mineshafts (mesa
ones in badlands), strongholds (the 128 ring positions) and woodland mansions (`MansionStart`, 1.12's pieces on our
corner heights). `/locate` and explorer maps search the same starts (growing squares; strongholds the rings). Pillager
outposts, buried treasure, ruined portals, shipwrecks and ocean ruins have no 1.12 pieces: the family's backports
place their own.

**Backport Core** (1.2.0 from its `cc-heights` branch, `deps/BackportCore-1.2.0-dev.jar`). The generator is a
`TerrainSource`: `TerrainHeights.forWorld` gets a `ColumnTerrain` that answers from the core's noise (1.18's
WORLD_SURFACE_WG and OCEAN_FLOOR_WG of the bare terrain, `ChunkFiller.baseHeight`), so 1.14-style villages and other
planned structures stand on our ground.

**The 1.17 generators step aside** in these worlds (`ModernTerrain.owns`): geodes, copper ore, dripstone and lush cave
regions, powder snow patches and the deepslate layer.

**The spawn point** (`WorldEvent.CreateSpawnPosition`): the nearest chunk centre to 0, 0 whose bare terrain is dry
land above the sea, found from the noise alone (1.12's search would generate chunks until it finds grass). 1.12's
bonus chest is not placed then.

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
*   The whole suite, `./gradlew runServer -Pselftest=all` in a fresh Default world (seed 1718): 4481 pass, 0 fail.
    (On seed 42 the lush test's moss check fails 3 times in a Default world: 1.12's population snows on its test
    floor. Not this stream's; the suite's seed is 1718.)
*   `sh tools/height/selftest.sh`: 127 pass, 0 fail; with `height,terrain`: 150 pass, 0 fail.
*   `./gradlew runClient -Pclienttest=height`: a single-player Caves & Cliffs world, now with the 1.18 generator:
    18 pass, 0 fail.
*   The release jar in a plain Forge 2860 server (the head-to-head below) generates and saves without errors.
*   `TerrainCheck`: ALL IDENTICAL.

## Speed

Measured 2026-11-12 on the 14900K, every run a fresh world.

**Dev server, `python tools/terrain/genbench.py 3`** (EXTENDED_HEIGHT section 4's harness: the `heightbench` self-test,
a 24x24 square of chunks at chunk 1000, 1000 generated and populated after an 8x8 warm-up, then written to NBT; seed
1718; pinned with `start /affinity`):

| Cores | Vanilla 1.12 (coremod off) | 1.18 terrain | vs vanilla's time per chunk |
| --- | --- | --- | --- |
| 16 E-cores (FFFF0000; 6 workers) | 9.76 ms/chunk, 102 chunks/s | **5.99 ms/chunk, 167 chunks/s** | **x0.61** |
| 4 E-cores (F0000000; 2 workers, a small machine) | 10.43 ms/chunk, 96 chunks/s | **7.20 ms/chunk, 139 chunks/s** | **x0.69** |

Where the server thread's time goes (16 E-cores, `TERRAINSTATS`): decoration 1.6-1.9 ms per decorated chunk (1.25
decorations per generated chunk in a square), waiting for workers 0.5 ms, writing the 1.12 chunk (sections, structure
starts, sky light) 2.3 ms; the workers' fill + surface + carvers 5.2 ms per chunk on a busy E-core. Saving: 0.5 ms/chunk
against vanilla's 1.1 (vanilla's population leaves scheduled block ticks to write; ours none).

**Head-to-head, `python tools/terrain/pregenbench.py G:/VBPTest/genbench 3`** (plain Forge 14.23.5.2860 servers, release
jars, Chunk Pregenerator's `pregen gen startradius square 300 300 16` (33x33 chunks), seed -3892104814965596348, -Xmx4G,
16 E-cores, wall clock from the command to "Pregenerated"):

| Terrain | Chunks/s (3 runs) | Time per chunk vs vanilla |
| --- | --- | --- |
| Vanilla 1.12 | 46.9 (46.9, 46.2, 47.3) | 1 |
| **This mod, Caves & Cliffs world** | **57.2** (56.6, 57.2, 58.3) | **x0.82** |
| Caves Not Cliffs 2.0.4 | 9.7 (9.7, 9.7, 9.7) | x4.83 |
| Farmer's Future Delight 1.4.6 (ffd_cac) | 8.8 (8.8, 8.8, 8.7) | x5.33 |

(Chunk Pregenerator paces itself by the tick, so its absolute rates are lower than the dev harness's.)

Targets (EXTENDED_HEIGHT section 4): at least 34 chunks/s and at most 2x vanilla's time per chunk: met on every
measurement, by 4 to 5 times the rate the competing 1.18 terrain mods reach.

**The core alone, one E-core** (`tools	errainench.cmd 8 PipelineBench 42 16 0`: 16x16 chunks finished in a spiral,
fill on the same thread, no Minecraft): 11.7 ms/chunk (85 chunks/s), fill 3.6 ms x 1.9 fills per chunk, decoration
3.6 ms (with fossils and the surface features merged). The same with 8 workers (unpinned): 2.8 ms/chunk, same checksum.

## Not done

*   **Trees and the rest of VEGETAL** are not ported in the core yet (another session, `cc-terrain`); until they are,
    these worlds have no trees, grass patches or flowers beyond what is ported. `StateMap` already maps logs, leaves,
    flowers, saplings and plants, so they need nothing here: merge `cc-terrain` and rerun `TerrainSelfTest`, which fails
    on any state without a 1.12 block.
*   **The beardifier is not used.** 1.12's pieces are laid out after the terrain exists (in `populate`), so 1.18's
    terrain adaptation around villages, outposts and strongholds cannot apply; 1.12 villages measure the ground as
    they build, as everywhere.
*   **Held chunks are not saved** (see Memory): after a restart, features that cross from the explored area's edge
    into the chunks beyond are cut there. A per-world store for decorated chunks (RegionFile) would fix it.
*   **Structures without 1.12 pieces** (pillager outposts, shipwrecks, ocean ruins, ruined portals, buried treasure)
    come from the family's backports with their own placement, not 1.18's. Core's `StructurePlacements` could take a
    1.18 entry from this mod.
*   **Spawn point**: dry land nearest 0, 0 by the noise, not 1.18's climate-based `findSpawnPosition`; no bonus chest.
*   **Mob spawning reads the column's surface biome.** 1.18's underground biomes (lush caves, dripstone caves) are not
    seen by 1.12's spawner: axolotls and the like follow their own stream's rules.
*   **Decoration runs on the server thread** (about 2 ms of the 6). It could run on one more thread ahead of need,
    in the same order; not needed for the targets.
*   **Waterlogged blocks below 0 or above 255** need Backport Core's own store (stream C); until then they are dry.
*   OptiFine and the Dregora packs were not run with the 1.18 terrain (stream H tested the height there).
