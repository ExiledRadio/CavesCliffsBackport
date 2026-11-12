"""The removal test (EXTENDED_HEIGHT section 5.3): an extended world, with this mod's section list stripped, loads on a
Forge server WITHOUT the mod: no crash, y 0-255 intact, no chunk regenerated.

    python tools/height/removal.py [server dir]

Needs the extended test world that tools/height/selftest.sh leaves in eclipse/ (with eclipse/height-markers.txt,
blocks the self-test put by the spawn) and a plain Forge 1.12.2 server directory (default G:/VBPTest/cc_removal:
the Forge jar, minecraft_server.1.12.2.jar, libraries/, eula.txt, no mods). The world goes there as "world",
stripped by strip_sections.py; the server starts on port 25857, the markers are checked with /testforblock (the
spawn chunks stay loaded), and the server stops. Prints REMOVAL OK or the failures.
"""
import os
import re
import subprocess
import sys
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ECLIPSE = os.path.join(ROOT, "eclipse")
JAVA = os.environ.get("JAVA8", r"E:\MincraftModding\JDK8\bin\java.exe")


def main():
    server = sys.argv[1] if len(sys.argv) > 1 else r"G:\VBPTest\cc_removal"
    jars = [f for f in os.listdir(server) if f.startswith("forge-1.12.2") and f.endswith(".jar")]
    if not jars:
        sys.exit("no Forge jar in " + server)
    mods = os.path.join(server, "mods")
    if os.path.isdir(mods) and any(f.lower().startswith("cavesandcliffs") for f in os.listdir(mods)):
        sys.exit("the removal server must not have this mod: " + mods)
    markers = [line.split() for line in open(os.path.join(ECLIPSE, "height-markers.txt"), encoding="utf-8") if line.strip()]

    world = os.path.join(server, "world")
    strip = subprocess.run([sys.executable, "-I", os.path.join(HERE, "strip_sections.py"), os.path.join(ECLIPSE, "world"), world],
                           capture_output=True, text=True)
    print(strip.stdout.strip())
    if strip.returncode != 0:
        sys.exit(1)
    with open(os.path.join(server, "server.properties"), "w", encoding="utf-8") as f:
        f.write("level-name=world\nserver-port=25857\nonline-mode=false\nspawn-protection=0\nmax-tick-time=-1\n"
                "view-distance=4\nmotd=removal test\nallow-nether=false\nlevel-type=DEFAULT\n")
    with open(os.path.join(server, "eula.txt"), "w") as f:
        f.write("eula=true\n")

    # The world's chunks hold this mod's blocks: confirm the missing-registry prompt instead of waiting on it.
    proc = subprocess.Popen([JAVA, "-Xmx2G", "-Dfml.queryResult=confirm", "-jar", jars[0], "nogui"], cwd=server,
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
    print("removal server pid %d" % proc.pid)
    lines = []
    done = threading.Event()

    def pump():
        for line in proc.stdout:
            lines.append(line.rstrip())
            if "Done (" in line:
                done.set()
    threading.Thread(target=pump, daemon=True).start()

    failures = []
    if not done.wait(300):
        failures.append("the server did not finish starting in 5 minutes")
    else:
        for x, y, z, block in markers:
            proc.stdin.write("testforblock %s %s %s %s\n" % (x, y, z, block))
        proc.stdin.flush()
        time.sleep(5)
        proc.stdin.write("stop\n")
        proc.stdin.flush()
    try:
        proc.wait(120)
    except subprocess.TimeoutExpired:
        failures.append("the server did not stop; killed pid %d" % proc.pid)
        proc.kill()
    text = "\n".join(lines)
    open(os.path.join(server, "removal-run.log"), "w", encoding="utf-8").write(text)

    for x, y, z, block in markers:
        inside = 0 <= int(y) <= 255
        found = re.search(r"Successfully found the block at %s, ?%s, ?%s" % (x, y, z), text)
        if inside and not found:
            failures.append("marker %s at y %s is not there (chunk not read, or regenerated)" % (block, y))
        if not inside and found:
            failures.append("marker %s at y %s outside 0-255 is still found" % (block, y))
    for bad in ("Exception", "regenerat", "Wrong location", "missing level data", "Couldn't load chunk"):
        for line in lines:
            if bad in line and "MissingMappings" not in line:
                failures.append("log: " + line.strip()[:200])
                break
    for f in failures:
        print("FAIL " + f)
    print("REMOVAL OK" if not failures else "REMOVAL FAILED")
    sys.exit(0 if not failures else 1)


if __name__ == "__main__":
    main()
