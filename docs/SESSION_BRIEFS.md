# Briefs for the streams

The streams are built one at a time by the integrator session (twelve agents share the machine). These
briefs exist so any stream can be handed to its own session once the user raises that limit. Each is
complete with `docs/PLAN.md` (its stream section and *Shared files and rules*); a finished stream doc in
`docs/streams/` shows what "done" looks like.

## Common to every brief

- **Where**: `git worktree add ..\CavesAndCliffsBackport-<stream> -b cc-<stream> main`. Commit on that
  branch; never push. The integrator merges into `main`.
- **Read first**: `docs/PLAN.md`, a finished `docs/streams/*.md`, and the memory notes on FG2, reobf,
  `@Config`, sided classes, lang `pack.mcmeta`, the dev-env coremod crash, VintageFix and 1.12 worldgen.
- **Behavior**: current Java Edition per minecraft.wiki; 1.14.4's decompiled code for numbers it has. Assets
  from the 1.18.2 jar and 1.18 asset index through `tools/assets.py`, in a `tools/<stream>/generate.py`.
- **Code**: your package only, registration through `common/Content`, your `ModConfig` category, your
  entity and packet ids from the plan's table, other streams' things by name with `Owners.ours(...)`.
- **Test**: `selftest/<Stream>SelfTest.java` (one line in `SelfTestRunner.TESTS`), `./gradlew runServer
  -Pselftest=<stream>` on port `25840 + row number` (row = the stream's line in the plan's table, D = 1),
  set in your worktree's `eclipse/server.properties` (copy it and `eula.txt` from the main worktree), then
  `python tools/check_assets.py` with 0 errors and `./gradlew build` last. RAM check first (8 GB free).
- **Languages**: `python tools/common/generate.py` after adding config fields; `python tools/lang/vanilla_keys.py`.
- **Done**: content working headlessly, build clean, `func_` names in the jar, `docs/streams/<stream>.md`.
  No mixins, no coremod. Never name other backport mods in user-facing text. American spelling.

## Per stream

- **D** (row 1, 25841): deepslate and its sets, calcite, tuff, smooth basalt, deepslate ores, raw iron and
  gold, raw ore drops, the deepslate layer pass.
- **Cu** (row 2, 25842): copper ores, raw copper, the 32 copper blocks, oxidation, waxing, scraping,
  lightning, lightning rod, copper ore generation, drowned drops.
- **Am** (row 3, 25843): amethyst blocks, buds and shards, tinted glass, spyglass, geodes.
- **Dr** (row 4, 25844): dripstone, pointed dripstone and its falling entity, lava cauldron, dripstone regions.
- **L** (row 5, 25845): moss, azaleas, rooted dirt, hanging roots, spore blossom, cave vines, dripleaf,
  glow lichen, lush regions.
- **Ps** (row 6, 25846): powder snow, bucket, cauldron, freezing, strays.
- **Ca** (row 7, 25847): candles and candle cakes.
- **Gs** (row 8, 25848): glow squid, glow ink sac, glow item frame.
- **Ax** (row 9, 25849): axolotls and their bucket.
- **Gt** (row 10, 25850): goats.
- **X** (row 11, 25851): light block, the *Caves & Cliffs* advancement.
- **Mu** (row 12, 25852): 1.18 music, the Otherside disc.
- **T** (row 13, 25853): waits for the user's terrain decision.
