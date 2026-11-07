package com.exiledradio.cavesandcliffsbackport.copper;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.BlockBasic;
import com.exiledradio.cavesandcliffsbackport.common.BlockCCOre;
import com.exiledradio.cavesandcliffsbackport.common.BlockCCSlab;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.common.ModSounds;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSlab;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stream Cu: copper ore and deepslate copper ore, raw copper and its block, the copper ingot, the 32
 * copper blocks (block, cut, cut stairs, cut slab; four stages; waxed and not) plus their double slabs,
 * and the lightning rod. Oxidation, waxing and scraping are {@link Weathering} and
 * {@link CopperInteractions}; lightning is {@link Lightning}; ore generation {@link CopperOreGenerator};
 * drowned drops {@link DrownedCopper}.
 *
 * <p>Registry names are 1.18's: {@code copper_block}, {@code exposed_copper}, {@code cut_copper},
 * {@code exposed_cut_copper_stairs}, {@code waxed_weathered_cut_copper_slab} and so on; double slabs are
 * {@code <slab name minus _slab>_double_slab} ({@code cut_copper_double_slab}).
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class CopperContent {

    /** The four shapes' name stems; the full block's name comes from {@link CopperStage#blockName()}. */
    public static final String[] SHAPES = {"block", "cut_copper", "cut_copper_stairs", "cut_copper_slab"};

    public static final Map<String, Block> BLOCKS = new LinkedHashMap<String, Block>();
    public static final Map<String, CopperBlocks.DoubleSlab> DOUBLE_SLABS = new LinkedHashMap<String, CopperBlocks.DoubleSlab>();
    public static Item copperIngot, rawCopper;
    public static BlockLightningRod lightningRod;
    public static SoundEvent waxOn, waxOff, scrape;

    private static final List<Block> ALL = new ArrayList<Block>();

    private CopperContent() {
    }

    public static boolean enabled() {
        return ModConfig.copper.enabled;
    }

    public static List<Block> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** @return the registry name of a shape at a stage, waxed or not */
    public static String name(String shape, CopperStage stage, boolean waxed) {
        String base = "block".equals(shape) ? stage.blockName() : stage.prefix + shape;
        return waxed ? "waxed_" + base : base;
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<Block> r = event.getRegistry();
        SoundType copper = ModSounds.type("block.copper");
        waxOn = ModSounds.event("item.honeycomb.wax_on");
        waxOff = ModSounds.event("item.axe.wax_off");
        scrape = ModSounds.event("item.axe.scrape");

        add(r, "copper_ore", new BlockCCOre(MapColor.STONE, SoundType.STONE, 3.0F, 3.0F, () -> rawCopper, 0, 2, 5,
                BlockCCOre.Bonus.MULTIPLY, 0, 0), 1);
        add(r, "deepslate_copper_ore", new BlockCCOre(MapColor.STONE, ModSounds.type("block.deepslate"), 4.5F, 3.0F,
                () -> rawCopper, 0, 2, 5, BlockCCOre.Bonus.MULTIPLY, 0, 0), 1);
        add(r, "raw_copper_block", new BlockBasic(Material.ROCK, MapColor.ADOBE, SoundType.STONE, 5.0F, 6.0F), 1);

        for (boolean waxed : new boolean[] {false, true}) {
            for (CopperStage stage : CopperStage.values()) {
                boolean ages = !waxed && stage != CopperStage.OXIDIZED;
                add(r, name("block", stage, waxed), new CopperBlocks.Full(stage, copper, ages), 1);
                Block cut = add(r, name("cut_copper", stage, waxed), new CopperBlocks.Full(stage, copper, ages), 1);
                add(r, name("cut_copper_stairs", stage, waxed), new CopperBlocks.Stairs(cut.getDefaultState(), ages), 1);
                String slab = name("cut_copper_slab", stage, waxed);
                CopperBlocks.Slab half = add(r, slab, new CopperBlocks.Slab(cut.getDefaultState(), ages), 1);
                String dbl = slab.substring(0, slab.length() - "_slab".length()) + "_double_slab";
                DOUBLE_SLABS.put(slab, add(r, dbl, new CopperBlocks.DoubleSlab(cut.getDefaultState(), half, ages), 1));
            }
        }
        lightningRod = add(r, "lightning_rod", new BlockLightningRod(copper), 1);

        for (String shape : SHAPES) {
            Block[] chain = new Block[4];
            Block[] doubles = new Block[4];
            for (CopperStage stage : CopperStage.values()) {
                Block plain = BLOCKS.get(name(shape, stage, false));
                chain[stage.ordinal()] = plain;
                Weathering.waxed(plain, BLOCKS.get(name(shape, stage, true)));
                if ("cut_copper_slab".equals(shape)) {
                    doubles[stage.ordinal()] = DOUBLE_SLABS.get(name(shape, stage, false));
                    Weathering.waxed(doubles[stage.ordinal()], DOUBLE_SLABS.get(name(shape, stage, true)));
                }
            }
            Weathering.chain(chain);
            if ("cut_copper_slab".equals(shape)) {
                Weathering.chain(doubles);
            }
        }

        GameRegistry.registerTileEntity(BlockLightningRod.TileEntityLightningRod.class, Content.id("lightning_rod"));
        CavesAndCliffsBackport.LOGGER.info("Registered {} copper blocks.", ALL.size());
    }

    /** Every block name this stream registers, known before registration, for Core's ownership table. */
    public static String[] blockNames() {
        List<String> n = new ArrayList<String>(java.util.Arrays.asList("copper_ore", "deepslate_copper_ore", "raw_copper_block"));
        for (boolean waxed : new boolean[] {false, true}) {
            for (CopperStage stage : CopperStage.values()) {
                for (String shape : SHAPES) {
                    n.add(name(shape, stage, waxed));
                }
                String slab = name("cut_copper_slab", stage, waxed);
                n.add(slab.substring(0, slab.length() - "_slab".length()) + "_double_slab");
            }
        }
        n.add("lightning_rod");
        return n.toArray(new String[0]);
    }

    /** From the mod's pre-init: tells Core which blocks are ours (or, switched off, that nobody provides them). */
    public static void preInit() {
        Owners.declare(enabled(), blockNames());
    }

    private static <B extends Block> B add(IForgeRegistry<Block> r, String name, B block, int level) {
        Content.block(r, name, block, "pickaxe");
        block.setHarvestLevel("pickaxe", level);
        BLOCKS.put(name, block);
        ALL.add(block);
        return block;
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<Item> r = event.getRegistry();
        for (Map.Entry<String, Block> e : BLOCKS.entrySet()) {
            Block b = e.getValue();
            if (b instanceof CopperBlocks.DoubleSlab) {
                continue;
            }
            if (b instanceof CopperBlocks.Slab) {
                Content.blockItem(r, b, new ItemSlab(b, (BlockCCSlab.Half) b, DOUBLE_SLABS.get(e.getKey())));
            } else {
                Content.registerBlockItem(r, b);
            }
        }
        copperIngot = Content.item(r, "copper_ingot", new Item());
        rawCopper = Content.item(r, "raw_copper", new Item());

        OreDictionary.registerOre("oreCopper", BLOCKS.get("copper_ore"));
        OreDictionary.registerOre("oreCopper", BLOCKS.get("deepslate_copper_ore"));
        OreDictionary.registerOre("ingotCopper", copperIngot);
        OreDictionary.registerOre("rawCopper", rawCopper);
        OreDictionary.registerOre("blockCopper", BLOCKS.get("copper_block"));
        OreDictionary.registerOre("blockRawCopper", BLOCKS.get("raw_copper_block"));
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!enabled()) {
            return;
        }
        Weathering.registerWaxOres();
        IForgeRegistry<IRecipe> r = event.getRegistry();
        ItemStack ingot = new ItemStack(copperIngot);
        shaped(r, "copper_block", stack("copper_block", 1), "XXX", "XXX", "XXX", 'X', ingot);
        shaped(r, "copper_ingot", new ItemStack(copperIngot, 9), "X", 'X', stack("copper_block", 1));
        shaped(r, "copper_ingot_from_waxed_copper_block", new ItemStack(copperIngot, 9), "X", 'X', stack("waxed_copper_block", 1));
        shaped(r, "raw_copper_block", stack("raw_copper_block", 1), "XXX", "XXX", "XXX", 'X', new ItemStack(rawCopper));
        shaped(r, "raw_copper", new ItemStack(rawCopper, 9), "X", 'X', stack("raw_copper_block", 1));
        shaped(r, "lightning_rod", stack("lightning_rod", 1), "X", "X", "X", 'X', ingot);
        for (boolean waxed : new boolean[] {false, true}) {
            for (CopperStage stage : CopperStage.values()) {
                ItemStack full = stack(name("block", stage, waxed), 1);
                ItemStack cut = stack(name("cut_copper", stage, waxed), 1);
                shaped(r, name("cut_copper", stage, waxed), stack(name("cut_copper", stage, waxed), 4), "XX", "XX", 'X', full);
                shaped(r, name("cut_copper_stairs", stage, waxed), stack(name("cut_copper_stairs", stage, waxed), 4), "X  ", "XX ", "XXX", 'X', cut);
                shaped(r, name("cut_copper_slab", stage, waxed), stack(name("cut_copper_slab", stage, waxed), 6), "XXX", 'X', cut);
            }
        }
        // Waxing in the crafting grid: any copper block and honeycomb.
        for (String shape : SHAPES) {
            for (CopperStage stage : CopperStage.values()) {
                String waxed = name(shape, stage, true);
                ShapelessOreRecipe sr = new ShapelessOreRecipe(new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "waxed_copper"),
                        stack(waxed, 1), stack(name(shape, stage, false), 1), "honeycomb");
                sr.setRegistryName(Content.id(waxed + "_from_honeycomb"));
                r.register(sr);
            }
        }
    }

    private static ItemStack stack(String name, int count) {
        return new ItemStack(BLOCKS.get(name), count);
    }

    private static void shaped(IForgeRegistry<IRecipe> r, String name, ItemStack out, Object... recipe) {
        ShapedOreRecipe sr = new ShapedOreRecipe(new ResourceLocation(CavesAndCliffsBackport.MOD_ID, name), out, recipe);
        sr.setRegistryName(Content.id(name));
        r.register(sr);
    }

    /** Smelting and world generation, from the mod's init. */
    public static void init() {
        if (!enabled()) {
            return;
        }
        FurnaceRecipes f = FurnaceRecipes.instance();
        f.addSmeltingRecipe(stack("copper_ore", 1), new ItemStack(copperIngot), 0.7F);
        f.addSmeltingRecipe(stack("deepslate_copper_ore", 1), new ItemStack(copperIngot), 0.7F);
        f.addSmelting(rawCopper, new ItemStack(copperIngot), 0.7F);
        GameRegistry.registerWorldGenerator(new CopperOreGenerator(), 0);
    }
}
