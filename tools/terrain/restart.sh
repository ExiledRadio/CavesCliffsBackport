#!/bin/sh
# Stream G's restart test (RestartSelfTest): decoration across a server restart against one uninterrupted run, in a
# Caves & Cliffs world (level-type cavesandcliffs, put back to DEFAULT afterwards).
#   1. fresh world, -Pselftest=restartwhole: areas A and B in one run   -> eclipse/restart-whole.txt
#   2. fresh world, -Pselftest=restart1:     area A, then the server stops and saves
#   3. same world,  -Pselftest=restart2:     area B, compared chunk by chunk with run 1
#   sh tools/terrain/restart.sh
set -e
root="$(cd "$(dirname "$0")/../.." && pwd)"
props="$root/eclipse/server.properties"
level="$(sed -n 's/^level-name=//p' "$props")"
world="$root/eclipse/${level:-world}"
sed -i 's/^level-type=.*/level-type=cavesandcliffs/' "$props"
rm -f "$root/eclipse/restart-whole.txt" "$root/eclipse/restart-first.txt" "$root/eclipse/restart-split.txt"
status=0
run() {
    (cd "$root" && ./gradlew runServer --offline -Pselftest="$1") || status=$?
    tail -3 "$root/eclipse/selftest-report.txt" 2>/dev/null || true
}
rm -rf "$world"
run restartwhole
rm -rf "$world"
run restart1
run restart2
sed -i 's/^level-type=.*/level-type=DEFAULT/' "$props"
rm -rf "$world"
exit $status
