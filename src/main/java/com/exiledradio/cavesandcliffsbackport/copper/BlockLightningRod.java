package com.exiledradio.cavesandcliffsbackport.copper;

import com.exiledradio.backportcore.waterlog.Waterloggable;
import com.exiledradio.cavesandcliffsbackport.common.BlockBasic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * The lightning rod: a thin copper rod pointing any of six ways. Lightning that would strike within 128
 * blocks goes to the nearest rod with open sky instead ({@link Lightning}); a struck rod gives a full
 * redstone signal for 8 ticks (strong into the block it stands on, weak to every side), and the copper
 * block it stands on is cleaned of oxidation.
 *
 * <p>Its tile entity holds nothing; it only lets the strike code find rods among the loaded tile entities
 * without scanning chunks (1.12 has no points of interest).
 */
public class BlockLightningRod extends Block implements ITileEntityProvider, Waterloggable {

    public static final PropertyBool POWERED = PropertyBool.create("powered");

    private static final AxisAlignedBB Y = new AxisAlignedBB(6 / 16.0, 0, 6 / 16.0, 10 / 16.0, 1, 10 / 16.0);
    private static final AxisAlignedBB X = new AxisAlignedBB(0, 6 / 16.0, 6 / 16.0, 1, 10 / 16.0, 10 / 16.0);
    private static final AxisAlignedBB Z = new AxisAlignedBB(6 / 16.0, 6 / 16.0, 0, 10 / 16.0, 10 / 16.0, 1);

    /** How long a struck rod stays powered. */
    public static final int POWER_TICKS = 8;

    public BlockLightningRod(SoundType sound) {
        super(Material.IRON, CopperStage.UNAFFECTED.color);
        setSoundType(sound);
        setHardness(3.0F);
        BlockBasic.resistance(this, 6.0F);
        setDefaultState(this.blockState.getBaseState().withProperty(BlockDirectional.FACING, EnumFacing.UP).withProperty(POWERED, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, BlockDirectional.FACING, POWERED);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(BlockDirectional.FACING, EnumFacing.byIndex(meta & 7)).withProperty(POWERED, (meta & 8) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(BlockDirectional.FACING).getIndex() | (state.getValue(POWERED) ? 8 : 0);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(BlockDirectional.FACING, rot.rotate(state.getValue(BlockDirectional.FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withProperty(BlockDirectional.FACING, mirror.mirror(state.getValue(BlockDirectional.FACING)));
    }

    /** Points away from the face clicked; placed against a rod pointing the same way, it continues the other way. */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer) {
        IBlockState against = world.getBlockState(pos.offset(facing.getOpposite()));
        EnumFacing dir = facing;
        if (against.getBlock() == this && against.getValue(BlockDirectional.FACING) == facing) {
            dir = facing.getOpposite();
        }
        return getDefaultState().withProperty(BlockDirectional.FACING, dir);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        switch (state.getValue(BlockDirectional.FACING).getAxis()) {
            case X:
                return X;
            case Z:
                return Z;
            default:
                return Y;
        }
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
        return face.getAxis() == state.getValue(BlockDirectional.FACING).getAxis() ? BlockFaceShape.CENTER : BlockFaceShape.UNDEFINED;
    }

    // ---------------------------------------------------------------------------------------------
    // Redstone

    /** Called by {@link Lightning} when a bolt strikes this rod. */
    public void onStruck(World world, BlockPos pos, IBlockState state) {
        world.setBlockState(pos, state.withProperty(POWERED, true), 3);
        notifyAround(world, pos, state);
        world.scheduleUpdate(pos, this, POWER_TICKS);
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (state.getValue(POWERED)) {
            world.setBlockState(pos, state.withProperty(POWERED, false), 3);
            notifyAround(world, pos, state);
        }
    }

    private void notifyAround(World world, BlockPos pos, IBlockState state) {
        world.notifyNeighborsOfStateChange(pos, this, false);
        world.notifyNeighborsOfStateChange(pos.offset(state.getValue(BlockDirectional.FACING).getOpposite()), this, false);
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    /** Strong power into the block the rod stands on (the side the rod points away from). */
    @Override
    public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return state.getValue(POWERED) && state.getValue(BlockDirectional.FACING) == side ? 15 : 0;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (state.getValue(POWERED)) {
            notifyAround(world, pos, state);
        }
        super.breakBlock(world, pos, state);
        world.removeTileEntity(pos);
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityLightningRod();
    }

    /** Marker tile entity: no data, no ticking. Keeps the tile entity when only {@code powered} changes. */
    public static class TileEntityLightningRod extends TileEntity {
        @Override
        public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
            return oldState.getBlock() != newState.getBlock();
        }
    }
}
