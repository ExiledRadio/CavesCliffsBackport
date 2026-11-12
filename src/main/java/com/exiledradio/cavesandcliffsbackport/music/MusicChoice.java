package com.exiledradio.cavesandcliffsbackport.music;

import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundEvent;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Which music 1.18 asks for where 1.12.2 would play the game music: the last line of 1.18.2's
 * {@code Minecraft.getSituationalMusic}, {@code biome.getBackgroundMusic().orElse(Musics.GAME)}.
 *
 * <p>The choosing logic alone: it reads no game state (the client fills in the situation) and plays nothing
 * (Backport Core's music ticker does). No client types, so the self-test checks the rules on a server.
 */
public final class MusicChoice {

    /** 1.18's {@code Music}: a sound event, the wait before it, and whether it cuts off a different track. */
    public static final class Track {
        public final SoundEvent event;
        public final int minDelay;
        public final int maxDelay;
        public final boolean replaceCurrent;

        public Track(SoundEvent event, int minDelay, int maxDelay, boolean replaceCurrent) {
            this.event = event;
            this.minDelay = minDelay;
            this.maxDelay = maxDelay;
            this.replaceCurrent = replaceCurrent;
        }

        @Override
        public String toString() {
            return this.event.getRegistryName() + "[" + this.minDelay + "-" + this.maxDelay
                    + (this.replaceCurrent ? ", replaces" : "") + "]";
        }
    }

    private static final Map<MusicRegions.Region, Track> BIOME = new EnumMap<MusicRegions.Region, Track>(MusicRegions.Region.class);

    private MusicChoice() {
    }

    /** 1.18's {@code Musics.createGameMusic}: 10 to 20 minutes apart, never cutting off another track. */
    public static Track game(SoundEvent event) {
        return new Track(event, 12000, 24000, false);
    }

    /** Called by {@link MusicContent} once the biome's music event exists. */
    static void setBiome(MusicRegions.Region region, SoundEvent event) {
        BIOME.put(region, game(event));
    }

    /** @return a music biome's track, or null for {@link MusicRegions.Region#NONE} and while the stream is off */
    @Nullable
    public static Track biome(MusicRegions.Region region) {
        return BIOME.get(region);
    }

    /** @return true when the event is one of the biomes' music events */
    public static boolean isBiomeMusic(@Nullable SoundEvent event) {
        if (event != null) {
            for (Track track : BIOME.values()) {
                if (track.event == event) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 1.18's change to the choice made so far.
     *
     * @param chosen       the event chosen by 1.12.2 and the versions before 1.18 (the game music, the creative
     *                     music, the nether's, a dimension's own, ...)
     * @param region       the music biome at the player's feet
     * @param biomePlaying true while one of the biomes' tracks is the one playing
     * @return the track to play instead, or null to leave the choice alone
     */
    @Nullable
    public static Track choose(SoundEvent chosen, MusicRegions.Region region, boolean biomePlaying) {
        // Only where the game music would play: creative flying, under water, the nether, the End and a
        // dimension's own music all come before the biome's in 1.18.
        if (chosen != SoundEvents.MUSIC_GAME) {
            return null;
        }
        Track track = biome(region);
        if (track != null) {
            return track;
        }
        // Walking out of the biome: 1.18's game music lets the playing track finish. 1.12.2's own cuts a
        // different track off, which would end the cave music at every region edge.
        return biomePlaying ? game(SoundEvents.MUSIC_GAME) : null;
    }
}
