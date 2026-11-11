package com.exiledradio.cavesandcliffsbackport.music;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.common.ModSounds;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryEmpty;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Stream Mu: the Otherside disc, 1.18's biome music and the tracks 1.18 added to the game and menu music. No
 * entity ids, no packets (ids 60-64 are unused).
 *
 * <p>This class is common: the disc, its loot, the sound events and the menu tracks handed to Backport Core. Which
 * music plays is {@link MusicChoice}, where the player is {@link MusicRegions}; the client part is
 * {@code music.client}.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class MusicContent {

    /** 1.18's {@code music_disc_otherside}: comparator signal 14. */
    public static final int OTHERSIDE_SIGNAL = 14;

    /** Otherside's weight in the first pool of 1.18's {@code chests/simple_dungeon} (2 of 129). */
    public static final int DUNGEON_WEIGHT = 2;
    public static final int DUNGEON_POOL_WEIGHT = 129;
    /** Otherside's weight in the first pool of 1.18's {@code chests/stronghold_corridor} (1 of 100). */
    public static final int STRONGHOLD_WEIGHT = 1;
    public static final int STRONGHOLD_POOL_WEIGHT = 100;

    /** The name of the disc's entry in a vanilla pool, and of our own pool where that pool is gone. */
    public static final String LOOT_NAME = CavesAndCliffsBackport.MOD_ID + ":music_disc_otherside";

    /** Where the generator lists the tracks 1.18 added to the menu music: a sound name and a weight per line. */
    public static final String MENU_TRACKS = "/" + CavesAndCliffsBackport.MOD_ID + "_packs/menu_music.txt";

    public static ItemMusicDisc otherside;

    private MusicContent() {
    }

    public static boolean enabled() {
        return ModConfig.music.enabled;
    }

    /** Items register before sound events, so the events are made here (see {@link ModSounds}). */
    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            return;
        }
        for (MusicRegions.Region region : MusicRegions.Region.values()) {
            if (region.id != null) {
                MusicChoice.setBiome(region, ModSounds.event("music.overworld." + region.id));
            }
        }
        otherside = Content.item(event.getRegistry(), "music_disc_otherside", new ItemMusicDisc(
                CavesAndCliffsBackport.MOD_ID + ".otherside", ModSounds.event("music_disc.otherside"), OTHERSIDE_SIGNAL));
    }

    /**
     * From the mod's pre-init, on both sides: 1.18's menu tracks join Backport Core's menu music pool.
     *
     * <p>Naming the version is also what makes Core run 1.18's music step (a version's steps are skipped until
     * that version is installed).
     */
    public static void preInit() {
        if (!enabled()) {
            return;
        }
        Contribution v118 = Presentation.version("1.18");
        if (ModConfig.music.menuMusic) {
            for (String[] track : menuTracks()) {
                v118.addMenuMusic(track[0], Integer.parseInt(track[1]));
            }
        }
    }

    /** @return the tracks 1.18 added to the menu music, as {sound name, weight} */
    public static List<String[]> menuTracks() {
        List<String[]> out = new ArrayList<String[]>();
        InputStream in = MusicContent.class.getResourceAsStream(MENU_TRACKS);
        if (in == null) {
            CavesAndCliffsBackport.LOGGER.warn("Music: {} is missing from the jar; no menu tracks added.", MENU_TRACKS);
            return out;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(" ");
                if (parts.length == 2) {
                    out.add(parts);
                }
            }
        } catch (IOException e) {
            CavesAndCliffsBackport.LOGGER.warn("Music: could not read {}.", MENU_TRACKS, e);
        }
        return out;
    }

    /**
     * Otherside in dungeon and stronghold corridor chests, at 1.18's weights (and current Java's).
     *
     * <p>1.18 lists it in the first pool of each table, among 1.12.2's own entries, so it joins that pool
     * ("main") and the weights come out as vanilla's: 2 of 129 and 1 of 100 per roll. Where another mod has
     * removed or renamed that pool, a pool of our own rolls once at the same chance.
     */
    @SubscribeEvent
    public static void lootLoad(LootTableLoadEvent event) {
        if (otherside == null || !ModConfig.music.othersideLoot) {
            return;
        }
        int weight;
        int total;
        if (LootTableList.CHESTS_SIMPLE_DUNGEON.equals(event.getName())) {
            weight = DUNGEON_WEIGHT;
            total = DUNGEON_POOL_WEIGHT;
        } else if (LootTableList.CHESTS_STRONGHOLD_CORRIDOR.equals(event.getName())) {
            weight = STRONGHOLD_WEIGHT;
            total = STRONGHOLD_POOL_WEIGHT;
        } else {
            return;
        }
        LootEntryItem disc = new LootEntryItem(otherside, weight, 0, new LootFunction[0], new LootCondition[0], LOOT_NAME);
        LootPool main = event.getTable().getPool("main");
        if (main != null) {
            if (main.getEntry(LOOT_NAME) == null) {
                main.addEntry(disc);
            }
            return;
        }
        LootEntry[] entries = {disc,
                new LootEntryEmpty(total - weight, 0, new LootCondition[0], new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "empty").toString())};
        event.getTable().addPool(new LootPool(entries, new LootCondition[0], new RandomValueRange(1.0F),
                new RandomValueRange(0.0F), LOOT_NAME));
    }
}
