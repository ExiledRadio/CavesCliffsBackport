package com.exiledradio.cavesandcliffsbackport.candle;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * A candle in the hand. Used on a block of the same candles that holds fewer than four (or on something next
 * to one), it adds a candle there instead of placing a new block; sneaking places as any block does.
 */
public class ItemCandle extends ItemBlock {

    public ItemCandle(Block block) {
        super(block);
    }

    private boolean takesMore(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() == this.block && state.getValue(BlockCandle.CANDLES) < 4;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (!stack.isEmpty()) {
            BlockPos target = addTarget(player, world, pos, facing);
            if (target != null && player.canPlayerEdit(target, facing, stack)) {
                IBlockState state = world.getBlockState(target);
                world.setBlockState(target, state.withProperty(BlockCandle.CANDLES, state.getValue(BlockCandle.CANDLES) + 1), 11);
                SoundType sound = this.block.getSoundType(state, world, target, player);
                world.playSound(player, target, sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F,
                        sound.getPitch() * 0.8F);
                if (player instanceof EntityPlayerMP) {
                    CriteriaTriggers.PLACED_BLOCK.trigger((EntityPlayerMP) player, target, stack);
                }
                stack.shrink(1);
                return EnumActionResult.SUCCESS;
            }
        }
        return super.onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
    }

    /**
     * The candles a click adds one to: the clicked block, or the block the click would place into, while it holds
     * fewer than four of these candles; null when the click places a new block (always while sneaking).
     */
    @Nullable
    private BlockPos addTarget(EntityPlayer player, World world, BlockPos pos, EnumFacing facing) {
        if (player.isSneaking()) {
            return null;
        }
        if (takesMore(world, pos)) {
            return pos;
        }
        if (!world.getBlockState(pos).getBlock().isReplaceable(world, pos) && takesMore(world, pos.offset(facing))) {
            return pos.offset(facing);
        }
        return null;
    }

    /**
     * The client asks this before it sends a click to the server (as it does for slabs, which {@code ItemSlab} answers
     * the same way): without it, a click on candles is refused on the client because a candle cannot stand on a
     * candle or go into one, and adding a candle never happens in a real game.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side, EntityPlayer player, ItemStack stack) {
        return addTarget(player, world, pos, side) != null || super.canPlaceBlockOnSide(world, pos, side, player, stack);
    }
}
