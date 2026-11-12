package com.exiledradio.cavesandcliffsbackport.height;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.Random;

/**
 * A world's height, for every patched site and for the rest of the mod. Plain static methods: the patches only push
 * their arguments and call one of these, so all the logic is ordinary Java.
 *
 * <p>A world without extended height (every world but a Caves & Cliffs overworld, and every world when the coremod
 * is off) answers 0 / 256 / 16 sections, and every patched site then computes exactly what vanilla's did.
 */
public final class Heights {

    /**
     * Whether {@code World} carries {@link HeightWorld}. False when the coremod did not run or its World patch was
     * skipped; then no world is ever extended and the cast below is never reached.
     */
    private static final boolean WORLD_PATCHED = HeightWorld.class.isAssignableFrom(World.class);

    private Heights() {
    }

    /** The lowest block y of the world: -64 in an extended world, else 0. */
    public static int minY(World world) {
        return WORLD_PATCHED && world != null ? ((HeightWorld) world).cavesandcliffsbackport$minY() : 0;
    }

    /** One above the highest block y: 320 in an extended world, else 256. */
    public static int maxY(World world) {
        return WORLD_PATCHED && world != null ? ((HeightWorld) world).cavesandcliffsbackport$maxY() : 256;
    }

    /** The highest block y. */
    public static int topY(World world) {
        return maxY(world) - 1;
    }

    public static boolean isExtended(World world) {
        return minY(world) != 0 || maxY(world) != 256;
    }

    /** Length of a chunk's section array in this world: 24 when extended, else 16. */
    public static int sections(World world) {
        return isExtended(world) ? HeightLayout.EXTENDED_SECTIONS : HeightLayout.VANILLA_SECTIONS;
    }

    /** The storage slot of block y in this world's chunks, or -1 when the world has no such height. */
    public static int slot(World world, int y) {
        int slot = HeightLayout.slot(y);
        return slot < sections(world) ? slot : -1;
    }

    /** Whether the world holds blocks at height y. */
    public static boolean inside(World world, int y) {
        return y >= minY(world) && y < maxY(world);
    }

    // ---------------------------------------------------------------------------------------------
    // Called from patched sites only (see asm/*Patch). Each keeps vanilla's result in a vanilla world.
    // ---------------------------------------------------------------------------------------------

    /** {@code Chunk.relightBlock}: vanilla masks the stored height with 255; an extended height is a full int. */
    public static int maskHeight(int height, World world) {
        return isExtended(world) ? height : height & 255;
    }

    /** The lowest section y ({@code minY >> 4}). */
    public static int minSection(World world) {
        return minY(world) >> 4;
    }

    /** One above the highest section y ({@code maxY >> 4}). */
    public static int maxSection(World world) {
        return maxY(world) >> 4;
    }

    /** {@code RenderGlobal.setupTerrain}: the middle of the top render chunk (vanilla's 248). */
    public static int topSectionMiddle(World world) {
        return maxY(world) - 8;
    }

    /** {@code RenderGlobal.setupTerrain}: the middle of the bottom render chunk (vanilla's 8). */
    public static int bottomSectionMiddle(World world) {
        return minY(world) + 8;
    }

    /** {@code Entity.onEntityUpdate}: the void starts 64 blocks below the world's bottom (y -64 in vanilla). */
    public static double voidY(World world) {
        return minY(world) - 64.0D;
    }

    /** {@code EntityFallingBlock.onUpdate}: one above the world's bottom (vanilla's 1). */
    public static int aboveBottom(World world) {
        return minY(world) + 1;
    }

    /** A chunk's relight round-robin length: 256 columns per section ({@code 4096} in a vanilla chunk). */
    public static int relightChecks(Chunk chunk) {
        return chunk.getBlockStorageArray().length << 8;
    }

    /** A chunk's section count, for its relight round robin. */
    public static int chunkSections(Chunk chunk) {
        return chunk.getBlockStorageArray().length;
    }

    /** Columns times sections of a chunk's relight round robin ({@code 256} in a vanilla chunk). */
    public static int chunkSectionsTimes16(Chunk chunk) {
        return chunk.getBlockStorageArray().length << 4;
    }

    /**
     * {@code WorldEntitySpawner.getRandomChunkPosition}: the height a spawn attempt tries. Vanilla draws
     * {@code nextInt(bound)}, from y 0; an extended world draws from its bottom, as 1.18 does. One draw either way.
     */
    public static int spawnY(Random random, int bound, World world) {
        int min = minY(world);
        return min == 0 ? random.nextInt(bound) : min + random.nextInt(bound - min);
    }

    /**
     * {@code NetHandlerPlayServer}: the build limit for a player's world. The server's {@code max-build-height}
     * (256 at most) stays the limit everywhere but an extended world, whose limit is its top when the setting is at
     * vanilla's maximum.
     */
    public static int buildLimit(MinecraftServer server, EntityPlayerMP player) {
        int limit = server.getBuildLimit();
        World world = player.world;
        return limit >= 256 && isExtended(world) ? maxY(world) : limit;
    }
}
