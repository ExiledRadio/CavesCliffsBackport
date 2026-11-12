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
        if (!player.isSneaking() && !stack.isEmpty()) {
            BlockPos target = null;
            if (takesMore(world, pos)) {
                target = pos;
            } else if (!world.getBlockState(pos).getBlock().isReplaceable(world, pos) && takesMore(world, pos.offset(facing))) {
                target = pos.offset(facing);
            }
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
}
