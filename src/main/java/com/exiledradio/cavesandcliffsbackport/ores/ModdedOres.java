package com.exiledradio.cavesandcliffsbackport.ores;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The overworld ores: every block state under an {@code ore*} ore dictionary name, less Nether and End ores. Built on
 * first use (world generation runs after post-init, so every mod's names are in), and again after any name is added.
 *
 * <p>Nether and End ores are those under an {@code oreNether*} or {@code oreEnd*} name, vanilla's quartz ore, and
 * blocks whose registry name says nether or end ({@code nether_*}, {@code *_end_*}, {@code endore}...). A block that is
 * both (a mod listing its Nether ore under {@code oreCopper} too) counts as Nether or End.
 *
 * <p>Ores named in {@code deepslate.moddedOresInclude} count too, with no ore dictionary name (the same states that
 * get deepslate forms: full blocks of rock, no Nether or End ores).
 */
public final class ModdedOres {

    /** Block to a mask of its ore metadata (bit m = metadata m is an ore). */
    @Nullable
    private static volatile Map<Block, Integer> ores;
    private static boolean listening;

    private ModdedOres() {
    }

    /** Whether the state is an overworld ore. */
    public static boolean isOre(IBlockState state) {
        Map<Block, Integer> map = ores;
        if (map == null) {
            map = build();
        }
        Integer mask = map.get(state.getBlock());
        if (mask == null) {
            return false;
        }
        int meta;
        try {
            meta = state.getBlock().getMetaFromState(state);
        } catch (RuntimeException e) {
            return false;
        }
        return (mask >>> (meta & 15) & 1) != 0;
    }

    /** How many blocks have an ore state (for the log and the self-test). */
    public static int blockCount() {
        Map<Block, Integer> map = ores;
        return (map == null ? build() : map).size();
    }

    private static synchronized Map<Block, Integer> build() {
        if (ores != null) {
            return ores;
        }
        if (!listening) {
            listening = true;
            MinecraftForge.EVENT_BUS.register(new ModdedOres.Listener());
        }
        Map<Block, Integer> in = new IdentityHashMap<Block, Integer>();
        Map<Block, Integer> out = new IdentityHashMap<Block, Integer>();
        for (String name : OreDictionary.getOreNames()) {
            if (name.length() < 4 || !name.startsWith("ore") || !Character.isUpperCase(name.charAt(3))) {
                continue;
            }
            boolean otherDimension = name.startsWith("oreNether") || name.startsWith("oreEnd");
            for (ItemStack stack : OreDictionary.getOres(name, false)) {
                Item item = stack.getItem();
                if (!(item instanceof ItemBlock)) {
                    continue;
                }
                Block block = ((ItemBlock) item).getBlock();
                if (block == null || block == Blocks.AIR) {
                    continue;
                }
                int mask;
                if (stack.getMetadata() == OreDictionary.WILDCARD_VALUE) {
                    mask = 0xFFFF;
                } else {
                    try {
                        IBlockState state = block.getStateFromMeta(item.getMetadata(stack.getMetadata()));
                        mask = 1 << (block.getMetaFromState(state) & 15);
                    } catch (RuntimeException e) {
                        continue;
                    }
                }
                Map<Block, Integer> to = otherDimension || block == Blocks.QUARTZ_ORE || otherDimension(block) ? out : in;
                Integer was = to.get(block);
                to.put(block, was == null ? mask : was | mask);
            }
        }
        // deepslate.moddedOresInclude: ores their mods give no ore* name.
        int included = 0;
        if (ModConfig.deepslate.moddedOresInclude.length > 0) {
            for (Block block : ForgeRegistries.BLOCKS.getValuesCollection()) {
                int mask = 0;
                for (IBlockState state : block.getBlockState().getValidStates()) {
                    if (com.exiledradio.cavesandcliffsbackport.deepslate.ModdedOres.includedOre(block, state)) {
                        try {
                            mask |= 1 << (block.getMetaFromState(state) & 15);
                        } catch (RuntimeException ignored) {
                            // includedOre read it already.
                        }
                    }
                }
                if (mask != 0 && !otherDimension(block)) {
                    Integer was = in.get(block);
                    in.put(block, was == null ? mask : was | mask);
                    included++;
                }
            }
        }
        for (Map.Entry<Block, Integer> e : out.entrySet()) {
            Integer was = in.get(e.getKey());
            if (was != null) {
                int left = was & ~e.getValue();
                if (left == 0) {
                    in.remove(e.getKey());
                } else {
                    in.put(e.getKey(), left);
                }
            }
        }
        ores = in;
        CavesAndCliffsBackport.LOGGER.info("Modded ore heights: {} blocks have overworld ore states ({} by "
                + "deepslate.moddedOresInclude; {} left out as Nether or End ores).", in.size(), included, out.size());
        return in;
    }

    private static boolean otherDimension(Block block) {
        ResourceLocation id = block.getRegistryName();
        if (id == null) {
            return false;
        }
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return path.contains("nether") || path.startsWith("end_") || path.contains("_end_") || path.endsWith("_end")
                || path.startsWith("endore") || path.startsWith("ore_end") || path.startsWith("oreend");
    }

    /** A name added after the list was built (late registration, the self-test's stand-ins) rebuilds it. */
    public static final class Listener {
        @SubscribeEvent
        public void registered(OreDictionary.OreRegisterEvent event) {
            ores = null;
        }
    }
}
