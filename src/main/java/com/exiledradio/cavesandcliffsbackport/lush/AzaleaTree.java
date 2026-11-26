package com.exiledradio.cavesandcliffsbackport.lush;

import net.minecraft.block.BlockLeaves;
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
 * The azalea tree, as 26.3's {@code azalea_tree}: an oak trunk of 4-6 upright logs that steps one way near its
 * top and then 2-3 more logs along that way, rooted dirt under it, and loose clouds of leaves (50 tries each,
 * within 2 across and 1 up or down) around every log from the fourth and the bend; one leaf in four is
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
        // TreeFeature.doPlace: BendingTrunkPlacer(4, 2, 0)'s height (4 + [0, 2] + [0, 0], both draws).
        int height = 4 + rand.nextInt(3) + rand.nextInt(1);
        for (int i = 0; i < 3; i++) {
            if (!free(world.getBlockState(base.up(i)))) {
                return false;
            }
        }
        // BendingTrunkPlacer.placeTrunk (26.3): a direction in Direction.Plane.HORIZONTAL's order, then logHeight + 1
        // upright logs, each moved one along it from the second-to-last on (a coin flip earlier); foliage on every log
        // from the fourth (minHeightForLeaves 3), then 1-2 + 1 more upright logs along the direction, each with foliage.
        EnumFacing dir = HORIZONTAL[rand.nextInt(4)];
        IBlockState log = Blocks.LOG.getDefaultState();
        int logHeight = height - 1;
        List<BlockPos> crowns = new ArrayList<BlockPos>();
        BlockPos p = base;
        world.setBlockState(base.down(), LushContent.rootedDirt.getDefaultState(), flags);
        for (int i = 0; i <= logHeight; i++) {
            if (i + 1 >= logHeight + rand.nextInt(2)) {
                p = p.offset(dir);
            }
            log(world, p, log, minX, minZ, flags);
            if (i >= 3) {
                crowns.add(p);
            }
            p = p.up();
        }
        int bend = 1 + rand.nextInt(2);
        for (int i = 0; i <= bend; i++) {
            log(world, p, log, minX, minZ, flags);
            crowns.add(p);
            p = p.offset(dir);
        }
        // RandomSpreadFoliagePlacer(radius 3, offset 0, height 2, 50 tries): each try places a leaf (azalea 3 :
        // flowering 1, drawn per placed leaf) wherever a tree may go, leaves included.
        for (BlockPos c : crowns) {
            for (int i = 0; i < 50; i++) {
                BlockPos l = c.add(rand.nextInt(3) - rand.nextInt(3), rand.nextInt(2) - rand.nextInt(2), rand.nextInt(3) - rand.nextInt(3));
                IBlockState here = world.getBlockState(l);
                if (inside(l, minX, minZ) && (world.isAirBlock(l) || here.getMaterial() == Material.LEAVES)) {
                    BlockLeaves leaves = rand.nextInt(4) == 3 ? LushContent.floweringLeaves : LushContent.leaves;
                    world.setBlockState(l, leaves.getDefaultState(), flags);
                }
            }
        }
        return true;
    }

    /** {@code Direction.Plane.HORIZONTAL}'s order. */
    private static final EnumFacing[] HORIZONTAL = {EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST};

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
