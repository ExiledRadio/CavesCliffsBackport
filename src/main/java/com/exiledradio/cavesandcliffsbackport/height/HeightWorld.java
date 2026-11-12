package com.exiledradio.cavesandcliffsbackport.height;

/**
 * Added to {@code net.minecraft.world.World} by the coremod ({@code asm.WorldPatch}), with two int fields behind it:
 * how many blocks the world reaches below y 0 and above y 255. Both start at 0, so a world nobody marks is vanilla's
 * 0-255 with no constructor change. Only {@link Heights} calls this; everything else asks {@link Heights}.
 */
public interface HeightWorld {

    /** The lowest block y. */
    int cavesandcliffsbackport$minY();

    /** One above the highest block y. */
    int cavesandcliffsbackport$maxY();

    /** Sets the world's range; called once, while the world is constructed, before its first chunk exists. */
    void cavesandcliffsbackport$setHeight(int minY, int maxY);
}
