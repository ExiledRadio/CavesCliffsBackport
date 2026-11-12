package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * The pattern language the patches are written in. A pattern is a run of consecutive real instructions (labels,
 * line numbers and frames between them are skipped); members match by MCP name (dev) or SRG name (an instance), plus
 * descriptor. Nothing here loads a class.
 */
public final class Asm {

    /** Where every patched site calls. */
    public static final String HEIGHTS = "com/exiledradio/cavesandcliffsbackport/height/Heights";
    public static final String LAYOUT = "com/exiledradio/cavesandcliffsbackport/height/HeightLayout";
    public static final String HOOKS = "com/exiledradio/cavesandcliffsbackport/height/HeightHooks";
    public static final String CLIENT_HOOKS = "com/exiledradio/cavesandcliffsbackport/height/client/HeightClientHooks";

    public static final String WORLD = "net/minecraft/world/World";
    public static final String WORLD_DESC = "Lnet/minecraft/world/World;";

    /**
     * True in a real instance, where Minecraft's members have SRG names; false in the dev environment (MCP names).
     * Set by the loading plugin from FML's {@code runtimeDeobfuscationEnabled}; matching accepts either, this only
     * picks the name of a member a patch emits.
     */
    public static volatile boolean srg = true;

    private Asm() {
    }

    /** The emitted name of a member: SRG in an instance, MCP in dev. */
    public static String n(String mcp, String srg) {
        return Asm.srg ? srg : mcp;
    }

    /** One instruction test. */
    public interface P {
        boolean m(AbstractInsnNode n);
    }

    public static P op(final int opcode) {
        return n -> n.getOpcode() == opcode;
    }

    /** An int load of a given local, or of any local when {@code var < 0}. */
    public static P iload(final int var) {
        return n -> n.getOpcode() == Opcodes.ILOAD && (var < 0 || ((VarInsnNode) n).var == var);
    }

    public static P aload(final int var) {
        return n -> n.getOpcode() == Opcodes.ALOAD && (var < 0 || ((VarInsnNode) n).var == var);
    }

    public static P istore(final int var) {
        return n -> n.getOpcode() == Opcodes.ISTORE && (var < 0 || ((VarInsnNode) n).var == var);
    }

    /** An int constant, in whatever form javac chose. */
    public static P icst(final int value) {
        return n -> {
            Integer v = intValue(n);
            return v != null && v == value;
        };
    }

    public static P dcst(final double value) {
        return n -> n instanceof LdcInsnNode && Double.valueOf(value).equals(((LdcInsnNode) n).cst);
    }

    public static P field(final int opcode, final String mcp, final String srg) {
        return n -> n.getOpcode() == opcode && is(((FieldInsnNode) n).name, mcp, srg);
    }

    public static P invoke(final int opcode, final String mcp, final String srg, final String desc) {
        return n -> n.getOpcode() == opcode && is(((MethodInsnNode) n).name, mcp, srg)
                && (desc == null || desc.equals(((MethodInsnNode) n).desc));
    }

    /** {@code BlockPos.getY()} through any owner (BlockPos, MutableBlockPos, Vec3i). */
    public static P getY() {
        return invoke(Opcodes.INVOKEVIRTUAL, "getY", "func_177956_o", "()I");
    }

    /** One of the zero-compare jumps. */
    public static P zeroJump() {
        return n -> {
            int o = n.getOpcode();
            return o == Opcodes.IFLT || o == Opcodes.IFGE || o == Opcodes.IFGT || o == Opcodes.IFLE;
        };
    }

    public static boolean is(String name, String mcp, String srg) {
        return name.equals(mcp) || name.equals(srg);
    }

    static Integer intValue(AbstractInsnNode n) {
        int o = n.getOpcode();
        if (o >= Opcodes.ICONST_M1 && o <= Opcodes.ICONST_5) {
            return o - Opcodes.ICONST_0;
        }
        if (o == Opcodes.BIPUSH || o == Opcodes.SIPUSH) {
            return ((IntInsnNode) n).operand;
        }
        if (o == Opcodes.LDC && ((LdcInsnNode) n).cst instanceof Integer) {
            return (Integer) ((LdcInsnNode) n).cst;
        }
        return null;
    }

    public static MethodNode method(org.objectweb.asm.tree.ClassNode node, String mcp, String srg, String desc) {
        for (MethodNode m : node.methods) {
            if (is(m.name, mcp, srg) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }

    /** The next real instruction after {@code n}, or null. */
    public static AbstractInsnNode next(AbstractInsnNode n) {
        AbstractInsnNode i = n.getNext();
        while (i != null && i.getOpcode() < 0) {
            i = i.getNext();
        }
        return i;
    }

    /** The real instruction {@code k} steps after {@code n} (0 is {@code n}). */
    public static AbstractInsnNode at(AbstractInsnNode n, int k) {
        AbstractInsnNode i = n;
        for (int s = 0; s < k && i != null; s++) {
            i = next(i);
        }
        return i;
    }

    /** Every place in the method where the pattern starts; each result is the pattern's first instruction. */
    public static List<AbstractInsnNode> find(MethodNode m, P... pattern) {
        List<AbstractInsnNode> out = new ArrayList<AbstractInsnNode>();
        if (m == null) {
            return out;
        }
        for (AbstractInsnNode start = m.instructions.getFirst(); start != null; start = start.getNext()) {
            if (start.getOpcode() < 0) {
                continue;
            }
            AbstractInsnNode i = start;
            boolean ok = true;
            for (int k = 0; k < pattern.length; k++) {
                if (i == null || !pattern[k].m(i)) {
                    ok = false;
                    break;
                }
                i = next(i);
            }
            if (ok) {
                out.add(start);
            }
        }
        return out;
    }

    /** Replaces one instruction by a list (which may be empty). */
    public static void replace(MethodNode m, AbstractInsnNode n, InsnList with) {
        m.instructions.insertBefore(n, with);
        m.instructions.remove(n);
    }

    // ------------------------------------------------------------------------------------------
    // Small instruction lists.
    // ------------------------------------------------------------------------------------------

    public static InsnList list(AbstractInsnNode... nodes) {
        InsnList l = new InsnList();
        for (AbstractInsnNode n : nodes) {
            l.add(n);
        }
        return l;
    }

    public static MethodInsnNode heights(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HEIGHTS, name, desc, false);
    }

    public static MethodInsnNode layout(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, LAYOUT, name, desc, false);
    }

    public static MethodInsnNode hooks(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, desc, false);
    }

    /** {@code HeightLayout.slot(int)}: replaces {@code iconst_4; ishr} on a y. */
    public static MethodInsnNode slot() {
        return layout("slot", "(I)I");
    }

    /** Pushes the world, then calls a {@code (World)I} method of Heights on it. */
    public static InsnList worldInt(InsnList pushWorld, String heightsMethod) {
        pushWorld.add(heights(heightsMethod, "(" + WORLD_DESC + ")I"));
        return pushWorld;
    }

    /** {@code aload 0} */
    public static InsnList self() {
        return list(new VarInsnNode(Opcodes.ALOAD, 0));
    }

    /** {@code aload 0; getfield owner.name} */
    public static InsnList selfField(String owner, String name, String desc) {
        return list(new VarInsnNode(Opcodes.ALOAD, 0), new FieldInsnNode(Opcodes.GETFIELD, owner, name, desc));
    }

    public static InsnNode insn(int opcode) {
        return new InsnNode(opcode);
    }

    /** The comparing jump that replaces a zero-compare jump ({@code IFLT} becomes {@code IF_ICMPLT}, ...). */
    public static int twoOperand(int zeroJump) {
        switch (zeroJump) {
            case Opcodes.IFLT:
                return Opcodes.IF_ICMPLT;
            case Opcodes.IFGE:
                return Opcodes.IF_ICMPGE;
            case Opcodes.IFGT:
                return Opcodes.IF_ICMPGT;
            case Opcodes.IFLE:
                return Opcodes.IF_ICMPLE;
            default:
                throw new IllegalArgumentException("not a zero-compare jump: " + zeroJump);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Rewrites shared by the patches. None adds a jump or changes the stack at a jump target.
    // ------------------------------------------------------------------------------------------

    /**
     * Turns a compare-with-zero jump ({@code y < 0}) into a compare with the value {@code push} leaves on the stack
     * ({@code y < minY}): the pushed value goes in front of the jump, which becomes its two-operand form.
     */
    public static void compareWith(MethodNode m, AbstractInsnNode zeroJump, InsnList push) {
        m.instructions.insertBefore(zeroJump, push);
        ((org.objectweb.asm.tree.JumpInsnNode) zeroJump).setOpcode(twoOperand(zeroJump.getOpcode()));
    }

    /** Replaces {@code iconst_4; ishr} (starting at {@code iconst4}) with {@code HeightLayout.slot(int)}. */
    public static void shiftToSlot(MethodNode m, AbstractInsnNode iconst4) {
        AbstractInsnNode ishr = next(iconst4);
        if (ishr == null || ishr.getOpcode() != Opcodes.ISHR) {
            throw new IllegalStateException("iconst_4 not followed by ishr");
        }
        m.instructions.remove(ishr);
        replace(m, iconst4, list(slot()));
    }
}
