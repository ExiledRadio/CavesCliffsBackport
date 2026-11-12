package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * {@code AnvilChunkLoader}: vanilla's {@code Sections} list keeps exactly the 16 sections of y 0-255, written as
 * vanilla writes them; the sections below 0 and above 255 go to {@code cavesandcliffsbackport:Sections} with a signed
 * {@code Y}, which vanilla never reads ({@code height.HeightHooks}).
 */
final class AnvilChunkLoaderPatch extends ClassPatch {

    private static final String CHUNK_DESC = "Lnet/minecraft/world/chunk/Chunk;";
    private static final String NBT_DESC = "Lnet/minecraft/nbt/NBTTagCompound;";
    private static final String EBS = "net/minecraft/world/chunk/storage/ExtendedBlockStorage";

    AnvilChunkLoaderPatch() {
        super("net.minecraft.world.chunk.storage.AnvilChunkLoader");
    }

    @Override
    protected void apply(ClassNode node) {
        MethodNode m = method(node, "writeChunkToNBT", "func_75820_a", "(" + CHUNK_DESC + WORLD_DESC + NBT_DESC + ")V");
        for (AbstractInsnNode n : expect("write.vanillaSections", m, 1,
                invoke(Opcodes.INVOKEVIRTUAL, "getBlockStorageArray", "func_76587_i", "()[L" + EBS + ";"))) {
            replace(m, n, list(hooks("vanillaSections", "(" + CHUNK_DESC + ")[L" + EBS + ";")));
        }
        for (AbstractInsnNode n : expect("write.ourSections", m, 1, aload(3), ldc("Sections"), aload(-1),
                invoke(Opcodes.INVOKEVIRTUAL, "setTag", "func_74782_a", "(Ljava/lang/String;Lnet/minecraft/nbt/NBTBase;)V"))) {
            m.instructions.insert(at(n, 3), list(new VarInsnNode(Opcodes.ALOAD, 1), new VarInsnNode(Opcodes.ALOAD, 2),
                    new VarInsnNode(Opcodes.ALOAD, 3), hooks("writeSections", "(" + CHUNK_DESC + WORLD_DESC + NBT_DESC + ")V")));
        }

        m = method(node, "readChunkFromNBT", "func_75823_a", "(" + WORLD_DESC + NBT_DESC + ")" + CHUNK_DESC);
        for (AbstractInsnNode n : expect("read.sections", m, 1, icst(16),
                x -> x.getOpcode() == Opcodes.ANEWARRAY && EBS.equals(((TypeInsnNode) x).desc))) {
            replace(m, n, list(new VarInsnNode(Opcodes.ALOAD, 1), heights("sections", "(" + WORLD_DESC + ")I")));
        }
        for (AbstractInsnNode n : expect("read.ourSections", m, 1, aload(-1), aload(-1),
                invoke(Opcodes.INVOKEVIRTUAL, "setStorageArrays", "func_76602_a", "([L" + EBS + ";)V"))) {
            int chunk = ((VarInsnNode) n).var;
            m.instructions.insert(at(n, 2), list(new VarInsnNode(Opcodes.ALOAD, chunk), new VarInsnNode(Opcodes.ALOAD, 2),
                    hooks("readSections", "(" + CHUNK_DESC + NBT_DESC + ")V")));
        }
    }

    private static P ldc(final String value) {
        return x -> x instanceof LdcInsnNode && value.equals(((LdcInsnNode) x).cst);
    }
}
