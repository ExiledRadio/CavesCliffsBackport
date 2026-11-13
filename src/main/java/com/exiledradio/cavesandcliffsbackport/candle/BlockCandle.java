package com.exiledradio.cavesandcliffsbackport.candle;

import com.exiledradio.backportcore.compat.QuarkCandles;
import com.exiledradio.backportcore.waterlog.Waterloggable;
import com.exiledradio.cavesandcliffsbackport.common.BlockBasic;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * A candle, plain or dyed: one to four in a block, each lit one giving light 3. Lit with flint and steel, a
 * fire charge or a burning arrow ({@link CandleEvents}), put out with an empty hand. Needs something under
 * it that holds up its middle; drops as many candles as it holds; pistons break it.
 *
 * <p>Waterloggable (1.18.2), through Backport Core's {@link Waterloggable}: placed into a water source it keeps
 * the water and is unlit (candles are always placed unlit); water coming in puts a lit one out; a waterlogged one
 * cannot be lit ({@link BlockAbstractCandle#canLight(IBlockAccess, BlockPos, IBlockState)}). Candle cakes hold none.
 */
public class BlockCandle extends BlockAbstractCandle implements Waterloggable {

    public static final PropertyInteger CANDLES = PropertyInteger.create("candles", 1, 4);

    /** 1.18's shapes for one to four candles. */
    private static final AxisAlignedBB[] BOXES = {
            box(7, 0, 7, 9, 6, 9), box(5, 0, 6, 11, 6, 9), box(5, 0, 6, 10, 6, 11), box(5, 0, 5, 11, 6, 10)};

    /** 1.18's flame positions for one to four candles. */
    private static final Vec3d[][] WICKS = {
            {new Vec3d(0.5, 0.5, 0.5)},
            {new Vec3d(0.375, 0.44, 0.5), new Vec3d(0.625, 0.5, 0.44)},
            {new Vec3d(0.5, 0.313, 0.625), new Vec3d(0.375, 0.44, 0.5), new Vec3d(0.56, 0.5, 0.44)},
            {new Vec3d(0.44, 0.313, 0.56), new Vec3d(0.625, 0.44, 0.56), new Vec3d(0.375, 0.44, 0.375), new Vec3d(0.56, 0.5, 0.375)}};

    public BlockCandle(MapColor color, SoundType sound) {
        super(Material.CIRCUITS, color);
        setSoundType(sound);
        setHardness(0.1F);
        BlockBasic.resistance(this, 0.1F);
        setDefaultState(this.blockState.getBaseState().withProperty(CANDLES, 1).withProperty(LIT, false));
    }

    /** 1.18 candles give no enchanting power; with Quark, ours stand in for its candles and give what its do. */
    @Override
    public float getEnchantPowerBonus(World world, BlockPos pos) {
        return QuarkCandles.enchantPower();
    }

    private static AxisAlignedBB box(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new AxisAlignedBB(x1 / 16.0, y1 / 16.0, z1 / 16.0, x2 / 16.0, y2 / 16.0, z2 / 16.0);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, CANDLES, LIT);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(CANDLES, (meta & 3) + 1).withProperty(LIT, (meta & 4) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(CANDLES) - 1 | (state.getValue(LIT) ? 4 : 0);
    }

    @Override
    protected Vec3d[] wicks(IBlockState state) {
        return WICKS[state.getValue(CANDLES) - 1];
    }

    @Override
    public int getLightValue(IBlockState state) {
        return state.getValue(LIT) ? 3 * state.getValue(CANDLES) : 0;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return BOXES[state.getValue(CANDLES) - 1];
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.DESTROY;
    }

    // ---------------------------------------------------------------------------------------------
    // Support

    /** @return whether the block below holds up the middle of its top face, as current Java asks */
    static boolean supported(IBlockAccess world, BlockPos pos) {
        BlockPos below = pos.down();
        IBlockState state = world.getBlockState(below);
        switch (state.getBlockFaceShape(world, below, EnumFacing.UP)) {
            case SOLID:
            case CENTER:
            case CENTER_SMALL:
            case CENTER_BIG:
            case MIDDLE_POLE_THIN:
            case MIDDLE_POLE:
            case MIDDLE_POLE_THICK:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && supported(world, pos);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!supported(world, pos)) {
            dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Putting out

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.getHeldItem(hand).isEmpty() || !state.getValue(LIT) || !player.capabilities.allowEdit) {
            return false;
        }
        if (!world.isRemote) {
            extinguish(world, pos, state);
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Drops

    @Override
    public int quantityDropped(IBlockState state, int fortune, Random random) {
        return state.getValue(CANDLES);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(new ItemStack(this, state.getValue(CANDLES)));
    }

    @Override
    public int damageDropped(IBlockState state) {
        return 0;
    }

    /** 1.18.2's {@code placeLiquid}: water coming into a lit candle puts it out. */
    @Override
    public void onWaterChanged(World world, BlockPos pos, IBlockState state, boolean hasWater) {
        if (hasWater && !world.isRemote && state.getValue(LIT)) {
            extinguish(world, pos, state);
        }
    }
}
