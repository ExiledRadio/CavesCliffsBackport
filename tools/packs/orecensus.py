"""orecensus.py <world dir> <out.json> [radius_chunks] [namespace ...]: counts ore blocks per block state (registry
name and metadata) per y over every populated overworld chunk (or those within radius_chunks of 0,0), for blocks whose
registry name contains "ore" in the given namespaces (default: every namespace but this mod's), plus stone, deepslate
and tuff per y as the rock they sit in. Reads vanilla's Sections and Caves & Cliffs Backport's
"cavesandcliffsbackport:Sections" (signed Y). Stream O2's census (docs/streams/moddedores.md).

orecensus.py --compare <a.json> <b.json> [...]: per ore, blocks per chunk in y bands, one column per census."""
import json
import os
import sys
from collections import defaultdict

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import scan  # noqa: E402

ROCK = ("minecraft:stone", "cavesandcliffsbackport:deepslate", "cavesandcliffsbackport:tuff")
BANDS = [(-64, -33), (-32, -1), (0, 15), (16, 31), (32, 63), (64, 127), (128, 319)]


def census(world, out, radius, namespaces):
    import glob
    import gzip
    import struct
    import zlib
    level = scan.read_root(gzip.open(os.path.join(world, "level.dat")).read())
    ids = {}
    for e in level["FML"]["Registries"]["minecraft:blocks"]["ids"]:
        ids[e["V"]] = e["K"]
    wanted = {}
    for i, name in ids.items():
        ns, path = name.split(":", 1)
        ore = "ore" in path and (ns in namespaces if namespaces else ns != "cavesandcliffsbackport")
        if ore or name in ROCK:
            wanted[i] = name
    lookup = np.zeros(4096, dtype=bool)
    for i in wanted:
        lookup[i] = True
    counts = defaultdict(lambda: defaultdict(int))  # "name:meta" -> y -> n
    chunks = 0
    for rf in glob.glob(os.path.join(world, "region", "r.*.mca")):
        with open(rf, "rb") as f:
            raw = f.read()
        if len(raw) < 8192:
            continue
        for i in range(1024):
            off = struct.unpack(">I", raw[i * 4:i * 4 + 4])[0]
            if off == 0:
                continue
            sec = (off >> 8) * 4096
            ln = struct.unpack(">I", raw[sec:sec + 4])[0]
            comp = raw[sec + 4]
            data = raw[sec + 5:sec + 4 + ln]
            data = zlib.decompress(data) if comp == 2 else gzip.decompress(data)
            lv = scan.read_root(data)["Level"]
            if radius is not None and (abs(lv["xPos"]) > radius or abs(lv["zPos"]) > radius):
                continue
            if not lv.get("TerrainPopulated"):
                continue
            chunks += 1
            for s in list(lv.get("Sections", [])) + list(lv.get("cavesandcliffsbackport:Sections", [])):
                bl = np.frombuffer(s["Blocks"], dtype=np.uint8).astype(np.uint16)
                if "Add" in s:
                    a = np.frombuffer(s["Add"], dtype=np.uint8)
                    add = np.empty(4096, dtype=np.uint16)
                    add[0::2] = a & 15
                    add[1::2] = a >> 4
                    bl = bl | (add << 8)
                hit = np.nonzero(lookup[bl])[0]
                if hit.size == 0:
                    continue
                d = np.frombuffer(s["Data"], dtype=np.uint8)
                meta = np.empty(4096, dtype=np.uint16)
                meta[0::2] = d & 15
                meta[1::2] = d >> 4
                key = (bl[hit].astype(np.int64) << 4 | meta[hit]) << 12 | (hit >> 8) + (s["Y"] * 16 + 64)
                u, c = np.unique(key, return_counts=True)
                for k, n in zip(u.tolist(), c.tolist()):
                    y = (k & 4095) - 64
                    bid = k >> 16
                    m = (k >> 12) & 15
                    name = wanted[bid]
                    counts[name if name in ROCK else "%s:%d" % (name, m)][y] += n
    res = {"chunks": chunks, "counts": {k: dict(v) for k, v in counts.items()}}
    with open(out, "w") as f:
        json.dump(res, f, indent=1, sort_keys=True)
    print("chunks", chunks, "states", len(counts))


def compare(files):
    data = [json.load(open(f)) for f in files]
    keys = sorted(set(k for d in data for k in d["counts"]))
    head = "%-44s" % "per chunk" + "".join("%-30s" % os.path.basename(f) for f in files)
    print(head)
    print("%-44s" % "" + "".join("%-30s" % " ".join("%5s" % ("%d" % lo) for lo, _ in BANDS) for _ in files))
    for k in keys:
        row = "%-44s" % k[:43]
        for d in data:
            c = d["counts"].get(k, {})
            cells = []
            for lo, hi in BANDS:
                n = sum(v for y, v in c.items() if lo <= int(y) <= hi)
                cells.append("%5.1f" % (n / max(d["chunks"], 1)))
            row += "%-30s" % " ".join(cells)
        print(row)
    print("%-44s" % "chunks" + "".join("%-30s" % d["chunks"] for d in data))


if __name__ == "__main__":
    if sys.argv[1] == "--compare":
        compare(sys.argv[2:])
    else:
        census(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 and sys.argv[3] != "-" else None,
               sys.argv[4:])
