# Stream Dr: dripstone and new cauldrons

Package `dripstone` (client part in `dripstone.client`). Config `dripstone`: `enabled`, `extraCopperVeins`
(16); dripstone caves follow `caves.dripstoneRegions`. Entity id 1 (`falling_stalactite`; 2-4 unused). No
packets (ids 20-24 unused).

Outside the package: one line each in the mod class's pre-init and init, `ClientSetup.init` and
`SelfTestRunner.TESTS`; the `extraCopperVeins` field in `ModConfig.Dripstone`; and three shared-tool edits the
integrator should know about:

*   `tools/common/generate.py`: the same one-line regex fix the axolotl session made (it is copied from that
    worktree, so the two merge cleanly). Without it a rerun drops every tooltip that sits behind a
    `@Setting` annotation.
*   `tools/lang/vanilla_keys.py`: `death.attack.<modid>.<x>` now maps to vanilla's `death.attack.<x>`, so
    death messages come from Mojang's language files.
*   `selftest/CopperSelfTest.java`: its "about 6 veins per chunk" count sets `extraCopperVeins` to 0 while its
    chunks generate. Its place is picked from the clock, and a third of all places are over a dripstone
    region.

## For the integrator: `Regions` is the plan's `common/CaveRegions`

**Done on merge:** lifted as `common/CaveRegions`, keyed by a hash of the seed, with `lush(...)`, a
`mayContain` and the client side. The note below is kept as written.

`dripstone/Regions.java` is the region lookup streams L and Ax need too. It already answers for both kinds:
one 3D value noise of the world seed, **dripstone above +0.40, lush below -0.40**, so the two never overlap.
Each is 9% of the underground. Lift it into `common` unchanged, add a `lush(World, x, y, z)`, and point
`DripstoneCaveGenerator` and `DripstoneSelfTest` at it. The formula and API are also in the sessions' shared
notes (`cc-dripstone-cave-regions.md`). Not done from the plan's wish list: config for region frequency and
size, a surface height recorded at generation ("underground" is 4 blocks under the height map), and sending
the seed to clients for the music stream.

## Changelog section

*   **Dripstone caves**: in some cave regions of new chunks the floors and ceilings are **dripstone block**,
    bristling with stalagmites and stalactites, crossed by thick dripstone columns and dotted with small pools.
    They hold extra copper ore.
*   **Pointed dripstone** hangs from ceilings as a stalactite or stands on floors as a stalagmite; stack it to
    make longer ones. A stalactite and a stalagmite that meet join into a column (sneak to keep them apart).
    Any tool breaks it, and four make a **dripstone block**.
*   A **stalactite** falls when the block it hangs from goes, and hurts whatever is under it: more the longer
    it is and the further it falls. A helmet helps.
*   Landing on a **stalagmite** hurts far more than the fall alone.
*   A thrown trident breaks pointed dripstone.
*   Stalactites **drip**. Under a block with water above it, a stalactite slowly fills a cauldron below with
    water; under lava, with lava. Hanging from a dripstone block with water above, it slowly grows, and so
    does a stalagmite beneath it.
*   **Lava cauldron**: a bucket of lava fills a cauldron. It glows, burns whatever stands in it, and an empty
    bucket takes the lava back.
*   Wandering traders sell pointed dripstone, when a mod adds them.

## How it works

*   **States**: `pointed_dripstone` has `thickness` (tip_merge, tip, frustum, middle, base) and
    `vertical_direction` (up, down), metadata `thickness | (down ? 8 : 0)`. Blockstate and models are 1.18's.
    Each segment recomputes its thickness when a block above or below it changes (current Java's rule, in
    `calculateThickness`). Shapes and the up-to-an-eighth sideways offset per column are current Java's
    (`getOffset` is overridden; 1.12's own offset is a quarter).
*   **Since 1.1.8** `BlockPointedDripstone` is Backport Core 1.5.0's `speleothem.BlockSpeleothem` (states, support,
    thickness, merging, growth, falling as `backportcore:falling_speleothem`, impaling, tridents, the two damage
    types), shared with Game Drops' icicles. Kept here: drips into cauldrons and their particles, `canGrow` (a
    dripstone block with a water source above), the stalagmite search stopping where a drip cannot pass. Same
    registry name, states and metadata, so 1.1.7 worlds load as they were; 1.1.7's `falling_stalactite` entity is
    still registered only to turn into Core's on its first tick (`LegacyLoadSelfTest`). The points below describe
    1.1.7 and still hold, except the stalagmite's fall: Core's 26.4 offset of 2.5 blocks (1.18.2: 2).
*   **Support**: a segment needs a solid face or a segment of its own direction behind it. A stalagmite that
    loses it breaks a tick later, segment by segment; a stalactite falls two ticks later, all of it at once.
*   **`EntityFallingStalactite`** is our own entity: 1.12's falling block has the anvil's damage built in and
    needs its block still in place when it first ticks. Each segment falls on its own. Only the tip hurts:
    per block fallen after the first, the stalactite's length (at least 6), 40 at most. Landing, a segment
    stays a block only where a stalactite could hang; otherwise it crashes (sound) and drops its item.
*   **Damage sources**: `cavesandcliffsbackport.stalagmite` and `.fallingStalactite`, with vanilla's death
    messages. 1.12 ties Feather Falling to `DamageSource.FALL` and helmet wear to the anvil's source by
    identity, so `DripstoneEvents` adds both back in `LivingHurtEvent`. Landing on a stalagmite tip runs the
    entity's ordinary fall with 2 more blocks and double damage (so Jump Boost, flying players and
    `LivingFallEvent` all apply), and the fall damage that results is re-issued as the stalagmite's.
*   **Dripping** (random ticks of a stalactite's topmost segment): the block it hangs from, up to 10 segments
    up, must have a water or lava source right above it. Chance 45/256 (water) or 15/256 (lava); the tip finds
    a cauldron up to 10 below with nothing in the drip's way (blocks that leave the middle of the column
    open, like torches, do not count). A drop shows at the tip and the cauldron changes when it lands, 50
    ticks plus one per block later. 1.12's cauldron cannot be given that tick, so the landings wait in a
    per-world list in `DripstoneEvents` (not saved: a drip in flight at shutdown is lost).
*   **Growth**: chance 64/5625 a random tick, only hanging from a dripstone block with a water source above;
    half the time the stalactite grows (to 8 at most), half the time the stalagmite under its tip does (its
    floor within 10 blocks), and tips that meet merge.
*   **Particles**: tips drip client-side, more with a liquid above. The drop is 1.12's own drip particle
    plus current Java's landing sound.
*   **Lava cauldron** is its own block (`lava_cauldron`, no item; drops and picks as a cauldron): 1.12's
    cauldron only has water levels. A lava bucket on vanilla's cauldron, whatever is in it, makes one
    (`RightClickBlock`); an empty bucket turns it back into vanilla's empty cauldron; a water bucket
    replaces the lava with a full water cauldron. Light 15, comparator 3. Its model is 1.18's full cauldron
    with 1.12's own cauldron and lava textures, so it matches the cauldron beside it under any resource pack.
*   **Dripstone caves** (`DripstoneCaveGenerator`, world generator weight 2): 1.18's three features with its
    settings, tried at random heights between y 6 and 70, each starting in air, underground, inside a
    dripstone region. 1.18 spreads its tries over 384 levels; ours are scaled to these 64 (10-19 clusters,
    2-9 large columns and 38-51 single pointed dripstones a chunk, most landing in rock). Everything reads and
    writes inside the 2x2 chunks being populated; large columns are capped at radius 7 for that (1.18: 19).
    Extra copper: `extraCopperVeins` tries a chunk at a vein of 20 in stone, heights a triangle over y 0-96,
    kept when the vein's start is in a dripstone region. The deepslate pass turns those in the deepslate
    layer into deepslate copper ore.
*   **Trade**: `TradeRegistry.addTraderTrade` (Backport Core), ordinary pool: 2 pointed dripstone for an
    emerald, 5 uses.

## Tested headlessly

`./gradlew runServer -Pselftest=all` on a fresh world (port 25844), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=2228 fail=0`** (147 of them dripstone's); **91 blocks, 1260 states, 84 item models,
463 sound files, 0 errors**. `./gradlew build` clean; the jar has `func_` names and no test classes.

*   Registration, the ten states' metadata round trip, hardness, push reaction, the tip's shape, the offset
    staying within an eighth, lava cauldron light 15 and comparator 3.
*   Thickness: a stalagmite of four reads base, middle, frustum, tip; a stalactite placed onto its tip merges
    both; removing it un-merges; placed sneaking, neither merges; direction follows where the placer looks
    when both ways hold, and the only way that holds otherwise; no placing in mid-air.
*   Support: a stalagmite whose floor goes breaks and drops its 3 segments; a stalactite whose ceiling goes
    becomes 3 falling entities (only the tip hurting, 6 a block), which land as 3 items and leave no block; a
    stalactite of nine does 9 a block.
*   Damage: a tip falling 8 blocks onto an iron golem does 36-40 (6 a block, 40 at most) with its own damage source; a helmet takes a
    quarter and wears; a zombie falling 5 blocks onto a stalagmite tip takes 8 (2 onto a stalagmite's lower
    segment) as the stalagmite's damage; Feather Falling IV takes 48% off.
*   Growth over 400 forced growth ticks: the stalactite reaches 8 (or merges), a stalagmite grows under it,
    both keep proper bases; nothing grows without the water, or from stone.
*   Drips: the chances' cut-offs, only the topmost segment sending, the delay before landing, a cauldron
    filling to 3 and no further, a block in the way stopping it and a wall torch not, lava only into an
    empty cauldron (making a lava cauldron), nothing from a dry stalactite, 10 blocks reaching and 11 not.
*   Lava cauldron through the server's own right-click path: lava bucket on a part-filled cauldron, no lava
    poured beside it, the bucket handed back; burning inside and not on the rim; empty bucket, water bucket;
    mined, it drops a cauldron. Pointed dripstone drops by hand; the dripstone block needs a pickaxe.
*   Recipe, and the trader's offer in Core's list.
*   Regions: 9% dripstone and 9% lush of 40,000 samples, repeatable, coherent.
*   Features placed by hand in stone hollows: clusters (dripstone block, pointed dripstone, pools that do not
    leak, nothing outside the window), large dripstone (builds in a tall cave, not in rock), single pointed
    dripstones; every pointed dripstone placed has support and the thickness its neighbors call for.
*   Generated chunks: 81 chunks in a dripstone region hold 9,174 dripstone blocks, 2,296 pointed dripstone
    (all sound, none outside the region) and 23,432 copper ore (about 3,400 without the extra); 9 chunks far
    from any region hold none.
*   Switched off (fresh world): boots, no errors.

## Not tested

*   Everything client-side: models, the cutout layer, the column offset as drawn, the falling stalactite's
    renderer, drip particles and their landing sound, the lava cauldron's look.
*   Sounds being heard (the calls run; nothing listens on a server).
*   Tridents breaking pointed dripstone: the dev environment has no trident. The handler looks for a
    projectile whose entity name contains `trident`, faster than 0.6.
*   Random ticks finding stalactites in play (growth and drips are called directly with chosen rolls), and
    the 50-tick wait through real world ticks (the test lands drips at once).
*   A player placing by hand (the test calls the placement rule), a player's death message, creative-mode
    buckets, pistons breaking pointed dripstone in play (the push reaction is checked).
*   A falling stalactite across a save and reload, or seen by a client joining mid-fall.
*   How dripstone caves look in play, and their cost: generation was not timed.

## Not built

*   **Waterlogging** (the plan's later pass): pointed dripstone is placed and generated in air only, never
    grows into water, and stalagmites do not stand in pools as they do in 1.18.
*   **Mud above a stalactite drying to clay**: mud is a later update's block.
*   Lava cauldrons do not take another filling's bucket (powder snow is stream Ps's), and dispensers do not
    fill or empty them (they do not in current Java either).
