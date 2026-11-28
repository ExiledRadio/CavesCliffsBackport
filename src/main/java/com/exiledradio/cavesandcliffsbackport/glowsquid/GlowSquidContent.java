package com.exiledradio.cavesandcliffsbackport.glowsquid;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.common.ModSounds;

import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * Stream Gs: the glow squid, the glow ink sac, the glow item frame and glowing sign text
 * ({@link GlowingText}).
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class GlowSquidContent {

    /** Entity ids 5-9 are this stream's. */
    public static final int GLOW_SQUID_ID = 5, GLOW_ITEM_FRAME_ID = 6;

    public static final ResourceLocation LOOT = Content.id("entities/glow_squid");

    public static Item glowInkSac, glowItemFrame;
    public static SoundEvent squidAmbient, squidDeath, squidHurt, squidSquirt;
    public static SoundEvent frameAddItem, frameBreak, framePlace, frameRemoveItem, frameRotateItem;
    public static SoundEvent glowInkSacUse, inkSacUse;

    /**
     * 1.17's "underground water creature" spawn group: its own cap of 5, so glow squid never take the squid's
     * places. Null if it could not be added; glow squid then share the water creatures' cap.
     */
    public static EnumCreatureType undergroundWater;

    private GlowSquidContent() {
    }

    public static boolean enabled() {
        return ModConfig.glowSquid.enabled;
    }

    /**
     * From the mod's constructor, before any mod's pre-init.
     *
     * <p>{@code Biome.getSpawnableList} switches on the creature type through a table sized when it first runs.
     * Mods that add spawns in pre-init run it, and a type added afterwards has an ordinal past the table's end
     * and crashes the mob spawner. So the type is added here, where no switch can have run - before the config
     * is read, which is why it is added even when the stream is off (it then has no spawns and costs nothing).
     */
    public static void construct() {
        if (undergroundWater != null) {
            return;
        }
        try {
            undergroundWater = EnumHelper.addCreatureType("CAVESANDCLIFFSBACKPORT_UNDERGROUND_WATER_CREATURE",
                    EntityGlowSquid.class, 5, Material.WATER, true, false);
        } catch (RuntimeException e) {
            CavesAndCliffsBackport.LOGGER.error("Could not add the underground water creature type; glow squid share the squid's cap.", e);
            return;
        }
        if (undergroundWater != null) {
            SwitchTables.grow(EnumCreatureType.class, "net.minecraft.world.biome.Biome$1");
        }
    }

    /** The group glow squid spawn in and count against. */
    public static EnumCreatureType spawnGroup() {
        return undergroundWater != null ? undergroundWater : EnumCreatureType.WATER_CREATURE;
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<Item> r = event.getRegistry();
        // Sound events are made here: the item event comes before the sound event one (see ModSounds).
        squidAmbient = ModSounds.event("entity.glow_squid.ambient");
        squidDeath = ModSounds.event("entity.glow_squid.death");
        squidHurt = ModSounds.event("entity.glow_squid.hurt");
        squidSquirt = ModSounds.event("entity.glow_squid.squirt");
        frameAddItem = ModSounds.event("entity.glow_item_frame.add_item");
        frameBreak = ModSounds.event("entity.glow_item_frame.break");
        framePlace = ModSounds.event("entity.glow_item_frame.place");
        frameRemoveItem = ModSounds.event("entity.glow_item_frame.remove_item");
        frameRotateItem = ModSounds.event("entity.glow_item_frame.rotate_item");
        glowInkSacUse = ModSounds.event("item.glow_ink_sac.use");
        inkSacUse = ModSounds.event("item.ink_sac.use");

        glowInkSac = Content.item(r, "glow_ink_sac", new Item());
        glowItemFrame = Content.item(r, "glow_item_frame", new ItemGlowItemFrame());
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        if (!enabled()) {
            return;
        }
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityGlowSquid.class)
                .id(Content.id("glow_squid"), GLOW_SQUID_ID)
                .name(CavesAndCliffsBackport.MOD_ID + ".glow_squid")
                .tracker(64, 3, true)
                .egg(0x095656, 0x85F1BC)
                .build());
        // Vanilla's item frame: seen from 160 blocks, never sends movement.
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityGlowItemFrame.class)
                .id(Content.id("glow_item_frame"), GLOW_ITEM_FRAME_ID)
                .name(CavesAndCliffsBackport.MOD_ID + ".glow_item_frame")
                .tracker(160, Integer.MAX_VALUE, false)
                .build());
        EntitySpawnPlacementRegistry.setPlacementType(EntityGlowSquid.class, EntityLiving.SpawnPlacementType.IN_WATER);
        // Checked before the spawner builds a squid (current Java's order); 1.12.2 built one for every attempt in water.
        com.exiledradio.backportcore.worldgen.SpawnPrechecks.register(EntityGlowSquid.class,
                (world, pos) -> !EntityGlowSquid.canSpawnAt(world, pos));
        LootTableList.register(LOOT);
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!enabled()) {
            return;
        }
        ShapelessOreRecipe recipe = new ShapelessOreRecipe(Content.id("glow_item_frame"), new ItemStack(glowItemFrame),
                new ItemStack(Items.ITEM_FRAME), new ItemStack(glowInkSac));
        recipe.setRegistryName(Content.id("glow_item_frame"));
        event.getRegistry().register(recipe);
    }

    /** From the mod's init: glow squid spawn in every biome of the overworld (1.18: weight 10). */
    public static void init() {
        if (!enabled() || ModConfig.glowSquid.spawnWeight <= 0) {
            return;
        }
        EnumCreatureType group = spawnGroup();
        int biomes = 0;
        for (Biome biome : ForgeRegistries.BIOMES) {
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.NETHER) || BiomeDictionary.hasType(biome, BiomeDictionary.Type.END)
                    || BiomeDictionary.hasType(biome, BiomeDictionary.Type.VOID)) {
                continue;
            }
            biome.getSpawnableList(group).add(new Biome.SpawnListEntry(EntityGlowSquid.class, ModConfig.glowSquid.spawnWeight, 4, 6));
            biomes++;
        }
        CavesAndCliffsBackport.LOGGER.info("Glow squid spawn in {} biomes.", biomes);
    }
}
