package com.exiledradio.cavesandcliffsbackport.height.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.HeightHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

/** The client half of {@code height.HeightPacket}. Client thread; only ever loaded on the client. */
public final class ClientHeight {

    private ClientHeight() {
    }

    /**
     * The current overworld runs from y -64 to 319: extended now; the chunk provider's stand-in for chunks it has not
     * got (made with the world, so with 16 sections of entity lists, which RenderGlobal.renderEntities indexes by the
     * world's slots) made again; and its render chunks laid out again.
     */
    public static void extend() {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.world;
        if (world == null || !HeightHooks.extendClient(world)) {
            return;
        }
        try {
            ObfuscationReflectionHelper.setPrivateValue(ChunkProviderClient.class, world.getChunkProvider(),
                    new EmptyChunk(world, 0, 0), "field_73238_a");
        } catch (RuntimeException e) {
            CavesAndCliffsBackport.LOGGER.error("Extended height: could not replace the client's empty chunk", e);
        }
        if (mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
        CavesAndCliffsBackport.LOGGER.info("Extended height: the server's overworld runs from y -64 to 319 ({}).",
                world.getWorldInfo().getTerrainType().getName());
    }
}
