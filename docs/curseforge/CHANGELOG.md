# 1.1.7

*   **Less lag**: axolotl spawning no longer builds an axolotl for every water block it tries; it checks the spot first. It took a fifth of every server tick.

# 1.1.6

*   **Fixed: a crash loading saved chunks near a stronghold** ("Exception ticking world", in `StrongholdBurial`), in any world type, since 1.1.3.
*   **Azalea trees grow as 1.17's do**: their bending trunk, its height and the leaf spread match vanilla's, so grown azaleas are no longer too small.
*   With Dynamic Trees - Backport, lush caves' azalea trees and azalea bushes grown with bone meal are dynamic azalea trees.
*   **RTG and OTG worlds run from y -64 to 319.** New Realistic Terrain Generation and Open Terrain Generator worlds keep their own terrain, biomes and structures from y 0 up. Below y 0 every chunk gets 1.18's deepslate, caves, aquifers, ores, lush caves, dripstone caves and, with Wild Update Backport, the deep dark, as 1.18 gave old worlds. The config's `otherWorldTypes` lists the world types. Existing worlds keep y 0-255.
*   **`/cavesandcliffs upgradeworld` upgrades RTG and OTG worlds too.** They stay RTG and OTG worlds: old and new chunks keep their own terrain from y 0 up and get 1.18's below.
*   Upgraded worlds keep the deep dark below y 0 (Wild Update Backport), as 1.19 does.
*   **Fixed: with Dynamic Trees, 1.18 trees and dynamic trees generated side by side** in the 1.18 terrain where two biomes meet. Each tree now asks about the biome it stands in: Dynamic Trees' trees where it grows them, 1.18's elsewhere.
*   **Fixed: with Valkyrie, nothing below y 0 was drawn** without Celeritas or Nothirium, and the game could crash when flying above y 320. Valkyrie replaces the code that places the game's render chunks, and its copy only knew y 0-255. Valkyrie 0.3-Dev-5 now draws y -64 to 319; with another Valkyrie version the y -64 to 319 height turns itself off and the log says why.
*   **Fixed: with PaperFixes, nothing below y 0 or above y 255 was drawn, and mobs could not walk up blocks there.** Its pathingChunkCache and pathNodeCache options replace code that reads blocks for chunk drawing and pathfinding, and their copies only knew y 0-255. PaperFixes 2.0.1 now works from y -64 to 319 with both options on; with another PaperFixes version the y -64 to 319 height turns itself off and the log says why (turning both options off in config/paperfixes.cfg also works).
*   **Fixed: with Roughly Enough IDs or Just Enough IDs, the world below y 0 (and above 255) went missing** once chunks were saved and loaded again. Those sections are now saved in their format. Chunks saved this way by 1.1.5 or older can't be read back: their part below 0 stays empty, and its data is kept in the world's `cavesandcliffsbackport_lost_sections` folder. Biomes in Caves & Cliffs worlds are also right with them now (their 1.18 vegetation was missing), and `/cavesandcliffs upgradeworld` reads their old chunks correctly.

Needs Backport Core 1.4.9 or newer.

# 1.1.5

*   **Upgrade an existing world to y -64 to 319**, as 1.18 upgraded old worlds: `/cavesandcliffs upgradeworld` (operators; it asks you to confirm and backs up level.dat), then restart. Explored chunks keep everything from y 0 up. Each one gets deepslate, caves, ores and aquifers below y 0 when it first loads, and its bedrock floor moves to y -64. New chunks get the 1.18 terrain and blend into the old ones at the border. No new structure starts in or reaches into an old chunk. Default worlds only; it can't be undone, so back up the world first.
*   **1.17's and 1.18's look and feel is Backport Core's now**: Hide Lightning Flashes, the Autosave Indicator, the clouds at 192, 1.18.1's fog, 1.18.2's F3 Block and Chunk rows, the brightness default of 50, the splashes, the title and startup logos, the button, check box and Accessibility textures, the multiplayer warning and vanilla's 1.18 wording. They work as before, and a later backport installed without this one has them too. Saved option values carry over, and the brightness default is not applied a second time. The panoramas and the music stay in this mod.
*   **Shader packs know our blocks** (OptiFine): deepslate ores, deepslate, tuff and the rest get the ids a pack gives their vanilla names, so Complementary's glowing ores and block effects work on them. Deepslate forms of other mods' ores get their original ore's id.
*   **Deepslate forms of other mods' ores look cleaner**, most of all ores with grey specks (Thermal's silver and platinum, Immersive Engineering's nickel and silver). A mod's ore textures are compared with each other to find which pixels are ore and which are stone.

Needs Backport Core 1.4.7 or newer.

# 1.1.4

*   **Fixed: Scape and Run: Parasites 1.10 mobs still did not spawn below y 0** in Caves & Cliffs worlds. SRP 1.10 spawns through a new spawner that 1.1.3 did not cover; both SRP 1.9 and 1.10 are covered now.

# 1.1.3

*   **With Game Drops Backport, new worlds get its biomes where 26.3 places them**: the dappled forest beside cool plains and taigas, and sulfur caves deep under low inland ground. The rest of the biome map is unchanged. Worlds made before keep their biomes.
*   **With Game Drops Backport, baby goats and axolotls get 26.1's models**, textures and animations. They follow Game Drops' Baby Models setting.
*   **fingerspit's music** with Game Drops Backport: Ebb, Home, Memories, Nightly and Shores play in lush and dripstone caves and the mountain biomes, as in 26.2.
*   **Scape and Run: Parasites mobs spawn below y 0** in Caves & Cliffs worlds, in the deepslate layer, as they do elsewhere underground. SRP's own spawner drew spawn heights from 0 up.

# 1.1.2

*   **The spyglass comes up to the eye in third person**, as in 1.17: the arm holding it rises and follows the head, armor and sleeves with it, and the spyglass is drawn at the eye. The Zoom with Spyglass key does the same when the spyglass is in hand.

# 1.1.1

*   **The spyglass is held as its 3D model** in first and third person, as in 1.17; the flat icon stays in inventories, on the ground and in item frames.
*   **Fixed: with VintageFix, everything below y 0 was drawn bright.** VintageFix replaces the method the game reads block light with while drawing chunks, and its copy only knew y 0-255.
*   **Caves & Cliffs worlds use much less memory.** The terrain generated past the loaded chunks, waiting to be decorated, is kept packed as 1.18 keeps chunks: about a tenth of the memory it took (225 MB at render distance 52). Terrain is unchanged and generation is as fast.
*   **Fixed: after zooming in with the spyglass, only what was in view while zoomed was drawn** until you moved or turned.
*   **Fixed a thin gap along the edges of the held spyglass.**

Bonus features, not in vanilla:

*   **Zoom with Spyglass key** (Z by default): hold it to look through a spyglass anywhere in your inventory, without holding it, with the spyglass's view, sounds and slowdown. Setting `amethyst.zoomKey` turns it off.

# 1.1.0

Behavior changes from 1.17 and 1.18 to things 1.12.2 already has, other mods' ores in 1.18's layers, and fixes. Each behavior change can be turned off in the Other Changes settings.

*   **Monsters spawn only in complete darkness** (block light 0), as in 1.18, spawners included. Sky light still counts as before.
*   **Each player has their own mob cap** in multiplayer, as in 1.18: one player's farm or a crowded base no longer stops mobs spawning around everyone else.
*   **Mineshafts as 1.17 builds them**: bridges over caves stand on log supports or hang from fences and chains, nothing floats, and cobwebs are only where they touch two solid blocks.
*   **Experience orbs merge** in large numbers, as in 1.17, and stop following players who have died.
*   **Rabbits and wolves spawn on snow**, as in 1.18.
*   **Smaller 1.17 and 1.18 changes**:
    *   Strongholds under the ocean are buried in the seabed in Default worlds, as Caves & Cliffs worlds already did.
    *   Minecarts in water go half as fast, sink slowly and slow down.
    *   A shulker box item destroyed by lava, fire, cactus or an explosion drops its contents.
    *   Flowers, saplings, grass, ferns, dead bushes and sugar cane can be planted on mycelium, and pumpkins and melons grow onto it.
    *   Anvil names can be 50 characters long instead of 35.
    *   Absorption hearts show withered under Wither and stay golden under Poison instead of going blank.
    *   Sleeping through the night clears the weather only when it is raining or snowing.
    *   Horses, donkeys and mules follow players holding a golden carrot or golden apple, and llamas players holding a hay bale.
    *   Enchanting tables give off light level 7.
    *   Brushing past a wall at a shallow angle no longer stops sprinting.
*   **Snow covers structures in Caves & Cliffs worlds**: villages, igloos and other structures in snowy places get their snow layer, as in 1.18, using 1.18's temperatures for the family backports' structures too.
*   **1.20's desert wells in Caves & Cliffs worlds** with Trails & Tales Backport: a basin a block deeper, with two suspicious sand to brush.
*   **Works with StellarCore** with any settings: its `WorldBlockPosJudgement` option no longer keeps Caves & Cliffs worlds at y 0-255.
*   **Other mods' ores move down in Caves & Cliffs worlds**, as 1.18 moved vanilla's: an ore another mod generates at y 0 to 63 is placed at twice that minus 64 (y 10 at -44, y 40 at 16), in stone, deepslate or tuff (as its deepslate form in deepslate and tuff), and those veins are doubled so each block of rock keeps the mod's own amount of ore. Ores above y 63, Nether and End ores and everything else a mod generates stay where they were. Setting `height.moddedOreHeights` turns it off.
*   **Glow lichen in Default worlds is as common as in 1.18**: it was about four times as dense on cave walls, and grew on floors and dirt. It now generates as 1.18.2's feature does, on ceilings and walls of stone, deepslate, tuff, calcite and dripstone. The `lush.lichenTries` setting is gone.
*   **Deepslate forms of other mods' ores**, as 1.18 gave vanilla's: every other mod's stone ore gets a deepslate ore with the same drops, experience, Fortune, ore dictionary names and smelting, 1.5 times as hard, with its texture drawn on deepslate at load (a resource pack can replace it by name). Nether and End ores get none. They replace the stone ores in the deepslate layer of Default worlds. The list is written to `config/cavesandcliffsbackport-modded-ores.txt`; `deepslate.moddedOresExclude` leaves ores out and `deepslate.moddedOres` turns it off.
*   **Works with BuildCraft's quarry** (8.0.0 and 7.99.24.8) in Caves & Cliffs worlds: it digs down to y -64 instead of stopping at y 0. A quarry placed before this update digs on down once its world is loaded again. BuildCraft's water springs generate there again, as they do in Default worlds.
*   **KAGIC's injectors work below y 5** in Caves & Cliffs worlds: gem seeds go down to y -59, and the depth bonus no longer breaks at or below y 0. Meteor rubies fall from the top of the world.
*   **Follows Backport Core's Newer Look and Feel switch**: with it off, the autosave indicator and Hide Lightning Flashes are off too, as in 1.12.2. Each keeps its own choice for when it is back on.
*   **Real item icons on the settings screen** for the features that showed vanilla stand-ins. Goats show Wild Update Backport's goat horn when it is installed.
*   **Fixed moss carpet and powder snow breaking into missing-texture particles.**

Needs Backport Core 1.4.0 or newer.

# 1.0.0

First full release. Since the beta:

*   **New worlds are Caves & Cliffs worlds**: Create World starts on the Caves & Cliffs world type, as 1.18's terrain is vanilla's default, and a new server uses it unless server.properties names another `level-type`. Default is still under World Type for 1.12's terrain, and existing worlds keep theirs. Setting `height.defaultWorldType` turns this off.
*   **New worlds spawn where 1.18.2 puts the spawn** for the seed, so a seed's start matches Chunkbase. Worlds made with the beta keep their spawn.
*   **Fixed a server crash with EagleMixins and NuclearCraft** when an entity was above y 192 in a Caves & Cliffs world.
*   **Fixed features cut off at the edge of the explored area after a crash.** If the server crashed or was killed, trees, ores and other features crossing into ungenerated chunks could be missing there. They are now kept through a crash.
*   **Works with Phosphor** (0.2.6 and 0.2.7) **and Hesperus** (0.2.9.1 to 0.2.9.3): the Caves & Cliffs world type no longer turns itself off with them, and their lighting covers y -64 to 319.
*   **Works with Cubic Chunks**: with it installed, the Caves & Cliffs world type is a cubic world with the same 1.18 terrain, block for block, from y -64 to 319.
*   **Pale gardens** in new Caves & Cliffs worlds with Tricky Trials Backport, where 1.21.5 and later put them, so seeds match Chunkbase for 1.21.5+. A setting places them as 1.21.4 does instead.
*   **The newer versions' changes to 1.18's features**: with Wild Update or Trails & Tales installed, trees, vines, disks and the rest decorate as 1.19 and 1.20 do. Mangrove leaves that grow into water hold it.
*   **Cave and mountain music follows 1.18's biomes**: lush caves, dripstone caves and the mountain biomes play their music where the terrain really has them.
*   **Water opened by 1.12's structures flows**: an aquifer a mineshaft or another mod's structure cuts into now spills, as in 1.18.
*   **Fixed goats now and then failing a long jump** they should make.
*   **Other mods' biomes in Caves & Cliffs worlds**: Biomes O' Plenty, Defiled Lands, Traverse and any mod's biomes in Forge's BiomeManager take the place of the closest 1.18 biome in some regions, with their own top blocks, trees, plants and mobs, as TerraBlender places them in 1.18. Without such a mod the terrain is exactly 1.18's. Settings in the `height` category tune or turn it off. Fixed Biomes O' Plenty's grove and meadow standing in for 1.18's grove and meadow.

Needs Backport Core 1.3.0 or newer. Bamboo jungles grow bamboo with Village & Pillage Backport, and warm oceans grow coral reefs with Update Aquatic Backport (without it, terracotta stands in).

# 0.1.0 (beta)

First beta. Everything from 1.17 and 1.18 is in: the Caves & Cliffs world type with 1.18 terrain from y -64 to 319, deepslate, copper, amethyst, dripstone, lush caves, powder snow, candles, glow squid, axolotls, goats, 1.18's music, and its title screen. Seeds match 1.18.2. 1.18 terrain works with OptiFine (shaders included) and Alfheim 1.6.

Superflat worlds keep exactly their preset's layers (no deepslate), and get copper ore, geodes and glow lichen only when the preset has decoration, as in 1.18.

Needs Backport Core 1.3.0 or newer.

Known limits in this beta:

*   The Caves & Cliffs world type turns itself off with Cubic Chunks installed, and says so in the log. The 1.17 content still works with it.

Back up worlds before trying it. Report bugs on the Discord.
