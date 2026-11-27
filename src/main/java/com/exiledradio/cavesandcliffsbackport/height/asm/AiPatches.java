package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * Mob movement and AI that stop at y 0 or 1. Below y 0 of an extended world:
 * <ul>
 *   <li>{@code WalkNodeProcessor.getPathNodeType} only looks at the floor from y 1 up, so every node below y 1 is
 *       OPEN, never WALKABLE. {@code EntityMoveHelper}'s strafe then cancels each strafe and walks forward: skeletons
 *       and other strafing mobs rush in. {@code FlyingNodeProcessor} has the same check.</li>
 *   <li>{@code WalkNodeProcessor.getStart} (a mob off the ground) and {@code getSafePoint} (stepping down) stop
 *       walking down at y 0, so paths start and end in the air there.</li>
 *   <li>{@code PathNavigateGround.getPathToPos} looks for the ground under a target only above y 0.</li>
 *   <li>{@code EntityBat.updateAITasks} drops a target below y 1 every tick and picks a new one: bats twitch, turn
 *       and end up inside blocks.</li>
 *   <li>{@code EntityLivingBase.attemptTeleport} (endermen, chorus fruit) finds no ground below y 0.</li>
 * </ul>
 * Each becomes the world's bottom (the same 0 or 1 in a 0-255 world). A miss leaves that class as vanilla's without
 * turning extended height off, since a mob AI quirk is not worth losing the world's height over. The sites go to the
 * mod-compat report ({@code PatchReport.compatLines}).
 */
final class AiPatches {

    private static final String IBLOCKACCESS = "Lnet/minecraft/world/IBlockAccess;";
    private static final String NODE_TYPE = "(" + IBLOCKACCESS + "III)Lnet/minecraft/pathfinding/PathNodeType;";

    private AiPatches() {
    }

    private abstract static class AiPatch extends ClassPatch {
        AiPatch(String target) {
            super(target);
        }

        AiPatch(String target, String label) {
            super(target, label);
        }

        @Override
        protected boolean modCompat() {
            return true;
        }
    }

    /** {@code Heights.aboveBottomOf(access)}, the access being local {@code var}. */
    private static InsnList aboveBottomOf(int var) {
        return list(new VarInsnNode(Opcodes.ALOAD, var), heights("aboveBottomOf", "(" + IBLOCKACCESS + ")I"));
    }

    /** {@code Heights.minYOf(this.blockaccess)} in a node processor. */
    private static InsnList minYOfBlockaccess() {
        InsnList l = selfField("net/minecraft/pathfinding/NodeProcessor", n("blockaccess", "field_176169_a"), IBLOCKACCESS);
        l.add(heights("minYOf", "(" + IBLOCKACCESS + ")I"));
        return l;
    }

    private static InsnList entityWorld() {
        return selfField("net/minecraft/entity/Entity", n("world", "field_70170_p"), WORLD_DESC);
    }

    /** {@code getPathNodeType(access, x, y, z)}: {@code y >= 1} (y is local 3) becomes one above the bottom. */
    private static void floorCheck(ClassPatch patch, MethodNode m) {
        for (AbstractInsnNode n : patch.expect("getPathNodeType.floor", m, 1, iload(3), icst(1), op(Opcodes.IF_ICMPLT))) {
            replace(m, next(n), aboveBottomOf(1));
        }
    }

    static final class WalkNodeProcessorPatch extends AiPatch {
        WalkNodeProcessorPatch() {
            super("net.minecraft.pathfinding.WalkNodeProcessor");
        }

        @Override
        protected void apply(ClassNode node) {
            floorCheck(this, method(node, "getPathNodeType", "func_186330_a", NODE_TYPE));
            MethodNode start = method(node, "getStart", "func_186318_b", "()Lnet/minecraft/pathfinding/PathPoint;");
            for (AbstractInsnNode n : expect("getStart.down", start, 1, getY(), op(Opcodes.IFLE))) {
                compareWith(start, next(n), minYOfBlockaccess());
            }
            MethodNode safe = method(node, "getSafePoint", "func_186332_a",
                    "(IIIIDLnet/minecraft/util/EnumFacing;)Lnet/minecraft/pathfinding/PathPoint;");
            // while (y > 0 && type == OPEN): y is parameter 2.
            for (AbstractInsnNode n : expect("getSafePoint.fall", safe, 1, iload(2), op(Opcodes.IFLE))) {
                compareWith(safe, next(n), minYOfBlockaccess());
            }
        }
    }

    static final class FlyingNodeProcessorPatch extends AiPatch {
        FlyingNodeProcessorPatch() {
            super("net.minecraft.pathfinding.FlyingNodeProcessor");
        }

        @Override
        protected void apply(ClassNode node) {
            floorCheck(this, method(node, "getPathNodeType", "func_186330_a", NODE_TYPE));
        }
    }

    static final class PathNavigateGroundPatch extends AiPatch {
        PathNavigateGroundPatch() {
            super("net.minecraft.pathfinding.PathNavigateGround");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "getPathToPos", "func_179680_a",
                    "(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/pathfinding/Path;");
            for (AbstractInsnNode n : expect("getPathToPos.ground", m, 2, getY(), op(Opcodes.IFLE))) {
                compareWith(m, next(n), worldInt(selfField("net/minecraft/pathfinding/PathNavigate",
                        n("world", "field_75513_b"), WORLD_DESC), "minY"));
            }
        }
    }

    static final class BatPatch extends AiPatch {
        BatPatch() {
            super("net.minecraft.entity.passive.EntityBat");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "updateAITasks", "func_70619_bc", "()V");
            // spawnPosition.getY() < 1, compiled as getY; iconst_1; if_icmpge.
            for (AbstractInsnNode n : expect("updateAITasks.bottom", m, 1, getY(), icst(1), op(Opcodes.IF_ICMPGE))) {
                replace(m, next(n), worldInt(entityWorld(), "aboveBottom"));
            }
        }
    }

    static final class TeleportPatch extends AiPatch {
        TeleportPatch() {
            super("net.minecraft.entity.EntityLivingBase", "EntityLivingBaseTeleport");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "attemptTeleport", "func_184595_k", "(DDD)Z");
            for (AbstractInsnNode n : expect("attemptTeleport.ground", m, 1, getY(), op(Opcodes.IFLE))) {
                compareWith(m, next(n), worldInt(entityWorld(), "minY"));
            }
        }
    }
}
