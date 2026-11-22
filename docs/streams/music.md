# Stream Mu: 1.18 music and the Otherside disc

Package `music` (client part in `music.client`). Config `music`: `enabled`, `gameMusic`, `biomeMusic`, `menuMusic`,
`othersideLoot`. No entity ids; packet ids 60-64 unused (a 1.18 world's biomes come in the terrain's packet 65). Also changed outside the package: one line each in the mod
class's pre-init, `ClientSetup.init` and `SelfTestRunner.TESTS`; `tools/check_assets.py` now accepts a sound that
1.12.2's own asset index has (`minecraft:music/game/calm1`), as the other family mods' checkers do.

## For the integrator

**Done:** `MusicContent.preInit` installs `MusicRegions.BIOMES` when `biomeMusic` is on. In a world with 1.18
terrain it answers 1.18's biome at the position from the biomes the server sends with each chunk
(`terrain.ClientBiomes`, packet 65; see `generator.md`): lush caves, dripstone caves, meadow, grove, snowy slopes,
jagged, frozen and stony peaks, as 1.18's client. Anywhere else it is `MusicRegions.CAVES`: lush and dripstone caves
from `common/CaveRegions` (the 1.17 regions, by the key the server sends at login), underground only.

1.  **The locator** (`music/MusicRegions`) is asked on the client thread, every tick, with the client world, so it
    must be cheap and must not load chunks.
2.  **This stream registers the 1.18 presentation version** (`Presentation.version("1.18")` in
    `MusicContent.preInit`). Core skips a version's music step until that version is installed, so the biome music
    needs it; it also means the game's look follows 1.18 wherever Core's tables say so, and only while the music
    stream is on. **Since stream Lf** (`lookfeel.LookAndFeel`) 1.18 is registered unconditionally there too; this
    call now only adds the menu tracks to the same contribution.
3.  **Jar size**: see the end of this file. The eight tracks and the disc are 112.3 MB of the jar.
4.  **Core requests**: `Mu-1` (jukebox comparator signal) and `Mu-2` (a game music list) in `docs/CORE_REQUESTS.md`.

## Changelog section

*   **Otherside**, Lena Raine's music disc. It plays in a jukebox like any other disc. It is found in dungeon chests
    and in the chests of stronghold corridors, rarely.
*   **1.18's music**: *Comforting Memories*, *Floating Dream*, *Left to Bloom* and *One More Day* play among the
    old tracks in survival and creative. *Infinite Amethyst*, *Left to Bloom*, *One More Day*, *Stand Tall* and
    *Wending* join the title screen's music.
*   **Lush caves and dripstone caves have their own music**: a selection of old and new tracks, *An Ordinary Day*
    among them. A track that is playing finishes when you walk in or out.

## How it works

*   **The disc** (`ItemMusicDisc`, `MusicContent`): 1.12.2's own `ItemRecord`, so the jukebox, the "Now Playing"
    line, the tooltip, the rare name color and the record stat are vanilla's. Registry name `music_disc_otherside`,
    sound event `music_disc.otherside` (streamed), texture from 1.18.2. Creepers killed by skeletons never drop it,
    as in current Java (it is not in `creeper_drop_music_discs`).
*   **Loot** (`MusicContent.lootLoad`): 1.18 lists the disc in the first pool of `chests/simple_dungeon` (weight 2
    of 129, 1-3 rolls) and of `chests/stronghold_corridor` (weight 1 of 100, 2-3 rolls); current Java's tables are
    the same. The entry joins that pool (`main`) in a `LootTableLoadEvent`, so the totals are vanilla's. If another
    mod has removed that pool, a pool of our own rolls once at 2 of 129 or 1 of 100. `music.othersideLoot`, read
    when loot tables load.
*   **Game music** (`music.client.GameMusicPack`): a built-in resource pack serving one file,
    `minecraft:sounds.json`, whose `music.game` lists the four tracks 1.18 added (volume 0.4, as 1.18's) and
    replaces nothing. The game merges it with 1.12.2's own list, another mod's and a resource pack's. 1.12.2's
    creative music includes the game music by reference, so it gains them too, as in 1.18. The file is under
    `cavesandcliffsbackport_packs/`, outside `assets/`, so it is served only while `music.gameMusic` is on. The pack
    is added to the default packs in client pre-init, after the startup resource load and before the sound handler
    first reads the lists.
*   **Menu music**: the five tracks 1.18 added to `music.menu` go to Backport Core's menu music pool
    (`Presentation.version("1.18").addMenuMusic`), from the generated list `cavesandcliffsbackport_packs/menu_music.txt`.
    `music.menuMusic`.
*   **Biome music** (`MusicChoice`, `MusicRegions`, `music.client.MusicClient`): Core's `GameMusic` step "1.18 biome
    music", the last line of 1.18.2's `getSituationalMusic`. Where the versions before chose the game music and the
    player's position is in a music biome, the biome's event plays instead, with 1.18's game music timing (10 to 20
    minutes, never cutting off a track). Creative flying, under water, the nether, the End and a dimension's own
    music come first, as in 1.18. On leaving the biome while its track plays, the game music is chosen as
    non-replacing so the track finishes: 1.12.2's own game music would cut it off at every region edge.
    `music.biomeMusic`, read every tick. Core's ticker plays it; a ticker another mod installed is left alone by
    Core, and then no biome music plays.
*   **26.2's additions** (branch `gd-paintings-music`, 1.1.5): with Game Drops Backport installed (its
    `music/game/ebb.ogg` is on the classpath) and `music.biomeMusic` on, a second built-in pack serves one more
    `cavesandcliffsbackport:sounds.json` (`cavesandcliffsbackport_packs/fingerspit/`) adding fingerspit's tracks to
    the eight lists as 26.3's `sounds.json` has them: 13 entries, weight 2, volume 0.4 (Nightly in the dripstone
    caves at full volume, as vanilla), replacing nothing. These lists play only without Trails & Tales, whose "1.20"
    step swaps each for its own list (which adds the same tracks).
*   **Events**: `music.overworld.lush_caves`, `dripstone_caves`, `meadow`, `grove`, `snowy_slopes`, `jagged_peaks`,
    `frozen_peaks`, `stony_peaks`, with 1.18's lists, weights and volumes.
*   **Assets** (`tools/music/generate.py`, from the 1.18 asset index): a file 1.12.2 already ships byte for byte
    (calm, hal, nuance, piano) is not copied; `sounds.json` names it `minecraft:music/game/...`, which every 1.12.2
    game has. None of these events has a subtitle in vanilla.

### Decided from the sources

*   **Comparator signal**: 1.18's disc gives 14. 1.12.2 counts the signal from the item id and this mod has no
    patch, so it is not 14 here (`CORE_REQUESTS` Mu-1). The value is on the item for the shared patch.
*   **Mountain music** is registered although the mountain biomes are the terrain stream's: the events cost nothing,
    and *Stand Tall* ships for the menu anyway.
*   **Ancient cities** also hold the disc in current Java; they are the Wild Update's.

## Tested headlessly

`./gradlew runServer -Pselftest=music` (port 25852): **`SELFTEST DONE pass=307 fail=0`**. `-Pselftest=all`:
**`pass=2403 fail=0`**. `python tools/check_assets.py`: **88 blocks, 1248 states, 83 item models, 433 sound files,
0 errors**. `./gradlew build` clean; the jar's classes carry `func_` and `field_` names and no test classes.

*   The disc: registry name, vanilla record class, stack size 1, rare, translation key, signal value 14, its sound
    event registered, a creative tab.
*   Jukebox: using the disc inserts it, a full jukebox takes no second one, the record survives saving, breaking
    the jukebox drops it.
*   Loot: the disc is in the first pool of both tables at weights 2 and 1, the pools total 129 and 100, luck does
    not change it, no pool of our own beside it, and seven other chest tables have none. 20,000 generated chests of
    each: 3.23% of dungeon chests and 2.64% of stronghold corridor chests hold it (the wiki: 3.1% and 2.5%).
*   Sound lists: the disc's, lush caves' and dripstone caves' tracks as 1.18's, every sound streamed, every file of
    ours in the jar, the eight biome events registered, *Left to Bloom*'s and *Wending*'s weight 2.
*   The game music pack file: one event, no `replace`, the four tracks at volume 0.4. The menu list: the five
    tracks, in the jar, and five tracks in Core's menu pool; Core's newest version is at least 1.18.
*   The rules (`MusicChoice`): biome music only where the game music was chosen; never over creative, nether, End,
    dragon, credits or menu music; timing 12000-24000, non-replacing; the game music made non-replacing on leaving
    a biome while its track plays.
*   `MusicRegions`: none by default, a locator's answers, a null answer and a failing locator are none.

## Tested on a client

`./gradlew runClient -Pclienttest=music` (`MusicClientSelfTest`), 2026-11-15: **`CLIENT SELFTEST DONE pass=28
fail=0`**. A single-player Caves & Cliffs world (seed 1718); at spawn and at a lush caves, a dripstone caves and a
mountain spot the server finds by its biome source, the server reads its 1.18 biome on a grid through 7 x 7 chunks
(every 3 blocks across, every 5 up, y -64 to 319) and the client's `ClientBiomes` id and `MusicRegions.at` are the
same at every point: 444,752 points, 0 different (lush caves 4,994, dripstone caves 8,130, meadow 24,497). The 1.17
regions would have given a different music biome at 69,383 of them. The music biome at the player's feet matches
the server's and the spot's. Then `./gradlew runServer -Pselftest=all` on a fresh `eclipse/`: **`SELFTEST DONE
pass=4448 fail=0`**.
*   Switched off (fresh world): boots, nothing registered, no loot entry (`pass=5 fail=0`).

## Not tested

Nothing here was listened to.

*   Playback of anything: the disc in a jukebox, the four game tracks, the menu tracks, the biome tracks.
*   The built-in pack on a real client: that the sound handler merges its `music.game` with 1.12.2's (it is checked
    as a file only), that it survives F3+T and a resource pack change, and that `music.gameMusic` off leaves
    1.12.2's twelve tracks.
*   Core's ticker taking the "1.18 biome music" step, and the step itself (`MusicClient.biomeMusic`): the client
    test checks the music biome it asks, not what plays.
*   A dedicated server's client (the test runs single player, where packet 65 goes through the integrated server's
    local channel).
*   The disc's model, texture, tooltip and "Now Playing" line.
*   What the game looks like with 1.18 registered as the presentation version (item 2 above).
*   The fallback loot pool, used only where another mod has removed a table's `main` pool.
*   The comparator beside a jukebox playing Otherside (known wrong, `CORE_REQUESTS` Mu-1).
*   The 18 languages: only `en_us.lang` and `tools/lang/vanilla_keys.json` were regenerated.

## Jar size

| | Size |
| --- | --- |
| `main` (6577924) | 2.9 MB |
| `cc-music` | 120.3 MB |

The music adds 117.4 MB (112.3 MiB of `.ogg`, stored as is; code and the rest are a few KB):

| File | MB | Used by |
| --- | --- | --- |
| `music/game/wending` | 18.1 | menu, dripstone caves, mountains |
| `music/game/left_to_bloom` | 17.6 | game, menu, lush caves, meadow |
| `music/game/stand_tall` | 17.5 | menu, mountains |
| `music/game/an_ordinary_day` | 14.8 | lush caves, dripstone caves, snowy slopes |
| `music/game/one_more_day` | 14.7 | game, menu, lush caves, mountains |
| `music/game/infinite_amethyst` | 13.7 | menu, dripstone caves, grove |
| `music/game/comforting_memories` | 11.1 | game, grove |
| `music/game/floating_dream` | 4.8 | game, lush caves, jagged peaks |
| `records/otherside` | 5.6 | the disc |

Every track is used by a feature, so there is nothing to drop without dropping one.
*Stand Tall* is the only one that serves just the menu until the mountains exist (17.5 MB).
