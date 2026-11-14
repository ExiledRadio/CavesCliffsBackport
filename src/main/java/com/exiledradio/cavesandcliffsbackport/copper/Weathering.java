package com.exiledradio.cavesandcliffsbackport.copper;

import com.exiledradio.backportcore.copper.Oxidation;

import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Random;

/**
 * The oxidation chain every copper block belongs to: what it oxidizes into, what scraping returns it to,
 * and its waxed and unwaxed forms. The table and the aging rule are Backport Core's ({@code copper.Oxidation},
 * Core 1.3.0), shared with every family mod's copper: Tricky Trials' bulbs, doors and grates wait for this mod's
 * copper and age with it, and an axe or honeycomb from either mod treats both alike. Wax items stay here.
 *
 * <h2>Oxidation (current Java)</h2>
 * On each random tick an unwaxed copper block that is not fully oxidized tries to age with chance
 * 64/1125. It looks at every other unwaxed copper block within a Manhattan distance of 4: if any is less
 * oxidized it stops; otherwise, with {@code a} as many at its own stage and {@code b} more oxidized, it
 * ages with chance {@code ((b + 1) / (a + b + 1))^2}, times 0.75 while unaffected. So a lone block ages
 * steadily, a block among fresh copper waits for the rest, and a patch ages roughly together.
 *
 * <p>States carry across by property (stairs keep facing, half and shape; slabs keep their half).
 */
public final class Weathering {

    /** 64/1125: the chance a random tick tries to age a block at all. */
    public static final float TRY_CHANCE = Oxidation.TRY_CHANCE;

    private Weathering() {
    }

    /** Declares one shape's four stages, unaffected first, in Backport Core's table. */
    public static void chain(Block... stages) {
        Oxidation.chain(stages);
    }

    /** Declares a block and its waxed form, in Backport Core's table. */
    public static void waxed(Block plain, Block waxed) {
        Oxidation.waxed(plain, waxed);
    }

    @Nullable
    public static Block next(Block b) {
        return Oxidation.next(b);
    }

    @Nullable
    public static Block previous(Block b) {
        return Oxidation.previous(b);
    }

    @Nullable
    public static Block waxedOf(Block b) {
        return Oxidation.waxedOf(b);
    }

    @Nullable
    public static Block unwaxed(Block b) {
        return Oxidation.unwaxed(b);
    }

    /** @return the unaffected form of an unwaxed copper block (itself if unaffected) */
    public static Block first(Block b) {
        return Oxidation.first(b);
    }

    /** @return 0-3 for unwaxed copper blocks (any mod's), -1 for anything else (waxed copper included) */
    public static int stage(Block b) {
        return Oxidation.stage(b);
    }

    public static boolean isCopper(Block b) {
        return Oxidation.isCopper(b);
    }

    /** The same state on another block of the chain: every property both blocks have is copied. */
    public static IBlockState copy(IBlockState from, Block to) {
        return Oxidation.copy(from, to);
    }

    /** A random tick on an unwaxed copper block: Backport Core's rule, while {@code copper.oxidation} is on. */
    public static void randomTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (ModConfig.copper.oxidation) {
            Oxidation.randomTick(world, pos, state, rand);
        }
    }

    /** @return the chance this block ages, given every mod's copper around it; 0 if a less oxidized one is near */
    public static float ageChance(World world, BlockPos pos, int age) {
        return Oxidation.ageChance(world, pos, age);
    }

    // ---------------------------------------------------------------------------------------------
    // Wax

    /** @return whether this stack waxes copper: an item in {@code copper.waxItems}, or one with the ore name honeycomb */
    public static boolean isWax(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = stack.getItem().getRegistryName();
        if (id != null && Arrays.asList(ModConfig.copper.waxItems).contains(id.toString())) {
            return true;
        }
        for (int ore : OreDictionary.getOreIDs(stack)) {
            if ("honeycomb".equals(OreDictionary.getOreName(ore))) {
                return true;
            }
        }
        return false;
    }

    /** Gives every installed item of {@code copper.waxItems} the ore name honeycomb, so recipes take them. */
    public static void registerWaxOres() {
        for (String name : ModConfig.copper.waxItems) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(name));
            if (item != null && item != net.minecraft.init.Items.AIR) {
                OreDictionary.registerOre("honeycomb", item);
            }
        }
    }
}
