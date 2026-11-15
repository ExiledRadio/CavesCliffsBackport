# Stream Gt: goats

Package `goat` (client part in `goat.client`). Entity `cavesandcliffsbackport:goat`, entity id 15 (16-19 free);
no packets (ids 50-54 free). Config `goats`: `enabled`, `spawnWeight` (10), `spawnBiomes` (the five extreme
hills biomes), `ramming` (on). Also changed: `tools/lang/vanilla_keys.py` knows `entity.<modid>.<name>.name`
keys, so mob names come from Mojang's language files like block and item names.

## Changelog section

*   **Goats** live in the extreme hills, in groups of one to three, on stone, gravel, snow and packed ice.
*   A goat **rams** a player or mob that stands still near it: it backs off, lowers its head, stomps and
    charges, and whoever it hits is thrown a long way. Keep moving, or raise a shield to halve the throw.
    Goats do not ram each other, ghasts, creative players, or anyone in Peaceful.
*   Goats **jump** high and far, up to five blocks, to reach ledges they cannot walk to, and take 10 less
    fall damage than anything else.
*   One goat in fifty is a **screaming goat**: it sounds different and rams far more often.
*   Milk a goat with a bucket; breed goats with wheat. A kid rams for half the damage and half the throw.
*   *Whatever Floats Your Goat!* for sharing a boat with one.

## How it works

*   **Numbers** are 1.18.2's code where current Java has not changed them: health 10, speed 0.2, size 0.9 x
    1.3, ram damage 2 (kid 1; 3 on Hard through the usual scaling), ram cooldown 30 seconds to 5 minutes
    (5 to 15 seconds for a screaming goat), jump cooldown 30 to 60 seconds, 2% screaming, 5% kids in a spawned
    group (never the first of it).
*   **1.12 has no brains**, so the goat's two behaviors are two `EntityAIBase` goals in 1.17's order: panic,
    breed, follow wheat, **ram**, **long jump**, follow parent, wander, look. Both cooldowns count down on the
    goat's own tick, never inside a goal, so a goal that is kept out by another still gets its turn later.
*   **Ram** (`EntityAIGoatRam`, 1.17's `PrepareRamNearestTarget` and `RamTarget`): the nearest visible target
    within 16 blocks; a start 4 to 7 blocks from it in a straight line over walkable ground that the goat can
    path to; one second of lowered head (entity states 58 and 59, as 1.17); a run at 3x speed at the far edge
    of the target's block. The hit is a mob attack whose victim does not turn on the goat (its revenge target
    is put back), then a knockback of `clamp(speed x 1.65, 0.2, 3) x 2.5` (x 1 for a kid, x 0.5 against a
    raised shield, +-0.25 per level of Speed or Slowness). A target that leaves its block restarts the
    preparation; nobody to ram costs the shortest cooldown.
*   **Long jump** (`EntityAIGoatLongJump`, 1.17's `LongJumpToRandomPos` with its projectile math unchanged):
    every standable block within 5 blocks each way that a jump of at most 1.5 blocks per tick at 65 to 80
    degrees reaches with a clear arc; one drawn per tick for 20 ticks, farther ones more often, the first the
    goat cannot walk to is taken; two seconds of looking, then the jump. In the air the goat is 0.7 of its
    size and its movement has no drag (1.17 switches friction off; here `travel` undoes 1.12's), so it lands
    where the math aimed, except that, as in 1.18.2, the arc is checked at full speed and flown at 0.95 of it, so
    some jumps fall short. Back at full size it stays where it came down (1.12's `setSize` would shift it 0.135
    toward -x -z). No long jumps from a honey block when a mod has one.
*   **Saved data** is current Java's: `IsScreamingGoat`, `HasLeftHorn`, `HasRightHorn` and the two cooldowns
    under `Brain.memories` (`minecraft:ram_cooldown_ticks`, `minecraft:long_jump_cooling_down`). Both horns
    are true and nothing here takes one: goat horns belong to a later update, which can read and write the
    tags (or call `setHorns`); the model hides a horn that is gone. No ram event is posted
    (`docs/CORE_REQUESTS.md` 4).
*   **Spawning**: creature spawns in the biomes of `spawnBiomes` (names, so a pack can add its own mountains),
    weight `spawnWeight`, groups of 1 to 3. A goat spawns on stone, gravel, packed ice, snow blocks, snow
    layers and powder snow (by name) in light above 8, which is 1.18's rule. 1.18's weight is 5 in a list of
    9; next to the hills' sheep (12), pigs, chickens (10), cows (8) and llamas (5) the default here is 10.
    1.12 places animals at chunk generation without asking the mob, so those goats also stand on grass.
*   **Milking** follows current Java: creative players keep the bucket and get one milk bucket.
*   **The advancement** has no item of ours to hang `item_exists` on, so it carries our own condition
    `cavesandcliffsbackport:goats` (registered whether goats are on or off) and is granted from the goat's
    tick, once a second, to every player in its boat.
*   The head turns at most 15 degrees from the body, as 1.17's.

## Not built

*   Goats avoiding powder snow when walking (1.17 gives it a pathfinding cost; 1.12's path node types are a
    fixed enum).
*   1.18's peaks and slopes as spawn biomes: add their names to `spawnBiomes` when the terrain stream has them.
*   Goat horns and the ram-at-a-block event: a later update's (see above).

## For the integrator

*   `tools/common/generate.py` is out of date on `main`: run as it is, it drops the `.tooltip` line of every
    option that has a `@Setting` annotation between its `@Config` ones, skips array options (`String[]`,
    `int[]`) and writes the removed `itemGroup` line back. This stream therefore did not run it; its three
    config options' six lang lines were added to the `common` block by hand.

## Tested headlessly

`./gradlew runServer -Pselftest=all` (port 25850, fresh world), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=2238 fail=0`** (142 of them the goat stream); **88 blocks, 1248 states, 82 item models,
452 sound files, 0 errors**.

*   Registration: entity id 15, spawn egg colors, 17 sound events, health, speed, sizes, ram damage 2 and 1.
*   Saved data: the three tags, both cooldowns under `Brain.memories`, a horn taken through the tag stays taken.
*   Chances over 6,000 spawned goats: about 2% scream, about 5% kids and never the first of a group, starting
    cooldowns in range; a kid of a screaming mate always screams, 2% otherwise.
*   Fall damage: none from 13 blocks (which kills a pig), 7 from 20.
*   Milk (one bucket, a stack, creative, a kid gives none), wheat, two goats in love having a kid.
*   Who may be rammed: pig, armor stand, survival player; not goats, ghasts, creative or spectator players,
    invulnerable mobs, mobs behind a wall, anyone in Peaceful.
*   Ramming a pig from five starting places, mobs ticked by hand: head lowered, charge from 4 to 7 blocks,
    2 damage, the pig thrown more than 4 blocks and not angered, cooldown restarted. Ramming off: nothing;
    back on: it rams. A screaming goat with nobody to ram waits 100 ticks.
*   Ramming a survival player: 2 damage on Easy, thrown back, velocity flagged for sending.
*   A long jump off a five-block pillar: 0.7 size in the air, lands at the block and height it aimed for,
    stays where it came down, unhurt, cooldown 30 to 60 seconds; on flat ground no jump and half a cooldown.
    1.18.2's code misses 15.6% of its draws on this pillar (always the same 20 of 106 places), so up to eight
    goats jump and the first that lands where it aimed is checked.
*   Spawning: in the five hills biomes at the configured weight, not in plains; on stone, gravel, packed ice
    and snow in light, not on grass, dirt, sand or ice, not in the dark.
*   *Whatever Floats Your Goat!* granted to a player sharing a boat with a goat, not for a goat alone.

## Not tested

*   Everything client-side: the model, the kid's proportions, the lowered head, hidden horns, sounds heard.
*   Goats switched off (`goats.enabled=false`): no run was made; the advancement's condition is untested off.
*   Natural spawning in generated hills (only the spawn list and the spawn rule are checked).
*   The release jar: `./gradlew build` and the `func_` name check were not run in this session.
*   A shield halving the throw; Speed and Slowness changing it; ramming in play against a moving player.
