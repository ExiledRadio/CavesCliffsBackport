#!/bin/sh
# Dumps vanilla 1.19.2/1.20.1 decoration with only the given placed features (all ported ones: "ported") on one
# area and runs FeatureCheck on it with the same editions and feature rules.
# Usage: subset.sh <119|120> <editions> <rules> <seed> <cx,cz|default> <side> <features,comma,separated|ported>
# "default" is the dumps' two default areas (around 0,0 and a far point per seed). Extra FeatureCheck options:
# JAVA_OPTS; extra oracle options: ORACLE_EXTRA. Output dir: G:/MCDownloads/oracle-subset/<v>.
v="$1"; ed="$2"; rules="$3"; seed="$4"; center="$5"; side="$6"; feats="$7"
copt="-Dcenters=$center"
[ "$center" = default ] && copt=""
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../../.." && pwd)"
out="G:/MCDownloads/oracle-subset/$v"
mkdir -p "$out"
cd "$root"
sh tools/terrain/run.sh PortedFeatures > /dev/null || exit 1
[ "$feats" = ported ] && feats="$(java -Deditions="$ed" -cp build/terrain-tools PortedFeatures)"
ORACLE_OPTS="-Dfeatures=$feats $copt $ORACLE_EXTRA" cmd //c "tools\\terrain\\oracle-newer\\run.cmd $v net.minecraft.world.level.levelgen.FeatureDump$v $(cygpath -w "$out") $side $seed" > build/subset-$v.log 2>&1
grep -q "chunks decorated" build/subset-$v.log || { grep -v "INFO" build/subset-$v.log | tail -20; exit 1; }
java $JAVA_OPTS -Xmx6G -Deditions="$ed" -Drules="$rules" $copt -cp build/terrain-tools FeatureCheck "$out" "$side" "$feats" "$seed" 2>&1 | grep -v "^states\|^decoration"
