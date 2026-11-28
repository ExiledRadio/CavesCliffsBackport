package com.exiledradio.cavesandcliffsbackport.lush;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryEmpty;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.SetCount;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;

/**
 * 1.17's chest loot for the lush caves' items, as in 1.18.2's tables:
 * <ul>
 *   <li>mineshaft chests (the chest minecarts): glow berries, weight 15, 3-6, in the pool that rolls 2-4 times (the one
 *       with iron ingots, bread and seeds; Forge names it {@code pool1});</li>
 *   <li>shipwreck supply chests (Update Aquatic Backport's table, which keeps a weight 7 empty placeholder named
 *       {@code minecraft:moss_block} for this): moss blocks, weight 7, 1-4, in place of the placeholder.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class LushLoot {

    /** Update Aquatic Backport's shipwreck supply chest. */
    public static final ResourceLocation SHIPWRECK_SUPPLY = new ResourceLocation("updateaquaticbackport", "chests/shipwreck_supply");
    /** The name of Update Aquatic's placeholder entry for moss. */
    static final String MOSS_PLACEHOLDER = "minecraft:moss_block";
    public static final String GLOW_BERRIES_ENTRY = CavesAndCliffsBackport.MOD_ID + ":glow_berries";
    public static final String MOSS_ENTRY = CavesAndCliffsBackport.MOD_ID + ":moss_block";

    private LushLoot() {
    }

    @SubscribeEvent
    public static void lootLoad(LootTableLoadEvent event) {
        if (!LushContent.enabled()) {
            return;
        }
        if (LootTableList.CHESTS_ABANDONED_MINESHAFT.equals(event.getName())) {
            LootPool pool = event.getTable().getPool("pool1");
            if (pool != null && pool.getEntry("minecraft:iron_ingot") != null) {
                add(pool, LushContent.glowBerries, 15, 3, 6, GLOW_BERRIES_ENTRY);
            }
        } else if (SHIPWRECK_SUPPLY.equals(event.getName())) {
            LootPool pool = event.getTable().getPool("pool0");
            if (pool == null) {
                return;
            }
            LootEntry placeholder = pool.getEntry(MOSS_PLACEHOLDER);
            if (placeholder instanceof LootEntryEmpty) {
                pool.removeEntry(MOSS_PLACEHOLDER);
            }
            add(pool, LushContent.moss == null ? null : Item.getItemFromBlock(LushContent.moss), 7, 1, 4, MOSS_ENTRY);
        }
    }

    private static void add(LootPool pool, @Nullable Item item, int weight, int min, int max, String name) {
        if (item == null || pool.getEntry(name) != null) {
            return;
        }
        LootFunction[] count = {new SetCount(new LootCondition[0], new RandomValueRange(min, max))};
        pool.addEntry(new LootEntryItem(item, weight, 0, count, new LootCondition[0], name));
    }
}
