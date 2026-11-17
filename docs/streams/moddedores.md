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

**As built (2026-11-17).** The patches are `height/asm/ModCompatPatches` (one class per target), registered by the
height coremod's transformer next to its own (`HeightTransformer.modCompat()`); every site calls `height/compat/
BuildCraftHooks` or `KagicHooks`, which give the mod's own value in a 0-255 world. Each site is an exact pattern with
exactly one match; nothing depends on offsets, and no Minecraft member name is emitted (the sites pass the mod's own
locals). A missed site leaves that one class as its mod wrote it, with a warning, and never turns extended height off
(the sites go to `PatchReport.compatLines()`, apart from the coremod's own). A target class that is not there (the mod
not installed) is never seen. Offline: `tools/height/run.sh PatchCheck compat <buildcraft jar> <kagic jar>` runs them
against release jars and verifies the patched classes (all 19 sites found in BuildCraft 8.0.0, 7.99.24.8 and KAGIC
X11p13).

| Class | Site | Pattern | Becomes |
|---|---|---|---|
| `buildcraft.builders.tile.TileQuarry` | `onPlacedBy.depth` | `getY; iconst_1; isub; getstatic miningMaxDepth; isub; istore n` | (read only: the depth field and the bottom's local) |
| | `onPlacedBy.floor` | `invokevirtual isOutsideBuildHeight; ifeq; iconst_0; istore n` | `aload_0; quarryFloor(TileEntity)` = the world's bottom |
| | `readFromNBT.boxes`, `.iterator`, `.order` | `getfield frameBox; aload_1; ldc "frame"; ..`, then `ldc "boxIterator"; ..; putfield boxIterator` after it | after the putfield: `low = savedQuarryFloor(this, frameBox.max(), miningBox.min(), miningMaxDepth)`; `miningBox.setMin(low)` (BuildCraft ignores null); `boxIterator = quarryIterator(boxIterator, low)` (dropped when lowered, so the quarry walks the whole box again) |
| `buildcraft.core.gen.SpringPopulate` | `doPopulate.loop` | `iload i; iconst_5; if_icmpge` | (read only: the layer's local) |
| | `doPopulate.search` | `iload; iload i; iload; invokespecial BlockPos(III); astore; aload_0; aload; getBlockState` | after `iload i`: `springY(int, World)` = i + the world's bottom |
| | `doPopulate.spring` | `iload i; iconst_1; isub; istore` (both branches of `i > 0 ? i : i - 1` meet at the store) | before the store: `springY` |
| `mod.akrivus.kagic.blocks.BlockInjector` | `calcDistanceAutomatically.floor` | `iconst_m1; istore; iconst_5; istore` | `aload_1; injectorFloor(World)` = bottom + 5 |
| | `calcDistanceUsingRedstoneSignals.floorCheck` | `getY; iconst_5; if_icmplt` | `injectorFloor` |
| | `calcDistanceUsingRedstoneSignals.floorClamp` | `iload; iconst_5; aload; getY; isub; isub` | `injectorFloor` |
| | `calcDistanceUsingRedstoneSignals.seedTaken` | `iinc n, 256` | `iload n; aload_1; seedTaken(World); iadd; istore n` (1024 in a taller world) |
| | `injectGemSeed.floor` | `iload; iconst_5; if_icmple` | `injectorFloor` |
| `mod.akrivus.kagic.util.injector.InjectorResult` | `create.depthRatio` | `dload; aload_1; getY; i2d; ddiv` | after `getY`: `aload_0; depthY(int, World)` = max(y, 6) in a taller world |
| `mod.akrivus.kagic.init.ModEvents` | `onWorldTick.rubyY` | `aload r; dload; ldc 256.0; dload; invokevirtual setPosition(DDD)` | `aload r; meteorY(Entity)` = the world's top |
| `mod.akrivus.kagic.command.CommandMeteorRuby` | `execute.rubyY` | `i2d; ldc 256.0; aload_2` | `aload_2; meteorY(ICommandSender)` |
| | `execute.soundY` | `getX; sipush 256; aload_2` | `aload_2; meteorTop(ICommandSender)` |
| | `execute.messageY` | `dup; iconst_1; sipush 256; Integer.valueOf` | `meteorTop` |

Tests: `sh tools/height/selftest.sh modcompat` with dev-named copies of the two jars in `eclipse/mods`
(`tools/height/run.sh DeobfJar`), on a fresh Caves & Cliffs world: 97 checks, 0 failures. A quarry placed at y 70 gets a
box bottom of -64 (miningMaxDepth 512), and with a full battery it digs down to -53 below a cleared column; a box saved
at 0 loads at -64 with a new iterator; a spring column whose first bedrock is layer 2 gets its spring at -62 and water
above it; KAGIC's redstone injector at y 0 and -30 puts the seed 6 below (-6, -36), clamps at -59, and adds 1024 over a
seed; the automatic injector finds a sky shaft at -30 and stops at -59 in solid rock; `InjectorResult.create` at y -30
gives the same result as at y 6 (same rock, same random). The same code in the Nether (0-255) gives BuildCraft's and
KAGIC's own values (bottom 0, floor 5, 256). Without the mods (the whole suite) the test checks the hooks alone.

Found while building, left as the mods do it:
*   BuildCraft's spring search takes layer 0 first, and both 1.12's and 1.18's floors are bedrock there, so its spring
    block goes one below the world and only its water column (from layer 1 up to the first air) is made. That is
    BuildCraft's own behavior in a 0-255 world; with the patch Caves & Cliffs worlds get the same columns from -63 up.
*   KAGIC: `RuinStructure.checkCorners`, `MaskIsland`, `PyramidTemple` and `SeaShrine.checkBiome` cap terrain checks at
    y 255, and the Floating Peaks and Kindergarten decorators compare surface heights with 175 and 120. These are
    surface-height choices for KAGIC's own biomes and structures, not the world's bounds; with 1.18 terrain only a peak
    above 255 is affected (a structure skips it).
*   **Further mods:** surveyed from the packs' mod lists (RLCraft, Dregora) and requests: quarries and miners in other
    mods (Immersive Engineering excavator, Mekanism digital miner, Thermal, Industrial Foregoing, EnderIO and the like),
    ore and structure generators with y ranges, and anything that loops `y > 0`. Each gets a site table and a patch in
    `height/compat/`, the same pattern as above.

## Tests

*   O1: a self-test with stand-in mod ores (the dev environment can't load real coremod mods), checking blocks, drops,
    ore dictionary, smelting, hardness; a client check of generated textures (a screenshot grid).
*   O2: a census before and after, per ore, per y, in a Caves & Cliffs world (the family rig with a few real ore mods).
*   O3: per mod, a dev-run self-test with the mod's jar where it loads in the dev environment, otherwise a test server.
