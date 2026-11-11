package com.exiledradio.cavesandcliffsbackport.lush;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Glow berries: food (2 hunger, 0.4 saturation) that plants a cave vine under a solid block or under
 * another cave vine. Used anywhere else they are eaten.
 */
public class ItemGlowBerries extends ItemFood {

    public ItemGlowBerries() {
        super(2, 0.1F, false);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        BlockPos at = world.getBlockState(pos).getBlock().isReplaceable(world, pos) ? pos : pos.offset(facing);
        BlockCaveVines.Head vines = LushContent.caveVines;
        if (stack.isEmpty() || !player.canPlayerEdit(at, facing, stack) || !world.getBlockState(at).getBlock().isReplaceable(world, at)
                || world.getBlockState(at).getMaterial().isLiquid() || !vines.canStay(world, at, vines.getDefaultState())) {
            return EnumActionResult.PASS;
        }
        if (!world.isRemote) {
            IBlockState state = vines.planted(world.rand);
            world.setBlockState(at, state, 3);
            // The vine above, if any, turns from tip to plant.
            BlockPos up = at.up();
            IBlockState above = world.getBlockState(up);
            if (above.getBlock() == vines) {
                above.neighborChanged(world, up, vines, at);
            }
            SoundType sound = vines.getSoundType(state, world, at, player);
            world.playSound(null, at, sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            if (player instanceof EntityPlayerMP) {
                CriteriaTriggers.PLACED_BLOCK.trigger((EntityPlayerMP) player, at, stack);
            }
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
        }
        return EnumActionResult.SUCCESS;
    }
}
