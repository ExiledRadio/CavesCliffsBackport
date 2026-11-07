package com.exiledradio.cavesandcliffsbackport.common;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Who owns a block that more than one mod of this family needs.
 *
 * <h2>The rule</h2>
 * The mod that shipped a block first owns it. When the owner is installed this mod registers nothing
 * for that block and uses the owner's; when it is not, this mod registers its own copy under its own
 * id, with the same states and metadata, so the owner can later rename it in place.
 *
 * <h2>What this mod owns</h2>
 * Everything 1.17 and 1.18 added. Nothing here is copied from another family mod: what we need from
 * them (honeycomb, basalt, tropical fish buckets) is looked up by name and used only when installed.
 * Backport Core's {@code BlockOwnership} table is told about our blocks for the later updates that copy
 * them.
 */
public final class Owners {

    /** 1.13: tropical fish buckets (axolotl breeding), the drowned (copper drops), fish (axolotl prey). */
    public static final String UPDATE_AQUATIC = "updateaquaticbackport";

    /** 1.14: the composter, stonecutter and blast furnace, foxes. */
    public static final String VILLAGE_AND_PILLAGE = "villagerbackport";

    /** 1.15: honeycomb, which waxes copper and makes candles. */
    public static final String BUZZY_BEES = "buzzybeesbackport";

    /** 1.16: basalt, smelted into our smooth basalt. */
    public static final String NETHER_UPDATE = "netherupdatebackport";

    private Owners() {
    }

    /** @return true if the owning mod is installed, so its block is used and ours is never registered */
    public static boolean present(String owner) {
        return Loader.isModLoaded(owner);
    }

    /**
     * @return the block registered under {@code owner:name} if the owner is installed, else ours under
     * this mod's id; null if neither exists (the stream that would register ours is switched off)
     */
    @Nullable
    public static Block block(String owner, String name) {
        ResourceLocation id = present(owner) ? new ResourceLocation(owner, name) : Content.id(name);
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        return block == null || !id.equals(block.getRegistryName()) ? null : block;
    }

    /** @return the item for {@code name} the same way as {@link #block}, or null */
    @Nullable
    public static Item item(String owner, String name) {
        ResourceLocation id = present(owner) ? new ResourceLocation(owner, name) : Content.id(name);
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || !id.equals(item.getRegistryName()) ? null : item;
    }

    /**
     * Looks up another stream's item by name, so streams never link against each other's classes.
     *
     * @return this mod's item {@code name}, or null when its stream is switched off
     */
    @Nullable
    public static Item ours(String name) {
        Item item = ForgeRegistries.ITEMS.getValue(Content.id(name));
        return item == null || !Content.id(name).equals(item.getRegistryName()) ? null : item;
    }

    /** As {@link #ours(String)}, for blocks. */
    @Nullable
    public static Block ourBlock(String name) {
        Block block = ForgeRegistries.BLOCKS.getValue(Content.id(name));
        return block == null || !Content.id(name).equals(block.getRegistryName()) ? null : block;
    }
}
