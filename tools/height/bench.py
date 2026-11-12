"""EXTENDED_HEIGHT section 4, "height alone": the heightbench self-test in three modes, each on a fresh world with the
same seed, the server pinned to the E-cores (logical processors 16-31 of the 14900K):

  vanilla   height.enabled=false: the coremod patches nothing (vanilla's code), Default world
  default   the coremod on, Default world (the patched paths in a world that is not extended)
  extended  the coremod on, a Caves & Cliffs world (y -64..319: 24 sections, deep stone, extra save list)

    python tools/height/bench.py [rounds]

Prints each run's HEIGHTBENCH line and the ratios to vanilla. Restores the config and level-type afterwards.
"""
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ECL = os.path.join(ROOT, "eclipse")
CFG = os.path.join(ECL, "config", "cavesandcliffsbackport.cfg")
PROPS = os.path.join(ECL, "server.properties")


def set_height_enabled(on):
    text = open(CFG, encoding="utf-8").read()
    text = re.sub(r"(height \{.*?B:enabled=)(true|false)", lambda m: m.group(1) + ("true" if on else "false"), text, flags=re.S)
    open(CFG, "w", encoding="utf-8", newline="\n").write(text)


def set_level_type(t):
    text = open(PROPS, encoding="utf-8").read()
    text = re.sub(r"^level-type=.*$", "level-type=" + t, text, flags=re.M)
    open(PROPS, "w", encoding="utf-8", newline="\n").write(text)


def run(mode):
    set_height_enabled(mode != "vanilla")
    set_level_type("cavesandcliffs" if mode == "extended" else "DEFAULT")
    shutil.rmtree(os.path.join(ECL, "world"), ignore_errors=True)
    log = os.path.join(ROOT, "build", "bench-%s.log" % mode)
    # Pinned to the E-cores: the forked server JVM inherits Gradle's affinity.
    cmd = 'start "" /D "%s" /b /wait /affinity FFFF0000 "%s" runServer -Pselftest=heightbench' % (ROOT, os.path.join(ROOT, "gradlew.bat"))
    with open(log, "w") as out:
        subprocess.run(cmd, shell=True, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT)
    for line in open(os.path.join(ECL, "logs", "latest.log"), encoding="utf-8", errors="replace"):
        if "HEIGHTBENCH" in line:
            return line.split("HEIGHTBENCH", 1)[1].strip()
    return None


def main():
    rounds = int(sys.argv[1]) if len(sys.argv) > 1 else 2
    results = {}
    try:
        for r in range(rounds):
            for mode in ("vanilla", "default", "extended"):
                line = run(mode)
                print("%s round %d: %s" % (mode, r + 1, line))
                if line:
                    g = float(re.search(r"gen_ms_per_chunk=([\d.]+)", line).group(1))
                    s = float(re.search(r"save_ms_per_chunk=([\d.]+)", line).group(1))
                    results.setdefault(mode, []).append((g, s))
    finally:
        set_height_enabled(True)
        set_level_type("DEFAULT")
        shutil.rmtree(os.path.join(ECL, "world"), ignore_errors=True)
    best = {m: (min(v[0] for v in vals), min(v[1] for v in vals)) for m, vals in results.items()}
    for m, (g, s) in best.items():
        line = "%-8s best gen %.3f ms/chunk, save %.3f ms/chunk" % (m, g, s)
        if "vanilla" in best and m != "vanilla":
            vg, vs = best["vanilla"]
            line += "  (gen x%.3f, gen+save x%.3f vs vanilla)" % (g / vg, (g + s) / (vg + vs))
        print(line)


if __name__ == "__main__":
    main()
