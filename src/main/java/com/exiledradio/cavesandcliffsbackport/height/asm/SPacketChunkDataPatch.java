package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * {@code SPacketChunkData(Chunk, int)}: "full chunk" ({@code 65535}) becomes all of an extended chunk's 24 sections.
 * The section mask is already an int and the format is unchanged; the client's {@code Chunk.read} reads by slot.
 */
final class SPacketChunkDataPatch extends ClassPatch {

    private static final String CHUNK_DESC = "Lnet/minecraft/world/chunk/Chunk;";

    SPacketChunkDataPatch() {
        super("net.minecraft.network.play.server.SPacketChunkData");
    }

    @Override
    protected void apply(ClassNode node) {
        MethodNode m = method(node, "<init>", "<init>", "(" + CHUNK_DESC + "I)V");
        for (AbstractInsnNode n : expect("<init>.filter", m, 1, aload(0),
                invoke(Opcodes.INVOKESPECIAL, "<init>", "<init>", "()V"))) {
            m.instructions.insert(next(n), list(new VarInsnNode(Opcodes.ILOAD, 2), new VarInsnNode(Opcodes.ALOAD, 1),
                    hooks("chunkPacketFilter", "(I" + CHUNK_DESC + ")I"), new VarInsnNode(Opcodes.ISTORE, 2)));
        }
        for (AbstractInsnNode n : expect("<init>.full", m, 1, iload(2), icst(65535), op(Opcodes.IF_ICMPNE))) {
            replace(m, next(n), list(new VarInsnNode(Opcodes.ALOAD, 1), hooks("fullChunkFilter", "(" + CHUNK_DESC + ")I")));
        }
        for (AbstractInsnNode n : expect("<init>.tileSection", m, 1, getY(), icst(4), op(Opcodes.ISHR))) {
            shiftToSlot(m, next(n));
        }
    }
}
