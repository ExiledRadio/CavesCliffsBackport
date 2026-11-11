package com.exiledradio.cavesandcliffsbackport.dripstone;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.BlockBasic;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.common.ModSounds;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import com.exiledradio.backportcore.trade.TradeRegistry;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.Random;

/**
 * Stream Dr: the dripstone block, pointed dripstone ({@link BlockPointedDripstone}, falling as
 * {@link EntityFallingStalactite}), the lava cauldron, and dripstone caves ({@link DripstoneCaveGenerator}
 * in the {@link Regions}).
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class DripstoneContent {

    /** This stream's entity ids are 1-4. */
    public static final int FALLING_STALACTITE_ID = 1;

    public static Block dripstone;
    public static BlockPointedDripstone pointed;
    public static BlockLavaCauldron lavaCauldron;
    public static SoundEvent land, dripWater, dripLava, dripWaterIntoCauldron, dripLavaIntoCauldron;

    /** Every block name this stream registers, for Core's ownership table. */
    public static final String[] BLOCK_NAMES = {"dripstone_block", "pointed_dripstone", "lava_cauldron"};

    private DripstoneContent() {
    }

    public static boolean enabled() {
        return ModConfig.dripstone.enabled;
    }

    /** From the mod's pre-init: block ownership, and the falling stalactite. */
    public static void preInit() {
        Owners.declare(enabled(), BLOCK_NAMES);
        if (enabled()) {
            EntityRegistry.registerModEntity(Content.id("falling_stalactite"), EntityFallingStalactite.class,
                    CavesAndCliffsBackport.MOD_ID + ".falling_stalactite", FALLING_STALACTITE_ID, CavesAndCliffsBackport.instance, 160, 20, true);
        }
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<Block> r = event.getRegistry();
        land = ModSounds.event("block.pointed_dripstone.land");
        dripWater = ModSounds.event("block.pointed_dripstone.drip_water");
        dripLava = ModSounds.event("block.pointed_dripstone.drip_lava");
        dripWaterIntoCauldron = ModSounds.event("block.pointed_dripstone.drip_water_into_cauldron");
        dripLavaIntoCauldron = ModSounds.event("block.pointed_dripstone.drip_lava_into_cauldron");

        dripstone = Content.block(r, "dripstone_block", new BlockBasic(Material.ROCK, MapColor.BROWN_STAINED_HARDENED_CLAY,
                ModSounds.type("block.dripstone_block"), 1.5F, 1.0F), "pickaxe");
        pointed = Content.block(r, "pointed_dripstone", new BlockPointedDripstone(ModSounds.type("block.pointed_dripstone")), "pickaxe");
        lavaCauldron = Content.block(r, "lava_cauldron", new BlockLavaCauldron(), "pickaxe");
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            return;
        }
        // The lava cauldron has no item: it is vanilla's cauldron with lava in it.
        Content.registerBlockItem(event.getRegistry(), dripstone);
        Content.registerBlockItem(event.getRegistry(), pointed);
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!enabled()) {
            return;
        }
        ShapedOreRecipe recipe = new ShapedOreRecipe(new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "dripstone_block"),
                new ItemStack(dripstone), "XX", "XX", 'X', new ItemStack(pointed));
        recipe.setRegistryName(Content.id("dripstone_block"));
        event.getRegistry().register(recipe);
    }

    /** From the mod's init: events and dripstone caves. */
    public static void init() {
        if (enabled()) {
            MinecraftForge.EVENT_BUS.register(new DripstoneEvents());
            // After the ores and geodes (weight 0), so dripstone covers what they left in the cave walls.
            GameRegistry.registerWorldGenerator(new DripstoneCaveGenerator(), 2);
            // Current Java's wandering trader sells two pointed dripstone for an emerald, five times.
            TradeRegistry.addTraderTrade(TradeRegistry.TraderPool.ORDINARY, new TradeRegistry.TraderTrade() {
                @Override
                public MerchantRecipe offer(Random random) {
                    return new MerchantRecipe(new ItemStack(Items.EMERALD), ItemStack.EMPTY, new ItemStack(pointed, 2), 0, 5);
                }
            });
        }
    }
}
