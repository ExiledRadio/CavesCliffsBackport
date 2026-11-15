# 1.0.0

First full release. Since the beta:

*   **New worlds spawn where 1.18.2 puts the spawn** for the seed, so a seed's start matches Chunkbase. Worlds made with the beta keep their spawn.
*   **Fixed a server crash with EagleMixins and NuclearCraft** when an entity was above y 192 in a Caves & Cliffs world.
*   **Fixed features cut off at the edge of the explored area after a crash.** If the server crashed or was killed, trees, ores and other features crossing into ungenerated chunks could be missing there. They are now kept through a crash.

Needs Backport Core 1.3.0 or newer.

# 0.1.0 (beta)

First beta. Everything from 1.17 and 1.18 is in: the Caves & Cliffs world type with 1.18 terrain from y -64 to 319, deepslate, copper, amethyst, dripstone, lush caves, powder snow, candles, glow squid, axolotls, goats, 1.18's music, and its title screen. Seeds match 1.18.2. 1.18 terrain works with OptiFine (shaders included) and Alfheim 1.6.

Superflat worlds keep exactly their preset's layers (no deepslate), and get copper ore, geodes and glow lichen only when the preset has decoration, as in 1.18.

Needs Backport Core 1.3.0 or newer.

Known limits in this beta:

*   The Caves & Cliffs world type turns itself off with Phosphor or Cubic Chunks installed, and says so in the log. The 1.17 content still works with them.

Back up worlds before trying it. Report bugs on the Discord.
