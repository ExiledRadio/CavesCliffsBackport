package com.exiledradio.cavesandcliffsbackport.copper.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Random;

/** Copper on the client: the wax and scrape particles. Registered from {@code ClientSetup} only when the stream is on. */
public final class CopperClient {

    private static final ResourceLocation GLOW = new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "particle/glow");
    private static TextureAtlasSprite sprite;

    private CopperClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new CopperClient());
    }

    @SubscribeEvent
    public void stitch(TextureStitchEvent.Pre event) {
        if (event.getMap() == Minecraft.getMinecraft().getTextureMapBlocks()) {
            sprite = event.getMap().registerSprite(GLOW);
        }
    }

    /** From the network thread: 3 to 5 particles on each face of the block, as 1.18.2's spawnParticlesOnBlockFaces. */
    public static void particles(final BlockPos pos, final int kind) {
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (mc.world != null && sprite != null) {
                    spawn(mc, pos, kind);
                }
            }
        });
    }

    /** @return how many particles were added */
    public static int spawn(Minecraft mc, BlockPos pos, int kind) {
        Random rand = mc.world.rand;
        int added = 0;
        for (EnumFacing face : EnumFacing.values()) {
            int n = 3 + rand.nextInt(3);
            int sx = face.getXOffset(), sy = face.getYOffset(), sz = face.getZOffset();
            for (int i = 0; i < n; i++) {
                double x = pos.getX() + 0.5 + (sx == 0 ? rand.nextDouble() - 0.5 : sx * 0.55);
                double y = pos.getY() + 0.5 + (sy == 0 ? rand.nextDouble() - 0.5 : sy * 0.55);
                double z = pos.getZ() + 0.5 + (sz == 0 ? rand.nextDouble() - 0.5 : sz * 0.55);
                double vx = sx == 0 ? rand.nextDouble() * 2 - 1 : 0;
                double vy = sy == 0 ? rand.nextDouble() * 2 - 1 : 0;
                double vz = sz == 0 ? rand.nextDouble() * 2 - 1 : 0;
                mc.effectRenderer.addEffect(new ParticleWax(mc.world, x, y, z, vx, vy, vz, kind, sprite));
                added++;
            }
        }
        return added;
    }
}
