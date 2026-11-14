import com.exiledradio.cavesandcliffsbackport.height.asm.Asm;
import com.exiledradio.cavesandcliffsbackport.height.asm.ClassPatch;
import com.exiledradio.cavesandcliffsbackport.height.asm.HeightTransformer;
import com.exiledradio.cavesandcliffsbackport.height.asm.PatchReport;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The pattern-drift check, offline: runs every extended-height patch against the classes of a Minecraft jar and
 * prints, per site, found or NOT FOUND; then checks every method of every patched class with ASM's basic verifier
 * (stack shapes and types). Exit status 1 on any miss or verify error.
 *
 * <pre>
 *   tools/height/run.sh PatchCheck mcp    &lt;forgeSrc jar&gt;  [celeritas jar [nothirium jar [vintagefix jar [alfheim jar]]]]   (dev names)
 *   tools/height/run.sh PatchCheck srg    &lt;forgeSrc jar&gt;  &lt;mcp-srg.srg&gt; [celeritas jar [nothirium jar [vintagefix jar [alfheim jar]]]]   (an instance's names)
 * </pre>
 * No jar on disk holds Forge-patched classes with SRG names (an instance makes them at run time), so the srg mode
 * renames the forgeSrc jar's classes from MCP to SRG names itself, walking superclasses for inherited members.
 * Both sides are checked: the server's patch set, then the client's.
 */
public final class PatchCheck {

    public static void main(String[] args) throws Exception {
        Asm.srg = "srg".equals(args[0]);
        ZipFile game = new ZipFile(args[1]);
        int next = 2;
        if (Asm.srg) {
            mappings = new java.util.HashMap<String, String>();
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(args[next++]))) {
                String[] p = line.split(" ");
                if (p[0].equals("MD:")) {
                    mappings.put(p[1] + p[2], p[3].substring(p[3].lastIndexOf('/') + 1));
                } else if (p[0].equals("FD:")) {
                    mappings.put(p[1], p[2].substring(p[2].lastIndexOf('/') + 1));
                }
            }
            gameJar = game;
        }
        ZipFile celeritas = args.length > next ? new ZipFile(args[next]) : null;
        ZipFile nothirium = args.length > next + 1 ? new ZipFile(args[next + 1]) : null;
        ZipFile vintagefix = args.length > next + 2 ? new ZipFile(args[next + 2]) : null;
        ZipFile alfheim = args.length > next + 3 ? new ZipFile(args[next + 3]) : null;
        int errors = 0;
        for (boolean client : new boolean[] {false, true}) {
            System.out.println("== " + (client ? "client" : "server") + " patches, " + args[0] + " names");
            for (ClassPatch patch : HeightTransformer.all(client)) {
                String path = patch.target.replace('.', '/') + ".class";
                ZipFile from = patch.target.startsWith("org.taumc.") ? celeritas
                        : patch.target.startsWith("meldexun.") ? nothirium
                        : patch.target.startsWith("org.embeddedt.vintagefix.") ? vintagefix
                        : patch.target.startsWith("dev.redstudio.alfheim.") ? alfheim : game;
                if (from == null || from.getEntry(path) == null) {
                    System.out.println("   (skipped " + patch.target + ": not in the given jars)");
                    continue;
                }
                byte[] in = read(from, from.getEntry(path));
                if (Asm.srg && from == game) {
                    in = toSrg(in);
                }
                byte[] out = patch.transform(in);
                if (out == in) {
                    System.out.println("   UNPATCHED " + patch.target);
                    errors++;
                    continue;
                }
                errors += verify(out);
            }
            for (String line : PatchReport.lines()) {
                System.out.println("   " + line);
            }
            errors += PatchReport.missed().size();
            PatchReport.clear();
        }
        System.out.println(errors == 0 ? "PATCHCHECK OK" : "PATCHCHECK FAILED: " + errors + " problem(s)");
        System.exit(errors == 0 ? 0 : 1);
    }

    private static java.util.Map<String, String> mappings;
    private static ZipFile gameJar;
    private static final java.util.Map<String, String> SUPERS = new java.util.HashMap<String, String>();

    /** MCP to SRG member names; an inherited member is found on the superclass that declares it. */
    private static byte[] toSrg(byte[] in) {
        ClassWriter w = new ClassWriter(0);
        new ClassReader(in).accept(new ClassRemapper(w, new Remapper() {
            @Override
            public String mapMethodName(String owner, String name, String desc) {
                for (String o = owner; o != null; o = superOf(o)) {
                    String srg = mappings.get(o + "/" + name + desc);
                    if (srg != null) {
                        return srg;
                    }
                }
                return name;
            }

            @Override
            public String mapFieldName(String owner, String name, String desc) {
                for (String o = owner; o != null; o = superOf(o)) {
                    String srg = mappings.get(o + "/" + name);
                    if (srg != null) {
                        return srg;
                    }
                }
                return name;
            }
        }), 0);
        return w.toByteArray();
    }

    private static String superOf(String owner) {
        if (!SUPERS.containsKey(owner)) {
            String sup = null;
            try {
                ZipEntry e = gameJar.getEntry(owner + ".class");
                if (e != null) {
                    sup = new ClassReader(read(gameJar, e)).getSuperName();
                }
            } catch (Exception ignored) {
            }
            SUPERS.put(owner, sup);
        }
        return SUPERS.get(owner);
    }

    private static int verify(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int errors = 0;
        for (MethodNode m : node.methods) {
            try {
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name, m);
            } catch (AnalyzerException e) {
                System.out.println("   VERIFY " + node.name + "." + m.name + m.desc + ": " + e.getMessage());
                errors++;
            }
        }
        return errors;
    }

    private static byte[] read(ZipFile zip, ZipEntry entry) throws Exception {
        InputStream in = zip.getInputStream(entry);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }
}
