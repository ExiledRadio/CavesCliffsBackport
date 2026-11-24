package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * Valkyrie 0.3 (Red Studio; a 1.12.2 client optimization mod): without OptiFine it {@code @Overwrite}s the
 * {@code ViewFrustum} methods that place and find render chunks and {@code RenderGlobal.getRenderChunkOffset}, with
 * y counted from 0 and the walk bounded by 0-256. Mixin applies them after this coremod's transformer, so they replace
 * {@link ClientPatches.ViewFrustumPatch}'s and {@link ClientPatches.RenderGlobalPatch}'s sites: the frustum ran from
 * y 0 to 383, nothing below 0 was drawn, and a camera above y 320 crashed {@code renderEntities}. The mixin classes are
 * patched as Mixin reads them through the transformer chain (as {@link AlfheimPatches}), with the same changes as
 * vanilla's methods get. Patterns are Valkyrie 0.3-Dev-5's; a miss turns extended height off, with Valkyrie named
 * ({@code HeightContent}). Valkyrie's other mixins do not touch height.
 */
final class ValkyriePatches {

    static final String LABEL_FRUSTUM = "ValkyrieViewFrustumMixin";
    static final String LABEL_GLOBAL = "ValkyrieRenderGlobalMixin";
    private static final String WORLD_CLIENT_DESC = "Lnet/minecraft/client/multiplayer/WorldClient;";

    private ValkyriePatches() {
    }

    /** {@code dev.redstudio.valkyrie.mixin.ViewFrustumMixin}: its four overwrites count render chunks from minY. */
    static final class ViewFrustumMixinPatch extends ClassPatch {
        ViewFrustumMixinPatch() {
            super("dev.redstudio.valkyrie.mixin.ViewFrustumMixin", LABEL_FRUSTUM);
        }

        @Override
        protected void apply(ClassNode node) {
            FieldInsnNode world = AlfheimPatches.own(node, "world", "field_178167_b");
            if (!site("world (shadow field)", world != null)) {
                return;
            }
            P setPosition = invoke(Opcodes.INVOKEVIRTUAL, "setPosition", "func_189562_a", "(III)V");

            // createRenderChunks: setPosition(x * 16, y * 16, z * 16): y * 16 + minY.
            MethodNode m = method(node, "createRenderChunks", "func_178158_a",
                    "(Lnet/minecraft/client/renderer/chunk/IRenderChunkFactory;)V");
            for (AbstractInsnNode n : expect("createRenderChunks.y", m, 1, iload(-1), icst(16), op(Opcodes.IMUL),
                    iload(-1), icst(16), op(Opcodes.IMUL), setPosition)) {
                m.instructions.insert(at(n, 2), plusMin(world));
            }

            // updateChunkPositions: the render chunk's y is (j << 4): (j << 4) + minY.
            m = method(node, "updateChunkPositions", "func_178163_a", "(DD)V");
            for (AbstractInsnNode n : expect("updateChunkPositions.y", m, 1, iload(-1), icst(4), op(Opcodes.ISHL), istore(-1))) {
                m.instructions.insert(at(n, 2), plusMin(world));
            }

            // markBlocksForUpdate: the two y arguments less minY first (then >> 4 and the modulo, as Valkyrie has it).
            m = method(node, "markBlocksForUpdate", "func_187474_a", "(IIIIIIZ)V");
            if (site("markBlocksForUpdate.shift", m != null
                    && find(m, iload(2), icst(4), op(Opcodes.ISHR)).size() == 1
                    && find(m, iload(5), icst(4), op(Opcodes.ISHR)).size() == 1)) {
                InsnList head = new InsnList();
                for (int var : new int[] {2, 5}) {
                    head.add(new VarInsnNode(Opcodes.ILOAD, var));
                    head.add(minY(world));
                    head.add(insn(Opcodes.ISUB));
                    head.add(new VarInsnNode(Opcodes.ISTORE, var));
                }
                m.instructions.insert(head);
            }

            // getRenderChunk: pos.getY() >> 4 becomes (pos.getY() - minY) >> 4 (minY is a multiple of 16).
            m = method(node, "getRenderChunk", "func_178161_a",
                    "(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/client/renderer/chunk/RenderChunk;");
            for (AbstractInsnNode n : expect("getRenderChunk.y", m, 1, getY(), icst(4), op(Opcodes.ISHR))) {
                InsnList l = minY(world);
                l.add(insn(Opcodes.ISUB));
                m.instructions.insert(n, l);
            }
        }

        private static InsnList minY(FieldInsnNode world) {
            return worldInt(list(new VarInsnNode(Opcodes.ALOAD, 0), copy(world)), "minY");
        }

        private static InsnList plusMin(FieldInsnNode world) {
            InsnList l = minY(world);
            l.add(insn(Opcodes.IADD));
            return l;
        }
    }

    /**
     * {@code dev.redstudio.valkyrie.mixin.RenderGlobalMixin}: its {@code getRenderChunkOffset} overwrite keeps the walk
     * inside 0-256; it becomes the world's height, as vanilla's does. The world is {@code RenderGlobal}'s own field
     * (the mixin has no shadow of it; the method runs as {@code RenderGlobal}'s once merged).
     */
    static final class RenderGlobalMixinPatch extends ClassPatch {
        RenderGlobalMixinPatch() {
            super("dev.redstudio.valkyrie.mixin.RenderGlobalMixin", LABEL_GLOBAL);
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "getRenderChunkOffset", "func_181562_a",
                    "(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/renderer/chunk/RenderChunk;"
                            + "Lnet/minecraft/util/EnumFacing;)Lnet/minecraft/client/renderer/chunk/RenderChunk;");
            range(m, "getRenderChunkOffset", 1, 1, () -> selfField("net/minecraft/client/renderer/RenderGlobal",
                    n("world", "field_72769_h"), WORLD_CLIENT_DESC));
        }
    }

    private static FieldInsnNode copy(FieldInsnNode f) {
        return new FieldInsnNode(f.getOpcode(), f.owner, f.name, f.desc);
    }
}
