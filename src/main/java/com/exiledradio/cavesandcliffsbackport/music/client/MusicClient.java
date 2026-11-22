package com.exiledradio.cavesandcliffsbackport.music.client;

import com.exiledradio.backportcore.presentation.client.GameMusic;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.music.MusicChoice;
import com.exiledradio.cavesandcliffsbackport.music.MusicContent;
import com.exiledradio.cavesandcliffsbackport.music.MusicRegions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.util.List;

/**
 * Client side of stream Mu: 1.18's biome music as Backport Core's "1.18" music step (Core's ticker plays it), and
 * the built-in pack that adds 1.18's tracks to the game music.
 */
public final class MusicClient {

    private MusicClient() {
    }

    /** From {@code ClientSetup}, in pre-init: before the sound handler first reads the sound lists. */
    public static void init() {
        if (!MusicContent.enabled()) {
            return;
        }
        if (ModConfig.music.gameMusic) {
            installGameMusic();
        }
        if (ModConfig.music.biomeMusic && fingerspitShipped()) {
            installFingerspit();
        }
        GameMusic.add("1.18", "biome music", new GameMusic.Step() {
            @Override
            public void choose(GameMusic.Situation s) {
                biomeMusic(s);
            }
        });
        if ("music".equals(System.getProperty(CavesAndCliffsBackport.MOD_ID + ".clienttest"))) {
            // A test build's client test (runClient -Pclienttest=music), reached by name: a release has no test classes.
            try {
                Class.forName("com.exiledradio.cavesandcliffsbackport.selftest.MusicClientSelfTest")
                        .getMethod("start", String.class).invoke(null, "music");
            } catch (ReflectiveOperationException e) {
                CavesAndCliffsBackport.LOGGER.error("CLIENT SELFTEST DONE pass=0 fail=1: the test classes are not in this build.", e);
            }
        }
    }

    /** 1.18.2's {@code getSituationalMusic}, its last line: the biome's music where the game music would play. */
    static void biomeMusic(GameMusic.Situation s) {
        Minecraft mc = s.mc;
        if (!ModConfig.music.biomeMusic || mc.player == null || mc.world == null) {
            return;
        }
        boolean playing = false;
        for (MusicRegions.Region region : MusicRegions.Region.values()) {
            MusicChoice.Track track = MusicChoice.biome(region);
            if (track != null && s.isPlaying(track.event)) {
                playing = true;
                break;
            }
        }
        MusicChoice.Track track = MusicChoice.choose(s.choice().event, MusicRegions.at(mc.world, new BlockPos(mc.player)), playing);
        if (track != null) {
            s.choose(new GameMusic.Track(track.event, track.minDelay, track.maxDelay, track.replaceCurrent));
        }
    }

    /** For the client self-test: 26.2's biome additions are in the default packs. */
    public static volatile boolean fingerspitInstalled;

    /** @return true when Game Drops Backport's fingerspit tracks are installed (26.2, which added them to the lists) */
    public static boolean fingerspitShipped() {
        return MusicClient.class.getResource("/assets/gamedropsbackport/sounds/music/game/ebb.ogg") != null;
    }

    private static void installGameMusic() {
        if (addPack(new GameMusicPack())) {
            CavesAndCliffsBackport.LOGGER.info("Music: 1.18's tracks join the game music.");
        }
    }

    /** 26.2: fingerspit's tracks join the eight biome lists, as 26.3's sounds.json has them. */
    private static void installFingerspit() {
        fingerspitInstalled = addPack(new GameMusicPack(GameMusicPack.FINGERSPIT, CavesAndCliffsBackport.MOD_ID, "fingerspit"));
        if (fingerspitInstalled) {
            CavesAndCliffsBackport.LOGGER.info("Music: 26.2's tracks join the biome music.");
        }
    }

    private static boolean addPack(GameMusicPack pack) {
        try {
            if (!pack.complete()) {
                throw new IllegalStateException(pack.getPackName() + " is missing from the jar");
            }
            Minecraft mc = Minecraft.getMinecraft();
            // Kept for every later reload (F3+T, a resource pack change), which rebuilds from this list...
            // The two-name overload is (MCP name, SRG name) and uses one by environment: SRG first failed in every game.
            List<IResourcePack> defaults = ReflectionHelper.getPrivateValue(Minecraft.class, mc, "defaultResourcePacks", "field_110449_ao");
            defaults.add(pack);
            // ...and added to the live manager now: the startup reload has happened, the sound handler's has not.
            IReloadableResourceManager manager = (IReloadableResourceManager) mc.getResourceManager();
            if (manager instanceof SimpleReloadableResourceManager) {
                ((SimpleReloadableResourceManager) manager).reloadResourcePack(pack);
            }
            return true;
        } catch (Exception e) {
            CavesAndCliffsBackport.LOGGER.warn("Music: could not add the built-in pack {}.", pack.getPackName(), e);
            return false;
        }
    }
}
