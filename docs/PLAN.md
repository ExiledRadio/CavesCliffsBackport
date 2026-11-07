# Caves & Cliffs Backport: plan

Everything Java Edition 1.17 (Caves & Cliffs: Part I, with 1.17.1) and 1.18 (Part II, with 1.18.1 and 1.18.2)
added or changed, for 1.12.2 Forge, grouped into streams that are built one at a time, each in its own
package, branch and worktree.

Mod id `cavesandcliffsbackport` (permanent), display name "Caves & Cliffs Backport", package
`com.exiledradio.cavesandcliffsbackport`, branch prefix `cc-`, dev-server ports 25840-25859.

Source: minecraft.wiki's *Java Edition 1.17* and *Java Edition 1.18* pages and the pages they link. Where
current Java behaves differently from 1.17 or 1.18, current Java wins (marked **now:**). Numbers the wiki
leaves out come from 1.14.4's decompiled code where 1.14 had the thing, and from the 1.18.2 client jar
otherwise. Assets come from the 1.18.2 client jar and the 1.18 asset index (`tools/assets.py`).

**Order: 1.17 first.** Everything in 1.17 fits 1.12.2's normal world height, so it is planned and built
first. Lush caves, dripstone caves and geodes are added as cave decoration inside y 0-255. The 1.18 terrain
(world height, noise caves, aquifers, ore distribution, new mountains) is the user's call: see *Still open*.

## Scope at a glance

| In | Out (and why) |
| --- | --- |
| Every 1.17 block, item, mob, effect-like mechanic (freezing), advancement and generated feature; 1.17's changes to existing blocks (ore drops, cauldrons); 1.18's music, its music disc and its cave biomes as decoration | The 1.18 terrain until the user decides (*Still open*) |
| Lush caves and dripstone caves as 3D cave regions inside the existing height | Data-pack, command, render-distance and options changes |
| Glow ink on signs, if the user wants a position-based store (*Still open*) | Experimental-only 1.17 content that shipped later (below) |

## Content owned by another mod of the family

Rule 1 of the family plan: the update that first shipped a thing **outside experiments** owns it.

Not ours, although 1.17 had it in some form:

- **Wild Update (1.19)**: sculk, sculk sensor (creative-only in 1.17, released in 1.19), the deep dark,
  reinforced deepslate, goat horns, mud and mangroves.
- **Tricky Trials (1.21)**: bundles (experimental in 1.17, released 1.21.2), chiseled copper, copper doors,
  trapdoors, grates and bulbs, polished tuff, tuff bricks and their sets, copper golems, chests and torches,
  pale moss.
- **Trails & Tales (1.20)**: the calibrated sculk sensor (it uses our amethyst shards).
- **Buzzy Bees (1.15)**: honeycomb, which waxes copper and makes candles (*Still open* 1).
- **Nether Update (1.16)**: basalt (we own only smooth basalt).
- **Update Aquatic (1.13)**: the tropical fish bucket axolotls breed with, the drowned that drop copper.
- **Village & Pillage (1.14)**: the composter, the stonecutter, foxes (that eat glow berries in 1.17).

None of these is copied here: everything we need from them is a soft lookup by registry name (Rule 2).

Ours, and needed by later updates (they register copies when we are absent): copper ingots and blocks,
tuff, deepslate and its sets, amethyst shards, moss blocks, goats, lightning rods, powder snow.

## Streams

Each stream owns one package under `com.exiledradio.cavesandcliffsbackport`, one config category with an
`enabled` switch (already present in `ModConfig`), one block of packet ids and one block of entity ids. No
stream calls another's classes; when one needs another's block or item it looks it up by name with
`common/Owners.ours(name)` / `ourBlock(name)`, which return null when that stream is switched off.

| # | Stream | Package | Config | Entity ids | Packet ids | Part |
| --- | --- | --- | --- | --- | --- | --- |
| D | Deepslate, stone and ores | `deepslate` | `deepslate` | none | 5-9 | 1.17 |
| Cu | Copper and lightning rods | `copper` | `copper` | none | 10-14 | 1.17 |
| Am | Amethyst, tinted glass, spyglass, geodes | `amethyst` | `amethyst` | none | 15-19 | 1.17 |
| Dr | Dripstone and new cauldrons | `dripstone` | `dripstone` | 1-4 | 20-24 | 1.17 |
| L | Lush caves (moss, azalea, cave vines, dripleaf, spore blossom, glow lichen, roots) | `lush` | `lush` | none | 25-29 | 1.17 |
| Ps | Powder snow and freezing | `powdersnow` | `powderSnow` | none | 30-34 | 1.17 |
| Ca | Candles and candle cakes | `candle` | `candles` | none | 35-39 | 1.17 |
| Gs | Glow squid, glow ink, glow item frames | `glowsquid` | `glowSquid` | 5-9 | 40-44 | 1.17 |
| Ax | Axolotls | `axolotl` | `axolotls` | 10-14 | 45-49 | 1.17 |
| Gt | Goats | `goat` | `goats` | 15-19 | 50-54 | 1.17 |
| X | Other 1.17 blocks, items and changes | `blocks` | `blocks` | none | 55-59 | 1.17 |
| Mu | 1.18 music and the Otherside disc | `music` | `music` | none | 60-64 | 1.18 |
| T | 1.18 terrain (waits for the user) | `terrain` | `terrain` | none | 65-74 | 1.18 |

Packet ids 0-4 are `common`'s. Build order: D, Cu, Am, Dr, L, Ps, Ca, Gs, Ax, Gt, X, Mu; T when decided.
D comes first because copper ore, geodes and dripstone caves sit in its deepslate.

**Status (2026-11-07):** D, Cu and Am built, tested and merged (`docs/streams/deepslate.md`, `copper.md`,
`amethyst.md`). Double slabs are `<name>_double_slab`, as Village & Pillage's released ones. Next: Dr.

### Shared by the cave streams: `common/CaveRegions`

1.12.2 biomes are per column, so a cave biome cannot be a biome. Instead a 3D region noise (seeded from the
world seed, cheap, no state) says at any underground position whether it is in a **lush** region, a
**dripstone** region or neither, at roughly the size and frequency of 1.18's cave biomes (a few hundred
blocks across, tens of blocks tall). Streams L and Dr decorate the air spaces inside their regions; Ax spawns
axolotls only there; Dr adds the extra copper ore and Cu reads it. It also answers "is this underground?"
(no sky, below the surface height recorded when the chunk generated). Config `caves`: region frequency and
size, `lushRegions`, `dripstoneRegions`. Biome-specific music (stream Mu) uses it client-side through the
same noise (the seed reaches the client in our login packet, id 0).

### D. Deepslate, stone and ores

- **Deepslate** (pillar axis, hardness 3, resistance 6, drops cobbled deepslate, Silk Touch itself; deepslate
  sound type), **cobbled deepslate** (3.5/6), **polished deepslate**, **deepslate bricks**, **cracked deepslate
  bricks** (smelted bricks), **deepslate tiles**, **cracked deepslate tiles**, **chiseled deepslate** (two
  cobbled deepslate slabs); stairs, slabs and walls for cobbled, polished, bricks and tiles (12 blocks plus
  double slabs). Walls are our own class with 1.16+'s low and tall sides (**now:**), since 1.12's
  `BlockWall` is fixed to its variants.
- **Infested deepslate** (silverfish, as infested stone).
- **Calcite** (0.75), **tuff** (1.5/6), **smooth basalt** (1.25/4.2; smelted from basalt when Nether Update
  Backport is installed).
- **Deepslate ores**: coal, iron, gold, redstone (lit form too), lapis, diamond, emerald (hardness 4.5,
  resistance 3); drops and experience as the stone ores. Deepslate copper ore is stream Cu's.
- **Raw iron**, **raw gold**, their blocks; iron and gold ore (stone and deepslate) drop raw ore with
  Fortune's ore formula (**now:**, config `rawOreDrops`); raw ore smelts to ingots (0.7 and 1.0 experience),
  blasts in a 1.14 blast furnace when Village & Pillage is installed.
- **Generation within 0-255**: deepslate below y 8, mixed into stone from y 8 to y 16 (config `layerTop`,
  `layerBlend`), with tuff blobs in it; every stone ore inside it becomes its deepslate ore. Done as a
  post-population pass on chunks whose neighbors have all populated, so caves, ravines, dungeons and every
  mod's ores generate first and the pass only swaps blocks. Only chunks generated with the mod; a chunk
  NBT flag marks each one. Modded ores the table doesn't know stay as they are.

### Cu. Copper and lightning rods

- **Copper ore**, **deepslate copper ore** (drop 2-5 raw copper, **now:**, Fortune), **raw copper**, **block
  of raw copper**, **copper ingot**, **block of copper**, **cut copper**, **cut copper stairs** and **slabs**,
  each in four stages (unaffected, exposed, weathered, oxidized) and waxed: 32 blocks plus double slabs.
- **Oxidation** on random ticks by current Java's rule (a chance per tick, slowed by less-oxidized copper
  nearby and sped by more-oxidized copper). **Waxing** with honeycomb (*Still open* 1); **scraping** with any
  axe (removes wax first, then one stage; one durability); **lightning** strips a struck block and a random
  walk of nearby copper.
- **Lightning rod** (facing, powered for 8 ticks when struck, redirects lightning within 128 blocks; mobs
  near it still convert); redirect done when the bolt joins the world.
- Copper ore generation: 1.17's numbers inside 0-255 (6 veins of 10, y 0-96; more in dripstone regions).
- Drowned drop copper ingots (11%, +2% per Looting) when Update Aquatic Backport's drowned exist.
- Advancements *Wax On*, *Wax Off*, *Surge Protector*.

### Am. Amethyst, tinted glass, spyglass, geodes

- **Block of amethyst**, **budding amethyst** (cannot be obtained or pushed; grows buds on random ticks),
  **small**, **medium** and **large amethyst bud**, **amethyst cluster** (six facings, light 1/2/4/5),
  **amethyst shard** (cluster drops 4 with a pickaxe and Fortune, 2 otherwise), amethyst chimes on stepping
  and on projectiles hitting.
- **Tinted glass** (blocks light), **spyglass** (zoom while used, scope overlay; client hook only).
- **Geodes**: smooth basalt shell, calcite, amethyst and budding amethyst, with crack and buds, 1.17's
  frequency (1 in 24 chunks) and y 6-46; looks up calcite and smooth basalt by name.
- Advancements *Is It a Bird?*, *Is It a Balloon?*, *Is It a Plane?*.

### Dr. Dripstone and new cauldrons

- **Dripstone block**; **pointed dripstone** (thickness tip, tip merge, frustum, middle, base; up or down):
  placement and merging, stalactites falling when unsupported (own falling entity, ids 1-4; damage per block
  fallen), stalagmites doubling fall damage, tips broken by tridents, growth under water sources, dripping
  water and lava with particles, filling cauldrons below.
- **Lava cauldron** (1.17; a bucket of lava into a cauldron; hurts and burns like lava; light 15) and the
  cauldron change-back when emptied; dripping lava fills it.
- **Dripstone regions** (`CaveRegions`): dripstone clusters, large dripstone columns, pools, extra copper.

### L. Lush caves

- **Moss block** (bone meal spreads moss and plants over stone-like blocks), **moss carpet**.
- **Azalea**, **flowering azalea** (bone meal grows an azalea tree), **azalea leaves**, **flowering azalea
  leaves**, potted azaleas (our own potted blocks, placed through the vanilla flower pot's right-click).
- **Rooted dirt** (hoe gives dirt and hanging roots), **hanging roots**, **spore blossom** (ceiling, particles).
- **Cave vines** and **glow berries** (light 14 with berries; picking; the berries plant vines and are food).
- **Big dripleaf** (tilts under an entity, stopped by redstone) and **small dripleaf** (two tall; bone meal).
- **Glow lichen** (any of six faces, light 7, bone meal spreading, shears): six faces need 64 states and
  1.12 has 16 metadata values. Leaning to four registered blocks that share one item (no tile entity, since
  1.18 puts lichen on most cave walls); Wild Update asks Core for a tile-entity multiface shared with its
  sculk vein instead (CORE_REQUESTS 9). Settled before L is built.
- **Lush regions** (`CaveRegions`): moss floors and ceilings, clay pools with dripleaves, cave vines, spore
  blossoms, azalea trees on the surface above with rooted dirt and hanging roots under them. Glow lichen on
  cave walls everywhere underground.
- Composting values when a composter exists (Village & Pillage).

### Ps. Powder snow and freezing

- **Powder snow** (entities sink in it and slow down, except those wearing leather boots, which walk on it;
  falling into it cancels fall damage), **powder snow bucket** (placing, picking up with an empty bucket).
- **Freezing**: entities inside build up freeze ticks to 140, then take damage every 40 ticks (more for
  blazes and other heat-loving mobs; none for freeze-immune ones); leather armor prevents it; the client
  frost overlay. Kept on our own entity data, so it works for every mob.
- **Powder snow cauldron** (snowfall fills cauldrons with powder snow, three levels, a bucket takes it).
- **Skeletons converting to strays** after freezing in powder snow (**now:** timings).
- Generation: patches in snowy high ground of the existing terrain (our placement until the user decides on
  1.18's snowy slopes and groves).
- Advancement *Light as a Rabbit*.

### Ca. Candles and candle cakes

- **Candle** and 16 dyed candles, one to four per block, lit with flint and steel or fire charges, put out
  by hand; light 3 per lit candle; dyed by crafting. **Candle cakes** (17), eaten as cakes, dropping the
  candle.

### Gs. Glow squid, glow ink, glow item frames

- **Glow squid** (dark water spawning, darkening when hurt, full-bright texture), **glow ink sac** (drops
  1-3), **glow item frame** (its own entity, contents drawn full-bright). Advancement *Glow and Behold!*
  only if glowing sign text is built (*Still open* 3).

### Ax. Axolotls

- Five colors (blue 1 in 1200 when bred), playing dead, hunting aquatic mobs (Update Aquatic Backport's fish
  and drowned by name, squid, glow squid, guardians), giving Regeneration and clearing Mining Fatigue for the
  player who fought with them, drying out on land, breeding with a bucket of tropical fish (Update Aquatic
  Backport's; otherwise config `breedingItem`), **bucket of axolotl** keeping color and age; spawning in
  lush region water. Advancements *The Healing Power of Friendship!*, *The Cutest Predator*.

### Gt. Goats

- Goats and screaming goats (2%): ramming mobs and players, high jumps, less fall damage, milk with a bucket,
  breeding with wheat, sounds; spawning in the extreme hills family (1.18's peaks and slopes when the
  terrain stream exists). Goat horns are Wild Update's: our goat records the 1.19 NBT tags it needs and posts
  a ram event Core can carry (`docs/CORE_REQUESTS.md`).

### X. Other 1.17 blocks, items and changes

- **Light** block (creative, levels 0-15, invisible unless held), the *Caves & Cliffs* advancement (fall
  from the top of the world to its bottom and survive; 1.12's heights).
- For Backport Core's packs: 1.17's new ore textures and "Dirt Path" (`docs/CORE_REQUESTS.md`).

### Mu. 1.18 music and the Otherside disc

- **Music disc "otherside"** (dungeon and stronghold loot as now), the 1.18 overworld tracks joining the game
  music (in a built-in resource pack behind a switch), and the lush and dripstone tracks played in their
  regions.

### T. 1.18 terrain (waits for the user)

See *Still open* 4. Nothing is built before the user picks an option.

## Waterlogging

Waterlogging comes from Backport Core's adapter over Fluidlogged API, which is not built yet. These blocks
are waterloggable in current Java and are built without it for now (placeable only in air); they move onto
the adapter in a later pass: candles, pointed dripstone, glow lichen, lightning rods, amethyst buds and
clusters, hanging roots, small and big dripleaf (and big dripleaf stem), light blocks, deepslate and cut
copper stairs and slabs, deepslate walls. Small dripleaf generates underwater in 1.18: until then it
generates on clay at pool edges.

## Shared files and rules

The same rules as Update Aquatic Backport's plan:

- **`ModConfig.java`**: every category already exists; a stream edits only inside its own class; category
  classes extend `Object`; new fields get `@Config.LangKey` with a name line and a `.tooltip` line.
- **`NetworkHandler.java`**: packets only from your id block, in id order.
- **`CavesAndCliffsBackport.java`**: at most one line per lifecycle hook, calling your package.
- **`selftest/SelfTestRunner.java`**: one line in `TESTS`.
- **`en_us.lang`, `sounds.json`**: written only by your generator through `tools/assets.py`
  (`merge_lang` / `merge_sounds`).
- **`common/`**: read freely; changes are the integrator's (this session).
- **Registration** from your own `@Mod.EventBusSubscriber` class through `common/Content`.
- **Other streams' things** by name with `Owners.ours(...)`; other family mods' with `Owners.item(owner, name)`.
- **Languages (Rule 4)**: every user-facing string is a lang key; vanilla names come through
  `assets.name(...)` from 1.18's `en_us.json`; the 18 languages from Backport Core's `tools/lang/build_langs.py`.
- **Docs**: `docs/streams/<stream>.md`: changelog section, how it works, tested, not tested.

## Build and test

- `./gradlew build` never deploys unless `ccbDeployDir` is set in `~/.gradle/gradle.properties`.
- Backport Core: `deps/BackportCore-1.0.0-dev.jar`, a plain `compile` dependency (copied from Core's
  `build/libs`; Core's `copyToDependents` doesn't list this repo yet - see `CORE_REQUESTS.md`).
- `./gradlew runServer -Pselftest=<stream>` on port 25840 (main) or `25840 + row` in a stream worktree, then
  `python tools/check_assets.py` with 0 errors; `./gradlew build` last; the jar's classes carry `func_` names.
- Never `cleanRunServer`. Kill only our own server, by PID. RAM check (8 GB free) before every Gradle run.
- No mixins, no coremod, no ASM. Client-only types only in `client/` classes.

## Integrations with other mods of the family (Rule 2)

All soft (`Loader.isModLoaded` and names):

- **Village & Pillage**: composting (azaleas, moss, dripleaves, glow berries, spore blossom, hanging roots,
  leaves); stonecutter recipes for the deepslate and copper sets (its stonecutter reads them as it reads
  vanilla's); raw ore in its blast furnace; wandering trader and mason trades through Core's
  `TradeRegistry` (1.17's trader sells pointed dripstone, moss, rooted dirt, small dripleaf, glow berries).
  For its maintainer: foxes eating glow berries (`docs/CORE_REQUESTS.md`, family notes).
- **Update Aquatic**: drowned copper drops; axolotls breeding with its tropical fish bucket and hunting its
  fish and drowned.
- **Nether Update**: smooth basalt from its basalt.
- **Buzzy Bees**: its honeycomb waxes copper and makes candles.
- **Later updates** read ours by name: Wild Update (goats for horns, deepslate for the deep dark),
  Trails & Tales (amethyst shards, moss for sniffers), Tricky Trials (copper, tuff, lightning rods).

## Still open (the user's call)

1. **Honeycomb without Buzzy Bees.** Waxing copper and crafting candles need honeycomb, which comes only from
   bees. Without Buzzy Bees Backport installed: (a) accept any mod's honeycomb (`honeycomb` ore name) and
   nothing else, so waxing and candles need a bee mod; (b) as (a), plus a fallback recipe (candles from
   string and slimeball or tallow-like vanilla items, waxing with a slimeball); (c) register our own copy of
   the honeycomb item (Rule 1) with a stand-in source. **Built until answered: (a).**
2. **Ore drops.** Iron and gold ore dropping raw ore changes an existing vanilla block in every pack
   (auto-smelting and ore-doubling mods expect the ore block). Built: on, with config `rawOreDrops`.
3. **Glowing sign text (glow ink sac on signs).** 1.12's sign loses unknown NBT on save, so the glow must
   live in a per-world list of sign positions and the sign renderer must be replaced for all signs (a
   conflict with other sign-rendering mods). Build it or skip it? **Not built until answered.**
4. **The 1.18 terrain** (world height y -64..319, noise caves - cheese, spaghetti and noodle - aquifers, 1.18
   ore distribution and ore veins, mountains and their biomes: meadow, grove, snowy slopes, jagged, frozen
   and stony peaks). Three options, with speed a hard requirement for the RLCraft Revamped pack:

   | | A. Full extended height (y -64..319) | B. 1.18 shapes inside y 0..255 | C. Leave the terrain alone |
   | --- | --- | --- | --- |
   | What | Real 1.18 heights. 1.12 hard-codes 16 sections per chunk in `Chunk`, `World`, the chunk packet, lighting and the client's render chunks, so this **needs bytecode patching** (as the existing extended-height mods do) | Our own chunk generator with 1.18's density terrain, noise caves, aquifers and ore veins, mapped into 0..255: sea level stays 63; y 0-63 holds the deep part (caves squeezed about 2:1), mountains reach about 250 | Vanilla 1.12 terrain; what stays from 1.18 is deepslate, cave regions, geodes and the 1.17 ores (already planned) |
   | Cost | Months; every chunk, packet, lighting and render path, plus a save format | Large but bounded: generator, climate noise and biome placement, caves, aquifers, ore veins, six new biomes; no patches | None beyond the 1.17 streams |
   | Speed | Measured ~4.5x slower generation for the existing mods (vanilla 68 chunks/s vs 15); 1.5x the blocks per chunk at best | Estimated 1.5-2.5x vanilla: the same block count as vanilla, 1.18's cell interpolation, column noise cached and computed ahead on worker threads | Vanilla's |
   | Existing worlds | New worlds only; old chunks need conversion; mixing breaks saves | New chunks only, as a world type (or default for new worlds); old chunks keep their terrain, with a seam at the border | No effect |
   | Compatibility | Breaks every mod that indexes `y >> 4` or assumes 0..255 (many structure mods, lighting and render mods); conflicts with the other extended-height mods | Every structure, ore and biome mod that assumes 0..255 keeps working; Forge-registered modded biomes are placed by our climate noise; conflicts only with other terrain generators (one terrain mod at a time) | Total |
   | Bytecode patching | Yes - not without the user's approval | No | No |

   A smaller variant of C: keep vanilla's surface and replace only the caves with 1.18-style noise caves
   (through Forge's cave generator event, no patches), about 1.2-1.5x vanilla's time.
