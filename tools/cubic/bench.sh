#!/bin/sh
# Generation speed, cubic against the normal Caves & Cliffs world (CubicSelfTest's cubicbench: a 24x24 square at
# chunk 1000, 1000 generated and populated after an 8x8 warm-up; seed from eclipse/server.properties). Each round runs
# both, alternating, on fresh worlds. Run it pinned to the E-cores (start it from a process with that affinity).
#   sh tools/cubic/bench.sh [rounds, default 3]
root="$(cd "$(dirname "$0")/../.." && pwd)"
props="$root/eclipse/server.properties"
cp "$props" "$props.bench-bak"
sed -i 's/^level-type=.*/level-type=cavesandcliffs/; s/^level-name=.*/level-name=cubicbench/' "$props"
rounds="${1:-3}"
i=1
while [ "$i" -le "$rounds" ]; do
    for mode in cubic extended; do
        rm -rf "$root/eclipse/cubicbench"
        if [ "$mode" = cubic ]; then extra=-Pcubicchunks; else extra=; fi
        (cd "$root" && ./gradlew runServer --no-daemon -PdeployDir= -PcoreDeployDir= $extra -Pselftest=cubicbench) \
            > "$root/build-cubicbench-$mode-$i.log" 2>&1
        grep -h "CUBICBENCH\|TERRAINSTATS" "$root/build-cubicbench-$mode-$i.log" | sed 's/^.*\]: //'
    done
    i=$((i + 1))
done
rm -rf "$root/eclipse/cubicbench"
mv "$props.bench-bak" "$props"
