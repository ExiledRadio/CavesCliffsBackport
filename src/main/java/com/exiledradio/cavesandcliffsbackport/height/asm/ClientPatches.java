package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * The client's patches, one class each: receiving chunks, the render frustum (24 render chunks high from the world's
 * bottom), and the fog colour near the bottom. Only names classes as strings, so a dedicated server never loads a
 * client class through here.
 */
final class ClientPatches {

    private static final String WORLD_CLIENT_DESC = "Lnet/minecraft/client/multiplayer/WorldClient;";

    private ClientPatches() {
    }

    /** {@code NetHandlerPlayClient.handleChunkData}: re-renders the chunk's whole height, not 0-256. */
    static final class NetHandlerPlayClientPatch extends ClassPatch {
        NetHandlerPlayClientPatch() {
            super("net.minecraft.client.network.NetHandlerPlayClient");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "handleChunkData", "func_147263_a",
                    "(Lnet/minecraft/network/play/server/SPacketChunkData;)V");
            for (AbstractInsnNode n : expect("handleChunkData.bottom", m, 2,
                    invoke(Opcodes.INVOKEVIRTUAL, "getChunkX", "func_149273_e", "()I"), icst(4), op(Opcodes.ISHL), icst(0))) {
                replace(m, at(n, 3), worldInt(world(), "minY"));
            }
            for (AbstractInsnNode n : expect("handleChunkData.top", m, 2, op(Opcodes.IADD), icst(256))) {
                replace(m, next(n), worldInt(world(), "maxY"));
            }
        }

        private static InsnList world() {
            return selfField("net/minecraft/client/network/NetHandlerPlayClient", n("world", "field_147300_g"), WORLD_CLIENT_DESC);
        }
    }

    /** {@code WorldClient.doPreChunk}: an unloaded chunk re-renders its whole height. */
    static final class WorldClientPatch extends ClassPatch {
        WorldClientPatch() {
            super("net.minecraft.client.multiplayer.WorldClient");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "doPreChunk", "func_73025_a", "(IIZ)V");
            for (AbstractInsnNode n : expect("doPreChunk.bottom", m, 1, op(Opcodes.IMUL), icst(0), iload(2))) {
                replace(m, next(n), worldInt(self(), "minY"));
            }
            for (AbstractInsnNode n : expect("doPreChunk.top", m, 1, op(Opcodes.IADD), icst(256))) {
                replace(m, next(n), worldInt(self(), "maxY"));
            }
        }
    }

    /**
     * {@code ViewFrustum}: as many render chunks high as the world has sections, render chunk {@code i} (from the
     * bottom) at y {@code minY + 16 i}.
     */
    static final class ViewFrustumPatch extends ClassPatch {
        private static final String VF = "net/minecraft/client/renderer/ViewFrustum";

        ViewFrustumPatch() {
            super("net.minecraft.client.renderer.ViewFrustum");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "setCountChunksXYZ", "func_178159_a", "(I)V");
            for (AbstractInsnNode n : expect("setCountChunksXYZ", m, 1, icst(16),
                    field(Opcodes.PUTFIELD, "countChunksY", "field_178168_c"))) {
                replace(m, n, worldInt(world(), "sections"));
            }

            P setPosition = invoke(Opcodes.INVOKEVIRTUAL, "setPosition", "func_189562_a", "(III)V");
            m = method(node, "createRenderChunks", "func_178158_a",
                    "(Lnet/minecraft/client/renderer/chunk/IRenderChunkFactory;)V");
            for (AbstractInsnNode n : expect("createRenderChunks.y", m, 1, iload(-1), icst(16), op(Opcodes.IMUL),
                    iload(-1), icst(16), op(Opcodes.IMUL), setPosition)) {
                m.instructions.insert(at(n, 2), plusMin());
            }

            m = method(node, "updateChunkPositions", "func_178163_a", "(DD)V");
            for (AbstractInsnNode n : expect("updateChunkPositions.y", m, 1, iload(-1), icst(16), op(Opcodes.IMUL), istore(-1))) {
                m.instructions.insert(at(n, 2), plusMin());
            }

            m = method(node, "markBlocksForUpdate", "func_187474_a", "(IIIIIIZ)V");
            if (site("markBlocksForUpdate.head", m != null)) {
                InsnList head = new InsnList();
                for (int var : new int[] {2, 5}) {
                    head.add(new VarInsnNode(Opcodes.ILOAD, var));
                    head.add(worldInt(world(), "minY"));
                    head.add(insn(Opcodes.ISUB));
                    head.add(new VarInsnNode(Opcodes.ISTORE, var));
                }
                m.instructions.insert(head);
            }

            m = method(node, "getRenderChunk", "func_178161_a",
                    "(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/client/renderer/chunk/RenderChunk;");
            for (AbstractInsnNode n : expect("getRenderChunk.y", m, 1, getY(), icst(16),
                    invoke(Opcodes.INVOKESTATIC, "intFloorDiv", "func_76137_a", "(II)I"))) {
                InsnList l = worldInt(world(), "minY");
                l.add(insn(Opcodes.ISUB));
                m.instructions.insert(n, l);
            }
        }

        private static InsnList world() {
            return selfField(VF, n("world", "field_178167_b"), WORLD_DESC);
        }

        private static InsnList plusMin() {
            InsnList l = worldInt(world(), "minY");
            l.add(insn(Opcodes.IADD));
            return l;
        }
    }

    /**
     * {@code RenderGlobal}: the neighbour walk stays inside the world's height, and a camera above or below the world
     * starts from its top or bottom render chunks.
     */
    static final class RenderGlobalPatch extends ClassPatch {
        RenderGlobalPatch() {
            super("net.minecraft.client.renderer.RenderGlobal");
        }

        @Override
        protected void apply(ClassNode node) {
            range(method(node, "getRenderChunkOffset", "func_181562_a", "(Lnet/minecraft/util/math/BlockPos;"
                            + "Lnet/minecraft/client/renderer/chunk/RenderChunk;Lnet/minecraft/util/EnumFacing;)"
                            + "Lnet/minecraft/client/renderer/chunk/RenderChunk;"),
                    "getRenderChunkOffset", 1, 1, RenderGlobalPatch::world);

            // A render chunk's entities: its chunk's entity list for its section, counted from the world's bottom.
            MethodNode m = method(node, "renderEntities", "func_180446_a",
                    "(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V");
            // RenderLib (meldexun; RLCraft) replaces that loop with its own, which walks the world's entity list and
            // never indexes sections: nothing to patch then.
            boolean renderLib = m != null && !Asm.find(m, n -> n.getOpcode() == Opcodes.INVOKESTATIC
                    && "meldexun/renderlib/renderer/EntityRenderManager".equals(((MethodInsnNode) n).owner)
                    && "renderEntities".equals(((MethodInsnNode) n).name)).isEmpty();
            if (renderLib && Asm.find(m, getY(), icst(16), op(Opcodes.IDIV), op(Opcodes.AALOAD)).isEmpty()) {
                site("renderEntities.list (RenderLib's loop)", true);
            } else {
                for (AbstractInsnNode n : expect("renderEntities.list", m, 1, getY(), icst(16), op(Opcodes.IDIV), op(Opcodes.AALOAD))) {
                    InsnList l = worldInt(world(), "minSection");
                    l.add(insn(Opcodes.ISUB));
                    m.instructions.insert(at(n, 2), l);
                }
            }

            m = method(node, "setupTerrain", "func_174970_a",
                    "(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V");
            for (AbstractInsnNode n : expect("setupTerrain.startHeight", m, 1, getY(), op(Opcodes.IFLE), icst(248),
                    op(Opcodes.GOTO), icst(8), istore(-1))) {
                AbstractInsnNode top = at(n, 2);
                AbstractInsnNode bottom = at(n, 4);
                replace(m, top, worldInt(world(), "topSectionMiddle"));
                replace(m, bottom, worldInt(world(), "bottomSectionMiddle"));
            }
            for (AbstractInsnNode n : expect("setupTerrain.above", m, 2, getY(), op(Opcodes.IFLE))) {
                compareWith(m, next(n), worldInt(world(), "minY"));
            }
        }

        private static InsnList world() {
            return selfField("net/minecraft/client/renderer/RenderGlobal", n("world", "field_72769_h"), WORLD_CLIENT_DESC);
        }
    }

    /**
     * {@code EntityRenderer.updateFogColor}: the fog darkens toward black near the bottom of the world, measured from
     * the world's bottom as in 1.18 ({@code (y - minY) * voidFogFactor}) instead of from y 0.
     */
    static final class EntityRendererPatch extends ClassPatch {
        EntityRendererPatch() {
            super("net.minecraft.client.renderer.EntityRenderer");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "updateFogColor", "func_78466_h", "(F)V");
            for (AbstractInsnNode n : expect("updateFogColor.voidFog", m, 1, aload(-1),
                    field(Opcodes.GETFIELD, "provider", "field_73011_w"),
                    invoke(Opcodes.INVOKEVIRTUAL, "getVoidFogYFactor", "func_76565_k", "()D"), op(Opcodes.DMUL))) {
                int world = ((VarInsnNode) n).var;
                InsnList l = list(new VarInsnNode(Opcodes.ALOAD, world), heights("minY", "(" + WORLD_DESC + ")I"),
                        insn(Opcodes.I2D), insn(Opcodes.DSUB));
                m.instructions.insertBefore(n, l);
            }
        }
    }

    /** Celeritas: its render section manager covers the world's sections, not 0-16. */
    static final class CeleritasManagerPatch extends ClassPatch {
        CeleritasManagerPatch() {
            super("org.taumc.celeritas.impl.render.terrain.VintageRenderSectionManager");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = null;
            for (MethodNode c : node.methods) {
                if (c.name.equals("create") && c.desc.contains(WORLD_CLIENT_DESC)) {
                    m = c;
                }
            }
            for (AbstractInsnNode n : expect("create.sections", m, 1, icst(0), icst(16),
                    invoke(Opcodes.INVOKESPECIAL, "<init>", "<init>", null))) {
                AbstractInsnNode max = next(n);
                replace(m, n, list(new VarInsnNode(Opcodes.ALOAD, 1), heights("minSection", "(" + WORLD_DESC + ")I")));
                replace(m, max, list(new VarInsnNode(Opcodes.ALOAD, 1), heights("maxSection", "(" + WORLD_DESC + ")I")));
            }
            // isSectionVisuallyEmpty(x, sectionY, z) reads chunk.getBlockStorageArray()[sectionY] behind a
            // 0..length guard: the section y becomes its slot first (a vanilla chunk's guard still rejects 16 and up).
            m = Asm.method(node, "isSectionVisuallyEmpty", "isSectionVisuallyEmpty", "(III)Z");
            if (site("isSectionVisuallyEmpty", m != null
                    && Asm.find(m, invoke(Opcodes.INVOKEVIRTUAL, "getBlockStorageArray", "func_76587_i", null)).size() == 1
                    && Asm.find(m, iload(2), op(Opcodes.AALOAD)).size() == 2)) {
                m.instructions.insert(list(new VarInsnNode(Opcodes.ILOAD, 2), layout("sectionSlot", "(I)I"),
                        new VarInsnNode(Opcodes.ISTORE, 2)));
            }
        }
    }

    /** Celeritas: {@code WorldSlice.prepare} reads {@code chunk.getBlockStorageArray()[sectionY]}: by our slots. */
    static final class CeleritasSlicePatch extends ClassPatch {
        CeleritasSlicePatch() {
            super("org.taumc.celeritas.impl.world.WorldSlice");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = null;
            for (MethodNode c : node.methods) {
                if (c.name.equals("prepare") && c.desc.startsWith("(" + WORLD_DESC)) {
                    m = c;
                }
            }
            String ebs = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;";
            for (AbstractInsnNode n : expect("prepare.section", m, 1,
                    invoke(Opcodes.INVOKEVIRTUAL, "getBlockStorageArray", "func_76587_i", null), aload(-1),
                    invoke(Opcodes.INVOKEVIRTUAL, "y", "y", "()I"), op(Opcodes.AALOAD))) {
                AbstractInsnNode aaload = at(n, 3);
                m.instructions.remove(n);
                replace(m, aaload, list(hooks("sectionAt", "(Lnet/minecraft/world/chunk/Chunk;I)" + ebs)));
            }
        }
    }

    /** Celeritas: {@code ClonedChunkSection.getChunkSection(chunk, sectionY)} reads our slots (negative y: 20-23). */
    static final class CeleritasSectionPatch extends ClassPatch {
        CeleritasSectionPatch() {
            super("org.taumc.celeritas.impl.world.cloned.ClonedChunkSection");
        }

        @Override
        protected void apply(ClassNode node) {
            String ebs = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;";
            String desc = "(Lnet/minecraft/world/chunk/Chunk;I)" + ebs;
            MethodNode m = Asm.method(node, "getChunkSection", "getChunkSection", desc);
            // Its body must be the plain bounds-checked read this replaces (y >= 0 && y < length).
            if (site("getChunkSection", m != null && Asm.find(m, iload(1), op(Opcodes.IFLT)).size() == 1)) {
                InsnList head = list(new VarInsnNode(Opcodes.ALOAD, 0), new VarInsnNode(Opcodes.ILOAD, 1),
                        hooks("sectionAt", "(Lnet/minecraft/world/chunk/Chunk;I)" + ebs), insn(Opcodes.ARETURN));
                // The original body follows unreachable; the verifier needs a frame on its first instruction.
                head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                m.instructions.insert(head);
            }
        }
    }

    /**
     * Entity Culling (meldexun; RLCraft): its ray tracer's {@code CachedBlockAccess.getChunkSection(x, sectionY, z)}
     * reads {@code chunk.getBlockStorageArray()[sectionY]}, which throws for a section below 0 once the world's
     * build height lets the ray get there; it reads our slot instead (null outside the world, as an empty section).
     */
    static final class EntityCullingAccessPatch extends ClassPatch {
        EntityCullingAccessPatch() {
            super("meldexun.entityculling.util.CachedBlockAccess");
        }

        @Override
        protected void apply(ClassNode node) {
            String ebs = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;";
            MethodNode m = Asm.method(node, "getChunkSection", "getChunkSection", "(III)" + ebs);
            P[] read = {invoke(Opcodes.INVOKEVIRTUAL, "getBlockStorageArray", "func_76587_i", "()[" + ebs), iload(2),
                    op(Opcodes.AALOAD)};
            if (m != null && Asm.find(m, read).isEmpty()) {
                // 6.5.0 (Dregora) reads sections through a lambda that answers null outside 0-15 itself: rays pass
                // through the sections outside it as through air, which never throws.
                site("EntityCulling.getChunkSection (no unchecked read)", true);
                return;
            }
            for (AbstractInsnNode n : expect("EntityCulling.getChunkSection", m, 1, read)) {
                AbstractInsnNode load = next(n);
                AbstractInsnNode aaload = next(load);
                m.instructions.remove(n);
                m.instructions.set(aaload, hooks("sectionAt", "(Lnet/minecraft/world/chunk/Chunk;I)" + ebs));
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Nothirium (meldexun): its own chunk renderer, camera-centred in y, with three 0-16 section limits.
    // ------------------------------------------------------------------------------------------

    private static final String NOTH_WORLD = "meldexun/nothirium/mc/util/WorldUtil";

    /** {@code getSectionY()} on a render chunk, through the class or the interface. */
    private static P sectionY() {
        return n -> (n.getOpcode() == Opcodes.INVOKEVIRTUAL || n.getOpcode() == Opcodes.INVOKEINTERFACE)
                && "getSectionY".equals(((org.objectweb.asm.tree.MethodInsnNode) n).name);
    }

    /** {@code Heights.<name>(WorldUtil.getWorld())}: the client world's section range. */
    private static InsnList nothiriumWorldInt(String name) {
        return list(new org.objectweb.asm.tree.MethodInsnNode(Opcodes.INVOKESTATIC, NOTH_WORLD, "getWorld",
                "()" + WORLD_DESC, false), heights(name, "(" + WORLD_DESC + ")I"));
    }

    /** {@code WorldUtil.getSection(world, x, sectionY, z)}: by our slots (the original allows 0-15 only). */
    static final class NothiriumWorldUtilPatch extends ClassPatch {
        NothiriumWorldUtilPatch() {
            super("meldexun.nothirium.mc.util.WorldUtil");
        }

        @Override
        protected void apply(ClassNode node) {
            String ebs = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;";
            MethodNode m = Asm.method(node, "getSection", "getSection", "(" + WORLD_DESC + "III)" + ebs);
            if (site("getSection", m != null && Asm.find(m, iload(2), op(Opcodes.IFLT)).size() == 1
                    && Asm.find(m, iload(2), icst(16), op(Opcodes.IF_ICMPLT)).size() == 1)) {
                InsnList head = list(new VarInsnNode(Opcodes.ALOAD, 0), new VarInsnNode(Opcodes.ILOAD, 1),
                        new VarInsnNode(Opcodes.ILOAD, 2), new VarInsnNode(Opcodes.ILOAD, 3),
                        hooks("sectionAt", "(" + WORLD_DESC + "III)" + ebs), insn(Opcodes.ARETURN));
                // The original body follows unreachable; the verifier needs a frame on its first instruction.
                head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                m.instructions.insert(head);
            }
        }
    }

    /** {@code RenderChunk.markDirty}: a render chunk outside the world height is all visible (not outside 0-15). */
    static final class NothiriumRenderChunkPatch extends ClassPatch {
        NothiriumRenderChunkPatch() {
            super("meldexun.nothirium.mc.renderer.chunk.RenderChunk");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = Asm.method(node, "markDirty", "markDirty", "()V");
            for (AbstractInsnNode n : expect("markDirty.bottom", m, 1, sectionY(), op(Opcodes.IFLT))) {
                compareWith(m, next(n), nothiriumWorldInt("minSection"));
            }
            for (AbstractInsnNode n : expect("markDirty.top", m, 1, sectionY(), icst(16), op(Opcodes.IF_ICMPLT))) {
                replace(m, next(n), nothiriumWorldInt("maxSection"));
            }
        }
    }

    /** {@code Direction.UP/DOWN.isFaceCulled}: the faces at the world top and bottom section, not 15 and 0. */
    static final class NothiriumDirectionPatch extends ClassPatch {
        private final boolean up;

        NothiriumDirectionPatch(boolean up) {
            super(up ? "meldexun.nothirium.util.Direction$1" : "meldexun.nothirium.util.Direction$2");
            this.up = up;
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = Asm.method(node, "isFaceCulled", "isFaceCulled",
                    "(Lmeldexun/nothirium/api/renderer/chunk/IRenderChunk;DDD)Z");
            if (this.up) {
                for (AbstractInsnNode n : expect("isFaceCulled.top", m, 1, sectionY(), icst(16), op(Opcodes.IF_ICMPLT))) {
                    replace(m, next(n), nothiriumWorldInt("maxSection"));
                }
            } else {
                for (AbstractInsnNode n : expect("isFaceCulled.bottom", m, 1, sectionY(), op(Opcodes.IFGE))) {
                    compareWith(m, next(n), nothiriumWorldInt("minSection"));
                }
            }
        }
    }
}
