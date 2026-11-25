#!/bin/bash
# render-instances.sh <C&C client-test jar> <name>:<mods,...> ...
# Lean Cleanroom test instances for the renderer matrix (OptiFine, Celeritas, Nothirium and their combinations), one
# per argument, under G:/VBPTest/ccrender/<name>, junctioned into Prism as ccrender-<name>. Each gets Cleanroom from
# BASE (the Dregora Celeritas test copy: Cleanroom 0.6.13), Fugue and Red-Core, Backport Core, Fluidlogged API,
# the C&C jar, RTG and OTG, plus the jars named for it (keys of the JARS table below).
# The world type of the client test comes from minecraft/selftest-client-world (RTG, OTG, or empty for Caves & Cliffs);
# launch with tools/packs/launch-prism.sh ccrender-<name> G:/VBPTest/ccrender/<name>.
set -eu
CC=$1; shift
BASE="/g/Dregora Cleanroom (Celeritas + Demonica TEST)"
ROOT=/g/VBPTest/ccrender
PRISM="/c/Users/Radio/AppData/Roaming/PrismLauncher/instances"
JARDIR=/g/VBPTest/ccrender/jars
declare -A JARS=(
  [optifine]="OptiFine_1.12.2_HD_U_G5.jar"
  [celeritas]="01-celeritas-forge-mc12.2-2.5.0-dev.jar"
  [nothirium]="Nothirium-1.12.2-0.4.9-beta.jar"
)
for spec in "$@"; do
  name=${spec%%:*}; mods=${spec#*:}
  D=$ROOT/$name
  rm -rf "$D"
  mkdir -p "$D/minecraft/mods/1.12.2" "$D/minecraft/config" "$D/minecraft/shaderpacks"
  cp -r "$BASE/patches" "$D/"
  cp "$BASE/mmc-pack.json" "$D/"
  sed -e "s/^name=.*/name=ccrender $name TEST/" -e 's/^MaxMemAlloc=.*/MaxMemAlloc=6144/' \
      -e 's/^JvmArgs=.*/JvmArgs="-Dcavesandcliffsbackport.clienttest=height"/' -e 's/^OverrideJavaArgs=.*/OverrideJavaArgs=true/' \
      "$BASE/instance.cfg" > "$D/instance.cfg"
  grep -q '^JvmArgs=' "$D/instance.cfg" || echo 'JvmArgs="-Dcavesandcliffsbackport.clienttest=height"' >> "$D/instance.cfg"
  for j in "!Red-Core-MC-1.8-1.12-0.7.1.jar" "+Fugue-0.23.6.jar" BackportCore-1.4.9.jar \
           Fluidlogged-API-v3.3.3-mc1.12.2.jar RTG-1.12.2-6.1.0.0-snapshot.1.jar OpenTerrainGenerator-1.12.2-v9.7.jar; do
    cp "$JARDIR/$j" "$D/minecraft/mods/"
  done
  cp "$JARDIR/OTG-Core.jar" "$D/minecraft/mods/1.12.2/"
  cp "$CC" "$D/minecraft/mods/"
  if [ "$mods" != "-" ]; then
    for m in ${mods//,/ }; do cp "$JARDIR/${JARS[$m]}" "$D/minecraft/mods/"; done
  fi
  if [[ ",$mods," == *",optifine,"* ]]; then
    cp -r "$JARDIR/shaderpacks/." "$D/minecraft/shaderpacks/"
    cp "$JARDIR/optionsshaders.txt" "$D/minecraft/"
  fi
  # A small render distance and no pause on focus loss, as the client test expects.
  printf 'renderDistance:8\npauseOnLostFocus:false\nlang:en_us\n' > "$D/minecraft/options.txt"
  [ -e "$PRISM/ccrender-$name" ] || cmd //c mklink //J "$(cygpath -w "$PRISM/ccrender-$name")" "$(cygpath -w "$D")" > /dev/null
  echo "$name: $(ls "$D/minecraft/mods" | tr '\n' ' ')"
done
