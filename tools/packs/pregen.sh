#!/bin/bash
# pregen.sh <rcon port> <radius chunks> [x z]: waits for the server, starts a square radius pregen, waits for it to end,
# prints the elapsed seconds and chunks/s (2304 chunks for radius 24, i.e. (2r)^2).
port=$1; r=$2; x=${3:-0}; z=${4:-0}
P="python -I "$(dirname "$0")/rcon.py" $port"
for i in $(seq 1 120); do $P "pregen info ShowTaskList" 2>/dev/null | grep -q TaskList && break; sleep 5; done
$P "pregen timepertick 2000" >/dev/null; $P "pregen utils setPriority pregenerator" >/dev/null
$P "pregen gen startradius square $x $z $r"
t0=$(date +%s)
sleep 5
while ! $P "pregen info ShowTaskList" 2>/dev/null | grep -q Empty; do sleep 3; done
t1=$(date +%s)
n=$(( (2*r) * (2*r) ))
echo "pregen r=$r at $x,$z: $((t1-t0)) s, $(awk "BEGIN{printf \"%.1f\", $n/($t1-$t0)}") chunks/s"
