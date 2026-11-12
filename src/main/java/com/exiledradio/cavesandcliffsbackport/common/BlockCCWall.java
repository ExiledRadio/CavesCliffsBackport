package com.exiledradio.cavesandcliffsbackport.common;

import com.exiledradio.backportcore.waterlog.Waterloggable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockWall;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A wall of one of our blocks, with 1.16+'s sides (**now:** current Java): each side is none, low or
 * tall, and the post shows unless the wall runs straight through.
 *
 * <ul>
 *   <li>A side connects to walls (ours and 1.12's), fence gates, panes and iron bars, and solid faces.</li>
 *   <li>A side is tall when the block above has a solid bottom or is a wall connected the same way.</li>
 *   <li>The post is dropped only on a straight run with matching sides and nothing above that needs it
 *       (a wall post, a torch, a lantern, a sign or a banner).</li>
 * </ul>
 * Vanilla's {@link BlockWall} has a fixed cobblestone variant, so this is its own block. It reports the
 * same face shapes, so 1.12's cobblestone walls connect to ours as well. Everything is worked out in
 * {@link #getActualState}; metadata is always 0.
 */
public class BlockCCWall extends Block implements Waterloggable {

    public enum Side implements IStringSerializable {
        NONE("none"), LOW("low"), TALL("tall");

        private final String name;

        Side(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }
    }

    public static final PropertyBool UP = PropertyBool.create("up");
    public static final PropertyEnum<Side> NORTH = PropertyEnum.create("north", Side.class);
    public static final PropertyEnum<Side> EAST = PropertyEnum.create("east", Side.class);
    public static final PropertyEnum<Side> SOUTH = PropertyEnum.create("south", Side.class);
    public static final PropertyEnum<Side> WEST = PropertyEnum.create("west", Side.class);

    private static final double POST_LO = 4 / 16.0, POST_HI = 12 / 16.0;
    private static final double SIDE_LO = 5 / 16.0, SIDE_HI = 11 / 16.0;
    private static final double LOW_TOP = 14 / 16.0;
    /** Entities cannot step or jump over a wall: its collision is a block and a half tall, as vanilla's. */
    private static final double COLLISION_TOP = 1.5;

    public BlockCCWall(IBlockState full) {
        super(full.getMaterial(), full.getBlock().getMapColor(full, null, null));
        Block b = full.getBlock();
        setSoundType(b.getSoundType());
        setHardness(b.getBlockHardness(full, null, null));
        setResistance(b.getExplosionResistance(null) * 5.0F / 3.0F);
        setDefaultState(this.blockState.getBaseState().withProperty(UP, true).withProperty(NORTH, Side.NONE)
                .withProperty(EAST, Side.NONE).withProperty(SOUTH, Side.NONE).withProperty(WEST, Side.NONE));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, UP, NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    public static PropertyEnum<Side> side(EnumFacing f) {
        switch (f) {
            case NORTH:
                return NORTH;
            case EAST:
                return EAST;
            case SOUTH:
                return SOUTH;
            default:
                return WEST;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Connections

    /** @return whether this wall at {@code pos} joins what is on its {@code facing} side */
    protected boolean connects(IBlockAccess world, BlockPos pos, EnumFacing facing) {
        BlockPos other = pos.offset(facing);
        IBlockState state = world.getBlockState(other);
        Block block = state.getBlock();
        if (block instanceof BlockCCWall || block instanceof BlockWall || block instanceof BlockPane) {
            return true;
        }
        if (block.canBeConnectedTo(world, other, facing.getOpposite())) {
            return true;
        }
        BlockFaceShape shape = state.getBlockFaceShape(world, other, facing.getOpposite());
        if (shape == BlockFaceShape.MIDDLE_POLE_THICK || shape == BlockFaceShape.MIDDLE_POLE && block instanceof BlockFenceGate) {
            return true;
        }
        return shape == BlockFaceShape.SOLID && !isExceptBlockForAttachWithPiston(block)
                && block != Blocks.BARRIER && block != Blocks.MELON_BLOCK && block != Blocks.PUMPKIN && block != Blocks.LIT_PUMPKIN;
    }

    @Override
    public boolean canBeConnectedTo(IBlockAccess world, BlockPos pos, EnumFacing facing) {
        return true;
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        IBlockState above = world.getBlockState(pos.up());
        boolean aboveSolid = above.getBlockFaceShape(world, pos.up(), EnumFacing.DOWN) == BlockFaceShape.SOLID;
        boolean aboveWall = above.getBlock() instanceof BlockCCWall;
        IBlockState aboveActual = aboveWall ? above.getActualState(world, pos.up()) : above;
        Side[] sides = new Side[4];
        for (EnumFacing f : EnumFacing.HORIZONTALS) {
            Side s = Side.NONE;
            if (connects(world, pos, f)) {
                boolean tall = aboveSolid || aboveWall && aboveActual.getValue(side(f)) != Side.NONE
                        || above.getBlock() instanceof BlockWall;
                s = tall ? Side.TALL : Side.LOW;
            }
            sides[f.getHorizontalIndex()] = s;
            state = state.withProperty(side(f), s);
        }
        Side south = sides[0], west = sides[1], north = sides[2], east = sides[3];
        boolean straightNS = north != Side.NONE && north == south && east == Side.NONE && west == Side.NONE;
        boolean straightEW = east != Side.NONE && east == west && north == Side.NONE && south == Side.NONE;
        boolean post = !(straightNS || straightEW) || needsPost(world, pos.up(), above, aboveActual);
        return state.withProperty(UP, post);
    }

    /** @return true for what stands on a wall's post: another post, torches, lanterns, signs, banners, end rods */
    protected boolean needsPost(IBlockAccess world, BlockPos abovePos, IBlockState above, IBlockState aboveActual) {
        Block b = above.getBlock();
        if (b instanceof BlockCCWall) {
            return aboveActual.getValue(UP);
        }
        if (b instanceof BlockWall) {
            return true;
        }
        if (b == Blocks.TORCH || b == Blocks.REDSTONE_TORCH || b == Blocks.UNLIT_REDSTONE_TORCH
                || b == Blocks.STANDING_SIGN || b == Blocks.STANDING_BANNER || b == Blocks.END_ROD) {
            return true;
        }
        String path = b.getRegistryName() == null ? "" : b.getRegistryName().getPath();
        return path.endsWith("lantern") || path.endsWith("_torch") || path.equals("torch");
    }

    // ---------------------------------------------------------------------------------------------
    // Shapes

    private static AxisAlignedBB box(IBlockState state, double sideTop, double tallTop, double postTop) {
        double minX = SIDE_LO, maxX = SIDE_HI, minZ = SIDE_LO, maxZ = SIDE_HI, top = 0;
        if (state.getValue(UP)) {
            minX = minZ = POST_LO;
            maxX = maxZ = POST_HI;
            top = postTop;
        }
        Side n = state.getValue(NORTH), s = state.getValue(SOUTH), e = state.getValue(EAST), w = state.getValue(WEST);
        if (n != Side.NONE) {
            minZ = 0;
        }
        if (s != Side.NONE) {
            maxZ = 1;
        }
        if (w != Side.NONE) {
            minX = 0;
        }
        if (e != Side.NONE) {
            maxX = 1;
        }
        for (Side side : new Side[] {n, s, e, w}) {
            if (side == Side.LOW) {
                top = Math.max(top, sideTop);
            } else if (side == Side.TALL) {
                top = Math.max(top, tallTop);
            }
        }
        if (top == 0) {
            top = postTop;
        }
        return new AxisAlignedBB(minX, 0, minZ, maxX, top, maxZ);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return box(getActualState(state, world, pos), LOW_TOP, 1, 1);
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean isActualState) {
        IBlockState actual = isActualState ? state : getActualState(state, world, pos);
        // The post and each side as their own boxes, so a corner does not fill the whole block.
        if (actual.getValue(UP) || noSides(actual)) {
            addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(POST_LO, 0, POST_LO, POST_HI, COLLISION_TOP, POST_HI));
        }
        if (actual.getValue(NORTH) != Side.NONE) {
            addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(SIDE_LO, 0, 0, SIDE_HI, COLLISION_TOP, SIDE_HI));
        }
        if (actual.getValue(SOUTH) != Side.NONE) {
            addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(SIDE_LO, 0, SIDE_LO, SIDE_HI, COLLISION_TOP, 1));
        }
        if (actual.getValue(WEST) != Side.NONE) {
            addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(0, 0, SIDE_LO, SIDE_HI, COLLISION_TOP, SIDE_HI));
        }
        if (actual.getValue(EAST) != Side.NONE) {
            addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(SIDE_LO, 0, SIDE_LO, 1, COLLISION_TOP, SIDE_HI));
        }
    }

    private static boolean noSides(IBlockState s) {
        return s.getValue(NORTH) == Side.NONE && s.getValue(SOUTH) == Side.NONE && s.getValue(EAST) == Side.NONE
                && s.getValue(WEST) == Side.NONE;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isPassable(IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Override
    public boolean canPlaceTorchOnTop(IBlockState state, IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return face != EnumFacing.UP && face != EnumFacing.DOWN ? BlockFaceShape.MIDDLE_POLE_THICK : BlockFaceShape.CENTER_BIG;
    }

    @Override
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return side == EnumFacing.DOWN ? super.shouldSideBeRendered(state, world, pos, side) : true;
    }
}
