# Modded ores and 1.18's height (plan, 2026-11-17)

Goal: every 1.12.2 mod's ores feel like they belong in a 1.18 world. They get deepslate forms, they generate in the
lower half (y -64 to 0) of Caves & Cliffs worlds, and mods that assume y 0 to 255 learn the new range.

## What happens today

*   **Caves & Cliffs worlds:** other mods' ore generators only pick y values in 1.12's range (0 and up), and their veins
    only replace stone. Below about y 8 the 1.18 terrain is already deepslate, so nothing modded generates from y 8
    down, and nothing ever generates below 0.
*   **Default worlds:** `deepslate/DeepslateLayer` swaps stone for deepslate after population and turns vanilla ores and
    our copper into their deepslate forms. Modded ores stay as stone-textured blocks inside deepslate.
*   **Mods with hard-coded heights:** BuildCraft's quarry stops at y 0; KAGIC's gem injectors refuse anything at y 11 or
    below (survey below).

## Order

O2 first (user's choice), then O1, then O3.

## Streams

### O1. Deepslate forms of modded ores (both world types)

*   **Find the ores:** every block (and metadata) under an `ore*` ore dictionary name whose host is stone, found at the
    last moment block registration allows (`RegistryEvent.Register<Block>` at LOWEST priority, which runs after every
    mod's handlers), by registry name and material, then checked against the ore dictionary in post-init. A candidate
    that turns out not to be an ore is never generated and is hidden from JEI and the creative tab. Nether and End ores
    (netherrack or end stone hosts) are left out.
*   **The blocks:** one block per ore state, named `cavesandcliffsbackport:deepslate/<modid>/<name>[_<meta>]` so it is
    stable from run to run. 1.18's deepslate ore rules: hardness 4.5 (1.5x stone ores), the same harvest tool and level,
    the same drops, experience and fortune (it hands those calls to the original ore), the same ore dictionary names (so
    every recipe and machine takes it), the same smelting result, and deepslate's sound.
*   **Textures, made at resource load:** the ore's texture minus the stone it was drawn on gives the overlay (pixels that
    differ from vanilla stone, or from the mod's own base when it has one); the overlay is drawn over 1.18's deepslate
    texture. Done in an in-memory resource pack, so it reloads with F3 + T and with resource packs, and a resource pack
    can replace any generated texture by name. Ores whose model is not a plain cube (overlay layers, emissive layers)
    take each layer the same way.
*   **Lang:** "Deepslate " + the ore's own name, in each language the family covers, built from the ore's localized name.
*   **Placement:** wherever a modded ore would sit in deepslate (both world types), the deepslate form goes instead.

**Built (2026-11-17, branch `claude/cool-lichterman-86c701`):**

*   `deepslate/ModdedOres`: candidates at `Register<Block>` LOWEST (an "ore" word in the registry path; rock, full
    opaque cube, breakable, no tile entity; one per metadata), named `cavesandcliffsbackport:deepslate/<modid>/<name>[_<meta>]`.
    Confirmed by an `ore*` ore dictionary name (sweep at item registration, `OreRegisterEvent` after, sweeps again in
    post-init and load-complete). Not confirmed, never placed and no creative tab (so not in JEI): no `ore*` name, a
    Nether or End name (registry path, ore dictionary name or translation key: nether, ender, end stone, glowstone...),
    or mined with something other than a pickaxe (oil sand). Left out with no block: Nether/End by registry path, this
    family's ores (`minecraft`, ours, any `*backport` namespace) and `deepslate.moddedOresExclude` (`modid:name`,
    `modid:name:meta`, `modid:*`). Settings `deepslate.moddedOres` (on) and `moddedOresExclude`.
*   `BlockModdedDeepslateOre` / `ItemModdedDeepslateOre`: hardness `max(4.5, 1.5 x ore)`, the ore's blast resistance,
    deepslate's sound; harvest tool and level, drops, experience, Silk Touch, light and render layers handed to the
    original with its state (a drop of the ore itself becomes the form). Ore dictionary names and smelting copied.
    Name: `gui.cavesandcliffsbackport.deepslate_ore` ("Deepslate %s"), with `.lowercase` for languages that put the
    ore's name after a word of their own in lower case (nl, pl, uk). Patterns taken from Mojang's 1.18.2 lang files.
*   Report: log line plus `config/cavesandcliffsbackport-modded-ores.txt` (forms, candidates that are not ores and why,
    left out and why).
*   Default worlds: `DeepslateLayer.swaps()` adds `ModdedOres.addSwaps`.
*   **Caves & Cliffs worlds (for O2):** `ModdedOres.deepslateForm(IBlockState)` returns the form's state, or null when
    the state is not a confirmed modded stone ore. Not wired into `terrain/`; O2 or the integrator calls it where O2
    picks the block to place.
*   Client (`deepslate/client`): an in-memory default pack (below resource packs, so they can replace any file by name;
    added to the live manager too, since the startup reload is before pre-init). At `TextureStitchEvent.Pre` each
    form's original model (`ModelLoaderRegistry`, through the state mapper) gives its textures; each is read once:
    mostly see-through (an overlay layer) is kept, a texture that matches vanilla stone (current pack's or the jar's) or
    the mod's own stone (per-pixel consensus of its other ore textures) has that stone replaced with
    `cavesandcliffsbackport:block/deepslate.png` (frames and `.mcmeta` kept). Served as
    `block/deepslate_ores/<modid>/<path>`, plus `block/deepslate_ores/_form/<modid>/<name>` for the generated blockstate's
    cube model. At `ModelBakeEvent` each form's model is the original's baked model with its quads moved onto the
    redrawn sprites (`BakedQuadRetextured`), by known names only (VintageFix), and the original's block and item color
    handlers tint it.
*   Tests: `moddedores` (stand-in ores in `selftest/StandInOres`), `runClient -Pclienttest=moddedores` (atlas, models,
    a wall of originals over forms, F3 + T, and drops/experience of every confirmed form on the integrated server).
*   Real mods (`G:\VBPTest\cc-o1`, Prism `cco1-client`, Forge 2847): Thermal Foundation 2.6.7.1 and Immersive
    Engineering 0.12-98, with JEI; plain, with OptiFine HD U G5 and with VintageFix 0.7.0 (dynamic resources): 362/0
    each. 16 real forms (Thermal's nine metal ores and destabilized redstone, IE's six) drop and give experience as
    their ores, with Thermal's and IE's own textures redrawn on deepslate. Thermal's oil sands and oil shale (dug with a
    shovel) and its netherrack and end stone clathrates are found but are not ores: hidden and never placed.
*   Not done: a Default-world generation census with real mods (the swap is tested with stand-ins).

### O2. Modded ore heights in Caves & Cliffs worlds

1.18 moved vanilla's ores down as the world grew: diamond from y 0-16 to -64-16, gold from 0-32 to -64-32, iron
centered on 16, lapis on 0. Other mods' ores should move the same way. Decided 2026-11-17: stretch as vanilla did, and double the veins.

*   While another mod's world generator runs in a Caves & Cliffs world, an ore block placed below the mod's "seam"
    (y 64) is placed at the remapped height `2y - 64` instead: 0 to 64 becomes -64 to 64, and everything above 64 stays.
    A vein that 1.12 put at y 10 lands at -44; a vein at 40 lands at 16. Each mod's ore keeps its depth relative to the
    others, as 1.18 did with vanilla's.
*   For that, its check for stone is answered for the target height too, and deepslate counts as stone there.
*   Only ore blocks from O1's list are moved, so structures, plants and anything else a generator places stay where the
    mod put them.
*   Vein counts double for ores the stretch moves, so the density in each block of rock stays the mod's own (the band is twice as tall): the generator runs a second time for that chunk with its own random, and from that run only the remapped low ores are kept.

#### Built (2026-11-17, branch `claude/cc-moddedores-o2`)

Setting `height.moddedOreHeights`, on by default, 18 languages. Code in `ores/` (`OreHeights`, `ModdedOres`), three
sites in the height coremod:

*   **`Chunk.populate`** calls `OreHeights.generateWorld` instead of Forge's `GameRegistry.generateWorld` (the one
    static call's owner swapped, same descriptor). Outside a Caves & Cliffs overworld with 1.18 terrain (`ModernGenerator`,
    dimension 0, min y -64, setting on) it calls Forge's method. Inside, it runs Forge's sorted generator list (read by
    reflection) with Forge's exact seeds and order, and marks each run of a generator that is not the family's own
    (`com.exiledradio.*`, the self-test's stand-ins excepted).
*   **`World.setBlockState(pos, state, flags)`**, first thing: `pos = OreHeights.setBlockPos(this, pos, state)`, then
    `state = OreHeights.placedState(this, pos, state)`. No jump, no frame. Outside a marked run both return their
    argument after one static field compare. In a run, an ore (below) set at y 0-63 inside the four chunks population may
    write (the chunk and its +x, +z neighbors: vanilla's +8 rule, so nothing reaches an ungenerated chunk) goes to
    `2y - 64`, if the target takes it; otherwise it is not placed. A position far below the world stands for "drop":
    vanilla's own range check then returns false. `placedState` → `OreHeights.placed(ore, replaced)` is the one place
    that picks the block; it returns the ore for now, O1 will return the deepslate form there.
*   **`Block.isReplaceableOreGen`** (Forge's stone check, which `WorldGenMinable`, CoFH World and Immersive Engineering
    call) has its body `return target.apply(state)` replaced by `OreHeights.isReplaceableOreGen`. In a run, at y 0-63,
    it also asks the generator's own predicate at the target, where our deepslate and tuff count as stone when the
    predicate takes stone, and answers yes if either does; it remembers both answers for that position. The next
    `setBlockState` there uses them: an ore needs the target to have passed; a non-ore needs its own position to have
    passed (so non-ores behave exactly as before). An ore set without a check is tested at the target with vanilla's
    stone (any variant), deepslate or tuff. Blocks that override `isReplaceableOreGen` keep their own answer.
*   **The doubling:** after every generator ran, each generator that has placed a moved ore (learned per generator, kept
    for the session) runs again for the chunk with `chunkSeed + salt`. In that run only moved ores are placed; every
    other block is dropped and every entity it spawns is cancelled (`EntityJoinWorldEvent`, HIGHEST). A generator that
    throws in that run is logged once and never run twice again; the first run is Forge's own and throws as before.
*   **Which blocks are ores (`ModdedOres`):** every block state under an `ore*` ore dictionary name, built on first use
    (after post-init) and rebuilt when a name is added later. Nether and End ores are left out: `oreNether*` / `oreEnd*`
    names, vanilla quartz ore, registry names with nether or end in them. Vanilla ores count too (a mod's extra iron
    veins move), but the 1.18 terrain's own ores are not placed by an `IWorldGenerator`, so they never move.
*   **Not covered:** worlds in Cubic Chunks (the coremod is off there), retrogen (CoFH's retrogen runs outside
    `Chunk.populate`), blocks set through `Chunk.setBlockState` directly, and generators that seed their own random from
    the chunk instead of the random Forge hands them (their second run repeats the first, so their veins are not
    doubled; nothing is placed twice).
*   **Tool fix:** `tools/height/run.sh` (PatchCheck) did not compile since `StellarCoreMixinPlugin`; it now adds Mixin
    and Gson to its class path and compiles with `-proc:none`. PatchCheck: OK on MCP and SRG names, both new sites
    verify.

**Self-test** `oreheights` (`OreHeightsSelfTest`, in `all` and in `tools/height/selftest.sh oreheights`): stand-in ores
(purpur, purpur pillar under `oreSelftestium*`, red nether brick under `oreNether*` and an overworld name), an end brick
non-ore, two stand-in generators. In a Caves & Cliffs world: ore at y 10 lands at -44 in deepslate with air at 10 (the
check answered at the target); 40 at 16; 100 stays; a non-ore and the Nether ore at 20 stay, nothing at -24; an ore
whose target is air is placed nowhere; an ore set without a check lands in tuff. Veins: 64 chunks, 4 per chunk, the
first run lands 185 of 256 (rock share at the targets 0.72), the second run 173, 358 in the world, all at even y in
-64..62, 0 above 63. In a Default world nothing moves and nothing doubles (223 of 256 veins in 0-63, none below).
Suite: `runServer -Pselftest=all` from a fresh `eclipse/world` 4582/0; `tools/height/selftest.sh
height,terrain,oreheights` 282/0. Release jar (`build --rerun-tasks`): 0 `/selftest/` entries, SRG names.

**Census** (`G:\VBPTest\cc-moddedores`, `run.sh`; Thermal Foundation 2.6.7.1 + CoFH Core/World + Immersive Engineering
0.12-98, seed 2026, square pregen r 48 = 9,216 chunks, counted by `tools/packs/orecensus.py` within r 47; the Default
world r 24). Blocks per chunk, y bands -64..-33 / -32..-1 / 0-15 / 16-31 / 32-63 / 64-127:

| Ore | Default (1.12 terrain) | C&C, setting off | C&C, setting on |
| --- | --- | --- | --- |
| TF copper (`ore:0`, 40-75) | 0 / 0 / 0 / 0 / 43.6 / 11.2 | 0 / 0 / 0 / 0.4 / 36.5 / 13.6 | 0 / 0.8 / 3.7 / 19.9 / 54.2 / 13.6 |
| TF tin (`ore:1`) | 0 / 0 / 0 / 13.7 / 22.6 / 0 | 0 / 0 / 0 / 12.8 / 20.1 / 0 | 0 / 25.7 / 15.0 / 14.8 / 10.4 / 0 |
| TF silver (`ore:2`) | 0 / 0 / 8.7 / 9.5 / 0.1 / 0 | 0 / 0 / 7.4 / 8.7 / 0.1 / 0 | 17.4 / 17.6 / 0.2 / 0 / 0 / 0 |
| TF lead (`ore:3`) | 0 / 0 / 7.6 / 13.8 / 1.1 / 0 | 0 / 0 / 6.8 / 12.7 / 1.1 / 0 | 14.8 / 25.6 / 2.0 / 0 / 0 / 0 |
| TF nickel (`ore:5`) | 0 / 0 / 3.4 / 0.7 / 0 / 0 | 0 / 0 / 2.9 / 0.6 / 0 / 0 | 6.9 / 1.3 / 0 / 0 / 0 / 0 |
| IE copper (`ore:0`) | 0 / 0 / 0 / 0 / 31.1 / 4.3 | 0 / 0 / 0 / 0 / 26.6 / 3.9 | 0 / 0 / 3.5 / 18.4 / 34.3 / 3.8 |
| IE lead (`ore:2`) | 0 / 0 / 4.9 / 8.0 / 1.3 / 0 | 0 / 0 / 4.4 / 7.6 / 1.2 / 0 | 9.6 / 15.3 / 2.3 / 0 / 0 / 0 |
| IE silver (`ore:3`) | 0 / 0 / 4.7 / 8.0 / 3.2 / 0 | 0 / 0 / 4.2 / 7.5 / 3.2 / 0 | 9.3 / 15.1 / 6.0 / 0 / 0 / 0 |
| IE nickel (`ore:4`) | 0 / 0 / 4.2 / 2.9 / 0 / 0 | 0 / 0 / 3.8 / 2.7 / 0 / 0 | 8.3 / 5.5 / 0 / 0 / 0 / 0 |
| IE uranium (`ore:5`) | 0 / 0 / 1.3 / 0.9 / 0 / 0 | 0 / 0 / 1.1 / 0.8 / 0 / 0 | 2.5 / 1.6 / 0 / 0 / 0 / 0 |

Below y 64 the totals per chunk go from 16.2 to 35.2 (TF silver), 20.6 to 42.4 (TF lead), 13.2 to 27.2 (IE lead): a bit
over 2x, because the off world loses the veins aimed at deepslate (y 0-7) and the on world does not. Ores above 63 and
every vanilla ore (the 1.18 terrain's own) are the same in both. Generation: the r 48 pregen (9,216 chunks, E-cores)
took 89 and 91 s off, 94 and 94 s on: 102.5 to 98.0 chunks/s, 4.4% slower with two ore mods; the cost is the second run
of each ore generator, so it grows with the number of ore mods.

### O3. Mods that assume y 0 to 255 (per-mod patches, extended worlds only)

Each patch asks `Heights.minY(world)` / `maxY(world)`, so in a normal world nothing changes.

*   **BuildCraft 8.0.0 (and 7.99.24.8, same classes):**
    *   `TileQuarry.onPlacedBy`: the mining box bottom is a hard-coded 0, which is why quarries stop there. It becomes
        the world's minimum. A quarry placed before the patch keeps its saved box; at load, a box whose bottom is 0 only
        because of that clamp is lowered.
    *   `SpringPopulate.doPopulate`: water springs look for bedrock at y 0-4. That becomes -64 to -60, so springs
        generate again.
    *   The mining well, pump and frame already ask the world and work.
*   **KAGIC X11p13:**
    *   `BlockInjector`: the y-5 floor in three places becomes the world's minimum + 5, and the "seed already there" value
        256 becomes 1024.
    *   `InjectorResult.create`: the depth bonus divides by the seed's y. It is clamped at y 6, so it never divides by 0 or
        goes negative, and every height 1.12 allowed keeps its numbers.
    *   Optional: meteor rubies may fall from up to y 320 instead of 256.
*   **Further mods:** surveyed from the packs' mod lists (RLCraft, Dregora) and requests: quarries and miners in other
    mods (Immersive Engineering excavator, Mekanism digital miner, Thermal, Industrial Foregoing, EnderIO and the like),
    ore and structure generators with y ranges, and anything that loops `y > 0`. Each gets a site table and a patch in
    `height/compat/`, the same pattern as above.

## Tests

*   O1: a self-test with stand-in mod ores (the dev environment can't load real coremod mods), checking blocks, drops,
    ore dictionary, smelting, hardness; a client check of generated textures (a screenshot grid).
*   O2: a census before and after, per ore, per y, in a Caves & Cliffs world (the family rig with a few real ore mods).
*   O3: per mod, a dev-run self-test with the mod's jar where it loads in the dev environment, otherwise a test server.
