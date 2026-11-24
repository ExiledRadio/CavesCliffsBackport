package com.exiledradio.cavesandcliffsbackport.height.compat;

import com.exiledradio.cavesandcliffsbackport.height.Heights;

import net.minecraft.world.World;

import java.util.Random;

/**
 * Scape and Run: Parasites 1.9.21 in a world with 1.18's height (patch in {@code height.asm.ModCompatPatches}).
 * SRP spawns its mobs with a spawner of its own, {@code SRPWorldEntitySpawner}, whose {@code getRandomChunkPosition}
 * is a copy of vanilla's: it draws a spawn attempt's height from 0, so no parasite ever spawned below y 0. It now
 * draws from the world's bottom, exactly as the patched vanilla spawner does. SRP 1.10 spawns through
 * {@code SRPWorldParasiteSpawner}, whose draw is the same copy (same patch), and rewrote the old spawner's draw and
 * searches to start at y 1 ({@link #floor}).
 */
public final class ParasitesHooks {

    private ParasitesHooks() {
    }

    /**
     * {@code SRPWorldEntitySpawner.getRandomChunkPosition}: {@code random.nextInt(bound)} becomes a draw from the
     * world's bottom up to {@code bound}. One random draw either way, and the same draw as SRP's in a world of 0-255.
     */
    public static int spawnY(Random random, int bound, World world) {
        return Heights.spawnY(random, bound, world);
    }

    /**
     * SRP 1.10's {@code SRPWorldEntitySpawner}: a search or draw floor SRP wrote as a constant (y 1) becomes the same
     * distance above the world's bottom. Unchanged in a world of 0-255.
     */
    public static int floor(int y, World world) {
        return y + Heights.minY(world);
    }
}
