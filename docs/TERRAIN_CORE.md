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

## Running the tools

```
sh tools/terrain/run.sh TerrainCheck E:/MincraftModding/Workspaces/MC1182Reference/oracle-out
tools\terrain\bench.cmd 8 TerrainBench 42 12 5            (one E-core; 8, 17, 21 or 25 picks the JDK)
E:\MincraftModding\Workspaces\MC1182Reference\bench-vanilla.cmd 42 12 5
sh tools/terrain/run.sh BiomeCheck E:/MincraftModding/Workspaces/MC1182Reference/oracle-out
sh tools/terrain/run.sh SurfaceCheck E:/MincraftModding/Workspaces/MC1182Reference/oracle-out
sh tools/terrain/run.sh BiomeSelfTest 2000000
tools\terrain\bench.cmd 8 SurfaceBench 42 12 5
E:\MincraftModding\Workspaces\MC1182Reference\bench-surface-vanilla.cmd 42 12 5    (append 0 0 cold for a cold search)
```

The biome and surface oracle classes (`VanillaWorld`, `VanillaBiomeDump`, `VanillaSurfaceDump`,
`VanillaSurfaceBench`, copies in `tools/terrain/oracle`) are compiled straight into the reference workspace with
`javac @javac.args -d build/classes/java/main ...` (JDK 17; `javac.args` is `-cp` and vanilla.args' classpath
line) and run with `java @vanilla.args net.minecraft.world.level.levelgen.<class>`:
`VanillaBiomeDump oracle-out 24 400 1024` and `VanillaSurfaceDump oracle-out 12 200 4` wrote the gzipped dumps
(about 0.1 MB and 1.5 MB per seed).

The oracle is `tools/terrain/oracle/*.java`, copies of the sources that live in the reference workspace
`Workspaces/MC1182Reference` (ForgeGradle 5, Mojang names, JDK 17): `gradlew oracle`, and `VanillaFillDump` /
`VanillaFillBench` through `vanilla.args` (written by `make_args.py`). Run benchmarks pinned to E-cores.

## Not done

Terrain, in the order to build it, each checked against the oracle the same way:

Done: biomes and surface rules (above).

1. Carvers (1.18 caves and canyons), features (ores, trees, geodes, dripstone, lush caves, springs, lakes) and
   structure placement with 1.18.2's numbers; the beardifier's terrain adaptation.
2. The chunk generator around it for 1.12.2: worker threads computing chunks ahead in tiles (sharing border
   corners between neighbors saves about a quarter of the corner work), writing sections directly.
3. The extended-height coremod (`docs/EXTENDED_HEIGHT.md`), without which none of this reaches a world.

Biome temperature (`Biomes.temperature`) is filled in only for the two frozen oceans, the only biomes 1.18.2's
overworld surface asks about; snow and freezing features will need the rest.

Further speed, measured but not built: noise for a whole column at a time (x and z work shared by its 49
corners), flattened octave loops for the one-octave cave noises, tiles of chunks.
