package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.network.NetworkHandler;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;

import io.netty.buffer.ByteBuf;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client, packet id 65, after each chunk packet of a world with 1.18 terrain: the chunk's stored 1.18
 * biomes (1.18's chunk packet carries them in each section), with the world's zoom seed (the seed's SHA-256, which
 * 1.18 sends in its login packet; never the seed). The client keeps them in {@link ClientBiomes}.
 *
 * <p>The 1,536 quarts go as runs (a biome id and a count), usually a few dozen bytes.
 */
public class PacketChunkBiomes implements IMessage {

    private int dimension;
    private long zoomSeed;
    private int cx, cz;
    private byte[] quarts;

    public PacketChunkBiomes() {
    }

    PacketChunkBiomes(int dimension, long zoomSeed, int cx, int cz, byte[] quarts) {
        this.dimension = dimension;
        this.zoomSeed = zoomSeed;
        this.cx = cx;
        this.cz = cz;
        this.quarts = quarts;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.dimension = buf.readInt();
        this.zoomSeed = buf.readLong();
        this.cx = buf.readInt();
        this.cz = buf.readInt();
        byte[] q = new byte[Pipeline.QUARTS];
        int i = 0;
        while (i < q.length) {
            byte b = buf.readByte();
            int n = ByteBufUtils.readVarInt(buf, 5);
            if (n <= 0 || n > q.length - i) {
                throw new IllegalArgumentException("bad biome run " + n + " at " + i);
            }
            java.util.Arrays.fill(q, i, i + n, b);
            i += n;
        }
        this.quarts = q;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.dimension);
        buf.writeLong(this.zoomSeed);
        buf.writeInt(this.cx);
        buf.writeInt(this.cz);
        byte[] q = this.quarts;
        int i = 0;
        while (i < q.length) {
            int j = i + 1;
            while (j < q.length && q[j] == q[i]) {
                j++;
            }
            buf.writeByte(q[i]);
            ByteBufUtils.writeVarInt(buf, j - i, 5);
            i = j;
        }
    }

    public static final class Handler implements IMessageHandler<PacketChunkBiomes, IMessage> {
        @Override
        public IMessage onMessage(final PacketChunkBiomes m, MessageContext ctx) {
            // On the client thread, in order with the chunk packets and their unloads.
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(
                    () -> ClientBiomes.put(m.dimension, m.zoomSeed, m.cx, m.cz, m.quarts));
            return null;
        }
    }

    @Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
    public static final class Events {
        private Events() {
        }

        /** Fired just after the chunk packet went to the player (PlayerChunkMapEntry). */
        @SubscribeEvent
        public static void onWatch(ChunkWatchEvent.Watch event) {
            net.minecraft.world.chunk.Chunk chunk = event.getChunkInstance();
            if (chunk == null) {
                return;
            }
            World world = chunk.getWorld();
            if (world.isRemote || !ModernTerrain.owns(world)) {
                return;
            }
            ModernGenerator gen = (ModernGenerator) ((WorldServer) world).getChunkProvider().chunkGenerator;
            byte[] q = gen.quarts(chunk.x, chunk.z);
            if (q != null) {
                NetworkHandler.channel.sendTo(new PacketChunkBiomes(world.provider.getDimension(), gen.zoomSeed(),
                        chunk.x, chunk.z, q), event.getPlayer());
            }
        }

        @SubscribeEvent
        public static void onChunkUnload(ChunkEvent.Unload event) {
            if (event.getWorld().isRemote) {
                ClientBiomes.remove(event.getChunk().x, event.getChunk().z);
            }
        }

        @SubscribeEvent
        public static void onWorldUnload(WorldEvent.Unload event) {
            if (event.getWorld().isRemote) {
                ClientBiomes.forget();
            }
        }

        @SubscribeEvent
        public static void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            FMLCommonHandler.instance().getWorldThread(event.getHandler()).addScheduledTask(ClientBiomes::forget);
        }
    }
}
