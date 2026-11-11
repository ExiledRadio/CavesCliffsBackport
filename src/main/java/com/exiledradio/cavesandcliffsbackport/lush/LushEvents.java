package com.exiledradio.cavesandcliffsbackport.lush;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFlowerPot;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.UseHoeEvent;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * What the lush blocks need from vanilla things: the hoe on rooted dirt, azaleas into flower pots, projectiles
 * on big dripleaves, and azaleas as furnace fuel. Registered only when the stream is on.
 */
public final class LushEvents {

    /** A hoe on rooted dirt: dirt, and the roots come out as hanging roots. */
    @SubscribeEvent
    public void hoe(UseHoeEvent event) {
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        if (world.getBlockState(pos).getBlock() != LushContent.rootedDirt) {
            return;
        }
        if (!world.isRemote) {
            world.setBlockState(pos, Blocks.DIRT.getDefaultState(), 11);
            Block.spawnAsEntity(world, pos.up(), new ItemStack(LushContent.hangingRoots));
        }
        world.playSound(event.getEntityPlayer(), pos, SoundEvents.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
        event.setResult(Event.Result.ALLOW);
    }

    /** An azalea used on an empty flower pot. */
    @SubscribeEvent
    public void pot(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        Block potted = held.getItem() == Item.getItemFromBlock(LushContent.azalea) ? LushContent.pottedAzalea
                : held.getItem() == Item.getItemFromBlock(LushContent.floweringAzalea) ? LushContent.pottedFloweringAzalea : null;
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        if (potted == null || world.getBlockState(pos).getBlock() != Blocks.FLOWER_POT) {
            return;
        }
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityFlowerPot && !((TileEntityFlowerPot) te).getFlowerItemStack().isEmpty()) {
            return;
        }
        if (!world.isRemote) {
            world.setBlockState(pos, potted.getDefaultState(), 3);
            EntityPlayer player = event.getEntityPlayer();
            if (!player.capabilities.isCreativeMode) {
                held.shrink(1);
            }
        }
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
    }

    /** A projectile that hits a big dripleaf tilts it all the way. */
    @SubscribeEvent
    public void projectile(ProjectileImpactEvent event) {
        RayTraceResult hit = event.getRayTraceResult();
        World world = event.getEntity().world;
        if (world.isRemote || hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() == LushContent.bigDripleaf && state.getValue(BlockBigDripleaf.TILT) != BlockBigDripleaf.Tilt.FULL
                && !world.isBlockPowered(pos)) {
            LushContent.bigDripleaf.tilt(world, pos, state, BlockBigDripleaf.Tilt.FULL, LushContent.tiltDown);
        }
    }

    /** Azaleas smelt half an item. */
    @SubscribeEvent
    public void fuel(FurnaceFuelBurnTimeEvent event) {
        Item item = event.getItemStack().getItem();
        if (item == Item.getItemFromBlock(LushContent.azalea) || item == Item.getItemFromBlock(LushContent.floweringAzalea)) {
            event.setBurnTime(100);
        }
    }
}
