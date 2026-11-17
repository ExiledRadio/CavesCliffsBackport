package com.exiledradio.cavesandcliffsbackport.height.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * The "Other Changes" page's patches: 1.17 and 1.18 changes inside vanilla methods that no Forge event reaches. Unlike
 * {@link HeightTransformer} these apply whether or not extended height is on. Each puts a static call to {@code
 * changes.ChangesHooks} (or {@code changes.client.ChangesClientHooks}) between a vanilla value and its use, or before
 * a call, with no jump and no new local, so no frame changes; the mycelium patch adds one method that calls the
 * superclass and passes its answer through a hook. With a setting off, each hook gives back 1.12.2's value.
 *
 * <ul>
 *   <li>{@code EntityMinecart} (minecarts in water): the gravity in {@code onUpdate}, {@code getMaximumSpeed}'s and
 *       Forge's {@code getMaxSpeed}'s results, Forge's {@code getSlopeAdjustment}, and the end of {@code applyDrag}.</li>
 *   <li>{@code EntityItem.attackEntityFrom}: before {@code setDead}, a destroyed shulker box spills its contents.</li>
 *   <li>{@code BlockMycelium}: a {@code canSustainPlant} override (super's answer, then the hook).</li>
 *   <li>{@code NetHandlerPlayServer.processCustomPayload}: the anvil name limit 35 of {@code MC|ItemName}.</li>
 *   <li>Client: {@code GuiIngameForge.renderHealth}'s absorption heart texture offsets ({@code MARGIN + 144} and
 *       {@code + 153}), and {@code EntityPlayerSP.onLivingUpdate}'s {@code collidedHorizontally} in the sprint test.</li>
 * </ul>
 * A target not of the expected shape is left exactly as it was (logged), so 1.12.2's behavior stays.
 */
public final class ChangesTransformer implements IClassTransformer {

    static final Logger LOG = LogManager.getLogger("Caves & Cliffs Backport Core");

    static final String HOOKS = "com/exiledradio/cavesandcliffsbackport/changes/ChangesHooks";
    static final String CLIENT_HOOKS = "com/exiledradio/cavesandcliffsbackport/changes/client/ChangesClientHooks";

    static final String CART = "net.minecraft.entity.item.EntityMinecart";
    static final String ITEM = "net.minecraft.entity.item.EntityItem";
    static final String MYCELIUM = "net.minecraft.block.BlockMycelium";
    static final String NET = "net.minecraft.network.NetHandlerPlayServer";
    static final String GUI = "net.minecraftforge.client.GuiIngameForge";
    static final String PLAYER_SP = "net.minecraft.client.entity.EntityPlayerSP";

    /** Every target with its side: true for client-only classes. */
    public static final String[] COMMON = {CART, ITEM, MYCELIUM, NET};
    public static final String[] CLIENT = {GUI, PLAYER_SP};

    /** Each target class once patched in the running game; the self-test reads it. */
    public static final Map<String, Boolean> APPLIED = new ConcurrentHashMap<String, Boolean>();

    private static final String CART_DESC = "Lnet/minecraft/entity/item/EntityMinecart;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        byte[] out;
        try {
            out = patch(transformedName, basicClass);
        } catch (RuntimeException e) {
            LOG.error("Other Changes: patching {} threw; the class is left as it was.", transformedName, e);
            return basicClass;
        }
        if (out == null) {
            return basicClass;
        }
        APPLIED.put(transformedName, Boolean.TRUE);
        return out;
    }

    /** @return the patched class, or null when the class is not a target or not of the expected shape */
    static byte[] patch(String target, byte[] bytes) {
        if (CART.equals(target)) {
            return cart(bytes);
        }
        if (ITEM.equals(target)) {
            return item(bytes);
        }
        if (MYCELIUM.equals(target)) {
            return mycelium(bytes);
        }
        if (NET.equals(target)) {
            return net(bytes);
        }
        if (GUI.equals(target)) {
            return gui(bytes);
        }
        if (PLAYER_SP.equals(target)) {
            return playerSP(bytes);
        }
        return null;
    }

    /** For the self-test: the class as given, patched (null when it would be left alone), without loading anything. */
    public static byte[] patchForTest(String target, byte[] bytes) {
        return patch(target, bytes);
    }

    // ------------------------------------------------------------------------------------------

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static List<AbstractInsnNode> sites(String what, MethodNode m, int count, P... pattern) {
        List<AbstractInsnNode> found = find(m, pattern);
        if (count >= 0 ? found.size() != count : found.isEmpty()) {
            LOG.warn("Other Changes: {} is not of the expected shape ({}); patch skipped, 1.12.2's behavior kept.", what,
                    m == null ? "method not found" : found.size() + " matches");
            return null;
        }
        return found;
    }

    private static MethodInsnNode hook(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, desc, false);
    }

    private static InsnList self(MethodInsnNode call) {
        return list(new VarInsnNode(Opcodes.ALOAD, 0), call);
    }

    /** Minecarts in water (1.17). */
    private static byte[] cart(byte[] bytes) {
        ClassNode node = read(bytes);
        MethodNode update = method(node, "onUpdate", "func_70071_h_", "()V");
        MethodNode max = method(node, "getMaximumSpeed", "func_174898_m", "()D");
        MethodNode forgeMax = method(node, "getMaxSpeed", "getMaxSpeed", "()D");
        MethodNode slope = method(node, "getSlopeAdjustment", "getSlopeAdjustment", "()D");
        MethodNode drag = method(node, "applyDrag", "func_94101_h", "()V");
        List<AbstractInsnNode> gravity = sites("EntityMinecart.onUpdate gravity", update, 1, dcst(0.03999999910593033D), op(Opcodes.DSUB));
        List<AbstractInsnNode> maxRet = sites("EntityMinecart.getMaximumSpeed", max, 1, op(Opcodes.DRETURN));
        List<AbstractInsnNode> railRet = sites("EntityMinecart.getMaxSpeed", forgeMax, 1, op(Opcodes.F2D), op(Opcodes.DRETURN));
        List<AbstractInsnNode> slopeRet = sites("EntityMinecart.getSlopeAdjustment", slope, 1, op(Opcodes.DRETURN));
        List<AbstractInsnNode> dragRet = sites("EntityMinecart.applyDrag", drag, -1, op(Opcodes.RETURN));
        if (gravity == null || maxRet == null || railRet == null || slopeRet == null || dragRet == null) {
            return null;
        }
        replace(update, gravity.get(0), self(hook("cartGravity", "(" + CART_DESC + ")D")));
        max.instructions.insertBefore(maxRet.get(0), self(hook("cartSpeed", "(D" + CART_DESC + ")D")));
        forgeMax.instructions.insertBefore(next(railRet.get(0)), self(hook("cartSpeed", "(D" + CART_DESC + ")D")));
        slope.instructions.insertBefore(slopeRet.get(0), self(hook("cartSlope", "(D" + CART_DESC + ")D")));
        for (AbstractInsnNode r : dragRet) {
            drag.instructions.insertBefore(r, self(hook("cartDrag", "(" + CART_DESC + ")V")));
        }
        return write(node);
    }

    /** Shulker box items spill their contents when destroyed (1.17). */
    private static byte[] item(byte[] bytes) {
        ClassNode node = read(bytes);
        MethodNode m = method(node, "attackEntityFrom", "func_70097_a", "(Lnet/minecraft/util/DamageSource;F)Z");
        List<AbstractInsnNode> dead = sites("EntityItem.attackEntityFrom", m, 1,
                aload(0), invoke(Opcodes.INVOKEVIRTUAL, "setDead", "func_70106_y", "()V"));
        if (dead == null) {
            return null;
        }
        m.instructions.insertBefore(dead.get(0), self(hook("itemDestroyed", "(Lnet/minecraft/entity/item/EntityItem;)V")));
        return write(node);
    }

    /** Plants on mycelium (1.17): {@code canSustainPlant} is super's answer through the hook. */
    private static byte[] mycelium(byte[] bytes) {
        ClassNode node = read(bytes);
        String desc = "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;"
                + "Lnet/minecraft/util/EnumFacing;Lnet/minecraftforge/common/IPlantable;)Z";
        if (method(node, "canSustainPlant", "canSustainPlant", desc) != null) {
            LOG.warn("Other Changes: BlockMycelium already has canSustainPlant; patch skipped, 1.12.2's behavior kept.");
            return null;
        }
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, "canSustainPlant", desc, null, null);
        for (int i = 0; i <= 5; i++) {
            m.instructions.add(new VarInsnNode(Opcodes.ALOAD, i));
        }
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, "canSustainPlant", desc, false));
        for (int i = 1; i <= 5; i++) {
            m.instructions.add(new VarInsnNode(Opcodes.ALOAD, i));
        }
        m.instructions.add(hook("myceliumSustains", "(Z" + desc.substring(1)));
        m.instructions.add(insn(Opcodes.IRETURN));
        m.maxStack = 6;
        m.maxLocals = 6;
        node.methods.add(m);
        return write(node);
    }

    /** Anvil names of 50 characters (1.17): the server's limit. */
    private static byte[] net(byte[] bytes) {
        ClassNode node = read(bytes);
        MethodNode m = method(node, "processCustomPayload", "func_147349_a", "(Lnet/minecraft/network/play/client/CPacketCustomPayload;)V");
        List<AbstractInsnNode> limit = sites("NetHandlerPlayServer.processCustomPayload anvil name", m, 1,
                invoke(Opcodes.INVOKEVIRTUAL, "length", "length", "()I"), icst(35), op(Opcodes.IF_ICMPGT));
        if (limit == null) {
            return null;
        }
        replace(m, next(limit.get(0)), list(hook("anvilNameLimit", "()I")));
        return write(node);
    }

    /** Absorption hearts under Poison and Wither (1.17). */
    private static byte[] gui(byte[] bytes) {
        ClassNode node = read(bytes);
        MethodNode m = method(node, "renderHealth", "renderHealth", "(II)V");
        List<AbstractInsnNode> full = sites("GuiIngameForge.renderHealth absorption", m, 1, icst(144), op(Opcodes.IADD));
        List<AbstractInsnNode> half = sites("GuiIngameForge.renderHealth absorption half", m, 1, icst(153), op(Opcodes.IADD));
        if (full == null || half == null) {
            return null;
        }
        for (AbstractInsnNode n : new AbstractInsnNode[] {full.get(0), half.get(0)}) {
            m.instructions.insert(next(n), new MethodInsnNode(Opcodes.INVOKESTATIC, CLIENT_HOOKS, "absorptionU", "(I)I", false));
        }
        return write(node);
    }

    /** Sprinting past walls (1.18). */
    private static byte[] playerSP(byte[] bytes) {
        ClassNode node = read(bytes);
        MethodNode m = method(node, "onLivingUpdate", "func_70636_d", "()V");
        List<AbstractInsnNode> hit = sites("EntityPlayerSP.onLivingUpdate sprint", m, 1,
                aload(0), field(Opcodes.GETFIELD, "collidedHorizontally", "field_70123_F"));
        if (hit == null) {
            return null;
        }
        replace(m, next(hit.get(0)), list(new MethodInsnNode(Opcodes.INVOKESTATIC, CLIENT_HOOKS, "sprintBlocked",
                "(Lnet/minecraft/client/entity/EntityPlayerSP;)Z", false)));
        return write(node);
    }

    /** The names, for the self-test's report. */
    public static List<String> targets(boolean client) {
        List<String> out = new ArrayList<String>();
        Collections.addAll(out, COMMON);
        if (client) {
            Collections.addAll(out, CLIENT);
        }
        return out;
    }
}
