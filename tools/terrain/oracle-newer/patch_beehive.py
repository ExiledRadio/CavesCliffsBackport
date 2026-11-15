"""Writes the oracles' patched BeehiveDecorator (vanilla's, decompiled under G:/MCDownloads/minecraft-<v>/src) into
build/oracle-src/<v119|v120>, for compiling ahead of the client jar. Vanilla shuffles the nest's positions with the
JVM's shared random (Collections.shuffle without a random), which no two runs share; the patch shuffles with the
Caves and Cliffs Backport core's random of the first position instead. Mojang's code stays out of the repository.
Usage: python patch_beehive.py <repo root>"""
import os, sys

root = sys.argv[1]
for version, tag in (("1.19.2", "v119"), ("1.20.1", "v120")):
    rel = "net/minecraft/world/level/levelgen/feature/treedecorators/BeehiveDecorator.java"
    s = open(os.path.join("G:/MCDownloads/minecraft-%s/src" % version, rel), encoding="utf-8").read()
    old = "            Collections.shuffle($$5);"
    assert s.count(old) == 1, version
    s = s.replace(old, "            BlockPos first = (BlockPos)$$5.get(0);\n"
                  "            Collections.shuffle($$5, new java.util.Random((long)first.getX() * 3129871L"
                  " ^ (long)first.getZ() * 116129781L ^ (long)first.getY()));")
    # The decompiler reuses names inside the bees lambda.
    for a, b in (("int $$2 = 2 + $$1.nextInt(2);", "int bees = 2 + $$1.nextInt(2);"),
                 ("for(int $$3 = 0; $$3 < $$2; ++$$3) {", "for(int b = 0; b < bees; ++b) {"),
                 ("CompoundTag $$4 = new CompoundTag();", "CompoundTag tag = new CompoundTag();"),
                 ('$$4.putString("id"', 'tag.putString("id"'),
                 ("$$1x.storeBee($$4, ", "$$1x.storeBee(tag, ")):
        assert s.count(a) == 1, (version, a)
        s = s.replace(a, b)
    out = os.path.join(root, "build", "oracle-src", tag, rel)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)
    print(out)
