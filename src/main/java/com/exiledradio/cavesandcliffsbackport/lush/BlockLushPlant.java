package com.exiledradio.cavesandcliffsbackport.lush;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * What every lush plant shares: no collision, cut-out drawing, broken by pistons, and breaking as soon as
 * {@link #canStay} stops holding (dropping whatever {@code getDrops} gives).
 */
public abstract class BlockLushPlant extends Block {

    protected BlockLushPlant(Material material, MapColor color, SoundType sound, float hardness) {
        super(material, color);
        setSoundType(sound);
        setHardness(hardness);
    }

    /** @return true while the block at {@code pos} (this one, or this one about to be placed) is supported */
    public abstract boolean canStay(World world, BlockPos pos, IBlockState state);

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && canStay(world, pos, getDefaultState());
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!canStay(world, pos, state)) {
            world.destroyBlock(pos, true);
        }
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public net.minecraft.util.BlockRenderLayer getRenderLayer() {
        return net.minecraft.util.BlockRenderLayer.CUTOUT;
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.DESTROY;
    }

    /** @return true if the block above {@code pos} has a solid bottom face to hang from */
    protected static boolean solidAbove(World world, BlockPos pos) {
        BlockPos up = pos.up();
        return world.getBlockState(up).getBlockFaceShape(world, up, EnumFacing.DOWN) == BlockFaceShape.SOLID;
    }
}
