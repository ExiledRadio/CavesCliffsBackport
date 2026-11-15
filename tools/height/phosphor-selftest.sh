#!/bin/sh
# Extended height with Phosphor (or Hesperus) on the dev server (docs/streams/height.md, "Phosphor"):
#   sh tools/height/phosphor-selftest.sh <dev-named phosphor jar> [extra mod jars...]
# The dev run does not remap mod jars and Mixin there does not rename a mixin's SRG members, so give it a copy with
# MCP names: tools/height/run.sh DeobfJar <srg-mcp.srg from the FG2 MCP cache> <release jar> <dev jar>.
# MixinBooter does not load from eclipse/mods, so Hesperus is tested on a real server instead (height.md, "Tested").
# Puts the jars in eclipse/mods, runs a fresh Caves & Cliffs world with -Pselftest=height,phosphorlight1, restarts it
# with -Pselftest=phosphorlight2 (the light read back from the save), then a fresh Default world twice with
# -Pselftest=phosphordump, once with extended height on and once with height.enabled=false: the two dumps must be the
# same file. Takes the jars out of eclipse/mods and puts level-type and the config back at the end.
# Phosphor 0.2.6/0.2.7 bring their own Mixin.
# On Windows the Gradle runs are pinned to the E-cores (the P-cores miscompute under load).
set -e
root="$(cd "$(dirname "$0")/../.." && pwd)"
props="$root/eclipse/server.properties"
cfg="$root/eclipse/config/cavesandcliffsbackport.cfg"
level="$(sed -n 's/^level-name=//p' "$props")"
level="${level:-world}"
mkdir -p "$root/eclipse/mods"
added=""
for j in "$@"; do
    cp "$j" "$root/eclipse/mods/"
    added="$added $(basename "$j")"
done
out="$root/build/phosphor-selftest"
mkdir -p "$out"

run() {
    # $1 selftest names, $2 log name
    status=0
    case "$(uname -s)" in
        MINGW*|MSYS*|CYGWIN*)
            w="$(cygpath -w "$root")"
            (cd "$root" && cmd //c start '""' //D "$w" //B //WAIT //AFFINITY FFFF0000 "$w\\gradlew.bat" --no-daemon \
                runServer -Pselftest="$1" -PdeployDir= -PcoreDeployDir= > "$out/$2.log" 2>&1) || status=$?;;
        *)
            (cd "$root" && ./gradlew --no-daemon runServer -Pselftest="$1" -PdeployDir= -PcoreDeployDir= \
                > "$out/$2.log" 2>&1) || status=$?;;
    esac
    # cmd's start answers 0 whatever Gradle did.
    grep -q "BUILD SUCCESSFUL" "$out/$2.log" || status=1
    cp "$root/eclipse/selftest-report.txt" "$out/$2-report.txt" 2>/dev/null || true
    echo "$2: exit $status, $(grep -o 'SELFTEST DONE.*' "$root/eclipse/logs/latest.log" 2>/dev/null | tail -1)"
}

cleanup() {
    for f in $added; do rm -f "$root/eclipse/mods/$f"; done
    sed -i 's/^level-type=.*/level-type=DEFAULT/' "$props"
    if [ -f "$cfg" ]; then sed -i '/^ *height {/,/^ *}/ s/B:enabled=false/B:enabled=true/' "$cfg"; fi
}
trap cleanup EXIT

rm -rf "$root/eclipse/$level" "$root/eclipse/phosphor-light.txt" "$root/eclipse/phosphor-dump.txt"
sed -i 's/^level-type=.*/level-type=cavesandcliffs/' "$props"
run "height,phosphorlight1" light1
run "phosphorlight2" light2

rm -rf "$root/eclipse/$level"
sed -i 's/^level-type=.*/level-type=DEFAULT/' "$props"
run "phosphordump" dump-on
cp "$root/eclipse/phosphor-dump.txt" "$out/dump-on.txt" 2>/dev/null || true
rm -rf "$root/eclipse/$level"
if [ -f "$cfg" ]; then sed -i '/^ *height {/,/^ *}/ s/B:enabled=true/B:enabled=false/' "$cfg"; fi
run "phosphordump" dump-off
cp "$root/eclipse/phosphor-dump.txt" "$out/dump-off.txt" 2>/dev/null || true
if cmp -s "$out/dump-on.txt" "$out/dump-off.txt"; then echo "dump: same file"; else echo "dump: DIFFERENT"; fi
rm -rf "$root/eclipse/$level"
