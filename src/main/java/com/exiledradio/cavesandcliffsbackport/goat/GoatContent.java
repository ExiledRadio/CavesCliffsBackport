package com.exiledradio.cavesandcliffsbackport.goat;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.common.ModSounds;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Stream Gt: goats and screaming goats.
 *
 * <p>The goat itself is {@link EntityGoat}; ramming is {@link EntityAIGoatRam} and the long jump
 * {@link EntityAIGoatLongJump}. Nothing else is registered: goats drop nothing, and goat horns belong to a
 * later update (the goat only keeps the two horn tags that update reads).
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class GoatContent {

    /** Entity id 15 of this mod (plan: goats 15-19). */
    public static final int GOAT_ENTITY_ID = 15;

    /** Granted in code: a player and a goat in the same boat. */
    public static final String BOAT_ADVANCEMENT = "husbandry/ride_a_boat_with_a_goat";

    /** Sounds that have a screaming version: index 0 the normal goat's, 1 the screaming goat's. */
    static final SoundEvent[] AMBIENT = new SoundEvent[2], DEATH = new SoundEvent[2], EAT = new SoundEvent[2],
            HURT = new SoundEvent[2], LONG_JUMP = new SoundEvent[2], MILK = new SoundEvent[2],
            PREPARE_RAM = new SoundEvent[2], RAM_IMPACT = new SoundEvent[2];
    static SoundEvent step;

    private static boolean registered;

    private GoatContent() {
    }

    public static boolean enabled() {
        return ModConfig.goats.enabled;
    }

    /** @return true once the goat is registered in this game */
    public static boolean on() {
        return registered;
    }

    /**
     * Sound events are made here, in the first registry event, because 1.12 fires the sound registry event
     * before the entity one.
     */
    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!enabled()) {
            return;
        }
        pair(AMBIENT, "ambient");
        pair(DEATH, "death");
        pair(EAT, "eat");
        pair(HURT, "hurt");
        pair(LONG_JUMP, "long_jump");
        pair(MILK, "milk");
        pair(PREPARE_RAM, "prepare_ram");
        pair(RAM_IMPACT, "ram_impact");
        step = ModSounds.event("entity.goat.step");
    }

    private static void pair(SoundEvent[] into, String name) {
        into[0] = ModSounds.event("entity.goat." + name);
        into[1] = ModSounds.event("entity.goat.screaming." + name);
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        if (!enabled()) {
            return;
        }
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityGoat.class)
                .id(Content.id("goat"), GOAT_ENTITY_ID)
                .name(CavesAndCliffsBackport.MOD_ID + ".goat")
                .tracker(80, 3, true)
                .egg(0xA59B8E, 0x55493E)
                .build());
        registered = true;
    }

    /** From the mod's init, after every registry: natural spawning. */
    public static void init() {
        if (!on() || ModConfig.goats.spawnWeight <= 0) {
            return;
        }
        List<Biome> biomes = new ArrayList<Biome>();
        for (String name : ModConfig.goats.spawnBiomes) {
            ResourceLocation id = new ResourceLocation(name.trim());
            Biome biome = ForgeRegistries.BIOMES.getValue(id);
            if (biome != null && id.equals(biome.getRegistryName())) {
                biomes.add(biome);
            } else {
                CavesAndCliffsBackport.LOGGER.warn("Goats: no biome named {}; skipped.", name);
            }
        }
        if (!biomes.isEmpty()) {
            // 1.18's group size (1-3). Its weight of 5 is out of a list of 9; here goats join the hills' sheep,
            // pigs, chickens, cows and llamas, so the weight is a setting.
            EntityRegistry.addSpawn(EntityGoat.class, ModConfig.goats.spawnWeight, 1, 3, EnumCreatureType.CREATURE,
                    biomes.toArray(new Biome[0]));
        }
        CavesAndCliffsBackport.LOGGER.info("Goats spawn in {} biomes.", biomes.size());
    }
}
