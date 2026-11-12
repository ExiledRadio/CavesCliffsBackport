"""The removal test's first half (EXTENDED_HEIGHT section 5.3): copies a world and strips this mod's section list
(Level."cavesandcliffsbackport:Sections") from every chunk of the overworld's region files, as removing the mod
and saving would. Also checks that vanilla's Sections list holds Y 0-15 only, so vanilla can read every chunk.

    python tools/height/strip_sections.py <world dir> <copy to>

Prints the chunk count, how many had our list, and "STRIP OK" (or a failure line and exit status 1).
"""
import io
import os
import shutil
import struct
import sys
import zlib

OURS = "cavesandcliffsbackport:Sections"


# --- a minimal NBT reader/writer (tag ids 0-12) -------------------------------------------------------------

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
        return bytearray(b.read(n))
    if tid == 8:
        n = struct.unpack(">H", b.read(2))[0]
        return b.read(n).decode("utf-8")
    if tid == 9:
        et = b.read(1)[0]
        n = struct.unpack(">i", b.read(4))[0]
        return ("list", et, [read_tag(b, et) for _ in range(n)])
    if tid == 10:
        out = {}
        order = []
        while True:
            t = b.read(1)[0]
            if t == 0:
                return ("compound", order, out)
            name = read_tag(b, 8)
            order.append((name, t))
            out[name] = read_tag(b, t)
    if tid == 11:
        n = struct.unpack(">i", b.read(4))[0]
        return list(struct.unpack(">%di" % n, b.read(4 * n)))
    if tid == 12:
        n = struct.unpack(">i", b.read(4))[0]
        return list(struct.unpack(">%dq" % n, b.read(8 * n)))
    raise ValueError("tag id %d" % tid)


def write_tag(o, tid, v):
    if tid == 1:
        o.write(struct.pack(">b", v))
    elif tid == 2:
        o.write(struct.pack(">h", v))
    elif tid == 3:
        o.write(struct.pack(">i", v))
    elif tid == 4:
        o.write(struct.pack(">q", v))
    elif tid == 5:
        o.write(struct.pack(">f", v))
    elif tid == 6:
        o.write(struct.pack(">d", v))
    elif tid == 7:
        o.write(struct.pack(">i", len(v)))
        o.write(bytes(v))
    elif tid == 8:
        s = v.encode("utf-8")
        o.write(struct.pack(">H", len(s)))
        o.write(s)
    elif tid == 9:
        _, et, items = v
        o.write(bytes([et]))
        o.write(struct.pack(">i", len(items)))
        for i in items:
            write_tag(o, et, i)
    elif tid == 10:
        _, order, d = v
        for name, t in order:
            if name not in d:
                continue
            o.write(bytes([t]))
            write_tag(o, 8, name)
            write_tag(o, t, d[name])
        o.write(b"\x00")
    elif tid == 11:
        o.write(struct.pack(">i", len(v)))
        o.write(struct.pack(">%di" % len(v), *v))
    elif tid == 12:
        o.write(struct.pack(">i", len(v)))
        o.write(struct.pack(">%dq" % len(v), *v))
    else:
        raise ValueError("tag id %d" % tid)


def read_root(data):
    b = io.BytesIO(data)
    tid = b.read(1)[0]
    name = read_tag(b, 8)
    return name, read_tag(b, tid)


def write_root(name, root):
    o = io.BytesIO()
    o.write(b"\x0a")
    write_tag(o, 8, name)
    write_tag(o, 10, root)
    return o.getvalue()


# --- region files -----------------------------------------------------------------------------------------

def strip_region(src, dst, stats):
    data = open(src, "rb").read()
    if len(data) < 8192:
        shutil.copyfile(src, dst)
        return
    header = data[:4096]
    times = data[4096:8192]
    chunks = []
    for i in range(1024):
        off = struct.unpack(">I", header[i * 4:i * 4 + 4])[0]
        sector, count = off >> 8, off & 0xFF
        if sector == 0:
            chunks.append(None)
            continue
        start = sector * 4096
        length = struct.unpack(">I", data[start:start + 4])[0]
        kind = data[start + 4]
        payload = data[start + 5:start + 4 + length]
        raw = zlib.decompress(payload) if kind == 2 else payload
        name, root = read_root(raw)
        level = root[2]["Level"][2]
        stats["chunks"] += 1
        for section in level["Sections"][2]:
            y = section[2]["Y"]
            if not 0 <= y <= 15:
                stats["bad"].append("%s chunk %d: vanilla Sections has Y %d" % (os.path.basename(src), i, y))
        if OURS in level:
            stats["stripped"] += 1
            stats["our_sections"] += len(level[OURS][2])
            del level[OURS]
        chunks.append(zlib.compress(write_root(name, root)))
    out = io.BytesIO()
    new_header = bytearray(4096)
    out.write(b"\x00" * 8192)
    sector = 2
    for i, c in enumerate(chunks):
        if c is None:
            continue
        blob = struct.pack(">I", len(c) + 1) + b"\x02" + c
        pad = (-len(blob)) % 4096
        count = (len(blob) + pad) // 4096
        new_header[i * 4:i * 4 + 4] = struct.pack(">I", (sector << 8) | count)
        out.write(blob + b"\x00" * pad)
        sector += count
    result = bytearray(out.getvalue())
    result[0:4096] = new_header
    result[4096:8192] = times
    open(dst, "wb").write(bytes(result))


def main():
    src, dst = sys.argv[1], sys.argv[2]
    if os.path.exists(dst):
        shutil.rmtree(dst)
    shutil.copytree(src, dst, ignore=shutil.ignore_patterns("session.lock"))
    stats = {"chunks": 0, "stripped": 0, "our_sections": 0, "bad": []}
    region = os.path.join(dst, "region")
    for f in sorted(os.listdir(region)):
        if f.endswith(".mca"):
            strip_region(os.path.join(src, "region", f), os.path.join(region, f), stats)
    print("chunks %d, with our section list %d (%d sections stripped)" % (stats["chunks"], stats["stripped"], stats["our_sections"]))
    for line in stats["bad"][:10]:
        print("FAIL " + line)
    ok = not stats["bad"] and stats["stripped"] > 0
    print("STRIP OK" if ok else "STRIP FAILED")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
