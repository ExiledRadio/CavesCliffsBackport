package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * Nether portals at every height, as in 1.18: a frame lights anywhere from the world's bottom to its top, a portal
 * below 0 spawns zombie pigmen like any other, and the overworld's portal search and portal building cover the whole
 * height. Each site is one of 1.12's {@code y > 0} / {@code y >= 0} floors, which becomes the world's bottom
 * ({@code Heights.minY}, 0 in any world that is not extended); the tops already come from
 * {@code WorldProvider.getActualHeight} (patched). The fallback height for a portal built where no space was found
 * stays clamped to 70 and up, as 1.18's {@code max(minY + 1, 70)} is.
 */
final class PortalPatches {

    private PortalPatches() {
    }

    /** A zero-compare jump on a height ({@code y > 0}, {@code y >= 0}) becomes a compare with the world's bottom. */
    private static void floor(MethodNode m, AbstractInsnNode jump, InsnList pushWorld) {
        compareWith(m, jump, worldInt(pushWorld, "minY"));
    }

    /**
     * {@code BlockPortal.updateTick}: zombie pigmen come out of a portal. Vanilla walks down from the portal block to
     * the ground while {@code y > 0}, and spawns only from a portal above 0.
     */
    static final class BlockPortalPatch extends ClassPatch {
        BlockPortalPatch() {
            super("net.minecraft.block.BlockPortal");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "updateTick", "func_180650_b", "(" + WORLD_DESC
                    + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V");
            for (AbstractInsnNode n : expect("updateTick.ground", m, 1, getY(), op(Opcodes.IFLE))) {
                floor(m, next(n), list(new VarInsnNode(Opcodes.ALOAD, 1)));
            }
            List<AbstractInsnNode> start = expect("updateTick.startY", m, 1, getY(), istore(-1));
            for (AbstractInsnNode n : start) {
                int var = ((VarInsnNode) next(n)).var;
                for (AbstractInsnNode i : expect("updateTick.above", m, 1, iload(var), op(Opcodes.IFLE))) {
                    floor(m, next(i), list(new VarInsnNode(Opcodes.ALOAD, 1)));
                }
            }
        }
    }

    /**
     * {@code BlockPortal.Size}: lighting a frame from above its bottom row walks down to that row while
     * {@code y > 0}; below 0 it never reached the frame's bottom and the portal did not light.
     */
    static final class BlockPortalSizePatch extends ClassPatch {
        private static final String SIZE = "net/minecraft/block/BlockPortal$Size";

        BlockPortalSizePatch() {
            super("net.minecraft.block.BlockPortal$Size");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "<init>", "<init>",
                    "(" + WORLD_DESC + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing$Axis;)V");
            // The walk's other test (y > start - 21) is relative and stays.
            for (AbstractInsnNode n : expect("init.bottom", m, 1, getY(), op(Opcodes.IFLE))) {
                floor(m, next(n), selfField(SIZE, n("world", "field_150867_a"), WORLD_DESC));
            }
        }
    }

    /**
     * {@code Teleporter}: an arriving entity's search for a portal (within 128 blocks) and the column search for where
     * to build one both run from the top ({@code getActualHeight() - 1}) down to y 0; they run down to the world's
     * bottom.
     */
    static final class TeleporterPatch extends ClassPatch {
        private static final String TP = "net/minecraft/world/Teleporter";

        TeleporterPatch() {
            super("net.minecraft.world.Teleporter");
        }

        private static InsnList world() {
            return selfField(TP, n("world", "field_85192_a"), "Lnet/minecraft/world/WorldServer;");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "placeInExistingPortal", "func_180620_b", "(Lnet/minecraft/entity/Entity;F)Z");
            for (AbstractInsnNode n : expect("placeInExistingPortal.bottom", m, 1, getY(), op(Opcodes.IFLT))) {
                floor(m, next(n), world());
            }

            // Two passes (a 3-wide spot, then a 1-wide one), each: for (y = height - 1; y >= 0; --y), and inside it
            // while (y > 0 && air below) --y.
            m = method(node, "makePortal", "func_85188_a", "(Lnet/minecraft/entity/Entity;)Z");
            List<AbstractInsnNode> loops = expect("makePortal.columns", m, 2,
                    invoke(Opcodes.INVOKEVIRTUAL, "getActualHeight", "func_72940_L", "()I"), icst(1), op(Opcodes.ISUB), istore(-1));
            if (loops.isEmpty()) {
                return;
            }
            int var = ((VarInsnNode) at(loops.get(0), 3)).var;
            if (!site("makePortal.sameLocal", ((VarInsnNode) at(loops.get(1), 3)).var == var)) {
                return;
            }
            for (AbstractInsnNode n : expect("makePortal.bottom", m, 2, iload(var), op(Opcodes.IFLT))) {
                floor(m, next(n), world());
            }
            for (AbstractInsnNode n : expect("makePortal.ground", m, 2, iload(var), op(Opcodes.IFLE))) {
                floor(m, next(n), world());
            }
        }
    }
}
