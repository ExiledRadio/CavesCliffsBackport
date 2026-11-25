package com.exiledradio.cavesandcliffsbackport.height;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.network.NetworkHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client, packet id 66: the overworld runs from y -64 to 319 though its world type is another mod's (RTG,
 * OTG with {@code height.otherWorldTypes}, or one upgraded by {@code /cavesandcliffs upgradeworld}). A Caves &amp; Cliffs
 * world needs none: the client sees its world type in the join and respawn packets.
 *
 * <p>Sent once per connection, as the player's connection starts (FML's ServerConnectionFromClientEvent, in
 * PlayerList.initializeConnectionToPlayer before the join packet). The client keeps it until it disconnects
 * ({@link HeightHooks#serverOverworldExtended}), so every overworld client world it makes on that server (the login,
 * a portal, a respawn from another dimension) is extended when it is made, before any chunk arrives. A respawn sends
 * the overworld's chunks before the player joins the world (PlayerList.recreatePlayerEntity, with another player near
 * the spawn), so a later notice would come too late for them.
 *
 * <p>Also sent when the player joins the overworld, for a client that got its world first some other way (the client
 * then extends its current world: {@code height.client.ClientHeight}).
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
            // On the client thread, in order with the join, respawn and chunk packets around it.
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                HeightHooks.serverOverworldExtended = true;
                com.exiledradio.cavesandcliffsbackport.height.client.ClientHeight.extend();
            });
            return null;
        }
    }

    /** Whether players of this world need the packet: an extended overworld that is not a Caves &amp; Cliffs one. */
    static boolean needed(World world) {
        return world != null && !world.isRemote && world.provider.getDimension() == 0 && Heights.isExtended(world)
                && world.getWorldInfo().getTerrainType() != HeightContent.worldType();
    }

    private static void send(EntityPlayerMP player, World overworld) {
        NetworkHandler.channel.sendTo(new HeightPacket(Heights.minY(overworld), Heights.maxY(overworld)), player);
    }

    @Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
    public static final class Events {
        private Events() {
        }

        /** The connection starts (server thread, before the join packet): told once, for the whole connection. */
        @SubscribeEvent
        public static void onConnection(FMLNetworkEvent.ServerConnectionFromClientEvent event) {
            if (!(event.getHandler() instanceof NetHandlerPlayServer)) {
                return;
            }
            EntityPlayerMP player = ((NetHandlerPlayServer) event.getHandler()).player;
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            WorldServer overworld = server == null ? null : server.getWorld(0);
            if (player != null && needed(overworld)) {
                send(player, overworld);
            }
        }

        @SubscribeEvent
        public static void onJoin(EntityJoinWorldEvent event) {
            if (event.getEntity() instanceof EntityPlayerMP && needed(event.getWorld())) {
                EntityPlayerMP player = (EntityPlayerMP) event.getEntity();
                if (player.connection != null) {
                    send(player, event.getWorld());
                }
            }
        }

        /** A new connection: whatever the last server said no longer holds (it says so again, after this). */
        @SubscribeEvent
        public static void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
            HeightHooks.serverOverworldExtended = false;
        }

        /** The client left the server: the next one says for itself. */
        @SubscribeEvent
        public static void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            HeightHooks.serverOverworldExtended = false;
        }
    }
}
