package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.MenuMusic;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.music.ItemMusicDisc;
import com.exiledradio.cavesandcliffsbackport.music.MusicChoice;
import com.exiledradio.cavesandcliffsbackport.music.MusicContent;
import com.exiledradio.cavesandcliffsbackport.music.MusicRegions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockJukebox;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/** Stream Mu: the Otherside disc and its loot, the music events and track lists, the music rules, the regions. */
public final class MusicSelfTest implements SelfTest {

    private static final String M = CavesAndCliffsBackport.MOD_ID;

    @Override
    public String name() {
        return "music";
    }

    @Override
    public void run(Check c) throws Exception {
        if (!ModConfig.music.enabled) {
            c.ok(MusicContent.otherside == null, "switched off: no disc");
            c.ok(ForgeRegistries.ITEMS.getValue(new ResourceLocation(M, "music_disc_otherside")) == null, "switched off: nothing registered");
            c.ok(!ForgeRegistries.SOUND_EVENTS.containsKey(new ResourceLocation(M, "music_disc.otherside")), "switched off: no sound events");
            c.ok(MusicChoice.biome(MusicRegions.Region.LUSH_CAVES) == null, "switched off: no biome music");
            c.ok(table(c, LootTableList.CHESTS_SIMPLE_DUNGEON).getPool("main").getEntry(MusicContent.LOOT_NAME) == null, "switched off: no loot");
            return;
        }
        disc(c);
        jukebox(c);
        loot(c);
        sounds(c);
        tracks(c);
        rules(c);
        regions(c);
    }

    private void disc(Check c) {
        ItemMusicDisc disc = MusicContent.otherside;
        c.ok(disc != null, "the disc is registered");
        c.eq(new ResourceLocation(M, "music_disc_otherside"), disc.getRegistryName(), "registry name");
        c.ok(disc instanceof ItemRecord, "it is a vanilla record");
        ItemStack stack = new ItemStack(disc);
        c.eq(1, stack.getMaxStackSize(), "stack size");
        c.eq(EnumRarity.RARE, disc.getRarity(stack), "rarity");
        c.eq("item." + M + ".music_disc_otherside", disc.getTranslationKey(stack), "translation key");
        c.eq(14, disc.comparatorSignal(), "comparator signal (1.18's analogOutput)");
        c.eq(new ResourceLocation(M, "music_disc.otherside"), disc.music().getRegistryName(), "sound event");
        c.ok(ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(M, "music_disc.otherside")) == disc.music(), "sound event is registered");
        c.ok(disc.getCreativeTab() != null, "it is in a creative tab");
    }

    private void jukebox(Check c) {
        BlockPos pos = c.area().add(4, 0, 4);
        World world = c.world;
        world.setBlockState(pos, Blocks.JUKEBOX.getDefaultState());
        FakePlayer player = c.player("music", pos.south(2));
        ItemStack held = new ItemStack(MusicContent.otherside);
        player.setHeldItem(EnumHand.MAIN_HAND, held);
        EnumActionResult result = held.onItemUse(player, world, pos, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1.0F, 0.5F);
        c.eq(EnumActionResult.SUCCESS, result, "using the disc on a jukebox");
        c.ok(world.getBlockState(pos).getValue(BlockJukebox.HAS_RECORD), "the jukebox has a record");
        TileEntity tile = world.getTileEntity(pos);
        c.ok(tile instanceof BlockJukebox.TileEntityJukebox
                && ((BlockJukebox.TileEntityJukebox) tile).getRecord().getItem() == MusicContent.otherside, "the record in it is Otherside");
        c.ok(held.isEmpty(), "the disc left the hand");
        // A second disc is not swallowed.
        ItemStack second = new ItemStack(MusicContent.otherside);
        c.eq(EnumActionResult.PASS, second.onItemUse(player, world, pos, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1.0F, 0.5F), "a full jukebox takes no second disc");
        // Saved and read back with the chunk.
        BlockJukebox.TileEntityJukebox copy = new BlockJukebox.TileEntityJukebox();
        copy.readFromNBT(tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
        c.ok(copy.getRecord().getItem() == MusicContent.otherside, "the record survives saving");
        // Breaking the jukebox drops it.
        world.destroyBlock(pos, false);
        boolean dropped = false;
        for (EntityItem item : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos).grow(3))) {
            if (item.getItem().getItem() == MusicContent.otherside) {
                dropped = true;
            }
            item.setDead();
        }
        c.ok(dropped, "breaking the jukebox drops the disc");
    }

    private static LootTable table(Check c, ResourceLocation name) {
        return c.world.getLootTableManager().getLootTableFromLocation(name);
    }

    private void loot(Check c) throws Exception {
        lootTable(c, LootTableList.CHESTS_SIMPLE_DUNGEON, 2, 129, 2.0);
        lootTable(c, LootTableList.CHESTS_STRONGHOLD_CORRIDOR, 1, 100, 2.5);
        for (ResourceLocation other : Arrays.asList(LootTableList.CHESTS_STRONGHOLD_LIBRARY, LootTableList.CHESTS_STRONGHOLD_CROSSING,
                LootTableList.CHESTS_DESERT_PYRAMID, LootTableList.CHESTS_ABANDONED_MINESHAFT, LootTableList.CHESTS_NETHER_BRIDGE,
                LootTableList.CHESTS_END_CITY_TREASURE, LootTableList.CHESTS_WOODLAND_MANSION)) {
            LootTable t = table(c, other);
            c.ok(t.getPool(MusicContent.LOOT_NAME) == null && (t.getPool("main") == null || t.getPool("main").getEntry(MusicContent.LOOT_NAME) == null),
                    "no disc in " + other.getPath());
        }
    }

    @SuppressWarnings("unchecked")
    private void lootTable(Check c, ResourceLocation name, int weight, int total, double meanRolls) throws Exception {
        LootTable t = table(c, name);
        LootPool main = t.getPool("main");
        c.ok(main != null, name.getPath() + " has its first pool");
        LootEntry entry = main.getEntry(MusicContent.LOOT_NAME);
        c.ok(entry != null, name.getPath() + ": the disc is in the first pool");
        c.eq(weight, entry.getEffectiveWeight(0.0F), name.getPath() + ": the disc's weight");
        c.eq(weight, entry.getEffectiveWeight(5.0F), name.getPath() + ": luck does not change it");
        List<LootEntry> entries = (List<LootEntry>) ReflectionHelper.findField(LootPool.class, "lootEntries", "field_186453_a").get(main);
        int sum = 0;
        for (LootEntry e : entries) {
            sum += e.getEffectiveWeight(0.0F);
        }
        c.eq(total, sum, name.getPath() + ": the pool's total weight, as 1.18's");
        c.ok(t.getPool(MusicContent.LOOT_NAME) == null, name.getPath() + ": no pool of our own beside it");
        // The odds in generated chests.
        int chests = 20000;
        int discs = 0;
        int withDisc = 0;
        Random random = new Random(1718L);
        LootContext context = new LootContext.Builder(c.world).build();
        for (int i = 0; i < chests; i++) {
            boolean any = false;
            for (ItemStack stack : t.generateLootForPools(random, context)) {
                if (stack.getItem() == MusicContent.otherside) {
                    discs += stack.getCount();
                    any = true;
                }
            }
            if (any) {
                withDisc++;
            }
        }
        double expected = chests * meanRolls * weight / total;
        c.ok(discs > expected * 0.85 && discs < expected * 1.15, name.getPath() + ": about " + (int) expected + " discs in " + chests
                + " chests, got " + discs);
        CavesAndCliffsBackport.LOGGER.info("SELFTEST music: {} of {} {} chests hold Otherside ({}%)", withDisc, chests, name.getPath(),
                String.format("%.2f", withDisc * 100.0 / chests));
    }

    private static JsonObject json(String resource) throws Exception {
        InputStream in = MusicSelfTest.class.getResourceAsStream(resource);
        if (in == null) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    /** @return the event's file names; every one of ours must be in the jar, and every sound must be streamed */
    private static List<String> files(Check c, JsonObject sounds, String event) {
        List<String> out = new ArrayList<String>();
        if (!c.ok(sounds.has(event), "sounds.json has " + event)) {
            return out;
        }
        JsonArray list = sounds.getAsJsonObject(event).getAsJsonArray("sounds");
        for (JsonElement e : list) {
            JsonObject o = e.getAsJsonObject();
            String name = o.get("name").getAsString();
            out.add(name.substring(name.lastIndexOf('/') + 1));
            c.ok(o.has("stream") && o.get("stream").getAsBoolean(), event + ": " + name + " is streamed");
            ResourceLocation rl = new ResourceLocation(name);
            if (M.equals(rl.getNamespace())) {
                c.ok(MusicSelfTest.class.getResource("/assets/" + M + "/sounds/" + rl.getPath() + ".ogg") != null, event + ": " + name + " is in the jar");
            } else {
                c.eq("minecraft", rl.getNamespace(), event + ": " + name + " is 1.12.2's own file");
            }
        }
        return out;
    }

    private static Set<String> set(String... names) {
        return new TreeSet<String>(Arrays.asList(names));
    }

    private void sounds(Check c) throws Exception {
        JsonObject sounds = json("/assets/" + M + "/sounds.json");
        c.eq(set("otherside"), new TreeSet<String>(files(c, sounds, "music_disc.otherside")), "the disc's sound");
        // 1.18's lists.
        c.eq(set("an_ordinary_day", "calm1", "calm2", "calm3", "floating_dream", "left_to_bloom", "one_more_day", "piano3"),
                new TreeSet<String>(files(c, sounds, "music.overworld.lush_caves")), "lush caves' tracks");
        c.eq(set("an_ordinary_day", "hal1", "hal4", "infinite_amethyst", "nuance1", "nuance2", "wending"),
                new TreeSet<String>(files(c, sounds, "music.overworld.dripstone_caves")), "dripstone caves' tracks");
        for (MusicRegions.Region region : MusicRegions.Region.values()) {
            if (region.id == null) {
                continue;
            }
            String event = "music.overworld." + region.id;
            c.ok(!files(c, sounds, event).isEmpty(), event + " has tracks");
            SoundEvent registered = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(M, event));
            c.ok(registered != null, event + " is registered");
            c.ok(MusicChoice.biome(region) != null && MusicChoice.biome(region).event == registered, region + " plays " + event);
        }
        // Weights and volumes that differ from the default, as 1.18's.
        for (JsonElement e : sounds.getAsJsonObject("music.overworld.lush_caves").getAsJsonArray("sounds")) {
            JsonObject o = e.getAsJsonObject();
            if (o.get("name").getAsString().endsWith("left_to_bloom")) {
                c.eq(2, o.get("weight").getAsInt(), "Left to Bloom's weight in lush caves");
                c.eq(0.4F, o.get("volume").getAsFloat(), "Left to Bloom's volume in lush caves");
            }
        }
        for (JsonElement e : sounds.getAsJsonObject("music.overworld.dripstone_caves").getAsJsonArray("sounds")) {
            JsonObject o = e.getAsJsonObject();
            if (o.get("name").getAsString().endsWith("wending")) {
                c.eq(2, o.get("weight").getAsInt(), "Wending's weight in dripstone caves");
            }
        }
    }

    private void tracks(Check c) throws Exception {
        // The built-in pack: an addition to minecraft:music.game, replacing nothing.
        JsonObject pack = json("/" + M + "_packs/game_music/assets/minecraft/sounds.json");
        if (c.ok(pack != null, "the game music pack is in the jar")) {
            c.eq(1, pack.entrySet().size(), "the pack touches one event");
            c.ok(!pack.getAsJsonObject("music.game").has("replace"), "the pack replaces nothing");
            c.eq(set("comforting_memories", "floating_dream", "left_to_bloom", "one_more_day"),
                    new TreeSet<String>(files(c, pack, "music.game")), "the tracks 1.18 added to the game music");
            for (JsonElement e : pack.getAsJsonObject("music.game").getAsJsonArray("sounds")) {
                c.eq(0.4F, e.getAsJsonObject().get("volume").getAsFloat(), e.getAsJsonObject().get("name").getAsString() + " volume");
                c.ok(e.getAsJsonObject().get("name").getAsString().startsWith(M + ":"), "a game track is our file");
            }
        }
        // 26.2: fingerspit's tracks in the eight biome lists (26.3's sounds.json), Game Drops Backport's files.
        JsonObject fs = json("/" + M + "_packs/fingerspit/assets/" + M + "/sounds.json");
        if (c.ok(fs != null, "the 26.2 biome pack is in the jar")) {
            c.eq(8, fs.entrySet().size(), "26.2 adds to all eight music biomes");
            String[][] want = {{"lush_caves", "shores"}, {"dripstone_caves", "ebb", "nightly", "shores"}, {"meadow", "home"},
                    {"grove", "shores"}, {"snowy_slopes", "nightly"}, {"jagged_peaks", "ebb", "nightly"},
                    {"frozen_peaks", "ebb", "home", "nightly"}, {"stony_peaks", "nightly"}};
            int entries = 0;
            for (String[] w : want) {
                String event = "music.overworld." + w[0];
                c.ok(fs.has(event) && !fs.getAsJsonObject(event).has("replace"), event + ": added to, nothing replaced");
                Set<String> names = new TreeSet<String>();
                if (fs.has(event)) {
                    for (JsonElement e : fs.getAsJsonObject(event).getAsJsonArray("sounds")) {
                        JsonObject o = e.getAsJsonObject();
                        String name = o.get("name").getAsString();
                        entries++;
                        c.ok(name.startsWith("gamedropsbackport:music/game/") && o.get("stream").getAsBoolean()
                                && o.get("weight").getAsInt() == 2, event + ": " + name + " is Game Drops', streamed, weight 2");
                        boolean full = "dripstone_caves".equals(w[0]) && name.endsWith("/nightly");
                        c.eq(full ? 1.0F : 0.4F, o.has("volume") ? o.get("volume").getAsFloat() : 1.0F, event + ": " + name + " volume");
                        names.add(name.substring(name.lastIndexOf('/') + 1));
                    }
                }
                c.eq(set(Arrays.copyOfRange(w, 1, w.length)), names, event + ": 26.2's tracks");
            }
            c.eq(13, entries, "13 entries");
        }

        // The menu tracks, and Core's pool with them.
        List<String[]> menu = MusicContent.menuTracks();
        Set<String> names = new TreeSet<String>();
        for (String[] track : menu) {
            ResourceLocation rl = new ResourceLocation(track[0]);
            names.add(rl.getPath().substring(rl.getPath().lastIndexOf('/') + 1));
            c.eq("1", track[1], track[0] + " weight");
            c.ok(MusicSelfTest.class.getResource("/assets/" + rl.getNamespace() + "/sounds/" + rl.getPath() + ".ogg") != null, track[0] + " is in the jar");
        }
        c.eq(set("infinite_amethyst", "left_to_bloom", "one_more_day", "stand_tall", "wending"), names, "the tracks 1.18 added to the menu music");
        c.ok(Presentation.newest().isAtLeast("1.18"), "Core knows 1.18 is installed (its 1.18 music step runs)");
        Contribution v118 = Presentation.contribution("1.18");
        c.ok(v118 != null, "our 1.18 contribution exists");
        List<MenuMusic.Track> base = new ArrayList<MenuMusic.Track>();
        int pooled = MenuMusic.pool(base, Presentation.installed()).size();
        c.eq(ModConfig.music.menuMusic ? 5 : 0, pooled, "menu tracks in Core's pool (over an empty base)");
        c.eq(ModConfig.music.menuMusic, MenuMusic.changed(Presentation.installed()), "Core sees the menu music as changed");
    }

    private void rules(Check c) {
        MusicChoice.Track lush = MusicChoice.biome(MusicRegions.Region.LUSH_CAVES);
        MusicChoice.Track drip = MusicChoice.biome(MusicRegions.Region.DRIPSTONE_CAVES);
        c.ok(MusicChoice.biome(MusicRegions.Region.NONE) == null, "no biome, no biome music");
        c.eq(12000, lush.minDelay, "biome music: least wait");
        c.eq(24000, lush.maxDelay, "biome music: longest wait");
        c.ok(!lush.replaceCurrent, "biome music never cuts off the playing track");
        SoundEvent game = SoundEvents.MUSIC_GAME;
        c.ok(MusicChoice.choose(game, MusicRegions.Region.LUSH_CAVES, false) == lush, "game music in a lush cave: lush music");
        c.ok(MusicChoice.choose(game, MusicRegions.Region.DRIPSTONE_CAVES, false) == drip, "game music in a dripstone cave: dripstone music");
        c.ok(MusicChoice.choose(game, MusicRegions.Region.DRIPSTONE_CAVES, true) == drip, "...also while a biome track plays");
        c.ok(MusicChoice.choose(game, MusicRegions.Region.MEADOW, false) == MusicChoice.biome(MusicRegions.Region.MEADOW), "a meadow: meadow music");
        c.ok(MusicChoice.choose(game, MusicRegions.Region.NONE, false) == null, "game music elsewhere: unchanged");
        MusicChoice.Track leaving = MusicChoice.choose(game, MusicRegions.Region.NONE, true);
        c.ok(leaving != null && leaving.event == game && !leaving.replaceCurrent && leaving.minDelay == 12000 && leaving.maxDelay == 24000,
                "leaving the biome: the game music, letting the biome track finish");
        for (SoundEvent other : new SoundEvent[] {SoundEvents.MUSIC_CREATIVE, SoundEvents.MUSIC_NETHER, SoundEvents.MUSIC_END,
                SoundEvents.MUSIC_DRAGON, SoundEvents.MUSIC_CREDITS, SoundEvents.MUSIC_MENU, MusicContent.otherside.music()}) {
            c.ok(MusicChoice.choose(other, MusicRegions.Region.LUSH_CAVES, false) == null, other.getRegistryName() + " is never replaced by biome music");
            c.ok(MusicChoice.choose(other, MusicRegions.Region.NONE, true) == null, other.getRegistryName() + " is left alone on leaving a biome");
        }
        c.ok(MusicChoice.isBiomeMusic(lush.event) && MusicChoice.isBiomeMusic(drip.event), "biome events are known");
        c.ok(!MusicChoice.isBiomeMusic(game) && !MusicChoice.isBiomeMusic(null) && !MusicChoice.isBiomeMusic(MusicContent.otherside.music()),
                "other events are not biome music");
        // Each region has its own event.
        Set<String> events = new TreeSet<String>();
        for (MusicRegions.Region region : MusicRegions.Region.values()) {
            if (region.id != null) {
                events.add(MusicChoice.biome(region).event.getRegistryName().toString());
            }
        }
        c.eq(8, events.size(), "eight biome music events");
    }

    private void regions(Check c) {
        BlockPos here = new BlockPos(0, 20, 0);
        MusicRegions.setLocator(null);
        c.eq(MusicRegions.Region.NONE, MusicRegions.at(c.world, here), "no locator: none");
        try {
            MusicRegions.setLocator(new MusicRegions.Locator() {
                @Override
                public MusicRegions.Region at(World world, BlockPos pos) {
                    return pos.getY() < 30 ? MusicRegions.Region.LUSH_CAVES : pos.getY() < 60 ? MusicRegions.Region.DRIPSTONE_CAVES : null;
                }
            });
            c.eq(MusicRegions.Region.LUSH_CAVES, MusicRegions.at(c.world, here), "a locator answers");
            c.eq(MusicRegions.Region.DRIPSTONE_CAVES, MusicRegions.at(c.world, here.up(20)), "...per position");
            c.eq(MusicRegions.Region.NONE, MusicRegions.at(c.world, here.up(60)), "a null answer is none");
            MusicRegions.setLocator(new MusicRegions.Locator() {
                @Override
                public MusicRegions.Region at(World world, BlockPos pos) {
                    throw new IllegalStateException("selftest");
                }
            });
            c.eq(MusicRegions.Region.NONE, MusicRegions.at(c.world, here), "a failing locator is none");
        } finally {
            MusicRegions.setLocator(null);
        }
        c.eq(MusicRegions.Region.NONE, MusicRegions.at(c.world, here), "locator removed: none");
        // The cave regions' locator: underground in a lush or dripstone region, nothing above ground.
        long seed = c.world.getSeed();
        BlockPos lush = null, drip = null;
        for (int x = 0; x < 20000 && (lush == null || drip == null); x += 64) {
            com.exiledradio.cavesandcliffsbackport.common.CaveRegions.Region r =
                    com.exiledradio.cavesandcliffsbackport.common.CaveRegions.at(seed, x, 12, 0);
            if (r == com.exiledradio.cavesandcliffsbackport.common.CaveRegions.Region.LUSH && lush == null) {
                lush = new BlockPos(x, 12, 0);
            } else if (r == com.exiledradio.cavesandcliffsbackport.common.CaveRegions.Region.DRIPSTONE && drip == null) {
                drip = new BlockPos(x, 12, 0);
            }
        }
        if (c.ok(lush != null && drip != null, "a lush and a dripstone region along the x axis")) {
            // The player's own chunk is always loaded; an unloaded one has no height map, so nothing is underground.
            c.world.getChunk(lush);
            c.world.getChunk(drip);
            c.eq(MusicRegions.Region.LUSH_CAVES, MusicRegions.CAVES.at(c.world, lush), "a lush region underground: lush caves");
            c.eq(MusicRegions.Region.DRIPSTONE_CAVES, MusicRegions.CAVES.at(c.world, drip), "a dripstone region underground: dripstone caves");
            c.eq(MusicRegions.Region.NONE, MusicRegions.CAVES.at(c.world, new BlockPos(lush.getX(), 255, 0)), "above ground: none");
        }
        if (ModConfig.music.biomeMusic) {
            MusicRegions.setLocator(MusicRegions.CAVES);
        }
        c.ok(Items.RECORD_13 != MusicContent.otherside, "sanity");
    }
}
