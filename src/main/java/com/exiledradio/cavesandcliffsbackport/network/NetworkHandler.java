package com.exiledradio.cavesandcliffsbackport.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

/**
 * The mod's one network channel.
 *
 * <h2>Packet ids</h2>
 * Each stream owns a block of five ids (see {@code docs/PLAN.md}) and registers its packets here, inside
 * its own block, so two streams can never pick the same number and merges stay mechanical:
 *
 * <pre>
 *   0-4   common          25-29 lush           50-54 goats
 *   5-9   deepslate       30-34 powder snow    55-59 blocks
 *   10-14 copper          35-39 candles        60-64 music
 *   15-19 amethyst        40-44 glow squid     65-74 terrain
 *   20-24 dripstone       45-49 axolotls
 * </pre>
 *
 * <p>The channel name is short because Forge caps channel names at 20 characters, one fewer than the
 * mod id.
 */
public final class NetworkHandler {

    public static final String CHANNEL = "ccbackport";

    public static SimpleNetworkWrapper channel;

    private NetworkHandler() {
    }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        // Streams register their packets below, in id order.
        channel.registerMessage(com.exiledradio.cavesandcliffsbackport.common.PacketCaveRegions.Handler.class,
                com.exiledradio.cavesandcliffsbackport.common.PacketCaveRegions.class, 0, net.minecraftforge.fml.relauncher.Side.CLIENT);
        channel.registerMessage(com.exiledradio.cavesandcliffsbackport.copper.PacketCopperParticles.Handler.class,
                com.exiledradio.cavesandcliffsbackport.copper.PacketCopperParticles.class, 10, net.minecraftforge.fml.relauncher.Side.CLIENT);
        channel.registerMessage(com.exiledradio.cavesandcliffsbackport.powdersnow.PacketFreeze.Handler.class,
                com.exiledradio.cavesandcliffsbackport.powdersnow.PacketFreeze.class, 30, net.minecraftforge.fml.relauncher.Side.CLIENT);
        channel.registerMessage(com.exiledradio.cavesandcliffsbackport.glowsquid.PacketGlowInk.Handler.class,
                com.exiledradio.cavesandcliffsbackport.glowsquid.PacketGlowInk.class, 40, net.minecraftforge.fml.relauncher.Side.CLIENT);
        channel.registerMessage(com.exiledradio.cavesandcliffsbackport.terrain.PacketChunkBiomes.Handler.class,
                com.exiledradio.cavesandcliffsbackport.terrain.PacketChunkBiomes.class, 65, net.minecraftforge.fml.relauncher.Side.CLIENT);
        channel.registerMessage(com.exiledradio.cavesandcliffsbackport.height.HeightPacket.Handler.class,
                com.exiledradio.cavesandcliffsbackport.height.HeightPacket.class, 66, net.minecraftforge.fml.relauncher.Side.CLIENT);
    }
}
