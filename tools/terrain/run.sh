#!/bin/sh
# Compiles the terrain core on its own (no Minecraft needed) and runs a tool class against it.
# Usage: tools/terrain/run.sh <MainClass> [args...]     (JAVA_HOME may point at any JDK 8+, JAVA_OPTS is passed on)
set -e
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
out="$root/build/terrain-tools"
java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
mkdir -p "$out"
"$javac" -nowarn -d "$out" "$root"/src/main/java/com/exiledradio/cavesandcliffsbackport/terrain/core/*.java "$here"/*.java
main="$1"
shift
"$java" $JAVA_OPTS -cp "$out" "$main" "$@"
