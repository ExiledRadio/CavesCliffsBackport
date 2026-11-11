package com.exiledradio.cavesandcliffsbackport.lush;

import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.IGrowable;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * Big dripleaf: a leaf on a stack of stems, on clay, moss, dirt or farmland. An entity standing on the leaf
 * makes it unstable; 10 ticks later it tilts part way, 10 ticks after that all the way (nothing can stand on
 * it), and 100 ticks later it springs back. A projectile tilts it all the way at once. While it is powered by
 * redstone it never tilts. Bone meal adds a stem.
 *
 * <h2>Noticing who stands on it</h2>
 * 1.12 only tells a block about entities inside its space, and an entity on the leaf is above its collision
 * box. So the leaf asks for a tick whenever an entity's collision search touches it, and that tick looks for
 * someone standing on it. That covers everything that moves, items included.
 */
public class BlockBigDripleaf extends BlockLushPlant implements IGrowable {

    public enum Tilt implements IStringSerializable {
        NONE, UNSTABLE, PARTIAL, FULL;

        @Override
        public String getName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public static final PropertyEnum<Tilt> TILT = PropertyEnum.create("tilt", Tilt.class);

    private static final AxisAlignedBB LEAF = new AxisAlignedBB(0, 0.6875, 0, 1, 0.9375, 1);
    private static final AxisAlignedBB LEAF_PARTIAL = new AxisAlignedBB(0, 0.6875, 0, 1, 0.8125, 1);
    private static final AxisAlignedBB OUTLINE = new AxisAlignedBB(0, 0, 0, 1, 0.9375, 1);

    public BlockBigDripleaf(SoundType sound) {
        super(Material.PLANTS, MapColor.FOLIAGE, sound, 0.1F);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(TILT, Tilt.NONE));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, TILT);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(TILT, Tilt.values()[(meta >> 2) & 3]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(TILT).ordinal() << 2);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withProperty(FACING, mirror.mirror(state.getValue(FACING)));
    }

    static boolean isPart(IBlockState s) {
        return s.getBlock() instanceof BlockBigDripleaf || s.getBlock() instanceof Stem;
    }

    @Override
    public boolean canStay(World world, BlockPos pos, IBlockState state) {
        IBlockState below = world.getBlockState(pos.down());
        return isPart(below) || Soil.bigDripleaf(below);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer) {
        IBlockState below = world.getBlockState(pos.down());
        EnumFacing f = isPart(below) ? below.getValue(FACING) : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, f);
    }

    /** A leaf placed on a leaf: the lower one becomes a stem. */
    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        BlockPos down = pos.down();
        IBlockState below = world.getBlockState(down);
        if (below.getBlock() instanceof BlockBigDripleaf) {
            world.setBlockState(down, LushContent.bigDripleafStem.getDefaultState().withProperty(FACING, below.getValue(FACING)), 2);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Shape

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return OUTLINE;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        switch (state.getValue(TILT)) {
            case FULL:
                return NULL_AABB;
            case PARTIAL:
                return LEAF_PARTIAL;
            default:
                return LEAF;
        }
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> list, @Nullable Entity entity, boolean isActualState) {
        super.addCollisionBoxToList(state, world, pos, entityBox, list, entity, isActualState);
        if (!world.isRemote && entity != null && state.getValue(TILT) == Tilt.NONE && standsOn(entity, pos)
                && entity.getEntityBoundingBox().maxX > pos.getX() && entity.getEntityBoundingBox().minX < pos.getX() + 1
                && entity.getEntityBoundingBox().maxZ > pos.getZ() && entity.getEntityBoundingBox().minZ < pos.getZ() + 1
                && !world.isUpdateScheduled(pos, this)) {
            world.scheduleUpdate(pos, this, 1);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tilting

    private static boolean standsOn(Entity e, BlockPos pos) {
        return e.onGround && e.posY > pos.getY() + 0.6875 && !e.isDead
                && !(e instanceof EntityPlayer && ((EntityPlayer) e).isSpectator());
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (world.isRemote) {
            return;
        }
        if (world.isBlockPowered(pos)) {
            reset(world, pos, state);
            return;
        }
        switch (state.getValue(TILT)) {
            case NONE:
                AxisAlignedBB above = new AxisAlignedBB(pos.getX(), pos.getY() + 0.6875, pos.getZ(), pos.getX() + 1, pos.getY() + 1.2, pos.getZ() + 1);
                for (Entity e : world.getEntitiesWithinAABB(Entity.class, above)) {
                    if (standsOn(e, pos)) {
                        tilt(world, pos, state, Tilt.UNSTABLE, null);
                        break;
                    }
                }
                break;
            case UNSTABLE:
                tilt(world, pos, state, Tilt.PARTIAL, LushContent.tiltDown);
                break;
            case PARTIAL:
                tilt(world, pos, state, Tilt.FULL, LushContent.tiltDown);
                break;
            default:
                reset(world, pos, state);
        }
    }

    /** Sets a tilt and asks for the tick that ends it: 10 ticks for unstable and partial, 100 for full. */
    public void tilt(World world, BlockPos pos, IBlockState state, Tilt tilt, @Nullable SoundEvent sound) {
        world.setBlockState(pos, state.withProperty(TILT, tilt), 2);
        if (sound != null) {
            world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0F, 0.8F + world.rand.nextFloat() * 0.4F);
        }
        if (tilt != Tilt.NONE) {
            world.scheduleUpdate(pos, this, tilt == Tilt.FULL ? 100 : 10);
        }
    }

    private void reset(World world, BlockPos pos, IBlockState state) {
        if (state.getValue(TILT) != Tilt.NONE) {
            tilt(world, pos, state, Tilt.NONE, LushContent.tiltUp);
        }
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!canStay(world, pos, state)) {
            world.destroyBlock(pos, true);
        } else if (world.isBlockPowered(pos)) {
            reset(world, pos, state);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Bone meal

    @Override
    public boolean canGrow(World world, BlockPos pos, IBlockState state, boolean isClient) {
        return pos.getY() < 254 && world.isAirBlock(pos.up());
    }

    @Override
    public boolean canUseBonemeal(World world, Random rand, BlockPos pos, IBlockState state) {
        return true;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, IBlockState state) {
        EnumFacing f = state.getValue(FACING);
        world.setBlockState(pos, LushContent.bigDripleafStem.getDefaultState().withProperty(FACING, f), 2);
        world.setBlockState(pos.up(), getDefaultState().withProperty(FACING, f), 3);
    }

    /**
     * Stands a dripleaf of 2 to 5 blocks (less if there is no room) at {@code pos}: stems and a leaf on top.
     *
     * @return how tall it is; 0 if there was no room at all
     */
    public static int placeTall(World world, Random rand, BlockPos pos, EnumFacing facing, int flags) {
        int want = 2 + rand.nextInt(4), n = 0;
        while (n < want && pos.getY() + n < 255 && world.isAirBlock(pos.up(n))) {
            n++;
        }
        for (int i = 0; i < n - 1; i++) {
            world.setBlockState(pos.up(i), LushContent.bigDripleafStem.getDefaultState().withProperty(FACING, facing), 2);
        }
        if (n > 0) {
            world.setBlockState(pos.up(n - 1), LushContent.bigDripleaf.getDefaultState().withProperty(FACING, facing), flags);
        }
        return n;
    }

    /** The stem under a leaf. Breaks when what is under or over it goes; drops a big dripleaf. */
    public static class Stem extends BlockLushPlant implements IGrowable {

        private static final AxisAlignedBB[] BOXES = new AxisAlignedBB[4];

        static {
            BOXES[EnumFacing.NORTH.getHorizontalIndex()] = new AxisAlignedBB(0.3125, 0, 0.5625, 0.6875, 1, 0.9375);
            BOXES[EnumFacing.SOUTH.getHorizontalIndex()] = new AxisAlignedBB(0.3125, 0, 0.0625, 0.6875, 1, 0.4375);
            BOXES[EnumFacing.EAST.getHorizontalIndex()] = new AxisAlignedBB(0.0625, 0, 0.3125, 0.4375, 1, 0.6875);
            BOXES[EnumFacing.WEST.getHorizontalIndex()] = new AxisAlignedBB(0.5625, 0, 0.3125, 0.9375, 1, 0.6875);
        }

        public Stem(SoundType sound) {
            super(Material.PLANTS, MapColor.FOLIAGE, sound, 0.1F);
            setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, FACING);
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return state.getValue(FACING).getHorizontalIndex();
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
            return BOXES[state.getValue(FACING).getHorizontalIndex()];
        }

        @Override
        public boolean canStay(World world, BlockPos pos, IBlockState state) {
            IBlockState below = world.getBlockState(pos.down());
            return (below.getBlock() == this || Soil.bigDripleaf(below)) && isPart(world.getBlockState(pos.up()));
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return Item.getItemFromBlock(LushContent.bigDripleaf);
        }

        @Override
        public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
            return new ItemStack(LushContent.bigDripleaf);
        }

        @Nullable
        private BlockPos leaf(World world, BlockPos pos) {
            BlockPos p = pos.up();
            while (p.getY() < 255 && world.getBlockState(p).getBlock() == this) {
                p = p.up();
            }
            return world.getBlockState(p).getBlock() == LushContent.bigDripleaf ? p : null;
        }

        @Override
        public boolean canGrow(World world, BlockPos pos, IBlockState state, boolean isClient) {
            BlockPos leaf = leaf(world, pos);
            return leaf != null && LushContent.bigDripleaf.canGrow(world, leaf, world.getBlockState(leaf), isClient);
        }

        @Override
        public boolean canUseBonemeal(World world, Random rand, BlockPos pos, IBlockState state) {
            return true;
        }

        @Override
        public void grow(World world, Random rand, BlockPos pos, IBlockState state) {
            BlockPos leaf = leaf(world, pos);
            if (leaf != null) {
                LushContent.bigDripleaf.grow(world, rand, leaf, world.getBlockState(leaf));
            }
        }
    }

    /** For the item: a big dripleaf may also go on top of another one. */
    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock().isReplaceable(world, pos) && world.getBlockState(pos).getBlock() != Blocks.WATER
                && canStay(world, pos, getDefaultState());
    }
}
