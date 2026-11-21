"""Game Drops Backport's request 1: 26.1's baby textures from the 26.3 client jar, for babies/client/CaveBabies.

Run: python -I tools/babies/models.py   (idempotent; copies to textures/entity/... in our namespace)
"""
import os
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "cavesandcliffsbackport", "textures", "entity")
JAR = "G:/MCDownloads/minecraft-26.3/client.jar"

TEXTURES = ["goat/goat_baby"] + ["axolotl/axolotl_%s_baby" % v for v in ("lucy", "wild", "gold", "cyan", "blue")]


def main():
    with zipfile.ZipFile(JAR) as jar:
        for t in TEXTURES:
            data = jar.read("assets/minecraft/textures/entity/%s.png" % t)
            path = os.path.join(OUT, *(t + ".png").split("/"))
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "wb") as f:
                f.write(data)
            print(t)


if __name__ == "__main__":
    main()
