package com.exiledradio.cavesandcliffsbackport.candle;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;

import net.minecraft.block.BlockCake;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtils;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * What other things do to candles (current Java):
 * <ul>
 *   <li>flint and steel and fire charges light a candle or candle cake instead of starting a fire next to it
 *       (one durability, or the charge);</li>
 *   <li>a candle used on an uneaten cake puts it on the cake;</li>
 *   <li>a burning projectile lights the candle it hits;</li>
 *   <li>a splash water bottle puts out the candles where it lands.</li>
 * </ul>
 * The clicks are cancelled on both sides, so vanilla's own handling (fire, eating the cake) never runs.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class CandleEvents {

    private CandleEvents() {
    }

    @SubscribeEvent
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!CandleContent.enabled()) {
            return;
        }
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        IBlockState state = world.getBlockState(pos);
        ItemStack held = event.getItemStack();
        EntityPlayer player = event.getEntityPlayer();

        if (BlockAbstractCandle.canLight(world, pos, state)) {
            boolean flint = held.getItem() == Items.FLINT_AND_STEEL;
            if (!flint && held.getItem() != Items.FIRE_CHARGE) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
            if (world.isRemote) {
                return;
            }
            BlockAbstractCandle.light(world, pos, state);
            if (flint) {
                world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0F,
                        world.rand.nextFloat() * 0.4F + 0.8F);
                held.damageItem(1, player);
            } else {
                world.playSound(null, pos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 1.0F,
                        (world.rand.nextFloat() - world.rand.nextFloat()) * 0.2F + 1.0F);
                if (!player.capabilities.isCreativeMode) {
                    held.shrink(1);
                }
            }
            player.addStat(StatList.getObjectUseStats(held.getItem()));
            return;
        }

        if (state.getBlock() == Blocks.CAKE && state.getValue(BlockCake.BITES) == 0 && !player.isSneaking()
                && held.getItem() instanceof ItemCandle) {
            BlockCandleCake cake = CandleContent.cakeFor(((ItemBlock) held.getItem()).getBlock());
            if (cake == null) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
            if (world.isRemote) {
                return;
            }
            world.setBlockState(pos, cake.getDefaultState(), 3);
            world.playSound(null, pos, CandleContent.addCandle, SoundCategory.BLOCKS, 1.0F, 1.0F);
            player.addStat(StatList.getObjectUseStats(held.getItem()));
            if (!player.capabilities.isCreativeMode) {
                held.shrink(1);
            }
        }
    }

    @SubscribeEvent
    public static void projectile(ProjectileImpactEvent event) {
        RayTraceResult hit = event.getRayTraceResult();
        Entity entity = event.getEntity();
        if (!CandleContent.enabled() || hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || entity == null
                || entity.world.isRemote) {
            return;
        }
        World world = entity.world;
        BlockPos pos = hit.getBlockPos();
        if (entity.isBurning()) {
            IBlockState state = world.getBlockState(pos);
            if (BlockAbstractCandle.canLight(world, pos, state)) {
                BlockAbstractCandle.light(world, pos, state);
            }
        }
        if (entity instanceof EntityPotion) {
            ItemStack potion = ((EntityPotion) entity).getPotion();
            if (PotionUtils.getPotionFromItem(potion) == PotionTypes.WATER && PotionUtils.getEffectsFromStack(potion).isEmpty()) {
                // The same places a water bottle puts fire out: the block it hit, the space in front and around it.
                BlockPos front = hit.sideHit == null ? pos : pos.offset(hit.sideHit);
                douse(world, pos);
                douse(world, front);
                for (EnumFacing side : EnumFacing.HORIZONTALS) {
                    douse(world, front.offset(side));
                }
            }
        }
    }

    private static void douse(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (BlockAbstractCandle.isLit(state)) {
            BlockAbstractCandle.extinguish(world, pos, state);
        }
    }
}
