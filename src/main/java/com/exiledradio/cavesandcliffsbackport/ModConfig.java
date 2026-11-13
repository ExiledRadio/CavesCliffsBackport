package com.exiledradio.cavesandcliffsbackport;

import com.exiledradio.backportcore.settings.Setting;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Configuration: one category per stream, each with an {@code enabled} switch.
 *
 * <h2>Rules for streams</h2>
 * <ul>
 *   <li>A stream edits only inside its own category class.</li>
 *   <li>Category classes extend {@code Object} directly. Forge 1.12 crashes at startup ("Unknown type")
 *       on a category class that extends another one.</li>
 *   <li>{@code enabled} is read once, during registration, and needs a restart: switching a stream off
 *       means its blocks and items are never registered, so a world that has them loses them.</li>
 *   <li>A changed default never reaches an installed game (Forge keeps the value in the file), so a
 *       default that must change gets a new key.</li>
 *   <li>Every field has an explicit {@code @Config.LangKey}, with a name line and a {@code .tooltip} line
 *       in {@code en_us.lang}.</li>
 * </ul>
 */
@Config(modid = CavesAndCliffsBackport.MOD_ID, name = CavesAndCliffsBackport.MOD_ID)
public final class ModConfig {
    @Setting.Feature(icon = "cavesandcliffsbackport:deepslate")
    @Config.LangKey("config.cavesandcliffsbackport.deepslate")
    @Config.Comment("Deepslate and its sets, calcite, tuff, smooth basalt, deepslate ores and raw ores.")
    public static final Deepslate deepslate = new Deepslate();
    @Setting.Feature(icon = "cavesandcliffsbackport:copper_ingot")
    @Config.LangKey("config.cavesandcliffsbackport.copper")
    @Config.Comment("Copper ore, raw copper, copper blocks and their oxidation, waxing and scraping, and lightning rods.")
    public static final Copper copper = new Copper();
    @Setting.Feature(icon = "cavesandcliffsbackport:amethyst_shard")
    @Config.LangKey("config.cavesandcliffsbackport.amethyst")
    @Config.Comment("Amethyst, budding amethyst and its buds, tinted glass, the spyglass and geodes.")
    public static final Amethyst amethyst = new Amethyst();
    @Setting.Feature(icon = "minecraft:cauldron")
    @Config.LangKey("config.cavesandcliffsbackport.dripstone")
    @Config.Comment("Dripstone, pointed dripstone, the lava cauldron and dripstone caves.")
    public static final Dripstone dripstone = new Dripstone();
    @Setting.Feature(icon = "cavesandcliffsbackport:moss_block")
    @Config.LangKey("config.cavesandcliffsbackport.lush")
    @Config.Comment("Moss, azalea, cave vines and glow berries, dripleaf, spore blossoms, glow lichen, rooted dirt, hanging roots and lush caves.")
    public static final Lush lush = new Lush();
    @Setting.Feature(icon = "minecraft:snow")
    @Config.LangKey("config.cavesandcliffsbackport.powderSnow")
    @Config.Comment("Powder snow, freezing, the powder snow bucket and cauldron.")
    public static final PowderSnow powderSnow = new PowderSnow();
    @Setting.Feature(icon = "minecraft:cake")
    @Config.LangKey("config.cavesandcliffsbackport.candles")
    @Config.Comment("Candles and candle cakes.")
    public static final Candles candles = new Candles();
    @Setting.Feature(icon = "minecraft:item_frame")
    @Config.LangKey("config.cavesandcliffsbackport.glowSquid")
    @Config.Comment("Glow squid, glow ink sacs and glow item frames.")
    public static final GlowSquid glowSquid = new GlowSquid();
    @Setting.Feature(icon = "minecraft:water_bucket")
    @Config.LangKey("config.cavesandcliffsbackport.axolotls")
    @Config.Comment("Axolotls and the bucket of axolotl.")
    public static final Axolotls axolotls = new Axolotls();
    @Setting.Feature(icon = "minecraft:wheat")
    @Config.LangKey("config.cavesandcliffsbackport.goats")
    @Config.Comment("Goats and screaming goats.")
    public static final Goats goats = new Goats();
    @Setting.Feature(icon = "minecraft:glowstone")
    @Config.LangKey("config.cavesandcliffsbackport.blocks")
    @Config.Comment("The light block, the Caves & Cliffs advancement and small 1.17 changes to shovels, shulkers, mob drops, pufferfish, llamas and infested blocks.")
    public static final Blocks blocks = new Blocks();
    @Setting.Feature(icon = "minecraft:jukebox")
    @Config.LangKey("config.cavesandcliffsbackport.music")
    @Config.Comment("1.18's music and the Otherside music disc.")
    public static final Music music = new Music();
    @Setting.Feature(icon = "minecraft:vine")
    @Config.LangKey("config.cavesandcliffsbackport.caves")
    @Config.Comment("Where lush and dripstone caves are: the regions the cave streams decorate.")
    public static final Caves caves = new Caves();
    @Setting.Feature(icon = "minecraft:bedrock")
    @Config.LangKey("config.cavesandcliffsbackport.height")
    @Config.Comment("1.18's world height, y -64 to 319, for new worlds of the Caves & Cliffs world type.")
    public static final Height height = new Height();

    // ------------------------------------------------------------------------------------------
    // Categories. Each stream owns exactly one; caves is common's.
    // ------------------------------------------------------------------------------------------

    public static final class Deepslate {
        @Config.RequiresMcRestart
        @Config.Comment("Deepslate, its stairs, slabs and walls, calcite, tuff, smooth basalt, the deepslate ores and raw iron and gold. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.deepslate.enabled")
        public boolean enabled = true;

        @Config.Comment({"Iron ore and gold ore drop raw iron and raw gold, as in 1.17, instead of themselves.",
                "Silk Touch still gives the ore. Off, they and the deepslate iron and gold ores drop themselves."})
        @Config.LangKey("config.cavesandcliffsbackport.deepslate.rawOreDrops")
        public boolean rawOreDrops = true;

        @Config.Comment("Newly generated chunks get a deepslate layer at the bottom of the world, with deepslate ores and tuff in it.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.deepslate.generate")
        public boolean generate = true;

        @Config.Comment("The height below which stone may become deepslate. Between layerFull and this, less and less of it does.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.deepslate.layerTop")
        @Config.RangeInt(min = 1, max = 128)
        public int layerTop = 16;

        @Config.Comment("The height below which all stone becomes deepslate.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.deepslate.layerFull")
        @Config.RangeInt(min = 0, max = 128)
        public int layerFull = 8;

        @Config.Comment("Tuff blobs generate in the deepslate layer.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.deepslate.tuff")
        public boolean tuff = true;

        @Config.Comment("Dimensions that get the deepslate layer.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.deepslate.dimensions")
        public int[] dimensions = {0};

        @Config.Comment("World types (their names, as in level.dat) that never get the deepslate layer.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.deepslate.skipWorldTypes")
        public String[] skipWorldTypes = {"flat", "debug_all_block_states"};
    }

    public static final class Copper {
        @Config.RequiresMcRestart
        @Config.Comment("Copper ore, raw copper, the copper blocks and lightning rods. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.copper.enabled")
        public boolean enabled = true;

        @Config.Comment("Unwaxed copper blocks slowly oxidize: exposed, weathered, then oxidized.")
        @Config.LangKey("config.cavesandcliffsbackport.copper.oxidation")
        public boolean oxidation = true;

        @Config.Comment("Lightning from thunderstorms strikes the nearest lightning rod within 128 blocks instead.")
        @Config.LangKey("config.cavesandcliffsbackport.copper.lightningRods")
        public boolean lightningRods = true;

        @Config.Comment("Items that wax copper when used on it or crafted with it (registry names). Any item with the ore name honeycomb works too.")
        @Setting.ListOf(Setting.Kind.ITEM)
        @Config.LangKey("config.cavesandcliffsbackport.copper.waxItems")
        @Config.RequiresMcRestart
        public String[] waxItems = {"buzzybeesbackport:honeycomb"};

        @Config.Comment("Copper ore generates in newly generated chunks.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.copper.generate")
        public boolean generate = true;

        @Config.Comment("Copper ore veins per chunk.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.copper.veinsPerChunk")
        @Config.RangeInt(min = 0, max = 64)
        public int veinsPerChunk = 6;

        @Config.Comment("Blocks of copper ore per vein, at most.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.copper.veinSize")
        @Config.RangeInt(min = 1, max = 64)
        public int veinSize = 10;

        @Config.Comment("Lowest height of a copper ore vein.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.copper.minY")
        @Config.RangeInt(min = 1, max = 255)
        public int minY = 1;

        @Config.Comment("Highest height of a copper ore vein.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.copper.maxY")
        @Config.RangeInt(min = 1, max = 255)
        public int maxY = 96;
    }

    public static final class Amethyst {
        @Config.RequiresMcRestart
        @Config.Comment("Amethyst blocks, buds and shards, tinted glass and the spyglass. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.amethyst.enabled")
        public boolean enabled = true;

        @Config.Comment("Amethyst geodes generate underground in newly generated chunks.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.amethyst.geodes")
        public boolean geodes = true;

        @Config.Comment("One geode in this many chunks, on average.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.amethyst.geodeRarity")
        @Config.RangeInt(min = 1, max = 1000)
        public int geodeRarity = 24;

        @Config.Comment("Lowest height of a geode.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.amethyst.geodeMinY")
        @Config.RangeInt(min = 1, max = 240)
        public int geodeMinY = 6;

        @Config.Comment("Highest height of a geode.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.amethyst.geodeMaxY")
        @Config.RangeInt(min = 1, max = 240)
        public int geodeMaxY = 46;
    }

    public static final class Dripstone {
        @Config.RequiresMcRestart
        @Config.Comment("Dripstone blocks, pointed dripstone and the lava cauldron. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.dripstone.enabled")
        public boolean enabled = true;

        @Config.Comment("Extra tries at a copper ore vein in each newly generated chunk of a dripstone cave. 0 for none.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.dripstone.extraCopperVeins")
        @Config.RangeInt(min = 0, max = 64)
        public int extraCopperVeins = 16;
    }

    public static final class Lush {
        @Config.RequiresMcRestart
        @Config.Comment("Moss, azaleas, cave vines, dripleaf, spore blossoms, glow lichen, rooted dirt and hanging roots. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.lush.enabled")
        public boolean enabled = true;

        @Setting.NewChunks
        @Config.Comment("Glow lichen grows on cave walls in newly generated chunks.")
        @Config.LangKey("config.cavesandcliffsbackport.lush.glowLichen")
        public boolean glowLichen = true;

        @Setting.NewChunks
        @Config.Comment("How many times a chunk a glow lichen is tried underground. Most tries land in solid stone.")
        @Config.LangKey("config.cavesandcliffsbackport.lush.lichenTries")
        @Config.RangeInt(min = 0, max = 500)
        public int lichenTries = 40;

        @Setting.NewChunks
        @Config.Comment("Azalea trees grow on the surface above lush caves, with rooted dirt running down to the cave.")
        @Config.LangKey("config.cavesandcliffsbackport.lush.azaleaTrees")
        public boolean azaleaTrees = true;
    }

    public static final class PowderSnow {
        @Config.RequiresMcRestart
        @Config.Comment("Powder snow, its bucket and cauldron, and freezing. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.powderSnow.enabled")
        public boolean enabled = true;

        @Config.Comment("Entities inside powder snow freeze: after 7 seconds they take damage every 2 seconds. Off, powder snow only slows and sinks.")
        @Config.LangKey("config.cavesandcliffsbackport.powderSnow.freezing")
        public boolean freezing = true;

        @Config.Comment("Skeletons that stay in powder snow turn into strays.")
        @Config.LangKey("config.cavesandcliffsbackport.powderSnow.strayConversion")
        public boolean strayConversion = true;

        @Config.Comment("Snowfall slowly fills cauldrons under the open sky with powder snow.")
        @Config.LangKey("config.cavesandcliffsbackport.powderSnow.cauldronSnow")
        public boolean cauldronSnow = true;

        @Config.Comment("Patches of powder snow generate in snowy high ground in newly generated chunks.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.powderSnow.patches")
        public boolean patches = true;

        @Config.Comment("One chunk in this many looks for a place for a patch. Only snowy ground at or above the lowest height takes one.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.powderSnow.patchRarity")
        @Config.RangeInt(min = 1, max = 1000)
        public int patchRarity = 2;

        @Config.Comment("Lowest height of a powder snow patch.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.powderSnow.patchMinY")
        @Config.RangeInt(min = 1, max = 255)
        public int patchMinY = 80;

        @Config.Comment("More items that keep their wearer from freezing, besides leather armor, as item ids. Worn as boots they also walk on powder snow.")
        @Config.LangKey("config.cavesandcliffsbackport.powderSnow.freezeImmuneWearables")
        public String[] freezeImmuneWearables = {};

        @Config.Comment("More mobs that never freeze, besides strays, polar bears, snow golems and the wither, as entity ids.")
        @Config.LangKey("config.cavesandcliffsbackport.powderSnow.freezeImmuneMobs")
        public String[] freezeImmuneMobs = {};

        @Config.Comment("More mobs that freezing hurts five times as much, besides blazes and magma cubes, as entity ids.")
        @Config.LangKey("config.cavesandcliffsbackport.powderSnow.freezeHurtsExtraMobs")
        public String[] freezeHurtsExtraMobs = {};

        @Config.Comment("More mobs that walk on powder snow, besides rabbits, endermites and silverfish, as entity ids.")
        @Config.LangKey("config.cavesandcliffsbackport.powderSnow.walkableMobs")
        public String[] walkableMobs = {};
    }

    public static final class Candles {
        @Config.RequiresMcRestart
        @Config.Comment("Candles, the sixteen dyed candles and candle cakes. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.candles.enabled")
        public boolean enabled = true;
    }

    public static final class GlowSquid {
        @Config.RequiresMcRestart
        @Config.Comment("Glow squid, glow ink sacs and glow item frames. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.glowSquid.enabled")
        public boolean enabled = true;

        @Config.Comment("How often glow squid spawn in dark water deep underground, next to other mobs' weights (vanilla: 10). 0 stops them spawning.")
        @Config.RequiresMcRestart
        @Config.LangKey("config.cavesandcliffsbackport.glowSquid.spawnWeight")
        @Config.RangeInt(min = 0, max = 1000)
        public int spawnWeight = 10;

        @Config.Comment("A glow ink sac used on a sign makes its text glow; an ink sac undoes it.")
        @Config.LangKey("config.cavesandcliffsbackport.glowSquid.glowingSigns")
        public boolean glowingSigns = true;
    }

    public static final class Axolotls {
        @Config.RequiresMcRestart
        @Config.Comment("Axolotls and the bucket of axolotl. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.axolotls.enabled")
        public boolean enabled = true;

        @Config.Comment("Axolotls spawn naturally in lush caves, in water above clay.")
        @Config.LangKey("config.cavesandcliffsbackport.axolotls.spawning")
        public boolean spawning = true;

        @Config.Comment("The most naturally spawned axolotls around one player.")
        @Config.LangKey("config.cavesandcliffsbackport.axolotls.spawnCap")
        @Config.RangeInt(min = 0, max = 100)
        public int spawnCap = 5;

        @Config.Comment({"The item axolotls breed with when the game has no bucket of tropical fish, as \"mod:item\" or \"mod:item:metadata\".",
                "With a bucket of tropical fish in the game, only that bucket works."})
        @Config.LangKey("config.cavesandcliffsbackport.axolotls.breedingItem")
        public String breedingItem = "minecraft:fish:2";

        @Config.Comment("Mobs axolotls hunt in water, then leave alone for two minutes. Mobs that are not installed are skipped.")
        @Setting.ListOf(Setting.Kind.ENTITY)
        @Config.LangKey("config.cavesandcliffsbackport.axolotls.huntTargets")
        public String[] huntTargets = {"minecraft:squid", "cavesandcliffsbackport:glow_squid", "updateaquaticbackport:cod",
                "updateaquaticbackport:salmon", "updateaquaticbackport:pufferfish", "updateaquaticbackport:tropical_fish"};

        @Config.Comment("Mobs axolotls always attack in water. Mobs that are not installed are skipped.")
        @Setting.ListOf(Setting.Kind.ENTITY)
        @Config.LangKey("config.cavesandcliffsbackport.axolotls.hostileTargets")
        public String[] hostileTargets = {"minecraft:guardian", "minecraft:elder_guardian", "updateaquaticbackport:drowned"};

        @Config.Comment("The mobs axolotls always attack go after axolotls too.")
        @Config.LangKey("config.cavesandcliffsbackport.axolotls.hostilesAttackAxolotls")
        public boolean hostilesAttackAxolotls = true;
    }

    public static final class Goats {
        @Config.RequiresMcRestart
        @Config.Comment("Goats. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.goats.enabled")
        public boolean enabled = true;

        @Config.RequiresMcRestart
        @Config.Comment({"How often goats spawn in the biomes of spawnBiomes, next to those biomes' other animals",
                "(sheep are 12, cows 8, llamas 5). 0: goats never spawn on their own."})
        @Config.LangKey("config.cavesandcliffsbackport.goats.spawnWeight")
        @Config.RangeInt(min = 0, max = 100)
        public int spawnWeight = 10;

        @Config.RequiresMcRestart
        @Config.Comment("The biomes goats spawn in, in groups of 1 to 3, on stone, gravel, snow and packed ice.")
        @Config.LangKey("config.cavesandcliffsbackport.goats.spawnBiomes")
        public String[] spawnBiomes = {"minecraft:extreme_hills", "minecraft:smaller_extreme_hills", "minecraft:extreme_hills_with_trees",
                "minecraft:mutated_extreme_hills", "minecraft:mutated_extreme_hills_with_trees"};

        @Config.Comment("Goats ram players and mobs that stand still near them. Off, goats never ram.")
        @Config.LangKey("config.cavesandcliffsbackport.goats.ramming")
        public boolean ramming = true;
    }

    public static final class Blocks {
        @Config.RequiresMcRestart
        @Config.Comment("The light block, the Caves & Cliffs advancement and the small 1.17 changes below. Off, nothing is registered and none of the changes apply.")
        @Config.LangKey("config.cavesandcliffsbackport.blocks.enabled")
        public boolean enabled = true;

        @Config.Comment("A shovel makes a path from dirt, coarse dirt, podzol, mycelium and rooted dirt as well as from grass.")
        @Config.LangKey("config.cavesandcliffsbackport.blocks.dirtPaths")
        public boolean dirtPaths = true;

        @Config.Comment("An open shulker hit by another shulker's bullet teleports away and may leave a new shulker behind, less often the more shulkers are nearby.")
        @Config.LangKey("config.cavesandcliffsbackport.blocks.shulkerDuplication")
        public boolean shulkerDuplication = true;

        @Config.Comment("Zombies, husks and zombie villagers that die while burning drop baked potatoes, and polar bears and guardians cooked fish.")
        @Config.LangKey("config.cavesandcliffsbackport.blocks.cookedMobDrops")
        public boolean cookedMobDrops = true;

        @Config.Comment("Eating a pufferfish gives Poison II instead of Poison IV.")
        @Config.LangKey("config.cavesandcliffsbackport.blocks.pufferfishPoison")
        public boolean pufferfishPoison = true;

        @Config.Comment("Llamas do not spit at players in Peaceful difficulty.")
        @Config.LangKey("config.cavesandcliffsbackport.blocks.peacefulLlamas")
        public boolean peacefulLlamas = true;

        @Config.RequiresMcRestart
        @Config.Comment("Infested blocks break faster with a pickaxe, and infested cobblestone takes half as long to break as cobblestone.")
        @Config.LangKey("config.cavesandcliffsbackport.blocks.infestedBlocks")
        public boolean infestedBlocks = true;
    }

    public static final class Music {
        @Config.RequiresMcRestart
        @Config.Comment("The Otherside music disc and 1.18's music. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.music.enabled")
        public boolean enabled = true;

        @Config.RequiresMcRestart
        @Config.Comment({"The four tracks 1.18 added to the game music play among the old ones, in survival and creative:",
                "Comforting Memories, Floating Dream, Left to Bloom and One More Day."})
        @Config.LangKey("config.cavesandcliffsbackport.music.gameMusic")
        public boolean gameMusic = true;

        @Config.Comment("Lush caves, dripstone caves and the mountain biomes play their own selection of tracks, as in 1.18.")
        @Config.LangKey("config.cavesandcliffsbackport.music.biomeMusic")
        public boolean biomeMusic = true;

        @Config.RequiresMcRestart
        @Config.Comment("The five tracks 1.18 added to the title screen music play among the old ones.")
        @Config.LangKey("config.cavesandcliffsbackport.music.menuMusic")
        public boolean menuMusic = true;

        @Config.Comment({"The Otherside music disc is found in dungeon chests and in the chests of stronghold corridors.",
                "Takes effect when the world is next loaded."})
        @Config.LangKey("config.cavesandcliffsbackport.music.othersideLoot")
        public boolean othersideLoot = true;
    }

    public static final class Caves {
        @Config.Comment("Lush caves: moss, azaleas, cave vines, clay pools and dripleaf in some cave regions of newly generated chunks.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.caves.lushRegions")
        public boolean lushRegions = true;

        @Config.Comment("Dripstone caves: dripstone and pointed dripstone in some cave regions of newly generated chunks.")
        @Setting.NewChunks
        @Config.LangKey("config.cavesandcliffsbackport.caves.dripstoneRegions")
        public boolean dripstoneRegions = true;
    }

    public static final class Height {
        @Config.RequiresMcRestart
        @Config.Comment({"The Caves & Cliffs world type: an overworld from y -64 to 319, as in 1.18. The Nether and the End,",
                "and every world created before, keep y 0 to 255 for good.",
                "Removing this mod from such a world keeps everything from y 0 to 255 and loses everything below and above:",
                "the bedrock is at y -64, so the bottom of the world is then open at y 0.",
                "Refuses to turn on, and says so in the log, when Alfheim, Phosphor, Cubic Chunks, Caves Not Cliffs or",
                "Farmer's Future Delight is installed: they change the same parts of the game."})
        @Config.LangKey("config.cavesandcliffsbackport.height.enabled")
        public boolean enabled = true;

        @Config.Comment({"New worlds that would get the Default world type get the Caves & Cliffs type instead: in single player",
                "and on a server whose level-type is DEFAULT. Takes effect for worlds created afterwards."})
        @Config.LangKey("config.cavesandcliffsbackport.height.defaultForNewWorlds")
        public boolean defaultForNewWorlds = false;

        @Config.RequiresWorldRestart
        @Config.Comment({"Worker threads that build the 1.18 terrain of a Caves & Cliffs world ahead of the server thread.",
                "0: the computer's cores less two, from 1 to 6."})
        @Config.LangKey("config.cavesandcliffsbackport.height.terrainThreads")
        @Config.RangeInt(min = 0, max = 32)
        public int terrainThreads = 0;

        @Config.RequiresWorldRestart
        @Config.Comment({"New Caves & Cliffs worlds with 1.18 terrain also get the newer versions' biomes the installed backports",
                "have blocks for: 1.19's mangrove swamps and deep dark with Wild Update Backport, 1.20's cherry groves with",
                "Trails & Tales Backport, placed where those versions place them. Worlds keep the biomes they were made with."})
        @Config.LangKey("config.cavesandcliffsbackport.height.newerBiomes")
        public boolean newerBiomes = true;
    }

    // ------------------------------------------------------------------------------------------

    /** Saves edits made in the in-game config screen. */
    @Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
    public static final class EventHandler {
        private EventHandler() {
        }

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (CavesAndCliffsBackport.MOD_ID.equals(event.getModID())) {
                ConfigManager.sync(CavesAndCliffsBackport.MOD_ID, Config.Type.INSTANCE);
            }
        }
    }

    private ModConfig() {
    }
}
