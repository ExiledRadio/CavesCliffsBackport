package com.exiledradio.cavesandcliffsbackport.candle;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.common.ModSounds;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Stream Ca: the candle and its sixteen dyed forms, and the seventeen cakes with a candle on them.
 *
 * <h2>Names</h2>
 * 1.18's: {@code candle}, {@code white_candle} ... {@code black_candle}, {@code candle_cake},
 * {@code white_candle_cake} ... {@code black_candle_cake}. Index 0 of each array is the plain one, index
 * {@code 1 + dye metadata} the dyed ones. Candle cakes have no item.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class CandleContent {

    /** 1.18's color names in dye metadata order (1.12 calls light gray "silver"). */
    public static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    /** The ore names of the sixteen dyes, in the same order. */
    private static final String[] DYES = {"dyeWhite", "dyeOrange", "dyeMagenta", "dyeLightBlue", "dyeYellow", "dyeLime",
            "dyePink", "dyeGray", "dyeLightGray", "dyeCyan", "dyePurple", "dyeBlue", "dyeBrown", "dyeGreen", "dyeRed", "dyeBlack"};

    public static final BlockCandle[] candles = new BlockCandle[17];
    public static final BlockCandleCake[] cakes = new BlockCandleCake[17];
    public static SoundEvent ambient, extinguish, addCandle;

    /** Every block name this stream registers, for Core's ownership table. */
    public static final String[] BLOCK_NAMES = names();

    private CandleContent() {
    }

    public static boolean enabled() {
        return ModConfig.candles.enabled;
    }

    /** @return the registry name of candle {@code i}: 0 the plain one, 1-16 the dyed ones */
    public static String name(int i) {
        return i == 0 ? "candle" : COLORS[i - 1] + "_candle";
    }

    private static String[] names() {
        List<String> n = new ArrayList<String>();
        for (int i = 0; i < 17; i++) {
            n.add(name(i));
        }
        for (int i = 0; i < 17; i++) {
            n.add(name(i) + "_cake");
        }
        return n.toArray(new String[0]);
    }

    /** @return the cake that carries this candle, or null if the block is not one of our candles */
    @Nullable
    public static BlockCandleCake cakeFor(Block candle) {
        for (int i = 0; i < 17; i++) {
            if (candles[i] == candle) {
                return cakes[i];
            }
        }
        return null;
    }

    /** From the mod's pre-init: tells Core which blocks are ours (or, switched off, that nobody provides them). */
    public static void preInit() {
        Owners.declare(enabled(), BLOCK_NAMES);
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<Block> r = event.getRegistry();
        SoundType sound = ModSounds.type("block.candle");
        ambient = ModSounds.event("block.candle.ambient");
        extinguish = ModSounds.event("block.candle.extinguish");
        addCandle = ModSounds.event("block.cake.add_candle");
        for (int i = 0; i < 17; i++) {
            MapColor color = i == 0 ? MapColor.SAND : MapColor.getBlockColor(EnumDyeColor.byMetadata(i - 1));
            candles[i] = Content.block(r, name(i), new BlockCandle(color, sound), null);
        }
        for (int i = 0; i < 17; i++) {
            cakes[i] = Content.block(r, name(i) + "_cake", new BlockCandleCake(candles[i]), null);
        }
        CavesAndCliffsBackport.LOGGER.info("Registered 17 candles and 17 candle cakes.");
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            return;
        }
        for (BlockCandle candle : candles) {
            Content.blockItem(event.getRegistry(), candle, new ItemCandle(candle));
        }
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<IRecipe> r = event.getRegistry();
        registerHoneycombOres();
        // String over honeycomb: any item with the ore name honeycomb.
        ShapedOreRecipe candle = new ShapedOreRecipe(new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "candle"),
                new ItemStack(candles[0]), "S", "H", 'S', new ItemStack(Items.STRING), 'H', "honeycomb");
        candle.setRegistryName(Content.id("candle"));
        r.register(candle);
        for (int i = 1; i < 17; i++) {
            ShapelessOreRecipe dyed = new ShapelessOreRecipe(new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "dyed_candle"),
                    new ItemStack(candles[i]), new ItemStack(candles[0]), DYES[i - 1]);
            dyed.setRegistryName(Content.id(name(i)));
            r.register(dyed);
        }
    }

    /**
     * The honeycomb lookup the copper stream uses, by name and without its classes: every installed item of
     * {@code copper.waxItems} gets the ore name honeycomb (registering twice changes nothing), and any other
     * mod's item that already has that ore name counts too.
     */
    private static void registerHoneycombOres() {
        for (String name : ModConfig.copper.waxItems) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(name));
            if (item != null && item != Items.AIR) {
                OreDictionary.registerOre("honeycomb", item);
            }
        }
    }
}
