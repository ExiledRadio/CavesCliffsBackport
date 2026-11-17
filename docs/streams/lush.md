# Stream L: lush caves

Package `lush` (client part in `lush.client`). Config `lush`: `enabled`, `glowLichen`, `azaleaTrees`
(`lichenTries` is gone: the count is 1.18.2's); the caves themselves are behind `caves.lushRegions`, which was already there. No packets (ids 25-29
unused), no entities, no advancements (1.17 has none for these). Also changed outside the package: the lush
category's settings icon in `ModConfig` is now our moss block.

## Changelog section

*   **Lush caves** generate in some regions underground: floors and ceilings of **moss**, grass, **moss
    carpets**, **azaleas**, **clay** with pools of water and **dripleaves**, **cave vines** with **glow berries**
    and **spore blossoms**. An **azalea tree** on the surface marks the lush cave below it: **rooted dirt** runs
    from its roots down to the cave, ending in **hanging roots**.
*   **Glow lichen** grows on cave walls everywhere. It gives a little light, takes shears to collect, and
    spreads with bone meal.
*   **Moss block**: bone meal spreads moss over nearby stone and dirt and grows plants on it. Makes moss carpet,
    mossy cobblestone and mossy stone bricks.
*   **Azalea** and **flowering azalea**: bushes you can stand on, potted like any flower, grown into azalea trees
    with bone meal. **Azalea leaves** and **flowering azalea leaves** drop them.
*   **Rooted dirt**: bone meal grows hanging roots under it; a hoe turns it to dirt and pulls the roots out.
*   **Cave vines** grow down from ceilings and can be climbed. Use one to pick its **glow berries**, which are
    food and plant new vines; bone meal grows berries, shears stop a vine growing.
*   **Big dripleaf**: a leaf to stand on, briefly: it tilts and drops whatever is on it, unless redstone holds
    it up. **Small dripleaf** grows into a big one with bone meal.
*   **Spore blossom**: hangs from ceilings and fills the air with green spores.

## How it works

*   **Glow lichen is four blocks with one item** (the plan's leaning, as built). Six faces are 64 combinations
    and a 1.12 block has 16 metadata values, so `glow_lichen`, `glow_lichen_down`, `glow_lichen_up` and
    `glow_lichen_up_down` differ in their up and down faces and keep the four sides in metadata. No tile entity.
    Everything goes through `BlockGlowLichen.of(mask)` / `faces(state)`, a six-bit face mask, so a later move onto
    a shared multiface block (CORE_REQUESTS 9) is a change in one class. Only `glow_lichen` has an item; the item
    adds a face to whatever lichen is already in the space. A face whose support goes is lost; shears give one
    lichen per face; bone meal picks one of the ways to spread (another face of the same space, the same face
    one space over, or around the corner) at random.
*   **Cave vines' age** is 0-25 in current Java and shares the tip's 16 metadata values with its berries bit
    here, so it has 8 steps and a growth advances it 7 times in 25: the same expected 25 growths from age 0, the
    same random starting age when planted, the same 10% growth chance a random tick and 11% berry chance for a
    new tip. `cave_vines` is the tip, `cave_vines_plant` everything above it, as in 1.17.
*   **Big dripleaf noticing who stands on it**: 1.12 only tells a block about entities inside its space, and an
    entity on the leaf is above its collision box. The leaf asks for a tick whenever an entity's collision
    search finds it underfoot, and that tick looks for someone standing on it. Items count, as in current Java.
    Timings are current Java's: unstable 10 ticks, partial 10, full 100; redstone power resets it; a projectile
    tilts it fully.
*   **Potted azaleas** are two blocks of our own without a tile entity (1.12's pot keeps its plant in a tile
    entity and accepts only its own list). Using an azalea on an empty vanilla pot swaps the pot for one; using
    the potted azalea gives the plant back and leaves a vanilla pot.
*   **Azalea leaves** extend 1.12's leaves (decay, shears, `treeLeaves`), always drawn see-through, with oak
    logs as their wood. The **azalea tree** is our own implementation of 1.17's bending trunk and random-spread
    foliage (trunk 4-6, bend 1-2, 50 leaf tries per crown point, one leaf in four flowering).
*   **Moss bone meal** is 1.17's moss patch: a 3x3 to 5x5 square without corners, edge columns 75% of the time,
    the ground followed up or down 5, 60% of new moss under vegetation (grass 50, carpet 25, tall grass 10,
    azalea 7, flowering azalea 4). Moss and rooted dirt count as dirt for 1.12's plants.
*   **Drops**: hanging roots, small dripleaf and glow lichen only for shears; cave vines drop their berries;
    every part of a big dripleaf drops one; leaves drop their azalea 5% (Fortune 6.25, 8.3, 10) and sticks 2%.
*   **Fire, fuel, composting**: current Java's fire table (moss does not burn); azaleas burn for 100 ticks;
    composting chances are handed to `common/Compat` (30% moss carpet, small dripleaf, hanging roots, azalea
    leaves, glow berries; 50% flowering leaves, glow lichen; 65% big dripleaf, moss, spore blossom, azalea; 85%
    flowering azalea).
*   **Spore particles** are drawn from 1.12's own particle sheet (the falling drip and the small puff) in
    current Java's green, from `lush.client.SporeParticles`.

### Lush regions (for the integrator)

**Done on merge:** `LushRegions` now delegates to `common/CaveRegions`, so lush regions are the other tail of
dripstone's noise (9% of the underground, never overlapping it). `patch` stays here. The note below is kept as written.

`common/CaveRegions` did not exist, so the region lookup is `lush/LushRegions`: a 3D value noise from the world
seed alone (cells 144 blocks wide, 48 tall; lush where it is above 0.74, 10.8% of the underground on the test seed),
with `isLush(seed, x, y, z)`, `isLush(world, pos)` (config, dimension and world type included) and `mayContain`
(a conservative whole-chunk pre-check). Nothing else in the package knows how regions are made. When
`CaveRegions` arrives, these three methods should delegate to it, and lush and dripstone regions should be
made to exclude each other there (today nothing stops a dripstone region overlapping a lush one). Streams Ax
and Mu need the same answer and should read it from `CaveRegions`, not from here.

`LushCaveGenerator` (world generator weight 10, after ores and geodes) dresses, in each populating chunk's
16x16 window, every cave air block in a lush region that has a floor or a ceiling of stone, dirt, gravel or
sand and lies at least 4 below the column's top block: moss on most floors (70% under vegetation) and
ceilings; clay patches two deep with water wherever it is held in on all sides, a dripleaf on a tenth of the
clay; cave vines under 12% of ceiling moss (1.18's lengths, a fifth of their blocks with berries), spore
blossoms under 1.5%; and one or two tries a chunk at 1.17's root system (azalea tree on the first open ground
within 100 above, rooted dirt down to the cave, hanging roots). Nothing is written outside the window. Glow
lichen is 1.18.2's placed feature (below, "Glow lichen density").

### Generation parity pass (2026-11-13, branch `cc-gen-parity`)

A playtest said azalea trees were too common and lush caves too small. Measured on the playtest saves (a 1.12
world of 3,339 chunks; a `cavesandcliffs` world of 46,870 chunks, whose features are vanilla 1.18.2's exactly):

*   **Azalea trees** were 85 per 1,000 chunks against vanilla's 0.2, nearly always another within 32 blocks
    (median 14 apart against 167). The root system was tried from a lush ceiling of every lush chunk, and 1.12's
    caves are shallow enough that it almost always reached open ground. It now places as 1.18.2's
    rooted_azalea_tree does (`rootedTrees`): 1-2 tries a chunk at a random height 0-255, scanning up at most 12
    through air to a solid ceiling, kept only where that air is lush and underground; then the root system
    climbs at most 100 to air, water, leaves or a replaceable plant standing on `azalea_grows_on` (dirt, sand,
    terracotta, snow) with room above, as `RootSystemFeature` does, rooted dirt stops under the tree's own block,
    and hanging roots go only in air.
*   **Lush caves**: the regions were about 2.3 times narrower than 1.18.2's lush caves biome (moss-bearing areas,
    chunk-weighted median 145 chunks against 792; the largest 173 against 929). `CaveRegions`' lattice is twice as
    wide now (256 and 96 across), the same 9% share. Dripstone regions grow the same way (the other tail of the
    same noise), as 1.18's two cave biomes are alike in size. Worlds generated before this get a seam where old
    and new chunks meet. Lush caves are still dressed 1.12 caves, so they stay thinner than 1.18's noise caves.

Before and after, the dev server (`-Pselftest=genscan`, `GenScanSelfTest`, a square of chunks named in
`eclipse/genscan.txt`) on the playtest seed -2903490073629024182, 3,940 chunks around chunk 21,56, scanned offline:

| | before | after | 1.18.2 (`cavesandcliffs` save) |
| --- | --- | --- | --- |
| azalea trees per 1,000 chunks | 76.6 | 0.3 | 0.2 |
| lush areas, chunk-weighted median / largest (chunks) | 166 / 211 | 342 / 342 | 792 / 929 |
| largest lush patch (blocks) | 9,153 | 12,189 | 42,924 |
| geode amethyst, median | 294 | 370 | 405 |
| buds per geode, median (10th-90th) | 6 (2-11) | 6 (3-11) | 6 (2-11) |

### Glow lichen density (2026-11-16, branch `cc-lichen-density`)

A playtest said glow lichen lit Default-world caves everywhere. The `cavesandcliffs` world type was already right:
`FeatureCheck` with all features (`oracle-out/fdef`, 2,048 chunks on the four oracle seeds) is ALL IDENTICAL, glow
lichen included. 1.18.2's placed `glow_lichen` is the configured feature (search range 20, ceiling and walls but
**not the floor**, chance of spreading 0.5, on stone, andesite, diorite, granite, dripstone block, calcite, tuff,
deepslate) with `UniformInt` 104-157 tries a chunk, RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT (y -64 to 256), in the
square, an OCEAN_FLOOR_WG threshold of -13 and the biome filter; lush caves add nothing of their own.

The Default-world generator was its own: 40 tries a chunk between y 5 and 54, each walking up to 8 blocks down from
stone to find air (so most tries found a cave), any of the six faces including the floor, on stone or dirt. It is
now the feature itself (`LushCaveGenerator.lichen`): the same draws in the same order, the same placement and
spreading (MultifaceBlock's static direction order included), `oceanFloor` for OCEAN_FLOOR_WG, and the tries that
land below y 0 do nothing, so tries are as dense a block as in 1.18.

Measured as `tools/terrain/LichenStats` measures the 1.18.2 dumps (`LichenCensusSelfTest`, `-Pselftest=lichencensus`,
seed 1718, the 484 fully populated chunks of a 24x24 square at 0,0). "Eligible air" is underground air (air or
water at most 13 below the ocean floor) with lichen-able stone above or beside it, the walls lichen can take:

| | before | after | 1.18.2 (`fdef`, 2,048 chunks) |
| --- | --- | --- | --- |
| lichen blocks a chunk (median, 90th percentile) | 4.75 (3, 12) | 1.03 (0, 3) | 3.11 (2, 7) |
| lichen per 1,000 eligible air | 15.8 | 3.43 | 3.93 |
| lichen per 1,000 underground air | 7.18 | 1.56 | 1.19 |
| blocks with a floor face | 7.8% | 1.8% | 4.7% |

Per wall lichen can take, the Default world is now within 13% of 1.18.2 (it was four times as dense); fewer lichen a
chunk is because 1.12's caves have a quarter of 1.18's underground air (661 blocks a chunk against 2,622), not fewer
tries. Exact parity is not possible there (1.12 terrain, java.util.Random).

## Differences from current Java

*   No waterlogging yet (the plan's later pass): glow lichen, hanging roots and both dripleaves go only in air,
    small dripleaf only on clay or moss, and pools hold no small dripleaves.
*   Cave vines' age has 8 steps (above). Glow lichen has no sound set of its own in 1.18 either (grass sounds).
*   Lush caves are regions inside 1.12's caves rather than a biome; the 1.18 terrain stream replaces this.
*   Wandering trader trades for moss, rooted dirt, small dripleaf and glow berries are not added (Core 1.1.0
    has no trade registry yet).

## Tested headlessly

`./gradlew runServer -Pselftest=all` (port 25845), then `python tools/check_assets.py`. Last full run:
**`SELFTEST DONE pass=2495 fail=1`**; **108 blocks, 1375 states, 95 item models, 703 sound files, 0 errors**.
The one failure was a wrong assertion in the test itself (it forgot the rooted dirt a tree leaves in the cave
floor); it was removed and the run was **not repeated**. `./gradlew build` and the `func_` check of the release
jar were **not run** either: both are owed before merging.

*   Registration, metadata round trips, all 63 glow lichen face masks, light, push reactions, fuel, fire table.
*   Moss bone meal (44 moss, plants, never the corners or past 3 blocks), carpet support and drop.
*   Azalea soils, the 45% bone meal chance, a grown tree (5 logs, 60 leaves, 21 flowering, rooted dirt under),
    no tree without room, leaf drops over 4,000 breaks, shears.
*   Potted azaleas through the real right-click path: in, out, and broken.
*   Rooted dirt: bone meal, the hoe (dirt, hanging roots, one durability); hanging roots and shears; spore blossom.
*   Cave vines: planting with glow berries under a block and under a vine, growth to 30 over random ticks,
    bone meal, picking, shears, breaking in the middle, losing the ceiling, climbing.
*   Big dripleaf: placing and stacking, bone meal, the whole tilt cycle under a pig, redstone, an arrow, the
    collapse and its drops. Small dripleaf: soils, both halves, shears, bone meal into a big one.
*   Glow lichen: placing faces with the item, joining, the up block, lost support, shears per face, spreading.
*   Recipes; region share, determinism, and the chunk pre-check never skipping a lush block.
*   A built cave in a lush spot dressed by the generator (moss, clay, vines, blossoms, plants, dripleaves, a
    rooted azalea tree, nothing outside the window), lichen generation (2,000 tries), and 64 really generated
    chunks (312 lichen before the density pass, 4,439 moss, 350 vine blocks, 823 clay, 74 dripleaves, azalea trees).
*   Switched off (fresh world): boots, scaffold test passes.

## Not tested

*   Everything client-side: models, the cut-out leaves, spore particles, the glow lichen item's choice of face
    from where a real player looks, the creative tab places.
*   A real entity walking onto a big dripleaf over real ticks (the test hands the leaf a standing pig and runs
    its ticks by hand), and items resting on one.
*   Sounds being heard.
*   Composting (needs the mod with the composter in the dev environment) and fire spreading in play.
*   Leaf decay over time (vanilla's code, with oak logs as the wood).
*   Pistons breaking the plants in play (the push reaction is checked).
*   Performance of the cave pass on a long pregeneration run.
