# Stream D: deepslate, stone and ores

Package `deepslate`, shared building blocks in `common` (`BlockBasic`, `BlockCCStairs`, `BlockCCSlab`,
`ModSounds`; walls are Backport Core's `block.ModernWall`). Config `deepslate`: `enabled`, `rawOreDrops`, `generate`, `layerTop` (16),
`layerFull` (8), `tuff`, `dimensions` ({0}), `skipWorldTypes` (flat, debug).

## Changelog section

*   **Deepslate** forms the bottom of the world in newly generated chunks: everything below y 8, and more and
    more of the stone between y 8 and 16. It drops **cobbled deepslate** (Silk Touch gives deepslate back), and
    smelting cobbled deepslate gives deepslate.
*   **Polished deepslate**, **deepslate bricks** and **deepslate tiles** (each crafted from four of the one
    before), their **cracked** forms (smelted) and **chiseled deepslate** (two cobbled deepslate slabs).
    Cobbled, polished, brick and tile deepslate each have **stairs**, **slabs** and **walls**. The walls have
    1.16's low and tall sides and join 1.12's cobblestone walls.
*   Cobbled deepslate makes stone tools, furnaces and anything else that takes cobblestone.
*   **Deepslate ores**: coal, iron, gold, redstone, lapis, diamond and emerald, wherever an ore lies in the
    deepslate. They drop what the stone ores drop.
*   **Raw iron** and **raw gold**: iron and gold ore (stone and deepslate) now drop them, more with Fortune,
    and still drop themselves with Silk Touch. Smelt them into ingots, or store nine in a **block of raw iron**
    or **raw gold**. Config `rawOreDrops` brings back the old drops.
*   **Tuff** in blobs in the deepslate, **infested deepslate** (a silverfish inside; Silk Touch gives
    deepslate), **calcite** and **smooth basalt** (both mostly in amethyst geodes; smooth basalt is also
    smelted from basalt when the Nether Update backport is installed).
*   Granite, diorite and andesite no longer show inside the deepslate layer, as in 1.18.

## How it works

*   **The layer is swapped in after population**, not while terrain is laid out, so 1.12's caves and ravines
    (which carve only stone and dirt) and every mod's ore generators (which replace only stone) work as
    before. A chunk is written by its own populator and those of its west, north and north-west neighbors,
    so it is swapped once all four have finished; the swap runs as the last world generator, on chunks it
    finds ready, with a ten-second sweep for any whose neighbors were unloaded at the time. It writes only to
    the chunk's own storage (no neighbor updates, no light change) and resends a chunk players already have.
*   **Only chunks generated with the mod**: each chunk's saved data carries a status byte
    (`cavesandcliffsbackport.deepslate`: 0 never, 1 pending, 2 done). Chunks from before the mod have none and
    are never touched; a chunk generated with `generate` off is marked 0 for good. Worlds whose height already
    goes below y 0 (another extended-height terrain) are skipped, through Core's `WorldHeights.minY`.
*   **The swap table**: stone (every variant) to deepslate, each stone ore to its deepslate ore (lit redstone
    ore to lit deepslate redstone ore), infested stone to infested deepslate, and stream Cu's copper ore, by
    name, when it exists. Modded ores not in the table stay as they are.
*   **Deepslate redstone ore** is one block with a `lit` property (light 9), as current Java has it.
*   **Slabs** are 1.12's two blocks: `<name>_slab` (half, `half=bottom|top`) and `<name>_double_slab`, the
    family's released naming. A one-value `variant=default` property satisfies 1.12's slab code.
*   **Walls** (Backport Core's `block.ModernWall`, moved there from this mod's `BlockCCWall` in Core 1.2.0) are their own block: each side none, low or tall, a post unless the wall
    runs straight; tall sides under a solid block or another wall; all of it actual state (meta 0); 1.5-high
    collision; one model name for all 162 states (VintageFix-safe).
*   **Raw ore drops** replace the ore item in `HarvestDropsEvent` at high priority, so auto-smelting and
    doubling handlers later in the chain see raw ore.
*   **Numbers** (current Java): hardness/blast resistance as in `DeepslateContent`'s comment; lapis 4-9, redstone
    4-5 (+0..Fortune), Fortune's ore formula on coal, lapis, diamond, emerald and the raw ores; smelting
    experience 0.1 (deepslate, cracked), coal 0.1, iron 0.7, gold 1.0, redstone 0.7, lapis 0.2, diamond and
    emerald 1.0. Blast resistance is given in Java's units and converted (`BlockBasic.resistance`).
*   Ore dictionary: `ore<Name>` for the deepslate ores, `rawIron`, `rawGold`, `blockRawIron`, `blockRawGold`,
    `cobblestone` (cobbled deepslate), `stone` (deepslate), `stoneCalcite`, `stoneTuff`.
*   Every block name is declared to Core's `BlockOwnership` for the later updates that copy them.

## Tested headlessly

`./gradlew runServer -Pselftest=scaffold,deepslate` (port 25841), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=1507 fail=0`**; **37 blocks, 846 states, 35 item models, 190 sound files, 0 errors**.

*   All 37 blocks registered, every state's metadata round trip, an item for each but the double slabs;
    hardness, blast resistance (deepslate 6, ores 3, stairs/slabs/walls 6) and harvest levels; ore names;
    the names declared to Core.
*   Breaking as a survival player with real tools: deepslate gives cobbled deepslate, itself with Silk Touch,
    nothing by hand; diamond ore nothing with a stone pickaxe; each ore its item; iron and gold ore (vanilla
    and deepslate) raw ore, the ore with Silk Touch; Fortune III raw iron averaging about 2.2; lapis 4-9;
    redstone 4-5; with `rawOreDrops` off, the ores drop themselves. Infested deepslate lets out one silverfish
    and drops nothing; with Silk Touch, deepslate and no silverfish.
*   Redstone ore lights when clicked (light 9) and goes out on a random tick.
*   Crafting (polished, bricks, tiles, stairs 4, slabs 6, walls 6, chiseled, raw blocks both ways, a stone
    pickaxe from cobbled deepslate) and smelting (deepslate, cracked bricks, raw ores, lapis ore).
*   Walls: lone post; straight run of low sides with no post; tall under stone; torch above forces the post;
    joining a cobblestone wall (and it joining ours) and a solid block, not glass; collision 1.5 high.
*   Slabs: placed by a player on the floor as a bottom slab, a second makes the double slab, which drops two.
*   The swap on a hand-built chunk: no stone below 8, none swapped at 16 or above, the 8-16 blend at the
    expected share, bedrock untouched, ores, lit redstone ore and infested stone swapped, granite swapped,
    deepslate upright.
*   **Real generation**: a fresh 6x6-chunk area from the world generator: all 16 inner chunks converted, about
    35,000 deepslate, 1,150 deepslate ores, 390 tuff, no stone or stone ores below y 8; saved status 2; a chunk
    loaded without a status is never converted.
*   Switched off (`enabled=false`, fresh world): the server boots and nothing registers.

## Not tested

*   Anything visual: models, textures, the mirrored deepslate variants, wall shapes on screen.
*   Sounds playing.
*   A chunk already sent to a player being resent after the swap (no client).
*   The stonecutter and blast furnace with Village & Pillage installed; smooth basalt from Nether Update's
    basalt.
*   Silverfish hiding in deepslate (current Java turns deepslate they enter into infested deepslate): not built.
*   Turning a stream off in a world that has its blocks stops a dedicated server at FML's missing-blocks prompt
    (vanilla 1.12 behavior; `-Dfml.queryResult=confirm` answers it).
