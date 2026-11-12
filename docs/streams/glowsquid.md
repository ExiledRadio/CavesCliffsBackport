# Stream Gs: glow squid, glow ink, glow item frames, glowing sign text

Package `glowsquid` (client part in `glowsquid.client`). Config `glowSquid`: `enabled`, `spawnWeight` (10; 0 stops
natural spawning), `glowingSigns` (true). Entity ids 5 (glow squid) and 6 (glow item frame); packet id 40 (ink
squirt). **No bytecode patch**: see *Glowing sign text* below for where the flag is kept and why.

Shared files touched: `ModConfig.GlowSquid` (two options), `NetworkHandler` (packet 40), `ClientSetup` (one
line), `SelfTestRunner` (one line), the mod class (one `init` line, and **a constructor** with one line, see
*Spawning*), `tools/lang/vanilla_keys.py` (entity names now map to `entity.minecraft.<name>`).

## Changelog section

*   **Glow squid** spawn in lightless water deep underground (y 30 and below). They are drawn at full
    brightness and shed glowing particles. Hit one and it squirts glowing ink and goes dark for five seconds.
    They drop 1 to 3 **glow ink sacs** (more with Looting) and have a spawn egg.
*   **Glow item frame** (an item frame and a glow ink sac): an item frame whose item or map is always fully
    lit, even in the dark.
*   **Glowing sign text**: use a glow ink sac on a sign to make its text glow, outlined and readable in the
    dark; an ink sac removes the glow. *Glow and Behold!* for the first sign.

## How it works

*   **Glow squid** (`EntityGlowSquid`) is a subclass of the squid, so guardians hunt it and anything that
    looks for squid finds it. Its dark time is a synced value saved as `DarkTicksRemaining` (current Java's
    name). As in current Java, only a hit with an attacker behind it makes it squirt and go dark. The squirt is
    packet 40: the client makes the thirty ink particles (1.12 cannot send a mod's particle from the server).
    Loot table `cavesandcliffsbackport:entities/glow_squid`.
*   **Spawning** is current Java's (1.18 and later): water, y at most sea level - 33, sky and block light both
    0, in every biome that is not Nether, End or void, weight 10. Glow squid have their own spawn group with
    a cap of 5 ("underground water creature"), so they never take the squid's places: an `EnumCreatureType`
    added with `EnumHelper`. It is added in **the mod's constructor**, before the config is read and so even
    when the stream is off, because a type added after `Biome.getSpawnableList`'s switch first ran crashes
    the mob spawner; `SwitchTables.grow` lengthens a table something filled even earlier. If the type cannot
    be added, glow squid share the squid's cap. 1.12's spawner picks its own pack sizes, so the 4-6 of 1.18
    only applies where 1.12 uses it (chunk generation does not spawn water mobs).
*   **Glow item frame** (`EntityGlowItemFrame`) is a subclass of the vanilla frame with its own entity id:
    comparators, maps and other mods see an item frame. It differs in what it drops and is picked as, its
    sounds (every vanilla frame sound is swapped for the glow frame's in `playSound`) and its look. Being a mod
    entity it sends where it hangs and its facing as extra spawn data. Like 1.12's item frame it hangs on
    walls only (floors and ceilings came in 1.13).
*   **Drawing the frame**: `RenderGlowItemFrame` repeats vanilla's renderer (whose model names and item drawing
    are private) with two changes, both current Java's numbers: the frame has block light 5 at least, and its
    contents are drawn at block light 13, sky light 15. The frame's two models are 1.12.2's own item frame
    models with the glow back, loaded as item models (`glow_item_frame_entity`, `..._map`) so the game loads,
    stitches and bakes them the normal way; the renderer asks for them by name.
*   **Particles**: the glow particle uses 1.17's sprite, stitched into the block texture sheet; glow ink runs
    through the eight generic particle pictures 1.12 already has, in the teal the game really shows.
*   **Glowing sign text** (`GlowingText`, decision 3 of the plan). The flag is on the sign itself, under
    current Java's name, in the sign's Forge data compound: NBT path **`ForgeData.GlowingText`**, not
    `GlowingText` at the root. Forge saves that compound with every tile entity, and a sign's update packet and
    chunk data are its whole NBT, so vanilla's own packets carry the flag to clients. That gives what the
    decision asked for (on the sign, no side list) with **no patch of `TileEntitySign`, no packet and no
    coremod in this jar**; the price is the one extra level in the path. Everything reads and writes it through
    `GlowingText.get` / `set`, so moving it to the root later (for instance inside the terrain stream's
    coremod) is a change to those two methods plus a patch of `readFromNBT`/`writeToNBT`.
*   **Using the sacs** is current Java's rule, as a `RightClickBlock` handler: a glow ink sac on a sign that
    does not glow, or an ink sac on one that does, changes it, plays the sac's sound and uses one up (not in
    creative); anything else is left alone; sneaking does nothing. Works on any `BlockSign` with a
    `TileEntitySign`.
*   **Drawing glowing text**: `RenderGlowingSign` replaces vanilla's sign renderer
    (`ClientRegistry.bindTileEntitySpecialRenderer`, no patch). It calls vanilla's and, for a glowing sign,
    draws the text again at full brightness: once at each of the eight pixels around it in the outline color,
    then the text on top. 1.12 has black sign text only, which current Java outlines in `0xF0EBCC`. Another mod
    that binds its own sign renderer after ours replaces it, and the glow is then not drawn.

## Tested headlessly

`./gradlew runServer -Pselftest=glowsquid` (port 25848): **`SELFTEST DONE pass=94 fail=0`**; with
`-Pselftest=all`: **`pass=2190 fail=0`**. `python tools/check_assets.py`: **88 blocks, 1248 states, 84 item
models, 421 sound files, 0 errors**.

*   Registration: items, entity ids and network ids, the spawn egg, the recipe, the spawn group (cap 5,
    present in plains, ocean and mushroom island, absent from the Nether, End and void and from the squid's
    list), vanilla spawn lists still answering.
*   Spawn rule in built pools: yes in sealed water at y 18 and y 30, no at y 31, no beside glowstone, yes
    again once it is gone, no in stone, no in open water at y 101; the world offers the glow squid entry there.
*   Glow squid: 10 health, counts against its own group only, stays lit after damage from nobody, dark for
    100 ticks after a player's hit, counting down, saved and read back as `DarkTicksRemaining`; killed, it
    drops 1-3 glow ink sacs and nothing else; the loot table gives 1, 2 or 3 over 300 rolls.
*   Glow item frame: placed from the item on a wall (not on top, not twice in one place), spawn data round
    trip (position, facing, box), takes an item, comparator 1 then 2, saves under its own id and loads with
    its item, first hit drops the item, second drops a glow item frame and no item frame, nothing for a
    creative player, falls off with its contents when the wall goes, pick result.
*   Signs, through the server's real right-click path: ink sac on a plain sign, sneaking, and the option
    switched off do nothing; a glow ink sac sets the flag, uses one, grants *Glow and Behold!*; a second is
    not used; the flag is in the saved NBT and the update tag, and a sign read from that tag glows with its
    text; an ink sac clears it everywhere; creative keeps the sac; another dye does nothing; wall signs; a
    new sign in a broken one's place does not glow.

*   Switched off (fresh world): boots, the scaffold test passes, no loot table or advancement errors.

## Not tested

*   Everything drawn: the glow squid's texture and brightness and its fade when hurt, glow and ink particles,
    the glow frame's models and the full-bright item and map in it, the glowing text's outline and brightness
    (standing and wall signs, each rotation), the item models, the spawn egg colors.
*   Client paths that need a client: the frame arriving through the mod-entity spawn packet (its data round
    trip is tested, the packet is not), packet 40 arriving, the sign's flag arriving in a real update packet
    (the tag it is built from is tested).
*   Natural spawning by the real spawner (it needs a player nearby): the rule, the spawn lists and the group
    are tested piece by piece, not a squid appearing by itself. Spawn rates against 1.18 are not measured.
*   Sounds being heard, including the frame's swapped sounds (the mapping runs; nothing listens on a server).
*   Another mod adding spawns in pre-init together with ours on a real Forge server (the case the constructor
    is there for): `testservers/quarkcompat` was not run for this stream.
*   A sign with a click command still running it after a sac is used; other mods' signs.
