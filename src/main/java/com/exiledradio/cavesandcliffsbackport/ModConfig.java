package com.exiledradio.cavesandcliffsbackport;

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
    @Config.LangKey("config.cavesandcliffsbackport.deepslate")
    @Config.Comment("Deepslate and its sets, calcite, tuff, smooth basalt, deepslate ores and raw ores.")
    public static final Deepslate deepslate = new Deepslate();
    @Config.LangKey("config.cavesandcliffsbackport.copper")
    @Config.Comment("Copper ore, raw copper, copper blocks and their oxidation, waxing and scraping, and lightning rods.")
    public static final Copper copper = new Copper();
    @Config.LangKey("config.cavesandcliffsbackport.amethyst")
    @Config.Comment("Amethyst, budding amethyst and its buds, tinted glass, the spyglass and geodes.")
    public static final Amethyst amethyst = new Amethyst();
    @Config.LangKey("config.cavesandcliffsbackport.dripstone")
    @Config.Comment("Dripstone, pointed dripstone, the lava cauldron and dripstone caves.")
    public static final Dripstone dripstone = new Dripstone();
    @Config.LangKey("config.cavesandcliffsbackport.lush")
    @Config.Comment("Moss, azalea, cave vines and glow berries, dripleaf, spore blossoms, glow lichen, rooted dirt, hanging roots and lush caves.")
    public static final Lush lush = new Lush();
    @Config.LangKey("config.cavesandcliffsbackport.powderSnow")
    @Config.Comment("Powder snow, freezing, the powder snow bucket and cauldron.")
    public static final PowderSnow powderSnow = new PowderSnow();
    @Config.LangKey("config.cavesandcliffsbackport.candles")
    @Config.Comment("Candles and candle cakes.")
    public static final Candles candles = new Candles();
    @Config.LangKey("config.cavesandcliffsbackport.glowSquid")
    @Config.Comment("Glow squid, glow ink sacs and glow item frames.")
    public static final GlowSquid glowSquid = new GlowSquid();
    @Config.LangKey("config.cavesandcliffsbackport.axolotls")
    @Config.Comment("Axolotls and the bucket of axolotl.")
    public static final Axolotls axolotls = new Axolotls();
    @Config.LangKey("config.cavesandcliffsbackport.goats")
    @Config.Comment("Goats and screaming goats.")
    public static final Goats goats = new Goats();
    @Config.LangKey("config.cavesandcliffsbackport.blocks")
    @Config.Comment("Smaller 1.17 additions: the light block and the Caves & Cliffs advancement.")
    public static final Blocks blocks = new Blocks();
    @Config.LangKey("config.cavesandcliffsbackport.music")
    @Config.Comment("1.18's music and the Otherside music disc.")
    public static final Music music = new Music();
    @Config.LangKey("config.cavesandcliffsbackport.caves")
    @Config.Comment("Where lush and dripstone caves are: the regions the cave streams decorate.")
    public static final Caves caves = new Caves();

    // ------------------------------------------------------------------------------------------
    // Categories. Each stream owns exactly one; caves is common's.
    // ------------------------------------------------------------------------------------------

    public static final class Deepslate {
        @Config.RequiresMcRestart
        @Config.Comment("Deepslate, its stairs, slabs and walls, calcite, tuff, smooth basalt, the deepslate ores and raw iron and gold. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.deepslate.enabled")
        public boolean enabled = true;
    }

    public static final class Copper {
        @Config.RequiresMcRestart
        @Config.Comment("Copper ore, raw copper, the copper blocks and lightning rods. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.copper.enabled")
        public boolean enabled = true;
    }

    public static final class Amethyst {
        @Config.RequiresMcRestart
        @Config.Comment("Amethyst blocks, buds and shards, tinted glass and the spyglass. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.amethyst.enabled")
        public boolean enabled = true;
    }

    public static final class Dripstone {
        @Config.RequiresMcRestart
        @Config.Comment("Dripstone blocks, pointed dripstone and the lava cauldron. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.dripstone.enabled")
        public boolean enabled = true;
    }

    public static final class Lush {
        @Config.RequiresMcRestart
        @Config.Comment("Moss, azaleas, cave vines, dripleaf, spore blossoms, glow lichen, rooted dirt and hanging roots. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.lush.enabled")
        public boolean enabled = true;
    }

    public static final class PowderSnow {
        @Config.RequiresMcRestart
        @Config.Comment("Powder snow, its bucket and cauldron, and freezing. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.powderSnow.enabled")
        public boolean enabled = true;
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
    }

    public static final class Axolotls {
        @Config.RequiresMcRestart
        @Config.Comment("Axolotls and the bucket of axolotl. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.axolotls.enabled")
        public boolean enabled = true;
    }

    public static final class Goats {
        @Config.RequiresMcRestart
        @Config.Comment("Goats. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.goats.enabled")
        public boolean enabled = true;
    }

    public static final class Blocks {
        @Config.RequiresMcRestart
        @Config.Comment("The light block and the other small 1.17 additions. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.blocks.enabled")
        public boolean enabled = true;
    }

    public static final class Music {
        @Config.RequiresMcRestart
        @Config.Comment("The Otherside music disc and 1.18's music. Off, nothing is registered.")
        @Config.LangKey("config.cavesandcliffsbackport.music.enabled")
        public boolean enabled = true;
    }

    public static final class Caves {
        @Config.Comment("Lush caves: moss, azaleas, cave vines, clay pools and dripleaf in some cave regions of newly generated chunks.")
        @Config.LangKey("config.cavesandcliffsbackport.caves.lushRegions")
        public boolean lushRegions = true;

        @Config.Comment("Dripstone caves: dripstone and pointed dripstone in some cave regions of newly generated chunks.")
        @Config.LangKey("config.cavesandcliffsbackport.caves.dripstoneRegions")
        public boolean dripstoneRegions = true;
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
