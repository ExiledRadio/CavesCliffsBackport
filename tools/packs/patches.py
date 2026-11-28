"""patches.py <world dir> [radius_chunks] [out.json]: contiguous biome patches over the column biomes of every
populated chunk (within radius_chunks of 0,0). A patch is a 4-connected run of columns with the same biome; patches
that touch the edge of the area are cut off by it, so they are counted apart ("edge"). Prints per biome: whole patches,
their mean and median area in blocks, the largest, and the biome's share of all columns."""
import gzip
import json
import os
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scan import read_root  # noqa: E402
from chunkdiff import chunks, BAD  # noqa: E402

import numpy as np  # noqa: E402


def main():
    world = sys.argv[1]
    radius = int(sys.argv[2]) if len(sys.argv) > 2 else None
    out = sys.argv[3] if len(sys.argv) > 3 else None
    level = read_root(gzip.open(os.path.join(world, "level.dat")).read())
    names = {e["V"]: e["K"] for e in level["FML"]["Registries"]["minecraft:biomes"]["ids"]}
    ch = chunks(world, radius)
    xs = [k[0] for k in ch]
    zs = [k[1] for k in ch]
    x0, z0 = min(xs), min(zs)
    w, h = (max(xs) - x0 + 1) * 16, (max(zs) - z0 + 1) * 16
    grid = np.full((w, h), -1, dtype=np.int32)
    for (cx, cz), lv in ch.items():
        b = lv.get("Biomes")
        if not b or len(b) != 256:
            continue
        a = np.frombuffer(bytes(b), dtype=np.uint8).astype(np.int32).reshape(16, 16)  # [z][x]
        grid[(cx - x0) * 16:(cx - x0) * 16 + 16, (cz - z0) * 16:(cz - z0) * 16 + 16] = a.T
    seen = np.zeros_like(grid, dtype=bool)
    whole = defaultdict(list)
    edge = defaultdict(list)
    total = defaultdict(int)
    for i in range(w):
        for j in range(h):
            if seen[i, j] or grid[i, j] < 0:
                continue
            bid = grid[i, j]
            stack = [(i, j)]
            seen[i, j] = True
            n = 0
            touches = False
            while stack:
                x, z = stack.pop()
                n += 1
                for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
                    if nx < 0 or nz < 0 or nx >= w or nz >= h or grid[nx, nz] < 0:
                        touches = True
                        continue
                    if not seen[nx, nz] and grid[nx, nz] == bid:
                        seen[nx, nz] = True
                        stack.append((nx, nz))
            name = names.get(int(bid), "?%d" % bid)
            total[name] += n
            (edge if touches else whole)[name].append(n)
    cols = sum(total.values())
    rows = []
    for name in sorted(total, key=lambda k: -total[k]):
        p = whole[name]
        rows.append({"biome": name, "share": round(100.0 * total[name] / cols, 2), "patches": len(p),
                     "edge_patches": len(edge[name]),
                     "mean": round(float(np.mean(p)), 0) if p else 0, "median": int(np.median(p)) if p else 0,
                     "largest": max(p + edge[name]) if (p or edge[name]) else 0,
                     "big_mean": round(float(np.mean([v for v in p if v >= 256])), 0) if any(v >= 256 for v in p) else 0,
                     "big": sum(1 for v in p if v >= 256)})
    print("columns %d, area %dx%d blocks, %d unreadable chunks" % (cols, w, h, BAD[0]))
    print("%-38s %6s %7s %5s %8s %7s %8s %5s %8s" % ("biome", "share", "patches", "edge", "mean", "median",
                                                    "largest", "big", "big_mean"))
    for r in rows:
        print("%-38s %6.2f %7d %5d %8d %7d %8d %5d %8d" % (r["biome"], r["share"], r["patches"], r["edge_patches"],
                                                         r["mean"], r["median"], r["largest"], r["big"], r["big_mean"]))
    if out:
        with open(out, "w") as f:
            json.dump(rows, f, indent=1)


if __name__ == "__main__":
    main()
