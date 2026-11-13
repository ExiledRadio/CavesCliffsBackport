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
        GameMusic.add("1.18", "biome music", new GameMusic.Step() {
            @Override
            public void choose(GameMusic.Situation s) {
                biomeMusic(s);
            }
        });
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

    private static void installGameMusic() {
        GameMusicPack pack = new GameMusicPack();
        try {
            if (!pack.complete()) {
                throw new IllegalStateException(GameMusicPack.FILE + " is missing from the jar");
            }
            Minecraft mc = Minecraft.getMinecraft();
            // Kept for every later reload (F3+T, a resource pack change), which rebuilds from this list... (The
            // (name, obfName) overload takes the dev name first; the other order failed in every game and dev run.)
            List<IResourcePack> defaults = ReflectionHelper.getPrivateValue(Minecraft.class, mc, "defaultResourcePacks", "field_110449_ao");
            defaults.add(pack);
            // ...and added to the live manager now: the startup reload has happened, the sound handler's has not.
            IReloadableResourceManager manager = (IReloadableResourceManager) mc.getResourceManager();
            if (manager instanceof SimpleReloadableResourceManager) {
                ((SimpleReloadableResourceManager) manager).reloadResourcePack(pack);
            }
            CavesAndCliffsBackport.LOGGER.info("Music: 1.18's tracks join the game music.");
        } catch (Exception e) {
            CavesAndCliffsBackport.LOGGER.warn("Music: could not add 1.18's tracks to the game music.", e);
        }
    }
}
