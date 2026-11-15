# Caves & Cliffs Backport

All of 1.17 and 1.18 Caves & Cliffs, on Minecraft 1.12.2. It needs Backport Core on the client and the server.

> 💬 [Join the Discord](https://discord.gg/kxQvMDJBTN) for bug reports and questions.

## 1.18 terrain

New worlds are Caves & Cliffs worlds, as 1.18's terrain is vanilla's default: 1.18's overworld, mountains up to y 319, caves down to bedrock at y -64, and 1.18's biomes. Seeds give the same terrain, biomes and structure spots as 1.18.2, so Chunkbase works.

The new biomes are meadows, groves, snowy slopes, jagged, frozen and stony peaks, lush caves and dripstone caves.

| | |
| --- | --- |
| ![Jagged peaks with goats](PENDING:cc-jagged-peaks) | ![A meadow below the mountains](PENDING:cc-meadow) |
| ![A lush cave with axolotls](PENDING:cc-lush-caves) | ![A dripstone cave](PENDING:cc-dripstone-caves) |

Underground there are cheese, spaghetti and noodle caves, aquifers with their own water and lava levels, ore veins, and deepslate below y 0. The ground is shaped around villages and outposts as in 1.18.

Generation runs on worker threads, ahead of where you're going. It is faster than vanilla 1.12's.

Default is still under World Type, for 1.12's terrain, and a server keeps the type it names in server.properties. A config option turns the new default off. Existing worlds keep their 0-255 height. They still get the 1.17 blocks, ores, geodes and cave biomes in new chunks.

![A huge cave above a lava aquifer](PENDING:cc-deep-caves)

## Mobs

Axolotls live in lush cave water in four colors. Breeding rarely gives a blue one. They hunt fish, squid and drowned, and play dead to heal. Finish off a mob one is fighting and you get Regeneration. Carry them in a bucket and breed them with tropical fish.

Glow squid glow in deep, dark water. Their glow ink sacs make glow item frames and glowing sign text.

Goats live on the mountains, jump up to five blocks and ram anything that stands still. One in fifty is a screaming goat.

![Axolotls in a lush cave pool](PENDING:cc-axolotls)

![Glow squid in a flooded cave](PENDING:cc-glow-squid)

## Amethyst

Amethyst geodes are deep underground. Budding amethyst grows clusters you can mine for shards. Shards make tinted glass and the spyglass.

![Inside an amethyst geode](PENDING:cc-amethyst-geode)

## Copper

Copper ore drops raw copper. Copper blocks age from orange to green. Wax them with honeycomb to keep the color, or scrape them back with an axe. Lightning rods catch lightning, and a strike cleans the copper around it.

![Copper blocks in every stage of aging](PENDING:cc-copper)

## Blocks

Deepslate and its cobbled, polished, brick and tile forms, deepslate ores, raw iron, gold and copper, tuff, calcite, dripstone and pointed dripstone, moss, azalea, cave vines with glow berries, big and small dripleaf, spore blossoms, hanging roots, rooted dirt, glow lichen, powder snow, candles in sixteen colors, smooth basalt, the light block, and lava and powder snow cauldrons.

## The rest of 1.17 and 1.18

You also get freezing in powder snow, 1.18's music and its own tracks in lush and dripstone caves, the Otherside music disc, 1.18's title screen and splashes, and 1.18's fog and cloud height.

Every feature can be turned off in Backport Core's settings screen, under Mod Options.

![Caves & Cliffs Backport's settings](PENDING:cc-settings)

## Works with

With Update Aquatic Backport, axolotls hunt its fish and drowned drop copper. With Buzzy Bees Backport, honeycomb waxes copper and makes candles. With Wild Update Backport or Trails & Tales Backport installed when a world is created, it also gets mangrove swamps and the deep dark, or cherry groves, where 1.19 and 1.20 put them.

1.18 terrain works with OptiFine (shaders included), Alfheim 1.6, Phosphor 0.2.6 and 0.2.7, Hesperus, Celeritas, Nothirium and VintageFix. It turns itself off with Cubic Chunks and says so in the log; the 1.17 content still works with it.

## Requirements

Minecraft 1.12.2 with Forge 14.23.5.2847 or newer, and [Backport Core](https://www.curseforge.com/minecraft/mc-mods/backport-core) 1.3.0 or newer. The CurseForge app installs Backport Core and Fluidlogged API with it.

Removing this mod from a Caves & Cliffs world keeps everything from y 0 to 255. Everything below and above is lost, and the bottom of the world is open.

***

Also by me: [Village & Pillage Backport](https://www.curseforge.com/minecraft/mc-mods/villager-backport), all of 1.14 on 1.12.2, [Update Aquatic Backport](https://www.curseforge.com/minecraft/mc-mods/update-aquatic-backport), all of 1.13 on 1.12.2, [Buzzy Bees Backport](https://www.curseforge.com/minecraft/mc-mods/buzzy-bees-backport), all of 1.15 on 1.12.2, [Nether Update Backport](https://www.curseforge.com/minecraft/mc-mods/nether-update-backport), all of 1.16 on 1.12.2, and [Enchantment Recipes](https://www.curseforge.com/minecraft/mc-mods/rlcraft-enchantment-recipes), which lets you craft enchanted books from mod materials.

Not affiliated with Mojang. Source: [github.com/ExiledRadio/CavesCliffsBackport](https://github.com/ExiledRadio/CavesCliffsBackport)
