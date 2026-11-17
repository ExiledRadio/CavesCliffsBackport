package com.exiledradio.cavesandcliffsbackport.deepslate;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * The item of a {@link BlockModdedDeepslateOre}: named "Deepslate " + the original's name in the current language, and
 * listed in the original's creative tab - only once the original is a confirmed ore. An unconfirmed candidate has no
 * tab at all, which keeps it out of the creative menu and out of JEI (JEI lists an item by its creative tabs).
 */
public class ItemModdedDeepslateOre extends ItemBlock {

    private static final CreativeTabs[] NONE = new CreativeTabs[0];

    private final ModdedOres.Form form;

    ItemModdedDeepslateOre(BlockModdedDeepslateOre block) {
        super(block);
        this.form = block.form;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return this.form.displayName();
    }

    @Nullable
    @Override
    public CreativeTabs getCreativeTab() {
        if (!this.form.confirmed) {
            return null;
        }
        ItemStack original = this.form.originalStack();
        CreativeTabs tab = original.isEmpty() ? null : original.getItem().getCreativeTab();
        return tab != null ? tab : CreativeTabs.BUILDING_BLOCKS;
    }

    @Override
    public CreativeTabs[] getCreativeTabs() {
        CreativeTabs tab = getCreativeTab();
        return tab == null ? NONE : new CreativeTabs[] {tab};
    }
}
