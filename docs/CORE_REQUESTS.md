# Requests for Backport Core

What Caves & Cliffs Backport needs from Backport Core and does not have yet. Each entry says the exact API
wanted, why, and the local shim used meanwhile. This repo never edits Core.

## 1. List this repo in `copyToDependents`

Add `'CavesAndCliffsBackport'` to `dependents` in Core's `build.gradle`. Meanwhile `deps/` holds a copy of
`build/libs/BackportCore-1.0.0-dev.jar` taken by hand (2026-11-06, the `languages` branch build).

## 2. Waterlogging adapter

The `Waterloggable` adapter over Fluidlogged API (see Update Aquatic's `BACKPORT_CORE_WATERLOGGING.md`). Our
blocks that current Java makes waterloggable: candles, pointed dripstone, glow lichen, lightning rods,
amethyst buds and clusters, hanging roots, small and big dripleaf and its stem, light blocks, deepslate
and cut copper stairs and slabs, deepslate walls. Meanwhile they place only in air.

## 3. A weathering registry (`weathering.Weathering`)

1.17's copper oxidizes, is waxed with honeycomb, scraped with an axe and cleaned by lightning. Tricky
Trials' copper (chiseled copper, doors, trapdoors, grates, bulbs, copper golems' statues, 1.21.9's copper
chests and lanterns) does the same thing and must join the same chain: a lightning strike or an axe
should treat every mod's copper alike. Wanted:

```java
Weathering.chain(Block unaffected, Block exposed, Block weathered, Block oxidized);   // oxidation order
Weathering.waxed(Block plain, Block waxed);                                           // honeycomb pair
Block next = Weathering.next(block);        // null at the end
Block previous = Weathering.previous(block); // null at the start
Block waxedOf = Weathering.waxedOf(block), unwaxed = Weathering.unwaxed(block);
boolean wax = Weathering.isWax(ItemStack);  // honeycomb from any installed source
```

States carry across by property name (`util.BlockStates`). Meanwhile `copper/Weathering` in this mod is
that class, with the same method names, so moving it is a rename.

## 4. A goat ram hook for goat horns

Wild Update's goat horns drop when a goat rams a log, stone, ore or packed ice (1.19). Our goat posts a
Forge event when its ram hits a block. The event class must live where both mods can see it:

```java
// com.exiledradio.backportcore.event.GoatRamBlockEvent extends net.minecraftforge.fml.common.eventhandler.Event
public GoatRamBlockEvent(EntityLiving goat, BlockPos pos, IBlockState state)
```

Meanwhile our goat writes the 1.19 NBT tags `HasLeftHorn` / `HasRightHorn` (both true) so Wild Update can
read them, and posts nothing.

## 5. Texture and lang packs: 1.17's look for vanilla blocks

For Core's `modern` pack (`tools/modern-textures/manual_map.py`):

- 1.17's ore textures (21w05a): coal, iron, gold, redstone, lapis, diamond, emerald ore, and the nether
  quartz and nether gold ores 1.17 touched up. From the 1.18.2 jar's `block/<name>_ore.png`.
- 1.17 renamed the grass path to **Dirt Path** (`tile.grassPath.name`), and the name of a few more:
  "Raw Fish" items are Update Aquatic's already. A lang override for `tile.grassPath.name` in the modern
  pack, from 1.18's `block.minecraft.dirt_path`.

## 6. Trades

Nothing missing: `TradeRegistry.addTraderTrade` covers 1.17's wandering trader additions. Noted here only so
the Core maintainer knows we call it.

## 7. Ownership declarations

Nothing missing: we call `BlockOwnership.declare("cavesandcliffsbackport", ...)` for the blocks later
updates copy (copper, tuff, deepslate, moss, lightning rod, powder snow).

## For Village & Pillage's maintainer (not Core)

- Foxes eat and carry glow berries as they do sweet berries (1.17): look up
  `cavesandcliffsbackport:glow_berries` by name.
- 1.17's mason trade (dripstone blocks) and wandering trader items: we add them through Core's
  `TradeRegistry`; nothing to do unless the career key for the mason differs from `"minecraft:mason/mason"`.

## 8. A shared modern wall (and slab) class

1.16 gave walls low and tall sides; every family mod with new walls needs the same block (our deepslate
walls, Tricky Trials' tuff walls, Wild Update's mud brick walls), or walls of different mods won't match
side by side. Ours is `common/BlockCCWall` (sides `none|low|tall`, `up`, all actual state, meta 0, face
shapes as 1.12's walls so they join cobblestone walls) and `common/BlockCCSlab` (`<name>_slab` with `half`
and a one-value `variant=default`, plus `<name>_double_slab`). Wanted: both in Core under
`com.exiledradio.backportcore.building`, same properties, so every mod's walls and slabs behave alike.
Meanwhile they live here.

## 9. Glow lichen and sculk vein (multiface)

Wild Update asks Core for a shared `BlockMultiface` with its faces in a tile entity (NBT byte `Faces`).
Glow lichen generates on most cave walls in 1.18, so a tile entity per block would be costly; we lean to
four registered blocks sharing one item (bits in metadata, no tile entity). To settle with Wild Update
and Core before our lush stream (L) is built.

## 10. Ownership per block, not per mod

`BlockOwnership.provides(modid, BooleanSupplier)` keeps one switch per mod (the last call wins), but each of
our streams switches off on its own: with copper off and deepslate on, Core answered "Caves & Cliffs provides
copper" and no later update registered a copy (found by Tricky Trials, 2026-11-07). The table is also
first-declared-wins, and later updates declare our names for us. Wanted:

```java
BlockOwnership.provides(String modid, java.util.function.Predicate<String> blockName);
```

Meanwhile (`common/Owners.declare`): each stream declares its names in our pre-init, and this mod loads
`before:` Wild Update, Trails & Tales and Tricky Trials so ours come first; a switched-off stream declares its
names for the owner `cavesandcliffsbackport:switched_off`, which is never a loaded mod, so Core answers that
nobody provides them. With the predicate, that pseudo-owner goes away.
