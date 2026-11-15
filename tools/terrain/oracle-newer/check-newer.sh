#!/bin/sh
# The newer decoration checks: per seed, vanilla 1.19.2 (editions 1, rules 119) and 1.20.1 (editions 3, rules 120)
# against the core, every ported feature, on the default areas (side 16: around 0,0 and a far point) and on 8x8
# chunk areas in a mangrove swamp, a swamp, a jungle, ice spikes and (1.20) a cherry grove (FindBiome).
# Usage: check-newer.sh [119|120|both]   Output: build/check-newer-<v>.log
here="$(cd "$(dirname "$0")" && pwd)"
which="${1:-both}"
run() {
    v="$1"; ed="$2"; rules="$3"; seed="$4"; areas="$5"
    echo "== $v seed $seed default 16"
    sh "$here/subset.sh" "$v" "$ed" "$rules" "$seed" default 16 ported | tail -1
    echo "== $v seed $seed $areas 8"
    sh "$here/subset.sh" "$v" "$ed" "$rules" "$seed" "$areas" 8 ported | tail -1
}
# mangrove swamp / swamp / jungle / ice spikes (/ cherry grove), chunk coordinates
A42="-72,-80/-16,8/88,80/344,104"
A12345="-112,-88/-112,-112/-40,-32/48,-48"
A7346="-48,-144/-184,-120/48,-120/-48,8"
A8675="-48,144/192,112/24,-40/-8,272"
cd "$here/../../.."
if [ "$which" != 120 ]; then
    {
        run 119 1 119 42 "$A42"
        run 119 1 119 12345 "$A12345"
        run 119 1 119 -7346534567 "$A7346"
        run 119 1 119 8675309 "$A8675"
    } > build/check-newer-119.log 2>&1
fi
if [ "$which" != 119 ]; then
    {
        run 120 3 120 42 "$A42/-184,48"
        run 120 3 120 12345 "$A12345/32,-64"
        run 120 3 120 -7346534567 "$A7346/-40,24"
        run 120 3 120 8675309 "$A8675/-24,24"
    } > build/check-newer-120.log 2>&1
fi
