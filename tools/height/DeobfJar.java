import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Makes a dev-named copy of a release mod jar, for the FG2 dev run ({@code tools/height/phosphor-selftest.sh}):
 * every SRG member name ({@code func_*}, {@code field_*}) in its classes becomes the MCP name. The dev environment does
 * not remap mod jars, and Mixin there neither uses the refmap nor renames a mixin's {@code @Shadow}/{@code @Overwrite}
 * members, so a release Phosphor fails in a dev run ("Overwrite target func_76615_h was not located"). SRG names are
 * unique, so the map is by name alone. Signature files are dropped (the classes change).
 *
 * <pre>  tools/height/run.sh DeobfJar &lt;srg-mcp.srg&gt; &lt;in jar&gt; &lt;out jar&gt;</pre>
 */
public final class DeobfJar {

    public static void main(String[] args) throws Exception {
        final Map<String, String> names = new HashMap<String, String>();
        for (String line : Files.readAllLines(Paths.get(args[0]))) {
            String[] p = line.split(" ");
            if (p[0].equals("FD:") && p.length >= 3) {
                names.put(last(p[1]), last(p[2]));
            } else if (p[0].equals("MD:") && p.length >= 5) {
                names.put(last(p[1]), last(p[3]));
            }
        }
        Remapper remapper = new Remapper() {
            @Override
            public String mapMethodName(String owner, String name, String desc) {
                String m = names.get(name);
                return m != null && name.startsWith("func_") ? m : name;
            }

            @Override
            public String mapFieldName(String owner, String name, String desc) {
                String m = names.get(name);
                return m != null && name.startsWith("field_") ? m : name;
            }
        };
        int classes = 0;
        // A jar may hold an entry twice (BuildCraft 8.0.0's fml_cache_annotation.json); the first one is kept.
        java.util.Set<String> seen = new java.util.HashSet<String>();
        try (ZipFile in = new ZipFile(args[1]); ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[2]))) {
            for (Enumeration<? extends ZipEntry> e = in.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                String n = entry.getName();
                if (!seen.add(n)) {
                    continue;
                }
                if (n.startsWith("META-INF/") && (n.endsWith(".SF") || n.endsWith(".DSA") || n.endsWith(".RSA"))) {
                    continue;
                }
                byte[] bytes = read(in.getInputStream(entry));
                if (n.endsWith(".class")) {
                    ClassWriter w = new ClassWriter(0);
                    new ClassReader(bytes).accept(new ClassRemapper(w, remapper), 0);
                    bytes = w.toByteArray();
                    classes++;
                }
                out.putNextEntry(new ZipEntry(n));
                out.write(bytes);
                out.closeEntry();
            }
        }
        System.out.println("DeobfJar: " + classes + " classes remapped to MCP names in " + args[2]);
    }

    private static String last(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static byte[] read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }
}
