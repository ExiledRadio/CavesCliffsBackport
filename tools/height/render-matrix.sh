#!/bin/bash
# render-matrix.sh <instance names> -- <world types>: runs the height client test in each render instance
# (tools/height/render-instances.sh) for each world type (RTG, OTG, CC for a Caves & Cliffs world), one at a time.
# Results: G:/VBPTest/ccrender/results/<instance>-<world>.{txt,log}, and a crash report copy if one was written.
root="$(cd "$(dirname "$0")/../.." && pwd)"
R=/g/VBPTest/ccrender/results
mkdir -p $R
names=(); worlds=(); seen=0
for a in "$@"; do
  if [ "$a" = "--" ]; then seen=1; continue; fi
  if [ $seen = 0 ]; then names+=("$a"); else worlds+=("$a"); fi
done
for n in "${names[@]}"; do
  for w in "${worlds[@]}"; do
    D=/g/VBPTest/ccrender/$n/minecraft
    rm -rf "$D/saves" "$D/screenshots"
    if [ "$w" = CC ]; then : > "$D/selftest-client-world"; else printf '%s' "$w" > "$D/selftest-client-world"; fi
    before=$(ls "$D/crash-reports" 2>/dev/null | wc -l)
    bash "$root/tools/packs/launch-prism.sh" "ccrender-$n" "/g/VBPTest/ccrender/$n" > "$R/$n-$w.txt" 2>&1
    cp "$D/logs/latest.log" "$R/$n-$w.log" 2>/dev/null
    now=$(ls "$D/crash-reports" 2>/dev/null | wc -l)
    [ "$now" != "$before" ] && cp "$D/crash-reports/$(ls -t "$D/crash-reports" | head -1)" "$R/$n-$w-crash.txt"
    [ -d "$D/screenshots" ] && cp -r "$D/screenshots" "$R/$n-$w-shots"
    echo "$n $w: $(grep -a 'pass=' "$R/$n-$w.txt" | tail -1) $(grep -a -m1 'CRASH\|exited\|killed' "$R/$n-$w.txt")"
  done
done
