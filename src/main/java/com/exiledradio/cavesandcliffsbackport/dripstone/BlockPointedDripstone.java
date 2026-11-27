package com.exiledradio.cavesandcliffsbackport.dripstone;

import com.exiledradio.cavesandcliffsbackport.common.BlockBasic;

import com.exiledradio.backportcore.speleothem.BlockSpeleothem;
import com.exiledradio.backportcore.speleothem.Thickness;
import com.exiledradio.backportcore.waterlog.Waterlogging;

import net.minecraft.block.BlockCauldron;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
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
 * Pointed dripstone (26.4's {@code PointedDripstoneBlock}): a Backport Core {@link BlockSpeleothem} growing on the
 * dripstone block, shared with Game Drops' icicles since 1.2.0.
 *
 * <p>Core does the states ({@code thickness}, {@code vertical_direction}; metadata {@code thickness | (down ? 8 : 0)},
 * unchanged), shapes and offset, support, thickness, tip merging, falling ({@code backportcore:falling_speleothem}),
 * the {@code stalagmite} and {@code falling_stalactite} damage, tridents and the growth roll. This class adds what
 * only dripstone does:
 * <ul>
 *   <li>Random ticks reach the topmost segment of a stalactite: with a water or lava source above the block it hangs
 *       from (up to ten segments up), a drip may fill a cauldron up to ten blocks under the tip (45/256 water,
 *       15/256 lava), landing 50 ticks plus one per block later ({@link DripstoneEvents}).</li>
 *   <li>Growth ({@link #canGrow}) needs a water source two blocks above the topmost segment, over the dripstone
 *       block it hangs from.</li>
 *   <li>A drip only falls through blocks that leave the middle of the column free, and the stalagmite search
 *       stops at any other block ({@link #blocksStalagmiteScan}).</li>
 *   <li>Free tips drip particles: always a few, more with a source above.</li>
 * </ul>
 * Water is Core's {@code Waterloggable} (Fluidlogged API when installed): a waterlogged tip does not drip, and water
 * kept in the block above a stalactite's root fills cauldrons as a water block there would.
 */
public class BlockPointedDripstone extends BlockSpeleothem {

    /** What a stalactite drips. */
    public enum Fluid {
        NONE, WATER, LAVA
    }

    /** {@code REQUIRED_SPACE_TO_DRIP_THROUGH_NON_SOLID_BLOCK}: a drip passes a block that leaves this free. */
    private static final AxisAlignedBB DRIP_SPACE = column(4, 0, 16);

    private static final float WATER_CHANCE = 45 / 256.0F, LAVA_CHANCE = 15 / 256.0F;
    private static final int MAX_DRIP_LENGTH = 11;

    public BlockPointedDripstone(SoundType sound, IBlockState dripstoneBlock) {
        super(Material.ROCK, MapColor.BROWN_STAINED_HARDENED_CLAY, dripstoneBlock);
        setSoundType(sound);
        setHardness(1.5F);
        BlockBasic.resistance(this, 3.0F);
    }

    // ------------------------------------------------------------------------------- Core's hooks

    /** Level event 1045: {@code block.pointed_dripstone.land}. */
    @Override
    protected SoundEvent landingSound() {
        return DripstoneContent.land;
    }

    /** {@code canGrow}: on a dripstone block, under a water source (water kept in a block counts). */
    @Override
    protected boolean canGrow(World world, BlockPos pos) {
        return super.canGrow(world, pos) && Waterlogging.hasWaterSource(world, pos.up(2));
    }

    @Override
    protected boolean blocksStalagmiteScan(World world, BlockPos pos, IBlockState state) {
        return !canDripThrough(world, pos, state);
    }

    /** {@code randomTick}: a drip, then Core's growth roll. */
    @Override
    protected void onRandomTick(World world, BlockPos pos, IBlockState state, Random random) {
        maybeTransferFluid(state, world, pos, random.nextFloat());
        super.onRandomTick(world, pos, state, random);
    }

    // ------------------------------------------------------------------------------- queries

    public static boolean isPointed(IBlockState state) {
        return state.getBlock() instanceof BlockPointedDripstone;
    }

    /** @return whether this is a pointed dripstone stalactite's free tip, the only segment that drips */
    public static boolean canDrip(IBlockState state) {
        return isPointed(state) && isStalactite(state) && state.getValue(THICKNESS) == Thickness.TIP;
    }

    /** {@code isFreeHangingStalactite}, for pointed dripstone: a free tip that is not waterlogged. */
    public static boolean canDrip(IBlockAccess world, BlockPos pos, IBlockState state) {
        return isPointed(state) && isFreeHangingStalactite(world, pos, state);
    }

    /** Walks a column: @return the first position whose state is the target, or null when the path ends first. */
    @Nullable
    private static BlockPos findVertical(World world, BlockPos from, EnumFacing direction, int maxIterations, boolean dripPath,
                                         @Nullable EnumFacing pathDirection, Target target) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(from);
        for (int i = 1; i < maxIterations; i++) {
            m.move(direction);
            IBlockState s = world.getBlockState(m);
            if (target.test(s, m)) {
                return m.toImmutable();
            }
            if (world.isOutsideBuildHeight(m)) {
                return null;
            }
            if (dripPath ? !canDripThrough(world, m, s) : !(isPointed(s) && s.getValue(VERTICAL_DIRECTION) == pathDirection)) {
                return null;
            }
        }
        return null;
    }

    private interface Target {
        boolean test(IBlockState state, BlockPos pos);
    }

    /** {@code findRootBlock}: @return the block a column grows from (the first that is not pointed dripstone), or null if too far */
    @Nullable
    static BlockPos findRoot(World world, BlockPos pos, IBlockState state, int maxIterations) {
        EnumFacing direction = state.getValue(VERTICAL_DIRECTION);
        return findVertical(world, pos, direction.getOpposite(), maxIterations, false, direction, new Target() {
            @Override
            public boolean test(IBlockState s, BlockPos at) {
                return !isPointed(s);
            }
        });
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
     * {@code getFluidAboveStalactite}: @return the source above the block a stalactite hangs from: NONE when there is
     * none there, null when this is no pointed dripstone stalactite or its root is more than ten segments away
     */
    @Nullable
    public static Fluid fluidAbove(World world, BlockPos pos, IBlockState state) {
        if (!isPointed(state) || !isStalactite(state)) {
            return null;
        }
        BlockPos root = findRoot(world, pos, state, MAX_DRIP_LENGTH);
        if (root == null) {
            return null;
        }
        // 26.4 reads the fluid state there, so water kept in a block (a waterlogged slab) counts as a source.
        BlockPos above = root.up();
        return Waterlogging.hasWaterSource(world, above) ? Fluid.WATER : sourceFluid(world.getBlockState(above));
    }

    /** {@code canDripThrough}: air, or anything dry that leaves the column's middle open. */
    static boolean canDripThrough(World world, BlockPos pos, IBlockState state) {
        if (state.getBlock().isAir(state, world, pos)) {
            return true;
        }
        if (state.isOpaqueCube() || state.getMaterial().isLiquid() || Waterlogging.hasWater(world, pos)) {
            return false;
        }
        List<AxisAlignedBB> hit = new ArrayList<AxisAlignedBB>();
        state.addCollisionBoxToList(world, pos, DRIP_SPACE.offset(pos), hit, null, false);
        return hit.isEmpty();
    }

    /** {@code canReceiveStalactiteDrip}: @return whether a drip of this fluid does something to this block */
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
            public boolean test(IBlockState s, BlockPos at) {
                return canReceiveDrip(s, fluid);
            }
        });
    }

    /** {@code findStalactiteTipAboveCauldron}: @return the dripping tip up to ten blocks above a cauldron with a clear path, or null */
    @Nullable
    static BlockPos findTipAboveCauldron(final World world, BlockPos cauldron) {
        return findVertical(world, cauldron, EnumFacing.UP, MAX_DRIP_LENGTH, true, null, new Target() {
            @Override
            public boolean test(IBlockState s, BlockPos at) {
                return canDrip(world, at, s);
            }
        });
    }

    // ------------------------------------------------------------------------------- dripping

    /**
     * {@code maybeTransferFluid}: a drip from a stalactite's tip into a cauldron below: a drop shows at the tip now
     * and the cauldron gains a level when it lands, 50 ticks plus one per block of fall later.
     *
     * @param roll this random tick's roll, 0..1
     * @return whether a drip was sent
     */
    static boolean maybeTransferFluid(IBlockState state, World world, BlockPos pos, float roll) {
        if (roll > WATER_CHANCE || !isPointed(state) || !isStalactiteStart(state, world, pos)) {
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

    /** {@code animateTick}: a free tip drips now and then, more often under a source. */
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        if (!canDrip(world, pos, state)) {
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
