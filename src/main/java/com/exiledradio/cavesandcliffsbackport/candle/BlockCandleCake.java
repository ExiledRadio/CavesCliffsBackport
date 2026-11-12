package com.exiledradio.cavesandcliffsbackport.candle;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCake;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * A whole cake with one candle on it, made by using a candle on an uneaten cake. The candle lights and goes
 * out as a candle does (light 3); eating a slice leaves a cake with one slice gone and drops the candle.
 * Breaking it gives the candle only, as a cake gives nothing.
 */
public class BlockCandleCake extends BlockAbstractCandle {

    private static final AxisAlignedBB CAKE = new AxisAlignedBB(1 / 16.0, 0, 1 / 16.0, 15 / 16.0, 8 / 16.0, 15 / 16.0);
    private static final AxisAlignedBB CANDLE = new AxisAlignedBB(7 / 16.0, 8 / 16.0, 7 / 16.0, 9 / 16.0, 14 / 16.0, 9 / 16.0);
    private static final Vec3d[] WICK = {new Vec3d(0.5, 1.0, 0.5)};

    private final Block candle;

    public BlockCandleCake(Block candle) {
        super(Material.CAKE, Material.CAKE.getMaterialMapColor());
        this.candle = candle;
        setSoundType(SoundType.CLOTH);
        setHardness(0.5F);
        setDefaultState(this.blockState.getBaseState().withProperty(LIT, false));
    }

    /** @return the candle on this cake */
    public Block candle() {
        return this.candle;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, LIT);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(LIT, (meta & 1) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(LIT) ? 1 : 0;
    }

    @Override
    protected Vec3d[] wicks(IBlockState state) {
        return WICK;
    }

    @Override
    public int getLightValue(IBlockState state) {
        return state.getValue(LIT) ? 3 : 0;
    }

    // ---------------------------------------------------------------------------------------------
    // Shape: the cake and the candle on it

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return CAKE;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean isActualState) {
        addCollisionBoxToList(pos, entityBox, boxes, CAKE);
        addCollisionBoxToList(pos, entityBox, boxes, CANDLE);
    }

    @Override
    @Nullable
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end) {
        RayTraceResult cake = rayTrace(pos, start, end, CAKE);
        RayTraceResult candle = rayTrace(pos, start, end, CANDLE);
        if (cake == null || candle == null) {
            return cake == null ? candle : cake;
        }
        return candle.hitVec.squareDistanceTo(start) < cake.hitVec.squareDistanceTo(start) ? candle : cake;
    }

    // ---------------------------------------------------------------------------------------------
    // Support, as a cake

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && world.getBlockState(pos.down()).getMaterial().isSolid();
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!world.getBlockState(pos.down()).getMaterial().isSolid()) {
            dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Putting out and eating

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == Items.FLINT_AND_STEEL || held.getItem() == Items.FIRE_CHARGE) {
            return false;
        }
        // The candle itself, clicked with an empty hand, goes out; anything else is a bite of the cake.
        if (hitY > 0.5F && held.isEmpty() && state.getValue(LIT)) {
            if (!world.isRemote) {
                extinguish(world, pos, state);
            }
            return true;
        }
        if (!player.canEat(false)) {
            return false;
        }
        if (!world.isRemote) {
            player.addStat(StatList.CAKE_SLICES_EATEN);
            player.getFoodStats().addStats(2, 0.1F);
            world.setBlockState(pos, Blocks.CAKE.getDefaultState().withProperty(BlockCake.BITES, 1), 3);
            spawnAsEntity(world, pos, new ItemStack(this.candle));
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Drops, comparators, pick block

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Item.getItemFromBlock(this.candle);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(new ItemStack(this.candle));
    }

    @Override
    public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
        return new ItemStack(Items.CAKE);
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    /** A whole cake's signal. */
    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        return 14;
    }
}
