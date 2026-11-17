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
import org.objectweb.asm.tree.VarInsnNode;
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
 *   tools/height/run.sh PatchCheck mcp    &lt;forgeSrc jar&gt;  [celeritas jar [nothirium jar [vintagefix jar [alfheim jar [eaglemixins jar [phosphor jar]]]]]]   (dev names)
 *   tools/height/run.sh PatchCheck srg    &lt;forgeSrc jar&gt;  &lt;mcp-srg.srg&gt; [celeritas jar [nothirium jar [vintagefix jar [alfheim jar [eaglemixins jar [phosphor jar]]]]]]   (an instance's names)
 *   tools/height/run.sh PatchCheck dump   &lt;dir&gt;   (classes an instance's transformer received, SRG names)
 * </pre>
 * The dump mode reads {@code <dir>/<class name>.class} as written by {@code -Dcavesandcliffsbackport.height.dump}
 * (for example OptiFine's classes from the Shaders instance) and checks the patches whose class is there.
 * <p>
 * Besides ASM's verifier (which infers types itself and ignores the class's stack map frames), a frame check reads
 * the frames as the JVM does: a local read after a frame must be declared by that frame (or stored since), and code
 * after a goto, return, throw or switch must start with a frame. ProGuard (OptiFine) writes frames that drop dead
 * locals, so code a patch inserts after one must not read a local the frame no longer holds.
 * No jar on disk holds Forge-patched classes with SRG names (an instance makes them at run time), so the srg mode
 * renames the forgeSrc jar's classes from MCP to SRG names itself, walking superclasses for inherited members.
 * Both sides are checked: the server's patch set, then the client's.
 */
public final class PatchCheck {

    public static void main(String[] args) throws Exception {
        if ("dump".equals(args[0])) {
            System.exit(dump(new java.io.File(args[1])));
        }
        if ("compat".equals(args[0])) {
            System.exit(compat(java.util.Arrays.copyOfRange(args, 1, args.length)));
        }
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
        ZipFile eaglemixins = args.length > next + 4 ? new ZipFile(args[next + 4]) : null;
        ZipFile phosphor = args.length > next + 5 ? new ZipFile(args[next + 5]) : null;
        int errors = 0;
        for (boolean client : new boolean[] {false, true}) {
            System.out.println("== " + (client ? "client" : "server") + " patches, " + args[0] + " names");
            for (ClassPatch patch : HeightTransformer.all(client)) {
                String path = patch.target.replace('.', '/') + ".class";
                ZipFile from = patch.target.startsWith("org.taumc.") ? celeritas
                        : patch.target.startsWith("meldexun.") ? nothirium
                        : patch.target.startsWith("org.embeddedt.vintagefix.") ? vintagefix
                        : patch.target.startsWith("dev.redstudio.alfheim.") ? alfheim
                        : patch.target.startsWith("eaglemixins.") ? eaglemixins
                        : patch.target.startsWith("me.jellysquid.mods.phosphor.") ? phosphor : game;
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

    /** The dump mode: every patch whose class is in the directory. */
    /**
     * {@code PatchCheck compat <mod jar>...}: the mod-compat patches (O3, {@code HeightTransformer.modCompat()}) against
     * release mod jars (SRG names, as an instance has them). Every target class must be in one of the jars and patched.
     */
    private static int compat(String[] jars) throws Exception {
        Asm.srg = true;
        int errors = 0;
        System.out.println("== mod-compat patches, srg names");
        for (ClassPatch patch : HeightTransformer.modCompat()) {
            String path = patch.target.replace('.', '/') + ".class";
            ZipFile from = null;
            for (String jar : jars) {
                ZipFile z = new ZipFile(jar);
                if (z.getEntry(path) != null) {
                    from = z;
                    break;
                }
                z.close();
            }
            if (from == null) {
                System.out.println("   (skipped " + patch.target + ": not in the given jars)");
                continue;
            }
            byte[] in = read(from, from.getEntry(path));
            byte[] out = patch.transform(in);
            if (out == in) {
                System.out.println("   UNPATCHED " + patch.target);
                errors++;
                continue;
            }
            errors += verify(out);
        }
        for (String line : PatchReport.compatLines()) {
            System.out.println("   " + line);
            if (line.endsWith("NOT FOUND")) {
                errors++;
            }
        }
        System.out.println(errors == 0 ? "OK" : errors + " error(s)");
        return errors == 0 ? 0 : 1;
    }

    private static int dump(java.io.File dir) throws Exception {
        Asm.srg = true;
        int errors = 0;
        int checked = 0;
        for (boolean client : new boolean[] {false, true}) {
            System.out.println("== " + (client ? "client" : "server") + " patches, dumped classes in " + dir);
            for (ClassPatch patch : HeightTransformer.all(client)) {
                java.io.File f = new java.io.File(dir, patch.target + ".class");
                if (!f.isFile()) {
                    System.out.println("   (skipped " + patch.target + ": not dumped)");
                    continue;
                }
                byte[] in = java.nio.file.Files.readAllBytes(f.toPath());
                int version = ((in[6] & 0xFF) << 8) | (in[7] & 0xFF);
                if (version > 52) {
                    // Cleanroom's transformers write some classes at their own Java version (69 for Java 25); this
                    // tool's ASM 5 reads up to Java 8, and none of these classes uses a later constant kind.
                    System.out.println("   (" + patch.target + ": class version " + version + ", read as 52)");
                    in[6] = 0;
                    in[7] = 52;
                }
                errors += frames(in, "unpatched ");
                byte[] out = patch.transform(in);
                checked++;
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
        if (checked == 0) {
            System.out.println("   no patched class in " + dir);
            errors++;
        }
        System.out.println(errors == 0 ? "PATCHCHECK OK" : "PATCHCHECK FAILED: " + errors + " problem(s)");
        return errors == 0 ? 0 : 1;
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
        return errors + frames(bytes, "");
    }

    /**
     * The frame check (see the class comment), over every method. {@code what} prefixes the messages ("unpatched "
     * for an input class, to tell a pre-existing oddity from one a patch made).
     */
    private static int frames(byte[] bytes, String what) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        if ((node.version & 0xFFFF) < org.objectweb.asm.Opcodes.V1_6) {
            return 0;
        }
        int errors = 0;
        for (MethodNode m : node.methods) {
            if (m.instructions.size() == 0) {
                continue;
            }
            java.util.Set<Integer> live = new java.util.HashSet<Integer>();
            int slot = 0;
            if ((m.access & org.objectweb.asm.Opcodes.ACC_STATIC) == 0) {
                live.add(slot++);
            }
            for (org.objectweb.asm.Type t : org.objectweb.asm.Type.getArgumentTypes(m.desc)) {
                live.add(slot);
                if (t.getSize() == 2) {
                    live.add(slot + 1);
                }
                slot += t.getSize();
            }
            boolean needFrame = false;
            for (org.objectweb.asm.tree.AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof org.objectweb.asm.tree.FrameNode) {
                    org.objectweb.asm.tree.FrameNode f = (org.objectweb.asm.tree.FrameNode) i;
                    live.clear();
                    int s = 0;
                    if (f.local != null) {
                        for (Object o : f.local) {
                            boolean wide = o == org.objectweb.asm.Opcodes.LONG || o == org.objectweb.asm.Opcodes.DOUBLE;
                            if (o != org.objectweb.asm.Opcodes.TOP) {
                                live.add(s);
                                if (wide) {
                                    live.add(s + 1);
                                }
                            }
                            s += wide ? 2 : 1;
                        }
                    }
                    needFrame = false;
                    continue;
                }
                int op = i.getOpcode();
                if (op < 0) {
                    continue;
                }
                String at = what + node.name + "." + m.name + m.desc + " at instruction " + m.instructions.indexOf(i);
                if (needFrame) {
                    System.out.println("   FRAME " + at + ": code after an unconditional jump has no frame");
                    errors++;
                    needFrame = false;
                }
                if (i instanceof VarInsnNode || i instanceof org.objectweb.asm.tree.IincInsnNode) {
                    int var = i instanceof VarInsnNode ? ((VarInsnNode) i).var : ((org.objectweb.asm.tree.IincInsnNode) i).var;
                    boolean store = op >= org.objectweb.asm.Opcodes.ISTORE && op <= org.objectweb.asm.Opcodes.ASTORE;
                    if (store) {
                        live.add(var);
                        if (op == org.objectweb.asm.Opcodes.LSTORE || op == org.objectweb.asm.Opcodes.DSTORE) {
                            live.add(var + 1);
                        }
                    } else if (!live.contains(var)) {
                        System.out.println("   FRAME " + at + ": reads local " + var + ", which the frame before it does not hold");
                        errors++;
                    }
                }
                needFrame = op == org.objectweb.asm.Opcodes.GOTO || op == org.objectweb.asm.Opcodes.ATHROW
                        || (op >= org.objectweb.asm.Opcodes.IRETURN && op <= org.objectweb.asm.Opcodes.RETURN)
                        || op == org.objectweb.asm.Opcodes.TABLESWITCH || op == org.objectweb.asm.Opcodes.LOOKUPSWITCH;
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
