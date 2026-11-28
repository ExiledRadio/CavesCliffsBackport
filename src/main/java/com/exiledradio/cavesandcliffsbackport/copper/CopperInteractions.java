package com.exiledradio.cavesandcliffsbackport.copper;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.common.Advancements;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Right-clicking copper (current Java): honeycomb waxes it (one honeycomb, <i>Wax On</i>); an axe takes the
 * wax off first (<i>Wax Off</i>), otherwise scrapes off one stage of oxidation; the axe loses one durability.
 * Both sides cancel the click so the arm swings and nothing else happens; only the server changes blocks.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class CopperInteractions {

    private CopperInteractions() {
    }

    static boolean isAxe(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof ItemAxe || stack.getItem().getToolClasses(stack).contains("axe"));
    }

    @SubscribeEvent
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!CopperContent.enabled()) {
            return;
        }
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (!Weathering.isCopper(block)) {
            return;
        }
        ItemStack held = event.getItemStack();
        EntityPlayer player = event.getEntityPlayer();
        Block to;
        SoundEvent sound;
        String advancement = null;
        int particles;
        if (Weathering.isWax(held)) {
            to = Weathering.waxedOf(block);
            sound = CopperContent.waxOn;
            advancement = "husbandry/wax_on";
            particles = PacketCopperParticles.WAX_ON;
        } else if (isAxe(held)) {
            to = Weathering.unwaxed(block);
            if (to != null) {
                sound = CopperContent.waxOff;
                advancement = "husbandry/wax_off";
                particles = PacketCopperParticles.WAX_OFF;
            } else {
                to = Weathering.previous(block);
                sound = CopperContent.scrape;
                particles = PacketCopperParticles.SCRAPE;
            }
        } else {
            return;
        }
        if (to == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        if (world.isRemote) {
            return;
        }
        world.setBlockState(pos, Weathering.copy(state, to), 11);
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0F, 1.0F);
        PacketCopperParticles.send(world, pos, particles);
        if (!player.capabilities.isCreativeMode) {
            if (Weathering.isWax(held)) {
                held.shrink(1);
            } else {
                held.damageItem(1, player);
            }
        }
        if (advancement != null) {
            Advancements.grant(player, advancement);
        }
    }
}
