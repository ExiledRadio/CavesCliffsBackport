package com.exiledradio.cavesandcliffsbackport.blocks;

import com.exiledradio.backportcore.waterlog.Waterlogging;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * The light item. Its level is kept where Java Edition keeps it, in {@code BlockStateTag.level}
 * ({@code /give @p cavesandcliffsbackport:light 1 0 {BlockStateTag:{level:"7"}}}); without it the level is 15.
 * It is in no creative tab, as 1.12.2's barrier: commands and pick block give it.
 */
public final class ItemLight extends ItemBlock {

    public ItemLight(Block block) {
        super(block);
        // The item model's overrides pick the texture with the level's number on it, as in 1.17.
        addPropertyOverride(new ResourceLocation("level"), new IItemPropertyGetter() {
            @SideOnly(Side.CLIENT)
            @Override
            public float apply(ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
                return level(stack) / 16.0F;
            }
        });
    }

    /** @return the level this stack places: {@code BlockStateTag.level} when it is 0-15, else 15 */
    public static int level(ItemStack stack) {
        NBTTagCompound tag = stack.getSubCompound("BlockStateTag");
        if (tag != null && tag.hasKey("level")) {
            NBTBase value = tag.getTag("level");
            int level = -1;
            if (value instanceof NBTPrimitive) {
                level = ((NBTPrimitive) value).getInt();
            } else if (value instanceof NBTTagString) {
                try {
                    level = Integer.parseInt(((NBTTagString) value).getString());
                } catch (NumberFormatException ignored) {
                }
            }
            if (level >= 0 && level <= 15) {
                return level;
            }
        }
        return 15;
    }

    /** Writes a level on a stack as Java Edition does: nothing for 15, the default. */
    public static ItemStack withLevel(ItemStack stack, int level) {
        if (level != 15) {
            stack.getOrCreateSubCompound("BlockStateTag").setString("level", String.valueOf(level));
        }
        return stack;
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    /**
     * Places beside a clicked light block instead of into it, and never into another light block: a light
     * block gives way to every block but its own kind.
     */
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        Block clicked = world.getBlockState(pos).getBlock();
        BlockPos target = clicked == this.block || !clicked.isReplaceable(world, pos) ? pos.offset(facing) : pos;
        Block there = world.getBlockState(target).getBlock();
        if (there == this.block || !there.isReplaceable(world, target)) {
            return EnumActionResult.FAIL;
        }
        // The target is replaceable, so the vanilla placement keeps it as the position.
        return super.onItemUse(player, world, target, hand, facing, hitX, hitY, hitZ);
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                float hitX, float hitY, float hitZ, IBlockState newState) {
        boolean placed = super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ,
                this.block.getDefaultState().withProperty(BlockLight.LEVEL, level(stack)));
        // 1.18.2's light has no placement state: placed into water it takes the water's place (only a bucket waterlogs it).
        if (placed && !world.isRemote && Waterlogging.hasWater(world, pos)) {
            Waterlogging.setWater(world, pos, false, 3);
        }
        return placed;
    }
}
