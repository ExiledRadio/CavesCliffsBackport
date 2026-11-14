# Unreleased

*   Fixed: in a Caves & Cliffs world, a Nether portal could not be lit below y 0 (unless lit from its bottom row), and
    a trip back from the Nether never arrived at a portal below 0. Portals now light, link and are built anywhere
    from y -64 to 319, as in 1.18.

# 0.1.0 (beta)

First beta. Everything from 1.17 and 1.18 is in: the Caves & Cliffs world type with 1.18 terrain from y -64 to 319, deepslate, copper, amethyst, dripstone, lush caves, powder snow, candles, glow squid, axolotls, goats, 1.18's music, and its title screen. Seeds match 1.18.2. 1.18 terrain works with OptiFine (shaders included) and Alfheim 1.6.

Needs Backport Core 1.3.0 or newer.

Known limits in this beta:

*   The Caves & Cliffs world type turns itself off with Phosphor or Cubic Chunks installed, and says so in the log. The 1.17 content still works with them.

Back up worlds before trying it. Report bugs on the Discord.
