package com.exiledradio.cavesandcliffsbackport.deepslate;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 1.17: iron ore and gold ore drop raw iron and raw gold, with Fortune's ore formula (as current Java),
 * and still drop themselves with Silk Touch. Config {@code deepslate.rawOreDrops}.
 *
 * <p>Runs early so that auto-smelting and ore-doubling handlers later in the chain see the raw ore.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class RawOreDrops {

    private RawOreDrops() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void harvest(BlockEvent.HarvestDropsEvent event) {
        if (!DeepslateContent.enabled() || !ModConfig.deepslate.rawOreDrops || event.isSilkTouching()) {
            return;
        }
        Block block = event.getState().getBlock();
        Item raw = block == Blocks.IRON_ORE ? DeepslateContent.rawIron : block == Blocks.GOLD_ORE ? DeepslateContent.rawGold : null;
        if (raw == null) {
            return;
        }
        Item ore = Item.getItemFromBlock(block);
        // Replace only the ore block itself, so other mods' extra drops stay.
        boolean replaced = event.getDrops().removeIf(s -> s.getItem() == ore);
        if (replaced) {
            int fortune = event.getFortuneLevel();
            int count = 1;
            if (fortune > 0) {
                count *= Math.max(event.getWorld().rand.nextInt(fortune + 2) - 1, 0) + 1;
            }
            event.getDrops().add(new ItemStack(raw, count));
        }
    }
}
