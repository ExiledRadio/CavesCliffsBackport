# Cubic Chunks: the 1.18 terrain in cubes

Package `cubic` (branch `cc-cubicchunks`). With Cubic Chunks installed (built and tested against
1.12.2-0.0.1301, CurseForge file 9027901), a Caves & Cliffs world is a cubic world from y -64 to 319 with the same
1.18.2 terrain as a normal Caves & Cliffs world. The height coremod still refuses to run beside Cubic Chunks; Cubic
Chunks stores the height itself.

## Changelog section

*   **Cubic Chunks support.** With Cubic Chunks installed, the Caves & Cliffs world type is a cubic one: y -64 to
    319, 1.18's terrain, block for block what a normal Caves & Cliffs world of the same seed holds, and the same seeds
    as vanilla 1.18.2. Above 319 and below -64 is Cubic Chunks' own limit. The 1.17 content works in every world, as
    before.

## How it works

*   **World type** (`CubicWorldType`, registered in place of `WorldTypeCavesAndCliffs` by `HeightContent` when the
    coremod is off and Cubic Chunks is installed; `CubicSupport` holds no Cubic Chunks type, so the mod loads without
    it). It is an `ICubicWorldType`: `hasCubicGeneratorForWorld` is true for an overworld that gets 1.18 terrain
    (`ModernTerrain`'s saved choice), `calculateGenerationHeightRange` is -64..320, and `createCubeGenerator` wraps the
    world's `ModernGenerator`. A world that keeps 1.12's terrain gets Cubic Chunks' vanilla-compatibility generator.
*   **Height limits.** Cubic Chunks reads a world's limits from its own saved data (`data/cubicChunksData.dat`), made
    from its config's defaults (all of y) when a world is new. `CubicWorlds.onAttachWorld` (at highest priority, before
    Cubic Chunks' own handler) writes that data first for a new cubic Caves & Cliffs overworld, with -64 and 320.
    Cubic Chunks copies the overworld's limits to its other cubic dimensions (the Nether here; the End is excluded by
    its default config), so the Nether is -64 to 319 too, with Cubic Chunks' vanilla-compatibility terrain.
*   **One generator.** Cubic Chunks asks for the world's chunk generator again when it replaces the chunk provider,
    after asking for the cube generator. `ModernTerrain.generator` now keeps one per world, so both are the same
    pipeline, and `ModernTerrain.owns` and every system that reads `chunkGenerator` work unchanged.
    `ModernTerrain.of` accepts a world that is about to be made cubic (`CubicSupport.willBeCubic`): its biome
    provider is asked for in the world's constructor, before Cubic Chunks sets the world up.
*   **Columns, then cubes** (`CubicGenerator`). 1.18 finishes a chunk as a full column: its decoration crosses chunk
    borders and a feature sees what was decorated before it. So `generateColumn` runs what `generateChunk` runs, minus
    writing sections and sky light (`ModernGenerator.generateColumn`: the pipeline's `finish`, 3D biomes, column
    biomes, fluid updates, tile entities, structure starts, prefetch). `generateCube` gives each cube a primer that
    reads the finished column's codes directly (`CodePrimer`: no copy, no id lookup). Chests, spawners, nests and
    waterlogged blocks go to their cube on `CubeEvent.Load`.
*   **A column's cubes are all made in the tick its column is.** The pipeline drops a finished column once nothing
    reads it, and a second `finish` of a settled column is not decorated again. So population asks for whole columns
    (`getPopulationPregenerationRequirements`: the column and the three to its +x and +z, every height), and whatever
    is left at the end of the world tick or when the world saves is generated then (`drain`). After a crash, a column
    with missing cubes is filled again without its decoration (logged once).
*   **Top down.** Cubic Chunks starts a new cube's sky light from the column's surface as far as its cubes exist, and
    it asks for a column's cubes from the bottom up. A cube under the ground made before the cubes over it started
    in full sky light, which then spread into its caves. 1.12's mineshafts read the light as they are built (planks
    bridge gaps darker than 8), so 16 planks were missing against the normal world. `generateCube` now makes the cubes
    above first, top down; every cube starts dark under the ground, as a normal world's fresh chunk does.
*   **Population** is 1.12's, once per column: `ModernGenerator.populate` (structure pieces, the decoration events,
    world-generation animals) and other mods' `IWorldGenerator`s (unless Cubic Chunks' `useVanillaChunkWorldGenerators`
    is on: then it runs them itself), when the column's anchor cube (y 0 to 15) is populated. A cube is complete once
    the anchors of its column and of the three to its -x and -z are populated (`getFullPopulationRequirements`): 1.12's
    rule for a chunk. Fluid updates wait for those anchors (`FluidUpdates.populated`).
*   **Heights.** `Heights.minY/maxY` answer Cubic Chunks' limits for a cubic Caves & Cliffs world, so the spawn
    search, the structures (Backport Core's `WorldHeights` asks `Heights` by reflection), cave regions and the rest
    see -64 and 320. `Heights.isExtended` now means the coremod's section layout only (false in a cubic world: its
    columns keep 16 slots), and `Heights.isTall` is "builds outside 0-255". Core's `isCavesAndCliffsExtended` stays
    false there, so Fluidlogged API keeps its own per-cube fluid storage.

Changed outside the package: `Heights` (the cubic range, `isTall`), `HeightContent.preInit` (registers the cubic
type), `WorldTypeCavesAndCliffs` (not final, protected constructor), `ModernTerrain` (one generator per world, the
cubic gate), `ModernGenerator` (`generateChunk` split into shared steps; `generateColumn`, `states`, `Specials`;
`pipeline()` public), `Nests.create`, `FluidUpdates.populated`, `build.gradle` (`-Pcubicchunks`).

## Tested

*   `sh tools/cubic/compare.sh` (three dev servers, seed 1718, 13 x 13 columns at chunk 300, -200 generated in a
    fixed order, the inner 12 x 12 populated, the inner 11 x 11 compared from y -64 to 319: 11,894,784 blocks, by
    saved state):
    1.  `-Pcubicchunks`, fresh world (`cubic`): 25 pass, 0 fail. Cubic Chunks' limits -64..320; `Heights`, Core's
        `WorldHeights`, build height, bedrock at -64, deepslate below 0, blocks set at -64 and 319 and none at 320, sky
        light 15 at 319, the 3D biome below 0, spawn on solid ground at 1.18's climate spawn, `/locate` village and
        mineshaft, Core's start rules; no column left with missing cubes.
    2.  `-Pcubicchunks`, the same world restarted (`cubicreload`): 5 pass, 0 fail. **0 of 11,894,784 blocks differ**
        from the area as the shutdown saved it, and no cube was generated again.
    3.  Without Cubic Chunks, fresh normal Caves & Cliffs world (`cubicref`): 3 pass, 0 fail. **0 of 11,894,784 blocks
        differ** from the cubic world.
    The test pins glow lichen's direction order before the area: that order is the JVM's (every spread shuffles it),
    and the two worlds' spawn areas differ in size. Without the pin, 163 blocks differed, all glow lichen and what
    grows on or over it (moss, a few plants); with it, none.
    Before the top-down fix: 16 differed (mineshaft planks, see How it works).
*   Without Cubic Chunks: `./gradlew runServer -Pselftest=all -PdeployDir= -PcoreDeployDir=` (fresh Default world,
    seed 1718): **4448 pass, 0 fail**. `height,terrain` in a fresh Caves & Cliffs world: **251 pass, 0 fail**.

## Speed

`sh tools/cubic/bench.sh 3` (2026-11-15, 14900K, pinned to the 16 E-cores, seed 1718; the `cubicbench` self-test:
a 24 x 24 square at chunk 1000, 1000 generated and populated after an 8 x 8 warm-up, each mode on a fresh world,
alternating; 6 worker threads). Per column of the square:

| World | Generate + populate (3 runs) | Median | vs normal |
| --- | --- | --- | --- |
| Normal Caves & Cliffs world (extended height) | 5.74, 5.91, 5.96 ms | **5.91 ms, 169 chunks/s** | 1 |
| Cubic Caves & Cliffs world, all 24 cubes of every column | 6.03, 6.51, 5.90 ms | **6.03 ms, 166 chunks/s** | **x1.02** |
| Cubic Chunks' own `VanillaCubic` (1.12 terrain, same 24 cubes; 2 runs) | 13.37, 13.66 ms | 13.5 ms, 74 chunks/s | x2.3 |

The cubic run does a little more: 1,401 columns against 1,331 (population at the square's -x and -z edges finishes
the ring outside it), and making each cube costs 0.10 ms (`cube_ms`, which includes the cubes above it that it
makes first). On the server thread, writing went from 1.26-1.44 ms per chunk (sections, sky light) to 0.45-0.65 (no
sections or sky light to write; Cubic Chunks builds the cube from the primer).

**Cubic Chunks' lighting** is not part of the figures above. Its first light of all 24 cubes of a column (asked with
`Requirement.LIGHT`, as it does before sending a cube to a player) took 10.1-10.7 ms per column in our world and
10.3 in its own `VanillaCubic` world: Cubic Chunks' cost, the same for any terrain. A player is sent only the cubes
within the vertical view distance.

## Not done

*   **A client** was not run with Cubic Chunks (dedicated dev servers only). Creating the world from the client's
    world type list and Cubic Chunks' client side are untested.
*   **Waterlogged blocks** in a cubic world go through Backport Core and Fluidlogged API's own Cubic Chunks storage.
    Fluidlogged API's coremod is not loaded in the dev run, so this is untested.
*   **`height.defaultForNewWorlds`** is a coremod patch site, so with Cubic Chunks it does nothing: new worlds stay
    Default unless `level-type=cavesandcliffs` is set or the type is picked.
*   **An existing normal Caves & Cliffs world opened with Cubic Chunks** becomes a cubic world (Cubic Chunks makes
    every world of an `ICubicWorldType` cubic) and is generated fresh in Cubic Chunks' files. Its old region files
    stay untouched on disk and come back if Cubic Chunks is removed.
*   **Not run in a real instance** (Cleanroom, the RLCraft Revamped pack, OptiFine, Alfheim with Cubic Chunks).
