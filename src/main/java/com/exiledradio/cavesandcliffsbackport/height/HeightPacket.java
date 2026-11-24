package com.exiledradio.cavesandcliffsbackport.height;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.network.NetworkHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client, packet id 66: the overworld runs from y -64 to 319 though its world type is another mod's (RTG,
 * OTG with {@code height.otherWorldTypes}, or one upgraded by {@code /cavesandcliffs upgradeworld}). A Caves &amp; Cliffs
 * world needs none: the client sees its world type in the join and respawn packets.
 *
 * <p>Sent when the player joins the overworld: on login and through a portal the server adds the player to the world
 * before it sends the first chunk, so the client is extended before any chunk arrives. A respawn sends the chunks first
 * (PlayerList.recreatePlayerEntity), so after one the chunks go again.
 */
public class HeightPacket implements IMessage {

    private int min;
    private int max;

    public HeightPacket() {
    }

    HeightPacket(int min, int max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.min = buf.readInt();
        this.max = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.min);
        buf.writeInt(this.max);
    }

    public static final class Handler implements IMessageHandler<HeightPacket, IMessage> {
        @Override
        public IMessage onMessage(final HeightPacket m, MessageContext ctx) {
            if (m.min != HeightLayout.EXTENDED_MIN_Y || m.max != HeightLayout.EXTENDED_MAX_Y) {
                return null;
            }
            // On the client thread, before the chunk packets that follow it.
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(
                    com.exiledradio.cavesandcliffsbackport.height.client.ClientHeight::extend);
            return null;
        }
    }

    /** Whether players of this world need the packet: an extended overworld that is not a Caves &amp; Cliffs one. */
    static boolean needed(World world) {
        return !world.isRemote && world.provider.getDimension() == 0 && Heights.isExtended(world)
                && world.getWorldInfo().getTerrainType() != HeightContent.worldType();
    }

    @Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onJoin(EntityJoinWorldEvent event) {
            if (event.getEntity() instanceof EntityPlayerMP && needed(event.getWorld())) {
                EntityPlayerMP player = (EntityPlayerMP) event.getEntity();
                if (player.connection != null) {
                    NetworkHandler.channel.sendTo(new HeightPacket(Heights.minY(event.getWorld()),
                            Heights.maxY(event.getWorld())), player);
                }
            }
        }

        /**
         * A respawn into the overworld from another dimension makes a new client world, and the server sent it the
         * chunks before the player joined it (so before the packet): they go again now, to the extended client.
         */
        @SubscribeEvent
        public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.player instanceof EntityPlayerMP && needed(event.player.world)) {
                EntityPlayerMP player = (EntityPlayerMP) event.player;
                PlayerChunkMap map = ((WorldServer) player.world).getPlayerChunkMap();
                map.removePlayer(player);
                map.addPlayer(player);
            }
        }
    }
}
