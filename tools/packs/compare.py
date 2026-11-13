"""compare.py a.json b.json [min]: per-chunk block, tile-entity and entity counts of two scans side by side (b/a),
skipping this mod's blocks; lists everything with at least `min` (default 200) total in either and a ratio outside
0.85-1.15, plus a fixed list of things worth seeing."""
import json
import sys
from collections import Counter


def totals(d, prefix_skip="cavesandcliffsbackport:"):
    t = Counter()
    for bd, m in d["blocks"].items():
        for k, v in m.items():
            if not k.startswith(prefix_skip):
                t[k] += v
    return t


def main():
    a = json.load(open(sys.argv[1]))
    b = json.load(open(sys.argv[2]))
    mn = int(sys.argv[3]) if len(sys.argv) > 3 else 200
    ca, cb = a["chunks"], b["chunks"]
    print("chunks", ca, cb)
    for title, ta, tb in (("blocks", totals(a), totals(b)),
                          ("tile entities", Counter(a["tile_entities"]), Counter(b["tile_entities"])),
                          ("entities", Counter(a["entities"]), Counter(b["entities"]))):
        print("==", title, "(per chunk: a, b, b/a)")
        keys = set(ta) | set(tb)
        rows = []
        for k in keys:
            va, vb = ta[k] / ca, tb[k] / cb
            if max(ta[k], tb[k]) < (mn if title == "blocks" else 5):
                continue
            r = vb / va if va else float("inf")
            if title != "blocks" or not (0.85 <= r <= 1.15):
                rows.append((k, va, vb, r))
        for k, va, vb, r in sorted(rows, key=lambda x: -max(x[1], x[2]))[:60]:
            print("  %-50s %10.2f %10.2f %6.2f" % (k, va, vb, r))


if __name__ == "__main__":
    main()
