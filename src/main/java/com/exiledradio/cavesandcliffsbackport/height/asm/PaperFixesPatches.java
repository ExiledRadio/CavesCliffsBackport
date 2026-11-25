package com.exiledradio.cavesandcliffsbackport.height.asm;

import com.exiledradio.cavesandcliffsbackport.height.HeightLayout;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * PaperFixes 2.0 (Elephant1214): two of its Lithium ports overwrite code that reads blocks by y with a 0-255 copy.
 * <ul>
 *   <li>{@code pathing_chunk_cache.ChunkCacheMixin} ({@code pathingChunkCache}) overwrites
 *       {@code ChunkCache.getBlockState}: air outside 0-256, and {@code getBlockStorageArray()[y >> 4]}. ChunkCache is
 *       the view chunk rendering and pathfinding read, so nothing below 0 was drawn and mobs saw air there.</li>
 *   <li>{@code path_node_cache.WalkNodeProcessorMixin} ({@code pathNodeCache}) overwrites
 *       {@code WalkNodeProcessor.checkNeighborBlocks}: the same bounds and read for its section shortcut.</li>
 * </ul>
 * Mixin applies them after this coremod's transformer, so they replace {@link ChunkCachePatch}'s site. The mixin
 * classes are patched as Mixin reads them through the transformer chain (as {@link AlfheimPatches}): the bounds become
 * the widest world's ({@code HeightLayout.EXTENDED_MIN_Y}..{@code EXTENDED_MAX_Y}) and each section read goes through
 * {@code HeightHooks.sectionAt}, our slot or null outside the chunk's array, which PaperFixes already treats as an
 * empty section. A 0-255 world reads exactly as before (its array has no slot below 0 or above 255). Patterns are
 * PaperFixes 2.0.1's; a miss turns extended height off with PaperFixes and its options named ({@code HeightContent}).
 */
final class PaperFixesPatches {

    static final String LABEL_CACHE = "PaperFixesPathingChunkCache";
    static final String LABEL_WALK = "PaperFixesWalkNodeProcessor";
    private static final String EBS = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;";

    private PaperFixesPatches() {
    }

    /** {@code y < 0} becomes {@code y < -64}, {@code y >= 256} becomes {@code y >= 320}, the reads by our slots. */
    static void widen(ClassPatch patch, MethodNode m, String id, int yVar, int reads) {
        List<AbstractInsnNode> min = patch.expect(id + ".min", m, 1, iload(yVar), op(Opcodes.IFLT));
        List<AbstractInsnNode> max = patch.expect(id + ".max", m, 1, iload(yVar), icst(256), op(Opcodes.IF_ICMPGE));
        List<AbstractInsnNode> read = patch.expect(id + ".section", m, reads, iload(yVar), icst(4), op(Opcodes.ISHR),
                op(Opcodes.AALOAD));
        if (min.isEmpty() || max.isEmpty() || read.isEmpty()) {
            return;
        }
        compareWith(m, next(min.get(0)), list(new IntInsnNode(Opcodes.BIPUSH, HeightLayout.EXTENDED_MIN_Y)));
        replace(m, next(max.get(0)), list(new IntInsnNode(Opcodes.SIPUSH, HeightLayout.EXTENDED_MAX_Y)));
        for (AbstractInsnNode n : read) {
            replace(m, at(n, 3), list(hooks("sectionAt", "([" + EBS + "I)" + EBS)));
        }
    }

    /** {@code me.elephant1214.paperfixes.mixin.common.lithium.pathing_chunk_cache.ChunkCacheMixin.getBlockState}. */
    static final class PathingChunkCachePatch extends ClassPatch {
        PathingChunkCachePatch() {
            super("me.elephant1214.paperfixes.mixin.common.lithium.pathing_chunk_cache.ChunkCacheMixin", LABEL_CACHE);
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "getBlockState", "func_180495_p",
                    "(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;");
            // y is read once into local 2: int y = pos.getY().
            if (site("getBlockState.yLocal", m != null && find(m, getY(), istore(2)).size() == 1)) {
                widen(this, m, "getBlockState", 2, 1);
            }
        }
    }

    /** {@code me.elephant1214.paperfixes.mixin.common.lithium.path_node_cache.WalkNodeProcessorMixin}. */
    static final class WalkNodeProcessorPatch extends ClassPatch {
        WalkNodeProcessorPatch() {
            super("me.elephant1214.paperfixes.mixin.common.lithium.path_node_cache.WalkNodeProcessorMixin", LABEL_WALK);
        }

        @Override
        protected void apply(ClassNode node) {
            // checkNeighborBlocks(IBlockAccess, x, y, z, nodeType): y is parameter 3; a World and a ChunkCache branch.
            MethodNode m = method(node, "checkNeighborBlocks", "func_193578_a",
                    "(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraft/pathfinding/PathNodeType;)"
                            + "Lnet/minecraft/pathfinding/PathNodeType;");
            widen(this, m, "checkNeighborBlocks", 3, 2);
        }
    }
}
