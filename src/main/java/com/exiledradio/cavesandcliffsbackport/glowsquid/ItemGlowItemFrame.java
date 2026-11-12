package com.exiledradio.cavesandcliffsbackport.glowsquid;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Hangs a glow item frame. Vanilla's hanging-entity item only knows how to make paintings and item frames,
 * so this repeats its placement rule for our entity: on the side of a block, where the frame fits.
 */
public class ItemGlowItemFrame extends Item {

    public ItemGlowItemFrame() {
        this.setCreativeTab(CreativeTabs.DECORATIONS);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        BlockPos at = pos.offset(facing);
        if (facing == EnumFacing.DOWN || facing == EnumFacing.UP || !player.canPlayerEdit(at, facing, stack)) {
            return EnumActionResult.FAIL;
        }
        EntityGlowItemFrame frame = new EntityGlowItemFrame(world, at, facing);
        if (frame.onValidSurface()) {
            if (!world.isRemote) {
                frame.playPlaceSound();
                world.spawnEntity(frame);
            }
            stack.shrink(1);
        }
        return EnumActionResult.SUCCESS;
    }
}
