package com.exiledradio.cavesandcliffsbackport.lush;

import net.minecraft.block.IGrowable;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * Azalea and flowering azalea: a bush on dirt, farmland or clay, solid enough to stand on (a top half on a
 * thin stem). Bone meal grows an azalea tree 45% of the time.
 */
public class BlockAzalea extends BlockLushPlant implements IGrowable {

    private static final AxisAlignedBB TOP = new AxisAlignedBB(0, 0.5, 0, 1, 1, 1);
    private static final AxisAlignedBB STEM = new AxisAlignedBB(0.375, 0, 0.375, 0.625, 0.5, 0.625);

    public BlockAzalea(SoundType sound) {
        super(Material.PLANTS, MapColor.FOLIAGE, sound, 0.0F);
    }

    @Override
    public boolean canStay(World world, BlockPos pos, IBlockState state) {
        return Soil.azalea(world.getBlockState(pos.down()));
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> list, @Nullable Entity entity, boolean isActualState) {
        addCollisionBoxToList(pos, entityBox, list, TOP);
        addCollisionBoxToList(pos, entityBox, list, STEM);
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return TOP;
    }

    @Override
    public boolean canGrow(World world, BlockPos pos, IBlockState state, boolean isClient) {
        return !world.getBlockState(pos.up()).getMaterial().isLiquid();
    }

    @Override
    public boolean canUseBonemeal(World world, Random rand, BlockPos pos, IBlockState state) {
        return rand.nextFloat() < 0.45F;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, IBlockState state) {
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 4);
        if (!AzaleaTree.grow(world, rand, pos, Integer.MIN_VALUE, Integer.MIN_VALUE, 3, false)) {
            world.setBlockState(pos, state, 4);
        }
    }
}
