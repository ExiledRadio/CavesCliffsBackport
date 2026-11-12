package com.exiledradio.cavesandcliffsbackport.powdersnow;

import com.exiledradio.cavesandcliffsbackport.common.BlockBasic;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * Powder snow: a block entities fall into.
 *
 * <h2>Collision (current Java)</h2>
 * 1.12 asks a block for its collision boxes with the entity that is moving, so the block can answer per
 * entity, as Java's does:
 * <ul>
 *   <li>an entity that has fallen more than 2.5 blocks lands on a box 0.9 high (and takes no fall
 *       damage: {@link #onFallenUpon}), then sinks in;</li>
 *   <li>falling blocks, and anything that can walk on powder snow ({@link Freezing#canWalkOn}) while it is
 *       above the block and not sneaking, get the full cube;</li>
 *   <li>everything else gets nothing.</li>
 * </ul>
 *
 * <h2>Sinking</h2>
 * Java multiplies an entity's movement inside the block by (0.9, 1.5, 0.9) and clears its velocity each
 * tick. 1.12 has that only for cobwebs, with their numbers, so here the velocity is reset after each move
 * to the value that gives Java's sinking speed on the next one ({@link #stick}).
 */
public class BlockPowderSnow extends BlockBasic {

    /** Java's powder snow material: snow-colored, not solid, no collision, not replaceable. */
    public static final Material MATERIAL = new Material(MapColor.SNOW) {
        @Override
        public boolean isSolid() {
            return false;
        }

        @Override
        public boolean blocksLight() {
            return false;
        }

        @Override
        public boolean blocksMovement() {
            return false;
        }
    };

    private static final AxisAlignedBB FALLING_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.9D, 1.0D);

    public BlockPowderSnow(SoundType sound) {
        super(MATERIAL, MapColor.SNOW, sound, 0.25F, 0.25F);
        setLightOpacity(1);
    }

    // ---- shape -------------------------------------------------------------------------------

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
        return BlockFaceShape.UNDEFINED;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean isActualState) {
        if (entity == null) {
            return;
        }
        if (entity.fallDistance > 2.5F) {
            addCollisionBoxToList(pos, entityBox, boxes, FALLING_AABB);
        } else if (entity instanceof EntityFallingBlock
                || (Freezing.canWalkOn(entity) && entity.posY > pos.getY() + 1 - 1.0E-5D && !entity.isSneaking())) {
            addCollisionBoxToList(pos, entityBox, boxes, FULL_BLOCK_AABB);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return world.getBlockState(pos.offset(side)).getBlock() != this && super.shouldSideBeRendered(state, world, pos, side);
    }

    // ---- entities ----------------------------------------------------------------------------

    @Override
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (!(entity instanceof EntityLivingBase) || feetIn(entity)) {
            stick(entity);
            if (world.isRemote) {
                Random random = world.rand;
                boolean moved = entity.lastTickPosX != entity.posX || entity.lastTickPosZ != entity.posZ;
                if (moved && random.nextBoolean()) {
                    world.spawnParticle(EnumParticleTypes.SNOW_SHOVEL, entity.posX, pos.getY() + 1, entity.posZ,
                            (random.nextFloat() * 2 - 1) / 12.0F, 0.05D, (random.nextFloat() * 2 - 1) / 12.0F);
                }
            }
        }
        if (entity instanceof EntityLivingBase) {
            Freezing.markInSnow((EntityLivingBase) entity);
        }
        if (!world.isRemote && entity.isBurning()) {
            if ((world.getGameRules().getBoolean("mobGriefing") || entity instanceof EntityPlayer)
                    && (!(entity instanceof EntityPlayer) || world.isBlockModifiable((EntityPlayer) entity, pos))) {
                world.destroyBlock(pos, false);
            }
            entity.extinguish();
        }
    }

    /** @return whether the block an entity's feet are in is powder snow */
    public static boolean feetIn(Entity entity) {
        return entity.world.getBlockState(new BlockPos(entity)).getBlock() instanceof BlockPowderSnow;
    }

    /**
     * Java's "stuck in block": no fall distance, and the next move goes at (0.9, 1.5, 0.9) of what was asked
     * from a standstill.
     *
     * <p>This runs at the end of a move. A living entity then takes gravity ((v - 0.08) * 0.98) before it
     * moves again, an item takes it (v - 0.04) at the start of its next tick, so a velocity of -0.04 and
     * -0.02 makes the next fall Java's 1.5 times one tick of gravity. Sideways the velocity is cleared, so
     * an entity moves only by what it pushes in one tick.
     */
    private static void stick(Entity entity) {
        entity.fallDistance = 0.0F;
        entity.motionX = 0.0D;
        entity.motionZ = 0.0D;
        if (entity instanceof EntityLivingBase) {
            entity.motionY = -0.04D;
        } else if (entity instanceof EntityItem) {
            entity.motionY = -0.02D;
        } else if (!entity.hasNoGravity()) {
            entity.motionY = 0.0D;
        }
    }

    /** No fall damage; the landing sound from 4 blocks up, as in Java. */
    @Override
    public void onFallenUpon(World world, BlockPos pos, Entity entity, float fallDistance) {
        if (fallDistance >= 4.0F && entity instanceof EntityLivingBase && !world.isRemote) {
            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    fallDistance < 7.0F ? Freezing.smallFall(entity) : Freezing.bigFall(entity),
                    entity.getSoundCategory(), 1.0F, 1.0F);
        }
    }

    /**
     * Mobs do not path into powder snow, or over it: it does not hold them. Those that walk on it see a
     * solid block.
     */
    @Nullable
    @Override
    public PathNodeType getAiPathNodeType(IBlockState state, IBlockAccess world, BlockPos pos, @Nullable EntityLiving entity) {
        if (entity != null && Freezing.canWalkOn(entity)) {
            return PathNodeType.BLOCKED;
        }
        return PathNodeType.DAMAGE_OTHER;
    }

    // ---- drops -------------------------------------------------------------------------------

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.AIR;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
    }

    @Override
    public boolean canSilkHarvest(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        return false;
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(PowderSnowContent.bucket);
    }

    @Override
    public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
        return new ItemStack(PowderSnowContent.bucket);
    }

    /** Takes the block as a bucket would: gone, with its break effect. */
    public static void pickUp(World world, BlockPos pos, IBlockState state) {
        world.setBlockState(pos, net.minecraft.init.Blocks.AIR.getDefaultState(), 11);
        if (!world.isRemote) {
            world.playEvent(2001, pos, Block.getStateId(state));
        }
    }
}
