package com.exiledradio.cavesandcliffsbackport.copper;

import com.exiledradio.cavesandcliffsbackport.network.NetworkHandler;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: copper at this position was waxed, had its wax taken off or was scraped, so the client draws 1.17's
 * particles on its faces (1.18.2's level events 3003, 3004 and 3005). 1.12 cannot send a mod's particle or an unknown
 * level event, so this goes to everyone within 64 blocks, as a level event does.
 */
public final class PacketCopperParticles implements IMessage {

    public static final int WAX_ON = 0, WAX_OFF = 1, SCRAPE = 2;

    public BlockPos pos;
    public int kind;

    public PacketCopperParticles() {
    }

    public PacketCopperParticles(BlockPos pos, int kind) {
        this.pos = pos;
        this.kind = kind;
    }

    /** Sends the particles of a change at {@code pos} to the players near it. */
    public static void send(World world, BlockPos pos, int kind) {
        if (world.isRemote || NetworkHandler.channel == null) {
            return;
        }
        NetworkHandler.channel.sendToAllAround(new PacketCopperParticles(pos, kind),
                new NetworkRegistry.TargetPoint(world.provider.getDimension(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 64));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.kind = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.pos.toLong());
        buf.writeByte(this.kind);
    }

    /** Registered for the client side only; names the client class only inside the method a client runs. */
    public static final class Handler implements IMessageHandler<PacketCopperParticles, IMessage> {
        @Override
        public IMessage onMessage(PacketCopperParticles message, MessageContext ctx) {
            com.exiledradio.cavesandcliffsbackport.copper.client.CopperClient.particles(message.pos, message.kind);
            return null;
        }
    }
}
