package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The patches of one target class. Each site is found by an exact pattern with an exact number of matches; if any
 * site of the class is not found, the class is left exactly as it was (no half-patched class) and the miss is
 * reported, which turns extended height off for the session.
 */
public abstract class ClassPatch {

    /** Dotted class name. */
    public final String target;
    private final String label;
    private boolean complete;

    protected ClassPatch(String target) {
        this(target, target.substring(target.lastIndexOf('.') + 1));
    }

    /** With a report label of its own, for a class whose simple name another patch's target already has. */
    protected ClassPatch(String target, String label) {
        this.target = target;
        this.label = label;
    }

    /** Finds and applies every site, recording each with {@link #site} or {@link #expect}. */
    protected abstract void apply(ClassNode node);

    public final byte[] transform(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        this.complete = true;
        try {
            apply(node);
        } catch (RuntimeException e) {
            HeightCorePlugin.LOG.error("Extended height: patching {} threw; the class is left unpatched.", this.target, e);
            site("threw " + e, false);
        }
        if (!this.complete) {
            PatchReport.classSkipped(this.target);
            HeightCorePlugin.LOG.error("Extended height: {} is left unpatched because a site was not found (above); "
                    + "extended height is off for this session.", this.target);
            return bytes;
        }
        // Frames are kept, not recomputed: computing them would load classes from inside a transformer. Every patch
        // either keeps the stack at each jump as it was, or adds its own frame.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        HeightCorePlugin.LOG.debug("Extended height: patched {}.", this.target);
        return writer.toByteArray();
    }

    /** Records one site as found or not. */
    protected final boolean site(String id, boolean found) {
        PatchReport.site(this.label + "." + id, found);
        if (!found) {
            this.complete = false;
            HeightCorePlugin.LOG.error("Extended height: site {}.{} NOT FOUND.", this.label, id);
        }
        return found;
    }

    /**
     * The matches of a pattern in a method, recorded as one site that is found when there are exactly {@code count}
     * matches. Returns no matches when the site is missed, so callers can apply whatever comes back.
     */
    protected final List<AbstractInsnNode> expect(String id, MethodNode m, int count, Asm.P... pattern) {
        List<AbstractInsnNode> found = m == null ? new ArrayList<AbstractInsnNode>() : Asm.find(m, pattern);
        if (!site(id, found.size() == count)) {
            if (m != null) {
                HeightCorePlugin.LOG.error("Extended height: {}.{}: expected {} match(es) in {}{}, found {}.",
                        this.label, id, count, m.name, m.desc, found.size());
            } else {
                HeightCorePlugin.LOG.error("Extended height: {}.{}: method not found.", this.label, id);
            }
            return Collections.emptyList();
        }
        return found;
    }

    /**
     * The usual range check: {@code pos.getY() <cmp> 0} becomes a compare with the world's bottom ({@code mins}
     * matches), and {@code pos.getY() <cmp> 256} with its top ({@code maxes} matches).
     */
    protected final void range(MethodNode m, String id, int mins, int maxes,
                               java.util.function.Supplier<org.objectweb.asm.tree.InsnList> pushWorld) {
        for (AbstractInsnNode n : expect(id + ".min", m, mins, Asm.getY(), Asm.zeroJump())) {
            Asm.compareWith(m, Asm.next(n), Asm.worldInt(pushWorld.get(), "minY"));
        }
        if (maxes > 0) {
            for (AbstractInsnNode n : expect(id + ".max", m, maxes, Asm.getY(), Asm.icst(256))) {
                Asm.replace(m, Asm.next(n), Asm.worldInt(pushWorld.get(), "maxY"));
            }
        }
    }

    /** A method that must exist, recorded as a site of its own when it does not. */
    protected final MethodNode method(ClassNode node, String mcp, String srg, String desc) {
        MethodNode m = Asm.method(node, mcp, srg, desc);
        if (m == null) {
            site(mcp + desc + " exists", false);
        }
        return m;
    }
}
