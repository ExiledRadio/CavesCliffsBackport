# Stream H: extended world height (y -64 to 319)

Package `height` (the coremod in `height.asm`; the design is `docs/EXTENDED_HEIGHT.md`). Config `height`: `enabled`,
`defaultForNewWorlds`. No blocks, items, entities or packets of its own. Also changed outside the package: one line
in the mod class's pre-init, two lines in `SelfTestRunner.TESTS`, the coremod's manifest entries and
`fml.coreMods.load` in `build.gradle` (and a `-Pclienttest` property for the client test), one category name in
`tools/common/generate.py`, and the PLAN's merge-conflict status lines.

Bytecode patching was approved for this part only (EXTENDED_HEIGHT, top). It is our own ASM: one
`IFMLLoadingPlugin` (`HeightCorePlugin`), one `IClassTransformer` (`HeightTransformer`), one small patch class per
target class. No mixins, no MixinBooter.

## For the integrator

1.  **Backport Core's `WorldHeights` does not know this mod yet** (CORE_REQUESTS 11, unchanged). Until it does, Core
    answers 0-255 for a Caves & Cliffs world. Two visible effects: the deepslate layer pass (`DeepslateLayer`, which
    asks Core) runs in Caves & Cliffs worlds over y 0-255 as in any world (harmless: it never touches y < 0), and the
    Caves & Cliffs advancement (`blocks/WorldFall`) measures the fall from 255 to 0. `Heights.minY/maxY(World)` is
    the answer Core's branch should give (plain static methods, no Minecraft state beyond the world).
2.  **The 1.18 terrain stream** writes into a chunk's sections directly: slot `HeightLayout.slot(y)`, base
    `HeightLayout.slotBase(slot)`, 24 sections when `Heights.isExtended(world)`. `WorldTypeCavesAndCliffs.Generator`
    is the place it replaces (today: vanilla 1.12's overworld, then `fillBelowZero`).
3.  **Not mine, seen while testing:** the music stream's client setup logs `UnableToFindFieldException:
    field_110449_ao` in the dev client (`MusicClient.installGameMusic`, `Minecraft.defaultResourcePacks`): the dev
    name it falls back to does not match. It was there before this stream.
4.  `PLAN.md`'s "Build and test" still says "No mixins, no coremod, no ASM"; that is now true of every stream but
    this one.
5.  **Modpacks (RLCraft Revamped / Dregora):** Alfheim 1.6 stays (branch `cc-alfheim`, "Alfheim" below); a pack must set StellarCore's
    `B:WorldBlockPosJudgement=false` (`config/stellar_core.cfg`). That option `@Overwrite`s
    `World.isOutsideBuildHeight` with vanilla's 0-255 check after our patch ran; the world then stays 0-255 and the
    log says which option to change (see "Overrides" below). VintageFix and Nothirium need nothing: both are handled.

## Changelog section

*   **The Caves & Cliffs world type**: a new world from y -64 to 319, as in 1.18. Choose it on the world creation
    screen ("World Type: Caves & Cliffs") or with `level-type=cavesandcliffs` in `server.properties`; the config switch
    `height.defaultForNewWorlds` makes it the default. The terrain is still 1.12's until the 1.18 terrain arrives: the
    new depth below y 0 is solid stone down to bedrock at y -64, and mobs spawn there as anywhere underground.
*   Everything works at every height: building (up to 319 and down to -64), light, saving, multiplayer, rendering,
    falling into the void (from y -128), Nether portals (lit, found and built anywhere from -64 to 319).
*   **Existing worlds stay as they are**: y 0 to 255, for good. So do the Nether and the End.
*   **Removing the mod** from a Caves & Cliffs world keeps everything from y 0 to 255; what was below 0 and above 255
    is gone, and the bottom of the world is then open at y 0.
*   **With Alfheim 1.6**: its faster lighting works at every height. Its lighting then covers x and z within
    16,777,216 blocks of 0 (the world border is at 29,999,984; Alfheim alone covers 33,554,432).
*   **Not with**: Phosphor, Cubic Chunks, Caves Not Cliffs, Farmer's Future Delight, or an Alfheim other than 1.6.
    With any of them installed, the world type is not offered (and the log says why); everything else in the mod
    works.
*   **Works with OptiFine** (HD U G5), shaders and their shadows included. In a Caves & Cliffs world OptiFine's
    single-player "chunk visibility" saving (skipping empty heights above the terrain) is off.

## How it works

**Which worlds.** A world's height is decided once, at the end of `World`'s constructor, before it has a chunk
(`HeightHooks.worldConstructed`). Extended means: extended height is on this session, the world is the overworld
(dimension 0), and its world type is `cavesandcliffs`. The server also records the height in the save
(`data/cavesandcliffsbackport_height.dat`, `MinY`/`MaxY`) the first time it loads a world, and from then on takes the
recorded one: a world never changes height, and an old world (recorded, or first seen, as 0-256) never becomes
extended. The client decides from the world type in the join and respawn packets. `World` carries the range in two
int fields the coremod adds (`HeightWorld`), both 0 in a world nobody marks, so a vanilla world needs no constructor
change.

**The section layout** (`HeightLayout`): slots 0-15 hold y 0-255 as in vanilla, 16-19 hold y 256-319 (where `y >> 4`
lands), 20-23 hold y -64..-1. One mapping, `slot(y)`, serves every world: it answers 0-15 for y 0-255 and a slot of
16 or more for anything else, and every patched lookup compares the slot with the chunk's array length (16 or 24),
so a vanilla chunk treats every other height as outside, exactly as before. Out of -64..319 it answers a huge slot.

**When it is on.** All of these, checked in pre-init (`HeightContent`): the config switch; the coremod ran (it reads
the switch from the config file itself, and checks for the conflicting mods' coremod classes, before Forge's config
exists); no conflicting mod id (`phosphor-lighting`, `cubicchunks`, `cavesnotcliffs`, `cavebiomesapi`,
`farmers_future_delight`); Alfheim, if installed, exactly 1.6 (the coremod reads `ProjectConstants.VERSION` from its
class file, pre-init the mod's version) with its three classes reported patched; and every patch site found. To know the last before any world exists, pre-init loads every
patched class (which is when the transformer patches it) and reads the drift report. Off, the world type is not
registered and no world is extended; every patched site then computes what vanilla's did.

**The patches.** Each site is an exact instruction pattern with an exact match count, by MCP name (dev) or SRG name
(an instance). A class with any site not found is left exactly as it was, the site is logged, and extended height is
off for the session. No patch replaces a method; most change one constant or one shift, and none adds a jump except
the two head guards below (each with its own frame). Sorting index 1500, after Fluidlogged API's 1401, so its
patterns see vanilla's code. 74 sites on a dedicated server (RLCraft's and Dregora's too), 97 on a client; 98 in
RLCraft's client (RenderLib, Entity Culling); 100 in the Dregora Celeritas pack and 104 in the Fast one (Nothirium and
VintageFix), counted before `getHeight.return` was added; 101 in the Dregora Shaders pack (OptiFine) and 106 with
OptiFine and Nothirium. `-Dcavesandcliffsbackport.height.dump=<dir>` writes every target class there as
the transformer receives it, to read what another mod left when a site is not found.

| Class | Sites |
| --- | --- |
| `World` | `HeightWorld` (fields, getters); end of the constructor; `isOutsideBuildHeight`, `isAreaLoaded`, `getLight` (both), `getLightFor`, `getLightFromNeighborsFor` (client), `canBlockFreezeBody`, `canSnowAtBody`, `getTopSolidOrLiquidBlock`: 0/256/255 become the world's bottom, top and top - 1; `getHeight(x, z)` never answers below 0 (1.12's "nothing here"; mods draw `nextInt(height + 32)` from it, which threw in a 1.18 pit open down to y -44) |
| `WorldProvider` | `getHeight`, `getActualHeight`: 256 becomes the world's top (320) |
| `Chunk` | constructor: 24 sections and 24 entity lists; every `storageArrays[y >> 4]` (getBlockState, setBlockState, light get/set/subtracted, generateSkylightMap, relightBlock, isEmptyBetween); `getBlockState`'s `y >= 0` guard; `getLastExtendedBlockStorage` walks by height; height loops stop at the bottom (generateHeightMap, generateSkylightMap, relightBlock, checkLight, getPrecipitationHeight); `relightBlock`'s `& 255`; `isEmptyBetween`'s clamps; the relight round robin covers 24 sections; client `read` builds each section at its slot's height; entity lists per section from the bottom (`addEntity`, `removeEntityAtIndex`, both area queries), `chunkCoordY` stays the section y |
| `ChunkCache` | `getBlockState`, `isSideSolid`, client `getLightForExt`, `getLightFor` |
| `AnvilChunkLoader` | write: vanilla's loop gets slots 0-15, then our list; read: a 24-slot array, then our list |
| `SPacketChunkData` | "full chunk" (65535) becomes all 24 sections; a block entity's section bit by slot |
| `PlayerChunkMapEntry` | `blockChanged` head guard: y outside 0-255 goes to a side list; `update` sends those as single-block packets |
| `Entity` | the void at the world's bottom - 64 |
| `EntityFallingBlock` | the 1-and-256 limits for dropping a falling block |
| `BlockPortal`, `BlockPortal$Size`, `Teleporter` | Nether portals as in 1.18 (`PortalPatches`): lighting a frame from above its bottom row walks down to the world's bottom (was `y > 0`: a frame below 0 lit only from its bottom row); zombie pigmen come out of a portal below 0; an arrival's search for a portal (`placeInExistingPortal`) and the column search for where to build one (`makePortal`, both passes, its floor walk too) run down to the world's bottom instead of 0. Their tops are `getActualHeight()` (already 320); the fallback height stays clamped to 70 and up, as 1.18's `max(minY + 1, 70)` |
| `NetHandlerPlayServer` | the build limit (placing, breaking) is the world's top |
| `WorldEntitySpawner` | spawn attempts draw y from the world's bottom (one random draw either way) |
| `WorldInfo` | a new save's world type (the `defaultForNewWorlds` switch) |
| `NetHandlerPlayClient`, `WorldClient` (client) | chunk receive and unload re-render the whole height |
| `ViewFrustum` (client) | 24 render chunks high, index from the bottom (`getRenderChunk`: vanilla's `intFloorDiv(y, 16)` or OptiFine's `y >> 4`) |
| `RenderGlobal` (client) | neighbour walk bounds, the start chunk for a camera above or below the world, render-chunk entity lists; with OptiFine, its `maxChunkY` local starts at the world's top instead of 256 (see below) |
| `EntityRenderer` (client) | the fog darkens near the world's bottom (`(y - minY) * voidFogFactor`, as 1.18) |
| Celeritas 2.5.0 (client) | `VintageRenderSectionManager.create`: the world's section range; `isSectionVisuallyEmpty`, `WorldSlice.prepare`, `ClonedChunkSection.getChunkSection` (head redirect): section y to our slot |
| Nothirium 0.4.9 (client) | `WorldUtil.getSection` (head redirect): our slots; `RenderChunk.markDirty`, `Direction.UP/DOWN.isFaceCulled`: the world's section range |
| VintageFix 0.7.0 | its `chunk_access.ChunkMixin` overwrite of `Chunk.getBlockState`: the same three sites as vanilla's |
| RenderLib 1.2.8 (client, RLCraft) | replaces `RenderGlobal.renderEntities`' per-render-chunk loop with its own over the world's entity list: the `renderEntities.list` site is then not needed (and counted found) |
| OptiFine HD U G5 (client) | `ChunkVisibility.getMaxChunkY` (head guard): the world's top in an extended world; `RenderChunkUtils.getCountBlocks` (Render Regions' buffer sizes): `sections[y >> 4]` by our slot (it threw below 0) |
| Entity Culling 6.3.1 (client, RLCraft) | `CachedBlockAccess.getChunkSection`: `getBlockStorageArray()[sectionY]` becomes our slot (it threw for a section below 0); 6.5.0 (Dregora) reads through its own 0-15 check and needs nothing |

**Saving** (`HeightHooks`): vanilla's `Sections` gets exactly slots 0-15, written by vanilla's own code; slots 16-23
go to `cavesandcliffsbackport:Sections` in vanilla's per-section format with a signed `Y` (-4..-1, 16..19). Reading
fills a 24-slot array from `Sections`, then ours.

**Sending.** A full chunk packet carries all 24 sections (mask `0xFFFFFF`; the format is vanilla's). A block change
outside 0-255 cannot go into vanilla's short (`x << 12 | z << 8 | y`), so it waits in a side list until the entry's
next update and goes as one `SPacketBlockChange` (plus the block entity's update); `SPacketMultiBlockChange` is
unchanged. A client with extended height off (an Alfheim other than 1.6 installed on it, say) in an extended server world reads the 16
sections it has room for and ignores the rest: no crash, but it does not see below 0.

**The world type** (`WorldTypeCavesAndCliffs`): biomes and the generator are exactly the Default type's (same seed,
same world); after each chunk is generated, its four sections below 0 are filled with stone and bedrock at -64, with
sky light 0 (right under solid stone, so no relight). Vanilla population then reaches a little below 0 here, as it
would in any world that holds those heights (ore veins, gravel and dirt blobs, a dungeon floor at -1).

**Other renderers.** Celeritas and Nothirium replace vanilla's chunk rendering with their own; each reads sections
and bounds its section range itself, and those few places are patched in their own classes (only present with that
mod). Client-only types appear only in `height/asm/ClientPatches` as strings and in the client test.

**OptiFine** (HD U G5; plan and site list in `docs/streams/optifine-alfheim.md`, section 1). OptiFine ships its
own `RenderGlobal` and `ViewFrustum`, and four of our client sites differ there. Each such site is a pair of patterns
on the same patch, vanilla's and OptiFine's: exactly one must match (with its exact count) and the other not at all,
and the drift report names the variant (`setupTerrain.startHeight (OptiFine)`). Nothing looks for OptiFine by class
name; the bytecode decides. The OptiFine variants:

| Site | OptiFine's code | Change |
| --- | --- | --- |
| `ViewFrustum.getRenderChunk.y` | `y >> 4` | `y - minY` first (a multiple of 16, so still a floor) |
| `RenderGlobal.getRenderChunkOffset` | `(pos, chunk, facing, fog, maxChunkY)`: reads the frustum's cached neighbour, drops one above `maxChunkY` | nothing; checked to hold no 0/256 compare (bounded by the frustum and by `maxChunkY`) |
| `RenderGlobal.setupTerrain.maxChunkY` | a local, `256` (the top for the neighbour walk, the start height and the shadow pass) | the world's top |
| `RenderGlobal.setupTerrain.startHeight` | `y > 0 ? Math.min(maxChunkY, 248) : 8` | `y > minY`, `topSectionMiddle`, `bottomSectionMiddle` |
| `RenderGlobal.setupTerrain.above` | 1 match (Forge's MC-73139 frustum expansion is gone) | as vanilla's |
| `ChunkVisibility.getMaxChunkY` | lowers `maxChunkY` to the highest non-empty section slot around the player (single player only); slots 20-23 are y -64..-1 | head guard: the world's top in an extended world, so its "chunk visibility" culling is off there |
| `RenderChunkUtils.getCountBlocks` | `getBlockStorageArray()[y >> 4]` (Render Regions) | `HeightHooks.sectionAt(sections, sectionY)`: our slot, null outside |

The shadow pass needs nothing of its own: it walks the frustum or looks chunks up through `getRenderChunk`, and
filters by `maxChunkY`. Code inserted into OptiFine's classes reads a parameter only at the method's first instruction
(ProGuard's frames drop dead locals later on); `PatchCheck`'s frame check reads the class's own frames to hold every
patch to that.

**Overrides.** A mixin `@Overwrite` applied after our transformer replaces a patched method silently, and the drift
report cannot see it. Two guards: when the first extended world is constructed, `World.isOutsideBuildHeight` and
`isValid` are probed at y -65, -64, 319 and 320, and if they answer vanilla's range the world stays 0-255 with a log
line naming the cause (StellarCore's `WorldBlockPosJudgement` is the one found); and the Caves & Cliffs generator reads
y -64 back from its first chunk and logs an error if a `Chunk.getBlockState` override hides it. VintageFix's
`chunk_access.ChunkMixin` is such an overwrite (of `Chunk.getBlockState`); since Mixin reads mixin classes through the
transformer chain, our transformer gives that overwrite the same three sites as vanilla's method.

**Alfheim 1.6** (`height/asm/AlfheimPatches`, `height/AlfheimHooks`, `height/AlfheimKey`; plan and the confirmed
site list: `docs/streams/optifine-alfheim.md`, section 2). Alfheim replaces vanilla's lighting engine with mixins and
`@Overwrite`s two of the methods we patch (`Chunk.relightBlock`, `Chunk.getLightFor`). Its mixin classes and its
`LightingEngine` are patched as Mixin reads them, like VintageFix's (reported as `AlfheimLightingEngine.*`,
`AlfheimChunkMixin.*`, `AlfheimChunkCacheMixin.*`; 26 recorded sites, the last one a check: on a server Forge strips
the client-only `ChunkCache` overwrite, on a client its body must read no height):
*   **The position key** (`AlfheimKey`): Alfheim's is z 26 bits, x 26, y 8 (raw), light 4, with "outside 0-255" a
    carry into the light bits. With extended height on it becomes z 25, x 25, y 9 (as y + 64), a spare bit, light 4;
    encode and decode go to `AlfheimHooks` (head redirects), the neighbor shifts in `<clinit>` and the two inlined
    chunk masks follow, and the carry test becomes a compare with the world's height. The layout is fixed once, as a
    constant, the first time the engine is used; `HeightContent` refuses extended height if it was already fixed
    narrow. With extended height off every site computes Alfheim's own values, and with the coremod off (config,
    a conflict, an Alfheim other than 1.6) Alfheim is not touched.
*   **A queued position outside the world's height is skipped** in the queue's first pass (as an unloaded chunk is):
    Alfheim's `World.checkLightFor` enqueues any position, and its own key garbles such a y into one inside.
*   **Sections by slot** in `LightingEngine.fetchNeighborDataFromCursor`, `ChunkMixin.alfheim$getCachedLightFor`
    (every light read), `alfheim$relightSkylightColumn` and the `generateSkylightMap` redirect; section bases by
    `slotBase` in `alfheim$initChunkLighting`.
*   **Boundary flags** stay a `short` per boundary and the save format stays Alfheim's: in an extended world a bit
    covers two sections (12 bits for 24), so a flagged pair rechecks 32 blocks of height instead of 16.
*   **Our lost sites back** in its `relightBlock` overwrite: the `& 255` height mask and the `> 0` bottom.

## Tested

*   **Unit test** `tools/height/run.sh HeightLayoutTest`: every y from -64 to 319 to its slot and back, vanilla and
    extended chunks, out-of-range heights, the by-height walk. 3119/3119.
*   **Pattern drift, offline** `tools/height/run.sh PatchCheck`: every patch against Forge 2847's classes with dev
    names, and again renamed to SRG names (an instance's), plus Celeritas 2.5.0's classes; ASM's verifier over every
    patched method. All sites found, no verify error.
*   **`-Pselftest=height`** (`sh tools/height/selftest.sh`: level-type `cavesandcliffs`, fresh world, port 25855):
    128 checks, 0 failures. The drift report (every site found); world range, `getHeight()` 320, build limit 320,
    spawn heights -64..; a generated chunk (24 sections, bedrock at -64, stone at -32, solid at -1); a block set and
    read at every y from -64 to 319, none at -65 or 320; height map and top filled section; sky light 15 at y -64 up
    an open column, 0 in the deep stone; block light at a light at y 319 and at y -40; the NBT round trip (16 vanilla
    sections with Y 0-15, 8 of ours with signed Y, every block and light value back); `SPacketChunkData` (mask
    `0xFFFFFF`, size = sections + biomes, through the wire, a one-section update); a watching player (a fake player on
    a capturing connection) is sent chunks with the sections below 0, and 20 changes below 0 in one tick arrive as 20
    single-block packets (and 20 above 255 too), with no multi-block or chunk packet; an item at y -127.5 lives, at
    -128.5 it is in the void; entities at -64, -30, 64 and 300 sit in their section's list and area queries find them;
    the Nether is vanilla's (0-255, 16 sections, void at -64, packet of 16 sections at most).
*   **The whole suite** `./gradlew runServer -Pselftest=all` in a Default world with the coremod on: 4535 checks, 0
    failures (the height test checks there that the overworld is vanilla's). `tools/check_assets.py`: 0 errors.
*   **Removal** `python tools/height/removal.py`: the extended test world, our list stripped from every chunk
    (`strip_sections.py`: 993 chunks, 3983 sections), loads on a plain Forge 2860 server without the mod (port 25857):
    no exception, markers by the spawn at y 0, 200 and 255 still there (so the chunks were read, not regenerated).
*   **Client** `./gradlew runClient -Pclienttest=height`: a single-player Caves & Cliffs world through the
    integrated server; 18 checks, 0 failures: the client world is extended, the frustum is 24 render chunks high from
    y -64 to 304, the client's chunks hold bedrock at -64 and the test's blocks at -62, -59 and 319, the render chunk
    at y -64 compiles with blocks in it (camera in a pocket at y -61), and so does the one at y 304 with the camera
    above the world (y 322).
*   **Modpacks, through Prism** (copies of the Dregora Cleanroom instances on G:, `G:/VBPTest/cc_height_inst`, the
    test jar built with `-Pclienttest`, `launch.sh`):
    *   **As shipped (Alfheim installed)**, both the Celeritas instance (Cleanroom 0.6.13, Celeritas 2.5.0, Demonica)
        and the Fast one (Cleanroom 0.5.17, Nothirium, VintageFix; plus Backport Core 1.2.0 and Fluidlogged API 3.3.3
        added, which this mod needs): the game reaches the main menu, the coremod patches nothing, and the log says
        "Extended height is OFF ... Alfheim is installed".
    *   **Alfheim removed**: first run, extended height turned itself off ("World.isOutsideBuildHeight(y -64) answers
        true" - StellarCore); with StellarCore's `WorldBlockPosJudgement=false`, the full client test in a new
        Caves & Cliffs world: **Celeritas 22/0** (blocks at -64, -62, -59 and 319 on the client; Celeritas's sections
        span y -4..19 and the ones at y -64 and 304 are built with something to draw), **Fast 15/0** (the same blocks;
        Nothirium's render chunks at y -64 and 304 compiled and not empty; Nothirium has no vanilla frustum to check).
        Found and fixed on the way: Celeritas's `WorldSlice.prepare` crashed on section y -4, and
        `isSectionVisuallyEmpty` would have hidden every section below 0; VintageFix's `Chunk.getBlockState` overwrite
        hid every block below 0.
*   **Nether portals** (branch `cc-portal-height`): in the height self-test, before the fix four checks failed (a
    frame at y -60 lit from its middle row got no portal, no pigman, the trip back from the Nether landed at y 59,
    the arrival's portal was not built in a room at y -50); after it all pass: the portals at y -60 and 300 light, a
    pig goes through the one at -60 to the Nether (a portal is made there) and comes back to (x, -60, z), and the
    arrival's portal is built in the room at y -50. 139/0 (83 sites on the server). A Default world's portal lit
    the same way at y 70 lights (full suite 4451/0). PatchCheck (Forge 2847's patched classes, dev and SRG names):
    all nine new sites found, the verifier and the frame check clean.
*   **OptiFine HD U G5** (branch `cc-optifine`; `docs/streams/packcompat.md`, "OptiFine"):
    *   **Offline drift** (`tools/height/run.sh PatchCheck mcp|srg`, Forge 2847 plus Celeritas, Nothirium and
        VintageFix): every site found, vanilla's variants; `PatchCheck dump G:\VBPTest\cc-packs\study\optifine\dump`
        (the classes the Shaders copy's transformer received, OptiFine's own included): all seven OptiFine variants
        found. ASM's verifier and the new frame check over every patched class and its input: clean (the frame check
        fails, as it should, when the head guard's frame is left out).
    *   **Dregora Shaders copy** (`G:\VBPTest\cc-packs\prism\ccheight-Shaders`: Cleanroom 0.5.17, OptiFine G5,
        Complementary v4.6; Alfheim removed): client test **42/0**, 101 sites. The report names the OptiFine variants;
        `ChunkVisibility.getMaxChunkY` answers 320; the render chunks at y -64 and 304 are compiled and in
        `renderInfos` (which spans y -64..304) with Complementary on, in the shadow pass, with Render Regions on and
        with shaders off. Screenshots of the world bottom, a cave at y -50 and the view from y 280 look right.
    *   **Dregora Shaders + Nothirium copy** (`ccheight-ShadersNothirium`): **32/0**, 106 sites; Nothirium's render
        chunks at y -64 and 304 compiled (OptiFine's `setupTerrain` does not run there). One run in three crashed out
        of direct memory in Nothirium's chunk compiler (packcompat.md).
    *   **Default world** in the Shaders copy: 13/0, vanilla's frustum, no slowdown against `height.enabled=false`.
    *   `-Pselftest=all` from a fresh world 4447/0; `sh tools/height/selftest.sh` 130/0 (74 sites).
*   **Alfheim 1.6** (branch `cc-alfheim`, 2026-11-14):
    *   **Unit test** `tools/height/run.sh AlfheimKeyTest`: 1,881,621/1,881,621. The wide key's round trip for every y
        -64..319 at x, z = 0, ±1, ±15/16/17, ±(2^24 - 1), -2^24 and others; the light bits change nothing; each
        neighbor shift lands on the neighbor and the chunk mask tells same chunk from another; `outside` exactly at
        -65 and 320 (extended), -1 and 256 (a vanilla world); the narrow layout equals Alfheim 1.6's formulas and
        constants literally; every section in exactly one flag bit, every block in exactly one bit's range.
    *   **Offline drift**: `PatchCheck` with Alfheim-1.6.jar as the fifth jar, SRG and MCP names: the 26 sites found
        on each side, ASM's verifier clean; the patched `LightingEngine` also loads under `-Xverify:all`.
    *   **Dedicated server** (`G:\VBPTest\cc_alfheim_srv`: Forge 2860, MixinBooter 11.18, Red Core 0.7.1, Alfheim
        1.6, Fluidlogged API 3.3.3, Backport Core 1.2.0 (playtest), the test jar; port 25881; stopped by its PID):
        `height,alfheimlight1` on a fresh Caves & Cliffs world 159/0 (100 sites); `alfheimlight2` on the same world
        after the restart 21/0. `selftest/AlfheimLightSelfTest`: a torch at y -30 lights 13 across a chunk border and 8
        six blocks into the next chunk; a shaft from the surface to -60 has sky light 15 at its bottom and 12 three
        blocks into a corridor, 0 when capped at y -1, 15 again when opened; a roof at y 310 leaves 12 under its
        middle; no boundary flag uses a bit above 11; after the restart every value read back the same, the open
        shaft capped goes dark and the capped one opened gets 15 and 12 again.
    *   **Default worlds unchanged**: `alfheimdump` (a hash of sky and block light at every block of 9x9 chunks around
        0, 0) on a fresh Default world, seed `alfheim`, with extended height on and with the config switch off
        (Alfheim then untouched): the same file.
    *   **Pregen** (that server, seed 1718, a fresh 48x48-chunk Chunk Pregenerator square at 2000, 2000, alternating,
        twice each; another session's dev server was running a suite part of the time): Caves & Cliffs world with
        Alfheim 8 and 12 s (288, 192 chunks/s), without Alfheim 18 and 18 s (128); Default world with Alfheim, extended
        height on 20 and 21 s, off 21 and 21 s. Alfheim stays faster than vanilla lighting with our patches in, and the
        patches cost nothing measurable in a Default world.
    *   **Dregora Cleanroom copies, Alfheim kept** (`G:\VBPTest\cc_height_inst`, StellarCore's option off, the
        `-Pclienttest` jar, Core 1.2.0 playtest; the client test now also reads block light at -59 and -60 on the
        client): **Fast 17/0** (131 sites), **Celeritas 24/0** (127 sites) with Demonica moved aside: with the
        current Core, Demonica 0.6.0's Iris mixin crashes the Celeritas instance at start-up whatever this mod does
        (see "Not tested"). Both copies were put back as they were.
*   **Speed** (EXTENDED_HEIGHT section 4, `python tools/height/bench.py`, E-cores): an extended world generates in
    1.11x vanilla's time per chunk (13.30 vs 11.96 ms), 1.15x with saving; a Default world with the coremod on, 1.04x.

## Not tested

*   OptiFine builds other than HD U G5: a pattern that does not match turns extended height off, as before.
*   OptiFine with the camera above y 320 or below -64: OptiFine's start chunk lacks Forge's MC-73139 fix, so looking
    level from outside the world can start with nothing visible (vanilla OptiFine does the same above 256).
*   Looking at the screen in the packs: the checks read the renderers' state (sections built, not empty), not pixels.
*   Overrides other than the ones found in Dregora: the probes cover `World.isOutsideBuildHeight`/`isValid` and
    `Chunk.getBlockState`; another mod overwriting another patched method would still go unnoticed until something
    below 0 misbehaves.
*   A real second client over the network (the packet path is tested with a capturing connection, the client path
    through the integrated server).
*   Mods that index sections themselves (EXTENDED_HEIGHT 1.3): none checked yet.
*   Fluidlogged blocks outside 0-255 (EXTENDED_HEIGHT 3: its storage packs y in 8 bits) are still open with Core.
*   **Alfheim with Demonica**: the Dregora Celeritas instance does not start with the current Backport Core 1.2.0
    (every build with `FeatureSource`, which this mod needs): Core's P24 (`SunriseTransformer`, `RenderGlobal.renderSky`)
    leaves Demonica 0.6.0's `RenderGlobalIrisMixin.demonica$beginSunset` without its target ("Critical injection
    failure ... (0/1) succeeded"), and `RenderGlobal` fails to load. A Core issue, not Alfheim's or this branch's;
    the Celeritas client test ran with Demonica moved aside.
*   Alfheim's boundary flags across an unloaded neighbor: Alfheim sets them only for empty (all-air) sections, which
    below 0 are rare; the bit mapping is unit-tested and no flag bit above 11 was seen, but a relight that depends on a
    flag set while the neighbor was unloaded was not staged.
*   Alfheim in the Shaders copies (with section 1, OptiFine).
