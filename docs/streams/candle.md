# Stream Ca: candles and candle cakes

Package `candle` (client part in `candle.client`). Config `candles`: `enabled`. No packets (ids 35-39 stay
free), no entities, no changes to `common`.

## Changelog section

*   **Candles**: craft one from string over honeycomb, and dye it any of the sixteen colors by crafting it
    with a dye. Up to four candles of one color share a block: use a candle on candles already placed.
*   Light candles with **flint and steel**, a **fire charge** or a **burning arrow**; each lit candle gives
    light 3, so four give 12. Put them out with an empty hand or a splash water bottle.
*   **Candle cakes**: use a candle on a whole cake to put it on top. It lights like any candle. Taking a slice
    gives the candle back.
*   Honeycomb is any item with the ore name `honeycomb`, and the items listed in the config `copper.waxItems`.

## How it works

*   **Names** are 1.18's: `candle`, `white_candle` ... `black_candle`, `candle_cake`, `white_candle_cake` ...
    `black_candle_cake` (34 blocks; candle cakes have no item). `CandleContent.candles[0]` is the plain one,
    `[1 + dye metadata]` the dyed ones; 1.12's "silver" is `light_gray`.
*   **States**: a candle has `candles` 1-4 and `lit` (metadata `candles - 1`, plus 4 when lit); a candle cake
    has `lit`. Light is `3 x candles` when lit, 3 for a cake.
*   **Numbers** (1.18): hardness and blast resistance 0.1, 1.18's four shapes, map color sand or the dye's;
    candle cake hardness 0.5, the cake's shape with the candle's on top, comparator signal 14, pick block gives
    a cake. Pistons break both.
*   **Adding candles** is `ItemCandle`: used on the same candle block with fewer than four, or on a block
    beside one (the space the click would place into), it adds one and keeps `lit`. Sneaking places normally.
    A fifth candle, or another color, falls through to ordinary placing, which fails on top of candles.
*   **Support** (current Java's "holds up the middle"): a solid top, or a top face shape of a fence, wall,
    pane or post. Slabs' lower halves, hoppers and other candles do not. Losing support drops every candle.
*   **Lighting** is a right-click handler (`CandleEvents`), cancelled on both sides so vanilla starts no fire:
    flint and steel loses one durability, a fire charge is used up; it also works while sneaking, as in Java.
    A projectile that is on fire lights the candle it hits (`ProjectileImpactEvent`).
*   **Putting out**: an empty hand on a lit candle (on a candle cake: on the candle, above half height),
    smoke over every wick and 1.17's sound. A splash water bottle (water, no effects) puts out candles in the
    block it hit, the space in front of that and the four spaces around it, the places Java douses fire.
*   **Candle cakes**: a candle used on a cake with no slice taken (not sneaking) swaps the block; the click
    is cancelled so 1.12 does not eat the cake first. Clicking the cake part eats as a cake does (2 hunger,
    0.1 saturation, the cake statistic), leaves vanilla's cake with one slice gone and drops the candle.
    Breaking it, or losing the block under it, drops the candle only. The cake under the candle uses
    1.12.2's own cake textures, so it looks the same before and after.
*   **Flames**: each lit wick shows a small flame every display tick, smoke 30% of the time and the crackle
    17% of the time (1.17's numbers). 1.12 has no small flame, so `candle.client.CandleParticles` has its own:
    1.12's flame at half size.
*   **Recipes**: string over `honeycomb` (ore name) gives one candle; a plain candle and any `dye<Color>` gives
    the dyed candle (dyed candles cannot be dyed again, as in Java). The honeycomb lookup is the copper
    stream's, without its classes: every installed item in `copper.waxItems` (default the Buzzy Bees
    backport's honeycomb) gets the ore name `honeycomb`, and any other mod's item that has it counts.
    Without a honeycomb item installed the candle recipe cannot be made.

## Tested headlessly

`./gradlew runServer -Pselftest=candle` (port 25847): **`SELFTEST DONE pass=589 fail=0`**; then
`python tools/check_assets.py`: **122 blocks, 1418 states, 99 item models, 442 sound files, 0 errors**.
`-Pselftest=all`: pass=2684 fail=1, the one failure being copper's "about 6 veins per chunk: 88" in this
worktree's fresh world (not this stream's code; candles generate nothing).

*   Registration: 34 blocks under 1.18's names, the pre-init name list, items for candles and none for cakes,
    metadata round trips, light 0/3/6/9/12 by state, push reactions, hardness, stack size.
*   Stacking as a survival player: one to four candles, a fifth neither fits nor is used, another color does
    not join, sneaking adds nothing, a lit block stays lit, clicking the block beside it adds one; mining three
    drops three.
*   Lighting: a stick does nothing; flint and steel lights for one durability and starts no fire, and world
    light at the block is 12 for four candles; a lit candle leaves the click to vanilla; a fire charge lights
    and is used up (light 6 for two). An empty hand puts it out and the light goes; a full hand does not.
*   Projectiles: a plain arrow does nothing, a burning one lights; a splash water bottle puts out a candle and
    a candle cake beside where it lands and not one three blocks away; a poison bottle puts nothing out.
*   Support: stone, fence, wall and glass hold a candle; a bottom slab, a hopper, another candle and air do
    not; no placing against a wall in mid-air; removing the floor drops all three candles.
*   Candle cakes: a bitten cake and a sneaking player get no candle; a whole cake becomes the red candle cake
    and one candle is used; comparator 14; pick block a cake; flint and steel lights it (light 3); a full
    player clicking the cake does nothing; an empty hand on the candle puts it out; a hungry player eats (cake
    with one slice gone, +2 hunger, candle dropped); breaking and losing the floor each drop one candle and
    never a cake.
*   Recipes: string over honeycomb (paper standing in), not the other way up; all sixteen dyes; a dyed candle
    is not dyed again.
*   Switched off (fresh world): boots, no candle blocks registered, scaffold test 18 of 18.
*   `./gradlew build` clean; `func_` names in the jar's candle classes, no self-test classes.

## Not tested

*   Everything client-side: models, the lit textures, the small flame and smoke, the selection outline of a
    candle cake (it outlines the cake only; the candle on top is still clickable).
*   Sounds being heard (the calls run; nothing listens on a server).
*   The clicks through a real client: the tests post the right-click event and call the item and block
    directly, so arm swings, the off hand and creative mode (nothing is used up) are unchecked.
*   A real arrow or thrown bottle in flight (the impact event is posted by the test).
*   Pistons in play (the push reaction is checked).
*   With a real honeycomb item: the dev world has none, so the test gives paper the ore name.
*   Candles under water: they are not waterloggable until Backport Core's adapter (PLAN, *Waterlogging*), and
    flowing water washes them away as it does torches.
