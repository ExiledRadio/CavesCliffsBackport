"""Copies a jar without its signature (META-INF/*.SF, *.RSA, *.DSA and the manifest's digests), so classes whose
digests no longer match (the remapped client jar) load. Usage: python unsign.py <in.jar> <out.jar>"""
import sys, zipfile

src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as z, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as o:
    for e in z.infolist():
        n = e.filename
        if n.startswith("META-INF/") and (n.endswith(".SF") or n.endswith(".RSA") or n.endswith(".DSA") or n == "META-INF/MANIFEST.MF"):
            continue
        o.writestr(e, z.read(n))
