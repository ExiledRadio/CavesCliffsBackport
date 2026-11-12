package com.exiledradio.cavesandcliffsbackport.lush;

import com.exiledradio.backportcore.waterlog.Waterloggable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.IGrowable;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.IShearable;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Small dripleaf: two blocks tall, on clay or moss. Dropped only for shears. Bone meal turns it into a big
 * dripleaf 2 to 5 blocks tall, facing the same way.
 */
public class BlockSmallDripleaf extends BlockLushPlant implements IGrowable, IShearable, Waterloggable {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    /** True for the top block (half=upper in current Java). */
    public static final PropertyBool UPPER = PropertyBool.create("upper");

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.125, 0, 0.125, 0.875, 0.8125, 0.875);

    public BlockSmallDripleaf(SoundType sound) {
        super(Material.PLANTS, MapColor.FOLIAGE, sound, 0.0F);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(UPPER, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, UPPER);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(UPPER, (meta & 4) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(UPPER) ? 4 : 0);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withProperty(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return BOX;
    }

    @Override
    public boolean canStay(World world, BlockPos pos, IBlockState state) {
        if (state.getValue(UPPER)) {
            IBlockState below = world.getBlockState(pos.down());
            return below.getBlock() == this && !below.getValue(UPPER);
        }
        IBlockState above = world.getBlockState(pos.up());
        return Soil.smallDripleaf(world, pos) && above.getBlock() == this && above.getValue(UPPER);
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        // Both halves go into air or water (each keeps a water source it is placed in, as 1.18.2's copyWaterloggedFrom).
        return pos.getY() < 255 && open(world, pos) && open(world, pos.up()) && Soil.smallDripleaf(world, pos);
    }

    private static boolean open(World world, BlockPos pos) {
        IBlockState s = world.getBlockState(pos);
        return s.getBlock().isReplaceable(world, pos) && s.getMaterial() != Material.LAVA;
    }

    /** On dirt it stands only in a water source: drained, the lower half (and so the upper) breaks. */
    @Override
    public void onWaterChanged(World world, BlockPos pos, IBlockState state, boolean hasWater) {
        if (!hasWater && !world.isRemote && !state.getValue(UPPER) && !canStay(world, pos, state)) {
            world.destroyBlock(pos, false);
        }
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        world.setBlockState(pos.up(), state.withProperty(UPPER, true), 3);
    }

    /** Places both halves, for world generation and tests. */
    public void place(World world, BlockPos pos, EnumFacing facing, int flags) {
        IBlockState lower = getDefaultState().withProperty(FACING, facing);
        world.setBlockState(pos, lower, flags);
        world.setBlockState(pos.up(), lower.withProperty(UPPER, true), flags);
    }

    /** Breaking one half takes the other with it, without a second drop. */
    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!canStay(world, pos, state)) {
            world.setBlockToAir(pos);
        }
    }

    @Override
    public void onBlockHarvested(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        BlockPos other = state.getValue(UPPER) ? pos.down() : pos.up();
        if (world.getBlockState(other).getBlock() == this) {
            world.setBlockState(other, Blocks.AIR.getDefaultState(), 2);
        }
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
    }

    @Override
    public boolean isShearable(ItemStack item, IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    public List<ItemStack> onSheared(ItemStack item, IBlockAccess world, BlockPos pos, int fortune) {
        return Collections.singletonList(new ItemStack(this));
    }

    @Override
    public boolean canGrow(World world, BlockPos pos, IBlockState state, boolean isClient) {
        return true;
    }

    @Override
    public boolean canUseBonemeal(World world, Random rand, BlockPos pos, IBlockState state) {
        return true;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, IBlockState state) {
        // 1.18.2: a big dripleaf grows in its place (it may replace small dripleaf), keeping any water there.
        BlockPos lower = state.getValue(UPPER) ? pos.down() : pos;
        EnumFacing facing = state.getValue(FACING);
        BlockBigDripleaf.placeTall(world, rand, lower, facing, 3);
    }
}
