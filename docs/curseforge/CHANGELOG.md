# 1.0.0

First full release. Since the beta:

*   **New worlds are Caves & Cliffs worlds**: Create World starts on the Caves & Cliffs world type, as 1.18's terrain is vanilla's default, and a new server uses it unless server.properties names another `level-type`. Default is still under World Type for 1.12's terrain, and existing worlds keep theirs. Setting `height.defaultWorldType` turns this off.
*   **New worlds spawn where 1.18.2 puts the spawn** for the seed, so a seed's start matches Chunkbase. Worlds made with the beta keep their spawn.
*   **Fixed a server crash with EagleMixins and NuclearCraft** when an entity was above y 192 in a Caves & Cliffs world.
*   **Fixed features cut off at the edge of the explored area after a crash.** If the server crashed or was killed, trees, ores and other features crossing into ungenerated chunks could be missing there. They are now kept through a crash.
*   **Works with Phosphor** (0.2.6 and 0.2.7) **and Hesperus** (0.2.9.1 to 0.2.9.3): the Caves & Cliffs world type no longer turns itself off with them, and their lighting covers y -64 to 319.
*   **Works with StellarCore** with any settings: its `WorldBlockPosJudgement` option no longer keeps Caves & Cliffs worlds at y 0-255.
*   **Works with Cubic Chunks**: with it installed, the Caves & Cliffs world type is a cubic world with the same 1.18 terrain, block for block, from y -64 to 319.
*   **Pale gardens** in new Caves & Cliffs worlds with Tricky Trials Backport, where 1.21.5 and later put them, so seeds match Chunkbase for 1.21.5+. A setting places them as 1.21.4 does instead.
*   **The newer versions' changes to 1.18's features**: with Wild Update or Trails & Tales installed, trees, vines, disks and the rest decorate as 1.19 and 1.20 do. Mangrove leaves that grow into water hold it.
*   **Cave and mountain music follows 1.18's biomes**: lush caves, dripstone caves and the mountain biomes play their music where the terrain really has them.
*   **Water opened by 1.12's structures flows**: an aquifer a mineshaft or another mod's structure cuts into now spills, as in 1.18.
*   **Fixed goats now and then failing a long jump** they should make.
*   **Glow lichen in Default worlds is as common as in 1.18**: it was about four times as dense on cave walls, and grew on floors and dirt. It now generates as 1.18.2's feature does, on ceilings and walls of stone, deepslate, tuff, calcite and dripstone. The `lush.lichenTries` setting is gone.
*   **Other mods' biomes in Caves & Cliffs worlds**: Biomes O' Plenty, Defiled Lands, Traverse and any mod's biomes in Forge's BiomeManager take the place of the closest 1.18 biome in some regions, with their own top blocks, trees, plants and mobs, as TerraBlender places them in 1.18. Without such a mod the terrain is exactly 1.18's. Settings in the `height` category tune or turn it off. Fixed Biomes O' Plenty's grove and meadow standing in for 1.18's grove and meadow.

Needs Backport Core 1.3.0 or newer. Bamboo jungles grow bamboo with Village & Pillage Backport, and warm oceans grow coral reefs with Update Aquatic Backport (without it, terracotta stands in).

# 0.1.0 (beta)

First beta. Everything from 1.17 and 1.18 is in: the Caves & Cliffs world type with 1.18 terrain from y -64 to 319, deepslate, copper, amethyst, dripstone, lush caves, powder snow, candles, glow squid, axolotls, goats, 1.18's music, and its title screen. Seeds match 1.18.2. 1.18 terrain works with OptiFine (shaders included) and Alfheim 1.6.

Superflat worlds keep exactly their preset's layers (no deepslate), and get copper ore, geodes and glow lichen only when the preset has decoration, as in 1.18.

Needs Backport Core 1.3.0 or newer.

Known limits in this beta:

*   The Caves & Cliffs world type turns itself off with Cubic Chunks installed, and says so in the log. The 1.17 content still works with it.

Back up worlds before trying it. Report bugs on the Discord.
