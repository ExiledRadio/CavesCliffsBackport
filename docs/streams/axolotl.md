# Stream Ax: axolotls

Package `axolotl` (client part in `axolotl.client`). Entity id 10 (`cavesandcliffsbackport:axolotl`), item
`axolotl_bucket`. No packets: everything the client needs (color, playing dead, from a bucket) is synced
entity data. Config `axolotls`: `enabled`, `spawning`, `spawnCap` (5), `breedingItem` (`minecraft:fish:2`),
`huntTargets`, `hostileTargets`, `hostilesAttackAxolotls`.

Also changed: `tools/common/generate.py` (one regex) now reads options that carry Core's `@Setting`
annotations or are lists; on `main` it dropped their language lines (see *For the integrator*).

## Changelog section

*   **Axolotls** live in underground water with a clay floor. They come in four colors, lucy, wild, gold
    and cyan, and a rare **blue** that only breeding gives, one time in 1200.
*   They swim freely and crawl slowly on land, where they dry out after five minutes unless it rains or a
    splash water bottle wets them.
*   They hunt squid, glow squid and fish, and always attack guardians, elder guardians and drowned. Kill a
    mob an axolotl is fighting and it gives you **Regeneration** and takes away **Mining Fatigue**.
*   Hurt in water, an axolotl may **play dead** for ten seconds: it heals, and mobs lose interest in it.
*   Breed them with a bucket of tropical fish; you get the bucket back with water in it. The baby takes
    after one parent.
*   Pick one up with a water bucket: the **bucket of axolotl** keeps its color, age, health and name, and
    an axolotl from a bucket never despawns.
*   Advancements *The Cutest Predator* and *The Healing Power of Friendship!*.

## How it works

*   **From a brain to goals.** 1.17 drives the axolotl with a brain; here it is 1.12.2 goals
    (`AxolotlGoals`) in the brain's order: play dead, mate, follow food, baby follows adult, fight, find
    water, wander, look at players. Three things the brain keeps as memories are counters on the entity,
    counted in `updateAITasks` so they run whatever goal holds the mutex: `playDeadTicks`, `huntingCooldown`
    (2400 ticks after every fight) and the target scan once a second (8 blocks, in water, in sight).
*   **Moving.** `EntityAxolotl.MoveHelper` is 1.17's smooth swimming control (turn 10 degrees a tick, pitch
    up to 85, a tenth of the asked speed in water, half on land); `travel` in water is push, move, 0.9 drag
    with no sinking. Speeds are 1.17's: 0.5 idling, 0.6 chasing, 0.2 mating in water, 0.15 on land, which
    works out to about a quarter of a block a second on land. Paths are `AxolotlNavigator` over
    `AxolotlNodeProcessor`, the family's walk-and-swim path finder (water cost 0, land 6, water against a
    wall 4), since 1.12.2 has only walkers that float and swimmers that never leave the water.
*   **Drying out.** Air is 6000; out of water and rain it drops by one a tick, then 2 damage a second from
    our own `dryout` damage source (vanilla's key `death.attack.dryout`). A splash water bottle
    (`ProjectileImpactEvent`) gives 1800 back to every axolotl within 4 blocks.
*   **Playing dead** (1.17's numbers): one hit in three, if a second roll of 0-2 is under the damage or the
    axolotl is under half health, the hit does not kill it, it is in water and somebody dealt the damage.
    200 ticks, Regeneration I for 200. A mob that targets a playing-dead axolotl has its target cleared
    (`LivingSetAttackTargetEvent`).
*   **Targets** are two config lists of registry names, 1.17's two entity tags: `huntTargets` (squid, our
    glow squid, and the cod, salmon, pufferfish and tropical fish of the 1.13 backport) and `hostileTargets`
    (guardian, elder guardian, that mod's drowned). Names of mobs that are not installed never match. The
    mobs in `hostileTargets` get one more target goal as they join the world, so they attack axolotls back
    as in current Java (a guardian only beyond 3 blocks, as with squid); `hostilesAttackAxolotls` turns that
    off.
*   **Helping.** When a fight ends because the target died, and the target's last damage came from a player
    within 20 blocks, that player gets Regeneration I, 100 ticks more each time up to 2400, loses Mining
    Fatigue, and gets the advancement. An axolotl's own kill helps nobody, as in vanilla.
*   **Breeding.** The bucket of tropical fish is a 1.13 item; it is looked up by name
    (`updateaquaticbackport:tropical_fish_bucket`) and, when it exists, is the only food, handed back as a
    water bucket. In a game without it, `breedingItem` stands in (the tropical fish, `minecraft:fish:2`).
    Bred babies are persistent.
*   **Bucket.** `Variant`, `Age`, `Health`, `HuntingCooldown`, the name and the NoAI, Silent, NoGravity,
    Glowing and Invulnerable flags, as current Java. Emptying follows the family's fish bucket: water as a
    water bucket places it (waterloggable blocks are filled through Core's `Waterlogging`), steam in the
    Nether, dispensers too. In creative the water bucket stays and the filled one is added.
*   **Spawning** (`AxolotlSpawning`) is our own small spawner, because 1.12.2 spawns by biome and a biome is
    a whole column. Every tick, while fewer than `spawnCap` (5, the 1.17 category cap, scaled by loaded
    chunks as vanilla scales caps) wild axolotls are loaded, it tries one random block in each chunk within
    7 chunks of a player; a water block starts a group: three walks of 4-6 steps, up to four axolotls, 24
    blocks from players and the world spawn, each in water with clay right below. A group shares two colors
    and its third and fourth are babies. Wild axolotls despawn as monsters do; bucketed, bred and named
    ones never do, and are not counted toward the cap.
*   **Model.** `ModelAxolotl` is 1.17's boxes and its five poses. Each pose eases every part from where it
    was last drawn, so the last rotations are kept on the entity as plain floats (no client type on the
    server).

## For the integrator

**Done on merge:** `AxolotlHabitat.LushCaves` (a lush region of `common/CaveRegions`, underground) is the default
habitat; the stand-in stays for the pool self-tests. The generator regex is merged and both generators were rerun.

*   **Lush regions.** Where axolotls may spawn is one interface, `axolotl/AxolotlHabitat`:

        boolean canSpawnAt(World world, BlockPos pos);   // pos: a water block with clay below, chunk loaded

    `AxolotlSpawning.habitat` holds the stand-in `AxolotlHabitat.DarkUndergroundWater` (below the top solid
    block of its column, light 0). Vanilla terrain has almost no such water over clay, so until lush caves
    generate, axolotls come from spawn eggs, buckets and player-built pools. To wire it up, set the field
    once (from `common`, in init) to an implementation that asks `CaveRegions` whether `pos` is in a lush
    region; nothing else changes. The config tooltip of `axolotls.spawning` says "underground water above
    clay" and can then say "lush caves".
*   **`tools/common/generate.py`.** One regex line: the annotation run now accepts `@Setting.*` and the
    field type a trailing `[]`. On `main` a rerun dropped the tooltips of every option annotated
    `@Setting.NewChunks` and all lines of list options. After merging the streams, rerun it and
    `tools/lang/vanilla_keys.py` once; the common block of `en_us.lang` is generated. It still writes the
    unused `itemGroup.cavesandcliffsbackport` line.
*   **Glow squid.** `huntTargets` names `cavesandcliffsbackport:glow_squid`; if stream Gs registers another
    name, change the default.
*   **Shared files touched:** `ModConfig.Axolotls` (fields), `ClientSetup.init` (one `if`),
    `SelfTestRunner.TESTS` (one line), `en_us.lang` and `sounds.json` through the generators. No line in the
    mod class and none in `NetworkHandler` (packet ids 45-49 are unused).

## Tested headlessly

`./gradlew runServer -Pselftest=all` (port 25849), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=2241 fail=0`** (145 of them the axolotl's); **88 blocks, 1248 states, 83 item models,
405 sound files, 0 errors**. Mobs are ticked by hand inside the test's one server tick.

*   Registration: entity, spawn egg, bucket (stack of 1, dispenser behavior), nine sounds; 14 health, 2
    attack, 0.75 x 0.42, 6000 air; saving and reading back, a bad color id reading as lucy.
*   Colors: 400 spawns give the four common ones and never blue; 50 groups of four are two adults then two
    babies in two colors at most; of 24,000 bred babies about 20 are blue, the rest a parent's color, all
    persistent.
*   Drying: one air a tick on land, 2 damage from `dryout` at the end, death of it; a splash water bottle
    gives 1800 (another splash potion nothing); full air again in water.
*   Swimming: holds its depth when idle, swims 9 blocks along and 2 up to a goal without leaving the water,
    keeps moving about its tank for 30 seconds.
*   On land it crawls to water 4 blocks away (several hundred ticks) and arrives unharmed.
*   Playing dead: about 2 hits in 9 of 300 start it in water; never for damage nobody dealt, a hit that would
    kill, or on land; it lasts 200 ticks, gives Regeneration, heals, clears the path and holds still; a
    zombie cannot target it meanwhile and can again after.
*   Hunting: picks a squid over a nearer pig, kills it through the fight goal, rests 2400 ticks and ignores a
    second squid, still targets a guardian while resting, hunts again after, and not while in love.
*   Helping: a squid it fights, killed by a player, gives that player Regeneration I for 100 ticks, removes
    Mining Fatigue and grants *The Healing Power of Friendship!*; a kill by nobody adds nothing; a second
    player kill makes it 200; it stops at 2400.
*   Breeding: tropical fish (the config item) puts two axolotls in love and is used up, raw cod does
    nothing; they have a half-size persistent baby in a parent's color and wait five minutes; the baby swims
    after an adult.
*   Bucket: an empty bucket does nothing; a water bucket gives the bucket of axolotl with `Variant`, `Age`,
    `Health`, `HuntingCooldown` and the name, removes the axolotl and grants *The Cutest Predator*; emptied,
    it places water and the same axolotl comes out, marked as from a bucket; not into stone; a plain bucket
    gives an adult lucy; in creative the water bucket stays.
*   Spawning: the group spawner fills a closed clay pool (never more than four at once, each in water over
    clay, none blue), spawns nothing over stone and nothing in a clay pool under the sky, and does spawn
    there once another habitat is plugged in.
*   A guardian joining the world gets the extra target goal (a squid does not), targets an axolotl 6 blocks
    away and leaves it alone while it plays dead.
*   Switched off (`axolotls.enabled=false`, fresh world): boots, nothing registered, no advancement errors.
*   `./gradlew build`: the jar's axolotl classes carry `func_` names and no test classes.

## Not tested

*   Everything client-side: the model and its poses, the five textures, the baby's size, the spawn egg and
    bucket icons, sounds being heard.
*   Natural spawning through the world tick with a real player (the group spawner and the habitat are
    tested directly; the per-chunk sampling and the cap are not, as no player is online headlessly).
*   Despawning of wild axolotls in play.
*   Breeding with a real bucket of tropical fish and getting the water bucket back (the 1.13 backport is not
    in this dev environment; the config item path is tested), and hunting its fish and drowned (squid and
    guardians are tested; the others are the same lookup by name).
*   Drowned and elder guardians attacking axolotls (guardians are tested).
*   Following a player who holds food; leads; dispensers emptying the bucket in a world (the behavior is
    registered and its `empty` call is tested); the bucket in the Nether.
*   Rain keeping an axolotl wet.
