package com.exiledradio.cavesandcliffsbackport.deepslate;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.BlockBasic;
import com.exiledradio.cavesandcliffsbackport.common.BlockCCSlab;
import com.exiledradio.cavesandcliffsbackport.common.BlockCCStairs;
import com.exiledradio.cavesandcliffsbackport.common.BlockCCWall;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.common.ModSounds;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSlab;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stream D: deepslate and its four sets, calcite, tuff, smooth basalt, the seven deepslate ores, raw iron
 * and raw gold with their blocks. Raw ore drops from vanilla's ores are {@link RawOreDrops}; the
 * deepslate layer in new chunks is {@link DeepslateLayer}.
 *
 * <p>Numbers (hardness / blast resistance, current Java): deepslate 3/6; cobbled, polished, bricks,
 * tiles, their cracked and chiseled forms and every stair, slab and wall 3.5/6; deepslate ores 4.5/3;
 * infested deepslate 1.5/0.75; calcite 0.75/0.75; tuff 1.5/6; smooth basalt 1.25/4.2; raw ore blocks 5/6.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class DeepslateContent {

    /** The four deepslate kinds that have stairs, slabs and walls. */
    public static final String[] SETS = {"cobbled_deepslate", "polished_deepslate", "deepslate_brick", "deepslate_tile"};

    public static BlockCCPillar deepslate;
    public static BlockInfestedDeepslate infestedDeepslate;
    public static final Map<String, Block> BLOCKS = new LinkedHashMap<String, Block>();
    public static final Map<String, BlockCCSlab.Half> SLABS = new LinkedHashMap<String, BlockCCSlab.Half>();
    public static final Map<String, BlockCCSlab.Double> DOUBLE_SLABS = new LinkedHashMap<String, BlockCCSlab.Double>();
    /** Stone ore (registry name) to our deepslate ore, for the layer pass. */
    public static final Map<String, String> ORE_NAMES = new LinkedHashMap<String, String>();

    public static Item rawIron, rawGold;

    private static final List<Block> ALL = new ArrayList<Block>();

    private DeepslateContent() {
    }

    public static boolean enabled() {
        return ModConfig.deepslate.enabled;
    }

    public static List<Block> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Full block name for a set ("deepslate_brick" -> "deepslate_bricks"). */
    public static String full(String set) {
        return set.endsWith("_brick") || set.endsWith("_tile") ? set + "s" : set;
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<Block> r = event.getRegistry();
        SoundType deepslateSound = ModSounds.type("block.deepslate");
        MapColor dark = MapColor.STONE;

        deepslate = add(r, "deepslate", new BlockCCPillar(Material.ROCK, dark, deepslateSound, 3.0F, 6.0F), "pickaxe");
        Map<String, SoundType> setSounds = new LinkedHashMap<String, SoundType>();
        setSounds.put("cobbled_deepslate", deepslateSound);
        setSounds.put("polished_deepslate", ModSounds.type("block.polished_deepslate"));
        setSounds.put("deepslate_brick", ModSounds.type("block.deepslate_bricks"));
        setSounds.put("deepslate_tile", ModSounds.type("block.deepslate_tiles"));
        for (String set : SETS) {
            Block full = add(r, full(set), new BlockBasic(Material.ROCK, dark, setSounds.get(set), 3.5F, 6.0F), "pickaxe");
            IBlockState model = full.getDefaultState();
            add(r, set + "_stairs", new BlockCCStairs(model), "pickaxe");
            BlockCCSlab.Half half = add(r, set + "_slab", new BlockCCSlab.Half(model), "pickaxe");
            BlockCCSlab.Double dbl = add(r, set + "_double_slab", new BlockCCSlab.Double(model, half), "pickaxe");
            SLABS.put(set, half);
            DOUBLE_SLABS.put(set, dbl);
            // 162 actual states drawn from one multipart file: one model name for all of them.
            Content.multipart(add(r, set + "_wall", new BlockCCWall(model), "pickaxe"));
        }
        deepslate.dropsWithoutSilkTouch(BLOCKS.get("cobbled_deepslate").getDefaultState());
        add(r, "chiseled_deepslate", new BlockBasic(Material.ROCK, dark, deepslateSound, 3.5F, 6.0F), "pickaxe");
        add(r, "cracked_deepslate_bricks", new BlockBasic(Material.ROCK, dark, setSounds.get("deepslate_brick"), 3.5F, 6.0F), "pickaxe");
        add(r, "cracked_deepslate_tiles", new BlockBasic(Material.ROCK, dark, setSounds.get("deepslate_tile"), 3.5F, 6.0F), "pickaxe");
        infestedDeepslate = add(r, "infested_deepslate", new BlockInfestedDeepslate(dark, deepslateSound), null);

        add(r, "calcite", new BlockBasic(Material.ROCK, MapColor.WHITE_STAINED_HARDENED_CLAY, ModSounds.type("block.calcite"), 0.75F, 0.75F), "pickaxe");
        add(r, "tuff", new BlockBasic(Material.ROCK, MapColor.GRAY_STAINED_HARDENED_CLAY, ModSounds.type("block.tuff"), 1.5F, 6.0F), "pickaxe");
        add(r, "smooth_basalt", new BlockBasic(Material.ROCK, MapColor.BLACK, ModSounds.type("block.basalt"), 1.25F, 4.2F), "pickaxe");

        // Ores: harvest levels as their stone forms (0 wood, 1 stone, 2 iron).
        ore(r, "coal", new BlockCCOre(dark, deepslateSound, () -> Items.COAL, 0, 1, 1, BlockCCOre.Bonus.MULTIPLY, 0, 2), 0);
        ore(r, "iron", new BlockCCOre(dark, deepslateSound, () -> ModConfig.deepslate.rawOreDrops ? rawIron : null, 0, 1, 1, BlockCCOre.Bonus.MULTIPLY, 0, 0), 1);
        ore(r, "gold", new BlockCCOre(dark, deepslateSound, () -> ModConfig.deepslate.rawOreDrops ? rawGold : null, 0, 1, 1, BlockCCOre.Bonus.MULTIPLY, 0, 0), 2);
        ore(r, "redstone", new BlockDeepslateRedstoneOre(dark, deepslateSound), 2);
        ore(r, "lapis", new BlockCCOre(dark, deepslateSound, () -> Items.DYE, EnumDyeColor.BLUE.getDyeDamage(), 4, 9, BlockCCOre.Bonus.MULTIPLY, 2, 5), 1);
        ore(r, "diamond", new BlockCCOre(dark, deepslateSound, () -> Items.DIAMOND, 0, 1, 1, BlockCCOre.Bonus.MULTIPLY, 3, 7), 2);
        ore(r, "emerald", new BlockCCOre(dark, deepslateSound, () -> Items.EMERALD, 0, 1, 1, BlockCCOre.Bonus.MULTIPLY, 3, 7), 2);

        Block rawIronBlock = add(r, "raw_iron_block", new BlockBasic(Material.ROCK, MapColor.WHITE_STAINED_HARDENED_CLAY, SoundType.STONE, 5.0F, 6.0F), "pickaxe");
        rawIronBlock.setHarvestLevel("pickaxe", 1);
        Block rawGoldBlock = add(r, "raw_gold_block", new BlockBasic(Material.ROCK, MapColor.GOLD, SoundType.STONE, 5.0F, 6.0F), "pickaxe");
        rawGoldBlock.setHarvestLevel("pickaxe", 2);

        com.exiledradio.backportcore.ownership.BlockOwnership.declare(CavesAndCliffsBackport.MOD_ID,
                BLOCKS.keySet().toArray(new String[0]));
        com.exiledradio.backportcore.ownership.BlockOwnership.provides(CavesAndCliffsBackport.MOD_ID, DeepslateContent::enabled);
        CavesAndCliffsBackport.LOGGER.info("Registered {} deepslate and stone blocks.", ALL.size());
    }

    private static void ore(IForgeRegistry<Block> r, String kind, BlockCCOre block, int level) {
        String name = "deepslate_" + kind + "_ore";
        add(r, name, block, "pickaxe").setHarvestLevel("pickaxe", level);
        ORE_NAMES.put("minecraft:" + kind + "_ore", name);
    }

    private static <B extends Block> B add(IForgeRegistry<Block> r, String name, B block, String tool) {
        Content.block(r, name, block, tool);
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
            if (b instanceof BlockCCSlab.Double) {
                continue;
            }
            if (b instanceof BlockCCSlab.Half) {
                String set = e.getKey().substring(0, e.getKey().length() - "_slab".length());
                Content.blockItem(r, b, new ItemSlab(b, (BlockCCSlab.Half) b, DOUBLE_SLABS.get(set)));
            } else {
                Content.registerBlockItem(r, b);
            }
        }
        infestedDeepslate.silkTouchGives(new ItemStack(deepslate));
        rawIron = Content.item(r, "raw_iron", new Item());
        rawGold = Content.item(r, "raw_gold", new Item());

        // Ore dictionary: the stone ores' names, so machines and other mods' recipes take ours.
        String[][] ores = {{"coal", "oreCoal"}, {"iron", "oreIron"}, {"gold", "oreGold"}, {"redstone", "oreRedstone"},
                {"lapis", "oreLapis"}, {"diamond", "oreDiamond"}, {"emerald", "oreEmerald"}};
        for (String[] o : ores) {
            OreDictionary.registerOre(o[1], BLOCKS.get("deepslate_" + o[0] + "_ore"));
        }
        OreDictionary.registerOre("rawIron", rawIron);
        OreDictionary.registerOre("rawGold", rawGold);
        OreDictionary.registerOre("blockRawIron", BLOCKS.get("raw_iron_block"));
        OreDictionary.registerOre("blockRawGold", BLOCKS.get("raw_gold_block"));
        // Current Java makes stone tools, furnaces and the other cobblestone things from cobbled deepslate.
        OreDictionary.registerOre("cobblestone", BLOCKS.get("cobbled_deepslate"));
        OreDictionary.registerOre("stone", BLOCKS.get("deepslate"));
        OreDictionary.registerOre("stoneCalcite", BLOCKS.get("calcite"));
        OreDictionary.registerOre("stoneTuff", BLOCKS.get("tuff"));
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<IRecipe> r = event.getRegistry();
        ItemStack cobbled = stack("cobbled_deepslate", 1);
        shaped(r, "polished_deepslate", stack("polished_deepslate", 4), "XX", "XX", 'X', cobbled);
        shaped(r, "deepslate_bricks", stack("deepslate_bricks", 4), "XX", "XX", 'X', stack("polished_deepslate", 1));
        shaped(r, "deepslate_tiles", stack("deepslate_tiles", 4), "XX", "XX", 'X', stack("deepslate_bricks", 1));
        shaped(r, "chiseled_deepslate", stack("chiseled_deepslate", 1), "X", "X", 'X', new ItemStack(SLABS.get("cobbled_deepslate")));
        for (String set : SETS) {
            ItemStack base = stack(full(set), 1);
            shaped(r, set + "_stairs", stack(set + "_stairs", 4), "X  ", "XX ", "XXX", 'X', base);
            shaped(r, set + "_slab", new ItemStack(SLABS.get(set), 6), "XXX", 'X', base);
            shaped(r, set + "_wall", stack(set + "_wall", 6), "XXX", "XXX", 'X', base);
        }
        shaped(r, "raw_iron_block", stack("raw_iron_block", 1), "XXX", "XXX", "XXX", 'X', new ItemStack(rawIron));
        shaped(r, "raw_gold_block", stack("raw_gold_block", 1), "XXX", "XXX", "XXX", 'X', new ItemStack(rawGold));
        shaped(r, "raw_iron_from_block", new ItemStack(rawIron, 9), "X", 'X', stack("raw_iron_block", 1));
        shaped(r, "raw_gold_from_block", new ItemStack(rawGold, 9), "X", 'X', stack("raw_gold_block", 1));
    }

    private static ItemStack stack(String name, int count) {
        return new ItemStack(BLOCKS.get(name), count);
    }

    private static void shaped(IForgeRegistry<IRecipe> r, String name, ItemStack out, Object... recipe) {
        ShapedOreRecipe sr = new ShapedOreRecipe(new ResourceLocation(CavesAndCliffsBackport.MOD_ID, name), out, recipe);
        sr.setRegistryName(Content.id(name));
        r.register(sr);
    }

    /** Smelting, from the mod's init (after every registry event). */
    public static void init() {
        if (!enabled()) {
            return;
        }
        FurnaceRecipes f = FurnaceRecipes.instance();
        f.addSmeltingRecipe(stack("cobbled_deepslate", 1), stack("deepslate", 1), 0.1F);
        f.addSmeltingRecipe(stack("deepslate_bricks", 1), stack("cracked_deepslate_bricks", 1), 0.1F);
        f.addSmeltingRecipe(stack("deepslate_tiles", 1), stack("cracked_deepslate_tiles", 1), 0.1F);
        f.addSmeltingRecipe(stack("deepslate_coal_ore", 1), new ItemStack(Items.COAL), 0.1F);
        f.addSmeltingRecipe(stack("deepslate_iron_ore", 1), new ItemStack(Items.IRON_INGOT), 0.7F);
        f.addSmeltingRecipe(stack("deepslate_gold_ore", 1), new ItemStack(Items.GOLD_INGOT), 1.0F);
        f.addSmeltingRecipe(stack("deepslate_redstone_ore", 1), new ItemStack(Items.REDSTONE), 0.7F);
        f.addSmeltingRecipe(stack("deepslate_lapis_ore", 1), new ItemStack(Items.DYE, 1, EnumDyeColor.BLUE.getDyeDamage()), 0.2F);
        f.addSmeltingRecipe(stack("deepslate_diamond_ore", 1), new ItemStack(Items.DIAMOND), 1.0F);
        f.addSmeltingRecipe(stack("deepslate_emerald_ore", 1), new ItemStack(Items.EMERALD), 1.0F);
        f.addSmelting(rawIron, new ItemStack(Items.IRON_INGOT), 0.7F);
        f.addSmelting(rawGold, new ItemStack(Items.GOLD_INGOT), 1.0F);
        // Nether Update Backport's basalt smelts into our smooth basalt (Rule 2).
        Item basalt = Owners.item(Owners.NETHER_UPDATE, "basalt");
        if (Owners.present(Owners.NETHER_UPDATE) && basalt != null) {
            f.addSmeltingRecipe(new ItemStack(basalt, 1, OreDictionary.WILDCARD_VALUE), stack("smooth_basalt", 1), 0.1F);
        }
        GameRegistry.registerWorldGenerator(new DeepslateLayer(), Integer.MAX_VALUE);
    }
}
