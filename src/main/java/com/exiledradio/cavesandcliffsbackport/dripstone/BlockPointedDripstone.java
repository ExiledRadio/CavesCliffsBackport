package com.exiledradio.cavesandcliffsbackport.dripstone;

import com.exiledradio.cavesandcliffsbackport.common.BlockBasic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pointed dripstone: stalactites (pointing down) and stalagmites (pointing up), one block per segment.
 *
 * <h2>States</h2>
 * {@code thickness} (tip_merge, tip, frustum, middle, base) and {@code vertical_direction} (up, down): ten
 * states, metadata {@code thickness | (down ? 8 : 0)}. Every segment works out its own thickness from the
 * segments before and after it whenever one of them changes, so a column always reads base, middle...,
 * frustum, tip from its root, and two tips that meet become tip_merge unless the placer sneaked.
 *
 * <h2>Behavior (current Java)</h2>
 * <ul>
 *   <li>A segment needs a solid face or another segment of its own direction behind it. A stalagmite that
 *       loses it breaks, one segment a tick; a stalactite falls two ticks later, every segment below it
 *       too, as {@link EntityFallingStalactite}.</li>
 *   <li>Landing on a stalagmite's tip is a fall two blocks longer that hurts twice as much.</li>
 *   <li>Random ticks reach the topmost segment of a stalactite: with a water or lava source above the block
 *       it hangs from, a drip may fill a cauldron up to ten blocks under the tip (45/256 water, 15/256
 *       lava); hanging from a dripstone block with a water source above, it grows itself or a stalagmite
 *       below by one (64/5625), up to eight long.</li>
 *   <li>Tips drip particles: always a few, more with a liquid above.</li>
 * </ul>
 * Not waterloggable yet (the plan's waterlogging pass): it is placed in air only and never grows into water.
 */
public class BlockPointedDripstone extends Block {

    public static final PropertyEnum<Thickness> THICKNESS = PropertyEnum.create("thickness", Thickness.class);
    public static final PropertyDirection VERTICAL_DIRECTION = PropertyDirection.create("vertical_direction", EnumFacing.Plane.VERTICAL);

    /** What a stalactite drips. */
    public enum Fluid {
        NONE, WATER, LAVA
    }

    private static final AxisAlignedBB TIP_MERGE = box(5, 0, 5, 11, 16, 11);
    private static final AxisAlignedBB TIP_UP = box(5, 0, 5, 11, 11, 11);
    private static final AxisAlignedBB TIP_DOWN = box(5, 5, 5, 11, 16, 11);
    private static final AxisAlignedBB FRUSTUM = box(4, 0, 4, 12, 16, 12);
    private static final AxisAlignedBB MIDDLE = box(3, 0, 3, 13, 16, 13);
    private static final AxisAlignedBB BASE = box(2, 0, 2, 14, 16, 14);
    /** A drip passes a block that leaves this middle of the column free. */
    private static final AxisAlignedBB DRIP_SPACE = box(6, 0, 6, 10, 16, 10);

    private static final float WATER_CHANCE = 45 / 256.0F, LAVA_CHANCE = 15 / 256.0F, GROWTH_CHANCE = 64 / 5625.0F;
    private static final int MAX_DRIP_LENGTH = 11, MAX_GROWTH_LENGTH = 7, MAX_STALAGMITE_SEARCH = 10;

    public BlockPointedDripstone(SoundType sound) {
        super(Material.ROCK, MapColor.BROWN_STAINED_HARDENED_CLAY);
        setSoundType(sound);
        setHardness(1.5F);
        BlockBasic.resistance(this, 3.0F);
        setTickRandomly(true);
        setDefaultState(this.blockState.getBaseState().withProperty(THICKNESS, Thickness.TIP).withProperty(VERTICAL_DIRECTION, EnumFacing.UP));
    }

    private static AxisAlignedBB box(int x0, int y0, int z0, int x1, int y1, int z1) {
        return new AxisAlignedBB(x0 / 16.0, y0 / 16.0, z0 / 16.0, x1 / 16.0, y1 / 16.0, z1 / 16.0);
    }

    // ------------------------------------------------------------------------------- states and shape

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, THICKNESS, VERTICAL_DIRECTION);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(THICKNESS).ordinal() | (state.getValue(VERTICAL_DIRECTION) == EnumFacing.DOWN ? 8 : 0);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        Thickness[] all = Thickness.values();
        return getDefaultState().withProperty(THICKNESS, all[Math.min(meta & 7, all.length - 1)])
                .withProperty(VERTICAL_DIRECTION, (meta & 8) != 0 ? EnumFacing.DOWN : EnumFacing.UP);
    }

    public IBlockState state(EnumFacing direction, Thickness thickness) {
        return getDefaultState().withProperty(VERTICAL_DIRECTION, direction).withProperty(THICKNESS, thickness);
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
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public EnumOffsetType getOffsetType() {
        return EnumOffsetType.XZ;
    }

    /** Current Java's offset: up to an eighth of a block each way, the same for a whole column. */
    @Override
    public Vec3d getOffset(IBlockState state, IBlockAccess world, BlockPos pos) {
        long i = MathHelper.getCoordinateRandom(pos.getX(), 0, pos.getZ());
        double x = MathHelper.clamp(((i & 15L) / 15.0F - 0.5) * 0.5, -0.125, 0.125);
        double z = MathHelper.clamp(((i >> 8 & 15L) / 15.0F - 0.5) * 0.5, -0.125, 0.125);
        return new Vec3d(x, 0, z);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        AxisAlignedBB shape;
        switch (state.getValue(THICKNESS)) {
            case TIP_MERGE:
                shape = TIP_MERGE;
                break;
            case TIP:
                shape = state.getValue(VERTICAL_DIRECTION) == EnumFacing.DOWN ? TIP_DOWN : TIP_UP;
                break;
            case FRUSTUM:
                shape = FRUSTUM;
                break;
            case MIDDLE:
                shape = MIDDLE;
                break;
            default:
                shape = BASE;
        }
        Vec3d off = getOffset(state, world, pos);
        return shape.offset(off.x, 0, off.z);
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.DESTROY;
    }

    /** Any tool, or none, gets the block; a pickaxe is only faster. */
    @Override
    public boolean canHarvestBlock(IBlockAccess world, BlockPos pos, EntityPlayer player) {
        return true;
    }

    // ------------------------------------------------------------------------------- queries

    public static boolean isPointed(IBlockState state) {
        return state.getBlock() instanceof BlockPointedDripstone;
    }

    static boolean isPointedWithDirection(IBlockState state, EnumFacing direction) {
        return isPointed(state) && state.getValue(VERTICAL_DIRECTION) == direction;
    }

    static boolean isStalactite(IBlockState state) {
        return isPointedWithDirection(state, EnumFacing.DOWN);
    }

    static boolean isTip(IBlockState state, boolean allowMerged) {
        if (!isPointed(state)) {
            return false;
        }
        Thickness t = state.getValue(THICKNESS);
        return t == Thickness.TIP || allowMerged && t == Thickness.TIP_MERGE;
    }

    static boolean isUnmergedTipWithDirection(IBlockState state, EnumFacing direction) {
        return isTip(state, false) && state.getValue(VERTICAL_DIRECTION) == direction;
    }

    /** @return whether this is a stalactite's free tip, the only segment that drips */
    public static boolean canDrip(IBlockState state) {
        return isStalactite(state) && state.getValue(THICKNESS) == Thickness.TIP;
    }

    /** @return whether a segment pointing {@code direction} at {@code pos} has something to hold on to */
    public static boolean isValidPlacement(IBlockAccess world, BlockPos pos, EnumFacing direction) {
        BlockPos behind = pos.offset(direction.getOpposite());
        IBlockState s = world.getBlockState(behind);
        return s.getBlockFaceShape(world, behind, direction) == BlockFaceShape.SOLID || isPointedWithDirection(s, direction);
    }

    public boolean canSurvive(IBlockAccess world, BlockPos pos, IBlockState state) {
        return isValidPlacement(world, pos, state.getValue(VERTICAL_DIRECTION));
    }

    /** The thickness a segment pointing {@code direction} at {@code pos} should have, from its two neighbors. */
    static Thickness calculateThickness(IBlockAccess world, BlockPos pos, EnumFacing direction, boolean mergeTips) {
        EnumFacing opposite = direction.getOpposite();
        IBlockState ahead = world.getBlockState(pos.offset(direction));
        if (isPointedWithDirection(ahead, opposite)) {
            return !mergeTips && ahead.getValue(THICKNESS) != Thickness.TIP_MERGE ? Thickness.TIP : Thickness.TIP_MERGE;
        }
        if (!isPointedWithDirection(ahead, direction)) {
            return Thickness.TIP;
        }
        Thickness aheadThickness = ahead.getValue(THICKNESS);
        if (aheadThickness == Thickness.TIP || aheadThickness == Thickness.TIP_MERGE) {
            return Thickness.FRUSTUM;
        }
        return isPointedWithDirection(world.getBlockState(pos.offset(opposite)), direction) ? Thickness.MIDDLE : Thickness.BASE;
    }

    /** Walks a column: @return the first position whose state is the target, or null when the path ends first. */
    @Nullable
    private static BlockPos findVertical(World world, BlockPos from, EnumFacing direction, int maxIterations, boolean dripPath,
                                         @Nullable EnumFacing pathDirection, Target target) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(from);
        for (int i = 1; i < maxIterations; i++) {
            m.move(direction);
            IBlockState s = world.getBlockState(m);
            if (target.test(s)) {
                return m.toImmutable();
            }
            if (world.isOutsideBuildHeight(m)) {
                return null;
            }
            if (dripPath ? !canDripThrough(world, m, s) : !isPointedWithDirection(s, pathDirection)) {
                return null;
            }
        }
        return null;
    }

    private interface Target {
        boolean test(IBlockState state);
    }

    @Nullable
    static BlockPos findTip(IBlockState state, World world, BlockPos pos, int maxIterations, final boolean allowMerged) {
        if (isTip(state, allowMerged)) {
            return pos;
        }
        EnumFacing direction = state.getValue(VERTICAL_DIRECTION);
        return findVertical(world, pos, direction, maxIterations, false, direction, new Target() {
            @Override
            public boolean test(IBlockState s) {
                return isTip(s, allowMerged);
            }
        });
    }

    /** @return the block a column grows from (the first that is not pointed dripstone), or null if too far */
    @Nullable
    static BlockPos findRoot(World world, BlockPos pos, IBlockState state, int maxIterations) {
        EnumFacing direction = state.getValue(VERTICAL_DIRECTION);
        return findVertical(world, pos, direction.getOpposite(), maxIterations, false, direction, new Target() {
            @Override
            public boolean test(IBlockState s) {
                return !isPointed(s);
            }
        });
    }

    private static boolean isStalactiteStart(IBlockState state, World world, BlockPos pos) {
        return isStalactite(state) && !isPointed(world.getBlockState(pos.up()));
    }

    /** @return a still source of water or lava, or NONE */
    static Fluid sourceFluid(IBlockState state) {
        if (state.getBlock() instanceof BlockLiquid && state.getValue(BlockLiquid.LEVEL) == 0) {
            if (state.getMaterial() == Material.WATER) {
                return Fluid.WATER;
            }
            if (state.getMaterial() == Material.LAVA) {
                return Fluid.LAVA;
            }
        }
        return Fluid.NONE;
    }

    /**
     * @return the source above the block a stalactite hangs from: NONE when there is none there, null when
     * this is no stalactite or its root is more than ten segments away
     */
    @Nullable
    static Fluid fluidAbove(World world, BlockPos pos, IBlockState state) {
        if (!isStalactite(state)) {
            return null;
        }
        BlockPos root = findRoot(world, pos, state, MAX_DRIP_LENGTH);
        return root == null ? null : sourceFluid(world.getBlockState(root.up()));
    }

    /** @return whether a drip can fall through this block: air, or anything that leaves the column's middle open */
    static boolean canDripThrough(World world, BlockPos pos, IBlockState state) {
        if (state.getBlock().isAir(state, world, pos)) {
            return true;
        }
        if (state.isOpaqueCube() || state.getMaterial().isLiquid()) {
            return false;
        }
        List<AxisAlignedBB> hit = new ArrayList<AxisAlignedBB>();
        state.addCollisionBoxToList(world, pos, DRIP_SPACE.offset(pos), hit, null, false);
        return hit.isEmpty();
    }

    /** @return whether a drip of this fluid does something to this block */
    static boolean canReceiveDrip(IBlockState state, Fluid fluid) {
        if (state.getBlock() != Blocks.CAULDRON) {
            return false;
        }
        return fluid == Fluid.WATER || fluid == Fluid.LAVA && state.getValue(BlockCauldron.LEVEL) == 0;
    }

    @Nullable
    private static BlockPos findCauldronBelow(World world, BlockPos tip, final Fluid fluid) {
        return findVertical(world, tip, EnumFacing.DOWN, MAX_DRIP_LENGTH, true, null, new Target() {
            @Override
            public boolean test(IBlockState s) {
                return canReceiveDrip(s, fluid);
            }
        });
    }

    /** @return the dripping tip up to ten blocks above a cauldron with a clear path, or null */
    @Nullable
    static BlockPos findTipAboveCauldron(World world, BlockPos cauldron) {
        return findVertical(world, cauldron, EnumFacing.UP, MAX_DRIP_LENGTH, true, null, new Target() {
            @Override
            public boolean test(IBlockState s) {
                return canDrip(s);
            }
        });
    }

    // ------------------------------------------------------------------------------- placing

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos)
                && (isValidPlacement(world, pos, EnumFacing.UP) || isValidPlacement(world, pos, EnumFacing.DOWN));
    }

    /**
     * The state a segment gets when placed: pointing away from where the placer looks (looking down makes a
     * stalagmite) if it can hold on that way, else the other way.
     */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer, EnumHand hand) {
        EnumFacing preferred = placer.rotationPitch > 0 ? EnumFacing.UP : EnumFacing.DOWN;
        return placementState(world, pos, preferred, !placer.isSneaking());
    }

    public IBlockState placementState(IBlockAccess world, BlockPos pos, EnumFacing preferred, boolean mergeTips) {
        EnumFacing direction = isValidPlacement(world, pos, preferred) || !isValidPlacement(world, pos, preferred.getOpposite())
                ? preferred : preferred.getOpposite();
        return state(direction, calculateThickness(world, pos, direction, mergeTips));
    }

    // ------------------------------------------------------------------------------- support and thickness

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (world.isRemote || from.getX() != pos.getX() || from.getZ() != pos.getZ()) {
            return;
        }
        EnumFacing direction = state.getValue(VERTICAL_DIRECTION);
        if (direction == EnumFacing.DOWN && world.isUpdateScheduled(pos, this)) {
            return;
        }
        if (from.equals(pos.offset(direction.getOpposite())) && !canSurvive(world, pos, state)) {
            // A stalagmite breaks next tick; a stalactite waits one more, as current Java.
            world.scheduleUpdate(pos, this, direction == EnumFacing.DOWN ? 2 : 1);
            return;
        }
        Thickness now = calculateThickness(world, pos, direction, state.getValue(THICKNESS) == Thickness.TIP_MERGE);
        if (now != state.getValue(THICKNESS)) {
            world.setBlockState(pos, state.withProperty(THICKNESS, now), 3);
        }
    }

    /** A scheduled tick: the support went. */
    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (world.isRemote || canSurvive(world, pos, state)) {
            return;
        }
        if (state.getValue(VERTICAL_DIRECTION) == EnumFacing.UP) {
            world.destroyBlock(pos, true);
        } else {
            spawnFallingStalactite(state, world, pos);
        }
    }

    /** Turns a stalactite, from {@code pos} down to its tip, into falling entities; only the tip hurts. */
    static void spawnFallingStalactite(IBlockState state, World world, BlockPos pos) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(pos);
        IBlockState s = state;
        while (isStalactite(s)) {
            EntityFallingStalactite falling = new EntityFallingStalactite(world, m.getX() + 0.5, m.getY(), m.getZ() + 0.5, s);
            boolean tip = isTip(s, true);
            if (tip) {
                // Current Java: the damage per block fallen is the stalactite's length, at least 6, up to 40 in all.
                falling.setHurts(Math.max(1 + pos.getY() - m.getY(), 6), 40);
            }
            world.setBlockToAir(m);
            world.spawnEntity(falling);
            if (tip) {
                break;
            }
            m.move(EnumFacing.DOWN);
            s = world.getBlockState(m);
        }
    }

    @Override
    public void onFallenUpon(World world, BlockPos pos, Entity entity, float fallDistance) {
        IBlockState state = world.getBlockState(pos);
        if (isPointed(state) && state.getValue(VERTICAL_DIRECTION) == EnumFacing.UP && state.getValue(THICKNESS) == Thickness.TIP) {
            DripstoneEvents.impale(entity, fallDistance + 2.0F, 2.0F);
        } else {
            super.onFallenUpon(world, pos, entity, fallDistance);
        }
    }

    // ------------------------------------------------------------------------------- dripping and growth

    @Override
    public void randomTick(World world, BlockPos pos, IBlockState state, Random random) {
        if (world.isRemote) {
            return;
        }
        maybeTransferFluid(state, world, pos, random.nextFloat());
        if (random.nextFloat() < GROWTH_CHANCE && isStalactiteStart(state, world, pos)) {
            growIfPossible(state, world, pos, random);
        }
    }

    /**
     * A drip from a stalactite's tip into a cauldron below: a drop shows at the tip now and the cauldron
     * gains a level when it lands, 50 ticks plus one per block of fall later.
     *
     * @param roll this random tick's roll, 0..1
     * @return whether a drip was sent
     */
    static boolean maybeTransferFluid(IBlockState state, World world, BlockPos pos, float roll) {
        if (roll > WATER_CHANCE || !isStalactiteStart(state, world, pos)) {
            return false;
        }
        Fluid fluid = fluidAbove(world, pos, state);
        if (fluid == null || fluid == Fluid.NONE || roll >= (fluid == Fluid.WATER ? WATER_CHANCE : LAVA_CHANCE)) {
            return false;
        }
        BlockPos tip = findTip(state, world, pos, MAX_DRIP_LENGTH, false);
        if (tip == null) {
            return false;
        }
        BlockPos cauldron = findCauldronBelow(world, tip, fluid);
        if (cauldron == null) {
            return false;
        }
        if (world instanceof WorldServer) {
            Vec3d off = state.getOffset(world, tip);
            ((WorldServer) world).spawnParticle(fluid == Fluid.LAVA ? EnumParticleTypes.DRIP_LAVA : EnumParticleTypes.DRIP_WATER,
                    tip.getX() + 0.5 + off.x, tip.getY() + 0.25, tip.getZ() + 0.5 + off.z, 1, 0, 0, 0, 0.0);
        }
        DripstoneEvents.scheduleDrip(world, cauldron, 50 + tip.getY() - cauldron.getY());
        return true;
    }

    /**
     * Grows the stalactite whose topmost segment is at {@code pos}, or the stalagmite under its tip, by one:
     * only hanging from a dripstone block with a water source above it, and only while its tip is free and at
     * most seven segments down.
     */
    static void growIfPossible(IBlockState state, World world, BlockPos pos, Random random) {
        IBlockState root = world.getBlockState(pos.up());
        if (root.getBlock() != DripstoneContent.dripstone || sourceFluid(world.getBlockState(pos.up(2))) != Fluid.WATER) {
            return;
        }
        BlockPos tip = findTip(state, world, pos, MAX_GROWTH_LENGTH, false);
        if (tip == null) {
            return;
        }
        IBlockState tipState = world.getBlockState(tip);
        if (canDrip(tipState) && canTipGrow(tipState, world, tip)) {
            if (random.nextBoolean()) {
                grow(world, tip, EnumFacing.DOWN);
            } else {
                growStalagmiteBelow(world, tip);
            }
        }
    }

    private static boolean canTipGrow(IBlockState tipState, World world, BlockPos tip) {
        EnumFacing direction = tipState.getValue(VERTICAL_DIRECTION);
        BlockPos aheadPos = tip.offset(direction);
        IBlockState ahead = world.getBlockState(aheadPos);
        if (ahead.getMaterial().isLiquid()) {
            return false;
        }
        return ahead.getBlock().isAir(ahead, world, aheadPos) || isUnmergedTipWithDirection(ahead, direction.getOpposite());
    }

    private static void growStalagmiteBelow(World world, BlockPos tip) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(tip);
        for (int i = 0; i < MAX_STALAGMITE_SEARCH; i++) {
            m.move(EnumFacing.DOWN);
            IBlockState s = world.getBlockState(m);
            if (s.getMaterial().isLiquid()) {
                return;
            }
            if (isUnmergedTipWithDirection(s, EnumFacing.UP) && canTipGrow(s, world, m)) {
                grow(world, m, EnumFacing.UP);
                return;
            }
            if (isValidPlacement(world, m, EnumFacing.UP) && world.getBlockState(m.down()).getMaterial() != Material.WATER) {
                grow(world, m.down(), EnumFacing.UP);
                return;
            }
            if (!canDripThrough(world, m, s)) {
                return;
            }
        }
    }

    private static void grow(World world, BlockPos tip, EnumFacing direction) {
        BlockPos target = tip.offset(direction);
        IBlockState there = world.getBlockState(target);
        BlockPointedDripstone block = DripstoneContent.pointed;
        if (isUnmergedTipWithDirection(there, direction.getOpposite())) {
            BlockPos down = there.getValue(VERTICAL_DIRECTION) == EnumFacing.UP ? target.up() : target;
            world.setBlockState(down, block.state(EnumFacing.DOWN, Thickness.TIP_MERGE), 3);
            world.setBlockState(down.down(), block.state(EnumFacing.UP, Thickness.TIP_MERGE), 3);
        } else if (there.getBlock().isAir(there, world, target)) {
            world.setBlockState(target, block.state(direction, Thickness.TIP), 3);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        if (!canDrip(state)) {
            return;
        }
        float roll = rand.nextFloat();
        if (roll > 0.12F) {
            return;
        }
        Fluid fluid = fluidAbove(world, pos, state);
        if (fluid == null || roll >= 0.02F && fluid == Fluid.NONE) {
            return;
        }
        // With nothing above, a tip drips what its dimension would: lava where water boils away.
        boolean lava = fluid == Fluid.LAVA || fluid == Fluid.NONE && world.provider.doesWaterVaporize();
        Vec3d off = state.getOffset(world, pos);
        com.exiledradio.cavesandcliffsbackport.dripstone.client.DripstoneClient.drip(world,
                pos.getX() + 0.5 + off.x, pos.getY() + 0.25, pos.getZ() + 0.5 + off.z, lava);
    }
}
