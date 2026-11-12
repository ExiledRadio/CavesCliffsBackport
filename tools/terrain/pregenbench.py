"""Head-to-head pregeneration benchmark: plain Forge 14.23.5.2860 servers with Chunk Pregenerator, one per terrain,
the same seed, each pinned to the same cores, a fresh world per run:

  van  vanilla 1.12 (Default world)
  cnc  Caves Not Cliffs 2.0.4 (+ Cave Biomes API, MixinBootstrap)
  ffd  Farmer's Future Delight 1.4.6, its Caves & Cliffs world type (ffd_cac)
  cc   this mod's Caves & Cliffs world (+ Backport Core, Fluidlogged API)

Each run waits for the server's "Done", sends `pregen gen startradius square 300 300 16` (33x33 chunks around chunk
300, 300, generated and populated), times it to the "Pregenerated" line on the wall clock, and stops the server.

    python tools/terrain/pregenbench.py <dir with van/cnc/ffd/cc> [rounds] [affinity mask, hex] [modes,comma,separated]

The default mask FFFF0000 is the 14900K's 16 E-cores (logical processors 16-31). Prints chunks/s per run and the
median per terrain. Kills only the servers it started, by PID.
"""
import os
import shutil
import statistics
import subprocess
import sys
import threading
import time

JAVA = r"C:\Program Files\Java\jdk-1.8\bin\java.exe"
JAR = "forge-1.12.2-14.23.5.2860.jar"


def run(base, mode, mask, log):
    d = os.path.join(base, mode)
    shutil.rmtree(os.path.join(d, "world"), ignore_errors=True)
    proc = subprocess.Popen([JAVA, "-Xms4G", "-Xmx4G", "-jar", JAR, "nogui"], cwd=d, stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, bufsize=1, universal_newlines=True,
                            encoding="utf-8", errors="replace")
    subprocess.run(["powershell", "-NoProfile", "-Command",
                    "(Get-Process -Id %d).ProcessorAffinity = [IntPtr]%d" % (proc.pid, int(mask, 16))], check=False)
    killer = threading.Timer(1200, proc.kill)
    killer.start()
    started = None
    result = None
    with open(log, "w", encoding="utf-8") as out:
        for line in proc.stdout:
            out.write(line)
            if started is None and "Done (" in line:
                started = time.time()
                proc.stdin.write("pregen gen startradius square 300 300 16\n")
                proc.stdin.flush()
            elif started is not None and result is None and "Pregenerated:" in line:
                result = (time.time() - started, line.strip())
                proc.stdin.write("stop\n")
                proc.stdin.flush()
    proc.wait()
    killer.cancel()
    return result


def main():
    base = sys.argv[1]
    rounds = int(sys.argv[2]) if len(sys.argv) > 2 else 3
    mask = sys.argv[3] if len(sys.argv) > 3 else "FFFF0000"
    modes = sys.argv[4].split(",") if len(sys.argv) > 4 else ["van", "cc", "cnc", "ffd"]
    rates = {}
    for r in range(rounds):
        for mode in modes:
            res = run(base, mode, mask, os.path.join(base, "%s-r%d.log" % (mode, r + 1)))
            if res is None:
                print("%s round %d: no result (see the log)" % (mode, r + 1))
                continue
            seconds, line = res
            done = int(line.split("Pregenerated: ", 1)[1].split(" ", 1)[0])
            rate = done / seconds
            rates.setdefault(mode, []).append(rate)
            print("%s round %d: %d chunks in %.1f s, %.1f chunks/s" % (mode, r + 1, done, seconds, rate))
            sys.stdout.flush()
    for mode, v in rates.items():
        line = "%-4s median %.1f chunks/s (%s)" % (mode, statistics.median(v), ", ".join("%.1f" % x for x in v))
        if "van" in rates and mode != "van":
            line += ", %.2fx vanilla's time per chunk" % (statistics.median(rates["van"]) / statistics.median(v))
        print(line)


if __name__ == "__main__":
    main()
