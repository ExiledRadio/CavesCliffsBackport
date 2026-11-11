package com.exiledradio.cavesandcliffsbackport.axolotl;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.common.ModSounds;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.BlockDispenser;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Stream Ax: the axolotl (entity id 10 of this stream's 10-14), the bucket of axolotl and their sounds.
 * Behavior is {@link EntityAxolotl} and {@link AxolotlGoals}; natural spawning {@link AxolotlSpawning}; what
 * other mobs do about axolotls {@link AxolotlEvents}. Off ({@code axolotls.enabled}), nothing is registered and
 * every handler does nothing.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class AxolotlContent {

    public static final int AXOLOTL_ID = 10;

    /** The bucket of axolotl; null when the stream is off. */
    @Nullable
    public static Item bucket;

    public static SoundEvent attack, death, hurt, idleAir, idleWater, splash, swim, bucketFill, bucketEmpty;

    private AxolotlContent() {
    }

    public static boolean enabled() {
        return ModConfig.axolotls.enabled;
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            return;
        }
        // Sound events are made here: 1.12 registers them after items, and ModSounds registers what was asked for.
        attack = ModSounds.event("entity.axolotl.attack");
        death = ModSounds.event("entity.axolotl.death");
        hurt = ModSounds.event("entity.axolotl.hurt");
        idleAir = ModSounds.event("entity.axolotl.idle_air");
        idleWater = ModSounds.event("entity.axolotl.idle_water");
        splash = ModSounds.event("entity.axolotl.splash");
        swim = ModSounds.event("entity.axolotl.swim");
        bucketFill = ModSounds.event("item.bucket.fill_axolotl");
        bucketEmpty = ModSounds.event("item.bucket.empty_axolotl");

        bucket = Content.item(event.getRegistry(), "axolotl_bucket", new ItemAxolotlBucket());
        BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(bucket, new ItemAxolotlBucket.Dispense());
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        if (!enabled()) {
            return;
        }
        // Spawn egg colors are 1.17's.
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityAxolotl.class)
                .id(Content.id("axolotl"), AXOLOTL_ID)
                .name(CavesAndCliffsBackport.MOD_ID + ".axolotl")
                .tracker(80, 3, true)
                .egg(16499171, 10890612)
                .build());
        EntitySpawnPlacementRegistry.setPlacementType(EntityAxolotl.class, EntityLiving.SpawnPlacementType.IN_WATER);
        CavesAndCliffsBackport.LOGGER.info("Registered the axolotl.");
    }

    // --- what axolotls breed with ----------------------------------------------------------------------

    /**
     * @return the bucket of tropical fish, a 1.13 item this mod does not have: another mod's when installed
     * (looked up by name, so nothing links against it), else null
     */
    @Nullable
    static Item tropicalFishBucket() {
        return Owners.present(Owners.UPDATE_AQUATIC) ? Owners.item(Owners.UPDATE_AQUATIC, "tropical_fish_bucket") : null;
    }

    static boolean isTropicalFishBucket(ItemStack stack) {
        Item tropical = tropicalFishBucket();
        return tropical != null && !stack.isEmpty() && stack.getItem() == tropical;
    }

    /**
     * Current Java: only a bucket of tropical fish. In a game that has no such bucket, the config's
     * {@code breedingItem} stands in (the tropical fish itself by default).
     */
    public static boolean isBreedingItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item tropical = tropicalFishBucket();
        if (tropical != null) {
            return stack.getItem() == tropical;
        }
        String wanted = ModConfig.axolotls.breedingItem;
        if (wanted == null || wanted.trim().isEmpty()) {
            return false;
        }
        String[] parts = wanted.trim().split(":");
        int meta = -1;
        String name = wanted.trim();
        // "mod:item:meta" - or "item:meta" is not accepted, so a third part is always the metadata.
        if (parts.length == 3) {
            name = parts[0] + ":" + parts[1];
            try {
                meta = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(name));
        return item != null && stack.getItem() == item && (meta < 0 || stack.getMetadata() == meta);
    }
}
