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

This only works if later updates declare our names (their Rule 1 shims) from their **pre-init**, never their
mod constructor: every mod's constructor runs before anyone's pre-init, so a constructor declaration beats
ours whatever the load order (Tricky Trials hit this and moved theirs, 7b7e501). Worth a line in Core's API
docs next to `declare`.

## 11. `WorldHeights` for our extended height

The user chose full 1.18 height for this mod (docs/EXTENDED_HEIGHT.md). Core's `WorldHeights` already answers
for the other extended-height mods; it needs a branch for ours, found by reflection so Core never links us:

```java
// com.exiledradio.cavesandcliffsbackport.height.Heights (public, static, no client types)
int minY(World world);           // -64 in an extended overworld, else 0
int maxY(World world);           // 320 (exclusive) in an extended overworld, else 256
int slot(World world, int y);    // section array index: 0-15 = y 0-255, 16-19 = 256-319, 20-23 = -64..-1
boolean isExtended(World world);
```

`WorldHeights.minY/maxYExclusive/storageIndex` call these when `cavesandcliffsbackport` is loaded.
`enterFullHeight()` needs no change for us: we don't clip population to 0-255, so it can return a no-op.
Wild Update's deep dark and Tricky Trials' trial chambers then use 1.18's heights in our worlds.

Also needed (EXTENDED_HEIGHT.md section 3): fluid states outside y 0-255. Fluidlogged API's per-chunk store packs
y in 8 bits, so either its author widens the key for extended worlds, or Core's `Waterloggable` adapter stores
those states itself.

**Status (2026-11-11):** the `WorldHeights` branch and `TerrainSource` (a generator that says where its surface
is) are on Core branch `cc-heights` (e680f1d), for 1.2.0. **User decision:** Core keeps water outside 0-255
itself. Design, read from Fluidlogged API 3.3.3:
- **Where the store is made.** Each chunk's store is created in `PluginChunk$Hooks.createCapabilityInstance(Chunk)`,
  which already picks a per-cube store under Cubic Chunks. It is held through `IHardcodedCapability`
  (`get/setFluidStateCapability`). Everything else asks `IFluidStateCapability.getContainer(y)`. The sync packet
  `SMessageSyncFluidStates` carries an `indexY` for per-section stores, and special-cases only
  `FluidStateCapabilityVanilla`.
- **The plan.**
  - A Core patch at the end of `createCapabilityInstance`: for a chunk of an extended world
    (`WorldHeights.isCavesAndCliffsExtended`), set a Core `IFluidStateCapability` with one container per
    16-block section, keyed by signed section y.
  - Its NBT holds every section.
  - Rendering, flow, light and sync then work at every height.
- **When.** To build and test once stream H's extended worlds exist.

## Mu-1. Jukebox comparator signal for discs added after 1.12.2

1.12.2's `BlockJukebox.getComparatorInputOverride` counts the signal from the disc's item id
(`id + 1 - id(record_13)`), right for vanilla's twelve discs only. Nether Update patches the method for its own
Pigstep (its patch P2); Otherside (signal 14) needs the same, and so will 1.19's and later discs. This mod has no
patch for it. Wanted in Core, one patch for the family:

```java
// com.exiledradio.backportcore.item.ComparatorDisc (an interface for ItemRecord subclasses)
int comparatorSignal();
```

and Core's jukebox patch answering with it when the record implements it. Meanwhile `music/ItemMusicDisc` has
`comparatorSignal()` under that name (14), nobody reads it, and a jukebox playing Otherside gives a comparator
whatever 1.12.2's id arithmetic comes to.

## Mu-2. Game music additions as a `Contribution` list

1.18 added four tracks to `music.game` (and so to `music.creative`); 1.19, 1.20 and 1.21 add more. Menu music has
`addMenuMusic`; the game music has no such list, so this mod adds its tracks with a built-in resource pack of its
own (`music/client/GameMusicPack`: one `minecraft:sounds.json` that adds to `music.game`). Wanted:

```java
v.addGameMusic("modid:music/game/comforting_memories", 1, 0.4F);   // sound name, weight, volume
```

resolved like the menu list (a later version can remove a track). Our pack then goes away.

## 12. Where a world's generator starts the family's structures, and terrain adaptation

The 1.18 generator (stream G) knows 1.18.2's placement of every overworld structure set, but shipwrecks, ocean
ruins, buried treasure (Update Aquatic), ruined portals (Nether Update) and pillager outposts (Village & Pillage) are
placed by their own mods, by their versions' rules. Wanted: a way for a chunk generator to rule where a kind starts
in its worlds, which those mods follow without knowing about this mod, and `/locate` with them.

```java
// com.exiledradio.backportcore.structure
public interface StructureSource { @Nullable StartRule startRule(World world, String kind); }   // on the IChunkGenerator
public interface StartRule { ScatteredPlacement grid(); @Nullable String startAt(int chunkX, int chunkZ); }
StartRules.of(world, kind);                          // the world generator's rule, or null
scatteredStructure.ruledStart(world, chunkX, chunkZ); // today's id of the start there: picks the variant
```

1.18 also shapes its terrain around villages, outposts and strongholds (`adapt_noise`); a structure another mod lays
out must be known before the chunk is filled:

```java
TerrainAdaptation.register(kind, TerrainAdaptation.Kind.BEARD_THIN, planner);   // planner lays starts out ahead
TerrainAdaptation.AdaptingPiece                                                 // rigid, ground level delta, junctions
```

**Status (2026-11-12):** built on Core branch `cc-structures` (bb6b2de, worktree `Workspaces/BackportCore-ccstructures`,
cut from `main` d152653), for 1.2.0; `deps/BackportCore-1.2.0-dev.jar` here is that build. `ScatteredStructure`
follows a rule in its start check, `locatableAt`, `nearest` and `placement()`, so Core's `/locate` agrees. The
owners' changes, each on a `cc-structures` branch: Update Aquatic (8cddaaa: shipwreck kind, ruin warmth),
Nether Update (1cfda62: ruined portal type), Village & Pillage (7281f9e, cut from `languages`, its newest line:
outposts ask `StartRules` and register with `TerrainAdaptation`). Each now requires Core 1.2.0.
