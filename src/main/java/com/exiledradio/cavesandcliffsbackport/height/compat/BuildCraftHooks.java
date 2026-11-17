package com.exiledradio.cavesandcliffsbackport.height.compat;

import com.exiledradio.cavesandcliffsbackport.height.Heights;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * BuildCraft 8.0.0 / 7.99.24.8 in a world with 1.18's height (plan {@code docs/streams/moddedores.md}, O3; patches in
 * {@code height.asm.ModCompatPatches}). Each method answers exactly what BuildCraft's own code did in a world of 0-255.
 */
public final class BuildCraftHooks {

    private BuildCraftHooks() {
    }

    /**
     * {@code TileQuarry.onPlacedBy}: the mining box bottom when {@code top - 1 - miningMaxDepth} is below the world
     * (BuildCraft: 0, "TODO: ask the world"). The world's lowest y.
     */
    public static int quarryFloor(TileEntity quarry) {
        return Heights.minY(quarry.getWorld());
    }

    /**
     * {@code TileQuarry.readFromNBT}: a quarry placed before this patch has a box bottom of 0 only because of that clamp.
     * Returns the box's new lowest corner (the bottom BuildCraft would give it now), or null to leave the box as saved.
     *
     * @param frameMax  the frame box's highest corner (null when the quarry has no box)
     * @param miningMin the mining box's lowest corner (null when the quarry has no box)
     * @param maxDepth  BuildCraft's {@code miningMaxDepth}
     */
    @Nullable
    public static BlockPos savedQuarryFloor(TileEntity quarry, @Nullable BlockPos frameMax, @Nullable BlockPos miningMin,
                                            int maxDepth) {
        World world = quarry.getWorld();
        int minY = Heights.minY(world);
        if (minY >= 0 || frameMax == null || miningMin == null || miningMin.getY() != 0) {
            return null;
        }
        int wanted = frameMax.getY() - 1 - maxDepth;
        if (wanted >= 0) {
            // A bottom of 0 the depth asked for, not the clamp.
            return null;
        }
        return new BlockPos(miningMin.getX(), Math.max(minY, wanted), miningMin.getZ());
    }

    /**
     * {@code TileQuarry.readFromNBT}: the saved box iterator is dropped when the box was lowered (the quarry makes a
     * new one over the whole box and walks past what it already dug); otherwise kept.
     */
    @Nullable
    public static Object quarryIterator(@Nullable Object iterator, @Nullable BlockPos lowered) {
        return lowered != null ? null : iterator;
    }

    /**
     * {@code SpringPopulate.doPopulate}: water springs look for bedrock in the bottom five layers (y 0-4); this is
     * layer {@code i}'s y in the world.
     */
    public static int springY(int layer, World world) {
        return layer + Heights.minY(world);
    }
}
