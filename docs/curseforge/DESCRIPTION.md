# Caves & Cliffs Backport

All of 1.17 and 1.18 Caves & Cliffs, on Minecraft 1.12.2. It needs Backport Core on the client and the server.

> 💬 [Join the Discord](https://discord.gg/kxQvMDJBTN) for bug reports and questions.

## 1.18 terrain

New worlds are Caves & Cliffs worlds, as 1.18's terrain is vanilla's default: 1.18's overworld, mountains up to y 319, caves down to bedrock at y -64, and 1.18's biomes. Seeds give the same terrain, biomes and structure spots as 1.18.2, so Chunkbase works.

The new biomes are meadows, groves, snowy slopes, jagged, frozen and stony peaks, lush caves and dripstone caves.

| | |
| --- | --- |
| ![Jagged peaks with goats](https://media.forgecdn.net/attachments/2205/647/cc-jagged-peaks-jpg.jpg) | ![A meadow below the mountains](https://media.forgecdn.net/attachments/2205/649/cc-meadow-jpg.jpg) |
| ![A lush cave with axolotls](https://media.forgecdn.net/attachments/2205/648/cc-lush-caves-jpg.jpg) | ![A dripstone cave](https://media.forgecdn.net/attachments/2205/644/cc-dripstone-caves-lifted-jpg.jpg) |

Underground there are cheese, spaghetti and noodle caves, aquifers with their own water and lava levels, ore veins, and deepslate below y 0. The ground is shaped around villages and outposts as in 1.18.

Generation runs on worker threads, ahead of where you're going. It is faster than vanilla 1.12's.

Default is still under World Type, for 1.12's terrain, and a server keeps the type it names in server.properties. A config option turns the new default off. Existing worlds keep their 0-255 height. They still get the 1.17 blocks, ores, geodes and cave biomes in new chunks.

![A huge cave above a lava aquifer](https://media.forgecdn.net/attachments/2205/642/cc-deep-caves-jpg.jpg)

## Mobs

Axolotls live in lush cave water in four colors. Breeding rarely gives a blue one. They hunt fish, squid and drowned, and play dead to heal. Finish off a mob one is fighting and you get Regeneration. Carry them in a bucket and breed them with tropical fish.

Glow squid glow in deep, dark water. Their glow ink sacs make glow item frames and glowing sign text.

Goats live on the mountains, jump up to five blocks and ram anything that stands still. One in fifty is a screaming goat.

![Axolotls in a lush cave pool](https://media.forgecdn.net/attachments/2205/640/cc-axolotls-jpg.jpg)

![Glow squid in a flooded cave](https://media.forgecdn.net/attachments/2205/646/cc-glow-squid-jpg.jpg)

## Amethyst

Amethyst geodes are deep underground. Budding amethyst grows clusters you can mine for shards. Shards make tinted glass and the spyglass.

![Inside an amethyst geode](https://media.forgecdn.net/attachments/2205/639/cc-amethyst-geode-jpg.jpg)

## Copper

Copper ore drops raw copper. Copper blocks age from orange to green. Wax them with honeycomb to keep the color, or scrape them back with an axe. Lightning rods catch lightning, and a strike cleans the copper around it.

![Copper blocks in every stage of aging](https://media.forgecdn.net/attachments/2205/641/cc-copper-jpg.jpg)

## Blocks

Deepslate and its cobbled, polished, brick and tile forms, deepslate ores, raw iron, gold and copper, tuff, calcite, dripstone and pointed dripstone, moss, azalea, cave vines with glow berries, big and small dripleaf, spore blossoms, hanging roots, rooted dirt, glow lichen, powder snow, candles in sixteen colors, smooth basalt, the light block, and lava and powder snow cauldrons.

## The rest of 1.17 and 1.18

You also get freezing in powder snow, 1.18's music and its own tracks in lush and dripstone caves, the Otherside music disc, 1.18's title screen and splashes, and 1.18's fog and cloud height.

Every feature can be turned off in Backport Core's settings screen, under Mod Options.

![Caves & Cliffs Backport's settings](https://media.forgecdn.net/attachments/2205/650/cc-settings-jpg.jpg)

## Compatibility

**Biomes O' Plenty, Defiled Lands, Traverse** and any mod with biomes in Forge's BiomeManager: in Caves & Cliffs worlds their biomes replace the closest 1.18 biome in some regions, as TerraBlender does in 1.18. They keep their own top blocks, trees, plants, ores and mobs. The `height` settings set how much of the world they take (at most half), the region size, and which mods take part. Without such a mod, the terrain is exactly 1.18's.

**OptiFine**: 1.18 terrain works with OptiFine HD U G5, with shaders (shadow pass included) and with Render Regions on or off.

**Quark**: its marble, limestone, jasper and slate, its underground biomes and its varied dungeons all generate in Caves & Cliffs worlds. 1.18's granite, diorite and andesite blobs step aside where Quark places its own. Quark's candles, speleothems and crystals live beside ours.

**Dynamic Trees**: its trees replace the 1.18 trees, so they don't grow twice.

**Cubic Chunks**: the Caves & Cliffs world type becomes a cubic world with the same 1.18 terrain, block for block, from y -64 to 319.

**Lighting and rendering**: Alfheim 1.6, Phosphor 0.2.6 and 0.2.7, Hesperus 0.2.9.1 to 0.2.9.3, Celeritas, Nothirium, VintageFix, Entity Culling and RenderLib all light and draw y -64 to 319. With Nothirium and shaders together, flying fast can run Nothirium out of direct memory; a larger `-XX:MaxDirectMemorySize` helps.

**RLCraft and RLCraft Dregora**: tested in both. On Dregora's Cleanroom instances, use Fluidlogged API 3.3.3 and set StellarCore's `B:WorldBlockPosJudgement=false`. A Caves & Cliffs world replaces Open Terrain Generator's terrain, so Dregora's own biomes and structures are only in Default worlds. Default worlds still get the 1.17 blocks, ores, geodes and cave biomes in new chunks.

**Fluidlogged API** (optional): candles, pointed dripstone, glow lichen, dripleaf, hanging roots, amethyst buds, lightning rods, light blocks, and the deepslate and copper stairs and slabs hold water. Without it they stay dry and everything else works.

**The other backports**: with Update Aquatic Backport, axolotls hunt its fish and drowned drop copper. With Buzzy Bees Backport, honeycomb waxes copper and makes candles. With Wild Update Backport or Trails & Tales Backport installed when a world is created, it also gets mangrove swamps and the deep dark, or cherry groves, where 1.19 and 1.20 put them. Bamboo comes from Village & Pillage Backport and coral from Update Aquatic Backport.

## Requirements

Minecraft 1.12.2 with Forge 14.23.5.2847 or newer, and [Backport Core](https://www.curseforge.com/minecraft/mc-mods/backport-core) 1.3.0 or newer. The CurseForge app installs Backport Core with it. [Fluidlogged API](https://www.curseforge.com/minecraft/mc-mods/fluidlogged-api) 3.3.3 or newer is optional, for waterlogged blocks.

Removing this mod from a Caves & Cliffs world keeps everything from y 0 to 255. Everything below and above is lost, and the bottom of the world is open.

***

Also by me: [Village & Pillage Backport](https://www.curseforge.com/minecraft/mc-mods/villager-backport), all of 1.14 on 1.12.2, [Update Aquatic Backport](https://www.curseforge.com/minecraft/mc-mods/update-aquatic-backport), all of 1.13 on 1.12.2, [Buzzy Bees Backport](https://www.curseforge.com/minecraft/mc-mods/buzzy-bees-backport), all of 1.15 on 1.12.2, [Nether Update Backport](https://www.curseforge.com/minecraft/mc-mods/nether-update-backport), all of 1.16 on 1.12.2, and [Enchantment Recipes](https://www.curseforge.com/minecraft/mc-mods/rlcraft-enchantment-recipes), which lets you craft enchanted books from mod materials.

Not affiliated with Mojang. Source: [github.com/ExiledRadio/CavesCliffsBackport](https://github.com/ExiledRadio/CavesCliffsBackport)
