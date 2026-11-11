package com.exiledradio.cavesandcliffsbackport.powdersnow;

import com.exiledradio.cavesandcliffsbackport.common.BlockBasic;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * A cauldron holding powder snow, one to three levels. Snowfall makes one from an empty cauldron and fills
 * it ({@link PowderSnowEvents}); a powder snow bucket fills any cauldron; an empty bucket takes a full one.
 * It drops the vanilla cauldron and turns back into it when emptied.
 */
public class BlockPowderSnowCauldron extends BlockBasic {

    public static final PropertyInteger LEVEL = PropertyInteger.create("level", 1, 3);

    private static final AxisAlignedBB LEGS = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.3125D, 1.0D);
    private static final AxisAlignedBB NORTH = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.125D);
    private static final AxisAlignedBB SOUTH = new AxisAlignedBB(0.0D, 0.0D, 0.875D, 1.0D, 1.0D, 1.0D);
    private static final AxisAlignedBB EAST = new AxisAlignedBB(0.875D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    private static final AxisAlignedBB WEST = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 0.125D, 1.0D, 1.0D);

    public BlockPowderSnowCauldron() {
        super(Material.IRON, MapColor.STONE, SoundType.STONE, 2.0F, 2.0F);
        setDefaultState(this.blockState.getBaseState().withProperty(LEVEL, 1));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, LEVEL);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(LEVEL, Math.max(1, Math.min(3, meta)));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(LEVEL);
    }

    // ---- shape: the vanilla cauldron's -------------------------------------------------------

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean isActualState) {
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
    public boolean isPassable(IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        if (face == EnumFacing.UP) {
            return BlockFaceShape.BOWL;
        }
        return face == EnumFacing.DOWN ? BlockFaceShape.UNDEFINED : BlockFaceShape.SOLID;
    }

    // ---- contents ----------------------------------------------------------------------------

    /** An entity on fire in the snow is put out, and the snow it touched melts: a water cauldron one level lower. */
    @Override
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        int level = state.getValue(LEVEL);
        float top = pos.getY() + (6.0F + 3 * level) / 16.0F;
        if (!world.isRemote && entity.isBurning() && entity.getEntityBoundingBox().minY <= top) {
            entity.extinguish();
            if (!(entity instanceof EntityPlayer) || world.isBlockModifiable((EntityPlayer) entity, pos)) {
                world.setBlockState(pos, Blocks.CAULDRON.getDefaultState().withProperty(BlockCauldron.LEVEL, level - 1));
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        Item item = held.getItem();
        if (item == Items.BUCKET) {
            if (state.getValue(LEVEL) != 3) {
                return false;
            }
            if (!world.isRemote) {
                fill(player, hand, held, new ItemStack(PowderSnowContent.bucket));
                world.setBlockState(pos, Blocks.CAULDRON.getDefaultState());
                world.playSound(null, pos, PowderSnowContent.bucketFill, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
            return true;
        }
        IBlockState becomes = null;
        SoundEvent sound = null;
        if (item == Items.WATER_BUCKET) {
            becomes = Blocks.CAULDRON.getDefaultState().withProperty(BlockCauldron.LEVEL, 3);
            sound = SoundEvents.ITEM_BUCKET_EMPTY;
        } else if (item == Items.LAVA_BUCKET) {
            // The lava cauldron is the dripstone stream's; without it lava has no cauldron to go in.
            Block lava = Owners.ourBlock("lava_cauldron");
            if (lava != null) {
                becomes = lava.getDefaultState();
                sound = SoundEvents.ITEM_BUCKET_EMPTY_LAVA;
            }
        } else if (item == PowderSnowContent.bucket) {
            becomes = state.withProperty(LEVEL, 3);
            sound = PowderSnowContent.bucketEmpty;
        }
        if (becomes == null) {
            return false;
        }
        if (!world.isRemote) {
            empty(player, hand);
            world.setBlockState(pos, becomes);
            world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
        return true;
    }

    /** A bucket was emptied into a cauldron: survival players get the empty bucket back. */
    static void empty(EntityPlayer player, EnumHand hand) {
        player.addStat(StatList.CAULDRON_FILLED);
        if (!player.capabilities.isCreativeMode) {
            player.setHeldItem(hand, new ItemStack(Items.BUCKET));
        }
    }

    /** One empty bucket of a stack was filled: the filled one goes to the hand, the inventory or the ground. */
    static void fill(EntityPlayer player, EnumHand hand, ItemStack held, ItemStack filled) {
        player.addStat(StatList.CAULDRON_USED);
        if (player.capabilities.isCreativeMode) {
            if (!player.inventory.hasItemStack(filled)) {
                player.inventory.addItemStackToInventory(filled);
            }
            return;
        }
        held.shrink(1);
        if (held.isEmpty()) {
            player.setHeldItem(hand, filled);
        } else if (!player.inventory.addItemStackToInventory(filled)) {
            player.dropItem(filled, false);
        }
    }

    /**
     * Snow falling on a cauldron: an empty vanilla cauldron becomes level 1, ours gains a level.
     *
     * @return whether anything changed
     */
    public static boolean snowInto(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() == Blocks.CAULDRON && state.getValue(BlockCauldron.LEVEL) == 0) {
            return world.setBlockState(pos, PowderSnowContent.cauldron.getDefaultState());
        }
        if (state.getBlock() == PowderSnowContent.cauldron && state.getValue(LEVEL) < 3) {
            return world.setBlockState(pos, state.cycleProperty(LEVEL));
        }
        return false;
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        return state.getValue(LEVEL);
    }

    // ---- drops: the vanilla cauldron ---------------------------------------------------------

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.CAULDRON;
    }

    @Override
    public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
        return new ItemStack(Items.CAULDRON);
    }

    @Override
    public boolean canSilkHarvest(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        return false;
    }
}
