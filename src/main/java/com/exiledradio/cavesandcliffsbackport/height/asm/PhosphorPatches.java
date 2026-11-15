package com.exiledradio.cavesandcliffsbackport.height.asm;

import com.exiledradio.cavesandcliffsbackport.height.AlfheimKey;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.height.asm.AlfheimPatches.call;
import static com.exiledradio.cavesandcliffsbackport.height.asm.AlfheimPatches.headRedirect;
import static com.exiledradio.cavesandcliffsbackport.height.asm.AlfheimPatches.lcst;
import static com.exiledradio.cavesandcliffsbackport.height.asm.AlfheimPatches.lload;
import static com.exiledradio.cavesandcliffsbackport.height.asm.AlfheimPatches.own;
import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * Phosphor (jellysquid's 0.2.6 and 0.2.7, and its fork Hesperus 0.2.9.1-0.2.9.3; plan {@code docs/streams/height.md},
 * "Phosphor"): its lighting engine's position key gets room for y -64..319, its sections are read by our slots, its
 * boundary flags cover two sections per bit in an extended world, and its {@code Chunk.relightBlock} overwrite gets our
 * sites back. The same design as {@link AlfheimPatches} (Alfheim is a Phosphor descendant and kept these structures);
 * in Phosphor most of the code is in plain classes ({@code LightingEngine}, {@code LightingHooks},
 * {@code LightingEngineHelpers}), and the one mixin with height code, {@code MixinChunk}, is patched as Mixin reads it.
 *
 * <p>Every site calls {@code height.PhosphorHooks}, {@code Heights} or {@code HeightLayout}; each computes Phosphor's
 * own value unless extended height is on (the key layout) or the world is extended (sections, flags, bottoms). The
 * height code of all five builds is the same bytecode; the coremod refuses any other version before this runs.
 */
final class PhosphorPatches {

    static final String HOOKS = "com/exiledradio/cavesandcliffsbackport/height/PhosphorHooks";
    private static final String PKG = "me.jellysquid.mods.phosphor.";
    private static final String POS = "Lnet/minecraft/util/math/BlockPos;";
    private static final String MPOS = "Lnet/minecraft/util/math/BlockPos$MutableBlockPos;";
    private static final String CHUNK = "Lnet/minecraft/world/chunk/Chunk;";
    private static final String EBS = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;";
    private static final String SKY = "Lnet/minecraft/world/EnumSkyBlock;";

    private PhosphorPatches() {
    }

    static MethodInsnNode ph(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, desc, false);
    }

    private static FieldInsnNode copy(FieldInsnNode f) {
        return new FieldInsnNode(f.getOpcode(), f.owner, f.name, f.desc);
    }

    private static InsnList aload0() {
        return list(new VarInsnNode(Opcodes.ALOAD, 0));
    }

    /** {@code mod.world.lighting.LightingEngine}: the position key, the queue filter, the neighbor section. */
    static final class LightingEnginePatch extends ClassPatch {
        LightingEnginePatch() {
            super(PKG + "mod.world.lighting.LightingEngine", "PhosphorLightingEngine");
        }

        @Override
        protected void apply(ClassNode node) {
            FieldInsnNode world = own(node, "world", "world");
            FieldInsnNode curPos = own(node, "curPos", "curPos");
            FieldInsnNode curChunk = own(node, "curChunk", "curChunk");
            if (!site("fields", world != null && curPos != null && curChunk != null)) {
                return;
            }

            // Encode and decode go to PhosphorHooks (Phosphor's own formula unless the layout is wide). The pattern
            // confirms each body is Phosphor's (y at bit 52). Every key is made by the (long, long, long) overload.
            MethodNode m = method(node, "encodeWorldCoord", "encodeWorldCoord", "(JJJ)J");
            if (!expect("encodeWorldCoord.layout", m, 1, lload(2), icst(52), op(Opcodes.LSHL)).isEmpty()) {
                site("encodeWorldCoord.head", headRedirect(m, list(new VarInsnNode(Opcodes.LLOAD, 0),
                        new VarInsnNode(Opcodes.LLOAD, 2), new VarInsnNode(Opcodes.LLOAD, 4), ph("encode", "(JJJ)J"),
                        insn(Opcodes.LRETURN))));
            }
            m = method(node, "encodeWorldCoord", "encodeWorldCoord", "(" + POS + ")J");
            expect("encodeWorldCoord(BlockPos).delegates", m, 1, call("encodeWorldCoord", "encodeWorldCoord", "(JJJ)J"),
                    op(Opcodes.LRETURN));
            m = method(node, "decodeWorldCoord", "decodeWorldCoord", "(" + MPOS + "J)" + MPOS);
            if (!expect("decodeWorldCoord.layout", m, 1, icst(52), op(Opcodes.LSHR)).isEmpty()) {
                site("decodeWorldCoord.head", headRedirect(m, list(new VarInsnNode(Opcodes.ALOAD, 0),
                        new VarInsnNode(Opcodes.LLOAD, 1), ph("decode", "(" + MPOS + "J)" + MPOS), insn(Opcodes.ARETURN))));
            }

            // The neighbor shifts, (dy << 52) | (dx << 26) | dz.
            m = method(node, "<clinit>", "<clinit>", "()V");
            for (AbstractInsnNode n : expect("<clinit>.yShift", m, 1, getY(), op(Opcodes.I2L), icst(52), op(Opcodes.LSHL))) {
                replace(m, at(n, 2), list(ph("yShift", "()I")));
            }
            for (AbstractInsnNode n : expect("<clinit>.xShift", m, 1, invoke(Opcodes.INVOKEVIRTUAL, "getX", "func_177958_n", "()I"),
                    op(Opcodes.I2L), icst(26), op(Opcodes.LSHL))) {
                replace(m, at(n, 2), list(ph("xShift", "()I")));
            }

            // The first pass over the queue skips a position outside the world's height as it skips an unloaded chunk
            // (World.checkLightFor enqueues any position; only a wide key can decode to one outside).
            m = method(node, "processLightUpdatesForTypeInner", "processLightUpdatesForTypeInner",
                    "(" + SKY + "Lme/jellysquid/mods/phosphor/mod/collections/PooledLongQueue;)V");
            for (AbstractInsnNode n : expect("processLightUpdatesForTypeInner.inWorld", m, 1, aload(0),
                    AlfheimPatches.getfield(curChunk.name), op(Opcodes.IFNONNULL))) {
                m.instructions.insert(next(n), list(new VarInsnNode(Opcodes.ALOAD, 0), copy(world),
                        new VarInsnNode(Opcodes.ALOAD, 0), copy(curPos),
                        ph("inWorld", "(" + CHUNK + WORLD_DESC + POS + ")" + CHUNK)));
            }

            // mChunk, the key masked to its chunk (inlined: one site per use). Phosphor's value is Alfheim's.
            m = method(node, "nextItem", "nextItem", "()Z");
            for (AbstractInsnNode n : expect("nextItem.mChunk", m, 1, lcst(AlfheimKey.NARROW_CHUNK_MASK), op(Opcodes.LAND))) {
                replace(m, n, list(ph("chunkMask", "()J")));
            }
            m = method(node, "fetchNeighborDataFromCursor", "fetchNeighborDataFromCursor", "(" + SKY + ")V");
            for (AbstractInsnNode n : expect("fetchNeighborDataFromCursor.mChunk", m, 1, lcst(AlfheimKey.NARROW_CHUNK_MASK),
                    op(Opcodes.LAND))) {
                replace(m, n, list(ph("chunkMask", "()J")));
            }
            // (key & yCheck) != 0 becomes PhosphorHooks.outside(key, world).
            for (AbstractInsnNode n : expect("fetchNeighborDataFromCursor.yCheck", m, 1, lload(4), lcst(AlfheimKey.NARROW_Y_CHECK),
                    op(Opcodes.LAND), op(Opcodes.LCONST_0), op(Opcodes.LCMP), op(Opcodes.IFEQ))) {
                AbstractInsnNode ldc = at(n, 1);
                m.instructions.remove(at(n, 4));
                m.instructions.remove(at(n, 3));
                m.instructions.remove(at(n, 2));
                replace(m, ldc, list(new VarInsnNode(Opcodes.ALOAD, 0), copy(world), ph("outside", "(J" + WORLD_DESC + ")Z")));
            }
            // The neighbor's section, by slot.
            for (AbstractInsnNode n : expect("fetchNeighborDataFromCursor.slot", m, 1,
                    call("getBlockStorageArray", "func_76587_i", null), aload(-1), getY(), icst(4), op(Opcodes.ISHR),
                    op(Opcodes.AALOAD))) {
                shiftToSlot(m, at(n, 3));
            }
        }
    }

    /** {@code mod.world.lighting.LightingHooks}: the sky light column, boundary flags, the block light scan. */
    static final class LightingHooksPatch extends ClassPatch {
        LightingHooksPatch() {
            super(PKG + "mod.world.lighting.LightingHooks", "PhosphorLightingHooks");
        }

        @Override
        protected void apply(ClassNode node) {
            // relightSkylightColumn(World, Chunk, x, z, height1, height2): the world is parameter 0.
            MethodNode m = method(node, "relightSkylightColumn", "relightSkylightColumn",
                    "(" + WORLD_DESC + CHUNK + "IIII)V");
            for (AbstractInsnNode n : expect("relightSkylightColumn.slot", m, 1, aload(8), iload(6), icst(4),
                    op(Opcodes.ISHR), op(Opcodes.AALOAD))) {
                shiftToSlot(m, at(n, 2));
            }
            for (AbstractInsnNode n : expect("relightSkylightColumn.bottom", m, 1, iload(6), op(Opcodes.IFLE))) {
                compareWith(m, next(n), worldInt(aload0(), "minY"));
            }
            for (AbstractInsnNode n : expect("relightSkylightColumn.sectionSlot", m, 1, aload(8), iload(12),
                    op(Opcodes.AALOAD))) {
                m.instructions.insert(next(n), list(layout("sectionSlot", "(I)I")));
            }
            flagMask(m, "relightSkylightColumn.flagSet", 12);
            flagMask(m, "relightSkylightColumn.flagTest", 19);

            // flagSecBoundaryForUpdate(Chunk, BlockPos, ...): (short) (1 << (pos.getY() >> 4)).
            m = method(node, "flagSecBoundaryForUpdate", "flagSecBoundaryForUpdate", "(" + CHUNK + POS + SKY
                    + "Lnet/minecraft/util/EnumFacing;Lme/jellysquid/mods/phosphor/mod/world/lighting/LightingHooks$EnumBoundaryFacing;)V");
            for (AbstractInsnNode n : expect("flagSecBoundaryForUpdate.flag", m, 1, icst(1), aload(1), getY(), icst(4),
                    op(Opcodes.ISHR), op(Opcodes.ISHL), op(Opcodes.I2S))) {
                AbstractInsnNode iconst4 = at(n, 3);
                m.instructions.remove(at(n, 5));
                m.instructions.remove(at(n, 4));
                replace(m, iconst4, list(new VarInsnNode(Opcodes.ALOAD, 0),
                        new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/chunk/Chunk",
                                n("getWorld", "func_177412_p"), "()" + WORLD_DESC, false),
                        ph("flagMask", "(I" + WORLD_DESC + ")I")));
                m.instructions.remove(n);
            }

            // scheduleRelightChecksForBoundary(World, ...): a flagged bit back to its height range.
            m = method(node, "scheduleRelightChecksForBoundary", "scheduleRelightChecksForBoundary", "(" + WORLD_DESC
                    + CHUNK + CHUNK + CHUNK + SKY + "IILnet/minecraft/util/EnumFacing$AxisDirection;)V");
            for (AbstractInsnNode n : expect("scheduleRelightChecksForBoundary.range", m, 1, iload(17), icst(4),
                    op(Opcodes.ISHL), iload(14), iload(15), iload(17), icst(4), op(Opcodes.ISHL), icst(15), op(Opcodes.IADD),
                    iload(16), call("scheduleRelightChecksForArea", "scheduleRelightChecksForArea", null))) {
                AbstractInsnNode[] k = new AbstractInsnNode[10];
                for (int i = 0; i < 10; i++) {
                    k[i] = at(n, i);
                }
                InsnList bottom = aload0();
                bottom.add(ph("flagBottom", "(I" + WORLD_DESC + ")I"));
                replace(m, k[1], bottom);
                m.instructions.remove(k[2]);
                InsnList top = aload0();
                top.add(ph("flagTop", "(I" + WORLD_DESC + ")I"));
                replace(m, k[6], top);
                m.instructions.remove(k[7]);
                m.instructions.remove(k[8]);
                m.instructions.remove(k[9]);
            }

            // initChunkLighting(Chunk, World): the block light scan's section base (its isAreaLoaded box, y 0..255,
            // meets any world's height).
            m = method(node, "initChunkLighting", "initChunkLighting", "(" + CHUNK + WORLD_DESC + ")V");
            for (AbstractInsnNode n : expect("initChunkLighting.base", m, 1, iload(6), icst(16), op(Opcodes.IMUL), istore(8))) {
                m.instructions.remove(at(n, 2));
                replace(m, next(n), list(layout("slotBase", "(I)I")));
            }

            // initSkylightForSection already compares the height map with the section's own base; nothing to change.
            m = method(node, "initSkylightForSection", "initSkylightForSection", "(" + WORLD_DESC + CHUNK + EBS + ")V");
            expect("initSkylightForSection.base", m, 1, call("getHeightValue", "func_76611_b", "(II)I"), aload(2),
                    call("getYLocation", "func_76662_d", "()I"), op(Opcodes.IF_ICMPGT));
        }

        /** {@code iconst_1; iload var; ishl} becomes {@code PhosphorHooks.flagMask(var, world)} (world: parameter 0). */
        private void flagMask(MethodNode m, String id, int var) {
            List<AbstractInsnNode> found = expect(id, m, 1, icst(1), iload(var), op(Opcodes.ISHL));
            for (AbstractInsnNode n : found) {
                AbstractInsnNode load = next(n);
                m.instructions.remove(next(load));
                m.instructions.remove(n);
                InsnList l = aload0();
                l.add(ph("flagMask", "(I" + WORLD_DESC + ")I"));
                m.instructions.insert(load, l);
            }
        }
    }

    /** {@code mod.world.lighting.LightingEngineHelpers.posToState(BlockPos, Chunk)}: the section, by slot. */
    static final class LightingEngineHelpersPatch extends ClassPatch {
        LightingEngineHelpersPatch() {
            super(PKG + "mod.world.lighting.LightingEngineHelpers", "PhosphorLightingEngineHelpers");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "posToState", "posToState",
                    "(" + POS + CHUNK + ")Lnet/minecraft/block/state/IBlockState;");
            for (AbstractInsnNode n : expect("posToState.slot", m, 1, call("getBlockStorageArray", "func_76587_i", null),
                    aload(0), getY(), icst(4), op(Opcodes.ISHR), op(Opcodes.AALOAD))) {
                shiftToSlot(m, at(n, 3));
            }
        }
    }

    /**
     * {@code mixins.lighting.common.MixinChunk}: every light read, its {@code generateSkylightMap} redirect, and its
     * {@code relightBlock} overwrite (which drops {@code ChunkPatch}'s {@code relightBlock.mask} and {@code .bottom}).
     * Its other overwrites need nothing: {@code getLightFor} reads through {@code getCachedLightFor} (patched here),
     * {@code checkLight()} goes to {@code LightingHooks}, and {@code recheckGaps} works in real heights.
     */
    static final class MixinChunkPatch extends ClassPatch {
        MixinChunkPatch() {
            super(PKG + "mixins.lighting.common.MixinChunk", "PhosphorMixinChunk");
        }

        @Override
        protected void apply(ClassNode node) {
            FieldInsnNode world = own(node, "world", "field_76637_e");
            if (!site("world", world != null)) {
                return;
            }
            P storage = field(Opcodes.GETFIELD, "storageArrays", "field_76652_q");

            MethodNode m = method(node, "getCachedLightFor", "getCachedLightFor", "(" + SKY + POS + ")I");
            for (AbstractInsnNode n : expect("getCachedLightFor.slot", m, 1, storage, iload(4), icst(4), op(Opcodes.ISHR),
                    op(Opcodes.AALOAD))) {
                shiftToSlot(m, at(n, 2));
            }

            m = method(node, "setLightForRedirectGenerateSkylightMap", "setLightForRedirectGenerateSkylightMap",
                    "(" + CHUNK + SKY + POS + "I)V");
            for (AbstractInsnNode n : expect("setLightForRedirectGenerateSkylightMap.slot", m, 1, storage, aload(3), getY(),
                    icst(4), op(Opcodes.ISHR), op(Opcodes.AALOAD))) {
                shiftToSlot(m, at(n, 3));
            }

            m = method(node, "relightBlock", "func_76615_h", "(III)V");
            for (AbstractInsnNode n : expect("relightBlock.mask", m, 1, icst(255), op(Opcodes.IAND))) {
                m.instructions.remove(next(n));
                InsnList l = list(new VarInsnNode(Opcodes.ALOAD, 0), copy(world));
                l.add(heights("maskHeight", "(I" + WORLD_DESC + ")I"));
                replace(m, n, l);
            }
            for (AbstractInsnNode n : expect("relightBlock.bottom", m, 1, iload(5), op(Opcodes.IFLE))) {
                compareWith(m, next(n), worldInt(list(new VarInsnNode(Opcodes.ALOAD, 0), copy(world)), "minY"));
            }
        }
    }
}
