![Backport Family](https://media.forgecdn.net/attachments/2271/405/hero-family-png.png)

[![Join the Discord](https://discord.com/api/guilds/1532942417618141204/widget.png?style=banner2)](https://discord.gg/kxQvMDJBTN)

**[Source](https://github.com/ExiledRadio/CavesCliffsBackport)**

[![Backport Core](https://media.forgecdn.net/attachments/2272/51/fcard-backport-core-png.png)](https://www.curseforge.com/minecraft/mc-mods/backport-core) [![Update Aquatic (1.13)](https://media.forgecdn.net/attachments/2272/52/fcard-update-aquatic-png.png)](https://www.curseforge.com/minecraft/mc-mods/update-aquatic-backport) [![Village & Pillage (1.14)](https://media.forgecdn.net/attachments/2272/53/fcard-village-pillage-png.png)](https://www.curseforge.com/minecraft/mc-mods/villager-backport) [![Buzzy Bees (1.15)](https://media.forgecdn.net/attachments/2272/54/fcard-buzzy-bees-png.png)](https://www.curseforge.com/minecraft/mc-mods/buzzy-bees-backport) [![Nether Update (1.16)](https://media.forgecdn.net/attachments/2272/55/fcard-nether-update-png.png)](https://www.curseforge.com/minecraft/mc-mods/nether-backport) [![Wild Update (1.19)](https://media.forgecdn.net/attachments/2272/57/fcard-wild-update-png.png)](https://www.curseforge.com/minecraft/mc-mods/wild-update-backport) [![Trails & Tales (1.20)](https://media.forgecdn.net/attachments/2272/58/fcard-trails-tales-png.png)](https://www.curseforge.com/minecraft/mc-mods/trails-tales-backport) [![Tricky Trials (1.21)](https://media.forgecdn.net/attachments/2272/59/fcard-tricky-trials-png.png)](https://www.curseforge.com/minecraft/mc-mods/trials-backport) [![Game Drops (26.x)](https://media.forgecdn.net/attachments/2272/60/fcard-game-drops-png.png)](https://www.curseforge.com/minecraft/mc-mods/game-drops-backport)

***

# Caves & Cliffs Backport

All of 1.17 and 1.18 Caves & Cliffs, on Minecraft 1.12.2. It needs Backport Core on the client and the server.

> 💬 [Join the Discord](https://discord.gg/kxQvMDJBTN) for bug reports and questions.

## 1.18 terrain

New worlds are Caves & Cliffs worlds, as 1.18's terrain is vanilla's default: 1.18's overworld, mountains up to y 319, caves down to bedrock at y -64, and 1.18's biomes. Seeds give the same terrain, biomes and structure spots as 1.18.2, so Chunkbase works.

The new biomes are meadows, groves, snowy slopes, jagged, frozen and stony peaks, lush caves and dripstone caves.

|                                                                                                                   |                                                                                                                      |
| ----------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| <img src="https://media.forgecdn.net/attachments/2205/647/cc-jagged-peaks-jpg.jpg" alt="Jagged peaks with goats"> | <img src="https://media.forgecdn.net/attachments/2205/649/cc-meadow-jpg.jpg" alt="A meadow below the mountains">     |
| <img src="https://media.forgecdn.net/attachments/2205/648/cc-lush-caves-jpg.jpg" alt="A lush cave with axolotls"> | <img src="https://media.forgecdn.net/attachments/2205/644/cc-dripstone-caves-lifted-jpg.jpg" alt="A dripstone cave"> |

Underground there are cheese, spaghetti and noodle caves, aquifers with their own water and lava levels, ore veins, and deepslate below y 0. The ground is shaped around villages and outposts as in 1.18.

Generation runs on worker threads, ahead of where you're going. It is faster than vanilla 1.12's.

Default is still under World Type, for 1.12's terrain, and a server keeps the type it names in server.properties. A config option turns the new default off. Existing worlds keep their 0-255 height. They still get the 1.17 blocks, ores, geodes and cave biomes in new chunks.

![A huge cave above a lava aquifer](https://media.forgecdn.net/attachments/2205/642/cc-deep-caves-jpg.jpg)

## Upgrading an old world

`/cavesandcliffs upgradeworld` turns a world made before into a Caves & Cliffs world, as 1.18 upgraded old worlds. An operator runs it once and confirms; it backs up level.dat, and the world is upgraded when it next loads.

Explored chunks keep everything from y 0 up. Below them come deepslate, caves, ores and aquifers down to y -64, and their bedrock floor moves there too. New chunks get 1.18's terrain and biomes, blended into the old ones at the border. No new structure starts in or reaches into an old chunk.

It works on Default worlds and can't be undone, so back up the world first.

|                                                                                        |                                                                                         |
| -------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| <img src="https://media.forgecdn.net/attachments/2268/908/cc-upgrade-badlands-river-jpg.jpg" alt="New 1.18 badlands beside an old 1.12 river valley"> | <img src="https://media.forgecdn.net/attachments/2268/909/cc-upgrade-taiga-badlands-jpg.jpg" alt="Old 1.12 taiga hills beside new 1.18 badlands"> |

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

## Other mods' ores

Every other mod's stone ore gets a deepslate form, as 1.18 gave vanilla's ores: the same drops, experience, Fortune, ore dictionary names and smelting, 1.5 times as hard, with the mod's own texture drawn on deepslate. In Caves & Cliffs worlds, other mods' ores also generate lower, down into the deepslate, as 1.18 moved vanilla's.

![Thermal Foundation, Immersive Engineering and NuclearCraft ores beside their deepslate forms](https://media.forgecdn.net/attachments/2268/934/cc-modded-deepslate-ores-jpg.jpg)

## Blocks

Deepslate and its cobbled, polished, brick and tile forms, deepslate ores, raw iron, gold and copper, tuff, calcite, dripstone and pointed dripstone, moss, azalea, cave vines with glow berries, big and small dripleaf, spore blossoms, hanging roots, rooted dirt, glow lichen, powder snow, candles in sixteen colors, smooth basalt, the light block, and lava and powder snow cauldrons.

## The rest of 1.17 and 1.18

You also get freezing in powder snow, 1.18's music and its own tracks in lush and dripstone caves, the Otherside music disc, 1.18's title screen and splashes, and 1.18's fog and cloud height.

Every feature can be turned off in Backport Core's settings screen, under Mod Options.

![Caves & Cliffs Backport's settings](https://media.forgecdn.net/attachments/2215/354/cc-settings-jpg.jpg)

## Compatibility

**Biomes O' Plenty, Defiled Lands, Traverse** and any mod with biomes in Forge's BiomeManager: in Caves & Cliffs worlds their biomes replace the closest 1.18 biome in some regions, as TerraBlender does in 1.18. They keep their own top blocks, trees, plants, ores and mobs. The `height` settings set how much of the world they take (at most half), the region size, and which mods take part. Without such a mod, the terrain is exactly 1.18's.

**OptiFine**: 1.18 terrain works with OptiFine HD U G5, with shaders (shadow pass included) and with Render Regions on or off.

**Quark**: its marble, limestone, jasper and slate, its underground biomes and its varied dungeons all generate in Caves & Cliffs worlds. 1.18's granite, diorite and andesite blobs step aside where Quark places its own. Quark's candles, speleothems and crystals live beside ours.

**Dynamic Trees**: its trees replace the 1.18 trees, tree by tree by the biome each stands in, so they don't grow twice.

**Cubic Chunks**: the Caves & Cliffs world type becomes a cubic world with the same 1.18 terrain, block for block, from y -64 to 319.

**Lighting and rendering**: Alfheim 1.6, Phosphor 0.2.6 and 0.2.7, Hesperus 0.2.9.1 to 0.2.9.3, Celeritas, Nothirium, VintageFix, Valkyrie 0.3-Dev-5, Entity Culling and RenderLib all light and draw y -64 to 319. With Nothirium and shaders together, flying fast can run Nothirium out of direct memory; a larger `-XX:MaxDirectMemorySize` helps.

**RLCraft and RLCraft Dregora**: tested in both. On Dregora's Cleanroom instances, use Fluidlogged API 3.3.3. A Caves & Cliffs world replaces Open Terrain Generator's terrain, so Dregora's own biomes and structures are only in Default worlds. Default worlds still get the 1.17 blocks, ores, geodes and cave biomes in new chunks.

**StellarCore**: works with any settings.

**[Alfheim Lighting Engine](https://www.curseforge.com/minecraft/mc-mods/alfheim-lighting-engine)** (optional, recommended): a faster lighting engine, so worlds generate faster, on Forge and Cleanroom. Use version 1.6: with any other version, the y -64 to 319 height turns itself off and the log says why.

**Fluidlogged API** (optional): candles, pointed dripstone, glow lichen, dripleaf, hanging roots, amethyst buds, lightning rods, light blocks, and the deepslate and copper stairs and slabs hold water. Without it they stay dry and everything else works.

**[Village & Pillage Backport](https://www.curseforge.com/minecraft/mc-mods/villager-backport)**: in Caves & Cliffs worlds, its villages and outposts generate on 1.18 terrain and the ground is shaped around them, and the terrain grows its bamboo, berry bushes and flowers. The composter takes the lush cave plants, foxes walk on powder snow, and the wandering trader sells pointed dripstone.

**[Update Aquatic Backport](https://www.curseforge.com/minecraft/mc-mods/update-aquatic-backport)**: in Caves & Cliffs worlds, its oceans, coral, kelp, shipwrecks and ocean ruins follow 1.18's biomes and placement. Axolotls hunt its fish and drowned and breed with a bucket of tropical fish, and drowned drop copper.

**[Buzzy Bees Backport](https://www.curseforge.com/minecraft/mc-mods/buzzy-bees-backport)**: honeycomb waxes copper and makes candles, goats can't jump off honey blocks, and trees in Caves & Cliffs worlds get bee nests.

**[Nether Update Backport](https://www.curseforge.com/minecraft/mc-mods/nether-backport)**: basalt smelts into smooth basalt, striders freeze in powder snow, mineshafts hang chains, and a doused soul campfire keeps its food. In Caves & Cliffs worlds, ruined portals follow 1.18's placement.

**[Wild Update Backport](https://www.curseforge.com/minecraft/mc-mods/wild-update-backport)**: installed when a world is created, it adds mangrove swamps and the deep dark, and this mod's goats are used. Its allays duplicate with amethyst, water turns rooted dirt into mud, mud under pointed dripstone turns into clay, and sculk spreads over tuff, calcite, dripstone and deepslate.

**[Trails & Tales Backport](https://www.curseforge.com/minecraft/mc-mods/trails-tales-backport)**: installed when a world is created, it adds cherry groves. Desert wells get suspicious sand, the brush and copper trim use this mod's copper, and sniffer eggs hatch twice as fast on moss.

**[Tricky Trials Backport](https://www.curseforge.com/minecraft/mc-mods/trials-backport)**: its copper and tuff are this mod's. In Caves & Cliffs worlds, trial chambers generate at their full depth.

**[Game Drops Backport](https://www.curseforge.com/minecraft/mc-mods/game-drops-backport)**: installed when a world is created, it adds the dappled forest and the sulfur caves. Baby goats and axolotls get 26.1's baby models.

## Requirements

Minecraft 1.12.2 with Forge 14.23.5.2847 or newer, and [Backport Core](https://www.curseforge.com/minecraft/mc-mods/backport-core) 1.4.7 or newer. The CurseForge app installs Backport Core with it. [Fluidlogged API](https://www.curseforge.com/minecraft/mc-mods/fluidlogged-api) 3.3.3 or newer is optional, for waterlogged blocks. [Alfheim](https://www.curseforge.com/minecraft/mc-mods/alfheim-lighting-engine) 1.6 is optional and recommended, for faster world generation.

Removing this mod from a Caves & Cliffs world keeps everything from y 0 to 255. Everything below and above is lost, and the bottom of the world is open.

***

Also by me: [Village & Pillage Backport](https://www.curseforge.com/minecraft/mc-mods/villager-backport), all of 1.14 on 1.12.2, [Update Aquatic Backport](https://www.curseforge.com/minecraft/mc-mods/update-aquatic-backport), all of 1.13 on 1.12.2, [Buzzy Bees Backport](https://www.curseforge.com/minecraft/mc-mods/buzzy-bees-backport), all of 1.15 on 1.12.2, [Nether Update Backport](https://www.curseforge.com/minecraft/mc-mods/nether-backport), all of 1.16 on 1.12.2, [Wild Update Backport](https://www.curseforge.com/minecraft/mc-mods/wild-update-backport), all of 1.19 on 1.12.2, [Trails & Tales Backport](https://www.curseforge.com/minecraft/mc-mods/trails-tales-backport), all of 1.20 on 1.12.2, [Tricky Trials Backport](https://www.curseforge.com/minecraft/mc-mods/trials-backport), all of 1.21 on 1.12.2, [Game Drops Backport](https://www.curseforge.com/minecraft/mc-mods/game-drops-backport), the 26.x game drops on 1.12.2, and [Enchantment Recipes](https://www.curseforge.com/minecraft/mc-mods/rlcraft-enchantment-recipes), which lets you craft enchanted books from mod materials.

## Credits

**Original content: [Minecraft](https://www.minecraft.net), by [Mojang Studios](https://www.minecraft.net/en-us/about-minecraft).** Every feature this mod adds is from a later version of Minecraft. The textures, models, sounds, music and translations are Mojang's and remain theirs. The features' names and designs are Mojang's too.

**How this mod differs from the original:** Minecraft's newer versions don't run on 1.12.2. This mod is a Forge mod for Minecraft 1.12.2 that reimplements those features on the older game, matching the newer versions as closely as it can. It is not a copy or port of another mod. Where 1.12.2 can't do something the same way, the description above says so.

This is an unofficial fan project, made under Mojang's [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines). Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.

Source: [github.com/ExiledRadio/CavesCliffsBackport](https://github.com/ExiledRadio/CavesCliffsBackport)
