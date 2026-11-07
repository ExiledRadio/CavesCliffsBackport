# Stream Cu: copper and lightning rods

Package `copper`. Config `copper`: `enabled`, `oxidation`, `lightningRods`, `waxItems`
({"buzzybeesbackport:honeycomb"}), `generate`, `veinsPerChunk` (6), `veinSize` (10), `minY` (1), `maxY` (96).
Shared code it added to `common`: `BlockCCOre` (moved from `deepslate`, with a hardness/resistance
constructor) and `Advancements`.

## Changelog section

*   **Copper ore** and **deepslate copper ore** generate between y 1 and 96 and drop 2 to 5 **raw copper**
    (more with Fortune). Smelt raw copper or the ore into **copper ingots**; store nine raw copper in a
    **block of raw copper**.
*   **Blocks of copper** (nine ingots), **cut copper** (four blocks make four), and cut copper **stairs** and
    **slabs**. Left in the open, copper slowly **oxidizes**: exposed, weathered, then oxidized, a patch ageing
    together.
*   **Waxing**: use a honeycomb on copper, or craft them together, to keep it as it is (*Wax On*). An **axe**
    takes the wax off (*Wax Off*), or scrapes off one stage of oxidation.
*   **Lightning rods** (three ingots): lightning from a storm strikes the nearest rod within 128 blocks
    instead of the ground, players or animals around it. A struck rod sends out a redstone signal, and the
    copper it stands on is cleaned back to new, with some of the copper around it. *Surge Protector* for
    protecting a villager this way.
*   Lightning striking copper cleans it the same way.
*   With the Update Aquatic backport, drowned killed by players sometimes drop a copper ingot.
*   Honeycomb comes from the Buzzy Bees backport, or any mod whose honeycomb has the ore name `honeycomb`
    (config `waxItems` adds more).

## How it works

*   **Names** are 1.18's: `copper_block`, `exposed_copper`, `weathered_copper`, `oxidized_copper`,
    `<stage>cut_copper`, `<stage>cut_copper_stairs`, `<stage>cut_copper_slab`, all with `waxed_` in front for
    the waxed forms (`waxed_copper_block`, `waxed_exposed_cut_copper_stairs`), plus 1.12's double slab blocks
    `<stage>cut_copper_double_slab` and `waxed_<stage>cut_copper_double_slab`. The full list and states are in
    the family notes (`caves-and-cliffs-copper-states.md`) for Tricky Trials' copies.
*   **`Weathering`** holds the chains (per shape: four stages, unwaxed; double slabs have their own chain) and
    the waxed pairs, and copies states across by property, so stairs keep facing, half and shape and slabs
    their half. It has the method names Core would keep if it takes the table over (CORE_REQUESTS 3).
*   **Oxidation** (current Java): a random tick tries with chance 64/1125; any less-oxidized unwaxed copper
    within Manhattan distance 4 stops it; otherwise it ages with chance ((older + 1) / (older + same + 1))^2,
    times 0.75 while unaffected. A lone block oxidizes fully in about 70 of its random ticks. 1.12's stairs
    hand random ticks to their model block, which would turn stairs into a full block; ours tick for
    themselves. Waxed and oxidized blocks do not tick.
*   **Waxing and scraping** are a right-click handler: both sides cancel the click (the arm swings, nothing
    else happens), the server swaps the block, plays 1.17's sound, uses one honeycomb or one axe durability
    (not in creative), and grants the advancement.
*   **Lightning**: 1.12 posts `EntityJoinWorldEvent` when a bolt is added, so no patch is needed. A real bolt
    from a storm (found by its call coming from the world's weather tick, as Java redirects only those) is
    cancelled when a rod qualifies, the fire 1.12 already lit in the cancelled bolt's constructor is put out,
    and a new bolt is added above the rod. Rods qualify when they top their column and rain falls on them;
    they are found through an empty tile entity each rod carries, among the loaded tile entities (1.12 has no
    points of interest). Every real bolt then cleans the copper it hits (or the copper a struck rod stands
    on): to unaffected, then 3-5 random walks of 1-8 steps through touching copper, one stage off each.
*   **The rod**: six facings, `powered` for 8 ticks after a strike: weak signal 15 to every side, strong 15 into
    the block it stands on. Placed against a rod pointing the same way, it points the other way.
*   **Ore veins** are our own: a short line of overlapping blobs, as 1.12's ores, inside the population window;
    in stone (granite, diorite, andesite included); a vein in the deepslate layer gives deepslate copper ore,
    and stream D's layer swap turns copper ore below y 8 into deepslate copper ore.
*   **Ore dictionary**: `oreCopper`, `ingotCopper`, `rawCopper`, `blockCopper`, `blockRawCopper`, and the
    configured wax items get `honeycomb`, which the grid waxing recipes use.
*   Numbers: hardness 3 and blast resistance 6 for every copper block and the rod, a stone pickaxe; copper ore
    3/3, deepslate copper ore 4.5/3; raw copper block 5/6; smelting experience 0.7.

## Tested headlessly

`./gradlew runServer -Pselftest=scaffold,deepslate,copper` (port 25842), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=2009 fail=0`** (502 of them copper's); **81 blocks, 1221 states, 73 item models, 243 sound
files, 0 errors**.

*   44 blocks with 1.18's names, metadata round trips, which blocks tick, the chains and wax pairs (stairs,
    slabs, double slabs), blast resistance, harvest level, ore names, names declared to Core.
*   Breaking: copper ore gives 2-5 raw copper with a stone pickaxe (average about 3.5 over 40), nothing with a
    wooden one; Silk Touch deepslate copper ore; copper blocks and the rod drop themselves.
*   Oxidation chances exactly as the formula for a lone block, a pair, a mixed group, a waxed neighbor and a
    younger neighbor; a lone block oxidizing fully in a plausible number of random ticks; stairs ageing
    without losing facing or half; a top slab staying a top slab; waxed copper never ageing.
*   Through the real right-click event with a server player: waxing (one honeycomb used, *Wax On*), no
    double waxing, wax off (*Wax Off*), scraping stage by stage to unaffected and no further, one durability
    each, stairs keeping their facing, nothing on stone. Honeycomb stands in as paper with the ore name.
*   Lightning: the rod found from 40 blocks, none from 200; a natural bolt 40 blocks away redirected to the
    rod (the original cancelled, a bolt above the rod); the rod powered (weak and strong as above, the block
    under it powered) and off again after its tick; the copper under it cleaned and some of the patch
    around; *Surge Protector* for a player with a villager near; a non-natural bolt not redirected but still
    cleaning the copper it hits.
*   Recipes (block, ingots from waxed block, cut copper, waxed slabs, rod, waxing in the grid) and smelting.
*   Veins: about 7 blocks each in solid stone (1.12's own size-10 veins: about 8.5); real generated chunks:
    about 20 copper ore per chunk, some as deepslate copper ore in the layer.
*   Switched off (fresh world): boots; stream D's test still passes without it.

## Not tested

*   Anything visual or audible: models, textures, the rod's powered look, sounds and subtitles.
*   A thunderstorm's own random strike reaching the rod (the test marks its bolt as natural; the stack check
    for the weather tick is not exercised).
*   Drowned drops (needs the Update Aquatic backport; the code checks its registry name).
*   Honeycomb from the Buzzy Bees backport itself (the config default names it; not installed here).
*   Rods with waterlogging (not built: waits for Core's adapter).
