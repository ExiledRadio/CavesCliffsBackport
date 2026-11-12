package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.tree.ClassNode;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * {@code net.minecraft.world.ChunkCache} (the region the client renders from, and pathfinding's view): its block,
 * light and side-solid lookups answer the world's height range instead of 0-255.
 */
final class ChunkCachePatch extends ClassPatch {

    private static final String SKY_POS = "(Lnet/minecraft/world/EnumSkyBlock;Lnet/minecraft/util/math/BlockPos;)I";
    private final boolean client;

    ChunkCachePatch(boolean client) {
        super("net.minecraft.world.ChunkCache");
        this.client = client;
    }

    @Override
    protected void apply(ClassNode node) {
        range(method(node, "getBlockState", "func_180495_p",
                "(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"), "getBlockState", 1, 1, this::world);
        range(method(node, "isSideSolid", "isSideSolid",
                "(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;Z)Z"), "isSideSolid", 1, 1, this::world);
        if (this.client) {
            range(method(node, "getLightForExt", "func_175629_a", SKY_POS), "getLightForExt", 1, 1, this::world);
            range(method(node, "getLightFor", "func_175628_b", SKY_POS), "getLightFor", 1, 1, this::world);
        }
    }

    private org.objectweb.asm.tree.InsnList world() {
        return selfField("net/minecraft/world/ChunkCache", n("world", "field_72815_e"), WORLD_DESC);
    }
}
