package com.exiledradio.cavesandcliffsbackport.copper;

import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The oxidation chain every copper block belongs to: what it oxidizes into, what scraping returns it to,
 * and its waxed and unwaxed forms. Written so Backport Core can take it over (CORE_REQUESTS 3) by a rename.
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
    public static final float TRY_CHANCE = 0.05688889F;

    private static final Map<Block, Block> NEXT = new HashMap<Block, Block>();
    private static final Map<Block, Block> PREVIOUS = new HashMap<Block, Block>();
    private static final Map<Block, Block> WAXED = new HashMap<Block, Block>();
    private static final Map<Block, Block> UNWAXED = new HashMap<Block, Block>();
    /** Oxidation stage of every unwaxed copper block, 0-3. */
    private static final Map<Block, Integer> STAGE = new HashMap<Block, Integer>();

    private Weathering() {
    }

    /** Declares one shape's four stages, unaffected first. */
    public static void chain(Block... stages) {
        for (int i = 0; i < stages.length; i++) {
            STAGE.put(stages[i], i);
            if (i + 1 < stages.length) {
                NEXT.put(stages[i], stages[i + 1]);
                PREVIOUS.put(stages[i + 1], stages[i]);
            }
        }
    }

    /** Declares a block and its waxed form. */
    public static void waxed(Block plain, Block waxed) {
        WAXED.put(plain, waxed);
        UNWAXED.put(waxed, plain);
    }

    @Nullable
    public static Block next(Block b) {
        return NEXT.get(b);
    }

    @Nullable
    public static Block previous(Block b) {
        return PREVIOUS.get(b);
    }

    @Nullable
    public static Block waxedOf(Block b) {
        return WAXED.get(b);
    }

    @Nullable
    public static Block unwaxed(Block b) {
        return UNWAXED.get(b);
    }

    /** @return the unaffected form of an unwaxed copper block (itself if unaffected) */
    public static Block first(Block b) {
        Block p;
        while ((p = PREVIOUS.get(b)) != null) {
            b = p;
        }
        return b;
    }

    /** @return 0-3 for unwaxed copper blocks, -1 for anything else (waxed copper included) */
    public static int stage(Block b) {
        Integer s = STAGE.get(b);
        return s == null ? -1 : s;
    }

    public static boolean isCopper(Block b) {
        return STAGE.containsKey(b) || UNWAXED.containsKey(b);
    }

    /** The same state on another block of the chain: every property both blocks have is copied. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static IBlockState copy(IBlockState from, Block to) {
        IBlockState out = to.getDefaultState();
        for (IProperty p : from.getPropertyKeys()) {
            if (out.getPropertyKeys().contains(p)) {
                out = out.withProperty(p, from.getValue(p));
            }
        }
        return out;
    }

    /** A random tick on an unwaxed copper block. */
    public static void randomTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (!ModConfig.copper.oxidation) {
            return;
        }
        Block next = NEXT.get(state.getBlock());
        if (next == null || rand.nextFloat() >= TRY_CHANCE) {
            return;
        }
        if (rand.nextFloat() < ageChance(world, pos, stage(state.getBlock()))) {
            world.setBlockState(pos, copy(state, next), 3);
        }
    }

    /** @return the chance this block ages, given the copper around it; 0 if a less oxidized one is near */
    public static float ageChance(World world, BlockPos pos, int age) {
        int same = 0, older = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -4; dx <= 4; dx++) {
            int rx = 4 - Math.abs(dx);
            for (int dy = -rx; dy <= rx; dy++) {
                int rz = rx - Math.abs(dy);
                for (int dz = -rz; dz <= rz; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    p.setPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!world.isBlockLoaded(p)) {
                        continue;
                    }
                    int other = stage(world.getBlockState(p).getBlock());
                    if (other < 0) {
                        continue;
                    }
                    if (other < age) {
                        return 0;
                    }
                    if (other > age) {
                        older++;
                    } else {
                        same++;
                    }
                }
            }
        }
        float f = (older + 1) / (float) (older + same + 1);
        return f * f * (age == 0 ? 0.75F : 1.0F);
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
