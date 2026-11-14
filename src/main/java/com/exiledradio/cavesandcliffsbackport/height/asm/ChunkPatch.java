package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * {@code net.minecraft.world.chunk.Chunk}: 24 sections in an extended world; every {@code storageArrays[y >> 4]}
 * becomes {@code storageArrays[HeightLayout.slot(y)]}; top-down loops stop at the world's bottom instead of 0; the
 * height map keeps full ints; the relight round robin covers every section.
 */
final class ChunkPatch extends ClassPatch {

    private static final String CHUNK = "net/minecraft/world/chunk/Chunk";
    private static final String EBS = "net/minecraft/world/chunk/storage/ExtendedBlockStorage";
    private static final String EBS_ARRAY = "[L" + EBS + ";";
    private static final String STORAGE_MCP = "storageArrays";
    private static final String STORAGE_SRG = "field_76652_q";
    private static final String QUEUED_MCP = "queuedLightChecks";
    private static final String QUEUED_SRG = "field_76649_t";

    private final boolean client;

    ChunkPatch(boolean client) {
        super("net.minecraft.world.chunk.Chunk");
        this.client = client;
    }

    @Override
    protected void apply(ClassNode node) {
        MethodNode m = method(node, "<init>", "<init>", "(Lnet/minecraft/world/World;II)V");
        for (AbstractInsnNode n : expect("<init>.sections", m, 1, icst(16),
                x -> x.getOpcode() == Opcodes.ANEWARRAY && EBS.equals(((TypeInsnNode) x).desc))) {
            replace(m, n, list(new VarInsnNode(Opcodes.ALOAD, 1), heights("sections", "(" + WORLD_DESC + ")I")));
        }
        for (AbstractInsnNode n : expect("<init>.relightChecks", m, 1, icst(4096),
                field(Opcodes.PUTFIELD, QUEUED_MCP, QUEUED_SRG))) {
            replace(m, n, chunkInt("relightChecks"));
        }

        // Entity lists: one per section, from the bottom; an entity's chunkCoordY stays its section y.
        for (AbstractInsnNode n : expect("<init>.entityLists", m, 1, icst(16),
                x -> x.getOpcode() == Opcodes.ANEWARRAY && "net/minecraft/util/ClassInheritanceMultiMap".equals(((TypeInsnNode) x).desc))) {
            replace(m, n, list(new VarInsnNode(Opcodes.ALOAD, 1), heights("sections", "(" + WORLD_DESC + ")I")));
        }
        m = method(node, "addEntity", "func_76612_a", "(Lnet/minecraft/entity/Entity;)V");
        for (AbstractInsnNode n : expect("addEntity.list", m, 1, field(Opcodes.GETFIELD, "posY", "field_70163_u"), dcst(16.0D),
                op(Opcodes.DDIV), floor(), istore(-1))) {
            m.instructions.insert(at(n, 3), minus(minSection()));
        }
        for (AbstractInsnNode n : expect("addEntity.chunkCoordY", m, 1, aload(1), iload(-1),
                field(Opcodes.PUTFIELD, "chunkCoordY", "field_70162_ai"))) {
            InsnList plus = minSection();
            plus.add(insn(Opcodes.IADD));
            m.instructions.insert(next(n), plus);
        }
        m = method(node, "removeEntityAtIndex", "func_76608_a", "(Lnet/minecraft/entity/Entity;I)V");
        if (site("removeEntityAtIndex.head", m != null)) {
            InsnList head = list(new VarInsnNode(Opcodes.ILOAD, 2));
            head.add(minus(minSection()));
            head.add(new VarInsnNode(Opcodes.ISTORE, 2));
            m.instructions.insert(head);
        }
        String aabb = "Lnet/minecraft/util/math/AxisAlignedBB;Ljava/util/List;Lcom/google/common/base/Predicate;)V";
        for (String[] q : new String[][] {{"getEntitiesWithinAABBForEntity", "func_177414_a", "(Lnet/minecraft/entity/Entity;" + aabb},
                {"getEntitiesOfTypeWithinAABB", "func_177430_a", "(Ljava/lang/Class;" + aabb}}) {
            m = method(node, q[0], q[1], q[2]);
            for (AbstractInsnNode n : expect(q[0] + ".lists", m, 2, floor())) {
                m.instructions.insert(n, minus(minSection()));
            }
        }

        // The highest section by height, not by index: slots 20-23 (below y 0) sit after the top ones.
        m = method(node, "getLastExtendedBlockStorage", "func_186031_y", "()L" + EBS + ";");
        for (AbstractInsnNode n : expect("getLastExtendedBlockStorage", m, 2, storage(), iload(1), op(Opcodes.AALOAD))) {
            m.instructions.insertBefore(at(n, 2), list(new VarInsnNode(Opcodes.ALOAD, 0), storageField(),
                    insn(Opcodes.ARRAYLENGTH), layout("byHeight", "(II)I")));
        }

        if (this.client) {
            m = method(node, "generateHeightMap", "func_76590_a", "()V");
            for (AbstractInsnNode n : expect("generateHeightMap.bottom", m, 1, iload(4), op(Opcodes.IFLE))) {
                compareWith(m, next(n), minY());
            }
        }

        m = method(node, "generateSkylightMap", "func_76603_b", "()V");
        List<AbstractInsnNode> heightLoops = expect("generateSkylightMap.bottom", m, 2, iload(4), op(Opcodes.IFLE));
        if (!heightLoops.isEmpty()) {
            // The first is the height-map loop (l > 0); the second tests the light level, not a height.
            compareWith(m, next(heightLoops.get(0)), minY());
        }
        for (AbstractInsnNode n : expect("generateSkylightMap.lightBottom", m, 1, iload(5), op(Opcodes.IFLE))) {
            compareWith(m, next(n), minY());
        }
        slots(m, "generateSkylightMap", 1);

        m = method(node, "relightBlock", "func_76615_h", "(III)V");
        for (AbstractInsnNode n : expect("relightBlock.mask", m, 1, icst(255), op(Opcodes.IAND))) {
            m.instructions.remove(next(n));
            replace(m, n, list(new VarInsnNode(Opcodes.ALOAD, 0), worldField(), heights("maskHeight", "(I" + WORLD_DESC + ")I")));
        }
        for (AbstractInsnNode n : expect("relightBlock.bottom", m, 2, iload(5), op(Opcodes.IFLE))) {
            compareWith(m, next(n), minY());
        }
        slots(m, "relightBlock", 3);

        m = method(node, "getBlockState", "func_186032_a", "(III)Lnet/minecraft/block/state/IBlockState;");
        for (AbstractInsnNode n : expect("getBlockState.notBelow", m, 1, iload(2), op(Opcodes.IFLT))) {
            m.instructions.insert(n, list(layout("notBelow", "(I)I")));
        }
        for (AbstractInsnNode n : expect("getBlockState.guard", m, 1, iload(2), icst(4), op(Opcodes.ISHR),
                aload(0), storage(), op(Opcodes.ARRAYLENGTH))) {
            shiftToSlot(m, next(n));
        }
        slots(m, "getBlockState", 1);

        slots(method(node, "setBlockState", "func_177436_a", "(Lnet/minecraft/util/math/BlockPos;"
                + "Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/block/state/IBlockState;"), "setBlockState", 2);
        slots(method(node, "getLightFor", "func_177413_a", "(Lnet/minecraft/world/EnumSkyBlock;"
                + "Lnet/minecraft/util/math/BlockPos;)I"), "getLightFor", 1);
        slots(method(node, "setLightFor", "func_177431_a", "(Lnet/minecraft/world/EnumSkyBlock;"
                + "Lnet/minecraft/util/math/BlockPos;I)V"), "setLightFor", 2);
        slots(method(node, "getLightSubtracted", "func_177443_a", "(Lnet/minecraft/util/math/BlockPos;I)I"),
                "getLightSubtracted", 1);

        m = method(node, "isEmptyBetween", "func_76606_c", "(II)Z");
        for (AbstractInsnNode n : expect("isEmptyBetween.min", m, 1, iload(1), op(Opcodes.IFGE), icst(0), istore(1))) {
            AbstractInsnNode zero = at(n, 2);
            compareWith(m, next(n), minY());
            replace(m, zero, minY());
        }
        for (AbstractInsnNode n : expect("isEmptyBetween.max", m, 1, iload(2), icst(256), op(Opcodes.IF_ICMPLT),
                icst(255), istore(2))) {
            AbstractInsnNode top = at(n, 3);
            replace(m, next(n), worldInt(self(worldField()), "maxY"));
            replace(m, top, worldInt(self(worldField()), "topY"));
        }
        slots(m, "isEmptyBetween", 1);

        if (this.client) {
            m = method(node, "read", "func_186033_a", "(Lnet/minecraft/network/PacketBuffer;IZ)V");
            for (AbstractInsnNode n : expect("read.sectionBase", m, 1, iload(-1), icst(4), op(Opcodes.ISHL), iload(-1),
                    invoke(Opcodes.INVOKESPECIAL, "<init>", "<init>", "(IZ)V"))) {
                m.instructions.remove(at(n, 2));
                replace(m, next(n), list(layout("slotBase", "(I)I")));
            }
        }

        m = method(node, "enqueueRelightChecks", "func_76594_o", "()V");
        for (AbstractInsnNode n : expect("enqueueRelightChecks.total", m, 2, queued(), icst(4096))) {
            replace(m, next(n), chunkInt("relightChecks"));
        }
        for (AbstractInsnNode n : expect("enqueueRelightChecks.section", m, 1, queued(), icst(16), op(Opcodes.IREM))) {
            replace(m, next(n), chunkInt("chunkSections"));
        }
        for (AbstractInsnNode n : expect("enqueueRelightChecks.column", m, 1, queued(), icst(16), op(Opcodes.IDIV))) {
            replace(m, next(n), chunkInt("chunkSections"));
        }
        for (AbstractInsnNode n : expect("enqueueRelightChecks.row", m, 1, queued(), icst(256), op(Opcodes.IDIV))) {
            replace(m, next(n), chunkInt("chunkSectionsTimes16"));
        }
        for (AbstractInsnNode n : expect("enqueueRelightChecks.base", m, 1, iload(3), icst(4), op(Opcodes.ISHL))) {
            m.instructions.remove(at(n, 2));
            replace(m, next(n), list(layout("slotBase", "(I)I")));
        }

        m = method(node, "checkLight", "func_150811_f", "(II)Z");
        for (AbstractInsnNode n : expect("checkLight.bottom", m, 2, iload(7), op(Opcodes.IFLE))) {
            compareWith(m, next(n), minY());
        }

        m = method(node, "getPrecipitationHeight", "func_177440_h",
                "(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/BlockPos;");
        for (AbstractInsnNode n : expect("getPrecipitationHeight.bottom", m, 1, getY(), op(Opcodes.IFLE))) {
            compareWith(m, next(n), minY());
        }
        // Its "not found yet" marker is -1, which is also the answer for a column topped at y -2: the walk then
        // never ends (a found block does not step down), hanging the server on the first rain or snow tick over
        // such a column. The marker becomes one below the world's bottom, which no block's y + 1 can be.
        List<AbstractInsnNode> marker = expect("getPrecipitationHeight.notFound", m, 1, icst(-1), istore(-1));
        for (AbstractInsnNode n : marker) {
            int var = ((VarInsnNode) next(n)).var;
            for (AbstractInsnNode test : expect("getPrecipitationHeight.notFoundTest", m, 1, iload(var), icst(-1),
                    op(Opcodes.IF_ICMPNE))) {
                replace(m, next(test), worldInt(self(worldField()), "belowBottom"));
            }
            replace(m, n, worldInt(self(worldField()), "belowBottom"));
        }
    }

    private static P floor() {
        return invoke(Opcodes.INVOKESTATIC, "floor", "func_76128_c", "(D)I");
    }

    private static InsnList minSection() {
        return worldInt(self(worldField()), "minSection");
    }

    /** The list, then {@code isub}. */
    private static InsnList minus(InsnList value) {
        value.add(insn(Opcodes.ISUB));
        return value;
    }

    /** Every {@code storageArrays[v >> 4]} of the method (exactly {@code count}) indexes by slot instead. */
    private void slots(MethodNode m, String id, int count) {
        for (AbstractInsnNode n : expect(id + ".slot", m, count, storage(), iload(-1), icst(4), op(Opcodes.ISHR))) {
            shiftToSlot(m, at(n, 2));
        }
    }

    private static P storage() {
        return field(Opcodes.GETFIELD, STORAGE_MCP, STORAGE_SRG);
    }

    private static P queued() {
        return field(Opcodes.GETFIELD, QUEUED_MCP, QUEUED_SRG);
    }

    private static FieldInsnNode storageField() {
        return new FieldInsnNode(Opcodes.GETFIELD, CHUNK, n(STORAGE_MCP, STORAGE_SRG), EBS_ARRAY);
    }

    private static FieldInsnNode worldField() {
        return new FieldInsnNode(Opcodes.GETFIELD, CHUNK, n("world", "field_76637_e"), WORLD_DESC);
    }

    private static InsnList self(AbstractInsnNode then) {
        return list(new VarInsnNode(Opcodes.ALOAD, 0), then);
    }

    private static InsnList minY() {
        return worldInt(self(worldField()), "minY");
    }

    /** {@code Heights.<name>(this)}, a {@code (Chunk)I} method. */
    private static InsnList chunkInt(String name) {
        return list(new VarInsnNode(Opcodes.ALOAD, 0), heights(name, "(L" + CHUNK + ";)I"));
    }
}
