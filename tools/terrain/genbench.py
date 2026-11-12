"""Stream G's benchmark (EXTENDED_HEIGHT section 4, "1.18 terrain"): the heightbench self-test on a dev server pinned to
the E-cores (logical processors 16-31 of the 14900K), alternating

  vanilla   height.enabled=false, Default world: vanilla 1.12's generator, nothing patched
  modern    a Caves & Cliffs world: the 1.18 generator (y -64..319)

each on a fresh world with the same seed (eclipse/server.properties). heightbench generates and populates a 24x24
square of chunks at chunk (1000, 1000) after an 8x8 warm-up, then writes each chunk to NBT four times.

    python tools/terrain/genbench.py [rounds] [cores-mask]

cores-mask is start's /affinity mask (default FFFF0000: the 16 E-cores; F0000000 gives four E-cores, a small machine).
Prints every run's HEIGHTBENCH and TERRAINSTATS lines, then the best and median of each mode. Logs: build/genbench-*.log.
"""
import os
import re
import shutil
import statistics
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


def run(mode, r, mask):
    set_height_enabled(mode != "vanilla")
    set_level_type("cavesandcliffs" if mode == "modern" else "DEFAULT")
    shutil.rmtree(os.path.join(ECL, "world"), ignore_errors=True)
    log = os.path.join(ROOT, "build", "genbench-%s-%d.log" % (mode, r))
    cmd = 'start "" /D "%s" /b /wait /affinity %s "%s" runServer -Pselftest=heightbench' % (ROOT, mask, os.path.join(ROOT, "gradlew.bat"))
    with open(log, "w") as out:
        subprocess.run(cmd, shell=True, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT)
    bench = stats = None
    for line in open(log, encoding="utf-8", errors="replace"):
        if "HEIGHTBENCH" in line:
            bench = line.split("HEIGHTBENCH", 1)[1].strip()
        if "TERRAINSTATS" in line:
            stats = line.split("TERRAINSTATS", 1)[1].strip()
    return bench, stats


def main():
    rounds = int(sys.argv[1]) if len(sys.argv) > 1 else 3
    mask = sys.argv[2] if len(sys.argv) > 2 else "FFFF0000"
    results = {}
    try:
        for r in range(rounds):
            for mode in ("vanilla", "modern"):
                bench, stats = run(mode, r + 1, mask)
                print("%s round %d: %s" % (mode, r + 1, bench))
                if stats:
                    print("    %s" % stats)
                sys.stdout.flush()
                if bench:
                    g = float(re.search(r"gen_ms_per_chunk=([\d.]+)", bench).group(1))
                    s = float(re.search(r"save_ms_per_chunk=([\d.]+)", bench).group(1))
                    results.setdefault(mode, []).append((g, s))
    finally:
        set_height_enabled(True)
        set_level_type("DEFAULT")
        shutil.rmtree(os.path.join(ECL, "world"), ignore_errors=True)
    for m, vals in results.items():
        gens = [v[0] for v in vals]
        saves = [v[1] for v in vals]
        line = "%-8s gen best %.3f median %.3f ms/chunk (%.1f chunks/s at the median), save best %.3f ms/chunk" % (
            m, min(gens), statistics.median(gens), 1000.0 / statistics.median(gens), min(saves))
        if "vanilla" in results and m != "vanilla":
            vg = statistics.median([v[0] for v in results["vanilla"]])
            line += "  (gen x%.2f vanilla's median)" % (statistics.median(gens) / vg)
        print(line)


if __name__ == "__main__":
    main()
