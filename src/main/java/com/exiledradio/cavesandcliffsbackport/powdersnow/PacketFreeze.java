package com.exiledradio.cavesandcliffsbackport.powdersnow;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client, packet id 30: an entity's freeze ticks and whether it is freezing or shaking. Sent when
 * either changes, not every tick: the client counts the ticks itself in between.
 */
public class PacketFreeze implements IMessage {

    private int entityId;
    private int ticks;
    private int flags;

    public PacketFreeze() {
    }

    public PacketFreeze(int entityId, int ticks, int flags) {
        this.entityId = entityId;
        this.ticks = ticks;
        this.flags = flags;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.entityId = ByteBufUtils.readVarInt(buf, 5);
        this.ticks = buf.readUnsignedByte();
        this.flags = buf.readUnsignedByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeVarInt(buf, this.entityId, 5);
        buf.writeByte(this.ticks);
        buf.writeByte(this.flags);
    }

    /** Names no client type: the work is in the client class, which only a client loads. */
    public static final class Handler implements IMessageHandler<PacketFreeze, IMessage> {
        @Override
        public IMessage onMessage(PacketFreeze message, MessageContext ctx) {
            com.exiledradio.cavesandcliffsbackport.powdersnow.client.PowderSnowClient.receive(message.entityId, message.ticks, message.flags);
            return null;
        }
    }
}
