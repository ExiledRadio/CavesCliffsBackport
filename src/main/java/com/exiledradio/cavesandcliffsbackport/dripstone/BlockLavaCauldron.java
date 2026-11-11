package com.exiledradio.cavesandcliffsbackport.dripstone;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * A cauldron full of lava (1.17 made each filling its own block; 1.12's cauldron only knows water levels).
 *
 * <p>It is vanilla's cauldron in every way but its contents: the same shape, hardness and item. An empty
 * bucket takes the lava and leaves vanilla's empty cauldron; a bucket of water or lava replaces what is in
 * it. Light 15, comparator 3, and whatever stands in the lava burns as in lava.
 */
public class BlockLavaCauldron extends Block {

    private static final AxisAlignedBB LEGS = new AxisAlignedBB(0, 0, 0, 1, 0.3125, 1);
    private static final AxisAlignedBB NORTH = new AxisAlignedBB(0, 0, 0, 1, 1, 0.125);
    private static final AxisAlignedBB SOUTH = new AxisAlignedBB(0, 0, 0.875, 1, 1, 1);
    private static final AxisAlignedBB EAST = new AxisAlignedBB(0.875, 0, 0, 1, 1, 1);
    private static final AxisAlignedBB WEST = new AxisAlignedBB(0, 0, 0, 0.125, 1, 1);

    public BlockLavaCauldron() {
        super(Material.IRON, MapColor.STONE);
        setSoundType(SoundType.STONE);
        setHardness(2.0F);
        setLightLevel(1.0F);
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        addCollisionBoxToList(pos, entityBox, boxes, LEGS);
        addCollisionBoxToList(pos, entityBox, boxes, WEST);
        addCollisionBoxToList(pos, entityBox, boxes, NORTH);
        addCollisionBoxToList(pos, entityBox, boxes, EAST);
        addCollisionBoxToList(pos, entityBox, boxes, SOUTH);
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
        return face == EnumFacing.UP ? BlockFaceShape.BOWL : face == EnumFacing.DOWN ? BlockFaceShape.UNDEFINED : BlockFaceShape.SOLID;
    }

    /** Current Java: in the lava when the feet are under its surface and the head is above the cauldron's floor. */
    @Override
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (world.isRemote || entity.posY >= pos.getY() + 0.9375 || entity.getEntityBoundingBox().maxY <= pos.getY() + 0.25) {
            return;
        }
        if (!entity.isImmuneToFire()) {
            entity.attackEntityFrom(DamageSource.LAVA, 4.0F);
            entity.setFire(15);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        Item item = held.getItem();
        if (item == Items.BUCKET) {
            if (!world.isRemote) {
                if (!player.capabilities.isCreativeMode) {
                    held.shrink(1);
                    if (held.isEmpty()) {
                        player.setHeldItem(hand, new ItemStack(Items.LAVA_BUCKET));
                    } else if (!player.inventory.addItemStackToInventory(new ItemStack(Items.LAVA_BUCKET))) {
                        player.dropItem(new ItemStack(Items.LAVA_BUCKET), false);
                    }
                }
                player.addStat(StatList.CAULDRON_USED);
                world.setBlockState(pos, Blocks.CAULDRON.getDefaultState(), 3);
                world.playSound(null, pos, SoundEvents.ITEM_BUCKET_FILL_LAVA, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
            return true;
        }
        if (item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET) {
            if (!world.isRemote) {
                if (!player.capabilities.isCreativeMode) {
                    player.setHeldItem(hand, new ItemStack(Items.BUCKET));
                }
                player.addStat(StatList.CAULDRON_FILLED);
                if (item == Items.WATER_BUCKET) {
                    world.setBlockState(pos, Blocks.CAULDRON.getDefaultState().withProperty(net.minecraft.block.BlockCauldron.LEVEL, 3), 3);
                    world.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0F, 1.0F);
                } else {
                    world.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY_LAVA, SoundCategory.BLOCKS, 1.0F, 1.0F);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.CAULDRON;
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(Items.CAULDRON);
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        return 3;
    }

    @Override
    public boolean isPassable(IBlockAccess world, BlockPos pos) {
        return false;
    }
}
