# Caves & Cliffs Backport

All of 1.17 and 1.18 Caves & Cliffs on Minecraft 1.12.2: the Caves & Cliffs world type with 1.18 terrain from y -64 to 319, deepslate, copper, amethyst, dripstone, lush caves, powder snow, candles, glow squid, axolotls, goats and 1.18's music. Seeds match 1.18.2.

## Install

1. Download `CavesAndCliffsBackport-<version>.jar` and `BackportCore-<version>.jar` from [Releases](https://github.com/ExiledRadio/CavesCliffsBackport/releases).
2. Download [Fluidlogged API](https://www.curseforge.com/minecraft/mc-mods/fluidlogged-api) 3.3.3 or newer.
3. Put all three in your `mods` folder (Forge 1.12.2, 14.23.5.2847 or newer). Client and server both need them.
4. Pick the Caves & Cliffs world type when you create a world.

Back up your worlds before adding it to one you play in.

## Works with

OptiFine HD U G5 (shaders included), Alfheim 1.6, Celeritas, Nothirium and VintageFix. With Cubic Chunks the world type is a cubic one, with the same terrain; it turns itself off with Phosphor, and the 1.17 content still works there.

## Building

Needs Java 8. Put Backport Core's `-dev` jar in `deps/` (named `BackportCore-<core_version>-dev.jar`, see `gradle.properties`), then run `./gradlew build`. The jar is in `build/libs/`.

## Bugs

[Discord](https://discord.gg/kxQvMDJBTN)

Not affiliated with Mojang.
