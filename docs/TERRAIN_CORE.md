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

### Features: the decoration loop and ores (2026-11-11)

`Decorator.decorate(region)` is `ChunkGenerator.applyBiomeDecoration` for a chunk on its 3x3 `Region`: the
decoration random is a `WorldgenRandom` over Xoroshiro (`XoroRandom`: java.util.Random's methods over
`next(bits)` = Xoroshiro `nextLong() >>> (64 - bits)`), `setDecorationSeed(seed, minX, minZ)`; per step, the union
over every biome stored in the 3x3 chunks of that biome's features, as indices into the step's global order
(`FeatureTables`, generated by `tools/terrain/make_feature_tables.py` from vanilla's `featuresPerStep()` and every
biome's lists), sorted, each with `setFeatureSeed(decorationSeed, index, step)`. Placement modifiers run as
vanilla's lazy streams do (each position goes through the remaining modifiers and the feature before the next one
is drawn). Features not ported are skipped; `only(names)` restricts to a set, as the oracle's `-Dfeatures` does
(each feature's random is its own, so a subset is exactly vanilla with the rest disabled). Structure pieces, which
vanilla places first in each step, are not in yet.

Ported: every overworld `Feature.ORE` placed feature (29: dirt, gravel, granite/diorite/andesite upper and lower,
tuff, coal upper/lower, iron upper/middle/small, gold, gold lower, gold extra, redstone and lower, diamond, large,
buried, lapis and buried, copper and large, emerald, infested, clay) with `CountPlacement`, `RarityFilter`,
`InSquarePlacement`, `HeightRangePlacement` (uniform, trapezoid), `BiomeFilter` (zoomed stored biome). The ore
feature reads `OCEAN_FLOOR_WG` as it stood after carving (from LIQUID_CARVERS on only the POST_FEATURES heightmaps
follow block changes; `Region.oceanFloorHeights`) and writes sections directly, as `BulkSectionAccess` does.

Speed (`DecorationBench`, one E-core, 12x12 chunks at 0,0 seed 42, chunks generated untimed, median of 5 warm
rounds): ores 1.02 ms/chunk on Java 8, 0.95 on Java 17, same checksum. Vanilla's decoration with the same 29
features on the same E-core (`VanillaFeatureDump`'s timer, 128 chunks, one pass) 16.9 ms/chunk. Most of ours is
the ore spheres themselves (`doPlace`); each chunk's stored-biome set is cached (it is in nine 3x3 unions) and the
zoom answers without fiddled distances where its 8 quarts agree.

Also ported: springs (spring_water, spring_lava, spring_lava_frozen: `VeryBiasedToBottomHeight`), disks
(disk_sand, disk_clay, disk_gravel: `HeightmapPlacement` on OCEAN_FLOOR_WG, sandstone under falling disks over air)
and underwater magma (`SurfaceRelativeThresholdFilter`, the column scan, a draw per box position in x, y, z
order); `Misc`. `FeatureCheck` with ores plus these 7 (`oracle-out/feat-misc`, 512 chunks): identical.
`DecorationBench` with all of them: 1.04 ms/chunk (Java 8), 0.97 (Java 17); fill + surface + carvers unchanged
(2.41 + 0.28). Writes through `Region.set` are dropped outside the writable 3x3 and the world's height as
`WorldGenRegion.setBlock` drops them.

Heightmaps and light (2026-11-11): `Region` keeps every chunk's POST_FEATURES heightmaps (WORLD_SURFACE, OCEAN_FLOOR,
MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES; `Region.height`) the way `ProtoChunk.setBlockState` does, with
`Heightmap.update` on every `set` and none on section writes. Vanilla primes them lazily; since every setBlock keeps
them exact and no section write changes a predicate, priming once after carving (`Region.primeHeightmaps`) gives the
same number at every read, and `FeatureCheck` checks the live ones against freshly primed ones after decoration. The
predicates come from per-code flags (`BlockCodes.flags`: not air, `Material.blocksMotion`, a fluid, leaves) that
`BlockCodes.register` derives from the state name; features register their states in class initializers.
Light: while a chunk is decorated no chunk within one of it is lit (LIGHT needs all neighbors at FEATURES), so
vanilla answers block light 0 and sky light 15 (no data layers) with sky darkening 0; `Region.blockLight` /
`rawBrightness` and the oracle region's `getBrightness` / `getRawBrightness` give exactly that. (A neighbor
chunk's sections can get data layers early when a chunk two away is lit, which depends on the server's generation
order; the center chunk never does.) No measurable cost: `DecorationBench` 1.05 ms/chunk (Java 8).

Cave features, first part (2026-11-11): lava lakes (lake_lava_underground with `EnvironmentScanPlacement` down to
non-air, lake_lava_surface on WORLD_SURFACE_WG; `LakeFeature`, cave air in the top half, stone barrier),
monster rooms (monster_room, monster_room_deep; `MonsterRoomFeature`, the chest turned by
`StructurePiece.reorient` and its loot seed drawn, the spawner's mob drawn last) in `Caves`, and the amethyst geode
in `Geodes` (`GeodeFeature`; its legacy NormalNoise on the world seed is made once per Decorator instead of per
placement, and positions whose inverse-root sum cannot reach the outer layer even with the largest noise skip the
noise, which draws nothing). `Region` gained the WORLD_SURFACE_WG snapshot (`Region.worldgenHeights` fills both
worldgen heightmaps), `Decorator` the `HEIGHTMAP` op over any heightmap and `CUSTOM` modifiers, `BlockCodes` the
Material flags features test (solid, liquid, replaceable, solid render, water). `FeatureCheck` with all 41 ported
features (`oracle-out/feat-caves2`, 512 chunks, 14 spawners, chests in all four facings, every geode layer and bud):
identical. `DecorationBench` 1.17 ms/chunk (Java 8) at seed 42 and at seed 12345 (500,-300).

Dripstone (2026-11-11): large_dripstone (`LargeDripstoneFeature`: `Column.scan`, the wind offsetter, the stone
moving back into rock and halving its radius), dripstone_cluster (`DripstoneClusterFeature`, pools, layers, merged
tips) and pointed_dripstone (the SimpleRandomSelector over the two inline placed `PointedDripstoneFeature`s with
their environment scans, after `RandomOffsetPlacement` of clamped normals) in `Dripstone`. Gaussians:
`XoroRandom.nextGaussian` is `java.util.Random`'s (StrictMath), and like WorldgenRandom it keeps its second value
across setFeatureSeed (WorldgenRandom.setSeed does not reset it); a new one per chunk. Large dripstone reads up to
about 38 blocks from its chunk (radius 9 plus the wind drift over the column), so `Region` now holds the 5x5 chunks
(`Region.READ`), writes still the 3x3. `FeatureCheck` with all 44 ported features: identical on the default areas
(`oracle-out/feat-caves3`, 512 chunks) and on a dripstone caves area per seed (`oracle-out/feat-drip`, 64 chunks each,
`-Dcenters` 42: -24,-32; 12345: 32,-40; -7346534567: -64,0; 8675309: -8,-8; every pointed dripstone state appears).
`DecorationBench`: 1.16 ms/chunk at seed 42 0,0; 1.54 in the dripstone area of seed 8675309 (vanilla's timer
there, unpinned Java 17: 6.6).

Lush caves and glow lichen (2026-11-11): lush_caves_ceiling_vegetation, cave_vines, lush_caves_clay,
lush_caves_vegetation, spore_blossom, classic_vines_cave_feature and glow_lichen in `LushCaves`
(`VegetationPatchFeature` and its waterlogged variant, `BlockColumnFeature`, `SimpleBlockFeature` with the plants'
canSurvive and `DoublePlantBlock.placeAt`, the random selectors, `VinesFeature`, `GlowLichenFeature` with
`MultifaceBlock`'s placement and spreading). Three things vanilla does that matter: the vegetation patch keeps its
ground positions in a `HashSet<BlockPos>` and draws per element as it iterates, so `LushCaves.Pos` hashes as
`Vec3i` does and a real `java.util.HashSet` gives vanilla's order; `BlockColumnFeature` tests the column from one
past its origin; and `MultifaceBlock.DIRECTIONS` is a static array that every lichen spread shuffles in place
(`Collections.shuffle(Arrays.asList(DIRECTIONS), random)`), so a spread starts from the order the previous one left
for the life of the JVM (vanilla is history dependent there, and racy across threads); `LushCaves.DIRECTIONS` is
process-global the same way, which the checks match by decorating in the oracle's order in one JVM. Face
sturdiness is per face (`BlockCodes.faceFull`: azalea's top face is full, carpets' bottom). rooted_azalea_tree
waits for trees. `FeatureCheck` with all 51 then ported features: identical on the default areas (lush caves at
0,0 on three seeds, `oracle-out/feat-lush`, 512 chunks) and in seed 8675309's lush caves at 16,-8
(`oracle-out/feat-lush2`, 64 chunks). `DecorationBench` (with freeze_top_layer): 1.42 ms/chunk at seed 42 0,0,
1.51 at seed 8675309 16,-8 (vanilla's unpinned timer there: 5.9).

freeze_top_layer (2026-11-11), in `SurfaceFeatures`: `SnowAndFreezeFeature` over the chunk's columns at the live
MOTION_BLOCKING height, the zoomed biome at the top block, ice on still water where it is not warm enough to rain
at that block, then a snow layer where `SnowLayerBlock.canSurvive` (after the ice) and the block below snowy. Block
light is always below 10 here (see *Heightmaps and light*). On the way: features match blocks, not states, where
vanilla writes `is(target.getBlock())` or a block tag (`BlockCodes.sameBlock`: disks replace snowy grass a neighbor's
freeze already made; `BlockTags.DIRT` holds snowy grass, podzol and mycelium), and the disks' origin test is
`FluidTags.WATER` (waterlogged blocks and water plants from neighbors count). `FeatureCheck` with all 52 ported
features: identical on the default areas (`oracle-out/feat-freeze`, 512 chunks) and on a snowy plains plus a frozen
ocean area per seed (`oracle-out/feat-snow/<seed>`, 128 chunks each; seed 42's has 22,376 snow layers and 4,147 ice).
`DecorationBench`: 1.41 ms/chunk at seed 42 0,0; 1.57 at 320,72 (snowy plains).

Surface features and fossils (2026-11-12): icebergs (iceberg_packed, iceberg_blue: `IcebergFeature` at sea level,
its `Math.pow`/`cos`/`sin` kept), forest_rock (`BlockBlobFeature`), ice_spike, ice_patch (down to a snow block, then
`BaseDiskFeature` without the water test), desert_well and blue_ice in `SurfaceFeatures`; fossil_upper and
fossil_lower in `Fossils` (`FossilFeature`: the 16 templates generated into `FossilTables` by
`tools/terrain/make_fossil_tables.py` in `loadPalette`'s order, a random rotation, `BlockRotProcessor` drawing a
float per template block from the feature's random because the settings carry it, every block processed before any
is placed so `ProtectedBlockProcessor` reads the world as it stood before that template, the diamond rule on the
lower ones). Two oracle fixes on the way: Forge patches `Feature.isStone` (forest rock's ground test) to the
`forge:stone` tag, which the oracle's minecraft-only tag binding left empty, so the oracle now binds it to vanilla's
`base_stone_overworld`; and `VanillaFeatureDump`'s region answers `getLevel()` with a stand-in `ServerLevel` whose
server's `StructureManager` reads templates from the jar (fossils needed it). One bug found in an older port: the
monster room skipped the spawner's mob draw (`nextInt(4)`, last in the feature), which moves every later room
attempt of the placed feature in that chunk; it showed only where two rooms land in one chunk.
`FeatureCheck` with all 61 ported features: identical on the default areas at side 16 (`oracle-out/feat-fossil`,
2,048 chunks), on six areas per seed for the surface features (`oracle-out/feat-surf/<seed>`: ice spikes, desert,
old growth pine taiga, frozen ocean, a desert well chunk and a blue iceberg chunk found with `FindRare`; 1,536
chunks) and on a desert and a swamp per seed for fossils (`oracle-out/feat-fossil/<seed>`, 1,152 chunks, 1,124 bone
blocks). Fossils only generate in deserts and swamps. `DecorationBench` (Java 8, one E-core): 1.41 ms/chunk at seed
42 0,0; 1.38 at 360,64 (frozen ocean, icebergs); 1.19 at -104,-104 (desert).

Trees, first part (2026-11-12), in `Trees`: `TreeFeature` with straight trunks, blob, spruce and pine foliage,
two-layer feature sizes and the beehive decorator, the `*_checked` sapling filter, `RANDOM_SELECTOR` and the grove's
snow scan: trees_snowy, trees_badlands, trees_birch, trees_taiga, trees_grove, birch_tall. `Decorator` gained
`COUNT_EXTRA` (countExtra's weighted list: `nextInt(1 / chance)`) and `WATER_DEPTH` (`SurfaceWaterDepthFilter` on the
live WORLD_SURFACE and OCEAN_FLOOR maps); `BlockCodes` the `REPLACEABLE_PLANT` flag (`validTreePos`). What matters
for exactness: the trunk, foliage and decoration sets are `HashSet<BlockPos>`s (`LushCaves.Pos`), the dirt under the
trunk goes into the trunk set, the decorators see the sets sorted by y with ties in hash order, `updateLeaves` sets
leaf distances by BFS from the trunk set (other trees' leaves too; the rest stay 7) and
`StructureTemplate.updateShapeAtEdge` runs over the changed shape in `DiscreteVoxelShape.forAllFaces`' order (z, then
y, then x faces), which takes the snow off snowy grass under new leaves (a neighbor's freeze_top_layer runs before).
`BeehiveDecorator` shuffles its candidates with `Collections.shuffle(list)`, the JVM's shared random, so vanilla's
hive position cannot be reproduced; ours shuffles with a position-seeded random, and every draw from the feature's
random (the chance, the bees) is vanilla's. `FeatureCheck -DlooseBees` compares a bee nest and air as equal.
Also: `ProtoChunk.getBlockState` answers air for every section that holds only air-like blocks, so a surface lake's
cave air above the ground reads (and dumps) as air; `Region.get` does the same and FeatureCheck compares through
`Region.readAsVanilla` (found as 73 blocks at y 64 in a wooded badlands lake). `FeatureCheck` with all 67 ported
features: identical on six areas per seed (`oracle-out/feat-trees/<seed>`, side 8, 1,536 chunks: snowy plains,
wooded badlands, birch forest, old growth birch forest, taiga, grove; centers in the hand-over note and below) and
still on every earlier check. `DecorationBench` (Java 8, one E-core): 1.37 ms/chunk at seed 42 0,0; 1.43 at -16,0
(birch forest); 1.38 at 48,-56 (taiga). Vanilla's timer over the same features there (unpinned Java 17): 3.4-4.2.

Fancy oaks (2026-11-12): `FancyTrunkPlacer` (limbs stepped in floats from the block's corner, branch logs with x or
z axes, `minClippedHeight` 4) and `FancyFoliagePlacer` in `Trees`, unlocking trees_plains (inline placed fancy oak
and oak with 5% bees, its own modifier order: the oak sapling filter before the biome), trees_water,
trees_windswept_hills, trees_windswept_forest, trees_flower_forest and trees_meadow. `FeatureCheck -DlooseBees` with
all 73 ported features: identical on plains, river, windswept hills, windswept forest, flower forest and meadow per
seed (`oracle-out/feat-trees2/<seed>`, side 8, 1,536 chunks), and the first tree areas still identical.
`DecorationBench`: 1.35 ms/chunk at seed 42 24,-24 (plains); 1.71 at -32,-32 (flower forest).

Acacia (2026-11-12): `ForkingTrunkPlacer` (a random horizontal direction, the bent top and an optional branch, each
attachment one above its last placed log) and `AcaciaFoliagePlacer` in `Trees`: trees_savanna and
trees_windswept_savanna. Identical on a savanna and a windswept savanna area per seed
(`oracle-out/feat-trees3/<seed>`, side 8, 512 chunks: 42 -72,-72/-40,16; 12345 -56,-8/72,-240; -7346534567
8,-112/-56,-144; 8675309 -24,-24/-104,144). `DecorationBench` with all 75: 1.43 ms/chunk at seed 42 0,0; 1.74 at
-72,-72 (savanna).

Vegetation (2026-11-12), in `Vegetation`: every plant of VEGETAL_DECORATION but the trees (VegetationPlacements and
AquaticPlacements without the trees: random patches of simple blocks and block columns, flowers with
`NoiseProvider`, `NoiseThresholdProvider` and `DualNoiseProvider` (seed 2345, legacy NormalNoise), grass and ferns,
mushrooms, dead bushes, lily pads, sugar cane, cactus, melons, pumpkins, berries, the tall plants, forest flowers
(`SimpleRandomSelector`), bamboo with its podzol, vines, seagrass, kelp, sea pickles and the three coral shapes),
the placements `NoiseThresholdCountPlacement`, `NoiseBasedCountPlacement` (both on `Biome.BIOME_INFO_NOISE`) and a
clamped uniform count, and trees_birch_and_oak. Survival is Forge's: `BushBlock.canSurvive` asks
`canSustainPlant` of the block below, which differs from vanilla for sugar cane (`BEACH`: water next to the block
below), cactus, lily pads (`WATER`: any water block below) and seagrass (`PLAINS` dirt also counts). Mushrooms
survive only on mycelium and podzol, since raw brightness is 15 while nothing is lit. seagrass_simple places
nothing: its `CarvingMaskPlacement(LIQUID)` reads a mask no overworld carver writes. Trees now cut plants that
earlier VEGETAL features placed, so `Trees.updateShape` asks `Vegetation.updateShape` (BushBlock survival, the
double plant halves, coral, kelp head and body, snow layers). `FeatureCheck -DlooseBees` with all 140 ported
features: identical on the default areas (`oracle-out/feat-veg`, side 8), on swamp, sunflower plains, desert, warm
ocean, beach and eroded badlands per seed (`oracle-out/feat-veg2/<seed>`, side 8, 1,536 chunks: 42
-16,8/24,-40/-104,-104/-80,-128/8,-16/-120,-128; 12345 -112,-112/-24,32/-144,24/-256,-248/-80,104/-328,-280;
-7346534567 -48,-144/112,128/-64,-152/8,-152/-24,-32/200,152; 8675309 -48,144/24,80/-48,-64/-64,-64/16,24/240,-152),
and on all the earlier tree areas again with the plants (`oracle-out/vt1`, `vt2`). `-DlooseBees` now also takes a
nest against a plant: a patch placed after the tree fills the air where the other side's nest went.
`DecorationBench` (Java 8, one E-core): 1.43 ms/chunk at seed 42 0,0; 1.94 at -16,8 (swamp); 1.26 at -80,-128
(warm ocean); 1.50 at 24,-40 (sunflower plains).

Java 8 and `Math.sin`: `OreFeature` takes `Math.sin`/`Math.cos` of a double, as vanilla does. Over all 2^24 angles
an ore can draw, Java 17, 21 and 25 give the same bits (vanilla's, on Java 17), but Java 8's `Math` differs from
them in the last bit at about a million angles (`StrictMath` matches neither). On the checks' 704 chunks Java 8
places every ore block identically too; a last-bit difference can only matter where a sphere test lands within
1e-15 of its threshold, so it is not ruled out on Java 8 in general. Cleanroom (Java 21/25) is exact.

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
structure type identical on the 4 seeds (11,855 starts). A second, wider dump
(`-Dsets=... VanillaStructureDump oracle-out/wide 2048 128`: chunks -2048..2047 for the rarer sets, desert
pyramids, igloos, jungle temples, swamp huts, outposts, monuments and mansions) adds 15,824 identical starts,
192 of them mansions. The oracle binds the data pack's biome tags
(`VanillaWorld.bindBiomeTags`; `RegistryAccess.builtinCopy()` has none) and makes every biome search cold.

Features, `tools/terrain/FeatureCheck` against `features_SEED.bin.gz` (`VanillaFeatureDump`: vanilla's own
decoration loop and `placeWithBiomeCheck` on a `WorldGenRegion` allocated without its constructor (17x17 chunks
readable, generated on demand; writes within 1, as at FEATURES), the data pack's biome, block and fluid tags bound,
chunks decorated in a fixed order since features write into neighbors). Ores (`-Dfeatures=` the 29): two 8x8 areas
per seed (512 chunks) and, for the ores of mountains, windswept hills and badlands, three 4x4 areas per seed found
with `FindBiome` (192 chunks, `-Dcenters=`): 704 chunks, 69 million blocks, every ore state present, identical on
Java 17 and Java 8. The dumps name states (`BlockCodes.state`), so the feature codes need no byte map on the oracle
side.

### Biome ties

Vanilla's `Climate.RTree.search` starts from the worker thread's previous answer (`lastResult`, a ThreadLocal)
and keeps it when another leaf is exactly as close. A climate value exactly on a parameter border makes two
biomes tie, so vanilla's answer there depends on what that thread generated before: not reproducible, by vanilla
itself or by Chunkbase. In the oracle's runs a warm search in `fillBiomesFromNoise` order differed from a cold one
at 76 to 643 quarts of 884,736 (about 1 in 2,500). The core always answers as a cold search does (closest leaf,
ties to the leaf the tree's depth-first order reaches first), which makes biomes a pure function of seed and
position; the oracle dumps vanilla the same way (it clears `lastResult` before every query).

## Block codes

`BlockCodes` (one 16-bit char per block, `char[]` chunk arrays, each code one 1.18.2 block state; `VanillaWorld` maps vanilla's states to the same
numbers): 0 air, 1 stone, 2 water, 3 lava, 4 copper ore, 5 raw copper block, 6 granite, 7 deepslate iron ore,
8 raw iron block, 9 tuff (the fill); 10 bedrock, 11 deepslate, 12 dirt, 13 grass block, 14 podzol, 15 coarse dirt,
16 mycelium, 17 calcite, 18 gravel, 19 sand, 20 sandstone, 21 red sand, 22 red sandstone, 23 packed ice,
24 snow block, 25 powder snow, 26 ice, 27 terracotta, 28 white, 29 orange, 30 yellow, 31 brown, 32 red,
33 light gray terracotta (surface). Biome ids (`Biomes`) are the index in 1.18.2's `Biomes.java` declaration order.

Codes are 16-bit since the features step (property-rich states such as glow lichen or dripleaves each get a
code of their own). The switch from byte to char arrays cost nothing measurable on one E-core (Java 8, 12x12
chunks, fill + surface + carvers): seed 42 2.694 -> 2.684 ms/chunk, 12345 3.052 -> 3.056, 8675309 3.223 -> 3.206,
same checksums (the dumps stay one byte per block; the checks compare code values).

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
sh tools/terrain/run.sh FeatureCheck E:/MincraftModding/Workspaces/MC1182Reference/oracle-out/feat-ores 8 <the 29 ore names>
tools\terrain\bench.cmd 8 DecorationBench 42 12 5 0 0
python tools/terrain/make_feature_tables.py E:/MincraftModding/Workspaces/MC1182Reference/oracle-out/feat-ores/feature_tables.txt
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

1. Features (placed features, GenerationStep.Decoration). Done: the decoration loop, its oracle, every ore,
   springs, disks, underwater magma, lava lakes, monster rooms, the geode, dripstone, lush caves but
   rooted_azalea_tree, glow lichen, freeze_top_layer, icebergs, forest rock, fossils, ice spike / patch, desert
   well and blue ice, and the first trees with fancy oaks (above): every step but VEGETAL. Done since: every plant of VEGETAL
   (above). Left: the other trees (dark oak and huge mushrooms, jungle, mega spruce and pine, swamp oak, azalea and
   rooted_azalea_tree). Left, as streams on the same framework, each checked with `-Dfeatures` and `FeatureCheck`: cave
   features (geodes, dripstone clusters / large / pointed, lush caves, monster rooms, fossils (templates), springs,
   lava lakes, underwater magma); surface (disks, icebergs, ice spikes and patches, blue ice, desert wells, forest
   rocks, freeze top layer, which reads block light: the oracle region has no light engine yet, see below); trees
   and vegetation; then structure pieces in each step. The notes on vanilla's loop below stay as the reference:
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
   - Biome climate (done): `Biomes` has every biome's base temperature, FROZEN modifier, downfall and
     precipitation from vanilla's registry; `temperature` and `coldEnoughToSnow` match vanilla's at 244,000
     samples (every biome, any height, out to 3 million blocks; `BiomeTemperatureCheck` against
     `VanillaBiomeInfoDump`).
2. The chunk generator around it for 1.12.2: worker threads computing chunks ahead in tiles (sharing border
   corners between neighbors saves about a quarter of the corner work), writing sections directly.
3. The extended-height coremod (`docs/EXTENDED_HEIGHT.md`), without which none of this reaches a world.

Biome temperature (`Biomes.temperature`) is filled in only for the two frozen oceans, the only biomes 1.18.2's
overworld surface asks about; snow and freezing features will need the rest.

Further speed, measured but not built: noise for a whole column at a time (x and z work shared by its 49
corners), flattened octave loops for the one-octave cave noises, tiles of chunks.
