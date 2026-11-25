#!/bin/sh
# Extended height with Roughly Enough IDs / Just Enough IDs (height.IdsCompat) on a real Forge server, release-style
# (SRG) jars:
#   sh tools/height/ids-selftest.sh <server dir> <test build of this mod> <Backport Core jar> <MixinBooter jar> [REID/JEID jars...]
# The mod jar is a test build (./gradlew build -Pselftest=ids, the self-tests inside). For "none" and then each
# REID/JEID jar: a fresh Caves & Cliffs world (seed 4242) runs -Dcavesandcliffsbackport.selftest=ids,height, the
# server stops and saves, then the same world runs ids2 (the chunks read back from disk). Every run's ids-dump.txt (the
# natural chunks' blocks and biomes) must be the same as the run without REID/JEID. MixinBooter goes in for every
# REID/JEID jar but Just Enough IDs 1.0.3, which brings its own Mixin.
# <server dir> holds a Forge 1.12.2 server (forge-*.jar, minecraft_server.1.12.2.jar, libraries/, eula.txt,
# server.properties); its mods/ and world are replaced. The server is stopped by the self-test runner, never killed.
set -u
dir=$1; mod=$2; core=$3; booter=$4
shift 4
forge=$(ls "$dir"/forge-1.12.2-*.jar | head -n 1)
java=${JAVA:-java}
[ -x /e/MincraftModding/JDK8/bin/java.exe ] && java=${JAVA:-/e/MincraftModding/JDK8/bin/java}
out="$dir/ids-results"
mkdir -p "$out"
sed -i 's/^level-type=.*/level-type=cavesandcliffs/; s/^level-seed=.*/level-seed=4242/' "$dir/server.properties"
grep -q '^level-seed=' "$dir/server.properties" || echo 'level-seed=4242' >> "$dir/server.properties"

run() {
    # $1 label, $2 selftest names
    rm -f "$dir/selftest-report.txt"
    (cd "$dir" && "$java" -Xmx3G -Dcavesandcliffsbackport.selftest="$2" -jar "$(basename "$forge")" nogui \
        > "$out/$1.log" 2>&1 < /dev/null)
    cp "$dir/selftest-report.txt" "$out/$1-report.txt" 2>/dev/null || true
    echo "$1: $(grep -o 'SELFTEST DONE.*' "$out/$1.log" | tail -1)"
    grep -E 'SELFTEST FAIL|Extended height (is|\(y -64 to 319\) is OFF)|sections below y 0 and above y 255 save' "$out/$1.log" | sed 's/^/    /' | head -20
}

status=0
for jar in none "$@"; do
    rm -rf "$dir/mods" "$dir/world"
    mkdir -p "$dir/mods"
    cp "$mod" "$core" "$dir/mods/"
    label=none
    if [ "$jar" != none ]; then
        cp "$jar" "$dir/mods/"
        label=$(basename "$jar" .jar)
        case "$label" in
            JustEnoughIDs-1.0.3*) ;;
            *) cp "$booter" "$dir/mods/!$(basename "$booter")" ;;
        esac
    fi
    run "$label-1" "ids,height"
    cp "$dir/ids-dump.txt" "$out/$label-dump.txt" 2>/dev/null || true
    run "$label-2" "ids2"
    if [ "$label" != none ]; then
        if cmp -s "$out/none-dump.txt" "$out/$label-dump.txt"; then echo "    dump: same as without REID/JEID"; else echo "    dump: DIFFERENT"; status=1; fi
    fi
    grep -q 'fail=0' "$out/$label-1.log" && grep -q 'fail=0' "$out/$label-2.log" || status=1
done
exit $status
