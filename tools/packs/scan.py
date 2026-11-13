"""scan.py <world dir> [out.json] [radius_chunks]: count blocks (by registry name and height band), tile entities and
entities over every generated overworld chunk (or those within radius_chunks of 0,0). Reads vanilla's Sections and
Caves & Cliffs Backport's "cavesandcliffsbackport:Sections" (signed Y). Writes JSON, prints a short summary."""
import glob
import gzip
import io
import json
import os
import struct
import sys
import zlib
from collections import Counter

import numpy as np


def read_tag(b, tid):
    if tid == 1:
        return struct.unpack(">b", b.read(1))[0]
    if tid == 2:
        return struct.unpack(">h", b.read(2))[0]
    if tid == 3:
        return struct.unpack(">i", b.read(4))[0]
    if tid == 4:
        return struct.unpack(">q", b.read(8))[0]
    if tid == 5:
        return struct.unpack(">f", b.read(4))[0]
    if tid == 6:
        return struct.unpack(">d", b.read(8))[0]
    if tid == 7:
        n = struct.unpack(">i", b.read(4))[0]
        return b.read(n)
    if tid == 8:
        n = struct.unpack(">H", b.read(2))[0]
        return b.read(n).decode("utf-8", "replace")
    if tid == 9:
        et = b.read(1)[0]
        n = struct.unpack(">i", b.read(4))[0]
        return [read_tag(b, et) for _ in range(n)]
    if tid == 10:
        out = {}
        while True:
            t = b.read(1)[0]
            if t == 0:
                return out
            name = read_tag(b, 8)
            out[name] = read_tag(b, t)
    if tid == 11:
        n = struct.unpack(">i", b.read(4))[0]
        return b.read(4 * n)
    if tid == 12:
        n = struct.unpack(">i", b.read(4))[0]
        return b.read(8 * n)
    raise ValueError("tag id %d" % tid)


def read_root(data):
    b = io.BytesIO(data)
    t = b.read(1)[0]
    read_tag(b, 8)
    return read_tag(b, t)


def band(y):
    if y < 0:
        return "<0"
    if y < 16:
        return "0-15"
    if y < 64:
        return "16-63"
    if y < 256:
        return "64-255"
    return "256+"


def main():
    world = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else None
    radius = int(sys.argv[3]) if len(sys.argv) > 3 else None
    level = read_root(gzip.open(os.path.join(world, "level.dat")).read())
    ids = {}
    for e in level["FML"]["Registries"]["minecraft:blocks"]["ids"]:
        ids[e["V"]] = e["K"]
    names = np.array([ids.get(i, "?%d" % i) for i in range(4096)], dtype=object)
    blocks = {}  # band -> np counts[4096]
    bids = {}
    for e in level["FML"]["Registries"]["minecraft:biomes"]["ids"]:
        bids[e["V"]] = e["K"]
    biomes = Counter()
    tes = Counter()
    ents = Counter()
    chunks = 0
    min_sec = 99
    max_sec = -99
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
            lv = read_root(data)["Level"]
            if radius is not None and (abs(lv["xPos"]) > radius or abs(lv["zPos"]) > radius):
                continue
            if not lv.get("TerrainPopulated"):
                continue
            chunks += 1
            secs = list(lv.get("Sections", [])) + list(lv.get("cavesandcliffsbackport:Sections", []))
            for s in secs:
                y = s["Y"]
                min_sec = min(min_sec, y)
                max_sec = max(max_sec, y)
                bl = np.frombuffer(s["Blocks"], dtype=np.uint8).astype(np.uint16)
                if "Add" in s:
                    a = np.frombuffer(s["Add"], dtype=np.uint8)
                    add = np.empty(4096, dtype=np.uint16)
                    add[0::2] = a & 15
                    add[1::2] = a >> 4
                    bl = bl | (add << 8)
                bd = band(y * 16)
                c = blocks.setdefault(bd, np.zeros(4096, dtype=np.int64))
                c += np.bincount(bl, minlength=4096)
            if "Biomes" in lv:
                for k, v in Counter(lv["Biomes"]).items():
                    biomes[bids.get(k, "?%d" % k)] += v
            for t in lv.get("TileEntities", []):
                tes[t.get("id", "?")] += 1
            for e in lv.get("Entities", []):
                ents[e.get("id", "?")] += 1
    res = {"chunks": chunks, "section_range": [min_sec, max_sec], "blocks": {}, "tile_entities": dict(tes),
           "entities": dict(ents), "biomes": dict(biomes)}
    for bd, c in blocks.items():
        nz = np.nonzero(c)[0]
        res["blocks"][bd] = {names[i]: int(c[i]) for i in nz}
    if out:
        with open(out, "w") as f:
            json.dump(res, f, indent=1, sort_keys=True)
    print("chunks", chunks, "sections Y", min_sec, max_sec)
    tot = Counter()
    for bd, d in res["blocks"].items():
        for k, v in d.items():
            tot[k] += v
    ours = {k: v for k, v in tot.items() if k.startswith("cavesandcliffsbackport:")}
    print("ours per chunk:", {k.split(":")[1]: round(v / max(chunks, 1), 2) for k, v in sorted(ours.items(), key=lambda x: -x[1])[:40]})
    print("tile entities:", dict(tes.most_common(25)))
    print("biomes %:", {k: round(100.0 * v / max(sum(biomes.values()), 1), 1) for k, v in biomes.most_common(12)})


if __name__ == "__main__":
    main()
