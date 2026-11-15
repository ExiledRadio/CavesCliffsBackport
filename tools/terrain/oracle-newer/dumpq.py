"""Prints blocks of a features_SEED.bin.gz dump. Usage: python dumpq.py <dump.bin.gz> x,y,z [x,y,z ...]
(append +r to a position for the 3x3x3 around it)."""
import gzip, struct, sys

def load(path):
    with gzip.open(path, "rb") as f:
        data = f.read()
    at = 0
    (n,) = struct.unpack_from(">i", data, at); at += 4
    names = []
    for _ in range(n):
        (l,) = struct.unpack_from(">H", data, at); at += 2
        names.append(data[at:at + l].decode("utf-8")); at += l
    (count,) = struct.unpack_from(">i", data, at); at += 4
    chunks = {}
    size = 16 * 16 * 384 * 2
    for _ in range(count):
        cx, cz = struct.unpack_from(">ii", data, at); at += 8
        chunks[(cx, cz)] = (at, data)
        at += size
    return names, chunks

def get(names, chunks, x, y, z):
    c = chunks.get((x >> 4, z >> 4))
    if c is None:
        return "?"
    at, data = c
    i = ((x & 15) * 16 + (z & 15)) * 384 + y + 64
    (v,) = struct.unpack_from(">H", data, at + 2 * i)
    return names[v]

names, chunks = load(sys.argv[1])
for arg in sys.argv[2:]:
    r = arg.endswith("+r")
    x, y, z = map(int, arg.rstrip("+r").split(","))
    if not r:
        print(arg, get(names, chunks, x, y, z))
        continue
    for dx, dy, dz, n in [(0, 0, 0, "self"), (0, -1, 0, "down"), (0, 1, 0, "up"), (0, 0, -1, "north"), (0, 0, 1, "south"), (-1, 0, 0, "west"), (1, 0, 0, "east")]:
        print(arg, n, get(names, chunks, x + dx, y + dy, z + dz))
