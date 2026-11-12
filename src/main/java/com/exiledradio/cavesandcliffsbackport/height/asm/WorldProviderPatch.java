package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * {@code WorldProvider.getHeight()} and {@code getActualHeight()} (Forge's): 256 becomes the world's top, so
 * {@code world.getHeight()} answers 320 in an extended world (the Nether's 128 stays).
 */
final class WorldProviderPatch extends ClassPatch {

    WorldProviderPatch() {
        super("net.minecraft.world.WorldProvider");
    }

    @Override
    protected void apply(ClassNode node) {
        for (String name : new String[] {"getHeight", "getActualHeight"}) {
            MethodNode m = method(node, name, name, "()I");
            for (AbstractInsnNode n : expect(name, m, 1, icst(256))) {
                replace(m, n, worldInt(selfField("net/minecraft/world/WorldProvider", n("world", "field_76579_a"), WORLD_DESC), "maxY"));
            }
        }
    }
}
