package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stand-ins for another mod's ores (namespace {@value #NS}), registered only in a test build: real ore mods are
 * coremods or need their libraries, which the dev environment can't load. They cover what stream O1 must handle:
 * <ul>
 *   <li>{@code copper_ore}: drops itself, smelts, harvest level 1, hardness 3 (form: 4.5);</li>
 *   <li>{@code ore} with three metadata variants, hardness 5 (form: 7.5): 0 drops quartz with Fortune and experience
 *       (level 2), 1 drops 2-4 prisma crystals with Fortune (level 3), 2 is in no ore dictionary (a candidate that is
 *       not an ore);</li>
 *   <li>{@code nether_copper_ore} (a Nether ore, left out) and {@code excluded_ore} (excluded by the setting);</li>
 *   <li>{@code deepslate_tin_ore} (already a deepslate ore) and {@code pearl_ore} (an End ore by its translation key):
 *       left out with no form.</li>
 * </ul>
 * In the client test they draw as vanilla's iron, diamond, emerald and redstone ores
 * ({@link StandInOresClient}), so their forms can be held against 1.18's own deepslate ores.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class StandInOres {

    public static final String NS = "ccstandin";

    public static Block copperOre, gemOre, netherOre, excludedOre, sandOre, clathrateOre, deepslateOre, enderKeyOre;
    public static final List<Item> ITEMS = new ArrayList<Item>();

    private StandInOres() {
    }

    public static final class GemOre extends Block {
        public static final PropertyInteger VARIANT = PropertyInteger.create("variant", 0, 2);

        GemOre() {
            super(Material.ROCK);
            setHardness(5.0F);
            setResistance(5.0F);
            setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
            setHarvestLevel("pickaxe", 2, getDefaultState().withProperty(VARIANT, 0));
            setHarvestLevel("pickaxe", 3, getDefaultState().withProperty(VARIANT, 1));
            setHarvestLevel("pickaxe", 1, getDefaultState().withProperty(VARIANT, 2));
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, VARIANT);
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState().withProperty(VARIANT, MathHelper.clamp(meta, 0, 2));
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return state.getValue(VARIANT);
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            int v = state.getValue(VARIANT);
            return v == 0 ? Items.QUARTZ : v == 1 ? Items.PRISMARINE_CRYSTALS : Item.getItemFromBlock(this);
        }

        @Override
        public int damageDropped(IBlockState state) {
            return state.getValue(VARIANT) == 2 ? 2 : 0;
        }

        @Override
        public void getDrops(net.minecraft.util.NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
            Random rand = world instanceof World ? ((World) world).rand : RANDOM;
            int v = state.getValue(VARIANT);
            int count = v == 1 ? 2 + rand.nextInt(3) : 1;
            if (v != 2 && fortune > 0) {
                count *= Math.max(rand.nextInt(fortune + 2) - 1, 0) + 1;
            }
            drops.add(new ItemStack(getItemDropped(state, rand, fortune), count, damageDropped(state)));
        }

        @Override
        public int getExpDrop(IBlockState state, IBlockAccess world, BlockPos pos, int fortune) {
            Random rand = world instanceof World ? ((World) world).rand : new Random();
            int v = state.getValue(VARIANT);
            return v == 0 ? MathHelper.getInt(rand, 2, 5) : v == 1 ? MathHelper.getInt(rand, 1, 3) : 0;
        }
    }

    private static Block plain(float hardness, int level) {
        Block b = new Block(Material.ROCK) {
        };
        b.setHardness(hardness);
        b.setResistance(5.0F);
        b.setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
        b.setHarvestLevel("pickaxe", level);
        return b;
    }

    private static <B extends Block> B name(B b, String path) {
        b.setRegistryName(new ResourceLocation(NS, path));
        b.setTranslationKey(NS + "." + path);
        return b;
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> event) {
        // As a pack maker would set it, before the forms are made at LOWEST priority.
        List<String> ex = new ArrayList<String>(Arrays.asList(ModConfig.deepslate.moddedOresExclude));
        if (!ex.contains(NS + ":excluded_ore")) {
            ex.add(NS + ":excluded_ore");
            ModConfig.deepslate.moddedOresExclude = ex.toArray(new String[0]);
        }
        copperOre = name(plain(3.0F, 1), "copper_ore");
        gemOre = name(new GemOre(), "ore");
        netherOre = name(plain(3.0F, 1), "nether_copper_ore");
        excludedOre = name(plain(3.0F, 1), "excluded_ore");
        // An oil-sand-like ore (rock, but dug with a shovel) and a Thermal-like clathrate in end stone.
        sandOre = name(plain(3.0F, 1), "sand_ore");
        sandOre.setHarvestLevel("shovel", 1);
        clathrateOre = name(plain(3.0F, 1), "ore_fluid");
        // A pack's own deepslate ore (left out), and an End ore told only by its translation key (left out, no form).
        deepslateOre = name(plain(4.5F, 1), "deepslate_tin_ore");
        enderKeyOre = name(plain(3.0F, 1), "pearl_ore");
        enderKeyOre.setTranslationKey(NS + ".enderPearlOre");
        event.getRegistry().registerAll(copperOre, gemOre, netherOre, excludedOre, sandOre, clathrateOre, deepslateOre, enderKeyOre);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        for (Block b : new Block[] {copperOre, netherOre, excludedOre, sandOre, clathrateOre}) {
            ITEMS.add(new ItemBlock(b).setRegistryName(b.getRegistryName()));
        }
        ItemBlock gem = new ItemBlock(gemOre) {
            @Override
            public int getMetadata(int damage) {
                return damage;
            }

            @Override
            public String getTranslationKey(ItemStack stack) {
                return super.getTranslationKey() + "." + stack.getMetadata();
            }
        };
        gem.setHasSubtypes(true);
        gem.setRegistryName(gemOre.getRegistryName());
        ITEMS.add(gem);
        for (Item i : ITEMS) {
            event.getRegistry().register(i);
        }
        OreDictionary.registerOre("oreCopper", copperOre);
        OreDictionary.registerOre("oreQuartzStandin", new ItemStack(gemOre, 1, 0));
        OreDictionary.registerOre("oreAquaStandin", new ItemStack(gemOre, 1, 1));
        OreDictionary.registerOre("blockStandinDisplay", new ItemStack(gemOre, 1, 2));
        OreDictionary.registerOre("oreNetherCopper", netherOre);
        OreDictionary.registerOre("oreExcluded", excludedOre);
        OreDictionary.registerOre("oreSandStandin", sandOre);
        OreDictionary.registerOre("oreClathrateEnder", clathrateOre);
    }

    /** After every item: a late ore dictionary name (reaches the form through the registration event) and smelting. */
    @SubscribeEvent
    public static void recipes(RegistryEvent.Register<IRecipe> event) {
        OreDictionary.registerOre("oreCopperLate", copperOre);
        FurnaceRecipes.instance().addSmeltingRecipe(new ItemStack(copperOre), new ItemStack(Items.IRON_NUGGET, 3), 0.5F);
    }
}
