package com.exiledradio.cavesandcliffsbackport.music;

import com.exiledradio.cavesandcliffsbackport.common.CaveRegions;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Which of 1.18's music biomes a position is in: the one question stream Mu asks of the cave regions
 * ({@code common/CaveRegions}) and of the 1.18 terrain.
 *
 * <p>1.12.2 biomes are per column, so a cave biome is not a biome here, and the mountain biomes are the terrain
 * stream's. Whoever knows the answer installs a {@link Locator} once, from common code (it names no client type, and
 * is asked on the client thread only, with the client's world):
 * <pre>{@code
 * MusicRegions.setLocator((world, pos) -> CaveRegions.isLush(world, pos) ? MusicRegions.Region.LUSH_CAVES : ...);
 * }</pre>
 * {@link MusicContent} installs {@link #CAVES}; until something is installed every position is {@link Region#NONE}.
 */
public final class MusicRegions {

    /** 1.18's biomes with music of their own, by their {@code music.overworld.<id>} event. */
    public enum Region {
        NONE(null),
        LUSH_CAVES("lush_caves"),
        DRIPSTONE_CAVES("dripstone_caves"),
        MEADOW("meadow"),
        GROVE("grove"),
        SNOWY_SLOPES("snowy_slopes"),
        JAGGED_PEAKS("jagged_peaks"),
        FROZEN_PEAKS("frozen_peaks"),
        STONY_PEAKS("stony_peaks");

        /** 1.18's biome id, or null for {@link #NONE}. */
        public final String id;

        Region(String id) {
            this.id = id;
        }
    }

    /** Answers for one position. Must be cheap (asked every client tick) and must not load or generate chunks. */
    public interface Locator {
        Region at(World world, BlockPos pos);
    }

    private static final Locator NOWHERE = new Locator() {
        @Override
        public Region at(World world, BlockPos pos) {
            return Region.NONE;
        }
    };

    /**
     * Lush and dripstone caves, from {@link CaveRegions}: underground in one of its regions. On a client this is
     * what the server sent at login, so it costs one noise sample and loads nothing (the player's chunk is loaded).
     */
    public static final Locator CAVES = new Locator() {
        @Override
        public Region at(World world, BlockPos pos) {
            if (CaveRegions.lush(world, pos.getX(), pos.getY(), pos.getZ())) {
                return CaveRegions.underground(world, pos) ? Region.LUSH_CAVES : Region.NONE;
            }
            if (CaveRegions.dripstone(world, pos.getX(), pos.getY(), pos.getZ())) {
                return CaveRegions.underground(world, pos) ? Region.DRIPSTONE_CAVES : Region.NONE;
            }
            return Region.NONE;
        }
    };

    private static volatile Locator locator = NOWHERE;

    private MusicRegions() {
    }

    /** Installs the answer; null puts back "none everywhere". */
    public static void setLocator(Locator value) {
        locator = value == null ? NOWHERE : value;
    }

    /** @return the music biome at a position; never null, and {@link Region#NONE} if the locator fails */
    public static Region at(World world, BlockPos pos) {
        try {
            Region region = locator.at(world, pos);
            return region == null ? Region.NONE : region;
        } catch (RuntimeException e) {
            return Region.NONE;
        }
    }
}
