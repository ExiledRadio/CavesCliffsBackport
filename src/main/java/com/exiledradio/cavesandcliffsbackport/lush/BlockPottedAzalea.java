package com.exiledradio.cavesandcliffsbackport.lush;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A flower pot holding an azalea or a flowering azalea.
 *
 * <p>1.12 keeps a pot plant in the pot tile entity and only accepts its own list of plants, so these are
 * blocks of their own with no tile entity: {@link LushEvents} swaps an empty vanilla pot for one when an
 * azalea is used on it, and using this block gives the azalea back and leaves the vanilla pot.
 */
public class BlockPottedAzalea extends Block {

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.3125, 0, 0.3125, 0.6875, 0.375, 0.6875);

    private final boolean flowering;

    public BlockPottedAzalea(boolean flowering) {
        super(Material.CIRCUITS, MapColor.AIR);
        this.flowering = flowering;
        setHardness(0.0F);
        setSoundType(SoundType.STONE);
    }

    public Block plant() {
        return this.flowering ? LushContent.floweringAzalea : LushContent.azalea;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return BOX;
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
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            ItemStack plant = new ItemStack(plant());
            ItemStack held = player.getHeldItem(hand);
            if (held.isEmpty()) {
                player.setHeldItem(hand, plant);
            } else if (!player.addItemStackToInventory(plant)) {
                player.dropItem(plant, false);
            }
            world.setBlockState(pos, Blocks.FLOWER_POT.getDefaultState(), 3);
        }
        return true;
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(new ItemStack(Items.FLOWER_POT));
        drops.add(new ItemStack(plant()));
    }

    @Override
    public Item getItemDropped(IBlockState state, java.util.Random rand, int fortune) {
        return Items.FLOWER_POT;
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(plant());
    }
}
