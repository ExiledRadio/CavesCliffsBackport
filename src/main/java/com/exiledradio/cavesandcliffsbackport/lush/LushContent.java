package com.exiledradio.cavesandcliffsbackport.lush;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Compat;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.common.ModSounds;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Stream L: moss and moss carpet, azaleas, their leaves and tree, potted azaleas, rooted dirt, hanging roots,
 * the spore blossom, cave vines and glow berries, big and small dripleaf and glow lichen. The caves themselves
 * are {@link LushCaveGenerator} over {@link LushRegions}; hoes, flower pots and fuel are {@link LushEvents}.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class LushContent {

    public static BlockMoss moss;
    public static BlockMoss.Carpet mossCarpet;
    public static BlockAzalea azalea, floweringAzalea;
    public static BlockAzaleaLeaves leaves, floweringLeaves;
    public static BlockPottedAzalea pottedAzalea, pottedFloweringAzalea;
    public static BlockRootedDirt rootedDirt;
    public static BlockCeilingPlant.Roots hangingRoots;
    public static BlockCeilingPlant.SporeBlossom sporeBlossom;
    public static BlockCaveVines.Head caveVines;
    public static BlockCaveVines.Body caveVinesPlant;
    public static BlockBigDripleaf bigDripleaf;
    public static BlockBigDripleaf.Stem bigDripleafStem;
    public static BlockSmallDripleaf smallDripleaf;
    /** The four glow lichen blocks, in {@link BlockGlowLichen#NAMES} order; the first carries the item. */
    public static BlockGlowLichen[] glowLichen;
    public static Item glowBerries;
    public static SoundEvent pickBerries, tiltDown, tiltUp, crop;

    private static final List<Block> ALL = new ArrayList<Block>();
    /** The blocks that have an item of their own, in creative order. */
    private static final List<Block> WITH_ITEM = new ArrayList<Block>();

    /** Every block name this stream registers, for Core's ownership table. */
    public static final String[] BLOCK_NAMES = {"moss_block", "moss_carpet", "azalea", "flowering_azalea", "azalea_leaves",
            "flowering_azalea_leaves", "potted_azalea_bush", "potted_flowering_azalea_bush", "rooted_dirt", "hanging_roots",
            "spore_blossom", "cave_vines", "cave_vines_plant", "big_dripleaf", "big_dripleaf_stem", "small_dripleaf",
            "glow_lichen", "glow_lichen_down", "glow_lichen_up", "glow_lichen_up_down"};

    private LushContent() {
    }

    public static boolean enabled() {
        return ModConfig.lush.enabled;
    }

    public static List<Block> all() {
        return Collections.unmodifiableList(ALL);
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
        pickBerries = ModSounds.event("block.cave_vines.pick_berries");
        tiltDown = ModSounds.event("block.big_dripleaf.tilt_down");
        tiltUp = ModSounds.event("block.big_dripleaf.tilt_up");
        crop = ModSounds.event("block.growing_plant.crop");

        moss = item(add(r, "moss_block", new BlockMoss(ModSounds.type("block.moss")), null));
        mossCarpet = item(add(r, "moss_carpet", new BlockMoss.Carpet(ModSounds.type("block.moss_carpet")), null));
        azalea = item(add(r, "azalea", new BlockAzalea(ModSounds.type("block.azalea")), null));
        floweringAzalea = item(add(r, "flowering_azalea", new BlockAzalea(ModSounds.type("block.flowering_azalea")), null));
        leaves = item(add(r, "azalea_leaves", new BlockAzaleaLeaves(ModSounds.type("block.azalea_leaves"), false), null));
        floweringLeaves = item(add(r, "flowering_azalea_leaves", new BlockAzaleaLeaves(ModSounds.type("block.azalea_leaves"), true), null));
        pottedAzalea = add(r, "potted_azalea_bush", new BlockPottedAzalea(false), null);
        pottedFloweringAzalea = add(r, "potted_flowering_azalea_bush", new BlockPottedAzalea(true), null);
        rootedDirt = item(add(r, "rooted_dirt", new BlockRootedDirt(ModSounds.type("block.rooted_dirt")), "shovel"));
        hangingRoots = item(add(r, "hanging_roots", new BlockCeilingPlant.Roots(ModSounds.type("block.hanging_roots")), null));
        sporeBlossom = item(add(r, "spore_blossom", new BlockCeilingPlant.SporeBlossom(ModSounds.type("block.spore_blossom")), null));
        caveVines = add(r, "cave_vines", new BlockCaveVines.Head(ModSounds.type("block.cave_vines")), null);
        caveVinesPlant = add(r, "cave_vines_plant", new BlockCaveVines.Body(ModSounds.type("block.cave_vines")), null);
        bigDripleaf = item(add(r, "big_dripleaf", new BlockBigDripleaf(ModSounds.type("block.big_dripleaf")), null));
        bigDripleafStem = add(r, "big_dripleaf_stem", new BlockBigDripleaf.Stem(ModSounds.type("block.big_dripleaf")), null);
        smallDripleaf = item(add(r, "small_dripleaf", new BlockSmallDripleaf(ModSounds.type("block.small_dripleaf")), null));
        glowLichen = new BlockGlowLichen[4];
        for (int i = 0; i < 4; i++) {
            glowLichen[i] = add(r, BlockGlowLichen.NAMES[i], new BlockGlowLichen(i), null);
            // The repeating-check light pass of 1.12 reads the block's plain light value.
            glowLichen[i].setLightLevel(7 / 15.0F);
            Content.multipart(glowLichen[i]);
        }
        CavesAndCliffsBackport.LOGGER.info("Registered {} lush cave blocks.", ALL.size());
    }

    private static <B extends Block> B add(IForgeRegistry<Block> r, String name, B block, String tool) {
        Content.block(r, name, block, tool);
        ALL.add(block);
        return block;
    }

    private static <B extends Block> B item(B block) {
        WITH_ITEM.add(block);
        return block;
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<Item> r = event.getRegistry();
        for (Block b : WITH_ITEM) {
            Content.registerBlockItem(r, b);
        }
        Content.blockItem(r, glowLichen[0], new ItemGlowLichen(glowLichen[0]));
        glowBerries = Content.item(r, "glow_berries", new ItemGlowBerries());
        OreDictionary.registerOre("treeLeaves", leaves);
        OreDictionary.registerOre("treeLeaves", floweringLeaves);

        // Current Java's composting chances.
        for (Block b : Arrays.asList(mossCarpet, smallDripleaf, hangingRoots, leaves)) {
            Compat.compost(0.3F, Item.getItemFromBlock(b));
        }
        Compat.compost(0.3F, glowBerries);
        for (Block b : Arrays.asList(floweringLeaves, glowLichen[0])) {
            Compat.compost(0.5F, Item.getItemFromBlock(b));
        }
        for (Block b : Arrays.asList(bigDripleaf, moss, sporeBlossom, azalea)) {
            Compat.compost(0.65F, Item.getItemFromBlock(b));
        }
        Compat.compost(0.85F, Item.getItemFromBlock(floweringAzalea));
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<IRecipe> r = event.getRegistry();
        ItemStack m = new ItemStack(moss);
        ShapedOreRecipe carpet = new ShapedOreRecipe(new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "moss_carpet"),
                new ItemStack(mossCarpet, 3), "MM", 'M', m);
        carpet.setRegistryName(Content.id("moss_carpet"));
        r.register(carpet);
        shapeless(r, "mossy_cobblestone_from_moss_block", new ItemStack(Blocks.MOSSY_COBBLESTONE), new ItemStack(Blocks.COBBLESTONE), m);
        shapeless(r, "mossy_stone_bricks_from_moss_block", new ItemStack(Blocks.STONEBRICK, 1, 1), new ItemStack(Blocks.STONEBRICK, 1, 0), m);
    }

    private static void shapeless(IForgeRegistry<IRecipe> r, String name, ItemStack out, Object... in) {
        ShapelessOreRecipe sr = new ShapelessOreRecipe(new ResourceLocation(CavesAndCliffsBackport.MOD_ID, name), out, in);
        sr.setRegistryName(Content.id(name));
        r.register(sr);
    }

    /** From the mod's init: events, what burns, and the caves. */
    public static void init() {
        if (!enabled()) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(new LushEvents());
        // Current Java's fire table: how easily each catches and how fast it burns away.
        Blocks.FIRE.setFireInfo(leaves, 30, 60);
        Blocks.FIRE.setFireInfo(floweringLeaves, 30, 60);
        Blocks.FIRE.setFireInfo(azalea, 30, 60);
        Blocks.FIRE.setFireInfo(floweringAzalea, 30, 60);
        Blocks.FIRE.setFireInfo(hangingRoots, 30, 60);
        Blocks.FIRE.setFireInfo(caveVines, 15, 60);
        Blocks.FIRE.setFireInfo(caveVinesPlant, 15, 60);
        Blocks.FIRE.setFireInfo(sporeBlossom, 60, 100);
        Blocks.FIRE.setFireInfo(bigDripleaf, 60, 100);
        Blocks.FIRE.setFireInfo(bigDripleafStem, 60, 100);
        Blocks.FIRE.setFireInfo(smallDripleaf, 60, 100);
        for (BlockGlowLichen b : glowLichen) {
            Blocks.FIRE.setFireInfo(b, 15, 100);
        }
        // After the ores and geodes (weight 0), so moss never ends up under a geode's shell.
        GameRegistry.registerWorldGenerator(new LushCaveGenerator(), 10);
    }
}
