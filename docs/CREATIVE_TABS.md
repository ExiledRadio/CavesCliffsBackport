# Creative tabs

Caves & Cliffs Backport has no creative tab of its own. Each block and item is in the vanilla tab Minecraft 1.19.2 gives it, next to its vanilla neighbor in 1.19.2's order, through Backport Core's creative placement (`creative.CreativePlacement`; the rule and the table are in Backport Core's `docs/CREATIVE_TABS.md`). Items are placed by their vanilla name: `common/Content` places every block with an item and every item, and the main class's pre-init the spawn eggs.

"After" and "Before" are the stacks either side of it with this mod and Backport Core alone, from a dev client's `-Dbackportcore.creativeDump` (an item is put after its nearest 1.19.2 neighbor before it, or before the one after it, whichever is of its kind). With other backports installed an item moves to stay by its 1.19.2 neighbor: stripped logs follow the warped stem once the Nether Update adds it, and so on.

## Building Blocks

| Item | After | Before |
|---|---|---|
| `deepslate` | `polished_andesite` | `cobbled_deepslate` (this mod) |
| `cobbled_deepslate` | `deepslate` (this mod) | `polished_deepslate` (this mod) |
| `polished_deepslate` | `cobbled_deepslate` (this mod) | `calcite` (this mod) |
| `calcite` | `polished_deepslate` (this mod) | `tuff` (this mod) |
| `tuff` | `calcite` (this mod) | `grass_block` |
| `deepslate_gold_ore` | `gold_ore` | `iron_ore` |
| `deepslate_iron_ore` | `iron_ore` | `copper_ore` (this mod) |
| `copper_ore` | `deepslate_iron_ore` (this mod) | `deepslate_copper_ore` (this mod) |
| `deepslate_copper_ore` | `copper_ore` (this mod) | `coal_ore` |
| `deepslate_coal_ore` | `coal_ore` | `oak_log` |
| `tinted_glass` | `glass` | `lapis_ore` |
| `deepslate_lapis_ore` | `lapis_ore` | `lapis_block` |
| `raw_iron_block` | `gold_block` | `raw_copper_block` (this mod) |
| `raw_copper_block` | `raw_iron_block` (this mod) | `raw_gold_block` (this mod) |
| `raw_gold_block` | `raw_copper_block` (this mod) | `amethyst_block` (this mod) |
| `amethyst_block` | `raw_gold_block` (this mod) | `budding_amethyst` (this mod) |
| `budding_amethyst` | `amethyst_block` (this mod) | `iron_block` |
| `copper_block` | `iron_block` | `smooth_stone_slab` |
| `deepslate_diamond_ore` | `diamond_ore` | `diamond_block` |
| `exposed_copper` | `diamond_block` | `weathered_copper` (this mod) |
| `weathered_copper` | `exposed_copper` (this mod) | `oxidized_copper` (this mod) |
| `oxidized_copper` | `weathered_copper` (this mod) | `cut_copper` (this mod) |
| `cut_copper` | `oxidized_copper` (this mod) | `exposed_cut_copper` (this mod) |
| `exposed_cut_copper` | `cut_copper` (this mod) | `weathered_cut_copper` (this mod) |
| `weathered_cut_copper` | `exposed_cut_copper` (this mod) | `oxidized_cut_copper` (this mod) |
| `oxidized_cut_copper` | `weathered_cut_copper` (this mod) | `cut_copper_stairs` (this mod) |
| `cut_copper_stairs` | `oxidized_cut_copper` (this mod) | `exposed_cut_copper_stairs` (this mod) |
| `exposed_cut_copper_stairs` | `cut_copper_stairs` (this mod) | `weathered_cut_copper_stairs` (this mod) |
| `weathered_cut_copper_stairs` | `exposed_cut_copper_stairs` (this mod) | `oxidized_cut_copper_stairs` (this mod) |
| `oxidized_cut_copper_stairs` | `weathered_cut_copper_stairs` (this mod) | `cut_copper_slab` (this mod) |
| `cut_copper_slab` | `oxidized_cut_copper_stairs` (this mod) | `exposed_cut_copper_slab` (this mod) |
| `exposed_cut_copper_slab` | `cut_copper_slab` (this mod) | `weathered_cut_copper_slab` (this mod) |
| `weathered_cut_copper_slab` | `exposed_cut_copper_slab` (this mod) | `oxidized_cut_copper_slab` (this mod) |
| `oxidized_cut_copper_slab` | `weathered_cut_copper_slab` (this mod) | `waxed_copper_block` (this mod) |
| `waxed_copper_block` | `oxidized_cut_copper_slab` (this mod) | `waxed_exposed_copper` (this mod) |
| `waxed_exposed_copper` | `waxed_copper_block` (this mod) | `waxed_weathered_copper` (this mod) |
| `waxed_weathered_copper` | `waxed_exposed_copper` (this mod) | `waxed_oxidized_copper` (this mod) |
| `waxed_oxidized_copper` | `waxed_weathered_copper` (this mod) | `waxed_cut_copper` (this mod) |
| `waxed_cut_copper` | `waxed_oxidized_copper` (this mod) | `waxed_exposed_cut_copper` (this mod) |
| `waxed_exposed_cut_copper` | `waxed_cut_copper` (this mod) | `waxed_weathered_cut_copper` (this mod) |
| `waxed_weathered_cut_copper` | `waxed_exposed_cut_copper` (this mod) | `waxed_oxidized_cut_copper` (this mod) |
| `waxed_oxidized_cut_copper` | `waxed_weathered_cut_copper` (this mod) | `waxed_cut_copper_stairs` (this mod) |
| `waxed_cut_copper_stairs` | `waxed_oxidized_cut_copper` (this mod) | `waxed_exposed_cut_copper_stairs` (this mod) |
| `waxed_exposed_cut_copper_stairs` | `waxed_cut_copper_stairs` (this mod) | `waxed_weathered_cut_copper_stairs` (this mod) |
| `waxed_weathered_cut_copper_stairs` | `waxed_exposed_cut_copper_stairs` (this mod) | `waxed_oxidized_cut_copper_stairs` (this mod) |
| `waxed_oxidized_cut_copper_stairs` | `waxed_weathered_cut_copper_stairs` (this mod) | `waxed_cut_copper_slab` (this mod) |
| `waxed_cut_copper_slab` | `waxed_oxidized_cut_copper_stairs` (this mod) | `waxed_exposed_cut_copper_slab` (this mod) |
| `waxed_exposed_cut_copper_slab` | `waxed_cut_copper_slab` (this mod) | `waxed_weathered_cut_copper_slab` (this mod) |
| `waxed_weathered_cut_copper_slab` | `waxed_exposed_cut_copper_slab` (this mod) | `waxed_oxidized_cut_copper_slab` (this mod) |
| `waxed_oxidized_cut_copper_slab` | `waxed_weathered_cut_copper_slab` (this mod) | `cobblestone_stairs` |
| `deepslate_redstone_ore` | `redstone_ore` | `ice` |
| `smooth_basalt` | `soul_sand` | `glowstone` |
| `deepslate_bricks` | `chiseled_stone_bricks` | `cracked_deepslate_bricks` (this mod) |
| `cracked_deepslate_bricks` | `deepslate_bricks` (this mod) | `deepslate_tiles` (this mod) |
| `deepslate_tiles` | `cracked_deepslate_bricks` (this mod) | `cracked_deepslate_tiles` (this mod) |
| `cracked_deepslate_tiles` | `deepslate_tiles` (this mod) | `chiseled_deepslate` (this mod) |
| `chiseled_deepslate` | `cracked_deepslate_tiles` (this mod) | `melon` |
| `deepslate_emerald_ore` | `emerald_ore` | `emerald_block` |
| `cobbled_deepslate_stairs` | `black_concrete_powder` | `polished_deepslate_stairs` (this mod) |
| `polished_deepslate_stairs` | `cobbled_deepslate_stairs` (this mod) | `deepslate_brick_stairs` (this mod) |
| `deepslate_brick_stairs` | `polished_deepslate_stairs` (this mod) | `deepslate_tile_stairs` (this mod) |
| `deepslate_tile_stairs` | `deepslate_brick_stairs` (this mod) | `cobbled_deepslate_slab` (this mod) |
| `cobbled_deepslate_slab` | `deepslate_tile_stairs` (this mod) | `polished_deepslate_slab` (this mod) |
| `polished_deepslate_slab` | `cobbled_deepslate_slab` (this mod) | `deepslate_brick_slab` (this mod) |
| `deepslate_brick_slab` | `polished_deepslate_slab` (this mod) | `deepslate_tile_slab` (this mod) |
| `deepslate_tile_slab` | `deepslate_brick_slab` (this mod) | (end of the tab) |

## Decoration Blocks

| Item | After | Before |
|---|---|---|
| `infested_deepslate` | `infested_chiseled_stone_bricks` | `iron_bars` |
| `cobbled_deepslate_wall` | `mossy_cobblestone_wall` | `polished_deepslate_wall` (this mod) |
| `polished_deepslate_wall` | `cobbled_deepslate_wall` (this mod) | `deepslate_brick_wall` (this mod) |
| `deepslate_brick_wall` | `polished_deepslate_wall` (this mod) | `deepslate_tile_wall` (this mod) |
| `deepslate_tile_wall` | `deepslate_brick_wall` (this mod) | `anvil` |
| `small_amethyst_bud` | `end_crystal` | `medium_amethyst_bud` (this mod) |
| `medium_amethyst_bud` | `small_amethyst_bud` (this mod) | `large_amethyst_bud` (this mod) |
| `large_amethyst_bud` | `medium_amethyst_bud` (this mod) | `amethyst_cluster` (this mod) |
| `amethyst_cluster` | `large_amethyst_bud` (this mod) | (end of the tab) |

## Redstone

| Item | After | Before |
|---|---|---|
| `lightning_rod` | `lever` | `stone_pressure_plate` |

## Miscellaneous

| Item | After | Before |
|---|---|---|
| `raw_copper` | `iron_ingot` | `copper_ingot` (this mod) |
| `copper_ingot` | `raw_copper` (this mod) | `raw_gold` (this mod) |
| `raw_gold` | `copper_ingot` (this mod) | `gold_ingot` |
| `amethyst_shard` | `quartz` | `raw_iron` (this mod) |
| `raw_iron` | `amethyst_shard` (this mod) | `prismarine_shard` |

## Tools

| Item | After | Before |
|---|---|---|
| `spyglass` | `clock` | `shears` |
