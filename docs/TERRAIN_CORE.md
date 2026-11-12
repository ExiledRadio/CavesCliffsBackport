# Terrain core: 1.18.2's noise fill, biomes and surface, exact and fast

Branch `cc-terrain`, package `terrain/core`. The first part of stream T's terrain: what 1.18.2 does in
`NoiseBasedChunkGenerator.fillFromNoise` (terrain shape, cheese, spaghetti and noodle caves, aquifers, ore veins),
`createBiomes` (the multi-noise biome source) and `buildSurface` (surface rules), from y -64 to 319. Plain Java 8 with no Minecraft classes, so it compiles, tests and benchmarks without Gradle or
a game, and runs on any thread.

Two rules:

1. **Same blocks as vanilla 1.18.2, for every seed.** Seeds must match Chunkbase. Every optimization keeps
   Mojang's floating-point operations in Mojang's order, or skips work whose result provably cannot matter.
2. **Fastest on one core.** 1.12.2 generates chunks on the server thread; the core is also a pure function of
   seed and position, so worker threads can compute chunks ahead.

## Results (2026-11-11)

One E-core of the 14900K (logical processor 20), 12x12 chunks, median of 5 rounds after warm-up. The checksum is
over every block of every chunk and is the same on both sides.

| Seed, center chunk | Vanilla 1.18.2 (Java 17) | Ours (Java 8) | Ours (Java 17) | Speedup |
| --- | --- | --- | --- | --- |
| 42 at 0,0 | 35.2 ms/chunk | 2.16 ms/chunk | 2.22 ms/chunk | 16.3x |
| 12345 at 500,-300 | 36.5 ms/chunk | 2.53 ms/chunk | 2.50 ms/chunk | 14.4x |
| 8675309 at -2000,1500 | 37.4 ms/chunk | 2.65 ms/chunk | 2.51 ms/chunk | 14.1x |

Threads (all 16 E-cores, 32x32 chunks, seed 42, Java 8): 1 thread 453 chunks/s, 8 threads 3,484, 16 threads 6,283.

"Vanilla" is Mojang's own `NoiseChunk`, aquifer and ore veinifier driven by `doFill`'s loop
(`VanillaFillBench`), without structures and without writing chunk sections, which is what ours does too. This
is the noise fill only. It is not chunks per second in a game: biomes, surface rules, carvers, features,
structures, lighting and chunk storage are not built yet (see *Not done*).

### Biomes and surface (2026-11-11)

Same machine and method (`SurfaceBench` against `VanillaSurfaceBench`, 12x12 chunks, median of 5). Biomes are
the stored biomes of every chunk (`ChunkAccess.fillBiomesFromNoise`, all 1,536 quarts); surface is
`SurfaceSystem.buildSurface` with the overworld rules. Vanilla reads its biomes from stored chunks the way
`WorldGenRegion` does. The checksum is over every block after surface and is the same on both sides.

| Seed, center chunk | Vanilla biomes | Ours biomes | Vanilla surface | Ours surface (Java 8 / 17) | Ours biomes + fill + surface (Java 8) |
| --- | --- | --- | --- | --- | --- |
| 42 at 0,0 | 3.09 ms | 0.054 ms | 13.4 ms | 0.28 / 0.28 ms | 2.52 ms/chunk, 396 chunks/s |
| 12345 at 500,-300 | 3.65 ms | 0.056 ms | 13.7 ms | 0.28 / 0.26 ms | 3.00 ms/chunk, 333 chunks/s |
| 8675309 at -2000,1500 | 3.01 ms | 0.055 ms | 13.3 ms | 0.38 / 0.33 ms | 3.16 ms/chunk, 317 chunks/s |

About 55x on biomes and 40x on surface. Vanilla's three steps together come to about 52 ms/chunk (19 chunks/s)
on this core; ours to about 3 ms/chunk. Seed 42's checksum matches vanilla's only with
`VanillaSurfaceBench ... cold`: one climate tie in that area is answered differently by vanilla's warm search
(see *Biome ties*).

For scale, from the earlier pack research: whole-chunk generation in a 1.12.2 server measured 68 chunks/s for
vanilla 1.12 terrain and about 15 for the existing 1.18-terrain mods, on this machine. Vanilla 1.18.2's noise
fill alone caps one core at 28 chunks/s; ours leaves that step at about 400.

### Carvers and the beardifier (2026-11-11)

Carvers (`Carvers`): `NoiseBasedChunkGenerator.applyCarvers` for `GenerationStep.Carving.AIR`, the three overworld
carvers (`cave`, `cave_extra_underground`, `canyon` with `data/worldgen/Carvers`' configurations) started from
the 17x17 chunks around, run in place after surface. `CarverBench` against `VanillaCarverBench` (vanilla's
carvers on its own chunks after fill and surface, carver biomes kept per chunk as `ChunkAccess.carverBiome` does),
12x12 chunks, median of 5, same checksum on both sides:

| Seed, center chunk | Vanilla carvers (Java 17) | Ours (Java 8 / 17) | Speedup |
| --- | --- | --- | --- |
| 42 at 0,0 | 3.02 ms/chunk | 0.275 / 0.310 ms | 11x |
| 12345 at 500,-300 | 2.39 ms/chunk | 0.245 / 0.234 ms | 10x |
| 8675309 at -2000,1500 | 2.77 ms/chunk | 0.321 / 0.328 ms | 8.6x |

Biomes + fill + surface + carvers come to about 2.7 to 3.2 ms/chunk (Java 8), about 330 chunks/s on one E-core.

Beardifier (`Beardifier`, `ChunkFiller.fill(cx, cz, out, beard)`): the terrain adaptation around structure pieces,
as an API taking the pieces (boxes, noise effect, jigsaw ground level delta, projection, junctions) until the
pieces themselves exist. Cells no piece or junction can reach keep every shortcut (each contribution is exactly
0.0 outside a box around its piece); a cell one reaches is filled the long way, final density plus the beard per
block. In `BeardCheck`'s areas (40 pieces each, 836 chunks) the fill costs 3.4 ms/chunk with the beard against
2.9 without.

### Structure placement (2026-11-11)

`Structures`: which chunks start which structure, for all 14 overworld structure sets of
`data/worldgen/StructureSets` (villages, desert pyramids, igloos, jungle temples, swamp huts, pillager outposts,
ocean monuments, woodland mansions, buried treasure, mineshafts, ruined portals, shipwrecks, ocean ruins,
strongholds). `startAt(set, cx, cz)` gives the structure a chunk starts (its index in `STRUCTURE_NAMES[set]`) or -1;
`starts(set, area)` every start in an area; `ringPositions()` the 128 stronghold chunks. It is the placement
(random spread cells, concentric rings moved to a stronghold biome by `findBiomeHorizontal`), then
`createStructures`' weighted choice among a set's structures, then each structure's own start check as its
`pieceGeneratorSupplier` makes it: the biome at the surface (`validBiomeOnTop`, WORLD_SURFACE_WG or OCEAN_FLOOR_WG
from the noise fill's base column), corner heights (desert pyramids, jungle temples, mansions), the jigsaw start
template's box center (villages, outposts; start pool, rotation and template boxes from vanilla), the ruined
portal's suitable height over its four corner columns, monuments' biomes within 29 blocks, outposts' distance to
village candidates, the probability draws of mineshafts and buried treasure. Pieces are not built.

The tables vanilla keeps in data (biome tags of every structure, start pools' weight-expanded template lists with
each template's box per rotation, ruined portal boxes per rotation and mirror, ocean and river biomes) are in
`StructureTables`, generated by `tools/terrain/make_structure_tables.py` from the oracle's `structure_tables.txt`.
Heights come from `ChunkFiller.baseHeight` / `baseColumn` (`getBaseHeight` / `getBaseColumn`: one noise cell
column, top down until the answer is known).

Speed (`StructureBench`, one E-core, Java 8, 64x64 chunks, median of 5): `startAt` for all 14 sets at a chunk costs
0.007 to 0.015 ms; the stronghold rings (128 searches of 3,249 quarts each) about 1 s, once per world (vanilla
computes them on a background thread too). Asked per chunk by the chunk generator, placement is noise.

## Verified

`tools/terrain/TerrainCheck` against the oracle's files, seeds 42, 12345, -7346534567, 8675309:

- 16,000 points x 15 noise router functions (barrier, floodedness, spread, lava, temperature, vegetation,
  continents, erosion, depth, ridges, initial density, final density, vein toggle, vein ridged, vein gap):
  bit-identical doubles.
- 256 chunks through `getBaseColumn` and 6,000 chunks through the real `NoiseChunk` (two 20x20 areas per seed and
  scattered chunks out to 30 million blocks): about 615 million blocks, all identical.

Biomes, `tools/terrain/BiomeCheck` against `biomes_SEED.bin.gz` (`VanillaBiomeDump`): every quart of 1,552
chunks per seed (24x24 around 0,0, 24x24 far out, 400 scattered to 30 million blocks) and a 1024x1024 quart layer
at y 64: 13.7 million quarts, all 50 overworld biomes, identical. The parameter list has vanilla's 7,578 leaves.
`BiomeSelfTest` checks the shortcut against the ported tree search on 2 million random climate points, a third
of them exactly on parameter borders or outside -1..1.

Surface, `tools/terrain/SurfaceCheck` against `surface_SEED.bin.gz` (`VanillaSurfaceDump`, real `ProtoChunk`s
through vanilla's fill and `SurfaceSystem`): 688 chunks per seed (12x12 around 0,0, 12x12 far, 200 scattered, and
up to 4 chunks for every biome found on a scan out to 16,384 blocks), 2,752 chunks and 270 million blocks, every
surface block below present (badlands bands and pillars, icebergs, powder snow, calcite, ...), identical.

Carvers, `tools/terrain/CarverCheck` against `carved_SEED.bin.gz` (`VanillaCarverDump`: vanilla's own carvers
on real `ProtoChunk`s after its fill and surface, the same 688 chunks per seed as the surface check): 2,752 chunks,
270 million blocks, about 2 million of them changed by the carvers, identical. That includes the dirt the carvers
turn into the surface's top material under a carved grass column (`SurfaceSystem.topMaterial`): 1,959 blocks in
141 chunks, which differ if that step is left out. McMath's sine table is checked against `Mth.SIN`.

Beardifier, `tools/terrain/BeardCheck` against `beard_SEED.bin.gz` (`VanillaBeardDump`: vanilla's `Beardifier`
built by its own constructor over a feature manager that hands it made-up pieces near the surface: plain beard
pieces, buried pieces, rigid and terrain-matching jigsaw pieces with ground level deltas and junctions, one area
packed so that many terms overlap): 836 chunks, 82 million blocks, 364,365 of them changed by the beard, identical.
The beard kernel is checked against `BEARD_KERNEL`.

Structures, `tools/terrain/StructureCheck` against `structures_SEED.bin.gz` (`VanillaStructureDump`: every
candidate chunk of every set in chunks -512..511 (mineshafts and buried treasure, which have a candidate in every
chunk: -128..127), strongholds' 128 ring positions, through vanilla's own `createStructures` choice and
`ConfiguredStructureFeature.generate`, pieces and all, with the templates read from the jar): every start of every
structure type identical on the 4 seeds (11,855 starts). The oracle binds the data pack's biome tags
(`VanillaWorld.bindBiomeTags`; `RegistryAccess.builtinCopy()` has none) and makes every biome search cold.

### Biome ties

Vanilla's `Climate.RTree.search` starts from the worker thread's previous answer (`lastResult`, a ThreadLocal)
and keeps it when another leaf is exactly as close. A climate value exactly on a parameter border makes two
biomes tie, so vanilla's answer there depends on what that thread generated before: not reproducible, by vanilla
itself or by Chunkbase. In the oracle's runs a warm search in `fillBiomesFromNoise` order differed from a cold one
at 76 to 643 quarts of 884,736 (about 1 in 2,500). The core always answers as a cold search does (closest leaf,
ties to the leaf the tree's depth-first order reaches first), which makes biomes a pure function of seed and
position; the oracle dumps vanilla the same way (it clears `lastResult` before every query).

## Block codes

`BlockCodes` (one byte per block, each the block's default state; `VanillaWorld` maps vanilla's states to the same
numbers): 0 air, 1 stone, 2 water, 3 lava, 4 copper ore, 5 raw copper block, 6 granite, 7 deepslate iron ore,
8 raw iron block, 9 tuff (the fill); 10 bedrock, 11 deepslate, 12 dirt, 13 grass block, 14 podzol, 15 coarse dirt,
16 mycelium, 17 calcite, 18 gravel, 19 sand, 20 sandstone, 21 red sand, 22 red sandstone, 23 packed ice,
24 snow block, 25 powder snow, 26 ice, 27 terracotta, 28 white, 29 orange, 30 yellow, 31 brown, 32 red,
33 light gray terracotta (surface). Biome ids (`Biomes`) are the index in 1.18.2's `Biomes.java` declaration order.

## How it is fast

In the order they were measured (seed 42, ms/chunk):

| Step | ms/chunk |
| --- | --- |
| Vanilla 1.18.2 | 35.2 |
| Straight port: the router as plain code instead of a tree of density function objects | 16.5 |
| Perlin gradients from tables instead of a switch (8 unpredictable branches per sample) | 12.5 |
| Whole cells at once: all 8 corners positive and noodles off is rock, all negative is air or fluid; aquifer answers from a summary of the 12 centers a position looks at | 3.9 |
| Corners computed only as far as read: far above ground not even the 3D noise, negative terrain no caves, noodle and vein noises only where a cell needs them | 2.2 |

Why each shortcut is exact is in the comments next to it (`ChunkFiller`, `SURE`, `Aquifer.summary`, `SKY`).

Biomes (`Climate`, `BiomeSource`): a leaf's distance is a sum of one term per parameter, and every surface leaf
comes as a pair at depth 0 and depth 1. So per quart column the search is done once: the closest depth-0 and
depth-1 leaves in the other five parameters come from a precomputed grid of the leaves' bounds (20,475 cells,
lowest depth-first rank per cell; values on a grid line or in an uncovered cell try every leaf), plus the two
underground leaves. Per y the biome is then a comparison of four numbers. Columns are cached by quart and share
the fill's column noise; per-quart results are kept, so the surface's zoom reads them for free.

Surface (`SurfaceBuilder`): the rule tree is plain code in Mojang's order. Below the min surface level only the
bedrock and deepslate gradients can apply, so the column scan stops there and the rest is a short loop over
y <= 7. The stone-depth-below scan is replaced by asking the block underneath (the overworld only reads
`ON_CEILING`). The zoom skips the fiddled distances where the 8 candidate quarts hold one biome, which is almost
everywhere. Stone replaced by a non-air block is a plain write (the heightmap cannot move).

How the carvers are fast (`Carvers`): a carver's whole path (every tunnel, room, radius and branch) depends only
on its start chunk and the seed; the chunk being carved only cuts tunnels short (`canReach`) and clips ellipsoids
to itself. So each start chunk's paths are recorded once and replayed by each of the up to 289 chunks that can
see them, in vanilla's order, testing `canReach` where vanilla tests it. A tunnel whose recorded ellipsoids,
branches included, all fail `carveEllipsoid`'s bounds test for a chunk is skipped (each of them would return
without effect). Every overworld biome lists the same three carvers (the oracle prints the lists), so the start
chunk's carver biome is never looked up.

## Running the tools

```
sh tools/terrain/run.sh TerrainCheck E:/MincraftModding/Workspaces/MC1182Reference/oracle-out
tools\terrain\bench.cmd 8 TerrainBench 42 12 5            (one E-core; 8, 17, 21 or 25 picks the JDK)
E:\MincraftModding\Workspaces\MC1182Reference\bench-vanilla.cmd 42 12 5
sh tools/terrain/run.sh BiomeCheck E:/MincraftModding/Workspaces/MC1182Reference/oracle-out
sh tools/terrain/run.sh SurfaceCheck E:/MincraftModding/Workspaces/MC1182Reference/oracle-out
sh tools/terrain/run.sh BiomeSelfTest 2000000
tools\terrain\bench.cmd 8 SurfaceBench 42 12 5
sh tools/terrain/run.sh CarverCheck E:/MincraftModding/Workspaces/MC1182Reference/oracle-out
sh tools/terrain/run.sh BeardCheck E:/MincraftModding/Workspaces/MC1182Reference/oracle-out
tools\terrain\bench.cmd 8 CarverBench 42 12 5
sh tools/terrain/run.sh StructureCheck E:/MincraftModding/Workspaces/MC1182Reference/oracle-out 512 128
tools\terrain\bench.cmd 8 StructureBench 42 64 5
python tools/terrain/make_structure_tables.py E:/MincraftModding/Workspaces/MC1182Reference/oracle-out/structure_tables.txt
E:\MincraftModding\Workspaces\MC1182Reference\bench-carvers-vanilla.cmd 42 12 5
E:\MincraftModding\Workspaces\MC1182Reference\bench-surface-vanilla.cmd 42 12 5    (append 0 0 cold for a cold search)
```

The biome and surface oracle classes (`VanillaWorld`, `VanillaBiomeDump`, `VanillaSurfaceDump`,
`VanillaSurfaceBench`, copies in `tools/terrain/oracle`) are compiled straight into the reference workspace with
`javac @javac.args -d build/classes/java/main ...` (JDK 17; `javac.args` is `-cp` and vanilla.args' classpath
line) and run with `java @vanilla.args net.minecraft.world.level.levelgen.<class>`:
`VanillaBiomeDump oracle-out 24 400 1024`, `VanillaSurfaceDump oracle-out 12 200 4`,
`VanillaCarverDump oracle-out 12 200 4`, `VanillaBeardDump oracle-out 10 40` and
`VanillaStructureDump oracle-out 512 128` wrote the gzipped dumps.

The oracle is `tools/terrain/oracle/*.java`, copies of the sources that live in the reference workspace
`Workspaces/MC1182Reference` (ForgeGradle 5, Mojang names, JDK 17): `gradlew oracle`, and `VanillaFillDump` /
`VanillaFillBench` through `vanilla.args` (written by `make_args.py`). Run benchmarks pinned to E-cores.

## Not done

Terrain, in the order to build it, each checked against the oracle the same way:

Done: biomes and surface rules, carvers, the beardifier, structure placement (above).

1. Features (placed features, GenerationStep.Decoration). Not started: it is a project of its own, best split
   into streams (the decoration loop and its oracle with ores first; then cave features: geodes, dripstone, lush
   caves, monster rooms, fossils, springs, lava lakes; then surface: disks, icebergs, freeze top layer; then
   trees and vegetation). What vanilla does, from `ChunkGenerator.applyBiomeDecoration`:
   - The random is a `WorldgenRandom` over a `XoroshiroRandomSource` (not the legacy LCG): java.util.Random's
     methods with `next(bits)` = Xoroshiro `nextLong() >>> (64 - bits)`; `setSeed` is `Xoroshiro.ofSeed`.
     `setDecorationSeed(worldSeed, chunkMinX, chunkMinZ)` once per chunk, then `setFeatureSeed(decorationSeed,
     index, step)` before each feature (`LegacyRandom` has both helpers, over the LCG; they need the Xoroshiro
     variant).
   - For each of the 11 steps: first the structure pieces of that step (configured structures grouped by
     step, `setFeatureSeed(i, l, step)` with l counting them in registry order; pieces placed in the chunk), then
     the step's placed features: the union, over every biome stored anywhere in the 3x3 chunks' sections, of the
     biome's features for that step, each mapped to its index in `biomeSource.featuresPerStep()` (a global
     topological order per step over all biomes' lists, `FeatureSorter`), sorted by index; each placed with
     `setFeatureSeed(decorationSeed, index, step)` and `placeWithBiomeCheck` (its `BiomeFilter` tests the zoomed
     stored biome at each placement position). The index tables and every biome's per-step lists should be
     dumped by the oracle and generated into a table, as StructureTables is.
   - Features read and write the 3x3 chunks around (WorldGenRegion with radius 1), on chunks after carving; the
     1.12 generator must run decoration per chunk with its neighbors filled, surfaced and carved.
   - The oracle needs a `WorldGenLevel`: vanilla's `WorldGenRegion` wants a `ServerLevel` (heights, seed, biome
     manager, ensureCanWrite); either allocate one with Unsafe and set its fields over a stand-in level, or a
     minimal `WorldGenLevel` over `ProtoChunk`s. `VanillaWorld.bindBiomeTags()` is needed (ore replaceables and
     biome filters use tags).
   - Block codes: one byte per block is short for features. Ores alone add about 14 states; geodes, dripstone
     (thickness and direction, waterlogged), lush caves (cave vines with berries, dripleaves with facing and tilt,
     glow lichen with six face bits, waterlogged) and vegetation carry properties. Decide on 16-bit codes or a
     side table before porting them.
   - Biome temperatures (`Biomes.temperature`) are still filled in only for the frozen oceans; freezing, snow
     and ice need every biome's (and `Biome.getTemperature`'s height adjustment).
2. The chunk generator around it for 1.12.2: worker threads computing chunks ahead in tiles (sharing border
   corners between neighbors saves about a quarter of the corner work), writing sections directly.
3. The extended-height coremod (`docs/EXTENDED_HEIGHT.md`), without which none of this reaches a world.

Biome temperature (`Biomes.temperature`) is filled in only for the two frozen oceans, the only biomes 1.18.2's
overworld surface asks about; snow and freezing features will need the rest.

Further speed, measured but not built: noise for a whole column at a time (x and z work shared by its 49
corners), flattened octave loops for the one-octave cave noises, tiles of chunks.
