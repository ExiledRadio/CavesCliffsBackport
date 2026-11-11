# Terrain core: 1.18.2's noise fill, exact and fast

Branch `cc-terrain`, package `terrain/core`. The first part of stream T's terrain: what 1.18.2 does in
`NoiseBasedChunkGenerator.fillFromNoise` (terrain shape, cheese, spaghetti and noodle caves, aquifers, ore veins),
from y -64 to 319. Plain Java 8 with no Minecraft classes, so it compiles, tests and benchmarks without Gradle or
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

## Running the tools

```
sh tools/terrain/run.sh TerrainCheck E:/MincraftModding/Workspaces/MC1182Reference/oracle-out
tools\terrain\bench.cmd 8 TerrainBench 42 12 5            (one E-core; 8, 17, 21 or 25 picks the JDK)
E:\MincraftModding\Workspaces\MC1182Reference\bench-vanilla.cmd 42 12 5
```

The oracle is `tools/terrain/oracle/*.java`, copies of the sources that live in the reference workspace
`Workspaces/MC1182Reference` (ForgeGradle 5, Mojang names, JDK 17): `gradlew oracle`, and `VanillaFillDump` /
`VanillaFillBench` through `vanilla.args` (written by `make_args.py`). Run benchmarks pinned to E-cores.

## Not done

Terrain, in the order to build it, each checked against the oracle the same way:

1. Biomes: the multi-noise biome source (1.18.2's parameter list and its search), on the same climate noises.
2. Surface rules (grass, sand, deepslate gradient, badlands bands, icebergs), which need the biomes.
3. Carvers (1.18 caves and canyons), features (ores, trees, geodes, dripstone, lush caves, springs, lakes) and
   structure placement with 1.18.2's numbers; the beardifier's terrain adaptation.
4. The chunk generator around it for 1.12.2: worker threads computing chunks ahead in tiles (sharing border
   corners between neighbors saves about a quarter of the corner work), writing sections directly.
5. The extended-height coremod (`docs/EXTENDED_HEIGHT.md`), without which none of this reaches a world.

Further speed, measured but not built: noise for a whole column at a time (x and z work shared by its 49
corners), flattened octave loops for the one-octave cave noises, tiles of chunks.
