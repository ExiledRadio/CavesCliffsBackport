package com.exiledradio.cavesandcliffsbackport.copper;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 1.17: drowned killed by a player drop a copper ingot 11% of the time, 2% more per Looting level.
 * The drowned are Update Aquatic Backport's (Rule 2), found by registry name.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class DrownedCopper {

    static final ResourceLocation DROWNED = new ResourceLocation(Owners.UPDATE_AQUATIC, "drowned");

    private DrownedCopper() {
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        if (!CopperContent.enabled() || event.getEntity().world.isRemote || !event.isRecentlyHit()
                || !DROWNED.equals(EntityList.getKey(event.getEntity()))) {
            return;
        }
        float chance = 0.11F + 0.02F * event.getLootingLevel();
        if (event.getEntity().world.rand.nextFloat() < chance) {
            EntityItem item = new EntityItem(event.getEntity().world, event.getEntity().posX, event.getEntity().posY,
                    event.getEntity().posZ, new ItemStack(CopperContent.copperIngot));
            item.setDefaultPickupDelay();
            event.getDrops().add(item);
        }
    }
}
