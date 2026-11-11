# Stream X: the light block, the Caves & Cliffs advancement, 1.17's small changes

Package `blocks` (client part in `blocks.client`). Config `blocks`: `enabled`, `dirtPaths`, `shulkerDuplication`,
`cookedMobDrops`, `pufferfishPoison`, `peacefulLlamas`, `infestedBlocks`. No packets (ids 55-59 unused), no
entities. Generator `tools/blocks/generate.py`.

## Changelog section

*   **Light**: an invisible block that gives off light, at any level from 0 to 15. Nothing collides with it. It
    shows only to a player holding a light in the main hand: its outline, and in creative mode a marker with its
    level. A creative-mode operator raises the level by using it (15 goes back to 0). It can't be mined, pushed
    or blown up; placing another block there replaces it. Get one with
    `/give @p cavesandcliffsbackport:light`, or a set level with
    `/give @p cavesandcliffsbackport:light 1 0 {BlockStateTag:{level:"7"}}`. Pick block copies a placed one.
*   New advancement ***Caves & Cliffs***: free fall from the top of the world to the bottom and survive.
*   A shovel makes a **path** from dirt, coarse dirt, podzol, mycelium and rooted dirt, not only from grass.
*   An open **shulker** hit by another shulker's bullet teleports away and may leave a new shulker behind:
    always when it is alone, less often for each shulker within 8 blocks, never with five. Shulker shells are
    now farmable.
*   **Zombies**, **husks** and **zombie villagers** that die while burning drop baked potatoes; **polar bears**
    and **guardians** drop their fish cooked.
*   **Pufferfish** gives Poison II instead of Poison IV.
*   **Llamas** no longer spit at players in Peaceful.
*   **Infested blocks** break faster with a pickaxe, and infested cobblestone takes half as long as cobblestone.
*   **Endermen** can pick up rooted dirt.

## How it works

*   **Light block** (`BlockLight`, `ItemLight`): one block, `level` 0-15 in its 16 metadata values. Its material
    is the structure void's: not solid, casts no shadow, and one of the few a flowing liquid treats as blocked, so
    water and lava stop at it as in Java. `isReplaceable` and the push reaction (blocks pistons) are overridden.
*   **Who can point at it**: a block's ray trace is not told who asks, so `canCollideCheck` asks a hook that only
    a client sets (`blocks.client.LightClient`): true on the client thread when its player's main hand holds a
    light. A server never points at one; it acts on the position a click names.
*   **The item** keeps its level as Java does, in `BlockStateTag.level` (a string; a number is read too); absent
    or out of range means 15. Its model is 1.18's, picking `light_00`..`light_15` from a `level` item property.
    Epic rarity. In **no creative tab**, as in 1.17 and as 1.12.2's barrier (current Java's Operator Utilities tab
    has no 1.12.2 counterpart); it is registered directly rather than through `Content.blockItem`, which would
    look for a tab and warn.
*   **A light never replaces a light**: used on a light block (sneaking), the item places beside it; aimed into
    a cell that holds one, it fails.
*   **Changing the level** needs creative mode, operator level 2 and a light in the main hand, checked on the
    server (`EntityPlayer.canUseCommandBlock`). Using a light block is always "used up", so the item in hand is
    never placed by the same click.
*   **Falling blocks**: 1.12.2's sand asks one fixed question about the block under it (air, fire, liquid). When
    a falling-type block comes to rest on a light, the light swaps itself for air, silently, for the length of
    that block's own update, then comes back. The falling block passes through (no collision) and replaces the
    light only if it lands in it.
*   **The marker** is 1.12.2's barrier marker with the light's own picture, from the block's random display
    tick: creative mode, light in the main hand, thinned by the particles option.
*   **Caves & Cliffs** (`WorldFall`): current Java's rule. A fall starts on the first tick the player's fall
    distance is above zero and ends when it returns to zero (landing, water, a ladder); alive at that moment, it
    counts when it started at most one block under the build limit, ended at most five above the world's floor
    and covered the height between but five. The limits come from Backport Core's `WorldHeights.minY` /
    `maxYExclusive` for the player's world each time: y 255 to y 5 at 1.12.2's height, y 319 to y -59 with
    extended height. The advancement file carries `item_exists: light`, so it goes when the stream is off.
*   **Changes to vanilla things** (`VanillaChanges`) are Forge events and public vanilla calls only:
    *   paths: `RightClickBlock` with an `ItemSpade`, then the vanilla shovel's own code;
    *   shulkers: `LivingDamageEvent` from a shulker bullet is noted and settled when the server tick ends
        (a shulker under half health may teleport by itself first, and then, as in Java, nothing more happens),
        then Java's `hitByShulkerBullet`: open, teleports, a new shulker of the same color where it was unless
        a random number is under (shulkers within 8 blocks - 1) / 5. The teleport is the shulker's own protected
        method, by reflection;
    *   cooked drops: `LivingDropsEvent` (raw cod and salmon, potatoes);
    *   pufferfish: `LivingEntityUseItemEvent.Finish` swaps the fresh Poison IV for Poison II;
    *   llamas: `LivingSetAttackTargetEvent` clears a player target in Peaceful;
    *   infested blocks: `Blocks.MONSTER_EGG.setHarvestLevel("pickaxe", 0)` (it still needs no tool) and a
        `BreakSpeed` factor of 0.75 for the cobblestone variant (hardness 0.75 to 1);
    *   endermen: `EntityEnderman.setCarriable` for stream L's rooted dirt, by name.

## 1.17 changes checked and left out

From minecraft.wiki's *Java Edition 1.17*, changes to existing things that no stream covers and this one does not
build:

| Change | Why not here |
| --- | --- |
| Anvil names up to 50 characters | 35 is a constant inside the server's packet handler; needs a patch |
| Mycelium takes the plants dirt takes | `Block.canSustainPlant` on a vanilla block; needs a patch or replacing the block |
| Rails can be waterlogged and survive flowing water | waits for the waterlogging pass (PLAN, *Waterlogging*) |
| Shulker boxes drop their contents when destroyed as items | no event for an item entity's destruction; needs a patch of `EntityItem` |
| Squid can be leashed | 1.12.2 only pulls `EntityCreature` on a lead; squid would need their own lead physics |
| Minecarts move through water | minecart physics; needs a patch |
| Experience orbs merge; no longer follow dead players | performance and a detail of `EntityXPOrb`; needs a patch |
| Mineshafts (log pillars, chains, no floating pieces), strongholds buried under the seabed | vanilla structure generation; belongs with the terrain work (stream T) if anywhere |
| Emerald ore in other stones, the new ore distribution | stream T |
| Dispensers wax copper with honeycomb | stream Cu's; not built there yet |
| Glow berries in mineshaft chest carts, moss in shipwreck chests | stream L's loot |
| *A Balanced Diet* needs glow berries; *Two by Two* needs goats and axolotls | each needs a vanilla advancement file replaced; the owning streams' call |
| Mason and wandering trader trades, composting, cauldrons as workstations, stonecutter recipes | already listed in PLAN under *Integrations* |
| Names (Dirt Path, Block of Lapis Lazuli, *Cover Me with Diamonds*), new textures | Backport Core's packs (`CORE_REQUESTS.md`) |
| Beacon beam distance, end portal underside, absorption hearts, glowing outlines, piston break particles, structure block screens, creative tab order, bone meal and dye sounds | client rendering, screens and sounds of vanilla things; out of scope (PLAN, *Scope at a glance*) |
| Endermen ignoring endermites not from pearls; guardians' first fish cooked when burning | already so in 1.12.2 |

Also not built: the light block's **waterlogging** (PLAN, *Waterlogging*): a water bucket used on a light
replaces it.

## Tested headlessly

`./gradlew runServer -Pselftest=all` (port 25851), then `python tools/check_assets.py`:
**`SELFTEST DONE pass=2236 fail=0`** (140 of them this stream's); **89 blocks, 1264 states, 83 item models,
369 sound files, 0 errors**. `./gradlew build` clean; the jar's classes carry `func_` names and no test classes.

*   Light block: registration, 16 states and their metadata, light value per level, hardness, blast resistance,
    push reaction, no collision, no drop, no creative tab, rarity; a server cannot point at it.
*   Item levels: no tag, string, integer, not a number, out of range; what `withLevel` writes; pick block.
*   Placing as a survival player: at the item's level, the world lit to 7 and 6 beside it; a light does not
    replace a light; used on a light it goes beside it; cobblestone placed into a light replaces it with no
    drop; adventure mode cannot place.
*   Changing the level: not for a creative non-operator, +1 for a creative operator, 15 to 0 (and the world goes
    dark), not with an empty hand, not in survival.
*   A piston cannot push into a light or push a block into one; flowing water spreads the other way and not
    through it.
*   Falling blocks: sand on a light on the ground takes its place; gravel on a light in the air falls through
    and the light stays; a light put under resting sand lets it fall.
*   *Caves & Cliffs*: the world's heights read from Core (0 to 256 here); the limits (and that both limits at
    once are a block short, as 319 to -59 is in 1.18); not for a fall that kills, not for one that started
    lower, nothing in mid-fall, granted at the end of a whole fall survived.
*   Paths from dirt, coarse dirt, podzol and mycelium with one durability each; not from below, not under a
    block, not from stone, grass left to the vanilla shovel, not with a pickaxe.
*   Cooked drops: burning zombie potato baked, not otherwise, rotten flesh kept; burning polar bear salmon
    cooked (count and kind kept), not otherwise, pufferfish kept, a polar bear's potato kept.
*   Pufferfish: Poison IV becomes Poison II for 1200 ticks; another food leaves a Poison IV alone.
*   A llama drops a player target in Peaceful and keeps it in Normal.
*   Infested blocks: pickaxe is the tool and mines at its speed; break speed x1 for stone, x0.75 for cobblestone.
*   Shulkers: a closed one hit by a bullet is hurt and stays one; an open one teleports and leaves a second
    where it was; with six others within 8 blocks, three hits make none; other damage makes none.
*   Switched off (fresh world, `blocks.enabled=false`): boots, no light block, no advancement error.

## Not tested

*   Everything client-side: the outline and pointing at a light while holding one, the marker and its
    thinning by the particles option, the item's numbered pictures, the arm swing on a path.
*   A real client's click path: the tests call the item's and block's use methods and post the click event
    themselves.
*   The advancement through a real fall (movement packets set a real player's fall distance; the test sets it),
    landing in water, and with extended height (stream T is not on this branch; the limits are Core's answer).
*   Shulker multiplying in a real fight (a bullet's own flight and hit), and a shulker under half health
    teleporting by itself in the same tick.
*   Cooked drops and pufferfish through a real death and a real meal (the events are posted by the test).
*   Llamas over time in Peaceful (their AI setting the target again each tick), infested blocks mined by a
    real player, endermen picking up rooted dirt (stream L is not on this branch).
*   Wither and ender dragon against a light block; lava at a light block (water is tested).
*   The jar in a real instance.

## For the integrator

*   `tools/common/generate.py` is behind `main`'s `en_us.lang`: run as it is, it drops the `.tooltip` line of every
    option that carries a `@Setting.*` annotation (13 lines) and adds back the `itemGroup` line. This stream's
    config lines were therefore added to the `common` section by hand, in the generator's format.
*   `ClientSetup.init` has one new line (the light block's client hook).
