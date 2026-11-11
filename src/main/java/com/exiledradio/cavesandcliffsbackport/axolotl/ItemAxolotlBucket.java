package com.exiledradio.cavesandcliffsbackport.axolotl;

import com.exiledradio.backportcore.waterlog.Waterlogging;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;

/**
 * The bucket of axolotl. Emptying it places water as a water bucket does and lets the axolotl out with what
 * the bucket kept: its color, age, health and name. That axolotl never despawns. Dispensers empty it too.
 */
public class ItemAxolotlBucket extends Item {

    public ItemAxolotlBucket() {
        this.setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        RayTraceResult hit = this.rayTrace(world, player, false);
        ActionResult<ItemStack> event = ForgeEventFactory.onBucketUse(player, world, stack, hit);
        if (event != null) {
            return event;
        }
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return new ActionResult<ItemStack>(EnumActionResult.PASS, stack);
        }
        BlockPos pos = hit.getBlockPos();
        if (!world.isBlockModifiable(player, pos)) {
            return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
        }
        boolean replaceable = world.getBlockState(pos).getBlock().isReplaceable(world, pos);
        BlockPos target = replaceable && hit.sideHit == EnumFacing.UP ? pos : pos.offset(hit.sideHit);
        if (!player.canPlayerEdit(target, hit.sideHit, stack) || !this.empty(player, world, target, stack)) {
            return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
        }
        if (player instanceof EntityPlayerMP) {
            CriteriaTriggers.PLACED_BLOCK.trigger((EntityPlayerMP) player, target, stack);
        }
        player.addStat(StatList.getObjectUseStats(this));
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, player.capabilities.isCreativeMode ? stack : new ItemStack(Items.BUCKET));
    }

    /**
     * Places water at a position, as a water bucket would, and lets the axolotl out there.
     *
     * <p>A block that can hold water (a slab, a stair) is filled if it is dry; one that already holds water just
     * takes the axolotl. In the Nether the water boils away and the axolotl is left on dry ground.
     *
     * @return false if water cannot go there; then nothing happened
     */
    public boolean empty(@Nullable EntityPlayer player, World world, BlockPos pos, ItemStack stack) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        Material material = state.getMaterial();
        if (material != Material.WATER && Waterlogging.canHoldWater(state, world, pos)) {
            if (!Waterlogging.hasWaterSource(world, pos) && !world.isRemote && !Waterlogging.setWater(world, pos, true, 11)) {
                return false;
            }
        } else {
            boolean nonSolid = !material.isSolid();
            boolean replaceable = block.isReplaceable(world, pos);
            if (!world.isAirBlock(pos) && !nonSolid && !replaceable) {
                return false;
            }
            if (world.provider.doesWaterVaporize()) {
                world.playSound(player, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F,
                        2.6F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.8F);
                for (int k = 0; k < 8; ++k) {
                    world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, pos.getX() + Math.random(), pos.getY() + Math.random(),
                            pos.getZ() + Math.random(), 0.0D, 0.0D, 0.0D);
                }
            } else {
                if (!world.isRemote && (nonSolid || replaceable) && !material.isLiquid()) {
                    world.destroyBlock(pos, true);
                }
                world.setBlockState(pos, Blocks.FLOWING_WATER.getDefaultState(), 11);
            }
        }
        world.playSound(player, pos, AxolotlContent.bucketEmpty, SoundCategory.NEUTRAL, 1.0F, 1.0F);
        if (!world.isRemote) {
            this.release(world, pos, stack, player);
        }
        return true;
    }

    /** @return the axolotl let out, already in the world, or null */
    @Nullable
    public EntityAxolotl release(World world, BlockPos pos, ItemStack stack, @Nullable EntityPlayer player) {
        EntityAxolotl entity = new EntityAxolotl(world);
        entity.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.rand.nextFloat() * 360.0F, 0.0F);
        entity.rotationYawHead = entity.rotationYaw;
        entity.renderYawOffset = entity.rotationYaw;
        entity.readBucketData(stack);
        ItemMonsterPlacer.applyItemEntityDataToEntity(world, player, stack, entity);
        return world.spawnEntity(entity) ? entity : null;
    }

    /** Dispensers empty the bucket in front of them, or drop it if water cannot go there. */
    static final class Dispense extends BehaviorDefaultDispenseItem {
        private final BehaviorDefaultDispenseItem drop = new BehaviorDefaultDispenseItem();

        @Override
        protected ItemStack dispenseStack(IBlockSource source, ItemStack stack) {
            ItemAxolotlBucket bucket = (ItemAxolotlBucket) stack.getItem();
            BlockPos pos = source.getBlockPos().offset(source.getBlockState().getValue(BlockDispenser.FACING));
            if (bucket.empty(null, source.getWorld(), pos, stack)) {
                return new ItemStack(Items.BUCKET);
            }
            return this.drop.dispense(source, stack);
        }
    }
}
