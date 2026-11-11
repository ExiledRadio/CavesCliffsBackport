package com.exiledradio.cavesandcliffsbackport.lush;

import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Azalea leaves and flowering azalea leaves: vanilla leaves (decay near no log, shears) that drop their own
 * azalea (5%, Fortune 6.25%, 8.3%, 10%) and sticks (2%, 1-2). Always drawn see-through: 1.12 switches only its
 * own two leaf blocks between fancy and fast.
 */
public class BlockAzaleaLeaves extends BlockLeaves {

    private final boolean flowering;

    public BlockAzaleaLeaves(SoundType sound, boolean flowering) {
        this.flowering = flowering;
        setSoundType(sound);
        setDefaultState(this.blockState.getBaseState().withProperty(CHECK_DECAY, false).withProperty(DECAYABLE, true));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, CHECK_DECAY, DECAYABLE);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(DECAYABLE, (meta & 4) == 0).withProperty(CHECK_DECAY, (meta & 8) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return (state.getValue(DECAYABLE) ? 0 : 4) | (state.getValue(CHECK_DECAY) ? 8 : 0);
    }

    @Override
    public BlockPlanks.EnumType getWoodType(int meta) {
        return BlockPlanks.EnumType.OAK;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public net.minecraft.util.BlockRenderLayer getRenderLayer() {
        return net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return true;
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) {
        items.add(new ItemStack(this));
    }

    private Item bush() {
        return Item.getItemFromBlock(this.flowering ? LushContent.floweringAzalea : LushContent.azalea);
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return bush();
    }

    @Override
    public int damageDropped(IBlockState state) {
        return 0;
    }

    private static final float[] BUSH = {0.05F, 0.0625F, 0.083333336F, 0.1F};
    private static final float[] STICK = {0.02F, 0.022222223F, 0.025F, 0.033333335F, 0.1F};

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        Random r = world instanceof World ? ((World) world).rand : new Random();
        if (r.nextFloat() < BUSH[Math.min(Math.max(fortune, 0), BUSH.length - 1)]) {
            drops.add(new ItemStack(bush()));
        }
        if (r.nextFloat() < STICK[Math.min(Math.max(fortune, 0), STICK.length - 1)]) {
            drops.add(new ItemStack(Items.STICK, 1 + r.nextInt(2)));
        }
    }

    @Override
    protected ItemStack getSilkTouchDrop(IBlockState state) {
        return new ItemStack(this);
    }

    @Override
    public List<ItemStack> onSheared(ItemStack item, IBlockAccess world, BlockPos pos, int fortune) {
        return Collections.singletonList(new ItemStack(this));
    }
}
