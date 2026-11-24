#!/bin/sh
# Another mod's world type from y -64 to 319 (HybridSelfTest): four servers on a real dedicated Forge server with that
# mod installed (RTG, OTG).
#   1. fresh world, hybridnew:    extended, its own terrain from y 0 up exactly, 1.18's below
#   2. same world,  hybridreload: after a restart every chunk loads as saved, none upgraded twice
#   3. fresh world, hybridold1:   the type taken out of height.otherWorldTypes (y 0-255), then /cavesandcliffs upgradeworld
#   4. same world,  hybridold2:   the type back in: old chunks upgraded below 0, every block from y 0 up kept
#
# Usage: tools/height/hybrid.sh <server dir> <level-type> <jar>...
# The jars go in mods/ beside what is in <server dir>/mods-base (the other mod, its core mods in mods-base/1.12.2,
# Backport Core, Fluidlogged API). Build this mod's jar with the tests in first:
#   ./gradlew build -Pselftest=hybridnew -PdeployDir=     (then rebuild the release jar without -Pselftest)
# Reports: <server dir>/selftest-report-<phase>.txt. Exit 0 when every phase has fail=0.
set -u
if [ $# -lt 3 ]; then
    echo "usage: $0 <server dir> <level-type> <jar>..." >&2
    exit 2
fi
dir=$1
type=$2
shift 2
forge=$(ls "$dir"/forge-1.12.2-*.jar 2>/dev/null | head -n 1)
[ -n "$forge" ] || { echo "no forge-1.12.2-*.jar in $dir" >&2; exit 2; }
java=${JAVA:-java}
[ -x /e/MincraftModding/JDK8/bin/java.exe ] && java=${JAVA:-/e/MincraftModding/JDK8/bin/java}

rm -rf "$dir/mods"
mkdir -p "$dir/mods"
cp -r "$dir/mods-base/." "$dir/mods/"
for jar in "$@"; do
    cp "$jar" "$dir/mods/"
done
sed -i "s/^level-type=.*/level-type=$type/; s/^level-name=.*/level-name=world/" "$dir/server.properties"
cfg="$dir/config/cavesandcliffsbackport.cfg"
status=0

run() {
    phase=$1
    rm -f "$dir/selftest-report.txt"
    log="$dir/hybrid-$phase.log"
    (cd "$dir" && "$java" -Xmx4G -Dfml.queryResult=confirm -Dcavesandcliffsbackport.selftest="$phase" \
        -jar "$(basename "$forge")" nogui < /dev/null > "$log" 2>&1) &
    pid=$!
    n=0
    while kill -0 $pid 2>/dev/null; do
        sleep 5
        n=$((n + 1))
        if [ $n -gt 180 ]; then
            echo "$phase: still running after 15 minutes, stopping it (pid $pid)"
            kill $pid
            break
        fi
    done
    wait $pid 2>/dev/null
    if [ -f "$dir/selftest-report.txt" ]; then
        cp "$dir/selftest-report.txt" "$dir/selftest-report-$phase.txt"
        tail -1 "$dir/selftest-report.txt"
        grep -q "fail=0$" "$dir/selftest-report.txt" || status=1
    else
        echo "$phase: no report (see $log)"
        status=1
    fi
}

set_types() {
    # Only the one setting; Forge fills in the rest of the file.
    if [ -f "$cfg" ]; then
        python -I - "$cfg" "$1" <<'PY'
import re, sys
path, names = sys.argv[1], sys.argv[2]
s = open(path, encoding='utf-8').read()
body = ''.join('        %s\n' % n for n in names.split(',') if n)
s, n = re.subn(r'(S:otherWorldTypes <\n)(.*?)(\s*>)', lambda m: m.group(1) + body + '     >', s, flags=re.S)
if n != 1:
    raise SystemExit('otherWorldTypes not found in ' + path)
open(path, 'w', encoding='utf-8').write(s)
PY
    fi
}

rm -rf "$dir/world"
set_types "RTG,OTG"
run hybridnew
run hybridreload
rm -rf "$dir/world"
set_types ""
run hybridold1
set_types "RTG,OTG"
run hybridold2
exit $status
