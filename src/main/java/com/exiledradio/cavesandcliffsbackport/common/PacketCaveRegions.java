package com.exiledradio.cavesandcliffsbackport.common;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.network.NetworkHandler;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client, packet id 0, on login: the cave region key ({@link CaveRegions#key}, a hash of the seed, never
 * the seed) and the server's two region switches, so the client finds the same regions for its music.
 */
public class PacketCaveRegions implements IMessage {

    private long key;
    private boolean lush, dripstone;

    public PacketCaveRegions() {
    }

    public PacketCaveRegions(long key, boolean lush, boolean dripstone) {
        this.key = key;
        this.lush = lush;
        this.dripstone = dripstone;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.key = buf.readLong();
        int flags = buf.readUnsignedByte();
        this.lush = (flags & 1) != 0;
        this.dripstone = (flags & 2) != 0;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.key);
        buf.writeByte((this.lush ? 1 : 0) | (this.dripstone ? 2 : 0));
    }

    public static final class Handler implements IMessageHandler<PacketCaveRegions, IMessage> {
        @Override
        public IMessage onMessage(PacketCaveRegions message, MessageContext ctx) {
            CaveRegions.receive(message.key, message.lush, message.dripstone);
            return null;
        }
    }

    @Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.player instanceof EntityPlayerMP) {
                long seed = event.player.getServer().getWorld(0).getSeed();
                NetworkHandler.channel.sendTo(new PacketCaveRegions(CaveRegions.key(seed), ModConfig.caves.lushRegions,
                        ModConfig.caves.dripstoneRegions), (EntityPlayerMP) event.player);
            }
        }

        @SubscribeEvent
        public static void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            CaveRegions.forget();
        }
    }
}
