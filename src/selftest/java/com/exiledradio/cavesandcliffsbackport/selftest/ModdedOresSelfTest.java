package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.deepslate.DeepslateContent;
import com.exiledradio.cavesandcliffsbackport.deepslate.DeepslateLayer;
import com.exiledradio.cavesandcliffsbackport.deepslate.ModdedOres;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.List;

/**
 * Stream O1: deepslate forms of other mods' ores, on {@link StandInOres}. Forms are registered for every candidate
 * and none for the Nether ore or the excluded one; drops, experience and Fortune are the original's (seeded alike);
 * ore dictionary names (a late one too) and smelting are copied; hardness and harvest level follow 1.18's rules; the
 * candidate that is in no ore dictionary is hidden and never placed; the Default-world layer swaps the ores.
 */
public final class ModdedOresSelfTest implements SelfTest {

    @Override
    public String name() {
        return "moddedores";
    }

    private static ModdedOres.Form form(Check c, Block original, int meta) {
        for (ModdedOres.Form f : ModdedOres.forms()) {
            if (f.original.getBlock() == original && f.meta == meta) {
                return f;
            }
        }
        c.fail("no form for " + original.getRegistryName() + ":" + meta);
        return null;
    }

    @Override
    public void run(Check c) {
        c.ok(ModdedOres.enabled(), "deepslate.moddedOres is on by default");
        ModdedOres.Form copper = form(c, StandInOres.copperOre, 0);
        ModdedOres.Form quartz = form(c, StandInOres.gemOre, 0);
        ModdedOres.Form aqua = form(c, StandInOres.gemOre, 1);
        ModdedOres.Form display = form(c, StandInOres.gemOre, 2);
        if (copper == null || quartz == null || aqua == null || display == null) {
            return;
        }
        // Names and registration.
        c.eq(new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "deepslate/ccstandin/copper_ore"), copper.block().getRegistryName(), "copper's form name");
        c.eq(new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "deepslate/ccstandin/ore_1"), aqua.block().getRegistryName(), "a variant's form name");
        c.eq(copper.block(), ForgeRegistries.BLOCKS.getValue(copper.block().getRegistryName()), "the form is in the block registry");
        c.eq(copper.item(), Item.getItemFromBlock(copper.block()), "the form has an item");
        for (ModdedOres.Form f : ModdedOres.forms()) {
            Block o = f.original.getBlock();
            c.ok(o != StandInOres.netherOre && o != StandInOres.excludedOre, "no form for " + o.getRegistryName());
            c.ok(!"minecraft".equals(o.getRegistryName().getNamespace()) && !o.getRegistryName().getNamespace().endsWith("backport"),
                    "no form for vanilla's or the family's ores: " + o.getRegistryName());
        }
        c.ok(ModdedOres.leftOut("ccstandin:nether_copper_ore") != null, "the Nether ore is listed as left out");
        c.ok(ModdedOres.leftOut("ccstandin:excluded_ore") != null, "the excluded ore is listed as left out");
        c.ok(ModdedOres.leftOut("ccstandin:deepslate_tin_ore") != null && ModdedOres.form(StandInOres.deepslateOre) == null
                && forBlock(StandInOres.deepslateOre).isEmpty(), "an ore that is already a deepslate ore gets no form");
        c.ok(ModdedOres.leftOut("ccstandin:pearl_ore") != null && forBlock(StandInOres.enderKeyOre).isEmpty(),
                "an End ore told by its translation key gets no form");

        // Confirmed against the ore dictionary.
        c.ok(copper.confirmed() && quartz.confirmed() && aqua.confirmed(), "the stand-in ores are confirmed");
        c.ok(!display.confirmed(), "a candidate in no ore* name is not confirmed");
        ModdedOres.Form sand = form(c, StandInOres.sandOre, 0), clathrate = form(c, StandInOres.clathrateOre, 0);
        c.ok(sand != null && !sand.confirmed(), "an ore dug with a shovel (oil sand) is not confirmed");
        c.ok(clathrate != null && !clathrate.confirmed(), "an ore under a Nether or End ore name (oreClathrateEnder) is not confirmed");
        c.eq(0, display.item().getCreativeTabs().length, "an unconfirmed candidate has no creative tab (so JEI leaves it out)");
        c.ok(copper.item().getCreativeTabs().length == 1, "a confirmed form is in a creative tab");

        // 1.18's numbers.
        BlockPos at = c.area();
        c.eq(4.5F, copper.block().getDefaultState().getBlockHardness(c.world, at), "hardness of a 3.0 ore's form");
        c.eq(7.5F, quartz.block().getDefaultState().getBlockHardness(c.world, at), "hardness of a 5.0 ore's form (1.5x)");
        c.eq("pickaxe", copper.block().getHarvestTool(copper.block().getDefaultState()), "harvest tool");
        c.eq(1, copper.block().getHarvestLevel(copper.block().getDefaultState()), "copper's harvest level");
        c.eq(2, quartz.block().getHarvestLevel(quartz.block().getDefaultState()), "variant 0's harvest level");
        c.eq(3, aqua.block().getHarvestLevel(aqua.block().getDefaultState()), "variant 1's harvest level");
        c.ok(copper.block().getSoundType() == DeepslateContent.deepslate.getSoundType(), "deepslate's sound");

        // Drops, experience, Fortune: the same as the original's with the same random numbers.
        for (ModdedOres.Form f : new ModdedOres.Form[] {copper, quartz, aqua}) {
            int diffs = 0, expDiffs = 0, total = 0;
            for (int fortune = 0; fortune <= 3; fortune++) {
                for (long seed = 1; seed <= 40; seed++) {
                    c.world.rand.setSeed(seed);
                    NonNullList<ItemStack> theirs = NonNullList.create();
                    f.original.getBlock().getDrops(theirs, c.world, at, f.original, fortune);
                    int theirExp = f.original.getBlock().getExpDrop(f.original, c.world, at, fortune);
                    c.world.rand.setSeed(seed);
                    NonNullList<ItemStack> ours = NonNullList.create();
                    f.block().getDrops(ours, c.world, at, f.block().getDefaultState(), fortune);
                    int ourExp = f.block().getExpDrop(f.block().getDefaultState(), c.world, at, fortune);
                    if (!sameDrops(f, theirs, ours)) {
                        diffs++;
                    }
                    if (theirExp != ourExp) {
                        expDiffs++;
                    }
                    total += count(ours);
                }
            }
            c.eq(0, diffs, f + ": drops differ from the original's");
            c.eq(0, expDiffs, f + ": experience differs from the original's");
            c.ok(total > 0, f + " drops something");
        }
        NonNullList<ItemStack> self = NonNullList.create();
        copper.block().getDrops(self, c.world, at, copper.block().getDefaultState(), 0);
        c.ok(self.size() == 1 && self.get(0).getItem() == copper.item(), "an ore that drops itself: the form drops itself: " + self);
        NonNullList<ItemStack> fortuneQuartz = NonNullList.create();
        int most = 0;
        for (long seed = 1; seed <= 40; seed++) {
            c.world.rand.setSeed(seed);
            fortuneQuartz.clear();
            quartz.block().getDrops(fortuneQuartz, c.world, at, quartz.block().getDefaultState(), 3);
            most = Math.max(most, count(fortuneQuartz));
        }
        c.ok(most > 1, "Fortune III multiplies the form's drops: up to " + most);
        c.ok(copper.block().canSilkHarvest(c.world, at, copper.block().getDefaultState(), null), "Silk Touch works when it does on the original");

        // Ore dictionary and smelting.
        c.ok(names(copper).contains("oreCopper"), "copper's form is oreCopper: " + names(copper));
        c.ok(names(copper).contains("oreCopperLate"), "a name registered after the items reaches the form: " + names(copper));
        c.ok(names(quartz).contains("oreQuartzStandin") && !names(quartz).contains("oreAquaStandin"), "variant 0's name only: " + names(quartz));
        c.ok(!names(display).contains("blockStandinDisplay"), "a non-ore name is not copied: " + names(display));
        ItemStack smelted = FurnaceRecipes.instance().getSmeltingResult(new ItemStack(copper.item()));
        c.ok(smelted.getItem() == Items.IRON_NUGGET && smelted.getCount() == 3, "copper's form smelts as the original: " + smelted);
        // 1.12 looks the experience up by the result, so it is the original's whatever was registered.
        c.eq(FurnaceRecipes.instance().getSmeltingExperience(FurnaceRecipes.instance().getSmeltingResult(new ItemStack(StandInOres.copperOre))),
                FurnaceRecipes.instance().getSmeltingExperience(smelted), "smelting experience");

        // The lookup the Caves & Cliffs placement uses.
        c.eq(copper.block().getDefaultState(), ModdedOres.deepslateForm(StandInOres.copperOre.getDefaultState()), "lookup: copper");
        c.eq(aqua.block().getDefaultState(), ModdedOres.deepslateForm(StandInOres.gemOre.getStateFromMeta(1)), "lookup: a variant");
        c.eq(null, ModdedOres.deepslateForm(StandInOres.gemOre.getStateFromMeta(2)), "lookup: an unconfirmed candidate");
        c.eq(null, ModdedOres.deepslateForm(StandInOres.netherOre.getDefaultState()), "lookup: the Nether ore");
        c.eq(null, ModdedOres.deepslateForm(Blocks.IRON_ORE.getDefaultState()), "lookup: vanilla's ore (stream D swaps it)");

        // Names.
        String name = copper.item().getItemStackDisplayName(new ItemStack(copper.item()));
        c.ok(name.startsWith("Deepslate ") && name.endsWith(new ItemStack(StandInOres.copperOre).getDisplayName()), "the form's name: " + name);

        names(c);
        include(c);
        layer(c);
    }

    /** The name filter: "ore" words, and words ending in "ore" that are not ores only as whole words. */
    private void names(Check c) {
        for (String ore : new String[] {"copper_ore", "ore_copper", "orecopper", "copperore", "ores", "crystalore", "synctumore",
                "obsidiansynctumore", "zincore", "pink_diamond_ore", "tin_ores", "blocks/coreore"}) {
            c.ok(ModdedOres.oreName(ore), "an ore by its name: " + ore);
        }
        for (String not : new String[] {"core", "reactor_core", "drill_core_2", "store", "spore", "spore_blossom", "lore", "more",
                "folklore", "hardcore", "ignore", "fore", "sycamore", "fancy_sycamore", "semaphore", "sophomore", "cores",
                "stone", "storage", "machine.core", "a-snore"}) {
            c.ok(!ModdedOres.oreName(not), "not an ore by its name: " + not);
        }
        ModdedOres.Form crystal = forBlock(StandInOres.nameOnlyOre).isEmpty() ? null : forBlock(StandInOres.nameOnlyOre).get(0);
        c.ok(crystal != null && !crystal.confirmed() && !crystal.included(),
                "crystalore is a candidate through the name filter, not confirmed without an ore dictionary name");
        c.ok(!com.exiledradio.cavesandcliffsbackport.ores.ModdedOres.isOre(StandInOres.nameOnlyOre.getDefaultState()),
                "and no ore for the ore heights");
    }

    /** deepslate.moddedOresInclude: ores by name, without an ore dictionary name. */
    private void include(Check c) {
        ModdedOres.Form tin = forBlock(StandInOres.includedOre).isEmpty() ? null : forBlock(StandInOres.includedOre).get(0);
        c.ok(tin != null && tin.confirmed() && tin.included(), "an included block with no ore word or ore name is confirmed");
        c.ok(tin != null && tin.oreNames().isEmpty(), "with no ore dictionary names of its own");
        c.ok(tin != null && ModdedOres.deepslateForm(StandInOres.includedOre.getDefaultState()) == tin.block().getDefaultState(),
                "lookup: the included ore");
        c.ok(com.exiledradio.cavesandcliffsbackport.ores.ModdedOres.isOre(StandInOres.includedOre.getDefaultState()),
                "an included block is an ore for the ore heights");
        c.ok(ModdedOres.included(StandInOres.includedOre.getRegistryName(), 0) && !ModdedOres.included(StandInOres.copperOre.getRegistryName(), 0),
                "the include list by name");
        c.ok(forBlock(StandInOres.includedEndOre).isEmpty(), "an included End ore gets no form");
        String why = ModdedOres.leftOut("ccstandin:shiny_rock");
        c.ok(why != null && why.contains("Nether or End") && why.contains("moddedOresInclude"), "an included End ore is left out, with why: " + why);
        c.ok(!com.exiledradio.cavesandcliffsbackport.ores.ModdedOres.isOre(StandInOres.includedEndOre.getDefaultState()),
                "and is no ore for the ore heights");
        why = ModdedOres.leftOut("ccstandin:no_such_block");
        c.ok(why != null && why.contains("no such block"), "an included name that is no block is listed: " + why);
    }

    /** The Default-world deepslate layer turns confirmed ores into their forms, and leaves the rest. */
    private void layer(Check c) {
        BlockPos o = c.area();
        Chunk chunk = c.world.getChunk(o);
        int bx = chunk.x << 4, bz = chunk.z << 4;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 1; y < 10; y++) {
                    c.world.setBlockState(new BlockPos(bx + x, y, bz + z), Blocks.STONE.getDefaultState(), 2 | 16);
                }
            }
        }
        BlockPos a = new BlockPos(bx + 3, 3, bz + 3), b = new BlockPos(bx + 4, 4, bz + 4), d = new BlockPos(bx + 5, 5, bz + 5),
                n = new BlockPos(bx + 6, 5, bz + 6);
        c.world.setBlockState(a, StandInOres.copperOre.getDefaultState(), 2 | 16);
        c.world.setBlockState(b, StandInOres.gemOre.getStateFromMeta(1), 2 | 16);
        c.world.setBlockState(d, StandInOres.gemOre.getStateFromMeta(2), 2 | 16);
        c.world.setBlockState(n, StandInOres.netherOre.getDefaultState(), 2 | 16);
        DeepslateLayer.convert(c.world, chunk);
        c.eq(ModdedOres.deepslateForm(StandInOres.copperOre.getDefaultState()), c.world.getBlockState(a), "the layer turns copper ore into its form");
        c.eq(ModdedOres.deepslateForm(StandInOres.gemOre.getStateFromMeta(1)), c.world.getBlockState(b), "the layer turns a variant into its form");
        c.eq(StandInOres.gemOre.getStateFromMeta(2), c.world.getBlockState(d), "the layer leaves an unconfirmed candidate");
        c.eq(StandInOres.netherOre.getDefaultState(), c.world.getBlockState(n), "the layer leaves the Nether ore");
        net.minecraft.block.Block around = c.world.getBlockState(a.up()).getBlock();
        // The layer also lays tuff blobs (half the chunks), so the block above may be tuff.
        c.ok(around == DeepslateContent.deepslate || around == DeepslateContent.BLOCKS.get("tuff"),
                "stone around it is deepslate (or a tuff blob): " + around.getRegistryName());
    }

    /** Every form made from this original block. */
    private static List<ModdedOres.Form> forBlock(Block original) {
        List<ModdedOres.Form> out = new ArrayList<ModdedOres.Form>();
        for (ModdedOres.Form f : ModdedOres.forms()) {
            if (f.original.getBlock() == original) {
                out.add(f);
            }
        }
        return out;
    }

    private static List<String> names(ModdedOres.Form f) {
        List<String> out = new ArrayList<String>();
        for (int id : OreDictionary.getOreIDs(new ItemStack(f.item()))) {
            out.add(OreDictionary.getOreName(id));
        }
        return out;
    }

    private static int count(List<ItemStack> l) {
        int n = 0;
        for (ItemStack s : l) {
            n += s.getCount();
        }
        return n;
    }

    /** The same drops, with the original ore itself standing for its form. */
    static boolean sameDrops(ModdedOres.Form f, List<ItemStack> theirs, List<ItemStack> ours) {
        if (theirs.size() != ours.size()) {
            return false;
        }
        for (int i = 0; i < theirs.size(); i++) {
            ItemStack t = theirs.get(i), o = ours.get(i);
            boolean self = t.getItem() == Item.getItemFromBlock(f.original.getBlock());
            Item want = self ? f.item() : t.getItem();
            int meta = self ? 0 : t.getMetadata();
            if (o.getItem() != want || o.getMetadata() != meta || o.getCount() != t.getCount()) {
                return false;
            }
        }
        return true;
    }
}
