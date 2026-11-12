package com.exiledradio.cavesandcliffsbackport.powdersnow;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Powder snow bucket: places powder snow as a block item does (against the clicked face, not where a
 * liquid would go) and leaves an empty bucket.
 *
 * <p>It is not an {@code ItemBlock}: powder snow has no item of its own, and an {@code ItemBlock} would make
 * this bucket "the block's item" for Silk Touch and for every mod that asks.
 */
public class ItemPowderSnowBucket extends Item {

    private final Block block;

    public ItemPowderSnowBucket(Block block) {
        this.block = block;
        setMaxStackSize(1);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        if (!world.getBlockState(pos).getBlock().isReplaceable(world, pos)) {
            pos = pos.offset(facing);
        }
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty() || !player.canPlayerEdit(pos, facing, stack) || !world.mayPlace(this.block, pos, false, facing, player)) {
            return EnumActionResult.FAIL;
        }
        if (!place(world, pos, player)) {
            return EnumActionResult.FAIL;
        }
        if (player instanceof EntityPlayerMP) {
            CriteriaTriggers.PLACED_BLOCK.trigger((EntityPlayerMP) player, pos, stack);
        }
        if (!player.capabilities.isCreativeMode) {
            player.setHeldItem(hand, new ItemStack(Items.BUCKET));
        }
        return EnumActionResult.SUCCESS;
    }

    /** Puts powder snow at a position with the bucket's sound; the caller has checked it may. */
    public boolean place(World world, BlockPos pos, EntityPlayer player) {
        if (!world.isRemote && !world.setBlockState(pos, this.block.getDefaultState(), 11)) {
            return false;
        }
        world.playSound(player, pos, PowderSnowContent.bucketEmpty, SoundCategory.BLOCKS, 1.0F, 1.0F);
        return true;
    }
}
