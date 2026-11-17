#!/bin/sh
# Compiles stream H's plain parts (the section layout, the coremod's patch classes) on their own and runs a tool class:
#   tools/height/run.sh HeightLayoutTest | AlfheimKeyTest
#   tools/height/run.sh PatchCheck mcp|srg <minecraft jar> [celeritas, nothirium, vintagefix, alfheim, eaglemixins, phosphor jar]
#   tools/height/run.sh PatchCheck compat <buildcraft jar> <kagic jar>   (the mod-compat patches, O3)
#   tools/height/run.sh DeobfJar <srg-mcp.srg> <release mod jar> <dev-named copy>
# Needs a JDK 8+ (JAVA_HOME) and the ForgeGradle caches (ASM, launchwrapper, log4j, the Forge jars).
set -e
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
out="$root/build/height-tools"
java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
g="$HOME/.gradle/caches"
# Windows' java takes Windows paths inside a class path (MSYS converts only whole arguments).
win() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }
find_jar() { win "$(find "$g/modules-2/files-2.1/$1" -name "$2" | head -1)"; }
forge="$g/minecraft/net/minecraftforge/forge/1.12.2-14.23.5.2847/stable/39"
sep=":"; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) sep=";";; esac
cp="$(find_jar org.ow2.asm/asm-debug-all asm-debug-all-5.2.jar)$sep$(find_jar net.minecraft/launchwrapper launchwrapper-1.12.jar)"
cp="$cp$sep$(find_jar org.apache.logging.log4j/log4j-api log4j-api-2.8.1.jar)$sep$(find_jar org.apache.logging.log4j/log4j-core log4j-core-2.8.1.jar)"
cp="$cp$sep$(find_jar com.google.code.findbugs/jsr305 jsr305-3.0.1.jar)$sep$(win "$forge/forgeSrc-1.12.2-14.23.5.2847.jar")"
# Mixin's API, for height.asm.StellarCoreMixinPlugin (any 0.8 copy in the cache; only compiled against).
cp="$cp$sep$(win "$(find "$g/modules-2/files-2.1/org.spongepowered/mixin" -name 'mixin-0.8*.jar' | head -1)")"
cp="$cp$sep$(find_jar com.google.code.gson/gson gson-2.8.0.jar)"
mkdir -p "$out"
src="$root/src/main/java/com/exiledradio/cavesandcliffsbackport/height"
"$javac" -nowarn -proc:none -encoding UTF-8 -cp "$cp" -d "$out" "$src"/HeightLayout.java "$src"/AlfheimKey.java "$src"/asm/*.java "$here"/*.java
main="$1"
shift
"$java" $JAVA_OPTS -cp "$(win "$out")$sep$cp" "$main" "$@"
