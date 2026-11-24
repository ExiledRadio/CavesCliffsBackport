#!/bin/sh
# The world upgrade's test (UpgradeSelfTest): a 0-255 world upgraded to y -64..319, three servers on one world.
#   1. fresh Default world, -Pselftest=upgrade1: old area with known blocks and a village, then the upgrade command
#   2. same world,          -Pselftest=upgrade2: it loads upgraded; old chunks, new chunks, blending, structures
#   3. same world,          -Pselftest=upgrade3: after a restart every chunk is as it was saved
# The world is deleted first and after. Extra Gradle arguments (e.g. -Pcore_version=1.4.5) are passed on.
#   sh tools/height/upgrade.sh [gradle arguments]
set -e
root="$(cd "$(dirname "$0")/../.." && pwd)"
props="$root/eclipse/server.properties"
level="$(sed -n 's/^level-name=//p' "$props")"
world="$root/eclipse/${level:-world}"
sed -i 's/^level-type=.*/level-type=DEFAULT/' "$props"
rm -f "$root/eclipse/upgrade-old.bin" "$root/eclipse/upgrade-after.bin"
status=0
run() {
    phase="$1"
    shift
    rm -f "$root/eclipse/selftest-report.txt"
    (cd "$root" && ./gradlew runServer --offline -Pselftest="$phase" "$@") || status=$?
    tail -3 "$root/eclipse/selftest-report.txt" 2>/dev/null || true
    cp "$root/eclipse/selftest-report.txt" "$root/eclipse/selftest-report-$phase.txt" 2>/dev/null || true
    grep -q "fail=0$" "$root/eclipse/selftest-report.txt" 2>/dev/null || status=1
}
rm -rf "$world"
run upgrade1 "$@"
run upgrade2 "$@"
run upgrade3 "$@"
rm -rf "$world"
exit $status
