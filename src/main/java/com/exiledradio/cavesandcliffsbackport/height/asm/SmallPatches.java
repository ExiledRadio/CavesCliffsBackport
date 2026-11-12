package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/** The one- and two-site patches of the server side, one class each. */
final class SmallPatches {

    private static final String ENTITY = "net/minecraft/entity/Entity";

    private SmallPatches() {
    }

    private static InsnList entityWorld() {
        return selfField(ENTITY, n("world", "field_70170_p"), WORLD_DESC);
    }

    /** {@code Entity.onEntityUpdate}: {@code posY < -64} (the void) becomes 64 below the world's bottom. */
    static final class EntityPatch extends ClassPatch {
        EntityPatch() {
            super("net.minecraft.entity.Entity");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "onEntityUpdate", "func_70030_z", "()V");
            for (AbstractInsnNode n : expect("onEntityUpdate.void", m, 1, field(Opcodes.GETFIELD, "posY", "field_70163_u"),
                    dcst(-64.0D), op(Opcodes.DCMPG))) {
                InsnList l = entityWorld();
                l.add(heights("voidY", "(" + WORLD_DESC + ")D"));
                replace(m, next(n), l);
            }
        }
    }

    /**
     * {@code EntityFallingBlock.onUpdate}: a falling block that has fallen below y 1 or above 256 for five seconds is
     * dropped; 1.18 measures from the world's bottom and top.
     */
    static final class FallingBlockPatch extends ClassPatch {
        FallingBlockPatch() {
            super("net.minecraft.entity.item.EntityFallingBlock");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "onUpdate", "func_70071_h_", "()V");
            for (AbstractInsnNode n : expect("onUpdate.bottom", m, 1, getY(), icst(1), op(Opcodes.IF_ICMPLT))) {
                replace(m, next(n), worldInt(entityWorld(), "aboveBottom"));
            }
            for (AbstractInsnNode n : expect("onUpdate.top", m, 1, getY(), icst(256), op(Opcodes.IF_ICMPGT))) {
                replace(m, next(n), worldInt(entityWorld(), "maxY"));
            }
        }
    }

    /**
     * {@code NetHandlerPlayServer}: placing and breaking check the server's build limit (at most 256); in an extended
     * world the limit is its top.
     */
    static final class NetHandlerPlayServerPatch extends ClassPatch {
        NetHandlerPlayServerPatch() {
            super("net.minecraft.network.NetHandlerPlayServer");
        }

        @Override
        protected void apply(ClassNode node) {
            limit(method(node, "processPlayerDigging", "func_147345_a",
                    "(Lnet/minecraft/network/play/client/CPacketPlayerDigging;)V"), "processPlayerDigging", 1);
            limit(method(node, "processTryUseItemOnBlock", "func_184337_a",
                    "(Lnet/minecraft/network/play/client/CPacketPlayerTryUseItemOnBlock;)V"), "processTryUseItemOnBlock", 3);
        }

        private void limit(MethodNode m, String id, int count) {
            for (AbstractInsnNode n : expect(id + ".buildLimit", m, count,
                    invoke(Opcodes.INVOKEVIRTUAL, "getBuildLimit", "func_71207_Z", "()I"))) {
                replace(m, n, list(new VarInsnNode(Opcodes.ALOAD, 0),
                        new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/network/NetHandlerPlayServer",
                                n("player", "field_147369_b"), "Lnet/minecraft/entity/player/EntityPlayerMP;"),
                        heights("buildLimit", "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/entity/player/EntityPlayerMP;)I")));
            }
        }
    }

    /**
     * {@code WorldEntitySpawner.getRandomChunkPosition}: a spawn attempt's height is drawn from the world's bottom, so
     * mobs spawn below y 0 in an extended world. One random draw either way.
     */
    static final class SpawnerPatch extends ClassPatch {
        SpawnerPatch() {
            super("net.minecraft.world.WorldEntitySpawner");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "getRandomChunkPosition", "func_180621_a",
                    "(" + WORLD_DESC + "II)Lnet/minecraft/util/math/BlockPos;");
            for (AbstractInsnNode n : expect("getRandomChunkPosition.y", m, 1,
                    invoke(Opcodes.INVOKEVIRTUAL, "nextInt", "nextInt", "(I)I"), istore(-1))) {
                replace(m, n, list(new VarInsnNode(Opcodes.ALOAD, 0),
                        heights("spawnY", "(Ljava/util/Random;I" + WORLD_DESC + ")I")));
            }
        }
    }

    /**
     * {@code WorldInfo(WorldSettings, String)}, where a new save gets its world type: lets the config switch make the
     * Caves & Cliffs type the default ({@code height.HeightHooks.newWorldInfo}).
     */
    static final class WorldInfoPatch extends ClassPatch {
        WorldInfoPatch() {
            super("net.minecraft.world.storage.WorldInfo");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "<init>", "<init>", "(Lnet/minecraft/world/WorldSettings;Ljava/lang/String;)V");
            for (AbstractInsnNode n : expect("<init>(WorldSettings).return", m, 1, op(Opcodes.RETURN))) {
                m.instructions.insertBefore(n, list(new VarInsnNode(Opcodes.ALOAD, 0), new VarInsnNode(Opcodes.ALOAD, 2),
                        hooks("newWorldInfo", "(Lnet/minecraft/world/storage/WorldInfo;Ljava/lang/String;)V")));
            }
        }
    }

    /**
     * VintageFix's {@code chunk_access.ChunkMixin} overwrites {@code Chunk.getBlockState(int, int, int)} with vanilla's
     * body less the debug-world branch, after our transformer patched Chunk. Mixin reads a mixin class through the
     * transformer chain, so its overwrite gets the same three sites as ours: the y guard and both section lookups.
     */
    static final class VintageFixChunkPatch extends ClassPatch {
        VintageFixChunkPatch() {
            super("org.embeddedt.vintagefix.mixin.chunk_access.ChunkMixin");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "getBlockState", "func_186032_a", "(III)Lnet/minecraft/block/state/IBlockState;");
            P storage = field(Opcodes.GETFIELD, "storageArrays", "field_76652_q");
            for (AbstractInsnNode n : expect("getBlockState.notBelow", m, 1, iload(2), op(Opcodes.IFLT))) {
                m.instructions.insert(n, list(layout("notBelow", "(I)I")));
            }
            for (AbstractInsnNode n : expect("getBlockState.guard", m, 1, iload(2), icst(4), op(Opcodes.ISHR),
                    aload(0), storage, op(Opcodes.ARRAYLENGTH))) {
                shiftToSlot(m, next(n));
            }
            for (AbstractInsnNode n : expect("getBlockState.slot", m, 1, storage, iload(2), icst(4), op(Opcodes.ISHR))) {
                shiftToSlot(m, at(n, 2));
            }
        }
    }
}
