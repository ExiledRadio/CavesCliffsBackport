# Stream Am: amethyst, tinted glass, spyglass, geodes

Package `amethyst` (client part in `amethyst.client`). Config `amethyst`: `enabled`, `geodes`, `geodeRarity`
(24), `geodeMinY` (6), `geodeMaxY` (46). Also changed: `tools/assets.py`'s advancements now carry an
`item_exists` condition, so a switched-off stream leaves no advancement and no loading error (copper's too).

## Changelog section

*   **Amethyst geodes** generate deep underground: a shell of smooth basalt and calcite around a hollow lined
    with **amethyst**, most of them cracked open on one side.
*   **Budding amethyst** grows **small**, **medium** and **large amethyst buds** and finally **amethyst
    clusters** on its open faces. It can't be picked up, even with Silk Touch, and breaks if a piston pushes
    it. Buds and clusters glow faintly.
*   Mine a cluster with a pickaxe for 4 **amethyst shards** (more with Fortune), or 2 by any other means; buds
    give nothing without Silk Touch. Four shards make a **block of amethyst**. Amethyst chimes when walked on
    or hit by an arrow.
*   **Tinted glass** (four shards around glass make two): glass that blocks light.
*   **Spyglass** (a shard on two copper ingots): hold use to zoom in. *Is It a Bird?*, *Is It a Balloon?* and
    *Is It a Plane?* for spotting a parrot, a ghast and the ender dragon through it.

## How it works

*   **Growth** (current Java): each random tick, one in five, budding amethyst picks a face; air or still
    water there gets a small bud, a bud there that points that way grows one stage. Buds need a solid face
    behind them and pop off without it.
*   **Drops**: 1.12 does not tell a block's drops which tool broke it, so the cluster records the tool during
    `harvestBlock` (its own call) and drops 4 shards for a pickaxe (Fortune's ore formula), 2 otherwise.
*   **Light and shapes** as 1.18: light 1, 2, 4, 5; shapes from 1.18's heights and insets (3/4, 4/3, 5/3, 7/3).
*   **Geodes** in 1.12 worlds are 1.18.2's `GeodeFeature` with AMETHYST_GEODE's configuration, the code of the
    1.18 terrain's `terrain/core/Geodes` on java.util.Random (since 2026-11-13; before, an approximation with
    white noise and two crack positions of its own made geodes 40% smaller in the median). 3-4 points 4-6
    blocks from a corner, `fastInvSqrt` sums plus the seed's geode NormalNoise times 0.05 per point, the layer
    sizes (filling 1.7, inner 2.2, middle 3.2, outer 4.2) and chances (budding 8.3%, crack 95%, a bud or
    cluster on the first open face of 35% of budding blocks). They abort when more than one point is in air,
    bedrock, water, lava or ice, never replace tile entities or bedrock, and stay inside the population window.
    Calcite and smooth basalt are stream D's, by name. **Buds as generated are vanilla's:** a median of 6 per
    geode (10th-90th percentile 2-11) in a 1.18 world of this mod (exact) and in 1.12 worlds alike; budding
    amethyst grows the rest over time, as in vanilla.
*   **Spyglass**: the zoom (field of view x0.1), hidden hand, hidden crosshair and scope overlay are client
    events in `SpyglassClient`, registered from `ClientSetup` only when the stream is on. The server finds
    what the player looks at every 5 ticks with its own ray (1.12's entity ray trace is client-only), up to
    100 blocks, not through blocks, skipping dead entities.
*   **Chimes**: `ProjectileImpactEvent` on amethyst plays the hit sound and the chime; walking plays it at most
    once a second per walker, louder when faster, not when sneaking.
*   Ore names: `gemAmethyst`, `blockAmethyst`, `blockGlassTinted`. The spyglass recipe takes any `ingotCopper`.

## Tested headlessly

`./gradlew runServer -Pselftest=all` (port 25843), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=2081 fail=0`** (72 of them amethyst's); **88 blocks, 1248 states, 82 item models,
369 sound files, 0 errors**.

*   Registration, light values, metadata round trips, push reactions, tinted glass opacity 255, which block
    ticks, ore names, stack size.
*   Growth over 5,000 random ticks: six buds, each pointing away, all grown to clusters; an unsupported bud
    breaks; a bud cannot be placed in mid-air.
*   Breaking as a survival player: cluster 4 shards with a pickaxe, 2 by hand, Fortune III averaging about 8.8,
    Silk Touch gives the cluster; buds nothing (Silk Touch gives them); budding amethyst nothing even with Silk
    Touch; the amethyst block nothing by hand, itself with a pickaxe; tinted glass itself.
*   Recipes: amethyst block, tinted glass (2), spyglass from our copper.
*   Spyglass: a parrot 20 blocks east seen (*Is It a Bird?*), not through a wall; a ghast 30 blocks away seen,
    the dead parrot skipped (*Is It a Balloon?*).
*   A geode placed in solid stone: 660 amethyst, 51 budding, 742 calcite, 861 smooth basalt, 11 buds, a hollow
    of 538 air. Real generated chunks: calcite and budding amethyst in 100 chunks (about four geodes).
*   Switched off (fresh world): boots, no advancement errors.

## Not tested

*   Everything client-side: the zoom, the scope overlay, the hidden hand, models, translucency of tinted glass.
*   Sounds and chimes being heard (the calls run; nothing listens on a server).
*   *Is It a Plane?* (no dragon fight headlessly); the spyglass's 1200-tick use and its sounds in play.
*   Pistons breaking budding amethyst in play (the push reaction is checked).
*   Buds and clusters underwater (waits for waterlogging).
