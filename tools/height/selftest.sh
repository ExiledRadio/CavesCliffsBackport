#!/bin/sh
# Stream H's self-test in a Caves & Cliffs world: points eclipse/server.properties at level-type cavesandcliffs on
# a fresh world, runs `gradlew runServer -Pselftest=height`, and puts level-type back to DEFAULT afterwards (the rest
# of the suite runs in a Default world). The test world is deleted first: the level type only applies to a new one.
#   sh tools/height/selftest.sh [selftest names, default height]
set -e
root="$(cd "$(dirname "$0")/../.." && pwd)"
props="$root/eclipse/server.properties"
level="$(sed -n 's/^level-name=//p' "$props")"
rm -rf "$root/eclipse/${level:-world}"
sed -i 's/^level-type=.*/level-type=cavesandcliffs/' "$props"
status=0
(cd "$root" && ./gradlew runServer -Pselftest="${1:-height}") || status=$?
sed -i 's/^level-type=.*/level-type=DEFAULT/' "$props"
# The extended world stays (the removal test reads it); delete it before a Default-world run.
tail -3 "$root/eclipse/selftest-report.txt" 2>/dev/null || true
exit $status
