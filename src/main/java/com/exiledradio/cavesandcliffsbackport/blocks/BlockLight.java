package com.exiledradio.cavesandcliffsbackport.blocks;

import com.exiledradio.backportcore.waterlog.Waterloggable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * The light block: an invisible, intangible block that gives off light at a level from 0 to 15.
 *
 * <h2>Behavior (current Java)</h2>
 * <ul>
 *   <li>Nothing collides with it and nothing can target it, except a player whose main hand holds a light:
 *       that player sees its outline and, in creative mode, a marker showing its level.</li>
 *   <li>Using it raises the level by one, 15 wrapping to 0, for a creative-mode operator only.</li>
 *   <li>It cannot be mined, pushed or blown up. Another block placed into it replaces it, a falling block
 *       passes through it, and one that lands in it replaces it.</li>
 *   <li>Flowing water and lava do not pass through it.</li>
 * </ul>
 *
 * <h2>How, in 1.12.2</h2>
 * The material is the structure void's: not solid, no shadow, and one of the few a flowing liquid treats as
 * blocked. It is not replaceable and breaks when pushed, so both of those are overridden here.
 *
 * <p>A block's ray trace is not told who is asking, so the question "does this player hold a light" goes to
 * {@link #client}, which only a client sets. A server never targets a light block by itself: it acts on the
 * position a click packet names.
 */
public final class BlockLight extends Block implements Waterloggable {

    public static final PropertyInteger LEVEL = PropertyInteger.create("level", 0, 15);

    /** What only a client can answer or do. Null on a dedicated server. */
    public interface ClientHook {

        /** @return whether the calling thread is the client's and its player holds a light in the main hand */
        boolean holding();

        /** Shows the marker of a light block at this level, if this player should see one. */
        void display(World world, BlockPos pos, int level);
    }

    @Nullable
    public static ClientHook client;

    /** Set while a light block is taken out of the world for a moment to let a falling block start. */
    private static boolean passing;

    public BlockLight() {
        super(Material.STRUCTURE_VOID);
        setBlockUnbreakable();
        setResistance(6000001.0F);
        disableStats();
        this.translucent = true;
        setDefaultState(this.blockState.getBaseState().withProperty(LEVEL, 15));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, LEVEL);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(LEVEL, meta & 15);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(LEVEL);
    }

    @Override
    public int getLightValue(IBlockState state) {
        return state.getValue(LEVEL);
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(LEVEL);
    }

    // ---- invisible and intangible ----

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public float getAmbientOcclusionLightValue(IBlockState state) {
        return 1.0F;
    }

    /** Only the player holding a light can point at one. */
    @Override
    public boolean canCollideCheck(IBlockState state, boolean hitIfLiquid) {
        ClientHook hook = client;
        return hook != null && hook.holding();
    }

    // ---- cannot be mined, pushed or destroyed; can be built into ----

    @Override
    public boolean isReplaceable(IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
    }

    @Override
    public void dropBlockAsItemWithChance(World world, BlockPos pos, IBlockState state, float chance, int fortune) {
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return ItemLight.withLevel(new ItemStack(this), state.getValue(LEVEL));
    }

    // ---- the level ----

    /** Whether this player may change a light block's level: a creative-mode operator holding a light. */
    public static boolean canAdjust(EntityPlayer player) {
        return player.canUseCommandBlock() && player.getHeldItemMainhand().getItem() instanceof ItemLight;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && canAdjust(player)) {
            world.setBlockState(pos, state.cycleProperty(LEVEL), 2);
        }
        // Always used up, as in current Java: clicking a light block never places the light in hand beside it.
        return true;
    }

    // ---- falling blocks pass through ----

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        letFall(world, pos, state);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        letFall(world, pos, state);
    }

    /**
     * Starts the fall of a sand-like block resting on this light.
     *
     * <p>1.12.2's falling blocks ask one fixed question about what is under them (air, fire or liquid), and
     * a light block is none of those. So for the length of the falling block's own update the light is
     * swapped for air, silently: no neighbor updates, nothing sent to clients. The falling block then falls
     * through the light, which has no collision, and takes its place only if it comes to rest in it.
     */
    private static void letFall(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote || passing) {
            return;
        }
        BlockPos up = pos.up();
        IBlockState above = world.getBlockState(up);
        if (!(above.getBlock() instanceof BlockFalling)) {
            return;
        }
        passing = true;
        try {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 4 | 16);
            above.getBlock().updateTick(world, up, above, world.rand);
            if (world.isAirBlock(pos)) {
                world.setBlockState(pos, state, 4 | 16);
            }
        } finally {
            passing = false;
        }
    }

    // ---- the marker ----

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        ClientHook hook = client;
        if (hook != null) {
            hook.display(world, pos, state.getValue(LEVEL));
        }
    }
}
