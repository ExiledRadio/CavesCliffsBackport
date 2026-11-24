#!/bin/sh
# Texture families of modded ores (stream O1): compiles the game's own texture code (OrePainter, OreTextures: plain
# java.awt) with the tool and runs it on mod jars.
#   tools/oretextures/run.sh <out dir> <minecraft 1.12.2 jar> <mod jar>...
# Writes <modid>-sheet.png (original / before / after / drawn as ore), family-<n>-mask.png and report.txt.
set -e
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
build="$root/build/oretextures-tool"
java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
win() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }
src="$root/src/main/java/com/exiledradio/cavesandcliffsbackport/deepslate/client"
mkdir -p "$build"
"$javac" -nowarn -encoding UTF-8 -d "$(win "$build")" "$(win "$src/OrePainter.java")" "$(win "$src/OreTextures.java")" "$(win "$here/OreFamilyTool.java")"
out="$1"; mc="$2"; shift 2
args=""
for j in "$@"; do args="$args $(win "$j")"; done
# shellcheck disable=SC2086
"$java" -cp "$(win "$build")" com.exiledradio.cavesandcliffsbackport.deepslate.client.OreFamilyTool "$(win "$out")" "$(win "$mc")" \
    "$(win "$root/src/main/resources/assets/cavesandcliffsbackport/textures/block/deepslate.png")" $args
