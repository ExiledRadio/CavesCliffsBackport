#!/bin/sh
# Cubic Chunks against the normal extended world (CubicSelfTest), three dev servers in a row:
#   1. -Pcubicchunks, fresh Caves & Cliffs world "cubic": limits and heights, the area generated and populated,
#      eclipse/cubic-genpop.dat, then eclipse/cubic-saved.dat at the shutdown's save;
#   2. -Pcubicchunks, the same world started again: the area read back equals cubic-saved.dat;
#   3. without Cubic Chunks, fresh Caves & Cliffs world "cubicref" (same seed): the normal generator's area against
#      cubic-genpop.dat, block for block.
# Each report is kept as eclipse/selftest-report-<phase>.txt; level-type and level-name are put back afterwards.
#   sh tools/cubic/compare.sh [phases, default "1 2 3"]
root="$(cd "$(dirname "$0")/../.." && pwd)"
props="$root/eclipse/server.properties"
cp "$props" "$props.cubic-bak"
phases="${1:-1 2 3}"
status=0
run() {
    (cd "$root" && ./gradlew runServer --no-daemon -PdeployDir= -PcoreDeployDir= "$@") > "$root/build-cubic-$phase.log" 2>&1 || status=1
    cp "$root/eclipse/selftest-report.txt" "$root/eclipse/selftest-report-$phase.txt" 2>/dev/null
    echo "phase $phase: $(head -1 "$root/eclipse/selftest-report.txt")"
}
sed -i 's/^level-type=.*/level-type=cavesandcliffs/' "$props"
for phase in $phases; do
    case $phase in
        1) sed -i 's/^level-name=.*/level-name=cubic/' "$props"; rm -rf "$root/eclipse/cubic" "$root/eclipse/cubic-saved.dat"
           run -Pcubicchunks -Pselftest=cubic ;;
        2) sed -i 's/^level-name=.*/level-name=cubic/' "$props"; run -Pcubicchunks -Pselftest=cubicreload ;;
        3) sed -i 's/^level-name=.*/level-name=cubicref/' "$props"; rm -rf "$root/eclipse/cubicref"
           run -Pselftest=cubicref ;;
    esac
done
mv "$props.cubic-bak" "$props"
exit $status
