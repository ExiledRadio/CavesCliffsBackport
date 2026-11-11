package com.exiledradio.cavesandcliffsbackport.lush;

import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The azalea tree, our own implementation of the one in 1.17: an oak trunk 4-6 tall that leans one way from
 * its third log up and then bends over for 1-2 more, rooted dirt under it, and loose clouds of leaves (50
 * tries each, within 2 across and 1 up or down) around the upper trunk and the bend; one leaf in four is
 * flowering.
 */
public final class AzaleaTree {

    private AzaleaTree() {
    }

    /**
     * @param base where the lowest log goes
     * @param minX and minZ: the 32x32 window leaves and logs must stay in (world generation), or
     *             {@code Integer.MIN_VALUE} for none
     * @param anyGround true to grow on whatever is there (world generation, which has checked it), false for
     *                  the soils an azalea can stand on
     */
    public static boolean grow(World world, Random rand, BlockPos base, int minX, int minZ, int flags, boolean anyGround) {
        IBlockState soil = world.getBlockState(base.down());
        if (base.getY() < 1 || base.getY() > 240 || !(anyGround || Soil.azalea(soil))) {
            return false;
        }
        int height = 4 + rand.nextInt(3);
        for (int i = 0; i < 3; i++) {
            if (!free(world.getBlockState(base.up(i)))) {
                return false;
            }
        }
        EnumFacing dir = EnumFacing.Plane.HORIZONTAL.random(rand);
        IBlockState log = Blocks.LOG.getDefaultState();
        List<BlockPos> crowns = new ArrayList<BlockPos>();
        BlockPos p = base;
        world.setBlockState(base.down(), LushContent.rootedDirt.getDefaultState(), flags);
        for (int i = 0; i < height - 1; i++) {
            if (i + 1 >= 3 && rand.nextBoolean()) {
                p = p.offset(dir);
            }
            if (log(world, p, log, minX, minZ, flags) && i >= 2) {
                crowns.add(p);
            }
            p = p.up();
        }
        int bend = 1 + rand.nextInt(2);
        IBlockState side = log.withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.fromFacingAxis(dir.getAxis()));
        for (int i = 0; i <= bend; i++) {
            if (log(world, p, i == 0 ? log : side, minX, minZ, flags)) {
                crowns.add(p);
            }
            p = p.offset(dir);
        }
        crowns.add(p);
        for (BlockPos c : crowns) {
            for (int i = 0; i < 50; i++) {
                BlockPos l = c.add(rand.nextInt(3) - rand.nextInt(3), rand.nextInt(2) - rand.nextInt(2), rand.nextInt(3) - rand.nextInt(3));
                if (inside(l, minX, minZ) && world.isAirBlock(l)) {
                    BlockLeaves leaves = rand.nextInt(4) == 0 ? LushContent.floweringLeaves : LushContent.leaves;
                    world.setBlockState(l, leaves.getDefaultState(), flags);
                }
            }
        }
        return true;
    }

    private static boolean inside(BlockPos p, int minX, int minZ) {
        return minX == Integer.MIN_VALUE || (p.getX() >= minX && p.getX() < minX + 32 && p.getZ() >= minZ && p.getZ() < minZ + 32);
    }

    private static boolean free(IBlockState s) {
        Material m = s.getMaterial();
        return s.getBlock() == Blocks.AIR || m == Material.LEAVES || m == Material.PLANTS || m == Material.VINE;
    }

    private static boolean log(World world, BlockPos p, IBlockState log, int minX, int minZ, int flags) {
        if (!inside(p, minX, minZ) || p.getY() > 254 || !free(world.getBlockState(p))) {
            return false;
        }
        world.setBlockState(p, log, flags);
        return true;
    }
}
