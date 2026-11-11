package com.exiledradio.cavesandcliffsbackport.lush;

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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Comparator;

/**
 * The one glow lichen item for the four glow lichen blocks: puts a face of lichen on the block that was
 * clicked, joining lichen already in that space; when that face cannot take it, the nearest face the player
 * is looking toward that can.
 */
public class ItemGlowLichen extends ItemBlock {

    public ItemGlowLichen(Block block) {
        super(block);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side,
                                      float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        IBlockState clicked = world.getBlockState(pos);
        boolean onLichen = clicked.getBlock() instanceof BlockGlowLichen;
        BlockPos at = onLichen || clicked.getBlock().isReplaceable(world, pos) ? pos : pos.offset(side);
        IBlockState there = world.getBlockState(at);
        boolean joins = there.getBlock() instanceof BlockGlowLichen;
        if (stack.isEmpty() || !player.canPlayerEdit(at, side, stack)
                || !(joins || (there.getBlock().isReplaceable(world, at) && !there.getMaterial().isLiquid()))) {
            return EnumActionResult.FAIL;
        }
        EnumFacing face = null;
        EnumFacing direct = side.getOpposite();
        if (at != pos && canTake(world, at, there, direct)) {
            face = direct;
        } else {
            final Vec3d look = player.getLookVec();
            EnumFacing[] order = EnumFacing.values().clone();
            Arrays.sort(order, new Comparator<EnumFacing>() {
                @Override
                public int compare(EnumFacing a, EnumFacing b) {
                    return Double.compare(dot(b, look), dot(a, look));
                }
            });
            for (EnumFacing f : order) {
                if (canTake(world, at, there, f)) {
                    face = f;
                    break;
                }
            }
        }
        if (face == null) {
            return EnumActionResult.FAIL;
        }
        if (!world.isRemote) {
            int mask = BlockGlowLichen.faces(there) | (1 << face.getIndex());
            IBlockState state = BlockGlowLichen.of(mask);
            world.setBlockState(at, state, 3);
            SoundType sound = state.getBlock().getSoundType(state, world, at, player);
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

    private static boolean canTake(World world, BlockPos at, IBlockState there, EnumFacing face) {
        return !BlockGlowLichen.has(BlockGlowLichen.faces(there), face) && BlockGlowLichen.supported(world, at, face);
    }

    private static double dot(EnumFacing f, Vec3d v) {
        return f.getXOffset() * v.x + f.getYOffset() * v.y + f.getZOffset() * v.z;
    }
}
