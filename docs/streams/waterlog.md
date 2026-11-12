# Waterlogging

Backport Core's `waterlog` API (`Waterloggable`, `Waterlogging`) over Fluidlogged API 3.3.3, which is optional.
No block has a `waterlogged` property: the water is Fluidlogged API's, and Core's patch P19 makes our
`Waterloggable` classes Fluidlogged API's `IFluidloggable`. Core's `WaterRules` lets them take water sources only,
never flowing water or lava.

## Changelog section

* Candles, pointed dripstone, glow lichen, lightning rods, amethyst buds and clusters, hanging roots, small and
  big dripleaf, light blocks, and the deepslate and cut copper stairs, slabs and walls can be waterlogged (with
  Fluidlogged API installed).
* Water puts a lit candle out, and a waterlogged candle can't be lit.
* A waterlogged dripstone tip doesn't drip; water kept in a block above a stalactite's root fills cauldrons.
* Glow lichen spreads into water. Small dripleaf grows on dirt under water. Big dripleaf grows up through water.
* Lush cave clay pools have dripleaf standing in them; dripstone caves grow into water.

## How it works

| Block | 1.18.2 rule kept |
| --- | --- |
| `candle/BlockCandle` (not cakes) | `onWaterChanged` puts a lit one out; `BlockAbstractCandle.canLight(world, pos, state)` refuses while waterlogged (flint and steel, fire charges, burning arrows) |
| `dripstone/BlockPointedDripstone` | `canDrip(world, pos, state)` false when waterlogged; `fluidAbove` counts a water source kept in a block; tips never grow into water and drips never fall through it; `grow` goes into water and keeps it |
| `lush/BlockGlowLichen`, `ItemGlowLichen` | spreads into (and is placed into) water sources; not into flowing water or lava |
| `lush/BlockSmallDripleaf` | `Soil.smallDripleaf(world, pos)`: clay or moss, or dirt/farmland with a water source in its own space (only when Fluidlogged API can keep it); drained on dirt it breaks |
| `lush/BlockBigDripleaf` (+ `Stem`) | `canReplace`: air, water or small dripleaf; every part placed with `placeKeepingWater` |
| `blocks/ItemLight` | placed into water it takes the water's place; only a bucket waterlogs a light |
| `common/BlockCCSlab` | `canHoldWater = !isDouble()` |
| roots, clusters and buds, lightning rod, stairs, walls | plain `Waterloggable` |

Worldgen: `LushCaveGenerator` places dripleaf in clay pools (with Fluidlogged API only, or the pool would drain
through it), and lichen and hanging roots into air or water. `DripstoneCaveGenerator` grows pointed dripstone into
air or water and waterlogs every segment where water was (`placeWithWater`, as `isWaterAt`). Geodes need nothing:
a bud set into a water source keeps it.

`build.gradle`: `-PnoFluidlogged` runs `runServer`/`runClient` without Fluidlogged API.

## Tested

`-Pselftest=waterlog` (`WaterlogSelfTest`, `WaterlogFluidChecks`):

* Every block of ours is `Waterloggable` exactly where 1.18.2 waterlogs it.
* All 1,577 states were checked: 1,443 take a water source; none take flowing water or lava.
* Placed into a source, each kind keeps it; double slabs and candle cakes don't.
* Candle: water puts it out, it can't be lit, and drained it can be lit again.
* Dripstone: a waterlogged tip doesn't drip, and water in a slab above the root counts.
* Lichen goes into a source but not into flowing water.
* Small dripleaf stands on dirt only in water and breaks when drained. A big dripleaf keeps the water in every part.
* A light placed by its item removes the water.
* A spring flowing beside dry candles, stalagmites and lichen neither enters nor washes them away.

Whole suite, fresh world: 0 failures with Fluidlogged API (3 runs) and without it (`-PnoFluidlogged`, which
takes the test's dry path).

## Not tested

* Client-side: water rendering inside our models (Fluidlogged API draws it).
* Buckets used on our blocks by a player, and sources forming between them (Core's tests cover both for its own blocks).
* Waterlogged blocks outside y 0-255 in extended worlds (Backport Core work, stream C).
