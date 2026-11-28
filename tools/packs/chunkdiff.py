"""chunkdiff.py <world a> <world b> [radius_chunks]: compares the blocks (name and meta, every section, both section
lists) and the column biomes (by name) of every populated chunk the two worlds share, within radius_chunks of 0,0.
Prints the chunks compared, how many differ, and the first differences. Exit code 1 when any differs."""
import gzip
import os
import struct
import sys
import zlib
import glob

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scan import read_root  # noqa: E402

import numpy as np  # noqa: E402


def registries(world):
    level = read_root(gzip.open(os.path.join(world, "level.dat")).read())
    regs = level["FML"]["Registries"]
    blocks = {e["V"]: e["K"] for e in regs["minecraft:blocks"]["ids"]}
    biomes = {e["V"]: e["K"] for e in regs["minecraft:biomes"]["ids"]}
    return blocks, biomes


BAD = [0]


def chunks(world, radius):
    out = {}
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
            try:
                data = zlib.decompress(data) if comp == 2 else gzip.decompress(data)
                lv = read_root(data)["Level"]
            except Exception:  # a chunk half written when the world was copied
                BAD[0] += 1
                continue
            if radius is not None and (abs(lv["xPos"]) > radius or abs(lv["zPos"]) > radius):
                continue
            if lv.get("TerrainPopulated"):
                out[(lv["xPos"], lv["zPos"])] = lv
    return out


def sections(lv, names):
    res = {}
    for s in list(lv.get("Sections", [])) + list(lv.get("cavesandcliffsbackport:Sections", [])):
        bl = np.frombuffer(s["Blocks"], dtype=np.uint8).astype(np.int32)
        if "Add" in s:
            a = np.frombuffer(s["Add"], dtype=np.uint8)
            add = np.empty(4096, dtype=np.int32)
            add[0::2] = a & 15
            add[1::2] = a >> 4
            bl = bl | (add << 8)
        d = np.frombuffer(s["Data"], dtype=np.uint8)
        meta = np.empty(4096, dtype=np.int32)
        meta[0::2] = d & 15
        meta[1::2] = d >> 4
        res[s["Y"]] = (names[bl], meta)
    return res


def main():
    a, b = sys.argv[1], sys.argv[2]
    radius = int(sys.argv[3]) if len(sys.argv) > 3 else None
    (ba, ia), (bb, ib) = registries(a), registries(b)
    na = np.array([ba.get(i, "air" if i == 0 else "?%d" % i) for i in range(4096)], dtype=object)
    nb = np.array([bb.get(i, "air" if i == 0 else "?%d" % i) for i in range(4096)], dtype=object)
    ca, cb = chunks(a, radius), chunks(b, radius)
    shared = sorted(set(ca) & set(cb))
    differ = 0
    shown = 0
    for k in shared:
        sa, sb = sections(ca[k][1] if False else ca[k], na), sections(cb[k], nb)
        bad = []
        for y in sorted(set(sa) | set(sb)):
            if y not in sa or y not in sb:
                # A missing section must be all air on the other side.
                n, m = (sb[y] if y in sb else sa[y])
                if not np.all(n == "minecraft:air"):
                    bad.append("section %d only in one" % y)
                continue
            (n1, m1), (n2, m2) = sa[y], sb[y]
            diff = np.nonzero((n1 != n2) | (m1 != m2))[0]
            if len(diff):
                i = diff[0]
                bad.append("y%d: %d blocks, first %s:%d vs %s:%d at %d,%d,%d" % (
                    y, len(diff), n1[i], m1[i], n2[i], m2[i], i & 15, y * 16 + (i >> 8), (i >> 4) & 15))
        bio_a = [ia.get(v, "?") for v in ca[k].get("Biomes", b"")]
        bio_b = [ib.get(v, "?") for v in cb[k].get("Biomes", b"")]
        if bio_a != bio_b:
            bad.append("biomes differ in %d columns" % sum(1 for p, q in zip(bio_a, bio_b) if p != q))
        if bad:
            differ += 1
            if shown < 12:
                shown += 1
                print("chunk", k, "; ".join(bad[:4]))
    print("chunks compared", len(shared), "(a %d, b %d), differ %d" % (len(ca), len(cb), differ))
    sys.exit(1 if differ else 0)


if __name__ == "__main__":
    main()
