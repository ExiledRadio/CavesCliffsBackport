package com.exiledradio.cavesandcliffsbackport.amethyst;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.common.ModSounds;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stream Am: block of amethyst, budding amethyst, the three buds and the cluster, tinted glass, the
 * amethyst shard and the spyglass; geodes are {@link GeodeGenerator}, chimes {@link AmethystEvents}.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class AmethystContent {

    public static BlockAmethyst amethyst;
    public static BlockAmethyst.Budding budding;
    /** Small, medium, large bud, cluster: the order budding amethyst grows them in. */
    public static BlockAmethystCluster[] stages;
    public static Block tintedGlass;
    public static Item shard, spyglass;
    public static SoundEvent chime, spyglassUse, spyglassStop;

    private static final List<Block> ALL = new ArrayList<Block>();

    private AmethystContent() {
    }

    public static boolean enabled() {
        return ModConfig.amethyst.enabled;
    }

    public static List<Block> all() {
        return Collections.unmodifiableList(ALL);
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<Block> r = event.getRegistry();
        SoundType block = ModSounds.type("block.amethyst_block");
        SoundType cluster = ModSounds.type("block.amethyst_cluster");
        chime = ModSounds.event("block.amethyst_block.chime");
        spyglassUse = ModSounds.event("item.spyglass.use");
        spyglassStop = ModSounds.event("item.spyglass.stop_using");

        amethyst = add(r, "amethyst_block", new BlockAmethyst(block), "pickaxe");
        budding = add(r, "budding_amethyst", new BlockAmethyst.Budding(block), "pickaxe");
        stages = new BlockAmethystCluster[] {
                add(r, "small_amethyst_bud", new BlockAmethystCluster(bud("small", cluster), 3, 4, 1, false), null),
                add(r, "medium_amethyst_bud", new BlockAmethystCluster(bud("medium", cluster), 4, 3, 2, false), null),
                add(r, "large_amethyst_bud", new BlockAmethystCluster(bud("large", cluster), 5, 3, 4, false), null),
                add(r, "amethyst_cluster", new BlockAmethystCluster(cluster, 7, 3, 5, true), null),
        };
        for (int i = 0; i + 1 < stages.length; i++) {
            stages[i].next = stages[i + 1];
        }
        tintedGlass = add(r, "tinted_glass", new BlockAmethyst.Tinted(), null);
        com.exiledradio.backportcore.ownership.BlockOwnership.declare(CavesAndCliffsBackport.MOD_ID,
                "amethyst_block", "budding_amethyst", "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud",
                "amethyst_cluster", "tinted_glass");
        CavesAndCliffsBackport.LOGGER.info("Registered {} amethyst blocks.", ALL.size());
    }

    /** 1.18's bud sound types: their own break and place, the cluster's step, hit and fall. */
    private static SoundType bud(String size, SoundType cluster) {
        return new SoundType(1.0F, 1.0F, ModSounds.event("block." + size + "_amethyst_bud.break"), cluster.getStepSound(),
                ModSounds.event("block." + size + "_amethyst_bud.place"), cluster.getHitSound(), cluster.getFallSound());
    }

    private static <B extends Block> B add(IForgeRegistry<Block> r, String name, B block, String tool) {
        Content.block(r, name, block, tool);
        ALL.add(block);
        return block;
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<Item> r = event.getRegistry();
        for (Block b : ALL) {
            Content.registerBlockItem(r, b);
        }
        shard = Content.item(r, "amethyst_shard", new Item());
        spyglass = Content.item(r, "spyglass", new ItemSpyglass());
        OreDictionary.registerOre("gemAmethyst", shard);
        OreDictionary.registerOre("blockAmethyst", amethyst);
        OreDictionary.registerOre("blockGlassTinted", tintedGlass);
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<IRecipe> r = event.getRegistry();
        ItemStack s = new ItemStack(shard);
        shaped(r, "amethyst_block", new ItemStack(amethyst), "XX", "XX", 'X', s);
        shaped(r, "tinted_glass", new ItemStack(tintedGlass, 2), " S ", "SGS", " S ", 'S', s, 'G', new ItemStack(Blocks.GLASS));
        // Any copper ingot: ours (stream Cu) or another mod's, by the shared ore name.
        shaped(r, "spyglass", new ItemStack(spyglass), "S", "C", "C", 'S', s, 'C', "ingotCopper");
    }

    private static void shaped(IForgeRegistry<IRecipe> r, String name, ItemStack out, Object... recipe) {
        ShapedOreRecipe sr = new ShapedOreRecipe(new ResourceLocation(CavesAndCliffsBackport.MOD_ID, name), out, recipe);
        sr.setRegistryName(Content.id(name));
        r.register(sr);
    }

    /** Geodes, from the mod's init. */
    public static void init() {
        if (enabled()) {
            GameRegistry.registerWorldGenerator(new GeodeGenerator(), 0);
        }
    }
}
