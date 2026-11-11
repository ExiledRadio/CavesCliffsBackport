package com.exiledradio.cavesandcliffsbackport.glowsquid;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: a glow squid at this position, tilted this way, squirted ink. 1.12 cannot send a mod's
 * particle from the server, so the client makes the thirty particles itself.
 */
public final class PacketGlowInk implements IMessage {

    public double x, y, z;
    public float pitch, yaw;

    public PacketGlowInk() {
    }

    public PacketGlowInk(double x, double y, double z, float pitch, float yaw) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.pitch = pitch;
        this.yaw = yaw;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.pitch = buf.readFloat();
        this.yaw = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.pitch);
        buf.writeFloat(this.yaw);
    }

    /** Registered for the client side only; names the client class only inside the method a client runs. */
    public static final class Handler implements IMessageHandler<PacketGlowInk, IMessage> {
        @Override
        public IMessage onMessage(PacketGlowInk message, MessageContext ctx) {
            com.exiledradio.cavesandcliffsbackport.glowsquid.client.GlowSquidClient.ink(message.x, message.y, message.z,
                    message.pitch, message.yaw);
            return null;
        }
    }
}
