package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * Other mods' own code that assumes y 0-255 (plan {@code docs/streams/moddedores.md}, O3): BuildCraft 8.0.0 (and
 * 7.99.24.8, same classes) and KAGIC X11p13. One class per target. Every site calls {@code height.compat}'s
 * {@code BuildCraftHooks} or {@code KagicHooks}, which answer the mod's own value in a world of 0-255, so only a
 * Caves &amp; Cliffs world sees a change. The patched sites push only the mod's own locals and fields: no Minecraft
 * member name is emitted. A miss leaves that class as its mod wrote it ({@link ClassPatch#modCompat}).
 */
final class ModCompatPatches {

    static final String BC_HOOKS = "com/exiledradio/cavesandcliffsbackport/height/compat/BuildCraftHooks";
    static final String KAGIC_HOOKS = "com/exiledradio/cavesandcliffsbackport/height/compat/KagicHooks";
    private static final String POS = "Lnet/minecraft/util/math/BlockPos;";
    private static final String TE = "Lnet/minecraft/tileentity/TileEntity;";

    private ModCompatPatches() {
    }

    static MethodInsnNode bc(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, BC_HOOKS, name, desc, false);
    }

    static MethodInsnNode kagic(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, KAGIC_HOOKS, name, desc, false);
    }

    static P ldc(final Object value) {
        return n -> n instanceof LdcInsnNode && value.equals(((LdcInsnNode) n).cst);
    }

    static P own(final int opcode, final String name) {
        return n -> n.getOpcode() == opcode && ((FieldInsnNode) n).name.equals(name);
    }

    static P iinc(final int increment) {
        return n -> n instanceof IincInsnNode && ((IincInsnNode) n).incr == increment;
    }

    static FieldNode field(ClassNode node, String name) {
        for (FieldNode f : node.fields) {
            if (f.name.equals(name)) {
                return f;
            }
        }
        return null;
    }

    /** The base of every mod-compat patch. */
    abstract static class ModPatch extends ClassPatch {
        ModPatch(String target, String label) {
            super(target, label);
        }

        @Override
        protected final boolean modCompat() {
            return true;
        }
    }

    /**
     * {@code buildcraft.builders.tile.TileQuarry}: the mining box bottom BuildCraft clamps to a hard-coded 0 becomes the
     * world's bottom (B1), and a saved box whose bottom is 0 only because of that clamp is lowered at load (B2).
     */
    static final class QuarryPatch extends ModPatch {
        QuarryPatch() {
            super("buildcraft.builders.tile.TileQuarry", "BuildCraftQuarry");
        }

        @Override
        protected void apply(ClassNode node) {
            // B1: int minY = max.getY() - 1 - BCCoreConfig.miningMaxDepth;
            //     if (world.isOutsideBuildHeight(new BlockPos(min.getX(), minY, min.getZ()))) minY = 0;
            MethodNode m = method(node, "onPlacedBy", "onPlacedBy",
                    "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V");
            FieldInsnNode depth = null;
            int floor = -1;
            for (AbstractInsnNode n : expect("onPlacedBy.depth", m, 1, getY(), icst(1), op(Opcodes.ISUB),
                    own(Opcodes.GETSTATIC, "miningMaxDepth"), op(Opcodes.ISUB), istore(-1))) {
                depth = (FieldInsnNode) at(n, 3);
                floor = ((VarInsnNode) at(n, 5)).var;
            }
            if (depth == null) {
                return;
            }
            for (AbstractInsnNode n : expect("onPlacedBy.floor", m, 1,
                    invoke(Opcodes.INVOKEVIRTUAL, "isOutsideBuildHeight", "func_189509_E", "(" + POS + ")Z"),
                    op(Opcodes.IFEQ), icst(0), istore(floor))) {
                replace(m, at(n, 2), list(new VarInsnNode(Opcodes.ALOAD, 0), bc("quarryFloor", "(" + TE + ")I")));
            }

            // B2: right after readFromNBT reads the boxes and the iterator (boxIterator = BoxIterator.readFromNbt(..)):
            //     BlockPos low = savedQuarryFloor(this, frameBox.max(), miningBox.min(), miningMaxDepth);
            //     miningBox.setMin(low);  (BuildCraft's setMin ignores null)
            //     boxIterator = quarryIterator(boxIterator, low);
            FieldNode frameBox = field(node, "frameBox");
            FieldNode miningBox = field(node, "miningBox");
            FieldNode iterator = field(node, "boxIterator");
            if (!site("fields", frameBox != null && miningBox != null && iterator != null
                    && frameBox.desc.equals(miningBox.desc))) {
                return;
            }
            String box = Type.getType(miningBox.desc).getInternalName();
            m = method(node, "readFromNBT", "func_145839_a", "(Lnet/minecraft/nbt/NBTTagCompound;)V");
            int boxesRead = -1;
            for (AbstractInsnNode n : expect("readFromNBT.boxes", m, 1, own(Opcodes.GETFIELD, "frameBox"), aload(1),
                    ldc("frame"), op(Opcodes.INVOKEVIRTUAL), op(Opcodes.INVOKEVIRTUAL))) {
                boxesRead = m.instructions.indexOf(n);
            }
            if (boxesRead < 0) {
                return;
            }
            for (AbstractInsnNode n : expect("readFromNBT.iterator", m, 1, ldc("boxIterator"), op(Opcodes.INVOKEVIRTUAL),
                    op(Opcodes.INVOKESTATIC), own(Opcodes.PUTFIELD, "boxIterator"))) {
                // Both boxes are read by then (the mining box first, then the frame box).
                if (!site("readFromNBT.order", boxesRead < m.instructions.indexOf(n))) {
                    return;
                }
                int low = m.maxLocals;
                m.maxLocals += 1;
                m.instructions.insert(at(n, 3), list(
                        new VarInsnNode(Opcodes.ALOAD, 0),
                        new VarInsnNode(Opcodes.ALOAD, 0),
                        new FieldInsnNode(Opcodes.GETFIELD, node.name, frameBox.name, frameBox.desc),
                        new MethodInsnNode(Opcodes.INVOKEVIRTUAL, box, "max", "()" + POS, false),
                        new VarInsnNode(Opcodes.ALOAD, 0),
                        new FieldInsnNode(Opcodes.GETFIELD, node.name, miningBox.name, miningBox.desc),
                        new MethodInsnNode(Opcodes.INVOKEVIRTUAL, box, "min", "()" + POS, false),
                        new FieldInsnNode(Opcodes.GETSTATIC, depth.owner, depth.name, depth.desc),
                        bc("savedQuarryFloor", "(" + TE + POS + POS + "I)" + POS),
                        new VarInsnNode(Opcodes.ASTORE, low),
                        new VarInsnNode(Opcodes.ALOAD, 0),
                        new FieldInsnNode(Opcodes.GETFIELD, node.name, miningBox.name, miningBox.desc),
                        new VarInsnNode(Opcodes.ALOAD, low),
                        new MethodInsnNode(Opcodes.INVOKEVIRTUAL, box, "setMin", "(" + POS + ")V", false),
                        new VarInsnNode(Opcodes.ALOAD, 0),
                        new VarInsnNode(Opcodes.ALOAD, 0),
                        new FieldInsnNode(Opcodes.GETFIELD, node.name, iterator.name, iterator.desc),
                        new VarInsnNode(Opcodes.ALOAD, low),
                        bc("quarryIterator", "(Ljava/lang/Object;" + POS + ")Ljava/lang/Object;"),
                        new TypeInsnNode(Opcodes.CHECKCAST, Type.getType(iterator.desc).getInternalName()),
                        new FieldInsnNode(Opcodes.PUTFIELD, node.name, iterator.name, iterator.desc)));
            }
        }
    }

    /**
     * {@code buildcraft.core.gen.SpringPopulate}: water springs look for bedrock in layers 0-4 of the world (B3).
     * {@code for (i = 0; i < 5; i++) { pos = new BlockPos(x, i, z); ... y = i > 0 ? i : i - 1; ... }}: both the
     * searched y and the spring's y are offset by the world's bottom.
     */
    static final class SpringPopulatePatch extends ModPatch {
        SpringPopulatePatch() {
            super("buildcraft.core.gen.SpringPopulate", "BuildCraftSpringPopulate");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "doPopulate", "doPopulate", "(Lnet/minecraft/world/World;Ljava/util/Random;II)V");
            int layer = -1;
            for (AbstractInsnNode n : expect("doPopulate.loop", m, 1, iload(-1), icst(5), op(Opcodes.IF_ICMPGE))) {
                layer = ((VarInsnNode) n).var;
            }
            if (layer < 0) {
                return;
            }
            for (AbstractInsnNode n : expect("doPopulate.search", m, 1, iload(-1), iload(layer), iload(-1),
                    invoke(Opcodes.INVOKESPECIAL, "<init>", "<init>", "(III)V"), op(Opcodes.ASTORE), aload(0), aload(-1),
                    invoke(Opcodes.INVOKEVIRTUAL, "getBlockState", "func_180495_p", null))) {
                m.instructions.insert(at(n, 1), list(new VarInsnNode(Opcodes.ALOAD, 0),
                        bc("springY", "(I" + WORLD_DESC + ")I")));
            }
            // y = i > 0 ? i : i - 1: both branches meet at the store.
            for (AbstractInsnNode n : expect("doPopulate.spring", m, 1, iload(layer), icst(1), op(Opcodes.ISUB),
                    istore(-1))) {
                m.instructions.insertBefore(at(n, 3), list(new VarInsnNode(Opcodes.ALOAD, 0),
                        bc("springY", "(I" + WORLD_DESC + ")I")));
            }
        }
    }

    /**
     * {@code mod.akrivus.kagic.blocks.BlockInjector}: the y-5 floor in its three methods becomes the world's bottom + 5
     * (K1), and the "a seed is already there" 256 becomes 1024 (K2), which a taller world needs to land below the floor.
     */
    static final class InjectorPatch extends ModPatch {
        InjectorPatch() {
            super("mod.akrivus.kagic.blocks.BlockInjector", "KagicInjector");
        }

        private static org.objectweb.asm.tree.InsnList floor() {
            return list(new VarInsnNode(Opcodes.ALOAD, 1), kagic("injectorFloor", "(" + WORLD_DESC + ")I"));
        }

        @Override
        protected void apply(ClassNode node) {
            String desc = "(" + WORLD_DESC + POS + ")I";
            // int y = -1; int floor = 5;
            MethodNode m = method(node, "calcDistanceAutomatically", "calcDistanceAutomatically", desc);
            for (AbstractInsnNode n : expect("calcDistanceAutomatically.floor", m, 1, icst(-1), istore(-1), icst(5),
                    istore(-1))) {
                replace(m, at(n, 2), floor());
            }

            m = method(node, "calcDistanceUsingRedstoneSignals", "calcDistanceUsingRedstoneSignals", desc);
            // if (seed.getY() < 5 || bedrock) distance -= 5 - seed.getY();
            AbstractInsnNode check = null;
            for (AbstractInsnNode n : expect("calcDistanceUsingRedstoneSignals.floorCheck", m, 1, getY(), icst(5),
                    op(Opcodes.IF_ICMPLT))) {
                check = at(n, 1);
            }
            AbstractInsnNode clamp = null;
            for (AbstractInsnNode n : expect("calcDistanceUsingRedstoneSignals.floorClamp", m, 1, iload(-1), icst(5),
                    aload(-1), getY(), op(Opcodes.ISUB), op(Opcodes.ISUB))) {
                clamp = at(n, 1);
            }
            // if (seed already there) distance += 256;
            IincInsnNode taken = null;
            for (AbstractInsnNode n : expect("calcDistanceUsingRedstoneSignals.seedTaken", m, 1, iinc(256))) {
                taken = (IincInsnNode) n;
            }
            if (check != null && clamp != null && taken != null) {
                replace(m, check, floor());
                replace(m, clamp, floor());
                replace(m, taken, list(new VarInsnNode(Opcodes.ILOAD, taken.var), new VarInsnNode(Opcodes.ALOAD, 1),
                        kagic("seedTaken", "(" + WORLD_DESC + ")I"), insn(Opcodes.IADD),
                        new VarInsnNode(Opcodes.ISTORE, taken.var)));
            }

            // if (y > 5) place the seed, else fail.
            m = method(node, "injectGemSeed", "injectGemSeed", "(" + WORLD_DESC + POS + ")V");
            for (AbstractInsnNode n : expect("injectGemSeed.floor", m, 1, iload(-1), icst(5), op(Opcodes.IF_ICMPLE))) {
                replace(m, at(n, 1), floor());
            }
        }
    }

    /**
     * {@code mod.akrivus.kagic.util.injector.InjectorResult.create}: the depth bonus is {@code min(1, depth / y)}; y
     * counts as at least 6 (KAGIC's lowest seed in a 0-255 world), so it never divides by 0 or a negative y (K3).
     */
    static final class InjectorResultPatch extends ModPatch {
        InjectorResultPatch() {
            super("mod.akrivus.kagic.util.injector.InjectorResult", "KagicInjectorResult");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "create", "create",
                    "(" + WORLD_DESC + POS + "Z)Lmod/akrivus/kagic/util/injector/InjectorResult;");
            for (AbstractInsnNode n : expect("create.depthRatio", m, 1, op(Opcodes.DLOAD), aload(1), getY(),
                    op(Opcodes.I2D), op(Opcodes.DDIV))) {
                m.instructions.insert(at(n, 2), list(new VarInsnNode(Opcodes.ALOAD, 0),
                        kagic("depthY", "(I" + WORLD_DESC + ")I")));
            }
        }
    }

    /** {@code mod.akrivus.kagic.init.ModEvents.onWorldTick}: meteor rubies fall from the world's top, not 256 (K4). */
    static final class MeteorRubyTickPatch extends ModPatch {
        MeteorRubyTickPatch() {
            super("mod.akrivus.kagic.init.ModEvents", "KagicMeteorRubyTick");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "onWorldTick", "onWorldTick",
                    "(Lnet/minecraftforge/fml/common/gameevent/TickEvent$WorldTickEvent;)V");
            // ruby.setPosition(x, 256.0, z)
            for (AbstractInsnNode n : expect("onWorldTick.rubyY", m, 1, aload(-1), op(Opcodes.DLOAD), dcst(256.0),
                    op(Opcodes.DLOAD), invoke(Opcodes.INVOKEVIRTUAL, "setPosition", "func_70107_b", "(DDD)V"))) {
                replace(m, at(n, 2), list(new VarInsnNode(Opcodes.ALOAD, ((VarInsnNode) n).var),
                        kagic("meteorY", "(Lnet/minecraft/entity/Entity;)D")));
            }
        }
    }

    /** {@code mod.akrivus.kagic.command.CommandMeteorRuby}: {@code /meteorruby} drops it from the world's top (K4). */
    static final class MeteorRubyCommandPatch extends ModPatch {
        MeteorRubyCommandPatch() {
            super("mod.akrivus.kagic.command.CommandMeteorRuby", "KagicMeteorRubyCommand");
        }

        @Override
        protected void apply(ClassNode node) {
            MethodNode m = method(node, "execute", "func_184881_a",
                    "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V");
            String sender = "Lnet/minecraft/command/ICommandSender;";
            P getX = invoke(Opcodes.INVOKEVIRTUAL, "getX", "func_177958_n", "()I");
            // ruby.setPosition(sender.getPosition().getX(), 256.0, ...)
            AbstractInsnNode rubyY = null;
            for (AbstractInsnNode n : expect("execute.rubyY", m, 1, op(Opcodes.I2D), dcst(256.0), aload(2))) {
                rubyY = at(n, 1);
            }
            // the sound at new BlockPos(x, 256, z), and the chat message's y
            AbstractInsnNode soundY = null;
            for (AbstractInsnNode n : expect("execute.soundY", m, 1, getX, icst(256), aload(2))) {
                soundY = at(n, 1);
            }
            AbstractInsnNode messageY = null;
            for (AbstractInsnNode n : expect("execute.messageY", m, 1, op(Opcodes.DUP), icst(1), icst(256),
                    invoke(Opcodes.INVOKESTATIC, "valueOf", "valueOf", "(I)Ljava/lang/Integer;"))) {
                messageY = at(n, 2);
            }
            if (rubyY == null || soundY == null || messageY == null) {
                return;
            }
            replace(m, rubyY, list(new VarInsnNode(Opcodes.ALOAD, 2), kagic("meteorY", "(" + sender + ")D")));
            replace(m, soundY, list(new VarInsnNode(Opcodes.ALOAD, 2), kagic("meteorTop", "(" + sender + ")I")));
            replace(m, messageY, list(new VarInsnNode(Opcodes.ALOAD, 2), kagic("meteorTop", "(" + sender + ")I")));
        }
    }
}
