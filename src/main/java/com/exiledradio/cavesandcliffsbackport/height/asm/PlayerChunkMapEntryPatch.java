package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * {@code PlayerChunkMapEntry}: vanilla packs a changed block into a short ({@code x << 12 | z << 8 | y}), which holds
 * y 0-255 only. A change outside that goes to a side list instead and is sent as a single-block packet on the entry's
 * next update ({@code height.HeightHooks}); changes inside 0-255 take vanilla's path, and the packet formats do not
 * change.
 */
final class PlayerChunkMapEntryPatch extends ClassPatch {

    private static final String ENTRY = "net/minecraft/server/management/PlayerChunkMapEntry";
    private static final String MAP_DESC = "Lnet/minecraft/server/management/PlayerChunkMap;";

    PlayerChunkMapEntryPatch() {
        super("net.minecraft.server.management.PlayerChunkMapEntry");
    }

    @Override
    protected void apply(ClassNode node) {
        boolean frames = (node.version & 0xFFFF) >= Opcodes.V1_6;

        MethodNode m = method(node, "blockChanged", "func_187265_a", "(III)V");
        if (site("blockChanged.head", m != null && !startsOnFrame(m))) {
            LabelNode vanilla = new LabelNode();
            InsnList head = list(new VarInsnNode(Opcodes.ILOAD, 2), new IntInsnNode(Opcodes.SIPUSH, -256),
                    insn(Opcodes.IAND), new JumpInsnNode(Opcodes.IFEQ, vanilla),
                    new VarInsnNode(Opcodes.ALOAD, 0),
                    new VarInsnNode(Opcodes.ALOAD, 0),
                    new FieldInsnNode(Opcodes.GETFIELD, ENTRY, n("playerChunkMap", "field_187282_b"), MAP_DESC),
                    new VarInsnNode(Opcodes.ALOAD, 0),
                    new FieldInsnNode(Opcodes.GETFIELD, ENTRY, n("sentToPlayers", "field_187290_j"), "Z"),
                    new VarInsnNode(Opcodes.ILOAD, 1), new VarInsnNode(Opcodes.ILOAD, 2), new VarInsnNode(Opcodes.ILOAD, 3),
                    hooks("blockChangedOutside", "(L" + ENTRY + ";" + MAP_DESC + "ZIII)V"),
                    insn(Opcodes.RETURN), vanilla);
            if (frames) {
                // Exactly the entry frame, so the method's own frames (relative to it) stay right.
                head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            }
            m.instructions.insert(head);
        }

        m = method(node, "update", "func_187280_d", "()V");
        if (site("update.head", m != null)) {
            m.instructions.insert(list(new VarInsnNode(Opcodes.ALOAD, 0), hooks("flushOutside", "(L" + ENTRY + ";)V")));
        }
    }

    private static boolean startsOnFrame(MethodNode m) {
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null && i.getOpcode() < 0; i = i.getNext()) {
            if (i instanceof FrameNode) {
                return true;
            }
        }
        return false;
    }
}
