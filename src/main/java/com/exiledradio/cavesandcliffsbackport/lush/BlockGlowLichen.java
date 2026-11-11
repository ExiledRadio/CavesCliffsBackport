package com.exiledradio.cavesandcliffsbackport.lush;

import net.minecraft.block.Block;
import net.minecraft.block.IGrowable;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.IShearable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Glow lichen: a thin growth on any of the six faces of its space, each needing a solid face behind it; light
 * 7; replaceable; dropped only for shears (one per face); bone meal spreads it to a neighboring face.
 *
 * <h2>Four blocks, one item</h2>
 * Six faces are 64 combinations and 1.12 gives a block 16 metadata values. So there are four blocks that
 * differ in whether they have the up and the down face ({@code glow_lichen}, {@code glow_lichen_up},
 * {@code glow_lichen_down}, {@code glow_lichen_up_down}), each keeping its four side faces in metadata. No
 * tile entity: lichen is on most cave walls. Everything outside this class goes through {@link #of(int)} and
 * {@link #faces(IBlockState)}, which speak in one six-bit face mask (bit = {@link EnumFacing#getIndex()}),
 * and all four share the item of the first.
 */
public class BlockGlowLichen extends BlockLushPlant implements IGrowable, IShearable {

    public static final PropertyBool NORTH = PropertyBool.create("north");
    public static final PropertyBool SOUTH = PropertyBool.create("south");
    public static final PropertyBool WEST = PropertyBool.create("west");
    public static final PropertyBool EAST = PropertyBool.create("east");
    private static final PropertyBool[] SIDES = {NORTH, SOUTH, WEST, EAST};
    private static final EnumFacing[] SIDE_FACES = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    /** The four blocks, indexed by (down ? 1 : 0) | (up ? 2 : 0). Filled as they are constructed. */
    private static final BlockGlowLichen[] BLOCKS = new BlockGlowLichen[4];

    /** The registry name of each of the four, in {@link #BLOCKS} order. */
    public static final String[] NAMES = {"glow_lichen", "glow_lichen_down", "glow_lichen_up", "glow_lichen_up_down"};

    private static final AxisAlignedBB[] FACE_BOXES = new AxisAlignedBB[6];

    static {
        FACE_BOXES[EnumFacing.DOWN.getIndex()] = new AxisAlignedBB(0, 0, 0, 1, 0.0625, 1);
        FACE_BOXES[EnumFacing.UP.getIndex()] = new AxisAlignedBB(0, 0.9375, 0, 1, 1, 1);
        FACE_BOXES[EnumFacing.NORTH.getIndex()] = new AxisAlignedBB(0, 0, 0, 1, 1, 0.0625);
        FACE_BOXES[EnumFacing.SOUTH.getIndex()] = new AxisAlignedBB(0, 0, 0.9375, 1, 1, 1);
        FACE_BOXES[EnumFacing.WEST.getIndex()] = new AxisAlignedBB(0, 0, 0, 0.0625, 1, 1);
        FACE_BOXES[EnumFacing.EAST.getIndex()] = new AxisAlignedBB(0.9375, 0, 0, 1, 1, 1);
    }

    private final int vertical;

    /** @param index 0-3, the place in {@link #NAMES} */
    public BlockGlowLichen(int index) {
        super(Material.VINE, MapColor.CYAN, SoundType.PLANT, 0.2F);
        this.vertical = index;
        BLOCKS[index] = this;
        setDefaultState(this.blockState.getBaseState().withProperty(NORTH, false).withProperty(SOUTH, false)
                .withProperty(WEST, false).withProperty(EAST, false));
    }

    // ---------------------------------------------------------------------------------------------
    // Faces

    /** @return the state with exactly these faces, or air for none */
    public static IBlockState of(int mask) {
        mask &= 63;
        if (mask == 0) {
            return Blocks.AIR.getDefaultState();
        }
        IBlockState s = BLOCKS[mask & 3].getDefaultState();
        for (int i = 0; i < 4; i++) {
            s = s.withProperty(SIDES[i], (mask & (1 << SIDE_FACES[i].getIndex())) != 0);
        }
        return s;
    }

    /** @return the six-bit face mask of a state; 0 if it is not glow lichen */
    public static int faces(IBlockState state) {
        if (!(state.getBlock() instanceof BlockGlowLichen)) {
            return 0;
        }
        int mask = ((BlockGlowLichen) state.getBlock()).vertical;
        for (int i = 0; i < 4; i++) {
            if (state.getValue(SIDES[i])) {
                mask |= 1 << SIDE_FACES[i].getIndex();
            }
        }
        return mask;
    }

    public static boolean has(int mask, EnumFacing face) {
        return (mask & (1 << face.getIndex())) != 0;
    }

    /** @return true if lichen in {@code pos} can hold on to the block on its {@code face} side */
    public static boolean supported(World world, BlockPos pos, EnumFacing face) {
        BlockPos behind = pos.offset(face);
        return world.getBlockState(behind).getBlockFaceShape(world, behind, face.getOpposite()) == BlockFaceShape.SOLID;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, NORTH, SOUTH, WEST, EAST);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        IBlockState s = getDefaultState();
        for (int i = 0; i < 4; i++) {
            s = s.withProperty(SIDES[i], (meta & (1 << i)) != 0);
        }
        return s;
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = 0;
        for (int i = 0; i < 4; i++) {
            if (state.getValue(SIDES[i])) {
                meta |= 1 << i;
            }
        }
        return meta;
    }

    private static IBlockState turned(IBlockState state, java.util.function.Function<EnumFacing, EnumFacing> turn) {
        int mask = faces(state), out = mask & 3;
        for (EnumFacing f : SIDE_FACES) {
            if (has(mask, f)) {
                out |= 1 << turn.apply(f).getIndex();
            }
        }
        return of(out);
    }

    @Override
    public IBlockState withRotation(IBlockState state, final Rotation rot) {
        return turned(state, rot::rotate);
    }

    @Override
    public IBlockState withMirror(IBlockState state, final Mirror mirror) {
        return turned(state, mirror::mirror);
    }

    // ---------------------------------------------------------------------------------------------
    // Block

    @Override
    public boolean canStay(World world, BlockPos pos, IBlockState state) {
        return faces(state) != 0;
    }

    /** As an item it can go wherever one of the six neighbors offers a solid face ({@link ItemGlowLichen}). */
    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        if (!world.getBlockState(pos).getBlock().isReplaceable(world, pos)) {
            return false;
        }
        for (EnumFacing f : EnumFacing.values()) {
            if (supported(world, pos, f)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isReplaceable(IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return 7;
    }

    /** A face whose support goes is lost; the last one takes the block with it. */
    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        int mask = faces(state), keep = 0;
        for (EnumFacing f : EnumFacing.values()) {
            if (has(mask, f) && supported(world, pos, f)) {
                keep |= 1 << f.getIndex();
            }
        }
        if (keep != mask) {
            world.setBlockState(pos, of(keep), 3);
        }
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        int mask = faces(state);
        AxisAlignedBB box = null;
        for (EnumFacing f : EnumFacing.values()) {
            if (has(mask, f)) {
                box = box == null ? FACE_BOXES[f.getIndex()] : box.union(FACE_BOXES[f.getIndex()]);
            }
        }
        return box == null ? FULL_BLOCK_AABB : box;
    }

    @Nullable
    @Override
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end) {
        int mask = faces(state);
        RayTraceResult best = null;
        double bestDistance = Double.MAX_VALUE;
        for (EnumFacing f : EnumFacing.values()) {
            if (has(mask, f)) {
                RayTraceResult hit = rayTrace(pos, start, end, FACE_BOXES[f.getIndex()]);
                if (hit != null) {
                    double d = hit.hitVec.squareDistanceTo(start);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = hit;
                    }
                }
            }
        }
        return best;
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(BLOCKS[0]);
    }

    @Override
    public boolean isShearable(ItemStack item, IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    public List<ItemStack> onSheared(ItemStack item, IBlockAccess world, BlockPos pos, int fortune) {
        int n = Integer.bitCount(faces(world.getBlockState(pos)));
        return n == 0 ? Collections.<ItemStack>emptyList() : Collections.singletonList(new ItemStack(BLOCKS[0], n));
    }

    // ---------------------------------------------------------------------------------------------
    // Spreading

    /**
     * Every way this lichen could spread by one face, as {position, face} pairs: for each face it has and each
     * direction along that face, another face of its own space, the same face of the next space over, or the
     * face around the corner.
     */
    public static List<Object[]> spreads(World world, BlockPos pos, int mask) {
        List<Object[]> out = new ArrayList<Object[]>();
        for (EnumFacing f : EnumFacing.values()) {
            if (!has(mask, f)) {
                continue;
            }
            for (EnumFacing d : EnumFacing.values()) {
                if (d.getAxis() == f.getAxis()) {
                    continue;
                }
                if (canAdd(world, pos, d)) {
                    out.add(new Object[] {pos, d});
                }
                BlockPos side = pos.offset(d);
                if (canAdd(world, side, f)) {
                    out.add(new Object[] {side, f});
                }
                if (open(world.getBlockState(side))) {
                    BlockPos corner = side.offset(f);
                    if (canAdd(world, corner, d.getOpposite())) {
                        out.add(new Object[] {corner, d.getOpposite()});
                    }
                }
            }
        }
        return out;
    }

    private static boolean open(IBlockState s) {
        return s.getBlock() == Blocks.AIR || s.getBlock() instanceof BlockGlowLichen;
    }

    /** @return true if the space is air or lichen without that face, and the face has something to hold */
    public static boolean canAdd(World world, BlockPos pos, EnumFacing face) {
        IBlockState s = world.getBlockState(pos);
        return pos.getY() > 0 && pos.getY() < 256 && open(s) && !has(faces(s), face) && supported(world, pos, face);
    }

    /** Adds one face to whatever lichen is (or is not yet) at a position. */
    public static void add(World world, BlockPos pos, EnumFacing face, int flags) {
        world.setBlockState(pos, of(faces(world.getBlockState(pos)) | (1 << face.getIndex())), flags);
    }

    @Override
    public boolean canGrow(World world, BlockPos pos, IBlockState state, boolean isClient) {
        return !spreads(world, pos, faces(state)).isEmpty();
    }

    @Override
    public boolean canUseBonemeal(World world, Random rand, BlockPos pos, IBlockState state) {
        return true;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, IBlockState state) {
        List<Object[]> options = spreads(world, pos, faces(state));
        if (!options.isEmpty()) {
            Object[] pick = options.get(rand.nextInt(options.size()));
            add(world, (BlockPos) pick[0], (EnumFacing) pick[1], 3);
        }
    }
}
