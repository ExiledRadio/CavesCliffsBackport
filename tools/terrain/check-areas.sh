#!/bin/sh
# Runs FeatureCheck per seed on per-seed areas. Usage: check-areas.sh <dump dir with <seed>/ subdirs> <side> <features|all> "seed centers" ...
dir="$1"; side="$2"; feats="$3"; shift 3
for p in "$@"; do
  set -- $p
  echo "== seed $1"
  java -Dcenters="$2" -cp build/terrain-tools FeatureCheck "$dir/$1" "$side" "$feats" "$1" 2>&1 | grep -v "^    [a-z]"
done
