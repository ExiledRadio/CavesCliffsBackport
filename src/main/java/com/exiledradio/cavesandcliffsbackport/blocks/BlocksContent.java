package com.exiledradio.cavesandcliffsbackport.blocks;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Stream X: the light block, the <i>Caves &amp; Cliffs</i> advancement ({@link WorldFall}) and 1.17's small
 * changes to things 1.12.2 already has ({@link VanillaChanges}).
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class BlocksContent {

    public static BlockLight light;
    public static ItemLight lightItem;

    /** The advancement's fall tracker, kept for the self-test. Null when the stream is off. */
    public static WorldFall worldFall;

    /** The changes to vanilla things, kept for the self-test. Null when the stream is off. */
    public static VanillaChanges changes;

    /** Every block name this stream registers, for Core's ownership table. */
    public static final String[] BLOCK_NAMES = {"light"};

    private BlocksContent() {
    }

    public static boolean enabled() {
        return ModConfig.blocks.enabled;
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (enabled()) {
            light = Content.block(event.getRegistry(), "light", new BlockLight(), null);
        }
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            return;
        }
        // Registered here rather than through Content.blockItem, which would look for a creative tab: the
        // light has none (commands and pick block give it), as 1.12.2's barrier has none.
        lightItem = new ItemLight(light);
        lightItem.setRegistryName(light.getRegistryName());
        event.getRegistry().register(lightItem);
        Content.model(lightItem, 0, "light");
    }

    /** From the mod's pre-init: tells Core which blocks are ours (or, switched off, that nobody provides them). */
    public static void preInit() {
        Owners.declare(enabled(), BLOCK_NAMES);
    }

    /** From the mod's init: the advancement and the changes to vanilla things. */
    public static void init() {
        if (!enabled()) {
            return;
        }
        worldFall = new WorldFall();
        MinecraftForge.EVENT_BUS.register(worldFall);
        changes = new VanillaChanges();
        MinecraftForge.EVENT_BUS.register(changes);
        VanillaChanges.init();
    }
}
