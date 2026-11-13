#!/bin/sh
# Compares the terrain core's beard_box with vanilla 1.19.2's Beardifier (oracle119/BeardBoxCheck119.java), run from
# 1.19.2's Mojang-named client jar and its libraries with JDK 17.
#   sh tools/terrain/beardbox119.sh       (MC1192 and JDK17 point elsewhere if needed)
set -e
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
mc="${MC1192:-G:/MCDownloads/minecraft-1.19.2}"
jdk="${JDK17:-C:/Program Files/Java/jdk-17}"
out="$(cygpath -m "$root" 2>/dev/null || echo "$root")/build/beardbox119"
mkdir -p "$out"
cp="$mc/client-named.jar;$mc/libs/*"
"$jdk/bin/javac" -nowarn -cp "$cp" -d "$out" "$root"/src/main/java/com/exiledradio/cavesandcliffsbackport/terrain/core/*.java \
    "$here"/oracle119/BeardBoxCheck119.java
cd "$out"
"$jdk/bin/java" -cp "$out;$cp" BeardBoxCheck119
