package com.exiledradio.cavesandcliffsbackport.glowsquid;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Advancements;

import net.minecraft.block.BlockSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Glowing sign text: a glow ink sac on a sign makes its text glow, an ink sac undoes it.
 *
 * <h2>Where the glow is kept</h2>
 * On the sign itself, under current Java's name {@code GlowingText}, inside the sign's Forge data compound
 * ({@code ForgeData.GlowingText} in the tile entity's NBT). Forge saves that compound with the tile entity
 * and, because a sign's update packet is its whole NBT, vanilla's own packets carry it to every client - so
 * there is no side list of positions, no packet of ours and no bytecode patch. Everything that reads or
 * writes the flag goes through {@link #get} and {@link #set}.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class GlowingText {

    public static final String KEY = "GlowingText";

    private GlowingText() {
    }

    public static boolean get(TileEntitySign sign) {
        return sign.getTileData().getBoolean(KEY);
    }

    /** Sets or clears the glow, saves the sign and sends it to the clients watching it. */
    public static void set(TileEntitySign sign, boolean glowing) {
        NBTTagCompound data = sign.getTileData();
        if (glowing) {
            data.setBoolean(KEY, true);
        } else {
            data.removeTag(KEY);
        }
        sign.markDirty();
        World world = sign.getWorld();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(sign.getPos());
            world.notifyBlockUpdate(sign.getPos(), state, state, 3);
        }
    }

    private static boolean isInkSac(ItemStack stack) {
        return stack.getItem() == Items.DYE && stack.getMetadata() == EnumDyeColor.BLACK.getDyeDamage();
    }

    /**
     * Current Java's sign rule: a glow ink sac on a sign that does not glow, or an ink sac on one that does,
     * changes it, plays the sac's sound and uses one up (not in creative mode). Anything else is left to the
     * sign. Sneaking uses the item instead of the block, as everywhere, and neither sac does anything then.
     */
    @SubscribeEvent
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!GlowSquidContent.enabled() || !ModConfig.glowSquid.glowingSigns) {
            return;
        }
        ItemStack stack = event.getItemStack();
        boolean glow = stack.getItem() == GlowSquidContent.glowInkSac;
        if (!glow && !isInkSac(stack)) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        if (player.isSneaking() || !player.isAllowEdit() || !(world.getBlockState(pos).getBlock() instanceof BlockSign)) {
            return;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntitySign) || get((TileEntitySign) tile) == glow) {
            return;
        }
        if (!world.isRemote) {
            TileEntitySign sign = (TileEntitySign) tile;
            world.playSound(null, pos, glow ? GlowSquidContent.glowInkSacUse : GlowSquidContent.inkSacUse, SoundCategory.BLOCKS, 1.0F, 1.0F);
            set(sign, glow);
            if (glow) {
                Advancements.grant(player, "husbandry/make_a_sign_glow");
            }
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
            // The click still reaches the sign, as in current Java (a sign with a click command runs it).
            sign.executeCommand(player);
        }
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
    }
}
