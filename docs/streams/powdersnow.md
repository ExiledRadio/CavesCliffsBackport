# Stream Ps: powder snow and freezing

Package `powdersnow` (client part in `powdersnow.client`). Config `powderSnow`: `enabled`, `freezing`,
`strayConversion`, `cauldronSnow`, `patches`, `patchRarity` (2), `patchMinY` (80), and `walkableMobs`. Since 1.1.8 the
freezing itself is Backport Core 1.5.0's (`freeze.FreezeTicks`), and its three id lists are in Core's Freezing settings. Packet id 30
(`PacketFreeze`). Also changed outside the package: `tools/common/generate.py` read no tooltip for an option
that carries a `@Setting` annotation (its pattern now allows them) and names `patchMinY`;
`tools/lang/vanilla_keys.py` maps the two `death.attack.freeze` keys to Mojang's.

## Changelog section

*   **Powder snow**: a block you fall into. Everything inside it sinks slowly and can barely move; falling into
    it never hurts. **Leather boots** walk on top of it (sneak to sink in, push against it or jump to climb
    out). Rabbits, endermites and silverfish walk on it too.
*   **Freezing**: staying in powder snow frosts over your screen, slows you, and after 7 seconds hurts every 2
    seconds. Blazes and magma cubes take five times as much; strays, polar bears, snow golems and the wither
    never freeze. Any piece of **leather armor** prevents it. The `freezeDamage` game rule turns the damage
    off for players.
*   **Powder snow bucket**: use an empty bucket on powder snow to pick it up, and place it again like a block.
    Dispensers do both.
*   **Powder snow cauldron**: snowfall slowly fills cauldrons under the open sky, in three levels. A bucket
    takes a full one; a powder snow bucket fills any cauldron.
*   **Skeletons** that stay in powder snow for 7 seconds start shaking and turn into **strays** 15 seconds later.
*   Patches of powder snow generate in snowy high ground.
*   Advancement *Light as a Rabbit*.

## How it works

*   **Collision** is per entity, as in Java: 1.12 passes the moving entity to `addCollisionBoxToList`. Fallen
    more than 2.5 blocks: a box 0.9 high (then `onFallenUpon` plays the landing sound and deals nothing).
    Falling blocks, and walkers above the block that are not sneaking: the full cube. Everyone else: nothing.
*   **Sinking**: Java multiplies movement inside the block by (0.9, 1.5, 0.9) and clears velocity. 1.12 has that
    only for cobwebs, so after each move the velocity is reset to the value that makes the next fall Java's
    (0.1176 a tick for living entities, 0.06 for items). Sideways the velocity is cleared but the 0.9 is not
    applied, and a jump from the bottom rises 0.42 instead of 0.63: the two known differences.
*   **Freezing** (1.1.8): Backport Core's freeze ticks. Each tick powder snow touches a living entity it calls
    `FreezeTicks.markInFreezingBlock` (while `freezing` is on); Core keeps the counter, damage, slowdown, frost
    overlay and frozen shaking. Core follows the newest backport installed: 1.17's immune list here, 26.4's (all undead) with a 26.4 backport. Powder snow's own
    state stays in `CCBFreeze` (`Seen`, and for skeletons `SnowTime`, `Convert`); `PacketFreeze` carries only a
    converting skeleton's shaking. Spawning inside powder snow keeps 1.17's immune types (`Freezing.spawnsInSnow`).
*   **Client** (`PowderSnowClient`): linear white fog with the camera in the block, Java's body shake for
    converting skeletons (frost overlay and frozen shaking: Core). A fully frozen player's hurt
    sound is swapped for the freezing one through `PlaySoundAtEntityEvent`.
*   **Bucket** is a plain item, not an `ItemBlock`, so powder snow has no item (nothing from Silk Touch).
    Picking up goes through `FillBucketEvent`.
*   **Cauldron** is our own block (levels 1-3) that turns back into the vanilla cauldron when emptied and
    drops it. 1.12 never calls a block's rain hook where it snows, so a world tick handler makes vanilla's
    one-column-in-sixteen-chunks pass while the weather is on, with Java's 1 in 10 for snow. A burning entity
    melts one level into water. A lava bucket needs stream Dr's `lava_cauldron`, looked up by name.
*   **Mobs** do not path into powder snow (`DAMAGE_OTHER`; walkers see `BLOCKED`) and do not spawn inside it.
*   **Patches** (our placement until 1.18's groves and snowy slopes exist): a chunk in `patchRarity` looks at
    6 columns and puts a rough disc, radius 2-4 and up to 3 deep, at the first whose ground is under a snow
    layer, cold enough to snow at its height and at or above `patchMinY`. Flush with the ground, layer removed.

## Tested headlessly

`./gradlew runServer -Pselftest=all` (port 25846, fresh world), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=2274 fail=0`** (178 of them powder snow's); **90 blocks, 1252 states, 83 item models,
433 sound files, 0 errors**.

*   Registration; no item for either block; material; the `freezeDamage` rule.
*   Collision boxes for: no entity, a zombie, fallen 3 blocks (0.9), leather boots above / sneaking / inside,
    iron boots, a leather helmet, a rabbit, a falling block, an item. Path node types.
*   An armor stand sinks 0.1176 a tick to the floor, with no fall distance; with leather boots it stays on
    top; an item sinks 0.06 a tick. A pig dropped 20 blocks: hurt on stone, unhurt on powder snow.
*   A pig in a snow-filled hole: one freeze tick a tick, capped at 140, slowed, 1 damage per 40 ticks; out of
    the snow it thaws 2 a tick and no data or slowdown is left. A blaze takes 5. Who cannot freeze; leather in
    each slot; the config lists.
*   A real server player: freezes, is hurt, not with `freezeDamage` off, thaws under a leather helmet, never as
    a spectator; stands on powder snow in leather boots and gets *Light as a Rabbit*.
*   A skeleton: shaking after 140 ticks, a stray 300 later even out of the snow, keeping name, bow and helmet,
    at full health; nothing with the option off.
*   Bucket: placing, picking up (single and from a stack), creative, pick block, no drops with Silk Touch.
*   Cauldron: snow levels, comparator, every bucket exchange, a burning entity, its drop; where it snows
    (snowy biome yes, desert never, extreme hills only high up).
*   Dispenser: places, takes back, does nothing when blocked, still takes water.
*   Fire melts the block; nothing spawns inside it but strays.
*   A patch on built ground, and real terrain (ice mountains at 7808, 4480): 29 blocks generated over 273
    snowy columns at or above y 80, none lower.

## Not tested

*   **The stream switched off** (fresh world with `enabled=false`): not run. **`./gradlew build`** and the
    `func_` check: see the hand-over note; they were the last steps when the session ran out.
*   Everything client-side: the overlay, fog, shaking, the block's look, particles, sounds being heard, the
    sync packet arriving (only its encoding is checked).
*   A player's own movement in powder snow (it is client-driven): sinking feel, climbing out in boots,
    sneaking through, and whether the server ever pulls a player back at the 2.5-block fall boundary.
*   Snowfall filling a cauldron through the weather pass itself (its two halves are tested apart).
*   Walker mobs hopping out; frozen hearts on the health bar are not built.
