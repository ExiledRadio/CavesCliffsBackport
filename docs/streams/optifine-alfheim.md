# Plan: extended height with OptiFine and Alfheim

Status: built (2026-11-14). Section 1 (OptiFine) on branch `cc-optifine`, section 2 (Alfheim) on branch
`cc-alfheim`; results in `docs/streams/height.md` "Tested" and `docs/streams/packcompat.md`. Scope: the `cavesandcliffs` world type (y -64..319) working
with OptiFine 1.12.2 HD U G5 (including shaders and the shadow pass) and with Alfheim 1.6. Phosphor followed later
with the same design (branch `cc-phosphor`; `height.md`, "Phosphor").

Same rules as stream H (`docs/streams/height.md`): our own ASM in `height/asm`, no mixins, no MixinBooter; every site
an exact pattern with an exact match count; every patched site calls `Heights`/`HeightLayout`/`HeightHooks`; any miss
leaves the class unpatched and turns extended height off for the session.

Read from: OptiFine's classes as our transformer receives them (`G:\VBPTest\cc-packs\study\optifine\dump`, SRG names);
OptiFine's own patches applied offline to vanilla 1.12.2 (`study\optifine\patched`, notch names, made with
`study\tools\gdiff.py`: OptiFine ships its vanilla rewrites as GDIFF patches in `patch/`); Alfheim-1.6.jar from the
Dregora Celeritas instance. Disassemblies of every method named below are in `study\optifine\javap` and
`study\alfheim\javap`.

The two are independent. OptiFine is the one the C&C page's shader screenshots need; the Shaders instance also has
Alfheim, so until Alfheim works the screenshot copy drops Alfheim (as the Celeritas and Fast copies did).

## 1. OptiFine

### What failed

The six missed sites in the Shaders run (`G:\VBPTest\cc-packs\prism\shaders-run*.txt`):

| Site | Why |
| --- | --- |
| `RenderGlobal.getRenderChunkOffset(BlockPos, RenderChunk, EnumFacing)` exists | OptiFine replaces it with `getRenderChunkOffset(BlockPos, RenderChunk, EnumFacing, boolean, int)` |
| `getRenderChunkOffset.min` / `.max` | same method; it has no 0 or 256 in it (below) |
| `setupTerrain.startHeight` | the start height is `y > 0 ? Math.min(maxChunkY, 248) : 8`, not `y > 0 ? 248 : 8` |
| `setupTerrain.above` | expects 2 `getY() > 0`, finds 1: Forge's MC-73139 frustum expansion is gone (OptiFine uses its own `RenderChunk.isBoundingBoxInFrustum(camera, frame)`) |
| `ViewFrustum.getRenderChunk.y` | `y >> 4` instead of `MathHelper.intFloorDiv(y, 16)` |

Everything else in our client patches matched OptiFine's classes: `setCountChunksXYZ`, `createRenderChunks`,
`updateChunkPositions`, `markBlocksForUpdate`, `renderEntities.list`, `EntityRenderer.updateFogColor`,
`NetHandlerPlayClient`, `WorldClient`, and every server-side class.

### How OptiFine bounds the render graph

* **Neighbors are cached.** `ViewFrustum.createRenderChunks` ends by linking every render chunk to its six neighbors
  through `getRenderChunk(pos.offset(facing, 16))`. `getRenderChunkOffset` reads that cache
  (`RenderChunk.getRenderChunkNeighbour`), so the bottom is bounded by `getRenderChunk` answering null below the
  frustum. Once `getRenderChunk` is patched, the bottom of a 24-high frustum is right with no other site.
* **The top is a local.** `setupTerrain` keeps `maxChunkY` in a local: `256`, or, while an integrated server runs,
  `ChunkVisibility.getMaxChunkY(world, entity, renderDistance)`. It is passed to `getRenderChunkOffset` (a neighbor
  above it is dropped), caps the start height, and filters the shadow pass's chunks. Left at 256, nothing above
  y 271 ever draws.
* **`ChunkVisibility.getMaxChunkY`** walks the server chunks around the player and returns the highest non-empty
  `getBlockStorageArray()` index (and entity list index, and tile entity `y >> 4`) times 16. In our layout slots
  20-23 are y -64..-1, always stone, so it always answers 368: correct (nothing culled) but the optimization never
  works; and entity list indexes count from the bottom (index 0 is y -64), so they are not slots either.
* **The shadow pass** (`ShadowUtils.makeShadowChunkIterator`): with unlimited shadow distance it iterates
  `ViewFrustum.renderChunks` (24 high, fine); otherwise `IteratorRenderChunks` walks chunk positions along the sun
  direction and looks each up with `getRenderChunk` (patched: null outside). Its results pass the same
  `y > maxChunkY` filter. No height constant of its own.

### Sites

Each OptiFine site is an alternative pattern on the same `ClassPatch`: the vanilla pattern or the OptiFine one,
exactly one of them found, the report naming which (`setupTerrain.startHeight (OptiFine)`). No OptiFine detection
by class name: the bytecode decides, as RenderLib's loop does in `renderEntities`.

| # | Class.method | Pattern | Change |
| --- | --- | --- | --- |
| O1 | `ViewFrustum.getRenderChunk` | `getY(); iconst_4; ishr` (OptiFine) | `getY() - minY` before the shift (`minY` is a multiple of 16, so `>>` stays a floor) |
| O2 | `RenderGlobal.getRenderChunkOffset(...ZI)` | method present, no `getY()` against 0/256 in it | nothing to patch; recorded as found (bounded by O1 and O3) |
| O3 | `RenderGlobal.setupTerrain` | `sipush 256; istore n` (the `maxChunkY` default, 1 match) | `Heights.maxY(world)` |
| O4 | `RenderGlobal.setupTerrain` | `getY(); ifle; iload n; sipush 248; Math.min; goto; bipush 8` | `ifle` compares with `minY`; 248 to `topSectionMiddle`; 8 to `bottomSectionMiddle` |
| O5 | `RenderGlobal.setupTerrain` | `setupTerrain.above`: 1 match with OptiFine (the O4 one), 2 without | count by variant |
| O6 | `net.optifine.render.ChunkVisibility.getMaxChunkY(World, Entity, int)` | head | `if (Heights.isExtended(world)) return Heights.maxY(world);` then the original body (own frame, as the Celeritas head redirects) |
| O7 | `net.optifine.util.RenderChunkUtils.getCountBlocks(RenderChunk)` | `getBlockStorageArray(); ...getY(); iconst_4; ishr; aaload` | `HeightHooks.sectionAt(chunk, sectionY)`. Today it **throws** (index -4..-1) for a render chunk below 0 when Render Regions sizes its VBOs |

O6 gives up OptiFine's "chunk visibility" culling in extended worlds (single-player only; it never runs on a
server). A real port (storage index to `HeightLayout.slotBase`, entity index to `minSection + i`) is a follow-up
only if a frame-time measurement says it matters.

Checked and needing nothing: `RenderChunk.getBoundingBoxParent` (32-block parents; -64 is a multiple of 32; looks
parents up through `getRenderChunk`), `RenderChunk.isChunkRegionEmpty` (`Chunk.isEmptyBetween`, patched),
`ChunkCacheOF` (arrays sized from its own corners; misses go to `ChunkCache`, patched), `ViewFrustum.updateVboRegion`
(VBO regions per 256x256 column, all heights), `ChunkUtils.getPrecipitationHeight` (treats a negative cached height
as unknown and asks `Chunk.getPrecipitationHeight`, patched: correct, a little slower in pits below 0),
`ExtendedBlockStorage` (OptiFine adds nothing height-related), `renderEntities`' `posY < 0 || posY >= 256` for the
camera entity (vanilla's own; only skips a loaded check).

Estimated: 7 sites, about 60 lines in `ClientPatches` plus two small `ClassPatch`es for OptiFine's own classes.

### Risks

* **OptiFine's start chunk lacks Forge's MC-73139 fix**: with the camera above 320 or below -64, the start row comes
  from `isBoundingBoxInFrustum` without the infinite y expansion, so looking level from above the world can start
  with nothing visible. Vanilla OptiFine does the same above 256. Accept; note it.
* **Shader packs**: Complementary v4.6 (the Shaders instances' pack) uses `eyeAltitude`/`cameraPosition.y` only for
  altitude fog, cloud height and texture noise. Below 0 its altitude fog clamps; nothing breaks, but caves at
  y -50 may look foggier than at y 10. Only seen in screenshots.
* **Nothirium plus OptiFine** (the ShadersNothirium instance): Nothirium replaces chunk rendering, so OptiFine's
  `setupTerrain` may not run there. Both sets of patches must be found; test both instances.
* **Unknown OptiFine options**: Render Regions, Smart Animations, Fast Render, dynamic lights, chunk updates
  "dynamic". O7 is the only option-gated path found; the test matrix covers the rest.
* **Other OptiFine builds**: patterns are for HD U G5 (the packs' build). Another build failing a pattern refuses
  extended height as today, with the site logged.

### Test

1. **Offline drift** (`tools/height/run.sh PatchCheck`): add the OptiFine dump's `RenderGlobal`, `ViewFrustum`
   and OptiFine's `ChunkVisibility`/`RenderChunkUtils` as SRG-named inputs (extend the
   `cavesandcliffsbackport.height.dump` list to those two classes and re-dump from the Shaders copy), every site
   found, ASM's verifier over every patched method. The vanilla inputs must still match their own variants.
2. **Client test in a Prism copy** on G: (`G:\VBPTest\cc-packs\prism\ccheight-Shaders`, from the Shaders instance;
   Alfheim removed, StellarCore `B:WorldBlockPosJudgement=false`, the `-Pclienttest` jar, `launch-prism.sh` stopped
   by its own PID). The existing 18 checks, plus: the drift report shows the OptiFine variants; `maxChunkY` is 320;
   render chunks at y -64 and 304 are in `renderInfos` and compiled, with shaders off, with Complementary on, and in
   the shadow pass (`renderInfosShadow`); Render Regions on and off (no exception from O7).
3. **Same in the ShadersNothirium copy.**
4. **Screenshots**: the client test saves F2-style screenshots (`ScreenShotHelper`) at a deep cave (y -50), a
   mountain top (y 280) and the world bottom; someone looks at them. This is the one check of pixels, and it is the
   deliverable's use.
5. **Default worlds unchanged**: the same copy in a Default world: no site reports a vanilla fallback, frame time
   within noise of today's.

Effort: **about one day** (half implementing and offline checks, half the two Prism copies and screenshots).

## 2. Alfheim

### What it replaces

Alfheim is a mixin mod (a Phosphor descendant; `mixins.alfheim.json`, `defaultRequire: 1`). Mixin reads mixin classes
through the transformer chain (as with VintageFix), so our transformer can patch Alfheim's mixin bodies and its own
`LightingEngine` before they are applied. Its overwrites replace vanilla methods our coremod patches, silently:

| Alfheim `@Overwrite` | Our sites lost with it |
| --- | --- |
| `Chunk.relightBlock` (`func_76615_h`) | `relightBlock.mask` (`& 255`), `relightBlock.bottom` (x2) |
| `Chunk.getLightFor` (`func_177413_a`) | its `storageArrays[y >> 4]` (now reads `alfheim$getCachedLightFor`, below) |
| `ChunkCache.getLightForExt` (`func_175629_a`) | `ChunkCache.getLightForExt.min/.max` |
| `World.getLight(pos, bool)`, `getLightFromNeighborsFor`, `checkLightFor` | none effective: Alfheim's bodies call `getLight(pos)`/`getLightFor`, which stay patched |
| `Chunk.checkLight()` (`func_150809_p`), `recheckGaps` (`func_150803_c`) | not ours |
| `ExtendedBlockStorage` light get/set, `RenderGlobal.notifyLightSet` | not height-related |

### Where Alfheim assumes 0-255

1. **The position key** (`LightingEngine`): z 26 bits (0-25), x 26 (26-51), y 8 (52-59), light 4 (60-63).
   `encodeWorldCoord` stores `(long) y << 52` with no offset or mask, so a negative y sign-extends over every bit.
   Neighbor keys are `key + neighborShifts[i]` (`<clinit>`), and "outside 0-255" is a carry into bit 60
   (`Y_CHECK`, tested in `fetchNeighborDataFromCursor`). `M_CHUNK` masks the key to its chunk (`nextItem`,
   `fetchNeighborDataFromCursor`). The constants are inlined, so each use is its own site.
2. **Section lookups by `y >> 4`**: `LightingEngine.fetchNeighborDataFromCursor`,
   `ChunkMixin.alfheim$getCachedLightFor` (every light read: **throws** below 0),
   `ChunkMixin.alfheim$relightSkylightColumn` (throws below 0), and slot index times 16 as a height in
   `alfheim$initChunkLighting` (the block light scan) and its `isAreaLoaded` box 0..255.
3. **Per-section boundary flags** (`alfheim$neighborLightChecks`, `short[32]`, saved in the chunk NBT as a list of
   shorts): one bit per section, `1 << sectionIndex`, `i2s`. 24 sections do not fit. Set in
   `alfheim$relightSkylightColumn`; read in `alfheim$scheduleRelightChecksForBoundary` (bit to y range); merged in
   `alfheim$mergeFlags`.
4. **Height loops** in its overwrites and helpers: `relightBlock` (`& 255`, `> 0` bottoms), `relightSkylightColumn`,
   `initSkylightForSection` (compares the heightmap with the section's base), `recheckGaps*` (heightmap values; take
   real heights already).

### Approach

* **A new key layout, only when extended height is on for the session**: z 25 bits, x 25 bits (offset 2^24), y 9 bits
  at bit 50 stored as `y + 64` (0-383; vanilla worlds use 64-319 of it), bit 59 spare, light 60-63 unchanged.
  Encode and decode become head redirects to `AlfheimHooks.encode/decode` (plain static Java, unit-tested); the
  `<clinit>` shifts (52, 26 to 50, 25) and the two `M_CHUNK` constants are replaced; the `Y_CHECK` test becomes
  `AlfheimHooks.outside(world, key)`, an explicit compare of the decoded y with the world's range (a 384 range has
  no carry bit). Cost: Alfheim's lighting then covers x and z within ±16,777,216, not ±33,554,432 (the world border
  is 29,999,984). The log says so once. With extended height off, Alfheim is not touched at all.
* **Section lookups** become `HeightHooks.sectionAt(chunk, sectionY)` (as for Celeritas); slot-times-16 heights
  become `HeightLayout.slotBase`; the `initChunkLighting` box becomes the world's range.
* **Boundary flags keep `short[32]` and the NBT format**: in an extended world, bit b covers the two sections
  `minSection + 2b` and `+ 2b + 1` (24 sections in 12 bits); in any other world, bit = section as today. The set site
  and the read site go through `AlfheimHooks.flagBit(world, sectionY)` / `flagRange(world, bit)`. A flagged boundary
  then rechecks 32 blocks of height instead of 16: more checks, never fewer. (Widening to `int[]` would change
  Alfheim's interface, field, NBT and four methods; not worth it.)
* **Re-apply the lost sites** in Alfheim's overwrite bodies, inside `ChunkMixin` and `ChunkCacheMixin`, with the
  same patterns as vanilla's methods (`relightBlock.mask/.bottom`, `getLightForExt.min/.max`), as was done for
  VintageFix's `ChunkMixin`.
* **Lift the refusal for Alfheim only**: `alfheim` out of `HeightContent`'s conflict list and `AlfheimPlugin` out of
  `HeightCorePlugin`'s, replaced by a version check (1.6 exactly; another version refuses, with the reason).

### Sites

| # | Class.method | Change |
| --- | --- | --- |
| A1 | `LightingEngine.encodeWorldCoord` | head redirect to `AlfheimHooks.encode` |
| A2 | `LightingEngine.decodeWorldCoord` | head redirect to `AlfheimHooks.decode` |
| A3 | `LightingEngine.<clinit>` | shifts 52/26 to 50/25 |
| A4 | `LightingEngine.processLightUpdatesForTypeInner` | nothing: it only uses the light bits (60) and `M_POS` (the low 60 bits), both unchanged; recorded as found |
| A5 | `LightingEngine.nextItem`, `fetchNeighborDataFromCursor` | `M_CHUNK` to the new chunk mask (2 sites) |
| A6 | `LightingEngine.fetchNeighborDataFromCursor` | `Y_CHECK` test to `AlfheimHooks.outside` |
| A7 | `LightingEngine.fetchNeighborDataFromCursor` | section by slot |
| A8 | `ChunkMixin.alfheim$getCachedLightFor` | section by slot |
| A9 | `ChunkMixin.alfheim$relightSkylightColumn` | sections by slot (2 reads); flag bits through `flagBit`; its 0 and 15 section bounds to the world's |
| A10 | `ChunkMixin.alfheim$scheduleRelightChecksForBoundary` | bit to height range through `flagRange` |
| A11 | `ChunkMixin.alfheim$initChunkLighting` | the area box, and section base by `slotBase` |
| A12 | `ChunkMixin.alfheim$initSkylightForSection` | section base from the section itself (`getYLocation`), not the index |
| A13 | `ChunkMixin.func_76615_h` (relightBlock overwrite) | our `relightBlock.mask` and `.bottom` |
| A14 | `ChunkCacheMixin.func_175629_a` | our `getLightForExt` range |

About 20 sites. The key constants (52, 26, 2^25, the masks) appear only in A1-A6. To confirm in the first step
(before writing hooks): whether `ChunkCacheMixin`'s body
reads a section; `BlockStateContainerMixin.alfheim$getLightFor` (neighbor brightness) only calls patched `World`
methods; `WorldChunkSlice` (x/z only, looks fine).

### Confirmed against Alfheim 1.6 (2026-11-14, before coding)

Read from `Alfheim-1.6.jar` (javap of every class in `study\alfheim`). What changed from the table above:

* **A14 needs nothing.** `ChunkCacheMixin.func_175629_a` is `getBlockState(pos)` then
  `ILightInfoProvider.alfheim$getLightFor(cache, type, pos)`; no section read, no height constant. The state's
  `alfheim$getLightFor` (`BlockStateContainerMixin` and `$StateImplementationMixin` forward to `BlockMixin`) reads
  `ILightLevelProvider.alfheim$getLight`, which is `ChunkCache.getLightFor` (patched, range-checked) or
  `World.getLightFor` (patched). Neighbor brightness goes through the same two with `pos.offset(facing)`. A14 is a
  presence check: the body has no `getY()` and exactly one `getBlockState`; any other body is a miss.
* **A12 needs nothing.** `alfheim$initSkylightForSection` already compares the height map with
  `section.getYLocation()`, not an index. Recorded as found when that compare is there.
* **A11: only the section base.** The `isAreaLoaded` box (y 0..255) only gates whether the box meets the world's
  height; 0..255 meets both kinds of world, and the rest of the test is x/z. The base `i * 16` becomes `slotBase(i)`.
* **A13: one bottom, not two.** Alfheim's `relightBlock` has one `j > 0` loop bottom and the `& 255` mask, and reads
  no section.
* **A9 in detail:** `sections[yMin >> 4]` (slot), `yMin > 0` (world's bottom), the flag loop's `sections[s]` where
  `s` is a section y (to `sectionSlot`), and the two `1 << s` (to `flagMask`, set and test). Its other section-y
  arithmetic (`s << 4`, `(s << 4) + 15` for the neighbor columns) is real heights and stays.
* **New A15: `ChunkMixin.setLightForRedirectGenerateSkylightMap`** (its `@Redirect` in `generateSkylightMap`) reads
  `storageArrays[pos.getY() >> 4]`: throws below 0. To slot.
* **A4 becomes a site: the queue's first loop skips positions outside the world.** `World.checkLightFor` (Alfheim's
  overwrite) enqueues any position, unchecked. In Alfheim's own layout a y outside 0..255 garbles into one inside
  (256 becomes the light bits and decodes as 0, -1 as 255): harmless. In the new layout y 320 or -65 decodes as itself,
  and the chunk's section read would throw. `processLightUpdatesForTypeInner` already skips a null chunk there; the
  chunk becomes null (`AlfheimHooks.inWorld`) when the decoded y is outside the world's range. Neighbors are filtered
  by A6; nothing else enters the queues.
* **The layout is decided once, as a constant.** `AlfheimHooks.WIDE` (static final) is read the first time the
  engine is used, from `HeightContent.allowed()`; `HeightContent` touches it right after deciding and refuses
  extended height if it was already fixed narrow. With the coremod running but extended height off (a missed site
  elsewhere), every patched Alfheim site computes Alfheim's own values: encode/decode/shifts/masks/`Y_CHECK` are the
  narrow ones, `slot`/`slotBase` equal `>> 4`/`<< 4` for 0..255, flags are one per section. With the coremod off
  (config, conflict, or an Alfheim that is not 1.6) Alfheim's classes are not touched.
* **Split for testing:** `AlfheimKey` is the plain arithmetic (key layout, flags), unit-tested on its own;
  `AlfheimHooks` is what the patched sites call (it reads the world's range and `WIDE`, then calls `AlfheimKey`).
* **Labels:** the patches report as `AlfheimLightingEngine.*`, `AlfheimChunkMixin.*`, `AlfheimChunkCacheMixin.*`
  (VintageFix's `ChunkMixin` already reports as `ChunkMixin.*`). With Alfheim installed, `HeightContent` also
  requires all three to be reported patched, and loads `LightingEngine` (not its mixins) at start-up so its sites are
  in the report before the decision.

Final list: A1-A11, A13, A15 change code (A4 the filter); A12 and A14 are checks. 26 recorded sites (the drift report also records that the fields the sites read exist, and each layout check before a head redirect).

### Risks

* **Mixin injection targets**: Alfheim's `@Redirect`/`@ModifyVariable` in `Chunk.setBlockState`
  (`new ExtendedBlockStorage`, the second boolean `STORE`, `propagateSkylightOcclusion`, `getLightFor`),
  `generateSkylightMap` (`setLightFor`) and `SPacketChunkData.<init>` (`getWorld`) must still find their targets after
  our patches. Ours replace array reads and constants, not invokes or stores, so they should; but with
  `defaultRequire: 1` a miss is a **crash at class load**, not a refusal. The first run in a pack shows it; if one
  ever fails, the fix is on our side (keep that instruction).
* **Lighting correctness** is harder to see than blocks: wrong boundary flags show as dark seams at chunk edges below
  y 0, a wrong key as light leaking between far-apart positions. Tests below target exactly those.
* **The ±16.7M limit**: positions beyond it alias in Alfheim's queue (wrong light there, no crash). Only in extended
  worlds; nobody plays that far, and Dregora's teleporters land at 13-17 thousand.
* **Fluidlogged API** and Alfheim already coexist in Dregora; our patches change neither's interplay.
* **Alfheim updates**: a new Alfheim version can move any of these; the version check refuses rather than guess.
* **Speed**: Alfheim is in the packs for speed. The coarser flags and the extra range compare cost a little; measure.

### Test

1. **Unit** (`tools/height/run.sh AlfheimKeyTest`): encode/decode round trip for every y -64..319 at x, z = 0, ±1,
   ±2^24 - 1; each neighbor shift lands on the neighbor; `outside` true exactly at -65 and 320 in an extended world
   and at -1 and 256 in a vanilla one; flag bit/range cover every section once.
2. **Offline drift** (`PatchCheck`) over Alfheim 1.6's `LightingEngine`, `ChunkMixin`, `ChunkCacheMixin` with the
   verifier.
3. **Dedicated server with Alfheim** (Forge 2860, MixinBooter, Red Core, Alfheim 1.6, Fluidlogged API, Core, our test
   jar; on G:, own port, stopped by its PID): the height self-test (`-Dcavesandcliffsbackport.selftest=height`, its
   light checks: sky 15 at y -64 up an open column, 0 in deep stone, block light at -40 and 319) plus new checks: a
   torch at y -30 by a chunk border lights across it; a shaft dug from the surface to -60 gets sky light after
   Alfheim's queue drains, and loses it when capped; the same after a save and reload (the flags in NBT); a vanilla
   (Default) world's lighting identical to Alfheim without our jar over a fixed area.
4. **Pregen speed**: `tools/packs/pregen.sh` on that server, `cavesandcliffs` world, with Alfheim and without (and a
   Default world with Alfheim, with and without our jar): chunks/s, E-cores.
5. **Clients**: the Dregora Celeritas and Fast copies on G: with Alfheim kept: their client tests (22/0, 15/0) and a
   light read at -60 on the client. Then the Shaders copy with Alfheim kept (with section 1 done): this is the full
   screenshot setup.

Effort: **about two and a half to three days** (one for the key and flag hooks with unit tests, one for the sites and
the server checks, the rest for the pack runs and speed).

## 3. Order

1. OptiFine (section 1): unblocks the shader screenshots with Alfheim removed from the screenshot copy.
2. Alfheim's first step: confirm the open site list against its classes (an hour).
3. Alfheim (section 2), then the Shaders copy with both.
4. Update `height.md` (sites, tested), `packcompat.md` (what each pack removes: Alfheim no longer), the changelog's
   "Not with" line, and `EXTENDED_HEIGHT.md` section 3.
