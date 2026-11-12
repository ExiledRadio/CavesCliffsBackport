package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * {@code net.minecraft.world.World}: carries its height ({@code height.HeightWorld}), decides it when constructed,
 * and answers every build-height and light range check with it instead of 0 and 256.
 */
final class WorldPatch extends ClassPatch {

    static final String HEIGHT_WORLD = "com/exiledradio/cavesandcliffsbackport/height/HeightWorld";
    private static final String BELOW = "cavesandcliffsbackport$below";
    private static final String ABOVE = "cavesandcliffsbackport$above";
    private static final String POS = "(Lnet/minecraft/util/math/BlockPos;)";
    private static final String SKY_POS = "(Lnet/minecraft/world/EnumSkyBlock;Lnet/minecraft/util/math/BlockPos;)I";

    private final boolean client;

    WorldPatch(boolean client) {
        super("net.minecraft.world.World");
        this.client = client;
    }

    @Override
    protected void apply(ClassNode node) {
        addHeight(node);

        MethodNode init = method(node, "<init>", "<init>", "(Lnet/minecraft/world/storage/ISaveHandler;"
                + "Lnet/minecraft/world/storage/WorldInfo;Lnet/minecraft/world/WorldProvider;"
                + "Lnet/minecraft/profiler/Profiler;Z)V");
        for (AbstractInsnNode ret : expect("<init>.return", init, 1, op(Opcodes.RETURN))) {
            init.instructions.insertBefore(ret, list(new VarInsnNode(Opcodes.ALOAD, 0),
                    hooks("worldConstructed", "(" + WORLD_DESC + ")V")));
        }

        MethodNode m = method(node, "isOutsideBuildHeight", "func_189509_E", POS + "Z");
        minAndMax(m, "isOutsideBuildHeight", 1, 1);

        m = method(node, "isAreaLoaded", "func_175663_a", "(IIIIIIZ)Z");
        for (AbstractInsnNode n : expect("isAreaLoaded.min", m, 1, iload(5), zeroJump())) {
            compareWith(m, next(n), minY());
        }
        for (AbstractInsnNode n : expect("isAreaLoaded.max", m, 1, iload(2), icst(256))) {
            replace(m, next(n), maxY());
        }

        m = method(node, "getLight", "func_175699_k", POS + "I");
        minAndMax(m, "getLight", 1, 1);
        top(m, "getLight", 1);

        m = method(node, "getLight", "func_175721_c", "(Lnet/minecraft/util/math/BlockPos;Z)I");
        minAndMax(m, "getLight(Z)", 1, 1);
        top(m, "getLight(Z)", 1);

        if (this.client) {
            m = method(node, "getLightFromNeighborsFor", "func_175705_a", SKY_POS);
            minAndMax(m, "getLightFromNeighborsFor", 1, 0);
            clampToMin(m, "getLightFromNeighborsFor");
        }

        m = method(node, "getLightFor", "func_175642_b", SKY_POS);
        minAndMax(m, "getLightFor", 1, 0);
        clampToMin(m, "getLightFor");

        m = method(node, "canBlockFreezeBody", "canBlockFreezeBody", "(Lnet/minecraft/util/math/BlockPos;Z)Z");
        minAndMax(m, "canBlockFreezeBody", 1, 1);

        m = method(node, "canSnowAtBody", "canSnowAtBody", "(Lnet/minecraft/util/math/BlockPos;Z)Z");
        minAndMax(m, "canSnowAtBody", 1, 1);

        m = method(node, "getTopSolidOrLiquidBlock", "func_175672_r", POS + "Lnet/minecraft/util/math/BlockPos;");
        minAndMax(m, "getTopSolidOrLiquidBlock", 1, 0);
    }

    /** {@code pos.getY() <cmp> 0} becomes {@code < minY}, {@code pos.getY() <cmp> 256} becomes {@code < maxY}. */
    private void minAndMax(MethodNode m, String id, int mins, int maxes) {
        for (AbstractInsnNode n : expect(id + ".min", m, mins, getY(), zeroJump())) {
            compareWith(m, next(n), minY());
        }
        if (maxes > 0) {
            for (AbstractInsnNode n : expect(id + ".max", m, maxes, getY(), icst(256))) {
                replace(m, next(n), maxY());
            }
        }
    }

    /** {@code new BlockPos(x, 255, z)} becomes the world's top. */
    private void top(MethodNode m, String id, int count) {
        for (AbstractInsnNode n : expect(id + ".top", m, count, icst(255))) {
            replace(m, n, worldInt(self(), "topY"));
        }
    }

    /** {@code new BlockPos(pos.getX(), 0, pos.getZ())} becomes the world's bottom. */
    private void clampToMin(MethodNode m, String id) {
        for (AbstractInsnNode n : expect(id + ".clamp", m, 1,
                invoke(Opcodes.INVOKEVIRTUAL, "getX", "func_177958_n", "()I"), icst(0), aload(-1),
                invoke(Opcodes.INVOKEVIRTUAL, "getZ", "func_177952_p", "()I"))) {
            replace(m, next(n), minY());
        }
    }

    private static InsnList minY() {
        return worldInt(self(), "minY");
    }

    private static InsnList maxY() {
        return worldInt(self(), "maxY");
    }

    /** Implements HeightWorld with two int fields that start at 0 (a vanilla world). */
    private void addHeight(ClassNode node) {
        if (!node.interfaces.contains(HEIGHT_WORLD)) {
            node.interfaces.add(HEIGHT_WORLD);
        }
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, BELOW, "I", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, ABOVE, "I", null, null));

        MethodNode min = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "cavesandcliffsbackport$minY", "()I", null, null);
        min.instructions.add(list(new VarInsnNode(Opcodes.ALOAD, 0), new FieldInsnNode(Opcodes.GETFIELD, WORLD, BELOW, "I"),
                new InsnNode(Opcodes.INEG), new InsnNode(Opcodes.IRETURN)));
        node.methods.add(min);

        MethodNode max = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "cavesandcliffsbackport$maxY", "()I", null, null);
        max.instructions.add(list(new IntInsnNode(Opcodes.SIPUSH, 256), new VarInsnNode(Opcodes.ALOAD, 0),
                new FieldInsnNode(Opcodes.GETFIELD, WORLD, ABOVE, "I"), new InsnNode(Opcodes.IADD), new InsnNode(Opcodes.IRETURN)));
        node.methods.add(max);

        MethodNode set = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "cavesandcliffsbackport$setHeight", "(II)V", null, null);
        set.instructions.add(list(new VarInsnNode(Opcodes.ALOAD, 0), new VarInsnNode(Opcodes.ILOAD, 1), new InsnNode(Opcodes.INEG),
                new FieldInsnNode(Opcodes.PUTFIELD, WORLD, BELOW, "I"),
                new VarInsnNode(Opcodes.ALOAD, 0), new VarInsnNode(Opcodes.ILOAD, 2), new IntInsnNode(Opcodes.SIPUSH, 256),
                new InsnNode(Opcodes.ISUB), new FieldInsnNode(Opcodes.PUTFIELD, WORLD, ABOVE, "I"), new InsnNode(Opcodes.RETURN)));
        node.methods.add(set);
        site("HeightWorld", true);
    }
}
