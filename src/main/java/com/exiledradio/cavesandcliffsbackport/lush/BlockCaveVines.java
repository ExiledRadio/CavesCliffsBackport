package com.exiledradio.cavesandcliffsbackport.lush;

import net.minecraft.block.Block;
import net.minecraft.block.IGrowable;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Cave vines: a hanging vine of plant blocks ending in a growing tip, either of which may carry glow berries
 * (light 14). Using a vine with berries picks them; bone meal grows berries; shears on the tip stop it growing.
 * Climbable.
 *
 * <h2>The tip's age</h2>
 * Current Java gives the tip an age of 0-25: each growth adds one, 25 stops, and a planted vine starts at a
 * random age, so vines end at different lengths. 1.12 has 16 metadata values and the tip also needs its
 * berries bit, so the age here has 8 steps (0-7) and a growth advances it 7 times in 25: the same expected
 * 25 growths from 0, and the same random start.
 */
public abstract class BlockCaveVines extends BlockLushPlant implements IGrowable {

    public static final PropertyBool BERRIES = PropertyBool.create("berries");
    public static final PropertyInteger AGE = PropertyInteger.create("age", 0, 7);
    public static final int MAX_AGE = 7;

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.0625, 0, 0.0625, 0.9375, 1, 0.9375);

    protected BlockCaveVines(SoundType sound) {
        super(Material.PLANTS, MapColor.FOLIAGE, sound, 0.0F);
    }

    public static boolean isVine(IBlockState s) {
        return s.getBlock() instanceof BlockCaveVines;
    }

    @Override
    public boolean canStay(World world, BlockPos pos, IBlockState state) {
        return isVine(world.getBlockState(pos.up())) || solidAbove(world, pos);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return BOX;
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(BERRIES) ? 14 : 0;
    }

    @Override
    public boolean isLadder(IBlockState state, IBlockAccess world, BlockPos pos, EntityLivingBase entity) {
        return true;
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        if (state.getValue(BERRIES)) {
            drops.add(new ItemStack(LushContent.glowBerries));
        }
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return state.getValue(BERRIES) ? LushContent.glowBerries : Items.AIR;
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(LushContent.glowBerries);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!state.getValue(BERRIES)) {
            return false;
        }
        if (!world.isRemote) {
            spawnAsEntity(world, pos, new ItemStack(LushContent.glowBerries));
            world.playSound(null, pos, LushContent.pickBerries, SoundCategory.BLOCKS, 1.0F, 0.8F + world.rand.nextFloat() * 0.4F);
            world.setBlockState(pos, state.withProperty(BERRIES, false), 2);
        }
        return true;
    }

    @Override
    public boolean canGrow(World world, BlockPos pos, IBlockState state, boolean isClient) {
        return !state.getValue(BERRIES);
    }

    @Override
    public boolean canUseBonemeal(World world, Random rand, BlockPos pos, IBlockState state) {
        return true;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, IBlockState state) {
        world.setBlockState(pos, state.withProperty(BERRIES, true), 2);
    }

    /** The tip: grows downward on random ticks. */
    public static class Head extends BlockCaveVines {

        public Head(SoundType sound) {
            super(sound);
            setTickRandomly(true);
            setDefaultState(this.blockState.getBaseState().withProperty(AGE, 0).withProperty(BERRIES, false));
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, AGE, BERRIES);
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState().withProperty(AGE, meta & 7).withProperty(BERRIES, (meta & 8) != 0);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return state.getValue(AGE) | (state.getValue(BERRIES) ? 8 : 0);
        }

        /** A newly planted tip: a random age short of the last, as current Java. */
        public IBlockState planted(Random rand) {
            return getDefaultState().withProperty(AGE, rand.nextInt(MAX_AGE));
        }

        @Override
        public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
            if (!canStay(world, pos, state)) {
                world.destroyBlock(pos, true);
            } else if (isVine(world.getBlockState(pos.down()))) {
                world.setBlockState(pos, LushContent.caveVinesPlant.getDefaultState().withProperty(BERRIES, state.getValue(BERRIES)), 2);
            }
        }

        @Override
        public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
            int age = state.getValue(AGE);
            if (world.isRemote || age >= MAX_AGE || rand.nextFloat() >= 0.1F) {
                return;
            }
            BlockPos below = pos.down();
            if (below.getY() < 1 || !world.isAirBlock(below)) {
                return;
            }
            int next = rand.nextInt(25) < MAX_AGE ? age + 1 : age;
            world.setBlockState(pos, LushContent.caveVinesPlant.getDefaultState().withProperty(BERRIES, state.getValue(BERRIES)), 2);
            world.setBlockState(below, getDefaultState().withProperty(AGE, next).withProperty(BERRIES, rand.nextFloat() < 0.11F), 3);
        }

        @Override
        public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                        EnumFacing facing, float hitX, float hitY, float hitZ) {
            ItemStack held = player.getHeldItem(hand);
            if (held.getItem() instanceof ItemShears && state.getValue(AGE) < MAX_AGE && !state.getValue(BERRIES)) {
                if (!world.isRemote) {
                    world.playSound(null, pos, LushContent.crop, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    world.setBlockState(pos, state.withProperty(AGE, MAX_AGE), 2);
                    held.damageItem(1, player);
                }
                return true;
            }
            return super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ);
        }
    }

    /** Every vine block above the tip. */
    public static class Body extends BlockCaveVines {

        public Body(SoundType sound) {
            super(sound);
            setDefaultState(this.blockState.getBaseState().withProperty(BERRIES, false));
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, BERRIES);
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState().withProperty(BERRIES, (meta & 1) != 0);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return state.getValue(BERRIES) ? 1 : 0;
        }

        @Override
        public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
            if (!canStay(world, pos, state)) {
                world.destroyBlock(pos, true);
            } else if (!isVine(world.getBlockState(pos.down()))) {
                world.setBlockState(pos, LushContent.caveVines.planted(world.rand).withProperty(BERRIES, state.getValue(BERRIES)), 2);
            }
        }
    }
}
