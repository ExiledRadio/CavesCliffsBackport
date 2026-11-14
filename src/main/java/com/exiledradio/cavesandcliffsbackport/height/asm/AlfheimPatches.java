package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * Alfheim 1.6 (a Phosphor descendant; plan {@code docs/streams/optifine-alfheim.md}, section 2): its lighting engine's
 * position key gets room for y -64..319, its chunk code reads sections by our slots, its boundary flags cover two
 * sections per bit in an extended world, and its overwrites of {@code Chunk.relightBlock} get our sites back.
 *
 * <p>The mixin classes are patched as Mixin reads them through the transformer chain, before it applies them (as
 * VintageFix's {@code ChunkMixin}, {@code SmallPatches}). Every site calls {@code height.AlfheimHooks},
 * {@code Heights} or {@code HeightLayout}; each computes Alfheim's own value unless extended height is on (the key
 * layout) or the world is extended (sections, flags, bottoms). Patterns are Alfheim 1.6's exactly; the coremod refuses
 * any other Alfheim version before this runs.
 */
final class AlfheimPatches {

    static final String HOOKS = "com/exiledradio/cavesandcliffsbackport/height/AlfheimHooks";
    private static final String POS = "Lnet/minecraft/util/math/BlockPos;";
    private static final String MPOS = "Lnet/minecraft/util/math/BlockPos$MutableBlockPos;";
    private static final String CHUNK = "Lnet/minecraft/world/chunk/Chunk;";

    private AlfheimPatches() {
    }

    static MethodInsnNode ah(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, desc, false);
    }

    static P lcst(final long value) {
        return n -> n instanceof LdcInsnNode && Long.valueOf(value).equals(((LdcInsnNode) n).cst);
    }

    static P lload(final int var) {
        return n -> n.getOpcode() == Opcodes.LLOAD && ((VarInsnNode) n).var == var;
    }

    static P getfield(final String name) {
        return n -> n.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode) n).name.equals(name);
    }

    static P call(final String mcp, final String srg, final String desc) {
        return n -> n instanceof MethodInsnNode && is(((MethodInsnNode) n).name, mcp, srg)
                && (desc == null || desc.equals(((MethodInsnNode) n).desc));
    }

    /** {@code getfield} of one of the class's own fields (by either name), as the class declares it; null if none. */
    static FieldInsnNode own(ClassNode node, String mcp, String srg) {
        for (FieldNode f : node.fields) {
            if (is(f.name, mcp, srg)) {
                return new FieldInsnNode(Opcodes.GETFIELD, node.name, f.name, f.desc);
            }
        }
        return null;
    }

    /**
     * Puts {@code head} (which ends in a return) in front of the method's body, which stays as unreachable code behind
     * a frame. Only when the body does not open with a frame of its own (two frames on one offset are invalid).
     */
    static boolean headRedirect(MethodNode m, InsnList head) {
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null && i.getOpcode() < 0; i = i.getNext()) {
            if (i instanceof FrameNode) {
                return false;
            }
        }
        head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        m.instructions.insert(head);
        return true;
    }

    /** {@code dev.redstudio.alfheim.lighting.LightingEngine}: A1-A7. */
    static final class LightingEnginePatch extends ClassPatch {
        private static final String MASK_CHUNK_ID = "M_CHUNK";

        LightingEnginePatch() {
            super("dev.redstudio.alfheim.lighting.LightingEngine", "AlfheimLightingEngine");
        }

        @Override
        protected void apply(ClassNode node) {
            FieldInsnNode world = own(node, "world", "world");
            FieldInsnNode currentPos = own(node, "currentPos", "currentPos");
            FieldInsnNode currentChunk = own(node, "currentChunk", "currentChunk");
            if (!site("fields", world != null && currentPos != null && currentChunk != null)) {
                return;
            }

            // A1, A2: encode and decode go to AlfheimHooks (Alfheim's own formula unless the layout is wide). The
            // pattern confirms each body is 1.6's (y at bit 52).
            MethodNode m = method(node, "encodeWorldCoord", "encodeWorldCoord", "(" + POS + ")J");
            if (!expect("encodeWorldCoord.layout", m, 1, icst(52), op(Opcodes.LSHL)).isEmpty()) {
                site("encodeWorldCoord.head", headRedirect(m, list(new VarInsnNode(Opcodes.ALOAD, 0),
                        ah("encode", "(" + POS + ")J"), insn(Opcodes.LRETURN))));
            }
            m = method(node, "decodeWorldCoord", "decodeWorldCoord", "(" + MPOS + "J)" + MPOS);
            if (!expect("decodeWorldCoord.layout", m, 1, icst(52), op(Opcodes.LSHR)).isEmpty()) {
                site("decodeWorldCoord.head", headRedirect(m, list(new VarInsnNode(Opcodes.ALOAD, 0),
                        new VarInsnNode(Opcodes.LLOAD, 1), ah("decode", "(" + MPOS + "J)" + MPOS), insn(Opcodes.ARETURN))));
            }

            // A3: the neighbor shifts, (dy << 52) | (dx << 26) | dz.
            m = method(node, "<clinit>", "<clinit>", "()V");
            for (AbstractInsnNode n : expect("<clinit>.yShift", m, 1, getY(), op(Opcodes.I2L), icst(52), op(Opcodes.LSHL))) {
                replace(m, at(n, 2), list(ah("yShift", "()I")));
            }
            for (AbstractInsnNode n : expect("<clinit>.xShift", m, 1, invoke(Opcodes.INVOKEVIRTUAL, "getX", "func_177958_n", "()I"),
                    op(Opcodes.I2L), icst(26), op(Opcodes.LSHL))) {
                replace(m, at(n, 2), list(ah("xShift", "()I")));
            }

            // A4: the first pass over the queue skips a position outside the world's height as it skips an unloaded
            // chunk (only a wide key can decode to one).
            m = method(node, "processLightUpdatesForTypeInner", "processLightUpdatesForTypeInner",
                    "(Lnet/minecraft/world/EnumSkyBlock;Ldev/redstudio/alfheim/utils/DeduplicatedLongQueue;)V");
            for (AbstractInsnNode n : expect("processLightUpdatesForTypeInner.inWorld", m, 1, aload(0),
                    getfield(currentChunk.name), op(Opcodes.IFNONNULL))) {
                m.instructions.insert(next(n), list(new VarInsnNode(Opcodes.ALOAD, 0), copy(world),
                        new VarInsnNode(Opcodes.ALOAD, 0), copy(currentPos),
                        ah("inWorld", "(" + CHUNK + WORLD_DESC + POS + ")" + CHUNK)));
            }

            // A5: M_CHUNK, the key masked to its chunk (inlined: one site per use).
            long mChunk = com.exiledradio.cavesandcliffsbackport.height.AlfheimKey.NARROW_CHUNK_MASK;
            long yCheck = com.exiledradio.cavesandcliffsbackport.height.AlfheimKey.NARROW_Y_CHECK;
            m = method(node, "nextItem", "nextItem", "()Z");
            for (AbstractInsnNode n : expect("nextItem." + MASK_CHUNK_ID, m, 1, lcst(mChunk), op(Opcodes.LAND))) {
                replace(m, n, list(ah("chunkMask", "()J")));
            }
            m = method(node, "fetchNeighborDataFromCursor", "fetchNeighborDataFromCursor", "(Lnet/minecraft/world/EnumSkyBlock;)V");
            for (AbstractInsnNode n : expect("fetchNeighborDataFromCursor." + MASK_CHUNK_ID, m, 1, lcst(mChunk), op(Opcodes.LAND))) {
                replace(m, n, list(ah("chunkMask", "()J")));
            }
            // A6: (key & Y_CHECK) != 0 becomes AlfheimHooks.outside(key, world).
            for (AbstractInsnNode n : expect("fetchNeighborDataFromCursor.Y_CHECK", m, 1, lload(4), lcst(yCheck),
                    op(Opcodes.LAND), op(Opcodes.LCONST_0), op(Opcodes.LCMP), op(Opcodes.IFEQ))) {
                AbstractInsnNode ldc = at(n, 1);
                m.instructions.remove(at(n, 4));
                m.instructions.remove(at(n, 3));
                m.instructions.remove(at(n, 2));
                replace(m, ldc, list(new VarInsnNode(Opcodes.ALOAD, 0), copy(world),
                        ah("outside", "(J" + WORLD_DESC + ")Z")));
            }
            // A7: the neighbor's section, by slot.
            for (AbstractInsnNode n : expect("fetchNeighborDataFromCursor.slot", m, 1,
                    call("getBlockStorageArray", "func_76587_i", null), aload(-1), getY(), icst(4), op(Opcodes.ISHR),
                    op(Opcodes.AALOAD))) {
                shiftToSlot(m, at(n, 3));
            }
        }
    }

    /** {@code dev.redstudio.alfheim.mixin.ChunkMixin}: A8-A13, A15. */
    static final class ChunkMixinPatch extends ClassPatch {
        ChunkMixinPatch() {
            super("dev.redstudio.alfheim.mixin.ChunkMixin", "AlfheimChunkMixin");
        }

        private FieldInsnNode world;

        private InsnList world() {
            return list(new VarInsnNode(Opcodes.ALOAD, 0), copy(this.world));
        }

        private InsnList minY() {
            return worldInt(world(), "minY");
        }

        @Override
        protected void apply(ClassNode node) {
            this.world = own(node, "world", "field_76637_e");
            if (!site("world", this.world != null)) {
                return;
            }
            P storage = field(Opcodes.GETFIELD, "storageArrays", "field_76652_q");

            // A8: every light read.
            MethodNode m = method(node, "alfheim$getCachedLightFor", "alfheim$getCachedLightFor",
                    "(Lnet/minecraft/world/EnumSkyBlock;" + POS + ")B");
            for (AbstractInsnNode n : expect("getCachedLightFor.slot", m, 1, storage, iload(4), icst(4), op(Opcodes.ISHR),
                    op(Opcodes.AALOAD))) {
                shiftToSlot(m, at(n, 2));
            }

            // A15: its @Redirect in generateSkylightMap.
            m = method(node, "setLightForRedirectGenerateSkylightMap", "setLightForRedirectGenerateSkylightMap",
                    "(" + CHUNK + "Lnet/minecraft/world/EnumSkyBlock;" + POS + "I)V");
            for (AbstractInsnNode n : expect("setLightForRedirectGenerateSkylightMap.slot", m, 1, storage, aload(3), getY(),
                    icst(4), op(Opcodes.ISHR), op(Opcodes.AALOAD))) {
                shiftToSlot(m, at(n, 3));
            }

            // A13: the relightBlock overwrite gets ChunkPatch's relightBlock.mask and .bottom back.
            m = method(node, "relightBlock", "func_76615_h", "(III)V");
            for (AbstractInsnNode n : expect("relightBlock.mask", m, 1, icst(255), op(Opcodes.IAND))) {
                m.instructions.remove(next(n));
                InsnList l = world();
                l.add(heights("maskHeight", "(I" + WORLD_DESC + ")I"));
                replace(m, n, l);
            }
            for (AbstractInsnNode n : expect("relightBlock.bottom", m, 1, iload(5), op(Opcodes.IFLE))) {
                compareWith(m, next(n), minY());
            }

            // A9: the sky light column.
            m = method(node, "alfheim$relightSkylightColumn", "alfheim$relightSkylightColumn", "(IIII)V");
            for (AbstractInsnNode n : expect("relightSkylightColumn.slot", m, 1, aload(8), iload(5), icst(4),
                    op(Opcodes.ISHR), op(Opcodes.AALOAD))) {
                shiftToSlot(m, at(n, 2));
            }
            for (AbstractInsnNode n : expect("relightSkylightColumn.bottom", m, 1, iload(5), op(Opcodes.IFLE))) {
                compareWith(m, next(n), minY());
            }
            for (AbstractInsnNode n : expect("relightSkylightColumn.sectionSlot", m, 1, aload(8), iload(12),
                    op(Opcodes.AALOAD))) {
                m.instructions.insert(next(n), list(layout("sectionSlot", "(I)I")));
            }
            flagMask(m, "relightSkylightColumn.flagSet", 12);
            flagMask(m, "relightSkylightColumn.flagTest", 19);

            // A10: a flagged bit back to its height range.
            m = method(node, "alfheim$scheduleRelightChecksForBoundary", "alfheim$scheduleRelightChecksForBoundary",
                    "(" + CHUNK + CHUNK + CHUNK + "Lnet/minecraft/world/EnumSkyBlock;IILnet/minecraft/util/EnumFacing$AxisDirection;)V");
            for (AbstractInsnNode n : expect("scheduleRelightChecksForBoundary.range", m, 1, iload(17), icst(4),
                    op(Opcodes.ISHL), iload(17), icst(4), op(Opcodes.ISHL), icst(15), op(Opcodes.IADD),
                    call("alfheim$scheduleRelightChecksForColumn", "alfheim$scheduleRelightChecksForColumn", null))) {
                AbstractInsnNode[] k = new AbstractInsnNode[8];
                for (int i = 0; i < 8; i++) {
                    k[i] = at(n, i);
                }
                InsnList bottom = world();
                bottom.add(ah("flagBottom", "(I" + WORLD_DESC + ")I"));
                replace(m, k[1], bottom);
                m.instructions.remove(k[2]);
                InsnList top = world();
                top.add(ah("flagTop", "(I" + WORLD_DESC + ")I"));
                replace(m, k[4], top);
                m.instructions.remove(k[5]);
                m.instructions.remove(k[6]);
                m.instructions.remove(k[7]);
            }

            // A11: the block light scan's section base (the isAreaLoaded box, y 0..255, meets any world's height).
            m = method(node, "alfheim$initChunkLighting", "alfheim$initChunkLighting", "(" + CHUNK + WORLD_DESC + ")V");
            for (AbstractInsnNode n : expect("initChunkLighting.base", m, 1, iload(6), icst(16), op(Opcodes.IMUL), istore(8))) {
                m.instructions.remove(at(n, 2));
                replace(m, next(n), list(layout("slotBase", "(I)I")));
            }

            // A12: already compares the height map with the section's own base; nothing to change.
            m = method(node, "alfheim$initSkylightForSection", "alfheim$initSkylightForSection",
                    "(Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;)V");
            expect("initSkylightForSection.base", m, 1, call("getHeightValue", "func_76611_b", "(II)I"), aload(1),
                    call("getYLocation", "func_76662_d", "()I"), op(Opcodes.IF_ICMPLE));
        }

        /** {@code iconst_1; iload var; ishl} becomes {@code AlfheimHooks.flagMask(var, world)}. */
        private void flagMask(MethodNode m, String id, int var) {
            List<AbstractInsnNode> found = expect(id, m, 1, icst(1), iload(var), op(Opcodes.ISHL));
            for (AbstractInsnNode n : found) {
                AbstractInsnNode load = next(n);
                m.instructions.remove(next(load));
                m.instructions.remove(n);
                InsnList l = world();
                l.add(ah("flagMask", "(I" + WORLD_DESC + ")I"));
                m.instructions.insert(load, l);
            }
        }
    }

    /**
     * {@code dev.redstudio.alfheim.mixin.ChunkCacheMixin} (A14): its {@code getLightForExt} overwrite reads the state
     * and asks it for the light through patched {@code ChunkCache}/{@code World} methods; no height of its own. Checked,
     * not changed: any other body is a miss.
     */
    static final class ChunkCacheMixinPatch extends ClassPatch {
        private final boolean client;

        ChunkCacheMixinPatch(boolean client) {
            super("dev.redstudio.alfheim.mixin.ChunkCacheMixin", "AlfheimChunkCacheMixin");
            this.client = client;
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = Asm.method(node, "getLightForExt", "func_175629_a",
                    "(Lnet/minecraft/world/EnumSkyBlock;" + POS + ")I");
            if (m == null && !this.client) {
                // ChunkCache.getLightForExt is client-only: Forge strips the overwrite from the mixin on a server.
                site("getLightForExt (stripped on a server)", true);
                return;
            }
            site("getLightForExt.noHeight", m != null && Asm.find(m, getY()).isEmpty()
                    && Asm.find(m, call("getBlockState", "func_180495_p", null)).size() == 1
                    && Asm.find(m, call("alfheim$getLightFor", "alfheim$getLightFor", null)).size() == 1);
        }
    }

    private static FieldInsnNode copy(FieldInsnNode f) {
        return new FieldInsnNode(f.getOpcode(), f.owner, f.name, f.desc);
    }
}
